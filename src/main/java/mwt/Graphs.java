/* Graphs.java - Plots data as a standard XY graph
 * Copyright 2010 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015 Calico Life Sciences LLC (authored by Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt;
 
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.*;
import java.awt.geom.*;
import javax.swing.*;
import javax.swing.event.*;

import mwt.plugins.*;

public class Graphs
{
  public enum Orientation { down,left,right };
  
  protected boolean sigDiff(double d,double f)
  {
    double ulp = Math.ulp(d)+Math.ulp(f);
    if (d<f)
    {
      return (f-d)>1000*ulp;
    }
    else
    {
      return (d-f)>1000*ulp;
    }
  }
  
  public static class Colorer
  {
    public static final int MAX_COLORS = 26;
    public static final Color[] colors;
    static
    {
      double color_phase = 0;
      double color_advance = 0.1;
      double red,green,blue,boost;
      double phase;
      double d;
      double[] bestpoints = {0,1/6.0,1/2.0,2/3.0,1.0};
      double[] realpoints = {0,0.3,0.5,0.8,1.0};
      double gamma = 1.3;
      colors = new Color[MAX_COLORS];
      for (int i=0;i<MAX_COLORS;i++)
      {
        int j=0;
        while ( realpoints[j] <= color_phase ) j++;
        double f = (color_phase-realpoints[j-1])/(realpoints[j]-realpoints[j-1]);
        phase = bestpoints[j-1] + f*(bestpoints[j]-bestpoints[j-1]);
        blue = Math.min( 1.0 , 2.0*(Math.max(0,1.0-3.0*phase) + Math.max(0,3.0*phase-2.0)) );
        green = Math.min(1.0 , 2.0*Math.max(0,1.0-Math.abs(1.0-3.0*phase)) );
        red = Math.min( 1.0 , 2.0*Math.max(0,1.0-Math.abs(2.0-3.0*phase)) );
        d = Math.max(1.0 , 1.6*green - 0.8*(blue+red));
        blue = Math.pow(blue/d,1.0/gamma);
        green = Math.pow(green/d,1.0/gamma);
        red = Math.pow(red/d,1.0/gamma);
        colors[i] = new Color((float)red,(float)green,(float)blue);
        color_phase += color_advance;
        if (color_phase + 0.05*color_advance >= 1.0)
        {
          color_phase -= 1.0;
          if (Math.abs(color_phase)<color_advance/10) { color_phase = 0.15; color_advance = 0.2; }
          else if (Math.abs(color_phase-0.15)<color_advance/20) { color_phase=0.05; }
          else if (Math.abs(color_phase-0.05)<color_advance/20) { color_phase=0.025; color_advance=0.05; }
          else if (Math.abs(color_phase-color_advance/2)<color_advance/20) { color_advance *= 0.5; color_phase = color_advance*0.2; }
        }
      }
    }
  }
  
  public class Ticker
  {
    public static final double MIN_INTERVAL = 1e-7;
    public static final double MIN_LABEL_DENSITY = 0.3;
    double lo;
    double hi;
    public int pixels;
    public float fracs[];
    public int locs[];
    public double values[];
    
    public Ticker leader;
    
    public Ticker(Ticker tk)
    {
      lo = tk.lo;
      hi = tk.hi;
      pixels = tk.pixels;
      fracs = null;
      locs = null;
      values = null;
      leader = tk.leader;
      
      fixHigh();
    }
    public Ticker(double low_value,double high_value)
    {
      lo = low_value;
      hi = high_value;
      pixels = -1;
      fracs = null;
      locs = null;
      values = null;
      leader = null;
      
      fixHigh();
    }
    
    public void imitate(Ticker tk)
    {
      lo = tk.lo;
      hi = tk.hi;
      pixels = tk.pixels;
      leader = tk.leader;
      if (tk.fracs!=null) fracs = Arrays.copyOf(tk.fracs,tk.fracs.length);
      locs = null;
      values = null;
    }
    
    public boolean isSame(Ticker tk)
    {
      return (lo==tk.lo && hi==tk.hi && pixels==tk.pixels && fracs!=null && tk.fracs!=null && fracs.length==tk.fracs.length);
    }
    
    void fixHigh()
    {
      double after_lo = Math.max(lo+MIN_INTERVAL,Math.nextUp(lo));
      if (after_lo > hi) hi = after_lo;
    }
    double fixDelta(double delta)
    {
      if (delta<MIN_INTERVAL) delta = MIN_INTERVAL;
      if (delta<Math.ulp(lo)) delta = Math.ulp(lo);
      if (delta<Math.ulp(hi)) delta = Math.ulp(hi);
      return delta;
    }
    
    public void setLow(double low_value) { lo = low_value; fixHigh(); }
    public void setHigh(double high_value) { hi = high_value; fixHigh(); }
    public void set(double low_value,double high_value)
    {
      lo = low_value;
      hi = high_value;
      fixHigh();
    }
    
    public double scale(double d,int steps)
    {
      double d_saved = d;
      int powers_of_ten = steps/3;
      int remainder_steps = steps-3*powers_of_ten;
      
      if (d < MIN_INTERVAL) d = MIN_INTERVAL;
      double base = Math.floor(Math.log10(d));
      double test = Math.log10(d) - base;
      double dist_1 = Math.min( Math.abs(test) , Math.abs(1.0-test) );
      double dist_2 = Math.abs( test - Math.log10(2.0) );
      double dist_5 = Math.abs( test - Math.log10(5.0) );
      
      // To prevent cumulative roundoff error, assign from scratch each time
      if (dist_5 < dist_2 && dist_5 < dist_1) d = 5.0;
      else if (dist_2 <= dist_5 && dist_2 < dist_1) d = 2.0;
      else
      {
        if (test < 0.5) d = 1.0;
        else d = 10.0;
      }
      
      // Now figure out multiples that are smaller than ten
      if (remainder_steps==2)
      {
        if (dist_5 < dist_2 && dist_5 < dist_1) d *= 4.0;
        else d *= 5.0;
      }
      else if (remainder_steps==1)
      {
        if (dist_2 <= dist_5 && dist_2 < dist_1) d *= 2.5;
        else d *= 2.0;
      }
      else if (remainder_steps==-1)
      {
        if (dist_5 < dist_2 && dist_5 < dist_1) d *= 0.4;
        else d *= 0.5;
      }
      else if (remainder_steps==-2)
      {
        if (dist_2 <= dist_5 && dist_2 < dist_1) d *= 0.25;
        else d *= 0.2;
      }
      
      // Now get the right power of ten
      return d * Math.pow( 10 , powers_of_ten + base );
    }
    public double smaller(double d) { return scale(d,-1); }
    public double bigger(double d) { return scale(d,1); }
    
    public int count(double delta) { return (int)Math.round( (hi-lo)/fixDelta(delta) ); } 
    
    public double findSmall(int min_spacing)
    {
      int max_intervals = pixels/min_spacing;
      if (max_intervals<1) max_intervals = 1;
      
      double smallest = Math.pow(10.0,Math.ceil(Math.log10((hi-lo)/max_intervals)));
      if (smallest*0.5 >= (hi-lo)/max_intervals)
      {
        if (smallest*0.2 >= (hi-lo)/max_intervals)
        {
          // Might be rounding errors with log, so need to test this one too
          if (smallest*0.1 >= (hi-lo)/max_intervals)  smallest *= 0.1;
          else smallest *= 0.2;
        }
        else smallest *= 0.5;
      }
      
      return smallest;
    }
    
    public boolean isNice(int min_spacing)
    {
      if (fracs==null) return false;
      if ((fracs.length-1)*min_spacing < MIN_LABEL_DENSITY*pixels && fracs.length-1 < count(findSmall(min_spacing))) return false;
      if ((fracs.length-1)*min_spacing > pixels) return false;
      return true;
    }
    
    public void setFracs(int min_spacing)
    {
      double delta;
      int number;
      if (leader==null || leader.fracs==null)
      {
        delta = findSmall(min_spacing);
        number = count(delta);
      }
      else
      {
        number = leader.fracs.length-1;
        delta = findSmall(min_spacing);
        if (number+1 < count(delta)) delta = bigger(delta);
      }
      
      if (number<1) number = 1;
      if (delta<MIN_INTERVAL) delta = MIN_INTERVAL;
      lo = delta*Math.rint( lo/delta );
      hi = lo + number*delta;
      fracs = new float[number+1];
      for (int i=0;i<fracs.length;i++) fracs[i] = i/(float)number;
    }
    
    public void refresh()
    {
      if (leader!=null && leader.fracs!= null)
      {
        if (fracs==null || leader.fracs.length != fracs.length) fracs = Arrays.copyOf(leader.fracs , leader.fracs.length);
      }
      if (fracs!=null)
      {
        if (locs==null || locs.length!=fracs.length) locs = new int[fracs.length];
        if (values==null || values.length!=fracs.length) values = new double[fracs.length];
        for (int i=0;i<fracs.length;i++)
        {
          locs[i] = Math.round( fracs[i]*pixels );
          values[i] = fracs[i]*(hi-lo) + lo;
        }
      }
      else
      {
        locs = null;
        values = null;
      }
    }
    
    public void setInitialRange(int n_pixels,double data_low,double data_high,int min_spacing)
    {
      if (n_pixels < 1) n_pixels = 1;
      if (min_spacing < 1) min_spacing = 1;
      if (min_spacing > n_pixels) min_spacing = n_pixels;
      pixels = n_pixels;
      lo = data_low;
      hi = data_high;
      fixHigh();
      double border = (hi-lo)/(n_pixels/min_spacing);
      if (lo>=0.0 && lo-border < 0.0) lo = 0.0;
      else lo -= border;
      if (hi<=0.0 && hi+border > 0.0) hi = 0.0;
      else hi += border;
      fixHigh();
      setFracs(min_spacing);
      refresh();
    }
    
    public void changeZoom(int zoom)
    {
      if (fracs==null || fracs.length<1) return;
      double delta = (hi-lo)/(fracs.length-1);
      delta = scale(delta,zoom);
      if (delta < MIN_INTERVAL) delta = MIN_INTERVAL;
      lo = delta*Math.rint( lo/delta );
      hi = lo + (fracs.length-1)*delta;
      fixHigh();
      refresh();
    }
    
    public void changeSize(int min_spacing,int n_pixels)
    {
      if (leader!=null && leader.pixels!=n_pixels) leader.changeSize(min_spacing,n_pixels);
      
      if (n_pixels==pixels) return;
      if (n_pixels<1) pixels = 1;
      else pixels = n_pixels;
      
      if (leader==null && !isNice(min_spacing)) setFracs(min_spacing);
      if (leader!=null && leader.fracs!=null && fracs!=null && leader.fracs.length != fracs.length) setFracs(min_spacing);
      refresh();
    }
    
    public void addTick(int min_spacing)
    {
      if (fracs==null || fracs.length<2) return;
      if (leader!=null && leader.fracs!=null && fracs!=null && leader.fracs.length == fracs.length) leader.addTick(min_spacing);
      hi = hi + (hi - lo)*fracs[1];
      fixHigh();
      setFracs(min_spacing);
      refresh();
    }
    
    public void subTick(int min_spacing)
    {
      if (fracs==null || fracs.length<3) return;
      if (leader!=null && leader.fracs!=null && fracs!=null && leader.fracs.length == fracs.length) leader.addTick(min_spacing);
      hi = fracs[fracs.length-1];
      fixHigh();
      setFracs(min_spacing);
      refresh();
    }
    
    public void makeReady(int n_pixels,int min_spacing)
    {
      if (leader!=null) leader.makeReady(n_pixels,min_spacing);
      if (pixels<0) setInitialRange(n_pixels,lo,hi,min_spacing);
      else if (pixels != n_pixels) changeSize(min_spacing,n_pixels);
      else if (fracs==null || locs==null || values==null) refresh();
      else if (fracs!=null && (fracs[0]!=lo || Math.abs(hi-fracs[fracs.length-1])<Math.max(MIN_INTERVAL,hi*MIN_INTERVAL))) refresh();
    }
  }
    
  public class TickMarks extends JPanel
  {
    public Orientation orient;
    public Ticker tick;
    protected Ticker old_tick;
    protected int old_span;
    public TickMarks(Orientation o,Ticker t)
    {
      super(true);
      orient = o;
      tick = t;
      old_tick = new Ticker(t);
      old_span = -1;
      setMinimumSize(new Dimension( (o==Orientation.down)?20:5 , (o==Orientation.down)?5:20 ));
      setPreferredSize(new Dimension( (o==Orientation.down)?20:5 , (o==Orientation.down)?5:20 ));
    }
    public int minTickSpacing() { return (orient==Orientation.down) ? 45 : 15; }
    public void paintComponent(Graphics g)
    {
      super.paintComponent(g);
      int H = getHeight();
      int W = getWidth();
      Insets inset = getInsets();
      int span = (orient==Orientation.down) ? W-(inset.right+inset.left) : H-(inset.top+inset.bottom);
      tick.makeReady( span , minTickSpacing() );
      if (orient==Orientation.down)
      {
        for (int x : tick.locs)
        {
          g.drawLine(x+inset.left,inset.top,x+inset.left,H-inset.bottom);
        }
        g.drawLine(inset.left,inset.top,W-inset.right,inset.top);
      }
      else
      {
        for (int y : tick.locs)
        {
          g.drawLine(inset.left,inset.top+(span-y-1),H-inset.right,inset.top+(span-y-1));
        }
        if (orient==Orientation.left) g.drawLine(W-inset.right-1,inset.top,W-inset.right-1,H-inset.bottom);
        else g.drawLine(inset.left,inset.top,inset.left,H-inset.bottom);
      }
    }
  }
  
  public class Axis extends JPanel
  {
    public final String axis_label;

    public Orientation orient;
    public Ticker tick;
    
    int shift;
    int lastloc;

    protected Ticker old_tick;
    protected int old_span;

    protected String tick_format;
    protected Font label_font;
    protected Font tick_font;
    protected TextLayout label_drawer;
    protected TextLayout[] major_ticklabels;
    
    public Axis(String label,Orientation o,Ticker tk)
    {
      super(true);
      axis_label = label;
      orient = o;
      tick = tk;
      
      shift = 0;
      lastloc = 0;
      
      old_tick = new Ticker(tk);
      old_span = -1;
      tick_format = "%.2f";
      label_font = new Font(Font.SERIF,Font.BOLD,12);
      tick_font = new Font(Font.SERIF,Font.PLAIN,12);
      major_ticklabels = new TextLayout[0];
      setMinimumSize(new Dimension( (o==Orientation.down)?10*label.length():60 , (o==Orientation.down)?30:10*label.length() ));
      setPreferredSize(new Dimension( (o==Orientation.down)?10*label.length():60 , (o==Orientation.down)?30:10*label.length() ));
      
      addMouseListener(
        new MouseAdapter()
        {
          public void mousePressed(MouseEvent me) { lastloc = (orient==Orientation.down) ? me.getPoint().x : me.getPoint().y; }
          public void mouseReleased(MouseEvent me)
          {
            int marks_shifted = Math.round(shift / (tick.pixels/(float)(tick.locs.length-1)));
            shift = 0;
            if (marks_shifted != 0)
            {
              double delta = marks_shifted * (tick.hi-tick.lo)/(tick.locs.length-1);
              if (orient==Orientation.down) delta = -delta;
              tick.lo += delta;
              tick.hi += delta;
              tick.fixHigh();
              tick.refresh();
              gui.repaint();
            }
            else repaint();
          }
        }
      );
      addMouseMotionListener(
        new MouseMotionAdapter()
        {
          public void mouseDragged(MouseEvent me)
          {
            if (orient==Orientation.down) shift += me.getPoint().x - lastloc;
            else shift += me.getPoint().y - lastloc;
            repaint();
            if (orient==Orientation.down) lastloc = me.getPoint().x;
            else lastloc = me.getPoint().y;
          }
        }
      );
      addMouseWheelListener(
        new MouseWheelListener()
        {
          public void mouseWheelMoved(MouseWheelEvent mwe)
          {
            if (mwe.getWheelRotation()==0) return;
            tick.changeZoom( mwe.getWheelRotation() );
            gui.repaint();
          }
        }
      );
    }
    
    public int minTickSpacing() { return (orient==Orientation.down) ? 45 : 15; }
    
    public void paintComponent(Graphics g)
    {
      super.paintComponent(g);
      int H = getHeight();
      int W = getWidth();
      Insets I = getInsets();
      int span = (orient==Orientation.down) ? W-(I.right+I.left) : H-(I.top+I.bottom);
      if (span!=old_span || !tick.isSame(old_tick))
      {
        tick.makeReady(span , minTickSpacing());
        old_tick.imitate(tick);
        old_span = span;
        major_ticklabels = new TextLayout[ tick.values.length ];
      }
      
      Graphics2D g2 = (Graphics2D)g;
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
      AffineTransform old_transform = g2.getTransform();
      
      label_drawer = new TextLayout(axis_label,label_font,g2.getFontRenderContext());
      double delta = (tick.values.length>1) ? ((tick.hi-tick.lo)/(tick.values.length-1)) : 1e-3;
      int digits = (int)Math.max(0 , -Math.floor(Math.log10(delta*(1+1e-4))));
      if ( Math.abs( delta - Math.pow(10,-digits)*Math.rint( delta*Math.pow(10,digits)) )/delta > 0.01 ) digits++;
      tick_format = "%." + digits + "f";
      for (int i=0 ; i<major_ticklabels.length ; i++)
      {
        major_ticklabels[i] = new TextLayout( String.format(tick_format,tick.values[i]) , tick_font , g2.getFontRenderContext() );
      }
      
      Rectangle2D bounds;
      float actual_center_x,actual_center_y;
      double desired_center_x,desired_center_y;
      double max_tickwidth = 0.0;
      
      for (int i=0 ; i<major_ticklabels.length ; i++)
      {
        if (span-tick.locs[i]<8) continue;
        bounds = major_ticklabels[i].getBounds();
        
        if (bounds.getWidth()>max_tickwidth) max_tickwidth = bounds.getWidth();
        
        actual_center_x = (float)bounds.getCenterX();
        actual_center_y = (float)bounds.getCenterY();
        
        if (orient==Orientation.left) desired_center_x = W-(I.right+2+0.5*bounds.getWidth());
        else if (orient==Orientation.right) desired_center_x = I.left+2+0.5*bounds.getWidth();
        else
        {
          desired_center_x = I.left + tick.locs[i] + shift;
          if (desired_center_x - 0.5*bounds.getWidth() < I.left) desired_center_x = I.left + 0.5*bounds.getWidth();
          else if (desired_center_x + 0.5*bounds.getWidth() > W-I.right-1) desired_center_x = W-I.right-1-0.5*bounds.getWidth();
        }
        
        if (orient==Orientation.down) desired_center_y = I.top + 2 + 0.5*bounds.getHeight();
        else
        {
          desired_center_y = I.top + (span - tick.locs[i]) + shift;
          if (desired_center_y - 0.5*bounds.getHeight() < I.top) desired_center_y = I.top + 0.5*bounds.getHeight();
          else if (desired_center_y + 0.5*bounds.getHeight() > H-I.bottom) desired_center_y = H-I.bottom-0.5*bounds.getHeight(); 
        }
        
        major_ticklabels[i].draw(g2 , (float)desired_center_x-actual_center_x , (float)desired_center_y-actual_center_y);
      }
      
      bounds = label_drawer.getBounds();
      actual_center_x = (float)bounds.getCenterX();
      actual_center_y = 0;
      if (orient==Orientation.down)
      {
        desired_center_x = I.left + 0.5*(W-(I.right+I.left));
        desired_center_y = H - (I.bottom+2+label_drawer.getDescent());
      }
      else
      {
        if (orient==Orientation.left) desired_center_x = Math.max( 2 + label_drawer.getAscent() , W-(I.right+2+max_tickwidth+1+label_drawer.getDescent()) );
        else desired_center_x = Math.min( W-(I.right+2+label_drawer.getAscent()) , 2+max_tickwidth+1+label_drawer.getDescent() ); 
        desired_center_y = I.top + 0.5*(H-(I.top+I.bottom));
      }
      
      g2.translate(desired_center_x,desired_center_y);
      if (orient==Orientation.left) g2.rotate(-0.5*Math.PI);
      else if (orient==Orientation.right) g2.rotate(0.5*Math.PI);
      label_drawer.draw(g2,-actual_center_x,-actual_center_y);
      g2.setTransform(old_transform);     
    }
  }
  
  protected class ColorCheckboxIcon extends javax.swing.plaf.metal.MetalCheckBoxIcon
  {
    Color bg;
    public ColorCheckboxIcon(Color c) { super(); bg = c; }
    public void drawCheck(Component c,Graphics g,int x,int y)
    {
      Color old = g.getColor();
      g.setColor(bg);
      super.drawCheck(c,g,x,y);
      g.setColor(old);
    }
  }
  
  public interface FunctionData
  {
    public String getTitle();
    public boolean isSingleValued();
    public boolean isSimilarClass(FunctionData fd);
    public int length();
    public float[] limits(float[] fa);
    public float timeOf(int i);
    public int indexOf(float t);
    public float value(int i);
    public float value(float t);
    public float[] value(int i,int j,float[] fa);
    public float[] value(float t0,float t1,float[] fa);
  }
  
  protected class Plotter extends JPanel
  {
    int old_x_span,old_y_span;
    Ticker old_x,old_ys[];
    FunctionData[] functions;
    int[] plot_order;
    Path2D[] paths;
    boolean[] fills;
    
    public Plotter(FunctionData[] fs)
    {
      old_x_span = old_y_span = -1;
      old_x = new Ticker(x_tick);
      old_ys = new Ticker[y_ticks.length];
      for (int i=0 ; i<y_ticks.length ; i++) old_ys[i] = new Ticker(y_ticks[i]);
      functions = fs;
      plot_order = new int[functions.length];
      int n=0;
      for (int i=0 ; i<functions.length ; i++) if (!functions[i].isSingleValued()) plot_order[n++] = i+1;
      for (int i=0 ; i<functions.length ; i++) if (functions[i].isSingleValued()) plot_order[n++] = i+1;
      paths = new Path2D[functions.length];
      fills = new boolean[functions.length];
      for (int i=0; i<functions.length ; i++)
      {
        paths[i] = new Path2D.Float();
        fills[i] = false;
      }
      setPreferredSize( new Dimension(400,200) );
    }
    
    @Override public void paintComponent(Graphics g)
    {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D)g;
      int H = getHeight();
      int W = getWidth();
      
      boolean recalc = false;
      if (old_x_span != W || old_y_span != H)
      {
        old_x_span = W;
        old_y_span = H;
        recalc = true;
      }
      if (!old_x.isSame(x_tick))
      {
        old_x.imitate(x_tick);
        recalc = true;
      }
      for (int i=0 ; i<plot_order.length ; i++)
      {
        if (plot_order[i]<0 && y_onoff[ -plot_order[i]-1 ].isSelected())
        {
          plot_order[i] = -plot_order[i];
          recalc = true;
        }
        else if (plot_order[i]>0 && !y_onoff[ plot_order[i]-1 ].isSelected())
        {
          plot_order[i] = -plot_order[i];
          recalc = true;
        }
        if (plot_order[i]>0)
        {
          if (!old_ys[plot_order[i]-1].isSame( y_ticks[plot_order[i]-1] ))
          {
            old_ys[plot_order[i]-1].imitate( y_ticks[plot_order[i]-1] );
            recalc = true;
          }
        }
      }
      
      if (recalc)
      {
        float f,f_old;
        LinkedList<Point2D> backpath = new LinkedList<Point2D>();
        for (int i=0 ; i<functions.length ; i++)
        {
          if (!y_onoff[i].isSelected()) continue;
          fills[i] = false;
          backpath.clear();
          paths[i].reset();
          
          int j0,j1,j;
          j0 = functions[i].indexOf( (float)x_tick.lo );
          if (j0>0) j0--;
          else if (j0<0) j0=0;
          j1 = functions[i].indexOf( (float)x_tick.hi );
          if (j1<0) j1 = functions[i].length()-1;
          else if (j1<functions[i].length()-1) j1++;
          
          float x;
          float y,yy;
          float[] fa = new float[2];
          float dt = (float)(x_tick.hi - x_tick.lo);
          float range = (float)(y_ticks[i].hi - y_ticks[i].lo);
          if (j1-j0 < W)
          {
            if (functions[i].isSingleValued())
            {
              f_old = Float.NaN;
              for (j=j0 ; j<=j1 ; j++)
              {
                f = functions[i].value(j);
                if (!Float.isNaN(f))
                {
                  x = W*(functions[i].timeOf(j)-(float)x_tick.lo)/dt;
                  y = (H-H*((f-(float)y_ticks[i].lo)/range));
                  if (Float.isNaN(f_old)) paths[i].moveTo(x,y);
                  else paths[i].lineTo(x,y);
                }
                f_old = f;
              }
            }
            else
            {
              fills[i] = true;
              f_old = Float.NaN;
              for (j=j0 ; j<=j1 ; j++)
              {
                fa = functions[i].value(j,j,fa);
                if (Float.isNaN(f_old))
                {
                  while (!backpath.isEmpty())
                  {
                    paths[i].lineTo( backpath.peek().getX() , backpath.peek().getY() );
                    backpath.pop();
                  }
                }
                if (!Float.isNaN(fa[0]))
                {
                  x = W*(functions[i].timeOf(j)-(float)x_tick.lo)/dt;
                  y = (H-H*((fa[1]-(float)y_ticks[i].lo)/range));
                  yy = (H-H*((fa[0]-(float)y_ticks[i].lo)/range));
                  if (Float.isNaN(f_old))
                  {
                    paths[i].moveTo(x,y);
                    backpath.push( new Point2D.Float(x,y) );
                    backpath.push( new Point2D.Float(x,yy) );
                  }
                  else
                  {
                    paths[i].lineTo(x,y);
                    backpath.push( new Point2D.Float(x,yy) );
                  }
                }
                f_old = fa[0];
              }
              while (!backpath.isEmpty())
              {
                paths[i].lineTo( backpath.peek().getX() , backpath.peek().getY() );
                backpath.pop();
              }
            }
          }
          else
          {
            fills[i] = true;
            f_old = Float.NaN;
            for (j=0 ; j<W ; j++)
            {
              f = (float)x_tick.lo + (dt*j)/W;
              fa = functions[i].value( f , f + dt/W , fa );
              if (Float.isNaN(f_old))
              {
                while (!backpath.isEmpty())
                {
                  paths[i].lineTo( backpath.peek().getX() , backpath.peek().getY() );
                  backpath.pop();
                }
              }
              if (!Float.isNaN(fa[0]))
              {
                x = j;
                y = (H - H*(fa[1] - (float)y_ticks[i].lo)/range);
                yy = (H - H*(fa[0] - (float)y_ticks[i].lo)/range);
                if (Float.isNaN(f_old))
                {
                  paths[i].moveTo(x,y);
                  backpath.push( new Point2D.Float(x,y) );
                  backpath.push( new Point2D.Float(x,yy) );
                }
                else
                {
                  paths[i].lineTo(x,y);
                  backpath.push( new Point2D.Float(x,yy) );
                }
              }
              f_old = fa[0];
            }
            while (!backpath.isEmpty())
            {
              paths[i].lineTo( backpath.peek().getX() , backpath.peek().getY() );
              backpath.pop();
            }
          }
          for (j=j0 ; j<j1 ; j++)
          {
            
          }
        }
      }
      
      g.setColor(new Color(48,48,48));
      if (x_tick.locs!=null) for (int i : x_tick.locs) g.drawLine(i,0,i,H);
      if (y_ticks[0].locs!=null) for (int i : y_ticks[0].locs) g.drawLine(0,H-i,W,H-i);
      
      for (int i : plot_order)
      {
        if (i<0) continue;
        i--;
        g2.setColor(Colorer.colors[i]);
        g2.draw(paths[i]);
        if (fills[i])
        {
          if (!functions[i].isSingleValued())
          {
            g2.setColor(new Color( Colorer.colors[i].getRed() , Colorer.colors[i].getGreen() , Colorer.colors[i].getBlue() , 64 ));
          }
          g2.fill(paths[i]);
        }
      }
    }
  }
  
  protected class MainGUI extends JFrame
  {
    JPanel plot_area;
    TickMarks lower_ticks;
    JPanel lower_axis;
    TickMarks left_ticks;
    TickMarks right_ticks;
    JPanel[] left_axes;
    JPanel[] right_axes;
    JCheckBox[] right_checks;
    Plotter plot;
    public MainGUI(String title)
    {
      super((title==null) ? "" : title);
      getContentPane().setBackground(Color.black);
      
      lower_ticks = new TickMarks(Orientation.down,x_tick);
      lower_ticks.setBackground(Color.black);
      lower_ticks.setForeground(Color.white);
      lower_axis = new Axis("Time (seconds)",Orientation.down,x_tick);
      lower_axis.setBackground(Color.black);
      lower_axis.setForeground(Color.lightGray);
      
      left_ticks = new TickMarks(Orientation.left,y_ticks[0]);
      left_ticks.setBackground(Color.black);
      left_ticks.setForeground(Color.white);
      if (y_ticks.length>1)
      {
        right_ticks = new TickMarks(Orientation.right,y_ticks[0]);
        right_ticks.setBackground(Color.black);
        right_ticks.setForeground(Color.white);
      }

      Axis master_y_axis = null;
      left_axes = new JPanel[(1+y_ticks.length)/2];
      right_axes = new JPanel[y_ticks.length/2];
      y_onoff = new JCheckBox[ y_ticks.length ];
      for (int i=0;i<left_axes.length;i++)
      {
        y_onoff[2*i] = new JCheckBox( new ColorCheckboxIcon(Colorer.colors[2*i]) );
        y_onoff[2*i].setSelected(true);
        y_onoff[2*i].setForeground(Color.black);
        y_onoff[2*i].setBackground(Color.black);
        if (master_y_axis==null)
        {
          master_y_axis = new Axis(funcs[0].getTitle(),Orientation.left,y_ticks[0]);
          left_axes[0] = master_y_axis;
        }
        else left_axes[i] = new Axis(funcs[2*i].getTitle(),Orientation.left,y_ticks[2*i]);
        left_axes[i].setBackground(Color.black);
        left_axes[i].setForeground(Colorer.colors[2*i]);
        if (2*i+1 < y_ticks.length)
        {
          y_onoff[2*i+1] = new JCheckBox( new ColorCheckboxIcon(Colorer.colors[2*i+1]) );
          y_onoff[2*i+1].setSelected(true);
          y_onoff[2*i+1].setBackground(Color.black);
          y_onoff[2*i+1].setForeground(Color.black);
          right_axes[i] = new Axis(funcs[2*i+1].getTitle(),Orientation.right,y_ticks[2*i+1]);
          right_axes[i].setBackground(Color.black);
          right_axes[i].setForeground(Colorer.colors[2*i+1]);
        }
      }
      
      GridBagLayout layout = new GridBagLayout();
      getContentPane().setLayout(layout);
      
      GridBagConstraints c = new GridBagConstraints();
      c.anchor = GridBagConstraints.NORTHWEST;
      c.fill = GridBagConstraints.VERTICAL;
      c.weighty = 1.0;
      for (int i=left_axes.length-1;i>=0;i--)
      {
        layout.setConstraints(left_axes[i],c);
        getContentPane().add(left_axes[i]);
      }
      layout.setConstraints(left_ticks,c);
      getContentPane().add(left_ticks);
      
      plot = new Plotter(funcs);
      plot.setBackground(Color.black);
      c.weightx = 1.0;
      c.fill = GridBagConstraints.BOTH;
      if (right_axes.length==0) c.gridwidth = GridBagConstraints.REMAINDER;
      layout.setConstraints(plot,c);
      getContentPane().add(plot);
      
      c.fill = GridBagConstraints.VERTICAL;
      c.weightx = 0.0;
      if (right_axes.length>0)
      {
        layout.setConstraints(right_ticks,c);
        getContentPane().add(right_ticks);
        for (int i=0;i<=right_axes.length-1;i++)
        {
          if (i==right_axes.length-1) c.gridwidth=GridBagConstraints.REMAINDER;
          layout.setConstraints(right_axes[i],c);
          getContentPane().add(right_axes[i]);
        }
      }
      
      c.weightx = 0.0;
      c.weighty = 0.0;
      c.fill = GridBagConstraints.NONE;
      c.anchor = GridBagConstraints.NORTH;
      c.gridwidth = 1;
      for (int i=left_axes.length-1 ; i>=0 ; i--)
      {
        if (i==0) c.gridwidth=2;
        layout.setConstraints(y_onoff[2*i],c);
        getContentPane().add(y_onoff[2*i]);
      }
      
      c.weightx = 1.0;
      c.weighty = 0.0;
      c.gridheight = 2;
      c.gridwidth = (right_axes.length>0) ? 1 : GridBagConstraints.REMAINDER;
      c.fill = GridBagConstraints.HORIZONTAL;
      JPanel blankC = new JPanel();
      blankC.setBackground(Color.black);
      blankC.setLayout(new BoxLayout(blankC,BoxLayout.Y_AXIS));
      blankC.add(lower_ticks);
      blankC.add(lower_axis);
      layout.setConstraints(blankC,c);
      getContentPane().add(blankC);
      c.weightx = 0.0;
      c.fill = GridBagConstraints.NONE;
      c.anchor = GridBagConstraints.NORTH;
      for (int i=0 ; i<=right_axes.length-1 ; i++)
      {
        if (i==right_axes.length-1) c.gridwidth=GridBagConstraints.REMAINDER;
        else if (i==0) c.gridwidth=2;
        else c.gridwidth=1;
        layout.setConstraints(y_onoff[2*i+1],c);
        getContentPane().add(y_onoff[2*i+1]);
      }
      
      pack();
      setLocation(100,100);

      for (JCheckBox jcb : y_onoff)
      {
        jcb.addChangeListener(
          new ChangeListener()
          {
            public void stateChanged(ChangeEvent ce)
            {
              plot.repaint();
            }
          }
        );
      }
    }
  }

  Ticker x_tick;
  Ticker[] y_ticks;
  JCheckBox[] y_onoff;
  FunctionData[] funcs;
  MainGUI gui;
  public Graphs(FunctionData[] functions)
  { 
    float x0 = 0.0f;
    float x1 = 0.0f;
    for(FunctionData fd : functions)
    {
      if (fd.timeOf(0) < x0) x0 = fd.timeOf(0);
      else if (fd.timeOf( fd.length()-1 ) > x1) x1 = fd.timeOf( fd.length()-1 );
    }
    x_tick = new Ticker(x0,x1);
    y_ticks = new Ticker[functions.length];
    funcs = functions;
    y_onoff = null;
    for (int i=0;i<functions.length;i++)
    {
      float[] fa = funcs[i].limits(null);
      y_ticks[i] = new Ticker(fa[0],fa[1]);
      if (i>0) y_ticks[i].leader = y_ticks[0];
    }
    
    // Looks better if functions plotting different metrics on the same data use the same y-axis range
    for (int i=0 ; i<functions.length ; i++)
    {
      for (int j=i+1 ; j<functions.length ; j++)
      {
        if (funcs[i].isSimilarClass(funcs[j]))
        {
          y_ticks[i].lo = Math.min(y_ticks[i].lo,y_ticks[j].lo);
          y_ticks[i].hi = Math.max(y_ticks[i].hi,y_ticks[j].hi);
          y_ticks[j] = y_ticks[i];
        }
      }
    }
    gui = null;
  }


  public void run(String title)
  {
    gui = new MainGUI(title);
    gui.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    gui.setLocationByPlatform(false);
    gui.setLocation(100,100);
    gui.setVisible(true);
  }
}

