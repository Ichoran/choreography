/* DataMapper.java - Displays a map and movie of where animals went
 * Copyright 2010 Howard Hughes Medical Institute and Rex Kerr
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */
 
package mwt;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Stack;
import java.util.TreeSet;
import java.io.*;
import java.util.concurrent.atomic.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.image.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.imageio.*;

import mwt.numerics.*;
import mwt.plugins.*;


public class DataMapper implements DataMapSource,ActionListener,ChangeListener
{
  public static final int MAX_BUFFERED_IMAGES = 16;
  public static final Dimension MINIMAP_SIZE = new Dimension(160,160);
  public static final double ANTIALIASING_FACTOR = 3.0;
  public static final double AREA_FRACTION_OPTIMIZED = 0.1;
  public static final double BORDER_FRACTION = 0.25;
  public static final double TIME_TOLERANCE = 1e-3;
  public static final double BEARING_SIZE = 2.0;
  public static final double OUTLINE_SIZE = 7.0;
  public static final double DWELL_RADIUS_FACTOR = Statistic.invnormcdf_tail(0.05f);
  
  class ViewRequest
  {
    Vec2D pos;
    double t_at;
    int bg_id;
    Vec2D t_range;
    Vec2I dim;
    double size;
    BufferedImage buf;
    BufferedImage view;
    Vec2I llc;
    Vec2I urc;
    Vec2D view_pos;
    Vec2I view_dim;
    public ColorMapper coloration;
    public Backgrounder background;
    public DotPainter dotter;
    public ValueSource valuer;
    public DataSpecifierWrapper wrapper;
    
    ViewRequest(Vec2D p,Vec2I d,double s,Vec2D t,double tt,int bgid)
    {
      size = s;
      buf = null;
      view = null;
      llc = p.opTimes(1.0/size).eqMinus( d.toD().eqTimes(BORDER_FRACTION) ).toI();
      urc = p.opTimes(1.0/size).eqPlus( d.toD().eqTimes(1.0+BORDER_FRACTION) ).toI();
      pos = llc.toD().opTimes(size);
      dim = urc.opMinus(llc);
      t_at = tt;
      bg_id = bgid;
      t_range = t.copy();
      view_pos = p.copy();
      view_dim = d.copy();
      coloration = null;
      background = null;
      dotter = null;
      valuer = null;
      wrapper = null;
    }
    
    boolean compatible(Vec2D p,Vec2I d,double s,Vec2D tt,int bgid)
    {
      if (buf==null || s!=size) return false;
      if (Math.abs(tt.x-t_range.x)>TIME_TOLERANCE || Math.abs(tt.y-t_range.y)>TIME_TOLERANCE) return false;
      Vec2I ll2 = p.opTimes(1.0/s).toI();
      Vec2I ur2 = p.opTimes(1.0/s).toI().opPlus(d);
      if (llc.x > ll2.x || urc.x < ll2.x || llc.x > ur2.x || urc.x < ur2.x) return false;
      if (llc.y > ll2.y || urc.y < ll2.y || llc.y > ur2.y || urc.y < ur2.y) return false;
      if (bg_id != bgid) return false;
      return true;
    }
    
    void setView(Vec2D p,Vec2I d,double t)
    {
      Vec2I ll = p.opTimes(1.0/size).toI().eqMinus(llc);
      view = new BufferedImage( d.x , d.y , BufferedImage.TYPE_INT_ARGB );
      Graphics2D g2 = view.createGraphics();
      Color hi = new Color(background.highlight());
      Color mi = new Color(background.midlight());
      g2.drawImage(buf,0,0,d.x-1,d.y-1,ll.x,ll.y,ll.x+d.x-1,ll.y+d.y-1,null);
      g2.setColor(hi);
      view_pos.eq(p);
      view_dim.eq(d);
      t_at = t;
      
      for (Dance dance : chore.dances) {
        if (dance==null) continue;
        int idx = dance.seekTimeIndex(chore.times , t , TIME_TOLERANCE);
        if (idx<0) continue;
        if (idx>=dance.centroid.length) {
          System.out.println("Weird, couldn't seek " + t + " in [" + chore.times[dance.first_frame] + "," + chore.times[dance.last_frame] + "]");
          continue;
        }
        if (dance.centroid[idx]==null) continue;
        double wormsize = dance.extent[idx].x * (1000.0*chore.mm_per_pixel) / size;
        Vec2D v = dance.centroid[idx].toD().eqTimes(1000.0*chore.mm_per_pixel).eqMinus(view_pos).eqTimes(1.0/size);
        Vec2I q = v.toI();
        if (v.x+wormsize<0 || v.y+wormsize<0 || v.x-wormsize>=d.x || v.y-wormsize>=d.y) continue;
        
        g2.setColor(hi);
        if (wormsize >= BEARING_SIZE)
        {
          Vec2F va = Vec2F.zero();
          Vec2D vb = Vec2D.zero();
          Vec2D u;
          Vec2D w = null;
          Path2D s = new Path2D.Float();
          if (wormsize >= OUTLINE_SIZE && ((dance.outline!=null && dance.outline[idx]!=null) || (dance.spine!=null && dance.spine[idx]!=null))) {
            boolean is_outline = (dance.outline!=null && dance.outline[idx]!=null);
            s.moveTo(q.x-1,q.y); s.lineTo(q.x+1,q.y);
            s.moveTo(q.x,q.y-1); s.lineTo(q.x,q.y+1);
            int len;
            Vec2S[] ol = null;
            Vec2F[] olf = null;
            float[] wd = null;
            int passes = (dance.outline!=null && dance.outline[idx]!=null && dance.spine!=null && dance.spine[idx]!=null) ? 2 : 1;
            while (passes>0) {
              passes--;
              if (is_outline) {
                if (dance.outline[idx].quantized()==false) {
                  olf = dance.outline[idx].unpack((Vec2F[])null);
                  len = olf.length;
                  ol = null;
                }
                else {
                  ol = dance.outline[idx].unpack(false);
                  len = ol.length;
                  olf = null;
                }
              }
              else {
                len = dance.spine[idx].size();
                if (dance.spine[idx].quantized()) {
                  olf = new Vec2F[len];
                  for (int a=0; a<len; a++) olf[a] = dance.spine[idx].get(a,new Vec2F());
                  if (!Float.isNaN(dance.spine[idx].width(0))) {
                    wd = new float[len];
                    for (int a=0; a<len; a++) wd[a] = dance.spine[idx].width(a);
                  }
                  ol = null;
                }
                else {
                  ol = new Vec2S[len];
                  for (int a=0; a<len; a++) ol[a] = dance.spine[idx].get(a,new Vec2S());
                  olf = null;
                }
              }
              for (int i=0 ; i<len ; i++) {
                u = (olf==null) ? ol[i].toD() : olf[i].toD();
                if (!is_outline) u.eqPlus(dance.centroid[idx].toD());
                u.eqTimes(1000*chore.mm_per_pixel).eqMinus(view_pos).eqTimes(1.0/size);
                if (i==0) { w=u; s.moveTo(u.x,u.y); }
                else s.lineTo(u.x,u.y);
              }
              if (is_outline) s.lineTo(w.x,w.y);
              else if (olf!=null && wd!=null) {
                for (int i=1 ; i<len-1 ; i++) {
                  if (wd[i]*1000*chore.mm_per_pixel/size > 4) {
                    va.eq(olf[i+1]).eqMinus(olf[i-1]).eqNorm();
                    vb.eq(-va.y,va.x).eqTimes(0.5*wd[i]*1000*chore.mm_per_pixel/size);
                    va.eq(dance.centroid[idx]).eqPlus(olf[i]);
                    v.eq(va.x,va.y);
                    v.eqTimes(1000*chore.mm_per_pixel).eqMinus(view_pos).eqTimes(1.0/size);
                    v.eqMinus(vb);
                    s.moveTo(v.x,v.y);
                    v.eqPlus(vb).eqPlus(vb);
                    s.lineTo(v.x,v.y);
                  }
                }
              }
              is_outline = false;
              if (passes>0) {
                g2.draw(s);
                s.reset();
                g2.setColor(mi);
                if (dance.spine != null && dance.spine[idx] != null && dance.spine[idx].oriented()) {
                  v.eq(dance.spine[idx].get(0,va).eqPlus(dance.centroid[idx])).eqTimes(1000*chore.mm_per_pixel).eqMinus(view_pos).eqTimes(1.0/size).eqMinus(3);
                  vb.eq(dance.spine[idx].get(dance.spine[idx].size()-1,va).eqPlus(dance.centroid[idx])).eqTimes(1000*chore.mm_per_pixel).eqMinus(view_pos).eqTimes(1.0/size).eqMinus(3);
                  if (vb.dist2(v) > 400) {
                    g2.drawOval(Math.round((float)v.x),Math.round((float)v.y),6,6);
                    g2.drawRect(Math.round((float)vb.x),Math.round((float)vb.y),6,6);
                  }
                }
              }
            }
          }
          else {
            w = dance.bearing[idx].toD().eqNorm().eqTimes( 0.5*dance.extent[idx].x );
            u = dance.centroid[idx].toD().eqMinus( w ).eqTimes(1000*chore.mm_per_pixel).eqMinus(view_pos).eqTimes(1.0/size);
            v = dance.centroid[idx].toD().eqPlus( w ).eqTimes(1000*chore.mm_per_pixel).eqMinus(view_pos).eqTimes(1.0/size);
            s.moveTo(u.x,u.y); s.lineTo(v.x,v.y);
            w = (new Vec2D(1,1)).eqCross( dance.bearing[idx].toD().eqNorm().eqTimes( 0.5*dance.extent[idx].y ) );
            u = dance.centroid[idx].toD().eqMinus( w ).eqTimes(1000*chore.mm_per_pixel).eqMinus(view_pos).eqTimes(1.0/size);
            v = dance.centroid[idx].toD().eqPlus( w ).eqTimes(1000*chore.mm_per_pixel).eqMinus(view_pos).eqTimes(1.0/size);
            s.moveTo(u.x,u.y); s.lineTo(v.x,v.y);
          }
          g2.draw(s);
        }
        else
        {
          g2.drawLine(q.x-1,q.y,q.x+1,q.y);
          g2.drawLine(q.x,q.y-1,q.x,q.y+1);
        }
      }
    }
  }
  
  
  public class ColorMapper {
    public ColorMapper() {}
    public int d2i(double d) { return (int)Math.floor(d*(256.0 - 1e-8)); }
    public int colorMap(double f) {
      if (Double.isNaN(f)) return 0xFFFF00FF;
      if (f<0.0) return 0xFF0000FF;
      if (f>1.0) return 0xFFFF0000;
      int c = d2i(f);
      return 0xFF000000 | (c<<16) | (c<<8) | c;
    }
    public String mapName() { return "Grayscale"; }
    @Override public String toString() { return mapName(); }
  }
  
  public class SunsetMapper extends ColorMapper {
    public SunsetMapper() {}
    @Override public int colorMap(double f) {
      if (Double.isNaN(f)) return 0xFF800080;
      if (f<0.0) return 0xFFB080FF;
      if (f>1.0) return 0xFFFF80B0;
      double red = Math.min(f,0.5)*2.0*255;
      double green = 128;
      double blue = Math.min(1.0-f,0.5)*2.0*255;
      return 0xFF000000 | (((int)red)<<16) | (((int)green)<<8) | (int)blue;
      
    }
    @Override public String mapName() { return "Sunset"; }
  }
  
  public class RainbowMapper extends ColorMapper {
    public RainbowMapper() {}
    @Override public int colorMap(double f) {
      if (Double.isNaN(f)) return 0xFFFFFFFF;
      if (f<0.0) return 0xFFFFFFFF;
      if (f>1.0) return 0xFFFFFFFF;
      double red = Math.min( 1.0  , Math.max(0.0 , 2.0-6.0*f) + Math.max(0.0 , 6.0*f - 4.0) );
      double green = Math.min( 1.0 , Math.max(0.0 , 2.0 - 6.0*Math.abs(f - 1/3.0)) );
      double blue = Math.min( 1.0 , Math.max(0.0 , 2.0 - 6.0*Math.abs(f-2/3.0)) );
      return 0xFF000000 | (d2i(red)<<16) | (d2i(green)<<8) | d2i(blue);
    }
    @Override public String mapName() { return "Rainbow"; }
  }
  
  public class SpatterMapper extends ColorMapper {
    int entries;
    public SpatterMapper(int N) { entries = Math.min(1,N); }
    public double frac(double d) { return d - Math.floor(d); }
    @Override public int colorMap(double f) {
      if (Double.isNaN(f)) return 0xFFFFFFFF;
      if (f<0.0) return 0xFFFFFFFF;
      if (f>1.0) return 0xFFFFFFFF;
      double red = frac( f*entries / Math.PI );
      double blue = frac( f*entries / Math.E );
      double green = frac( f*entries / Math.sqrt(2.0) );
      double m = 1.0/Math.min(1.0,Math.max(0.1,red*red + blue*blue + green*green));
      return 0xFF000000 | (d2i(red*m)<<16) | (d2i(green*m)<<8) | d2i(blue*m);
    }
    @Override public String mapName() { return "Spatter"; }
  }
  
  
  public class Backgrounder {
    public Backgrounder() {}
    public int highlight() { return 0xFFFFFFFF; }
    public int midlight() { return 0xFFA0A0A0; }
    public int colorAt(Vec2D p) {
      return 0xFF000000;
    }
    public Backgrounder atTime(double t) { return this; }
    public int idAtTime(double t) { return 0; }
    public String bgName() { return "Black"; }
    public String toString() { return bgName(); }
  }
  
  class Whitegrounder extends Backgrounder {
    public Whitegrounder() {}
    @Override public int highlight() { return 0xFF000000; }
    @Override public int midlight() { return 0xFF808080; }
    @Override public int colorAt(Vec2D p) {
      return 0xFFFFFFFF;
    }
    @Override public String bgName() { return "White"; }
  }
  
  class Greengrounder extends Backgrounder {
    public Greengrounder() {}
    @Override public int highlight() { return 0xFFFF80FF; }
    @Override public int midlight() { return 0xFF804080; }
    @Override public int colorAt(Vec2D p) {
      return 0xFF008000;
    }
    @Override public String bgName() { return "Green"; }
  }
  
  class Imagegrounder extends Backgrounder {
    Choreography chore;
    int which = -1;
    int last = -1;
    HashMap< Long , Integer > timeindex = new HashMap< Long , Integer >();
    long keytimes[];
    BufferedImage lastbi;
    BufferedImage bi;
    public Imagegrounder(Choreography c) {
      chore = c;
      bi = null;
      if (chore!=null) {
        if (chore.png_zip!=null) {
          try {
            lastbi = bi = ImageIO.read( chore.directory_zip.getInputStream(chore.png_zip) );
            timeindex.put(0L,-1);
          }
          catch (IOException ioe) { }
          
        }
        else if (chore.png_file!=null) {
          try {
            lastbi = bi = ImageIO.read(chore.png_file);
            timeindex.put(0L,-1);
          }
          catch (IOException ioe) { } // Just leave it null if we can't read it
        }
        if (chore.png_zip!=null && chore.png_zip_set != null && chore.png_zip_set.length > 0) {
          for (int i=0 ; i<chore.png_zip_set.length ; i++) {
            timeindex.put( snipKeyTime( chore.png_zip.getName(), chore.png_zip_set[i].getName() ) , i );
          }
        }
        else if (chore.png_file!=null && chore.png_set_files != null && chore.png_set_files.length > 0) {
          for (int i=0 ; i<chore.png_set_files.length ; i++) {
            timeindex.put( snipKeyTime( chore.png_file.getName(), chore.png_set_files[i].getName() ) , i );
          }
        }
        keytimes = new long[ timeindex.size() ];
        int i = 0;
        for (long l : timeindex.keySet()) { keytimes[i++] = l; }
        Arrays.sort(keytimes);
      }
    }
    protected long snipKeyTime(String base,String fname) {
      int extb = base.lastIndexOf('.');
      int ext = fname.lastIndexOf('.');
      String sn = fname.substring(extb,ext);
      long n = 0;
      try { n = Long.valueOf(sn); } catch (NumberFormatException nfe) { }
      return n;
    }
    @Override public int highlight() { return 0xFF000000; }
    @Override public int midlight() { return 0xFFFF00FF; }
    @Override public int colorAt(Vec2D p) {
      if (chore==null || bi==null) return super.colorAt(p);
      int x = (int)Math.round(p.y/(1000.0*chore.mm_per_pixel));  // Axes flipped thanks to LabView
      int y = (int)Math.round(p.x/(1000.0*chore.mm_per_pixel));  // Axes flipped thanks to LabView
      if (x<0 || y<0 || x>=bi.getWidth() || y>=bi.getHeight()) return super.colorAt(p);
      return bi.getRGB(x,y);
    }
    @Override public int idAtTime(double t) {
      long tl = Math.round(t*1000);
      int i,j,k;
      i = 0;
      j = keytimes.length - 1;
      while (j-i > 1) {
        k = (i+j)/2;
        if (keytimes[k] < tl) i = k;
        else j = k;
      }
      if (Math.abs(tl-keytimes[j]) < 2L) return j;
      else return i;
    }
    @Override public Backgrounder atTime(double t) {
      if (keytimes==null || keytimes.length==0) return this;
      int i = idAtTime(t);
      if (i==which) { } // Already there, do nothing
      else if (i==last) {
        int temp = last;
        BufferedImage tempbi = lastbi;
        last = which;
        lastbi = bi;
        which = temp;
        bi = tempbi;
      }
      else if (chore!=null) {
        BufferedImage nextbi = null;
        int k = timeindex.get(keytimes[i]);
        int next = -1;
        if (chore.png_zip_set != null && chore.png_zip_set.length > 0) {
          try {
            if (k<0) nextbi = ImageIO.read(chore.directory_zip.getInputStream(chore.png_zip));
            else nextbi = ImageIO.read( chore.directory_zip.getInputStream(chore.png_zip_set[k]) );
            next = i;
          }
          catch (IOException ioe) { }
        }
        else if (chore.png_set_files != null && chore.png_set_files.length > 0) {
          try {
            if (k<0) nextbi = ImageIO.read(chore.png_file);
            else nextbi = ImageIO.read(chore.png_set_files[k]);
            next = i;
          }
          catch (IOException ioe) { }
        }
        if (nextbi != null) {
          last = which;
          lastbi = bi;
          which = next;
          bi = nextbi;
        }
      }
      return this;
    }
    @Override public String bgName() { return "Image"; }
  }
  
  class Dimimagegrounder extends Imagegrounder {
    public Dimimagegrounder(Choreography c) { super(c); }
    @Override public int highlight() { return 0xFFFFFFFF; }
    @Override public int midlight() { return 0xFFFF00FF; }
    @Override public int colorAt(Vec2D p) { return ((super.colorAt(p)>>1) & 0xFF7F7F7F) | 0xFF000000; }
    @Override public String bgName() { return "Dimmed"; }
  }
  
  
  class DotPainter {
    BufferedImage image;
    ViewRequest request;
    Dance dance;
    ColorMapper cm;
    public DotPainter() { image=null; cm = null; dance=null; }
    public synchronized void setTarget(BufferedImage bi) { image=bi; }
    public synchronized void setSubject(Dance d) { dance=d; }
    public synchronized void setRequest(ViewRequest vr) { request=vr; }
    public synchronized void setMapper(ColorMapper c) { cm=c; }
    public synchronized void putDot(Vec2D v,int index,double value) { if (v!=null) justADot(v,value); }
    public synchronized void justADot(Vec2D v,double value) { image.setRGB((int)Math.round(v.x),(int)Math.round(v.y),cm.colorMap(value)); }
    public synchronized void allDone() { }
    public String dotName() { return "Pixel"; }
    public String toString() { return dotName(); }
  }
    
  class CirclePainter extends DotPainter {
    double diameter;
    Graphics2D g2;
    public CirclePainter(double d) { super(); g2=null; diameter = d; }
    @Override public synchronized void setTarget(BufferedImage bi) {
      super.setTarget(bi);
      if (bi!=null) { g2=bi.createGraphics(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON); }
      else g2=null;
    }
    @Override public synchronized void putDot(Vec2D v,int index,double value) {
      if (diameter < lastPixelSize) super.putDot(v,index,value);
      else {
        g2.setColor(new Color(cm.colorMap(value)));
        double diam = diameter/lastPixelSize;
        g2.drawOval(
          (int)Math.round(v.x-0.5*diam),
          (int)Math.round(v.y-0.5*diam),
          (int)Math.round(diam),
          (int)Math.round(diam)
        );
      }
    }
    @Override public String dotName() { return "Micron"; }
  }
  
  class SpotPainter extends CirclePainter {
    Ellipse2D spot;
    public SpotPainter(double d) { super(d); spot = new Ellipse2D.Double(0,0,0,0); }
    @Override public synchronized void putDot(Vec2D v,int index,double value) {
      if (g2!=null) {
        spot.setFrame(v.x - 0.5*diameter , v.y-0.5*diameter , diameter , diameter);
        g2.setColor( new Color(cm.colorMap(value)) );
        g2.fill(spot); g2.draw(spot);
      }
    }
    @Override public String dotName() { return "Spot"; }
  }
  
  class LinePainter extends DotPainter {
    Graphics2D g2;
    DotPainter backup;
    Dance.Style recent;
    double recent_value;
    int recent_number;
    Ellipse2D dwell;
    Line2D.Double straight;
    Arc2D.Double arc;
    public LinePainter(DotPainter dp) {
      super(); g2=null; backup=dp; recent=null; recent_value=0.0; recent_number=0;
      dwell=new Ellipse2D.Double(); straight=new Line2D.Double(); arc=new Arc2D.Double(); 
    }
    @Override public synchronized void setTarget(BufferedImage bi) {
      super.setTarget(bi);
      if (backup!=null) backup.setTarget(bi);
      if (bi!=null) { g2=bi.createGraphics(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON); }
      else g2 = null;
    }
    @Override public synchronized void setSubject(Dance d) { recent=null; super.setSubject(d); if (backup!=null) backup.setSubject(d); }
    @Override public synchronized void setMapper(ColorMapper c) { super.setMapper(c); if (backup!=null) backup.setMapper(c); }
    protected void putArrowhead(Vec2D where,Vec2F arrow,float arlen) {
      arlen *= 0.1f;
      if (arlen < 3.0f) arlen = 3.0f;
      arrow.eqTimes( -arlen/arrow.length() );
      where.eqTimes(1000*chore.mm_per_pixel).eqMinus(request.pos).eqTimes(1.0/request.size);
      float old_x = arrow.x;
      arrow.x = (arrow.x - arrow.y)*(float)Math.sqrt(0.5);
      arrow.y = (old_x + arrow.y)*(float)Math.sqrt(0.5);
      straight.x1 = where.x; straight.y1 = where.y;
      straight.x2 = straight.x1 + arrow.x; straight.y2 = straight.y1 + arrow.y;
      g2.draw(straight);
      straight.x2 = straight.x1 + arrow.y; straight.y2 = straight.y1 - arrow.x;
      g2.draw(straight);
    }
    protected void putLine(double value) {
      if (g2==null) return;
      Vec2D pixeler = new Vec2D();
      float line_length = 0.0f;
      if (recent.kind==Dance.Styled.Dwell) {
        pixeler.eq( recent.fit.spot.params.x0 , recent.fit.spot.params.y0 );
        pixeler.eqTimes(1000*chore.mm_per_pixel).eqMinus(request.pos).eqTimes(1.0/request.size);
        double radius = Math.sqrt(recent.fit.n*recent.fit.spot.meanVariance()/(recent.fit.n-1)) * DWELL_RADIUS_FACTOR;
        radius *= 1000*chore.mm_per_pixel/request.size;
        dwell.setFrame( pixeler.x-radius , pixeler.y-radius , 2*radius , 2*radius );
        if (radius>1) {
          g2.setColor( new Color( cm.colorMap(value)&0x60FFFFFF , true ) );
          g2.fill(dwell);
        }
        g2.setColor( new Color(cm.colorMap(value)) );
        g2.draw(dwell);
      }
      else if (recent.kind==Dance.Styled.Straight) {
        int i0 = recent.i0;
        int i1 = recent.i1;
        double c0 = recent.fit.line.lineCoord( dance.centroid[i0].x , dance.centroid[i0].y );
        double c1 = recent.fit.line.lineCoord( dance.centroid[i1].x , dance.centroid[i1].y );
        if (recent.endpoints!=null) {
          int i,j;
          double c;
          if (c1 < c0) { i=i0; i0=i1; i1=i; c=c0; c0=c1; c1=c; }
          for (j=0 ; j<recent.endpoints.length ; j++) {
            i = recent.endpoints[j];
            if (i==i0 || i==i1) continue;
            c = recent.fit.line.lineCoord( dance.centroid[i].x , dance.centroid[i].y );
            if (c>c1) { i1=i; c1=c; }
            else if (c<c0) { i0=i; c0=c; }
          }
        }
        pixeler.eq(dance.centroid[i0]);
        recent.snapToLine(pixeler);
        pixeler.eqTimes(1000*chore.mm_per_pixel).eqMinus(request.pos).eqTimes(1.0/request.size);
        straight.x1 = pixeler.x;
        straight.y1 = pixeler.y;
        pixeler.eq(dance.centroid[i1]);
        recent.snapToLine(pixeler);
        pixeler.eqTimes(1000*chore.mm_per_pixel).eqMinus(request.pos).eqTimes(1.0/request.size);
        straight.x2 = pixeler.x;
        straight.y2 = pixeler.y;
        g2.setColor(new Color(cm.colorMap(value)));
        g2.draw(straight);
        line_length = (float)Math.sqrt((straight.x2-straight.x1)*(straight.x2-straight.x1) + (straight.y2-straight.y1)*(straight.y2-straight.y1));
      }
      else if (recent.kind==Dance.Styled.Arc) {
        int i0 = recent.i0;
        int i1 = recent.i1;
        double c = recent.fit.circ.arcDeltaCoord( dance.centroid[i0].x , dance.centroid[i0].y , dance.centroid[i1].x , dance.centroid[i1].y );
        if (recent.endpoints!=null) {
          int i,j;
          double c0,c1;
          for (j=0 ; j<recent.endpoints.length ; j++) {
            i = recent.endpoints[j];
            if (i==i0 || i==i1) continue;
            c0 = recent.fit.circ.arcDeltaCoord( dance.centroid[i0].x , dance.centroid[i0].y , dance.centroid[i].x , dance.centroid[i].y );
            c1 = recent.fit.circ.arcDeltaCoord( dance.centroid[i1].x , dance.centroid[i1].y , dance.centroid[i].x , dance.centroid[i].y );
            if (Math.abs(c0)>Math.abs(c) || Math.abs(c1)>Math.abs(c)) {
              if (Math.abs(c1)>Math.abs(c0)) { i0=i; c=-c1; }
              else { i1=i; c=c0; }
            }
          }
        }
        if (i0>i1) c=-c;
        double thickness = 100*(recent.fit.circ.params.MSE*1000*chore.mm_per_pixel/request.size)*DWELL_RADIUS_FACTOR;
        pixeler.eq( recent.fit.circ.params.x0 , recent.fit.circ.params.y0 );
        pixeler.eqTimes(1000*chore.mm_per_pixel).eqMinus(request.pos).eqTimes(1.0/request.size);
        double radius = 1000*chore.mm_per_pixel*recent.fit.circ.params.R/request.size;
        arc.x = pixeler.x - radius;
        arc.y = pixeler.y - radius;
        arc.height = arc.width = 2*radius;
        pixeler.eq(dance.centroid[i0]);
        recent.snapToLine(pixeler);
        pixeler.x -= recent.fit.circ.params.x0;
        pixeler.y -= recent.fit.circ.params.y0;
        arc.start = (180/Math.PI)*Math.atan2(-pixeler.y,pixeler.x);
        arc.extent = -(180/Math.PI)*c;
        line_length = (float)(Math.abs(c)*radius);
        g2.setColor( new Color(cm.colorMap(value)) );
        g2.draw(arc);
      }
      if (line_length>3.0f && recent.hasDirection()) {
        Vec2F arrowhead = new Vec2F();
        recent.initialVector(arrowhead);
        pixeler.eq( (recent.endpoints==null) ? dance.centroid[recent.i0] : dance.centroid[recent.endpoints[0]] );
        recent.snapToLine(pixeler);
        putArrowhead(pixeler,arrowhead,line_length);
        recent.finalVector(arrowhead);
        pixeler.eq( (recent.endpoints==null) ? dance.centroid[recent.i1] : dance.centroid[recent.endpoints[recent.endpoints.length-1]] );
        recent.snapToLine(pixeler);
        putArrowhead(pixeler,arrowhead,line_length);
      }
    }
    @Override public synchronized void putDot(Vec2D v,int index,double value) {
      if (backup!=null) backup.putDot(v,index,value);
      else super.putDot(v,index,value);
      
      if (recent!=null) {
        if (index >= recent.i0 && index <= recent.i1) {
          recent_value += value;
          recent_number++;
        }
        else {
          putLine(recent_value/recent_number);
          recent = null;
        }
      }
      if (recent==null) {
        recent = dance.segmentation[ dance.indexToSegment(index) ];
        recent_value = value;
        recent_number = 1;
      }
    }
    @Override public synchronized void allDone() {
      if (recent!=null) {
        putLine(recent_value/recent_number);
        recent = null;
      }
    }
    @Override public String dotName() { return "Line" + ((backup==null) ? "" : ("+"+backup.dotName())); }
  }
  
  class IdentityPainter extends CirclePainter {
    Font f;
    FontRenderContext frc;
    String id_string;
    public IdentityPainter(double d) { 
      super(d);
      f = null;
      frc = null;
      id_string = null;
    }
    @Override public synchronized void setTarget(BufferedImage bi) {
      super.setTarget(bi);
      if (g2!=null) {
        f = new Font(Font.SANS_SERIF,Font.PLAIN,Math.min(16,(int)Math.round(diameter/lastPixelSize)));
        g2.setFont(f);
        frc = g2.getFontRenderContext();
      }
      else { f=null; frc=null; }
    }
    @Override public synchronized void setSubject(Dance d) {
      super.setSubject(d);
      if (d!=null) id_string = String.valueOf(d.ID); else id_string = "X";
    }
    @Override public synchronized void putDot(Vec2D v,int index,double value) {
      if (diameter < 3.0*lastPixelSize || g2==null) justADot(v,value);
      else if (g2!=null) {
        g2.setColor(new Color(cm.colorMap(value)));
        TextLayout tl = new TextLayout(myString(index),f,frc);
        Rectangle2D r2d = tl.getBounds();
        tl.draw(g2 , (float)(v.x - 0.5*r2d.getWidth()) , (float)(v.y + 0.5*r2d.getHeight()));
      }
    }
    public String myString(int index) { return id_string; }
    @Override public String dotName() { return "ID"; }
  }
  
  class FramePainter extends IdentityPainter {
    public FramePainter(double d) { super(d); }
    @Override public String myString(int index) { return (dance==null) ? "X" : String.valueOf(index); }
    @Override public String dotName() { return "Frame"; }
  }
  
  class ValuePainter extends IdentityPainter {
    Choreography.DataPrinter printer;
    public ValuePainter(double d) { super(d); printer=null; }
    public synchronized void setPrinter(Choreography.DataPrinter dp) { printer=dp; }
    @Override public synchronized String myString(int index) { 
      if (dance==null || dance.quantity==null || index<0 || index>=dance.quantity.length) return "X";
      return printer.printValue( dance.quantity[index] );
    }
    @Override public String dotName() { return "Value"; }
  }
  
  
  class ValueSource {
    double t_min;
    double t_max;
    public ValueSource() {
      t_min = chore.times[0];
      t_max = chore.times[chore.times.length-1];
      if (t_max==t_min) { t_max = t_max*1.1+0.5; t_min = t_min*0.9 - 0.5; }
    }
    public double valueAt(Dance d,int idx) {
      return (d.t(idx)-t_min)/(t_max-t_min);
    }
    @Override public String toString() { return "Time"; }
  }
  
  class ValueIdentity extends ValueSource {
    public ValueIdentity() {
      t_min = Double.NaN;
      t_max = Double.NaN;
      for (Dance d : chore.dances) {
        if (d==null) continue;
        if (Double.isNaN(t_min) || t_min>d.ID) t_min = d.ID;
        if (Double.isNaN(t_max) || t_max<d.ID) t_max = d.ID;
      }
      t_min -= 1;
      t_max += 1;
    }
    @Override public double valueAt(Dance d,int idx) {
      return (d.ID-t_min)/(t_max-t_min);
    }
    @Override public String toString() { return "ID"; }
  }
  
  class ValueValue extends ValueSource {
    DataSpecifierWrapper wrapper;
    public ValueValue(DataSpecifierWrapper dsw) { wrapper = dsw; }
    public void setWrapper(DataSpecifierWrapper dsw) { wrapper = dsw; }
    @Override public double valueAt(Dance d,int idx) {
      if (Float.isNaN(d.quantity[idx])) return Float.NaN;
      return (d.quantity[idx]-wrapper.global_min)/Math.max(1e-6,wrapper.global_max-wrapper.global_min);
    }
    @Override public String toString() { return "Value"; }
  }
  
  
  class DataSpecifierWrapper {
    Choreography.DataSpecifier specifier;
    Choreography.DataPrinter printer;
    double global_min;
    double global_max;
    public DataSpecifierWrapper(Choreography.DataSpecifier ds,Choreography.DataPrinter dp) { specifier = ds; printer = dp; }
    public void load() {
      global_min = Double.NaN;
      global_max = Double.NaN;
      for (Dance d : chore.dances) {
        if (d==null) continue;
        chore.loadDancerWithData(d,specifier);
        d.loadMinMax();
        if (Double.isNaN(global_min)) global_min = (double)d.quantity_min;
        else if (!Double.isNaN(d.quantity_min) && global_min>d.quantity_min) global_min = (double)d.quantity_min;
        if (Double.isNaN(global_max)) global_max = (double)d.quantity_max;
        else if (!Double.isNaN(d.quantity_max) && global_max<d.quantity_max) global_max = (double)d.quantity_max;
      }
    }
    @Override public String toString() {
      return Choreography.DataSource.toText(specifier.source) + " " + Choreography.DataMeasure.toText( specifier.measure );
    }
  }
  
  
  class Player extends JPanel implements ChangeListener,ActionListener,Runnable {
    public boolean already_working_on_dancers = false;
    public AtomicBoolean painting;
    public AtomicBoolean forward;
    public AtomicBoolean reverse;
    public AtomicInteger rate;
    public AtomicLong last_play_time;
    public AtomicReference< String > target_id = new AtomicReference< String >("none");
    JComboBox dancer_id;
    JToggleButton play_fwd;
    JButton dont_play;
    JToggleButton play_bkw;
    JSpinner play_rate;
    Thread waits_to_repaint;
    TreeSet< Integer > current_dancers = new TreeSet< Integer >();
    
    public Player() {
      super();
      
      painting = new AtomicBoolean(false);
      forward = new AtomicBoolean(false);
      reverse = new AtomicBoolean(false);
      rate = new AtomicInteger(100);
      last_play_time = new AtomicLong( 0 );
      
      BufferedImage bi;
      Graphics2D g2;
      Path2D p;
      
      bi = new BufferedImage(12,12,BufferedImage.TYPE_INT_ARGB);
      g2 = bi.createGraphics();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
      p = new Path2D.Double();
      p.moveTo(11,5.5); p.lineTo(1,1); p.lineTo(1,10); p.lineTo(11,5.5);
      g2.setColor(Color.BLACK); g2.fill(p);
      g2.setColor(Color.WHITE); g2.draw(p);
      play_fwd = new JToggleButton( new ImageIcon(bi) );
      play_fwd.addChangeListener(this);
      
      bi = new BufferedImage(12,12,BufferedImage.TYPE_INT_ARGB);
      g2 = bi.createGraphics();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
      p = new Path2D.Double();
      p.moveTo(1,5.5); p.lineTo(11,10); p.lineTo(11,1); p.lineTo(1,5.5);
      g2.setColor(Color.BLACK); g2.fill(p);
      g2.setColor(Color.WHITE); g2.draw(p);
      play_bkw = new JToggleButton( new ImageIcon(bi) );
      play_bkw.addChangeListener(this);

      bi = new BufferedImage(12,12,BufferedImage.TYPE_INT_ARGB);
      g2 = bi.createGraphics();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
      p = new Path2D.Double();
      p.moveTo(10,2); p.lineTo(2,2); p.lineTo(2,10); p.lineTo(10,10); p.lineTo(10,2);
      g2.setColor(Color.BLACK); g2.fill(p);
      g2.setColor(Color.WHITE); g2.draw(p);
      dont_play = new JButton( new ImageIcon(bi) );
      dont_play.addActionListener(this);
      
      play_rate = new JSpinner( new SpinnerNumberModel(100 , 0 , 100000 , 1) );
      play_rate.addChangeListener(this);
      
      dancer_id = new JComboBox();
      dancer_id.setEditable(true);
      dancer_id.addItem("none");
      for (Dance d : chore.dances) {
        if (d==null) continue;
        if (d.first_frame > 1) continue;
        current_dancers.add(d.ID);
        dancer_id.addItem(""+d.ID);
      }
      dancer_id.addActionListener(this);
      
      add(new JLabel("Follow:"));
      add(dancer_id);
      add(play_bkw);
      add(dont_play);
      add(play_fwd);
      add(play_rate);
      add(new JLabel("speed (%)"));
      
      waits_to_repaint = new Thread(this);
      waits_to_repaint.start();
    }
    
    public synchronized void findDancersAt(float t) {
      if (already_working_on_dancers) return;  // Need this to avoid re-entering when we throw list-changed events on updating list
      already_working_on_dancers = true;
      TreeSet< Integer > present = new TreeSet< Integer >();
      for (Dance d : chore.attendance.get(chore.indexNear(t))) {
        if (d==null) continue;
        present.add(d.ID);
      }
      boolean mismatch = false;
      int i;
      Stack< Integer > to_remove = null;
      Stack< Integer > removed_val = null;
      i = 0;
      if (dancer_id.getItemCount()-1 != current_dancers.size()) {
        System.out.println("Mismatch between on-screen ("+dancer_id.getItemCount()+") and internal list ("+current_dancers.size()+")...how'd that happen?");
      }
      for (Integer j : current_dancers) {
        i += 1;
        if (present.contains(j)) continue;
        if (to_remove==null) {
          to_remove = new Stack< Integer >();
          removed_val = new Stack< Integer >();
        }
        mismatch = true;
        to_remove.push(i);
        removed_val.push(j);
      }
      if (to_remove != null) {
        while (!to_remove.isEmpty()) dancer_id.removeItemAt( to_remove.pop() );
        while (!removed_val.isEmpty()) current_dancers.remove( removed_val.pop() );
      }
      i = 0;
      for (Integer j : present) {
        i += 1;
        if (!current_dancers.contains(j)) {
          mismatch = true;
          dancer_id.insertItemAt(""+j,i);
        }
      }
      if (mismatch) current_dancers = present;
      already_working_on_dancers = false;
    }
    public void stateChanged(ChangeEvent e) {
      if (e.getSource() instanceof JToggleButton) {
        JToggleButton jtb = (JToggleButton)e.getSource();
        if (jtb==play_fwd) {
          if (play_fwd.isSelected() && play_bkw.isSelected()) play_bkw.setSelected(false);
          if (play_fwd.isSelected()) last_play_time.set( System.currentTimeMillis() );
        }
        else if (jtb==play_bkw) {
          if (play_bkw.isSelected() && play_fwd.isSelected()) play_fwd.setSelected(false);
          if (play_bkw.isSelected()) last_play_time.set( System.currentTimeMillis() );
        }
        forward.set( play_fwd.isSelected() );
        reverse.set( play_bkw.isSelected() );
      }
      else if (e.getSource() instanceof JSpinner) {
        JSpinner js = (JSpinner)e.getSource();
        if (js==play_rate) {
          rate.set( ((Integer)js.getValue()).intValue() );
        }
      }
    }
    public void actionPerformed(ActionEvent e) {
      if (e.getSource() instanceof JButton) {
        JButton jb = (JButton)e.getSource();
        if (jb==dont_play) {
          if (play_fwd.isSelected()) play_fwd.setSelected(false);
          if (play_bkw.isSelected()) play_bkw.setSelected(false);
          forward.set( false );
          reverse.set( false );
          last_play_time.set( 0 );
        }
      }
      else if (e.getSource() instanceof JComboBox) {
        JComboBox jcb = (JComboBox)e.getSource();
        target_id.set(jcb.getSelectedItem().toString());
        if (!forward.get() && !reverse.get()) lsb.getTriSpinner().fireStateChanged();
      }
    }
    public void run() {
      while (true) {
        if (!painting.get() && (forward.get() || reverse.get())) {
          long then = last_play_time.get();
          long now = System.currentTimeMillis();
          long elapsed = (then==0) ? 0 : now - then; 
          
          double dt = (0.001*elapsed) * (0.01*rate.get());
          if (reverse.get()) dt = -dt;
          double t = lsb.getTriSpinner().centerValue();
          double t0 = lsb.getTriSpinner().leftValue();
          double t1 = lsb.getTriSpinner().rightValue();
          if (forward.get() && t==t1) {
            forward.set(false);
            play_fwd.setSelected(false);
            last_play_time.set(0);
          }
          if (reverse.get() && t==t0) {
            reverse.set(false);
            play_bkw.setSelected(false);
            last_play_time.set(0);
          }
          if (t+dt>t1) dt=t1-t;
          if (t+dt<t0) dt=t0-t;
          
          int i_old = Arrays.binarySearch( chore.times , (float)t );
          int i_new = Arrays.binarySearch( chore.times , (float)(t+dt) );
          if (i_old<0) i_old = -1-i_old;
          if (i_new<0) i_new = -1-i_new;
          if (i_new>=0 && i_new<chore.times.length && i_old!=i_new) {
            LinearSelectorBar.RangeSpinner lsb_rs = lsb.getTriSpinner();
            last_play_time.set( now );
            painting.set(true);
            lsb_rs.setCenter(t+dt);
            
            // Check to make sure something actually happened
            if (lsb_rs.centerValue()==t) {
              painting.set(false);
              last_play_time.set( then );
            }
            else { findDancersAt( (float)(t+dt) ); }
          }
        }
        try {
          if (forward.get() || reverse.get()) Thread.sleep(33);
          else Thread.sleep(500);
        }
        catch (InterruptedException ie) {}
      }
    }
  }
  

  Choreography chore;
  Choreography.DataPrinter[] printers;
  Choreography.DataSpecifier[] specifiers;
  LinearSelectorBar lsb;
  java.util.List<DataReadyListener> drl_list;
  Rectangle2D.Double data_bounds;
  java.util.List<ViewRequest> vr_list;
  double lastPixelSize;
  ColorMapper[] color_options;
  JComboBox color_picker;
  Backgrounder[] background_options;
  JComboBox background_picker;
  DotPainter[] dot_options;
  ValuePainter value_dot;
  JComboBox dot_picker;
  ValueSource[] value_options;
  ValueValue value_value;
  JComboBox value_picker;
  DataSpecifierWrapper[] data_options;
  JComboBox data_picker;
  JPanel option_panel;
  JPanel master_panel;
  Player play_buttons;
  Dance tracked_dancer = null;
  
  public DataMapper(Choreography c,Choreography.DataPrinter[] c_dp,Choreography.DataSpecifier[] c_ds)
  {
    chore = c;
    printers = c_dp;
    specifiers = c_ds;
    drl_list = new LinkedList<DataReadyListener>();
    vr_list = new LinkedList<ViewRequest>();
    data_bounds = null;

    double[] d_times = new double[ chore.times.length ];
    for (int i=0;i<d_times.length;i++) d_times[i] = 0.001*Math.rint( chore.times[i]*1000 );  // Float has crappy precision--put it back at 3 decimals
    lsb = new LinearSelectorBar( new RealArray( d_times ) );

    lastPixelSize = -1.0;
    lsb.getTriSpinner().addChangeListener(this);
    
    option_panel = new JPanel();
    
    color_options = new ColorMapper[4];
    color_options[0] = new ColorMapper();
    color_options[1] = new SunsetMapper();
    color_options[2] = new RainbowMapper();
    color_options[3] = new SpatterMapper(chore.dances.length);
    color_picker = new JComboBox(color_options);
    color_picker.setEditable(false);
    color_picker.setSelectedIndex(1);
    color_picker.addActionListener(this);
    option_panel.add(color_picker);
    
    background_options = new Backgrounder[5];
    background_options[0] = new Backgrounder();
    background_options[1] = new Whitegrounder();
    background_options[2] = new Greengrounder();
    background_options[3] = new Imagegrounder(chore);
    background_options[4] = new Dimimagegrounder(chore);
    background_picker = new JComboBox(background_options);
    background_picker.setEditable(false);
    background_picker.setSelectedIndex(0);
    background_picker.addActionListener(this);
    option_panel.add(background_picker);
    
    dot_options = new DotPainter[6+((c.segment_path)?2:0)];
    dot_options[0] = new DotPainter();
    dot_options[1] = new SpotPainter(3.0);
    dot_options[2] = new CirclePainter(1.0);
    dot_options[3] = new IdentityPainter(1.5);
    dot_options[4] = new FramePainter(1.5);
    dot_options[5] = value_dot = new ValuePainter(1.5);
    if (c.segment_path) {
      dot_options[6] = new LinePainter( dot_options[0] );
      dot_options[7] = new LinePainter( dot_options[2] );
    }
    dot_picker = new JComboBox(dot_options);
    dot_picker.setEditable(false);
    dot_picker.setSelectedIndex(0);
    dot_picker.addActionListener(this);
    option_panel.add(dot_picker);
    
    value_options = new ValueSource[3];
    value_options[0] = new ValueSource();
    value_options[1] = new ValueIdentity();
    value_options[2] = value_value = new ValueValue(null);
    value_picker = new JComboBox(value_options);
    value_picker.setEditable(false);
    value_picker.setSelectedIndex(0);
    value_picker.addActionListener(this);
    option_panel.add(value_picker);
    
    int n_dsw = (printers==null || specifiers==null) ? 0 : Math.min(printers.length,specifiers.length);
    data_options = new DataSpecifierWrapper[n_dsw];
    for (int i=0;i<n_dsw;i++) {
      data_options[i] = new DataSpecifierWrapper(specifiers[i],printers[i]);
    }
    data_options[0].load();
    value_dot.setPrinter(printers[0]);
    value_value.setWrapper(data_options[0]);
    data_picker = new JComboBox(data_options);
    data_picker.setEditable(false);
    data_picker.setSelectedIndex(0);
    data_picker.addActionListener(this);
    option_panel.add(data_picker);
    
    play_buttons = new Player();
    lsb.setRepaintWaiter( play_buttons.painting );
    
    master_panel = new JPanel();
    master_panel.setLayout(new BoxLayout(master_panel,BoxLayout.Y_AXIS));
    master_panel.add(lsb);
    master_panel.add(option_panel);
    master_panel.add(play_buttons);
  }
  
  // ActionListener section
  public void actionPerformed(ActionEvent ae) {
    if (ae.getSource()==data_picker) {
      DataSpecifierWrapper dsw = (DataSpecifierWrapper)data_picker.getSelectedItem();
      dsw.load();
      value_value.setWrapper(dsw);
      value_dot.setPrinter(dsw.printer);
    }
    Object o = ae.getSource();
    if (o==color_picker || o==background_picker || o==dot_picker || o==value_picker || o==data_picker) {
      for (DataReadyListener drl : drl_list) {
        drl.dataReady(new DataReadyEvent(this,null));
      }
    }
  }
  
  // DataMapSource section
  public void addDataReadyListener(DataReadyListener drl)
  {
    synchronized(drl_list)
    {
      if (!drl_list.contains(drl)) drl_list.add(drl);
    }
  }
  public void removeDataReadyListener(DataReadyListener drl)
  {
    synchronized(drl_list)
    {
      if (drl_list.contains(drl)) drl_list.remove(drl);
    }
  }
  public String getStatus(Vec2D position, Vec2I dimensions, double pixelSize, Vec2D cursor_position) { return ""; }
  public JPopupMenu getContextMenu(Vec2D position, Vec2I dimensions, double pixelSize, Vec2D cursor_position) { return null; }
  public JComponent getGUIElement() { return master_panel; }
  public void stateChanged(ChangeEvent e) {
    if (e.getSource() instanceof LinearSelectorBar.RangeSpinner) {
      float t = (float)(lsb.getTriSpinner().centerValue());
      if (!play_buttons.forward.get() && !play_buttons.reverse.get()) play_buttons.findDancersAt(t);
      for (DataReadyListener drl : drl_list) {
        drl.dataReady(new DataReadyEvent(this,trackLoc(t)));
      }
    }
  }
  public Vec2D trackLoc(float t) {
    String chosen = (play_buttons==null) ? "" : play_buttons.target_id.get();
    if (chosen==null) chosen = "";
    int id = -1;
    try { id = Integer.valueOf(chosen); } catch (Exception e) { }
    if (id < 0) return null;
    if (tracked_dancer == null || tracked_dancer.ID!=id) {
      LinkedList< Dance > ld = chore.attendance.get( chore.indexNear(t) );
      if (ld != null) {
        for (Dance d : ld) {
          if (d!=null && d.ID==id) {
            tracked_dancer = d;
            break;
          }
        }
      }
    }
    if (tracked_dancer != null && tracked_dancer.ID==id) {
      int idx = tracked_dancer.seekTimeIndex(chore.times,t,1e-3);
      if (idx<0 || idx >= tracked_dancer.centroid.length) return null;
      return tracked_dancer.centroid[idx].toD().eqTimes(1000*chore.mm_per_pixel);
    }
    else return null;
  }

  // Make sure the dancers are ready for multiscale operation before calling this!  
  public Rectangle2D.Double getBounds()
  {
    if (data_bounds==null)
    {
      double x0,x1,y0,y1;
      
      // Use these values if there is no real data
      x0 = y0 = 0.0;
      x1 = y1 = 1.0;
      
      // Find a real data point to start off
      for (Dance d : chore.dances)
      {
        if (d==null) continue;
        if (d.ranges_xy == null) continue;
        x0 = d.ranges_xy.xy0.x;
        y0 = d.ranges_xy.xy0.y;
        x1 = d.ranges_xy.xy1.x;
        y1 = d.ranges_xy.xy1.y;
      }
      
      // And now scan through all data
      for (Dance d : chore.dances)
      {
        if (d==null) continue;
        if (d.ranges_xy == null) continue;
        if (d.ranges_xy.xy0.x < x0) x0 = d.ranges_xy.xy0.x;
        if (d.ranges_xy.xy0.y < y0) y0 = d.ranges_xy.xy0.y;
        if (d.ranges_xy.xy1.x > x1) x1 = d.ranges_xy.xy1.x;
        if (d.ranges_xy.xy1.y > y1) y1 = d.ranges_xy.xy1.y;
      }
      
      if (x0==x1) { x0 -= 0.5; x1 += 0.5; }
      if (y0==y1) { y0 -= 0.5; y1 += 0.5; }
      
      data_bounds = new Rectangle2D.Double(x0,y0,x1-x0,y1-y0);
    }
    
    return new Rectangle2D.Double( data_bounds.getX() , data_bounds.getY() , data_bounds.getWidth() , data_bounds.getHeight() );
  }
  
  public synchronized BufferedImage getView(Vec2D position, Vec2I dimensions, double pixelSize, BufferedImage buffer) throws IllegalArgumentException
  {
    Vec2D t_range = lsb.getTriSpinner().rangeValue();
    double t_now = lsb.getTriSpinner().centerValue();
    double t_delta = Math.max( TIME_TOLERANCE , t_range.y - t_range.x );
    ColorMapper cm = (ColorMapper)color_picker.getSelectedItem();
    Backgrounder bg = (Backgrounder)background_picker.getSelectedItem();
    int bgid = bg.idAtTime(t_now);
    DotPainter dp = (DotPainter)dot_picker.getSelectedItem();
    ValueSource vs = (ValueSource)value_picker.getSelectedItem();
    DataSpecifierWrapper dsw = (DataSpecifierWrapper)data_picker.getSelectedItem();
    
    for (ViewRequest vr : vr_list)
    {
      if (cm != vr.coloration || bg != vr.background || dp != vr.dotter || vs!=vr.valuer || dsw!=vr.wrapper) continue;
      if (vr.compatible(position,dimensions,pixelSize,t_range,bgid))
      {
        if (position.x==vr.view_pos.x && position.y==vr.view_pos.y && dimensions.x==vr.view_dim.x && dimensions.y==vr.view_dim.y && pixelSize==vr.size &&
            Math.abs(t_now-vr.t_at)<=TIME_TOLERANCE)
        {
          return vr.view;
        }
        else
        {
          vr.setView(position,dimensions,t_now);
          for (CustomOutputModification com : chore.plugmods) com.modifyMapDisplay(position,dimensions,pixelSize,vr.view,t_now,this);
          return vr.view;
        }
      }
    }
    while (vr_list.size() >= MAX_BUFFERED_IMAGES)
    {
      if (vr_list.isEmpty()) break;  // Unnecessary unless some idiot sets MAX_BUFFERED_IMAGES to 0 or less
      vr_list.remove(0);
    }
    
    ViewRequest vr = new ViewRequest(position , dimensions,pixelSize , t_range , t_now , bgid);
    vr.coloration = cm;
    vr.background = bg.atTime(vr.t_at);
    vr.dotter = dp;
    vr.valuer = vs;
    vr.wrapper = dsw;
    if (buffer==null || buffer.getWidth() < vr.dim.x || buffer.getHeight() < vr.dim.y || pixelSize != lastPixelSize)
    {
      buffer = new BufferedImage(vr.dim.x , vr.dim.y , BufferedImage.TYPE_INT_ARGB);
    }
    lastPixelSize = pixelSize;
    Vec2D v = new Vec2D();
    for (int j=0 ; j<vr.dim.y ; j++)
    {
      v.y = vr.pos.y + j*vr.size;
      for (int i=0 ; i<vr.dim.x ; i++)
      {
        v.x = vr.pos.x + i*vr.size;
        buffer.setRGB(i,j,bg.colorAt(v));
      }
    }
    
    Vec2F this_corner = vr.pos.toF();
    Vec2F other_corner = vr.urc.toD().eqTimes(pixelSize).toF();
    double area = other_corner.opMinus(this_corner).length2();
    java.util.List<QuadRanger.Interval> lqri = null;
    
    dp.setTarget(buffer);
    dp.setRequest(vr);
    dp.setMapper(cm);
    for (Dance d : chore.dances)
    {
      if (d==null) continue;
      Vec2I clip = d.seekTimeIndices( chore.times , vr.t_range , TIME_TOLERANCE );
      if (clip==null) continue;
      dp.setSubject(d);
      
      int scale;
      double stepsize;
      for (scale = 0 ; scale < d.multiscale_x.data.length-1 ; scale++)
      {
        stepsize = d.multiscale_x.diffsize[scale] + d.multiscale_y.diffsize[scale];
        if (stepsize*ANTIALIASING_FACTOR > pixelSize) break;
      }
      if (d.ranges_xy.boundedArea() * AREA_FRACTION_OPTIMIZED > area)
      {
        lqri = d.ranges_xy.accumulateValid(this_corner,other_corner,null);
        for (QuadRanger.Interval qri : lqri)
        {
          int k0 = Math.max(qri.x0 , clip.x);
          int k1 = Math.min(qri.x1 , clip.y+1);
          if (k1<k0) continue;
          
          for (int k=k0 ; k<k1 ; k++)
          {
            v.eq( d.multiscale_x.data[0][k] , d.multiscale_y.data[0][k] );
            v.eqMinus(vr.pos).eqDivide(pixelSize);
            if (v.x < 0 || v.y < 0 || v.x+0.5001 >= vr.dim.x || v.y+0.5001 >= vr.dim.y) continue;
            dp.putDot( v , k , vs.valueAt(d,k) );
          }
        }
      }
      else
      {
        for (int k=0 ; k < d.multiscale_x.data[ scale ].length ; k++)
        {
          v.eq( d.multiscale_x.data[ scale ][k] , d.multiscale_y.data[ scale ][k] );
          v.eqMinus(vr.pos).eqDivide(pixelSize);
          if (v.x < 0 || v.y < 0 || v.x+0.5001 >= vr.dim.x || v.y+0.5001 >= vr.dim.y) continue;
          int idx = (int)(k*Math.pow(2,scale));
          if (idx<clip.x || idx>clip.y) continue;
          dp.putDot( v , idx , vs.valueAt(d,idx) );
        }
      }
    }
    dp.allDone();
    dp.setTarget(null);
    
    vr.buf = buffer;
    vr.setView(position,dimensions,t_now);
    for (CustomOutputModification com : chore.plugmods) com.modifyMapDisplay(position,dimensions,pixelSize,vr.view,t_now,this);

    vr_list.add(vr);

    return vr.view;
  }
}

