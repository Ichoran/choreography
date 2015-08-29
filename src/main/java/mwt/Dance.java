/* Dance.java - Computations on individual moving objects
 * Copyright 2010, 2011 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015 Calico Life Sciences LLC (authored by Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */
 
package mwt;

import java.io.*;
import java.util.*;

import mwt.numerics.*;
import mwt.plugins.*;

public class Dance
{
  public static final double ln_four = 1.3862943611198906;
  
  public static final float derivative_noise_factor = 0.88622692f;  // sqrt(pi)/2
  public static final float curvature_noise_factor = 0.511663354f;  // sqrt(pi/3)/2
  
  public static final int ERROR_CONVERGE_ITERATIONS = 12;
  public static final double ERROR_CONVERGE_FRACTION = Math.sqrt(0.001);

  
  public int ID;
  public Choreography chore;
  
  public boolean has_holes;
  public int first_frame;
  public int last_frame;
  public boolean shadow_avoided;
  public float ignored_dt;
  public Vec2F ignored_start;
  public float ignored_travel;
  
  public ReceptiveField[] attend;
  public ReceptiveField[] shun;
  
  public int area[];
  public Vec2F centroid[];
  public Vec2F bearing[];
  public Vec2F extent[];
  public Vec2I circles[];
  public Spine spine[];
  public Outline outline[];
  public Vector<Integer> origins;
  public Vector<Integer> fates;
  
  public float quantity[];
  public float quantity_max;
  public float quantity_min;
  public float quantity_noise;
  public float endpoint_angle_fraction;
  
  public Statistic body_area;
  public Statistic body_length;
  public Statistic body_width;
  public Statistic body_aspect;
  public Statistic body_spine;
  public Statistic noise_estimate;
  public Statistic directional_bias;
  public Fitter global_position_noise;
  public Style[] segmentation;
  public DirectionSet directions;
  
  //public Statistic aheadness;
  public LinkedList<Vec2I> invert_list;
  
  public Fractionator multiscale_x;
  public Fractionator multiscale_y;
  public Fractionator multiscale_q;
  public QuadRanger ranges_xy;
  
  public float loaded_jitter;
  
  public class Preloaded {
    boolean already = false;
    protected Float jittery = null;
    float jit() {
      if (jittery==null) return 0.0f;
      else return jittery.floatValue();
    }
    void setJit() {
      if (jittery==null) jittery = new Float( estimateNoise() );
      loaded_jitter = jittery.floatValue();
    }
  };

  // Egad, is this horrible or what?  Map from enum, anyone?!
  Preloaded loaded_time = new Preloaded();
  Preloaded loaded_frame = new Preloaded();
  Preloaded loaded_constant = new Preloaded();
  Preloaded loaded_area = new Preloaded();
  Preloaded loaded_speed = new Preloaded();
  Preloaded loaded_angular = new Preloaded();
  Preloaded loaded_length = new Preloaded();
  Preloaded loaded_width = new Preloaded();
  Preloaded loaded_aspect = new Preloaded();
  Preloaded loaded_midline = new Preloaded();
  Preloaded loaded_outlinewidth = new Preloaded();
  Preloaded loaded_kink = new Preloaded();
  Preloaded loaded_bias = new Preloaded();
  Preloaded loaded_path = new Preloaded();
  Preloaded loaded_curve = new Preloaded();
  Preloaded loaded_dirchange = new Preloaded();
  Preloaded loaded_phaseadvance = new Preloaded();
  Preloaded loaded_x = new Preloaded();
  Preloaded loaded_y = new Preloaded();
  Preloaded loaded_vx = new Preloaded();
  Preloaded loaded_vy = new Preloaded();
  Preloaded loaded_theta = new Preloaded();
  Preloaded loaded_crab = new Preloaded();
  Preloaded loaded_qxfw = new Preloaded();
  Preloaded loaded_stim1 = new Preloaded();
  Preloaded loaded_stim2 = new Preloaded();
  Preloaded loaded_stim3 = new Preloaded();
  Preloaded loaded_stim4 = new Preloaded();
  Preloaded[] loaded_custom = new Preloaded[0];

  
  // Custom exceptions
  public class DancerFileException extends IOException { DancerFileException(String s) { super(s); } }
  
  
  // Custom enums
  public enum Metric { DIST,DISTX,DISTY,ANGLE,CRAB; }


  // Holds spine data
  public class RawSpine implements Spine {
    short[] x;
    short[] y;
    boolean orientationKnown = false;
    public RawSpine(LinkedList<Vec2S> points) {
       x = new short[points.size()];
      y = new short[points.size()];
      int i = 0;
      for (Vec2S p : points) { x[i] = p.x; y[i] = p.y; i++; }
    }
    public int size() { return x.length; }
    public boolean quantized() { return true; }
    public boolean oriented() { return orientationKnown; }
    public void headfirstKnown(boolean b) {}
    public void flip() {
      short temp;
      int n = x.length/2;
      for (int i=0,j=x.length-1 ; i<n ; i++,j--) {
        temp = x[i];
        x[i] = x[j];
        x[j] = temp;
        temp = y[i];
        y[i] = y[j];
        y[j] = temp;
      }
    }
    public Vec2S get(int i) { return new Vec2S(x[i],y[i]); }
    public Vec2S get(int i, Vec2S buf) { return buf.eq(x[i],y[i]); }
    public Vec2F get(int i, Vec2F buf) { return buf.eq(x[i],y[i]); }
    public float width(int i) { return Float.NaN; }
  }
  
  
  // Converts bitwise packed outlines into vectors
  public class RawOutline implements Outline
  {
    Vec2I loc;
    int length;
    byte[] bits;
    Vec2S[] points;
    boolean pre_verified;
    public RawOutline(int x,int y,int l,String o) {
      loc = new Vec2I(x,y);
      length = l;
      bits = new byte[o.length()];
      for (int i=0 ; i<bits.length ; i++) bits[i] = (byte)(o.charAt(i) - '0');
      points = null;
    }
    public RawOutline(Vec2S[] o) {
      loc = null;
      length = o.length;
      bits = null;
      points = o;
    }
    public int size() { return length; }
    public boolean quantized() { return true; }
    public void compact() { if (bits!=null && points!=null) points=null; }
    public Vec2S get(int i, Vec2S buf) { return buf.eq(unpack(true)[i]); }
    public Vec2F get(int i, Vec2F buf) { return buf.eq(unpack(true)[i]); }

    void bip(int a,int n,Vec2S[] pts) {
      switch (a) {
        case 0: pts[n].x--; break;
        case 1: pts[n].x++; break;
        case 2: pts[n].y--; break;
        case 3: pts[n].y++; break;
      }
    }
    public Vec2S[] unpackBits(Vec2S[] storage) {
      if (bits==null) return null;
      Vec2S[] pts = (storage==null||storage.length<length) ? new Vec2S[length] : storage;
      for (int i=0 ; i<length ; i++) if (pts[i]==null) pts[i] = new Vec2S();
      int a,n,b,nb;
      pts[0].eq( loc );
      n = 1;
      for (int i=0;i<bits.length;i++) {
        b = bits[i];
        if (n>=length) break;
        pts[n].eq( pts[n-1] );
        bip((b>>4)&0x3,n++,pts);
        if (n>=length) break;
        pts[n].eq( pts[n-1] );
        bip((b>>2)&0x3,n++,pts);
        if (n>=length) break;
        pts[n].eq( pts[n-1] );
        bip(b&0x3,n++,pts);
      }
      return pts;
    }
    public Vec2S[] unpack(boolean store) {
      if (bits!=null && (points==null || !store)) {
        Vec2S[] pts = unpackBits(null);
        if (store) points=pts;
        return pts;
      }
      else return points;
    }
    public Vec2S[] unpack(Vec2S[] storage) {
      if (points!=null) {
        if (storage==null || storage.length<points.length) storage = new Vec2S[points.length];
        
        int i;
        for (i=0 ; i<points.length ; i++) {
          if (storage[i]==null) storage[i] = points[i].copy();
          else storage[i].eq( points[i] );
          if (i>0 && points[i].dist2(points[i-1])>=4) { i=-1; break; }
        }
        if (i<points.length) // Screwy old method for finding outline--try linear interpolation at least
        {
          int pixelmoves = 0;
          for (i=1 ; i<points.length ; i++) {
            pixelmoves += Math.abs(points[i].x - points[i-1].x) + Math.abs(points[i].y - points[i-1].y);
          }
          pixelmoves += Math.abs(points[0].x - points[i-1].x) + Math.abs(points[0].y - points[i-1].y);
          if (storage.length < pixelmoves) storage = new Vec2S[pixelmoves];
          Vec2S p = new Vec2S();
          Vec2S q = new Vec2S();
          int j,k,n;
          n = 0;
          for (i=1 ; i<=points.length ; i++) {
            j = i-1;
            k = (i<points.length)?i:0;
            p.eq( points[k] ).eqMinus( points[j] );
            q.x = q.y = (short)0;
            while (q.x!=p.x || q.y!=p.y) {
              if (storage[n]==null) storage[n] = points[j].copy(); else storage[n].eq(points[j]);
              storage[n].eqPlus(q);
              n++;
              if (q.y==p.y) q.x += (q.x<p.x) ? 1 : -1;
              else if (q.x==p.x) q.y += (q.y<p.y) ? 1 : -1; 
              else {
                if ((Math.abs(q.x)+1)*Math.abs(p.y) < (Math.abs(q.y)+1)*Math.abs(p.x)) q.x += (q.x<p.x) ? 1 : -1;
                else q.y += (q.y<p.y) ? 1 : -1;
              }
            }
          }
          points = new Vec2S[n];
          for (i=0;i<n;i++) points[i] = storage[i].copy();
          length = n;
        }
        return storage;
      }
      else return unpackBits(storage);
    }
    public Vec2F[] unpack(Vec2F[] storage) {
      if (storage==null || storage.length < length) storage = new Vec2F[length];
      Vec2S[] pts = unpack(true);
      for (int i=0 ; i<pts.length ; i++) {
        if (storage[i]==null) storage[i] = pts[i].toF();
        else storage[i].eq(pts[i]);
      }
      return storage;
    }
    public boolean verify() {
      if (bits==null && points!=null) return true;
      if ((length+2)/3 != bits.length) return false;
      for (int i=0;i<bits.length;i++) if ( (bits[i]&0x3F)!=bits[i] ) return false;
      return true;
    }
  }
  
  
  // Reading individual lines of data
  public class DanceLine
  {
    int index;
    int area;
    float time;  // Only used if we're going to ignore this data
    Vec2F centroid;
    Vec2F bearing;
    Vec2F extent;
    Spine spine;
    Outline outline;
    String[] tokens;
    
    public DanceLine() { index=area=-1; centroid=null; bearing=null; extent=null; spine=null; outline=null; tokens=null; }
    
    public boolean isSame(String[] tok , Choreography.FrameMap[] valid)
    {
      if (tokens==null) return false;
      if (tok==null || tok.length<10) return true;
      int i;
      try { i = Integer.parseInt(tok[0]) - 1; }
      catch (NumberFormatException nfe) { return false; }
      if (i<0) i = 0;
      else if (i>=valid.length) i = valid.length-1;
      return index==valid[i].actual;
    }
    
    public boolean parseLine(String[] tok , Choreography.FrameMap[] valid) throws NumberFormatException
    {
      tokens = tok;
      if (tokens==null || tokens.length<10) return false;
      int i;
      short a,b;
      float x,y;
      
      // Read index number
      i = Integer.parseInt( tokens[0] ) - 1;
      if (i<0) i = 0;
      if (i>=valid.length) i = valid.length-1;
      index = valid[i].actual;
      
      // tokens[1] is time--redundant unless we're going to quit early, since we can figure that out with the index number
      if (!valid[i].okay) time = Float.parseFloat( tokens[1] );
      
      // tokens[2] and tokens[3] are the centroid
      x = Float.parseFloat( tokens[2] );
      y = Float.parseFloat( tokens[3] );
      centroid = new Vec2F(x,y);

      if ( !valid[i].okay ) return false;  // Don't parse any more if we're supposed to ignore it
      
      // tokens[4] is the area; sometimes comes out spuriously as 0, but we just have to record it
      area = Integer.parseInt( tokens[4] );
      
      // tokens[5] and tokens[6] are the bearing
      x = Float.parseFloat( tokens[5] );
      y = Float.parseFloat( tokens[6] );
      bearing = new Vec2F(x,y);
      
      // tokens[7] is width measured as variance--skip it
      
      // tokens[8] is the length and tokens[9] is the width (measured along and across the bearing direction)
      x = Float.parseFloat( tokens[8] );
      y = Float.parseFloat( tokens[9] );
      extent = new Vec2F(x,y);
      
      
      // Is there a spine?
      i = 10;
      if (i>=tokens.length) return true;
      if (tokens[i].equals("%"))
      {
        LinkedList<Vec2S> spine_builder = new LinkedList<Vec2S>();
        for (i++ ; i+1<tokens.length ; i+=2)
        {
          if (tokens[i].startsWith("%")) break;
          if (tokens[i+1].startsWith("%")) { i++; break; }
          a = Short.parseShort( tokens[i] );
          b = Short.parseShort( tokens[i+1] );
          spine_builder.add( new Vec2S(a,b) );
        }
        if (spine_builder.size()>1) spine = new RawSpine(spine_builder);
      }
      
      // Is there an outline?
      if (i>=tokens.length) return true;
      if (tokens[i].equals("%%") && i+4 < tokens.length)
      {
        try {
          int ox = Integer.parseInt(tokens[i+1]);
          int oy = Integer.parseInt(tokens[i+2]);
          int oL = Integer.parseInt(tokens[i+3]);
          String os = tokens[i+4];
          RawOutline raw =  new RawOutline(ox,oy,oL,os);
          if (!raw.verify()) throw new NumberFormatException();
          outline = raw;
        }
        catch (NumberFormatException nfe) {
          // This is the method for reading old outlines--try it if the other one fails
          LinkedList<Vec2S> edge = new LinkedList<Vec2S>();
          for (i++ ; i+1<tokens.length ; i+=2)
          {
            a = Short.parseShort( tokens[i] );
            b = Short.parseShort( tokens[i+1] );
            edge.add( new Vec2S(a,b) );
          }
          if (edge.size()>1)
          {
            Vec2S[] ol = new Vec2S[edge.size()];
            int n=0;
            for (Vec2S v : edge) ol[n++] = v;
            outline = new RawOutline(ol);
          }
        }
      }
      
      return true;  // Line was in-range, all is okay
    }
  }
  
  // Handle regions of interest inside or outside of which we reject data
  public static abstract class ReceptiveField
  {
    public static ReceptiveField makeField(String s)
    {
      String[] ss = s.split(",");
      if (ss.length==3)
      {
        Vec2F v = null;
        float r = 0.0f;
        try
        { 
          r = Float.parseFloat(ss[2]);
          v = new Vec2F( Float.parseFloat(ss[0]) , Float.parseFloat(ss[1]) );
        }
        catch (NumberFormatException nfe) { return null; }
        return new CircularField(v,r);
      }
      else if (ss.length==4)
      {
        Vec2F v0 = null;
        Vec2F v1 = null;
        try
        {
          v0 = new Vec2F( Float.parseFloat(ss[0]) , Float.parseFloat(ss[1]) );
          v1 = new Vec2F( Float.parseFloat(ss[2]) , Float.parseFloat(ss[3]) );
        }
        catch (NumberFormatException nfe) { return null; }
        return new RectangularField(v0,v1);
      }
      else return null;
    }
    public abstract boolean includes(Vec2F v);
  }
  public static class CircularField extends ReceptiveField
  {
    Vec2F center;
    float radius;
    public CircularField(Vec2F c,float r) { center = new Vec2F(c); radius = r; }
    public boolean includes(Vec2F v)
    {
      Vec2F u = new Vec2F(v);
      u.eqMinus(center);
      return (u.length2() <= radius*radius);
    }
  }
  public static class RectangularField extends ReceptiveField
  {
    Vec2F lowerleft;
    Vec2F upperright;
    public RectangularField(Vec2F ll,Vec2F ur) { lowerleft = new Vec2F(ll); upperright = new Vec2F(ur); }
    public boolean includes(Vec2F v)
    {
      return (lowerleft.x <= v.x && v.x <= upperright.x && lowerleft.y <= v.y && v.y <= upperright.y);
    }
  }
  
  public static class ReversalEvent implements Comparable<ReversalEvent>
  {
    public int ID;
    public float t0,t1;
    public float path_length;
    
    public ReversalEvent(int id) { ID = id; t0 = t1 = path_length = -1.0f; }
    public ReversalEvent(int id,float t0,float t1,float pathlen)
    {
      ID = id;
      this.t0 = t0; this.t1 = t1;
      path_length = pathlen;
    }
    public float duration() { return t1-t0; }
    public int compareTo(ReversalEvent re)
    {
      if (t0 < re.t0) return -1;
      else if (t0 > re.t0) return 1;
      else if (t1 < re.t1) return -1;
      else if (t1 > re.t1) return 1;
      else return 0;
    }
  }
  
  public static class Oval {
    public int i0,i1,n;
    public double sx,sy,sxx,sxy;
    public Vec2F v;
    public Vec2F w;
    public Oval() { v=w=null; }
    public Oval(Oval o) { i0=o.i0; i1=o.i1; n=o.n; sx=o.sx; sy=o.sy; sxx=o.sxx; sxy=o.sxy; v=w=null; }
    public void zero() { sx = sy = sxx = sxy = 0.0; n = 0; }
    public void add(Vec2F u) { sx+=u.x; sy+=u.y; sxx+=u.x*u.x; sxy+=u.x*u.y; n++; }
    public void sub(Vec2F u) { sx-=u.x; sy-=u.y; sxx-=u.x*u.x; sxy-=u.x*u.y; n--; }
    public void calc() {
      if (n==0) v = Vec2F.zero();
      else v = new Vec2F( (float)(sxx - sx*sx/n) , (float)(sxy - sx*sy/n) ).eqNorm();
    }
    public static Oval[] ovalize(Vec2F[] loc,float sigma) {
      Oval[] ovl = new Oval[ loc.length ];
      Oval o=null;
      for (int i=0;i<ovl.length;i++) ovl[i]=null;
      for (int i=2;i<ovl.length-2;i++) {
        if (o==null) {
          o = new Oval();
          o.i0 = i-2; o.i1 = i+2;
          o.zero();
          for (int j=o.i0 ; j<=o.i1 ; j++) {
            if (loc[j]!=null) o.add(loc[j]);
          }
        }
        else {
          o = new Oval(o);
          if (loc[o.i0]!=null) o.sub(loc[o.i0]);
          o.i0++; o.i1++;
          if (loc[o.i1]!=null) o.add(loc[o.i1]);
        }
        o.calc();
        ovl[i] = o;
      }
      return ovl;
    }
  }
  
  public enum Styled { Weird,Dwell,Clutter,Straight,Arc }
  public static final Styled zeroStyle = Styled.Dwell;
  public final class Style {
    public Styled kind;
    public int dir;
    public int i0,i1;
    public Fitter fit;
    public int[] endpoints = null;
    public LinkedList<Style> children = null;
    
    public Style(Styled s,int i) {
      kind = s;
      dir = 0;
      i0 = i1 = i;
      fit = null;
    }
    public Style(Styled s,int ia,int ib,Fitter f) {
      kind = s;
      dir = 0;
      i0 = ia; i1 = ib;
      fit = f;
    }
    public Style(Styled s,Style old) {
      kind = s;
      dir = 0;
      i0 = old.i0; i1 = old.i1;
      fit = new Fitter(old.fit);
    }
    public Style(LinkedList<Style> adoptables) {
      if (adoptables==null || adoptables.size()==0) {
        kind = Styled.Weird;
        dir = 0;
        i0 = i1 = -1;
        fit = null;
      }
      else if (adoptables.size()==1) mimic(adoptables.pop());
      else {
        mimic( adoptables.peek() );
        fit = null;
        children = new LinkedList<Style>();
        while (!adoptables.isEmpty() && adoptables.peek().kind==kind) {
          Style s = adoptables.pop();
          children.add( s );
          i1 = s.i1;
        }
      }
    }
    
    public int size() { return 1+i1-i0; }
    public void mimic(Style s) {
      kind = s.kind;
      dir = s.dir;
      i0 = s.i0;
      i1 = s.i1;
      fit = s.fit;
      endpoints = s.endpoints;
      children = s.children;
    }
    public int id() {
      return kind.ordinal() - zeroStyle.ordinal();
    }
    public boolean isLine() { return (kind==Styled.Straight || kind==Styled.Arc); }
    public void fit() {
      if (fit==null) return;
      if (kind==Styled.Dwell) fit.spot.fit();
      else if (kind==Styled.Straight) fit.line.fit();
      else if (kind==Styled.Arc) fit.circ.fit();
    }
    public double sqError( Vec2F v ) {
      if (fit==null) return Double.NaN;
      if (kind==Styled.Arc) return fit.circ.sqError(v.x,v.y);
      if (kind==Styled.Straight) return fit.line.sqError(v.x,v.y);
      if (kind==Styled.Dwell) return fit.spot.sqError(v.x,v.y);
      return Double.NaN;
    }
    public void addRightSimply( int i ) { i1 = i; fit.addC(centroid[i].x,centroid[i].y); }
    public void addLeftSimply( int i ) { i0 = i; fit.addC(centroid[i].x,centroid[i].y); }
    public int addRight( int i ) { addRightSimply(i); i++; while (i<area.length && centroid[i]==null) i++; return i; }
    public int addLeft( int i ) { addLeftSimply(i); i--; while (i>=0 && centroid[i]==null) i--; return i ; }
    public void subRight() { fit.subC(centroid[i1].x,centroid[i1].y); i1--; while (i1>=0 && centroid[i1]==null) i1--; }
    public void subLeft() { fit.subC(centroid[i0].x,centroid[i0].y); i0++; while (i0<area.length && centroid[i0]==null) i0++; }
    public int shiftRight( int i ) { subLeft(); return addRight(i); }
    public void deltaVector(Vec2F delta,Vec2F u,Vec2F v) {
      if (kind==Styled.Straight) {
        delta.eq(v).eqMinus(u);
        float l = (float)((fit.line.params.b*delta.y - fit.line.params.a*delta.x)/fit.line.varianceAngleBias());
        delta.x = -(float)fit.line.params.a * l;
        delta.y = (float)fit.line.params.b * l;
      }
      else if (kind==Styled.Arc) {
        double x = 2.0*fit.circ.params.y0-(u.y + v.y);
        double y = u.x+v.x - 2.0*fit.circ.params.x0;
        delta.eq(v).eqMinus(u);
        double l = (delta.x*x + delta.y*y)/(x*x + y*y);
        delta.x = (float)(x*l);
        delta.y = (float)(y*l);
      }
    }
    public boolean hasDirection() {
      return (isLine() && (endpoints==null || endpoints.length>1));
    }
    public boolean hasSeveralDirections() {
      return (isLine() && endpoints!=null && endpoints.length>2);
    }
    public int directions() {
      if (!isLine()) return 0;
      if (endpoints==null) return 1;
      else if (endpoints.length==0) return 0;
      else return endpoints.length-1;
    }
    public void initialVector(Vec2F direction) {
      if (endpoints==null || endpoints.length==0) deltaVector(direction,centroid[i0],centroid[i1]);
      else deltaVector(direction,centroid[endpoints[0]],centroid[endpoints[1]]);
    }
    public void finalVector(Vec2F direction) {
      if (endpoints==null || endpoints.length<2) deltaVector(direction,centroid[i0],centroid[i1]);
      else deltaVector(direction,centroid[endpoints[endpoints.length-2]],centroid[endpoints[endpoints.length-1]]);
    }
    public void pickVector(Vec2F direction,int n) {
      deltaVector(direction,centroid[endpoints[n]],centroid[endpoints[n+1]]);
    }
    public float dotWith(Style s) {
      Vec2F mydir = new Vec2F();
      Vec2F theirdir = new Vec2F();
      if (s.i0 < i0) {
        s.finalVector(theirdir);
        initialVector(mydir);
      }
      else {
        s.initialVector(theirdir);
        finalVector(mydir);
      }
      return mydir.unitDot(theirdir);
    }
    public void snapToLine(Vec2D stray) {  // Make sure this stays identical to Vec2F version!
      if (kind==Styled.Arc) {
        stray.x -= fit.circ.params.x0;
        stray.y -= fit.circ.params.y0;
        stray.eqNorm().eqTimes(fit.circ.params.R);
        stray.x += fit.circ.params.x0;
        stray.y += fit.circ.params.y0;
      }
      else if (kind==Styled.Straight) {
        double off = fit.line.params.a*stray.y + fit.line.params.b*stray.x + fit.line.params.c;
        if (off>0) {
          double shift = -off/(fit.line.params.a*fit.line.params.a + fit.line.params.b*fit.line.params.b);
          stray.x += shift * fit.line.params.b;
          stray.y += shift * fit.line.params.a;
        }
      }
    }
    public void snapToLine(Vec2F stray) {  // Make sure this stays identical to Vec2D version!
      if (kind==Styled.Arc) {
        stray.x -= (float)fit.circ.params.x0;
        stray.y -= (float)fit.circ.params.y0;
        stray.eqNorm().eqTimes((float)fit.circ.params.R);
        stray.x += (float)fit.circ.params.x0;
        stray.y += (float)fit.circ.params.y0;
      }
      else if (kind==Styled.Straight) {
        double off = fit.line.params.a*stray.y + fit.line.params.b*stray.x + fit.line.params.c;
        if (off>0) {
          double shift = -off/(fit.line.params.a*fit.line.params.a + fit.line.params.b*fit.line.params.b);
          stray.x += (float)(shift * fit.line.params.b);
          stray.y += (float)(shift * fit.line.params.a);
        }
      }
    }
    public double parameterize(Vec2D v) {
      if (kind==Styled.Arc) return fit.circ.arcCoord(v.x,v.y);
      else if (kind==Styled.Straight) return fit.line.parallelCoord(v.x,v.y);
      else return 0.0;
    }
    public double distanceTraversed(int n) {
      int j0;
      int j1;
      if (endpoints==null) {
        if (n!=0) return 0.0;
        else { j0=i0; j1=i1; }
      }
      else {
        if (n<0 || n>=endpoints.length-1) return 0.0;
        else { j0=endpoints[n]; j1=endpoints[n+1]; }
      }
      if (kind!=Styled.Arc) return centroid[j1].dist(centroid[j0]);
      else return Math.abs(fit.circ.params.R * fit.circ.arcDeltaCoord(centroid[j0].x,centroid[j0].y,centroid[j1].x,centroid[j1].y));
    }
    public float distanceTraversed() {
      if (endpoints==null) return (float)distanceTraversed(0);
      else {
        double cumulator = 0.0;
        for (int i=0 ; i<endpoints.length-1 ; i++) cumulator += distanceTraversed(i);
        return (float)cumulator;
      }
    }
  }
  
  
  public Dance(int identifier, Choreography chore0, ReceptiveField[] attend, ReceptiveField[] shun)
  {
    ID = identifier;
    chore = chore0;
    first_frame = last_frame = -1;
    shadow_avoided = false;
    ignored_dt = 0;
    ignored_start = null;
    ignored_travel = 0;
    endpoint_angle_fraction = 0.1f;
    
    area = null;
    centroid = null;
    bearing = null;
    extent = null;
    spine = null;
    circles = null;
    origins = new Vector<Integer>(2,2);
    fates = new Vector<Integer>(2,2);
    quantity = null;
    
    this.attend = (attend==null) ? new ReceptiveField[0] : attend;
    this.shun = (shun==null) ? new ReceptiveField[0] : shun;
    
    invert_list = null;
    
    segmentation = null;
    directions = null;
    multiscale_x = null;
    multiscale_y = null;
    multiscale_q = null;
    ranges_xy = null;
  }
  
  // Reads the dancer from a buffered reader.  Returns the last line that was read.
  public String readInputStream(BufferedReader data_file,Choreography.FrameMap[] valid) throws DancerFileException
  {
    boolean any_skeletons = false;
    boolean any_outlines = false;
    int i,j;
    int n_lines;
    DanceLine line = null;
    LinkedList<DanceLine> data = new LinkedList<DanceLine>();
    String input_line;
    String tokens[];
    
    boolean good_line;
    float t_bad = -1.0f;
    
    n_lines = 0;
    while (true) // Read data_file; break to terminate in the middle when we run out
    {
      try { input_line = data_file.readLine(); }
      catch (IOException ioe) { throw new DancerFileException("Unable to access file."); }
      
      if (input_line==null) break;  // Done--end of file (see also 2nd Done case below)
      
      n_lines++;
      if (input_line.length()==0) continue;  // Ignore blank lines
      if (input_line.startsWith("#")) continue;  // Comment character
      if (input_line.startsWith("% ")) {
        try {
          int new_id = Integer.parseInt( input_line.substring(2) );
          if (new_id!=ID) break;  // Done--beginning of next entry in file (see also 1st Done case above)
        }
        catch (NumberFormatException nfe) {}  // Wasn't really a new entry; just ignore it.
      }
      
      tokens = input_line.split("\\s+");
      if (tokens.length < 10) 
      {
        throw new DancerFileException("Too few tokens on line " + n_lines + ":\n'" + input_line + "'");
      }
      
      if (line!=null && line.isSame(tokens,valid)) continue;
      
      line = new DanceLine();
      
      try
      {
        good_line = line.parseLine(tokens,valid);
        if (ignored_start!=null) ignored_travel = Math.max( ignored_travel , ignored_start.dist(line.centroid) );
        else ignored_start = line.centroid;
        if (!good_line) {
          if (t_bad>=0.0f) ignored_dt += line.time-t_bad;
          t_bad = line.time;
          continue;
        }
        t_bad = -1.0f;
        data.add(line);
      }
      catch (NumberFormatException nfe)
      {
        throw new DancerFileException("Format error on line " + n_lines + ".");
      }
      
      if (first_frame < 0) first_frame = line.index;
      last_frame = line.index;
      if (line.spine!=null) any_skeletons = true;
      if (line.outline!=null) any_outlines = true;
    }
    
    if (last_frame==-1 || first_frame==-1) return input_line;  // Empty file; be sure to catch this later
    
    area = new int[ 1+last_frame-first_frame ];
    centroid = new Vec2F[ 1+last_frame-first_frame ];
    bearing = new Vec2F[ 1+last_frame-first_frame ];
    extent = new Vec2F[ 1+last_frame-first_frame ];
    if (any_skeletons) spine = new Spine[1+last_frame-first_frame];
    if (any_outlines) outline = new Outline[1+last_frame-first_frame];
    
    int n = 0;
    has_holes = false;
    for (DanceLine dl : data)
    {
      if (n+first_frame != dl.index)
      {
        n = dl.index - first_frame;
        has_holes = true;
      }
      
      area[n] = dl.area;
      centroid[n] = dl.centroid;
      bearing[n] = dl.bearing;
      extent[n] = dl.extent;
      if (any_skeletons) spine[n] = dl.spine;
      if (any_outlines) outline[n] = dl.outline;
      n++;
    }
    
    // Fix up skeletons so they match each other through time as well as possible
    if (any_skeletons) alignAllSpines();
    
    if (attend!=null || shun!=null) has_holes = true;
    return input_line;
  }

  public static void alignSpines(Spine template, Spine fix) {
    if (template.quantized() || fix.quantized()) {
        long sq_err = 0;
        long rre_sq = 0;
        Vec2S u = new Vec2S();
        Vec2S v = new Vec2S();
        for (int k=0 ; k<template.size() ; k++) {
          template.get(k,u);
          sq_err += u.dist2( fix.get(k,v) );
          rre_sq += u.dist2( fix.get(fix.size()-k-1,v) );
        }
        if (rre_sq < sq_err) fix.flip();
    }
  }
  public void alignAllSpines() {
    int i,j;
    for (i=0; i<spine.length; i++) if (spine[i]!=null) break;
    for (j=i+1; j<spine.length; j++) if (spine[j]!=null) break;
    for (; j<spine.length; i=j) {
      for (j=j+1; j<spine.length; j++) if (spine[j]!=null) break;
      if (j>=spine.length) break;
      alignSpines(spine[i],spine[j]);
    }
  }
  
  public boolean hasData() { return last_frame!=-1 && first_frame!=-1; }
  
  public void trimData(int how_many,boolean start_at_front)
  {
    if (how_many <= 0) return;
    
    int new_length = area.length - how_many;
    if (new_length < 0) new_length = 0;
    
    int j;
    int delta = (start_at_front) ? area.length - new_length : 0;
    
    int[] a = area;
    area = new int[new_length];
    for (int i=0 ; i<area.length ; i++) area[i] = a[i+delta];
    
    Vec2F[] v;
    v = centroid;
    centroid = new Vec2F[new_length];
    for (int i=0 ; i<centroid.length ; i++) centroid[i] = v[i+delta];
    v = bearing;
    bearing = new Vec2F[new_length];
    for (int i=0 ; i<bearing.length ; i++) bearing[i] = v[i+delta];
    v = extent;
    extent = new Vec2F[new_length];
    for (int i=0 ; i<extent.length ; i++) extent[i] = v[i+delta];
    
    if (spine != null)
    {
      Spine[] s = spine;
      spine = new Spine[new_length];
      for (int i=0 ; i<spine.length ; i++) spine[i] = s[i+delta];
    }
    if (outline != null)
    {
      Outline[] o = outline;
      outline = new Outline[new_length];
      for (int i=0 ; i<outline.length ; i++) outline[i] = o[i+delta];
    }
    
    if (start_at_front) first_frame += a.length-area.length;
    else last_frame -= a.length-area.length;
  }
  
  public void findOriginsFates(HashMap<Integer,LinkedList<Ancestry>> geneology)
  {
    LinkedList<Ancestry> lla = geneology.get( ID );
    if (lla==null) return;
    
    for (Ancestry a : lla)
    {
      for (Vec2I v : a.orifates)
      {
        if (v.y==ID) origins.add( v.x );
        if (v.x==ID) fates.add( v.y );
      }
    }
  }
  
  public float t(int i) { return chore.times[first_frame + i]; }
  public float dt(int i, int j) { return chore.times[first_frame+j] - chore.times[first_frame+i]; }
  public float totalT() { return chore.times[last_frame] - chore.times[first_frame]; }
  
  public boolean loc_okay(Vec2F v)
  {
    if (!has_holes) return true;
    for (ReceptiveField rf : attend) { if (!rf.includes(v)) return false; }
    for (ReceptiveField rf : shun) { if (rf.includes(v)) return false; }
    return true;
  }
  
  public void loadMinMax() {
    quantity_min = Float.NaN;
    quantity_max = Float.NaN;
    if (quantity!=null) {
      for (int i=0 ; i<quantity.length ; i++) {
        if (Float.isNaN(quantity_min)) quantity_min = quantity[i];
        else if (!Float.isNaN(quantity[i]) && quantity[i]<quantity_min) quantity_min = quantity[i];
        if (Float.isNaN(quantity_max)) quantity_max = quantity[i];
        else if (!Float.isNaN(quantity[i]) && quantity[i]>quantity_max) quantity_max = quantity[i];
      }
    }
  }
  
  public void loadNoise() {
    quantity_noise = estimateNoise();
  }
  
  public double positionNoiseEstimate() {
    double x = body_area.average;
    double y = noise_estimate.average;
    if (global_position_noise.n>10) {
      double y2 = global_position_noise.line.getY(x);
      if (y<y2) y = y2;
    }
    return y;
  }

  public void prepareForData(boolean nanify) {
    if (quantity==null || quantity.length != area.length) { quantity = new float[area.length]; }
    if (nanify) Arrays.fill(quantity,Float.NaN);
  }
  
  // We don't use this currently.
  public LinkedList<Integer> findStatisticalWeirdness() {
    LinkedList<Integer> lli = new LinkedList<Integer>();
    Vec2F v = new Vec2F();
    prepareForData(true);
    
    float better;
    for (int i=1 ; i<area.length-1 ; i++) {
      if (centroid[i+1]==null) { i+=2; continue; }
      else if (centroid[i]==null) { i++; continue; }
      else if (centroid[i-1]==null) { continue; }
      v.eq( centroid[i-1] );
      v.eqPlus( centroid[i+1] );
      v.eqTimes( 0.5f );
      quantity[i] = v.dist(centroid[i]);
    }
    
    float[] qtemp = Arrays.copyOf(quantity,quantity.length);
    Statistic betweenness = new Statistic(qtemp);
    float rare_sd = Statistic.invnormcdf_tail( 0.05f/area.length );  // One-tailed
    float rare_sd_2tail = Statistic.invnormcdf_tail( 0.025f/area.length );  // Two-tailed
    
    for (int i=0 ; i<area.length ; i++) {
      if (!Float.isNaN(quantity[i])) quantity[i] = (float) ((quantity[i]-betweenness.average)/betweenness.deviation);
    }
    
    // For betweenness, need to blank on either side of maximum weirdness
    for (int i=1 ; i<area.length-1 ; i++) {
      if (!Float.isNaN(quantity[i]) && !Float.isNaN(quantity[i+1]) && !Float.isNaN(quantity[i-1])) {
        if (quantity[i] > rare_sd && quantity[i]>quantity[i-1] && quantity[i]>quantity[i+1]) {
          quantity[i-1] = quantity[i+1] = Float.NaN;
        }
      }
    }
    
    for (int i=0 ; i<area.length ; i++) {
      /*if (!Float.isNaN(area[i]) && Math.abs(area[i]-body_area.average)/body_area.deviation > rare_sd_2tail) lli.add(i);
      else*/ if (!Float.isNaN(quantity[i]) && quantity[i] > rare_sd) lli.add(i);
    }
    return lli;
  }
  
  // Find the index of the requested time or where it is between two indices.  NaN if out of range.
  public double seek(float t,float[] time_array) {
    int i = Arrays.binarySearch(time_array,first_frame,last_frame+1,t);
    if (i>=0) {
      if (i>=first_frame && i<last_frame) return i-first_frame;
      else return Double.NaN;
    }
    else { 
      i = -i-1;
      if (i<=first_frame || i>=last_frame) return Double.NaN;
      else return ((double)(i-1-first_frame)) + (t-time_array[i-1])/(time_array[i]-time_array[i-1]);
    }
  }
  
  // Same thing except near a given index with time specified as an offset
  public double seekNearT(float dt,float[] time_array,int i) {
    int j = i+first_frame;
    float t = time_array[j];
    if (dt>0.0f) {
      for (i++,j++ ; j<=last_frame && time_array[j]-t<dt ; i++,j++) {}
      if (j>=last_frame) {
        if (j>last_frame || time_array[j]-t!=dt) return Double.NaN;
        else return i;
      }
      else if (time_array[j]-t==dt) return i;
      else return ((double)(i-1)) + ((t+dt)-time_array[j-1])/(time_array[j]-time_array[j-1]);
    }
    else if (dt<0.0f) {
      for (i--,j-- ; j>=first_frame && time_array[j]-t>dt ; i--,j--) {}
      if (j<=first_frame) {
        if (j<first_frame || time_array[j]-t!=dt) return Double.NaN;
        else return i;
      }
      else if (time_array[j]-t==dt) return i;
      else return ((double)i) + ((t+dt)-time_array[j])/(time_array[j+1]-time_array[j]);
    }
    else return i;
  }
  
  public Vec2I seekTimeIndices(float[] t,Vec2D range,double tol) {
    if (t[first_frame] > range.y+tol || t[last_frame]<range.x-tol) return null;
    double i0 = (range.x-tol < t[first_frame]) ? 0.0 : seek((float)range.x,t);
    double i1 = (range.y+tol > t[last_frame]) ? 1.0+last_frame-first_frame : seek((float)range.y,t);
    Vec2I v = new Vec2I( (int)Math.round(i0) , (int)Math.round(i1) );
    if (v.x<0) v.x=0;
    if (v.y>=centroid.length) v.y = centroid.length-1;
    if (v.y<v.x) v.y=v.x;
    return v;
  }
  
  public int seekTimeIndex(float[] t,double time,double tol) {
    if (t[first_frame] > time+tol || t[last_frame] < time-tol) return -1;
    if (t[first_frame] >= time) return 0;
    else if (t[last_frame] <= time) return 1+last_frame-first_frame;
    else {
      double idx = seek((float)time,t);
      if (Double.isNaN(idx)) return -1;
      else return (int)Math.round(idx);
    }
  }
  
  public Vec2F fracLoc(double d,Vec2F[] loc,Vec2F v) {
    if (Double.isNaN(d)) return null;
    int i = (int)Math.round(d);
    if (loc[i]==null) return null;
    if (v==null) v = new Vec2F();
    float f = (float)(d-i);
    if (f*f<1e-6) v.eq(loc[i]);
    else {
      if (f<0) { f += 1.0f; i--; } 
      if (loc[i]==null || loc[i+1]==null) return null;
      v.eq(loc[i]).eqWeightedSum(1.0f-f , f , loc[i+1]);
    }
    return v;
  }
  public Vec2F fracLoc(float t,float[] time_array,Vec2F[] loc,Vec2F v) { return fracLoc( seek(t,time_array) , loc , v ); }
  public Vec2F fracLoc(float dt,float[] time_array,Vec2F[] loc,int i,Vec2F v) { return fracLoc( seekNearT(dt,time_array,i) , loc , v ); }
  
  public float fracQuant(double d,float[] data) {
    if (Double.isNaN(d)) return Float.NaN;
    int i = (int)Math.round(d);
    float f = (float)(d-i);
    if (f*f<1e-6) return data[i];
    else {
      if (f<0) { f += 1.0f; if (Float.isNaN(data[i+1])) return Float.NaN; }
      else { i--; if (Float.isNaN(data[i])) return Float.NaN; }
      return (1.0f-f)*data[i] + f*data[i+1];
    }
  }
  public float fracQuant(float t,float[] time_array,float[] data) { return fracQuant( seek(t,time_array) , data ); }
  public float fracQuant(float dt,float[] time_array,int i,float[] data) { return fracQuant( seekNearT(dt,time_array,i) , data ); }
  

  public float meanBodyLengthEstimate() {
    if (spine!=null) {
      if (body_spine==null) { findSpineLength(); normalizeSpineLength(); }
      return (float)body_spine.average;
    }
    else return (float)Math.sqrt( body_length.average*body_length.average + 4.0f*body_width.average*body_width.average );
  }
  
  public float maximumExcursion()
  {
    Vec2F v0;
    float max_d2 = 0.0f;
    float d2;
    
    v0 = null;
    int i = 0;
    for ( ; i<centroid.length && centroid[i]==null ; i++) {}
    if (i==centroid.length) return Float.NaN;
    
    v0 = centroid[i];
    for (i++ ; i<centroid.length ; i++) {
      if (centroid[i]==null) continue;
      d2 = v0.dist2(centroid[i]);
      if (d2>max_d2) max_d2 = d2;
    }
    return (float)Math.sqrt(max_d2);
  }
  
  // Returns the index of the first frame after first_index that's more than distance_limit away, or 0 if no such frame exists
  public int findFirstBeyond(int first_index , double distance_limit)
  {
    if (distance_limit < 0.0f) return 0;
    float dist_squared = (float) (distance_limit*distance_limit);  // Avoid square root
    
    if (first_index < 0) first_index = 0;
    
    while (first_index < centroid.length && centroid[first_index]==null) first_index++;
    
    for (int i=first_index ; i<centroid.length ; i++)
    {
      if (centroid[i]==null) continue;
      if (centroid[i].dist2(centroid[first_index]) > dist_squared) return i;
    }
    return centroid.length;
  }
  
  // Assuming Gaussian white noise and an underlying time series with no impulses, estimate the noise
  public float estimateNoise() {
    int i;
    int n_n_nan = 0;
    int n_static = 0;
    float last = Float.NaN;
    for (i=0 ; i<quantity.length ; i++) {
      if (Float.isNaN(quantity[i])) n_n_nan++;
      else {
        if (quantity[i]==last) n_static++;
        last = quantity[i];
      }
    }
    
    int n;
    double[] derivs = new double[quantity.length - n_n_nan - n_static];
    last = Float.NaN;
    for (i=0 , n=0 ; i<quantity.length ; i++) {
      if (!Float.isNaN(quantity[i]) && quantity[i]!=last) {
        last = quantity[i];
        derivs[n++] = last;
      }
    }
    
    boolean converged = false;
    double sumsq,ssq2;
    LinkedList<Double> results = new LinkedList<Double>();
    double result;
    double[] pascal = new double[1];
    pascal[0] = 1.0;
    while (!converged && derivs.length-results.size()>3+results.size() && results.size()<ERROR_CONVERGE_ITERATIONS) {
      for (i=1 ; i<derivs.length-results.size() ; i++) derivs[i-1] -= derivs[i];
      for (sumsq=0.0 , i=0 , n=0 ; i<derivs.length-results.size() ; n++,i+=2+results.size()) sumsq += derivs[i]*derivs[i];
      for (ssq2=0.0 , i=0 ; i<pascal.length ; i++) ssq2 += pascal[i]*pascal[i];
      result = Math.sqrt( sumsq / (n*ssq2) );
      double[] temp = pascal;
      pascal = new double[ temp.length+1 ];
      for (i=0 ; i<pascal.length ; i++) pascal[i] = ((i-1<0) ? 0 : temp[i-1]) + ((i>=temp.length) ? 0 : temp[i]);
      if (results.size()>0) {
        if ( (results.getLast() - result)/results.getLast() < ERROR_CONVERGE_FRACTION ) converged = true;
      }
      if (!converged || result<results.getLast()) results.add(result);
    }
    return (results.size()>0) ? results.getLast().floatValue() : 1.0f;
  }
  
  public boolean calcBasicStatistics(boolean avoid_shadow)
  {
    prepareForData(false);
    
    if (has_holes)
    {
      int i,j;
      float f;
      for (i=j=0;i<extent.length;i++) if (extent[i]!=null && loc_okay(centroid[i])) quantity[j++] = extent[i].x;
      body_length = new Statistic( quantity , 0 , j );
      float distance_to_trim = (float)body_length.average - ((centroid.length>0 && ignored_start!=null) ? centroid[0].dist(ignored_start) : 0.0f);
      if (distance_to_trim <= 0.0f) shadow_avoided = true;
      if (avoid_shadow && !shadow_avoided)
      {
        shadow_avoided = true;
        trimData( findFirstBeyond(0,distance_to_trim) , true );
        if (centroid==null || centroid.length==0) return false;
        calcBasicStatistics(false);
        return true;
      }
      for (i=j=0;i<extent.length;i++) if (extent[i]!=null && loc_okay(centroid[i])) quantity[j++] = extent[i].y;
      body_width = new Statistic( quantity , 0 , j );
      for (i=j=0;i<area.length;i++) if (!Double.isNaN(area[i]) && centroid[i]!=null && loc_okay(centroid[i])) quantity[j++] = area[i];
      body_area = new Statistic( quantity , 0 , j );
      for (i=j=0;i<extent.length;i++)
      {
        if (extent[i]!=null && loc_okay(centroid[i]))
        {
          quantity[j++] = (extent[i].x*extent[i].x < 1e-12) ? 0.0f : extent[i].y / extent[i].x;
        }
      }
      body_aspect = new Statistic( quantity , 0 , j );
    }
    else
    {
      int j;
      j=0; for (Vec2F v : extent) quantity[j++] = v.x;
      body_length = new Statistic( quantity );
      float distance_to_trim = (float)body_length.average - ((centroid.length>0 && ignored_start!=null) ? centroid[0].dist(ignored_start) : 0.0f);
      if (distance_to_trim <= 0.0f) shadow_avoided = true;
      if (avoid_shadow && !shadow_avoided)
      {
        shadow_avoided = true;
        trimData( findFirstBeyond(0,distance_to_trim) , true );
        if (centroid==null || centroid.length==0) return false;
        calcBasicStatistics(false);
        return true;
      }
      j=0; for (Vec2F v : extent) quantity[j++] = v.y;
      body_width = new Statistic( quantity );
      j=0; for (float a : area) quantity[j++] = a;
      body_area = new Statistic( quantity );
      j=0; for (Vec2F v : extent) quantity[j++] = (v.x*v.x < 1e-12) ? 0.0f : v.y/v.x;
      body_aspect = new Statistic( quantity );
    }
    calcPositionNoise();
    
    quantity = null;
    return true;
  }
  public void calcBasicStatistics() { calcBasicStatistics(false); }
  
  public void calcPositionNoise()
  {
    prepareForData(false);
    
    int i,j;
    Vec2F u = new Vec2F();
    Vec2F v = new Vec2F();
    
    // Calculate the approximate position noise based on the magnitude of second derivative vectors
    for (j=0 , i=2 ; i<centroid.length ; i++)
    {
      if (centroid[i]==null || centroid[i-1]==null || centroid[i-2]==null) continue;
      u.eq( centroid[i] );
      u.eqPlus( centroid[i-2] );
      v.eq( centroid[i-1] );
      v.eqTimes(2.0f);
      u.eqMinus(v);
      quantity[j++] = u.length() * curvature_noise_factor / (float)Math.sqrt(2.0);
    }
    noise_estimate = new Statistic();
    noise_estimate.robustCompute( quantity , 0 , j );
  }
  
  protected void findNonNullSegment(Vec2I seg) {
    if (seg.y >= area.length) seg.y = area.length-1;
    if (seg.x < 0) seg.x = 0;
    while (seg.x <= seg.y && centroid[seg.x]==null) seg.x++;
    while (seg.y >= seg.x && centroid[seg.y]==null) seg.y--;
  }
  protected boolean identical(int value,int[] values) {
    for (int v : values) if (v!=value) return false;
    return true;
  }
  public void findSegmentation() {
    if (area.length<10) return;
    
    Fitter shared = new Fitter();  // Don't use this one--use it in constructors as source of common fitting classes!
    shared.shiftZero(true);
    
    Fitter f;
    Vec2F u = new Vec2F();
    Vec2F v = new Vec2F();
    Vec2F w = new Vec2F();
    Vec2I seg = new Vec2I();
    int i,j,n;
    
    double jitter = positionNoiseEstimate();
    final double RARE = 0.05/area.length;
    final double credibleDistSq = Math.pow((jitter*Statistic.invnormcdf_tail((float)RARE)),2);
    
    LinkedList<Style> moves = new LinkedList<Style>();
    LinkedList<Style> refined;
    Style s,ss;
    boolean okay;
    
    // Find all patches where the object is still, just dwelling about a point.
    i = 0;
    while (i<area.length) {
      if (centroid[i]==null) { i++; continue; }
      
      f = new Fitter(shared);
      s = new Style(Styled.Dwell , i , i-1 , f);
      while (s.size()<5 && i<area.length) i = s.addRight(i);
      
      double pSpot = f.spot.pFit(jitter*jitter);
      while (pSpot <= RARE && i<area.length) {
        i = s.shiftRight(i);
        pSpot = f.spot.pFit(jitter*jitter);
      }
      
      while (pSpot > RARE && i<area.length) {
        i = s.addRight(i);
        pSpot = f.spot.pFit(jitter*jitter);
      }
      
      if (s.size()>5 && pSpot <= RARE) {
        i = s.i1;
        s.subRight();
      }
      
      if (s.size()>=5) {
        seg.eq( (moves.peekLast()==null) ? 0 : moves.peekLast().i1+1 , s.i0-1 );
        if (seg.x <= seg.y) findNonNullSegment(seg);
        if (seg.x <= seg.y) moves.add( new Style(Styled.Weird , seg.x , seg.y , null) );
        s.fit();  // Will need spot fit for later stuff!
      }
      else {
        s.kind = Styled.Weird;
        s.fit = null;
      }
      moves.add( s );
    }
    // Refine these patches
    okay = false;
    while (!okay && moves.size()>1) {
      okay = true;
      Iterator<Style> is = moves.iterator();
      Style last = null;
      Style current = null;
      while (is.hasNext()) {
        last = current;
        current = is.next();
        if (last==null) continue;
        if (last.kind != Styled.Dwell && current.kind != Styled.Dwell) {
          last.i1 = current.i1;
          current.i0 = last.i1+1;
          okay = false;
        }
        else if (last.kind != Styled.Dwell && current.kind == Styled.Dwell) {
          while (last.size()>0 && current.fit.spot.sqError(centroid[last.i1].x,centroid[last.i1].y)<credibleDistSq) {
            last.i1 = current.addLeft( last.i1 );
            current.fit.spot.fit();
            okay = false;
          }
        }
        else if (last.kind == Styled.Dwell && current.kind != Styled.Dwell) {
          while (current.size()>0 && last.fit.spot.sqError(centroid[current.i0].x,centroid[current.i0].y)<credibleDistSq) {
            current.i0 = last.addRight( current.i0 );
            last.fit.spot.fit();
            okay = false;
          }
        }
        else {
          boolean changed = true;
          do {
            double deltaErrorL = last.fit.spot.sqError(centroid[current.i0].x,centroid[current.i0].y) -
              current.fit.spot.sqError(centroid[current.i0].x,centroid[current.i0].y);
            double deltaErrorR = current.fit.spot.sqError(centroid[last.i1].x,centroid[last.i1].y) -
              last.fit.spot.sqError(centroid[last.i1].x,centroid[last.i1].y);
            if (deltaErrorL >= 0 && deltaErrorR >=0) changed = false;
            else if (deltaErrorL < deltaErrorR || (deltaErrorL==deltaErrorR && last.size()<current.size())) {
              last.addRightSimply( current.i0 ); current.subLeft();
              okay = false;
            }
            else {
              current.addLeftSimply( last.i1 ); last.subRight();
              okay = false;
            }
          } while (changed && last.size()>0 && current.size()>0);
        }
        if (current.size()==0) { is.remove(); current=last; }
        else if (last.size()==0) { last.mimic(current); is.remove(); current=last; }
        else if (current.size()<2 && current.kind==Styled.Dwell) { current.kind = Styled.Weird; }
        else if (last.size()<2 && last.kind==Styled.Dwell) { last.kind = Styled.Weird; }
      }
    }
    
    // Find all patches where the object is essentially moving along a straight line
    refined = new LinkedList<Style>();
    while (moves.peek()!=null) {
      s = moves.pop();
      if (s.kind==Styled.Dwell) refined.addLast( s );
      else if (s.size() < 3) refined.addLast( s );
      else {
        ss = new Style(Styled.Straight , s.i0 , s.i0-1 , new Fitter(shared));
        i = s.i0;
        while (ss.size()<5 && i<=s.i1) i = ss.addRight(i);
        ss.fit.line.fit();
        
        double pLine = ss.fit.line.pNonRoundFit(jitter*jitter,0.05);
        while (pLine <= RARE && i<=s.i1) {
          i = ss.shiftRight(i);
          ss.fit.line.fit();
          pLine = ss.fit.line.pNonRoundFit(jitter*jitter,0.05);
        }
        
        while (pLine > RARE && i<=s.i1) {
          i = ss.addRight(i);
          ss.fit.line.fit();
          pLine = ss.fit.line.pNonRoundFit(jitter*jitter,0.05);
        }
        
        if (ss.size() > 5 && pLine <= RARE) {
          i = ss.i1;
          ss.subRight();
          ss.fit.line.fit();
        }
        
        if (ss.size()<3) refined.addLast(s);
        else {
          if (s.i0 < ss.i0) {
            seg.eq(s.i0 , ss.i0-1);
            findNonNullSegment(seg);
            if (seg.x <= seg.y) refined.addLast( new Style(s.kind , seg.x , seg.y , null) );
          }
          refined.addLast( ss );
          if (i <= s.i1) {
            s.i0 = i;
            if (s.size()<3) refined.addLast( s );
            else moves.addFirst( s );
          }
        }
      }
    }
    moves = refined;
    // Switch from straight lines to arcs anywhere it is a much better fit
    for (Style m : moves) {
      if (m.kind==Styled.Straight && m.size()>4) {
        m.fit.circ.fit();
        double pLine = m.fit.line.pFit(jitter*jitter);
        double pCirc = m.fit.circ.pFit(jitter*jitter);
        if (pCirc > RARE && pCirc*RARE*m.fit.n>pLine) m.kind = Styled.Arc;
      }
    }
    // Refine these patches, converting to arcs as necessary
    okay = false;
    int[] oldsizes = new int[6];
    int oi;
    for (oi=0 ; oi<oldsizes.length ; oi++) oldsizes[oi] = -1;
    while (!okay && moves.size()>1 && !identical(moves.size(),oldsizes)) {
      okay = true;
      oi++;
      if (oi >= oldsizes.length) oi=0;
      oldsizes[oi] = moves.size();
      Iterator<Style> is = moves.iterator();
      Style last = null;
      Style current = null;
      while (is.hasNext()) {
        last = current;
        current = is.next();
        if (last==null) continue;
        if (last.kind==Styled.Dwell || current.kind==Styled.Dwell) continue;
        if (last.isLine() || current.isLine()) {
          boolean changed = false;
          // Try merging
          if (last.isLine() && current.isLine()) {
            Fitter nuf = (new Fitter(last.fit)).join(current.fit);
            nuf.line.fit();
            double pLine = nuf.line.pFit(jitter*jitter);
            nuf.circ.fit();
            double pCirc = nuf.circ.pFit(jitter*jitter);
            if (pCirc > RARE && pCirc*RARE*nuf.n > pLine) {
              last.kind = Styled.Arc;
              changed = true;
            }
            else if (pLine > RARE) {
              last.kind = Styled.Straight;
              changed = true;
            }
            if (changed) {
              last.i1 = current.i1;
              last.fit = nuf;
              current = last;
              is.remove();
              okay = false;
            }
          }
          if (changed) continue;
          // If merge failed, go point by point
          int max_iterations = 1 + 2*(current.size() + last.size());
          double d2ll,d2lr,d2rl,d2rr;
          d2ll = d2lr = d2rl = d2rr = credibleDistSq;
          changed = true;  // To make the "changed" condition act as a do...while
          while (last.i1>=last.i0 && current.i1>=current.i0 && changed && max_iterations > 0) {
            changed = false;
            max_iterations--;
            if (last.isLine()) {
              d2ll = last.sqError( centroid[last.i1] );
              d2lr = last.sqError( centroid[current.i0] );
            }
            if (current.isLine()) {
              d2rl = current.sqError( centroid[last.i1] );
              d2rr = current.sqError( centroid[current.i0] );
            }
            if (d2ll>d2rl && d2rr>d2lr) {
              if (d2rl < d2lr) d2lr = d2rr = credibleDistSq;
              else if (d2lr < d2rl) d2rl = d2ll = credibleDistSq;
              else d2ll = d2lr = d2rl = d2rr = credibleDistSq;
            }
            if (d2rl < d2ll) {
              changed = true;
              if (current.isLine()) {
                if (last.isLine()) {
                  current.addLeftSimply(last.i1);
                  last.subRight(); last.fit();
                }
                else last.i1 = current.addLeft(last.i1);
                current.fit();
              }
              else if (last.isLine()) {
                current.i0 = last.i1;
                last.subRight();
                last.fit();
              }
            }
            else if (d2lr < d2rr) {
              changed = true;
              if (last.isLine()) {
                if (current.isLine()) {
                  last.addRightSimply(current.i0);
                  current.subLeft(); current.fit();
                }
                else current.i0 = last.addRight(current.i0);
                last.fit();
              }
              else if (current.isLine()) {
                last.i1 = current.i0;
                current.subLeft();
                current.fit();
              }
            }
            if (changed) okay = false;
          }
          if (last.i1-last.i0 < 0) {
            last.mimic(current);
            is.remove();
            current = last;
          }
          else if (current.i1-current.i0 < 0) {
            is.remove();
            current = last;
          }
          else if (last.i1-last.i0 < 2 + ((last.kind==Styled.Arc)?1:0)) last.kind = Styled.Clutter;
          else if (current.i1-current.i0 < 2 + ((current.kind==Styled.Arc)?1:0)) current.kind = Styled.Clutter;
        }
      }
    }
    
    // Mark little bits of junk as clutter, and move everything over to the array
    for (Style m : moves) if (m.size()<3) { m.kind = Styled.Clutter; }
    segmentation = moves.toArray( new Style[ moves.size() ] );
    Fitter tfit = new Fitter();
    Vec2D dv = new Vec2D();
    for (n=0; n<segmentation.length; n++) {
      Style m = segmentation[n];
      if (!m.isLine()) continue;  // Anything that's junk is already junk
      if (m.size()>20) continue;  // Anything this big should be fine
      u.eq(centroid[m.i0+1]).eqMinus(centroid[m.i0]);
      int dotp = 0;
      int dotn = 0;
      int bigp = 0;
      int bign = 0;
      for (i=m.i0+1; i<=m.i1-1; i++) {
        v.eq(centroid[i+1]).eqMinus(centroid[i]);
        if (u.dot(v)>0) {
          dotp++;
          u.eqPlus(v);
        }
        else {
          dotn++;
          u.eq(v);
        }
      }
      // Check binomial theorem--are we significantly different from random jitter?
      if (dotn>0) {
        int tot = dotn+dotp;
        int comb = 1<<tot;
        int sp = 1;
        int p = 1;
        for (i=1; i<tot; i++) {
          p *= (1+tot-i);
          p /= i;
          if (20*(p+sp) > comb) break;
          sp += p;
        }
        // Not significantly different; now we're really worried that it's just enlarged noise.  Is there significant correlation with time?
        if (dotn>=i) {
          tfit.reset();
          if (m.kind == Styled.Straight) {
            for (i=m.i0; i<=m.i1; i++) tfit.addL(i-m.i0, m.parameterize(dv.eq(centroid[i])));
            tfit.line.fit();
            if (Statistic.cdfTstat(tfit.line.tScoreCorrelation(),m.i1-m.i0-1) < 0.95f) m.kind = Styled.Clutter;  // Not a significant time correlation; throw it away
          }
          else {
            double[] param = new double[1+m.i1-m.i0];
            for (i=m.i0; i<=m.i1; i++) param[i-m.i0] = m.parameterize(dv.eq(centroid[i]));
            double[] pcopy = Arrays.copyOf(param,param.length);
            Arrays.sort(param);
            j = 0;
            double dmax = 2*Math.PI + param[0]-param[param.length-1];
            for (int k=1 ; k<param.length ; k++) {
              double delta = param[k]-param[k-1];
              if (delta>dmax) {
                dmax = delta; j = k;
              }
            }
            if (j>0) dmax = 0.5*(param[j]+param[j-1]);
            else dmax = Math.PI;
            for (i=0;i<pcopy.length;i++) tfit.addL(i-m.i0,(pcopy[i]>dmax) ? pcopy[i]-2.0*Math.PI : pcopy[i]);
            tfit.line.fit();
            if (Statistic.cdfTstat(tfit.line.tScoreCorrelation(),m.i1-m.i0-1) < 0.95f) m.kind = Styled.Clutter;  // Not a significant time correlation; throw it away
          }
        }
      }
    }
    
    // Find endpoints of everything that's a line
    for (n = 0 ; n<segmentation.length ; n++) {
      if (!segmentation[n].isLine()) continue;
      
      Style m = segmentation[n];
      boolean extend_pre = false;
      boolean extend_post = false;
      LinkedList<Integer> endpoint_list = new LinkedList<Integer>();
      int e0,e1;
      
      u.eq(0,0);
      for (e0=e1=m.i0,i=m.i0+1 ; i<=m.i1 && u.length2()<credibleDistSq ; i++) {
        m.deltaVector(v,centroid[e0],centroid[i]);
        if (u.dot(v)<0) {
          e0 = e1; e1 = i;
          m.deltaVector(u,centroid[e0],centroid[e1]);
        }
        else if (u.length2() < v.length2()) { 
          e1 = i;
          u.eq(v);
        }
      }
      if (n>0 && !segmentation[n-1].isLine()) {
        ss = segmentation[n-1];
        m.deltaVector(v,centroid[ss.i1],centroid[e0]);
        if (u.dot(v)>0 && m.sqError(centroid[ss.i1])<credibleDistSq && u.length2()+v.length()>=credibleDistSq) {
          m.i0 = e0 = ss.i1;
          u.eqPlus(v);
          extend_pre = true;
        }
      }
      for ( ; i<=m.i1 ; i++) {
        m.deltaVector(v,centroid[e1],centroid[i]);
        if (u.dot(v)>=0) {
          e1 = i;
          u.eqPlus(v);
        }
        else if (v.length2() >= credibleDistSq) {
          endpoint_list.addLast( e0 );
          e0 = e1;
          e1 = i;
          u.eq(v);
        }
      }
      if (n<segmentation.length-1 && !segmentation[n+1].isLine()) {
        ss = segmentation[n+1];
        m.deltaVector(v,centroid[e1],centroid[ss.i0]);
        if (u.dot(v)>0 && m.sqError(centroid[ss.i0])<credibleDistSq && u.length2()+v.length2()>=credibleDistSq) {
          m.i1 = e1 = ss.i0;
          u.eqPlus(v);
          extend_post = true;
        }
      }
      if (u.length2() >= credibleDistSq || endpoint_list.size()>0) {
        if (extend_pre) m.fit.addC( centroid[m.i0].x , centroid[m.i0].y );
        if (extend_post) m.fit.addC( centroid[m.i1].x , centroid[m.i1].y );
        if (extend_pre || extend_post) m.fit();
        
        if (e0==m.i0 && e1==m.i1) continue;  // No sense in storing default case, just leave it as null!
        m.endpoints = new int[ endpoint_list.size()+2 ];
        i = 0;
        for (int ep : endpoint_list) m.endpoints[i++] = ep;
        m.endpoints[i++] = e0;
        m.endpoints[i++] = e1;
      }
      else m.endpoints = new int[0];  // Found nothing, and "null" means straight, so need to pass an empty array here as a marker!
    }
  }
  
  public int indexToSegment(int i) {
    if (segmentation==null) return -1;
    int j0 = 0;
    int j1 = segmentation.length-1;
    int j = 0;
    while (j1-j0>1) {
      j = (j1+j0)/2;
      if (i < segmentation[j].i0) j1=j;
      else j0=j;
    }
    if (segmentation[j0].i0 <= i && segmentation[j0].i1>=i) return j0;
    else return j1;
  }

  public Vec2F getSegmentedDirection(int i, Vec2F v) {
    if (v==null) v = new Vec2F(); else v.eq(0,0);
    int j = indexToSegment(i);
    if (j<0) return v;
    Style s = segmentation[j];
    int i0 = (i>s.i0) ? i-1 : i;
    int i1 = (i<s.i1) ? i+1 : i;
    if (i0==i1) return v;
    s.snapToLine(v.eq(centroid[i0]));
    float x0 = v.x;
    float y0 = v.y;
    s.snapToLine(v.eq(centroid[i1]));
    v.x -= x0;
    v.y -= y0;
    return v;
  }
  
  public float pathLength(int i0,int i1) {
    float path = 0.0f;
    Vec2F v = new Vec2F();
    if (segmentation!=null) {
      int j0 = indexToSegment(i0);
      int j1 = indexToSegment(i1);
      for (int j=j0 ; j<=j1 ; j++) {
        if (segmentation[j].hasDirection()) {
          segmentation[j].initialVector(v);
          path += v.length();
          if (segmentation[j].hasSeveralDirections()) {
            for (int k=1 ; k<segmentation[j].endpoints.length-1 ; k++) {
              segmentation[j].pickVector(v,k);
              path += v.length();
            }
          }
        }
      }
    }
    else {
      int i = i0;
      while (i0<area.length && centroid[i0]==null) i0++;
      while (i<i1) {
        i = i0+1; while (i<area.length && centroid[i]==null) i++;
        if (i<area.length) path += centroid[i].dist(centroid[i0]);
        i0 = i;
      }
    }
    return path;
  }
    
  public void findDirectionBiasSegmented(float min_travel) {
    if (segmentation==null) findSegmentation();
    prepareForData(false);
    if (segmentation==null) return;
       
    Vec2F u = new Vec2F();
    Vec2F v = new Vec2F();
    Vec2F w = new Vec2F();
    Style s,ss;
    int g,h,i,j,k;
    float f;

    // Figure out where the animal may have done something weird
    findPostureConfusion();

    // Pull out all the pieces which are not part of a turn and are long enough
    LinkedList<Vec2I> consistent_pieces = new LinkedList<Vec2I>();
    for (i=j=0 ; i<quantity.length ; i=j) {
      for (j=i ; j<quantity.length && quantity[j]==quantity[i] ; j++) { }
      if (quantity[i]==0) { consistent_pieces.addLast( new Vec2I(i,j-1) ); }
    }
    Vec2I[] cp_array = consistent_pieces.toArray(new Vec2I[consistent_pieces.size()]);

    // Figure out which bits are long enough to get a good reading on and heal gaps on adjacent good bits
    boolean[] ok_array = new boolean[cp_array.length];
    for (i=0; i<cp_array.length; i++) ok_array[i] = (pathLength(cp_array[i].x,cp_array[i].y) >= min_travel);
    for (i=1; i<cp_array.length; i++) {
      if (ok_array[i] && ok_array[i-1]) {
        j = (cp_array[i-1].y + cp_array[i].x)/2;
        cp_array[i-1].y = j;
        cp_array[i].x = j+1;
      }
    }
    for (h=0, i=0; i<ok_array.length; i++) if (ok_array[i]) h++;
    Vec2I[] cp_good = new Vec2I[h];
    for (i=j=0; i<cp_array.length; i++) if (ok_array[i]) cp_good[j++] = cp_array[i];

    // Generate a self-consistent set of directions across the entire trace (but may contain flips)
    g = 1;
    for (j=0 ; j<quantity.length ; j++) quantity[j] = 0.0f;
    for (h=i=j=0 ; i<segmentation.length ; i=j) {
      s = segmentation[i];
      if (!s.hasDirection()) {
        j++;
        if (s.kind==Styled.Dwell) for (;h<=s.i1;h++) quantity[h]=0;
        else for (;h<=s.i1;h++) quantity[h]=g;
      }
      else {
        if (s.hasSeveralDirections()) {
          for (k=1 ; k<s.endpoints.length-1 ; k++) {
            for (;h<=s.endpoints[k];h++) quantity[h]=g;
            g = -g;
          }
        }
        for (;h<=s.i1;h++) quantity[h]=g;
          
        for (j=i+1 ; j<segmentation.length ; j++) if (segmentation[j].hasDirection()) break;
        if (j<segmentation.length) {
          ss = segmentation[j];
          s.finalVector(u);
          ss.initialVector(v);
          float udv = u.unitDot(v);
          if (udv<-0.5f) g = -g;  // Surely backwards!
          else if (udv<0.0 || (udv < 0.2 && ss.size()<10)) {  // Probably backwards, but we'd better check as these sorts of flips are really bad
            // If the segment is short, check the following segment to see if it might resolve the ambiguity
            if (ss.size()<10) {
              for (k=j+1 ; k<segmentation.length ; k++) if (segmentation[k].hasDirection()) break;
              if (k < segmentation.length) {
                Style sss = segmentation[k];
                if (sss.size() >= 2*ss.size()) {  // We should trust this one more
                  sss.initialVector(w);
                  if (Math.abs(u.unitDot(w))>0.5f) { // Clear answer
                    Vec2F x = new Vec2F();
                    ss.finalVector(x);
                    for (;h<ss.i0;h++) quantity[h]=0;
                    if (udv+w.unitDot(x) < 0) for (;h<=ss.i1;h++) quantity[h] = -g;
                    else for (;h<ss.i1;h++) quantity[h] = g;
                    for (;h<sss.i0;h++) quantity[h]=0;
                    if (u.unitDot(w)<0) g = -g;
                    j = k;
                    continue;  // Next!
                  }
                }
              }
            }
            // Next segment hasn't rescued us, so see if we can get information from flow along spine
            if (spine!=null) {
              int i1 = s.i1;
              int i0 = (s.endpoints==null) ? s.i0 : s.endpoints[s.endpoints.length-2];
              int ii0 = ss.i0;
              int ii1 = (ss.endpoints==null) ? ss.i1 : ss.endpoints[1];
              if (spine[i1]==null || spine[i0]==null || spine[ii0]==null || spine[ii1]==null) { if (udv<0) g = -g; }  // Best guess
              else if (spine[i1].size()!=spine[i0].size() || spine[i1].size()!=spine[ii0].size() || spine[i1].size()!=spine[ii1].size()) { if (udv<0) g = -g; }  // Best guess
              else {
                int sz = spine[i1].size();
                float dots = 0.0f;
                for (k=0; k<sz; k++) {
                  u.eqPlus(spine[i1].get(k,w)).eqPlus(centroid[i1]).eqMinus(spine[i0].get(k,w)).eqMinus(centroid[i0]);
                  v.eqPlus(spine[ii1].get(k,w)).eqPlus(centroid[ii1]).eqMinus(spine[ii0].get(k,w)).eqMinus(centroid[ii0]);
                  dots += u.dot(v);
                }
                if (dots <= 0.0f) g = -g;   // Flow along body seems to agree that it's backwards
              }
            }
            else if (udv<0) g = -g;  // Best guess
          }
          for (;h<ss.i0;h++) quantity[h]=0;
        }
      }
    }

    // Fix up directions in the good parts
    for (Vec2I cp : cp_good) {
      f = 0.0f;
      for (i=cp.x ; i<=cp.y ; i++) {
        if (!Float.isNaN(quantity[i])) f += quantity[i];
      }
      if (f<0) for (i=cp.x ; i<=cp.y ; i++) quantity[i] = -quantity[i];
    }

    // Throw out the bad parts
    if (cp_good.length>0) {
      for (i=0; i<cp_good[0].x; i++) quantity[i] = Float.NaN;
      for (j=0; j<cp_good.length-1; j++) {
        for (i=cp_good[j].y+1; i<cp_good[j+1].x; i++) quantity[i] = Float.NaN;
      }
      for (i=cp_good[cp_good.length-1].y+1; i<quantity.length; i++) quantity[i] = Float.NaN;
    }
    else for (i=0; i<quantity.length; i++) quantity[i] = Float.NaN;
  }
  
  public void findDirectionBiasUnsegmented(float speed_window,float[] t) {
    float accuracy_limit = (float)(noise_estimate.average * Statistic.invnormcdf_tail( 0.05f/centroid.length ));
    float direction_scale = (float)Math.pow( body_length.average / accuracy_limit , 1.0/3.0 );
    findDirectionChange(speed_window,t);
    float[] reference = Arrays.copyOf(quantity,quantity.length);
    boolean changing = false;
    int sign = 1;
    int i,j,k,l,n;
    Vec2F u,v,w;
    float a;
    Vec2I sure = Vec2I.zero();
    Vec2S su = Vec2S.zero();
    Vec2S sv = Vec2S.zero();
    LinkedList<Vec2I> sure_list = new LinkedList<Vec2I>();
    float[] bigness = new float[reference.length];
    if (spine!=null) {
      for (i=0 ; i<spine.length ; i++) {
        bigness[i] = 0;
        if (spine[i]==null) continue;
        for (j=1 ; j<spine[i].size() ; j++) bigness[i] += (float)Math.sqrt( spine[i].get(j,su).dist2(spine[i].get(j-1,sv)) );
      }
    }
    else if (outline!=null) {
      for (i=0 ; i<outline.length ; i++) {
        if (outline[i]==null) { bigness[i] = Float.NaN; continue; }
        Vec2S[] ol = outline[i].unpack(false);
        float D = -1.0f;
        for (j=0 ; j<ol.length ; j++) {
          v = ol[j].toF();
          u = ol[ (j+ol.length/6)%ol.length ].toF();
          w = ol[ (j+5*ol.length/6)%ol.length ].toF();
          u.eqMinus(v);
          w.eqMinus(v);
          a = u.unitDot(w);
          if (a>D) D=a;
        }
        bigness[i] = D;
      }
    }
    else {
      for (i=0 ; i<centroid.length ; i++) {
        bigness[i] = (extent[i]==null || extent[i].x==0 || area[i]==0) ? 0 : (extent[i].y/extent[i].x)/area[i]; 
      }
    }
    float[] temp = Arrays.copyOf(bigness,bigness.length);
    Arrays.sort(temp);
    float smallness = temp[ (temp.length-1)/Math.min(200,temp.length) ];
    
    for (i=0 ; i<quantity.length ; i++) {
      if (quantity[i]<-0.9f) {
        for (j=i+1 ; j<reference.length ; j++) if (reference[j]>0) break;
        for (k=i-1 ; k>=0 ; k--) if (reference[k]<-0.9f || centroid[k].dist2(centroid[i])>accuracy_limit*accuracy_limit) break;
        for (l=j+1 ; l<reference.length ; l++) if (reference[l]<-0.9f || centroid[l].dist2(centroid[j])>accuracy_limit*accuracy_limit) break;
        for (int h=i ; h<j ; h++) quantity[i] = 0;
        if (k>=0 && l<reference.length) {
          u = centroid[i].opMinus(centroid[k]);
          v = centroid[l].opMinus(centroid[j]);
          if (u.unitDot(v)<0) sign = -sign;
        }
        for (int h=i ; h<j ; h++) { 
          if (bigness[h]<smallness) {
            sure_list.add(sure);
            sure = new Vec2I(j,j);
            break;
          }
        }
        i=j-1;
      }
      else {
        quantity[i] = sign;
        sure.y = i;
      }
    }
    sure_list.add(sure);
    
    for (Vec2I sureness : sure_list) {
      if (sureness.y-sureness.x<10) continue;
      
      float dist_forward = 0.0f;
      float dist_reverse = 0.0f;
      for (i=sureness.x+1 ; i<=sureness.y ; i++) {
        if (quantity[i]==0) continue;
        if (centroid[i]==null || centroid[i-1]==null) continue;
        float dist = (float)Math.max(0.0f , centroid[i].dist(centroid[i-1]) - noise_estimate.last_quartile);
        if (quantity[i]>0) dist_forward += dist;
        else if (quantity[i]<0) dist_reverse += dist;
      }
      
      if (dist_reverse > dist_forward) {
        for (i=sureness.x+1 ; i<=sureness.y ; i++) quantity[i] = -quantity[i];
      }
    }
  }
  
  public void findDirectionBias(float speed_window,float[] t,float min_travel) {
    if (segmentation==null) findDirectionBiasUnsegmented(speed_window,t);
    else findDirectionBiasSegmented(min_travel);
  }

  public void storeDirections() {
    directions = new DirectionSet(area.length);
    for (int i=0; i<area.length; i++) {
      directions.set(i, quantity[i]);
    }
  }

  public void loadDirections() {
    prepareForData(false);
    for (int i=0; i<quantity.length; i++) {
      quantity[i] = directions.getFloat(i);
    }
  }
  
  
  public void findDirectionChangeAtScale(float speed_window,float[] t,float scale) {
    prepareForData(false);

    float accuracy_limit = (float)(noise_estimate.average * Statistic.invnormcdf_tail( 0.05f/centroid.length ));
    float circle_size = accuracy_limit*scale;
    float circle_exclusive = Math.max( accuracy_limit , circle_size*0.5f );
    LinkedList<Vec2I> circle_list = new LinkedList<Vec2I>();

    // Cover the track with circles
    Vec2I circle = Vec2I.zero();
    int i,i0,j,n;
    for (i=i0=0 ; i<centroid.length ; i++) {
      if (centroid[i]==null) continue;
      if (centroid[circle.x]!=null) i0=circle.x;
      else if (centroid[i0]==null) { i0=i; continue; }
      if (centroid[i].dist(centroid[i0]) > circle_size) {
        circle.y = i;
        circle_list.add(circle);
        for (j=i ; j>i0 ; j--) { if (centroid[j]==null) continue; if (centroid[j].dist(centroid[i0]) < circle_exclusive) break; }
        circle = new Vec2I(j+1,i);
      }
    }
    circle.y=i;
    circle_list.add(circle);

    // Find the centers of those circles
    circles = circle_list.toArray(new Vec2I[circle_list.size()]);
    Vec2F[] centers = new Vec2F[circles.length];
    int[] numbers = new int[circles.length];
    Vec2F u,v,w;
    for (i=0 ; i<circles.length ; i++) {
      v = Vec2F.zero();
      for (n=0 , j=circles[i].x ; j<circles[i].y ; j++) {
        if (centroid[j]!=null) { v.eqPlus(centroid[j]); n++; }
      }
      if (n>0) v.eqDivide(n);
      centers[i] = v;
      numbers[i] = n;
    }
    Vec2F[] old_centers = Arrays.copyOf(centers,centers.length);

    // Pull back the coverage until the circles get adjacent-in-time chunks closest to their centers
    for (n=1 ; n<circles.length ; n++) {
      while (circles[n-1].y > circles[n].x) {
        boolean warn_x = false;
        boolean warn_y = false;
        if (centroid[circles[n].x]==null) {
          if (circles[n].y-circles[n].x >= circles[n-1].y-circles[n-1].x) { circles[n].x++; continue; }
          warn_x = true;
        }
        if (centroid[circles[n-1].y-1]==null) {
          if (circles[n].y-circles[n].x <= circles[n-1].y-circles[n-1].x) { circles[n-1].y--; continue; }
          warn_y = true;
        }
        if (warn_x || warn_y) continue;
        warn_x = warn_y = true;
        i = circles[n].x;
        if (centers[n].dist2(centroid[i]) >= centers[n-1].dist2(centroid[i]) && numbers[n]>1) {
          centers[n].eqTimes( numbers[n] ).eqMinus( centroid[i] ).eqDivide( numbers[n]-1 );
          numbers[n]--; circles[n].x++; warn_x = false;
        }
        j = circles[n-1].y-1;
        if (centers[n].dist2(centroid[j]) <= centers[n-1].dist2(centroid[j]) && numbers[n-1]>1 && circles[n-1].y>circles[n].x) {
          centers[n-1].eqTimes( numbers[n-1] ).eqMinus( centroid[j] ).eqDivide( numbers[n-1]-1 );
          numbers[n-1]--; circles[n-1].y--; warn_y = false;
        }
        if (warn_x && warn_y) {
          if (circles[n-1].y-1 > circles[n].x && circles[n-1].y-circles[n-1].x>1 && circles[n].y-circles[n].x>1) {
            centers[n].eqTimes( numbers[n] ).eqMinus( centroid[i] ).eqDivide( Math.max(1,numbers[n]-1) );
            centers[n-1].eqTimes( numbers[n-1] ).eqMinus( centroid[j] ).eqDivide( Math.max(1,numbers[n-1]-1) );
            numbers[n]--; numbers[n-1]--;
            circles[n].x++; circles[n-1].y--;
          }
          else if (circles[n-1].y-circles[n-1].x > circles[n].y-circles[n].x) {
            centers[n-1].eqTimes( numbers[n-1] ).eqMinus( centroid[j] ).eqDivide( Math.max(1,numbers[n-1]-1) );
            numbers[n-1]--; circles[n-1].y--;
          }
          else {
            centers[n].eqTimes( numbers[n] ).eqMinus( centroid[i] ).eqDivide( Math.max(1,numbers[n]-1) );
            numbers[n]--; circles[n].x++;
          }
        }
      }
    }

    // Make sure we really do cover everything
    circles[0].x = 0;
    for (n=1 ; n<circles.length ; n++) {
      if (circles[n].x != circles[n-1].y) {
        if (circles[n].y-circles[n].x < circles[n-1].y-circles[n-1].x) circles[n].x = circles[n-1].y;
        else circles[n-1].y = circles[n].x;
      }
    }
    circles[circles.length-1].y = centroid.length;

    // Find centers of half-overlapping circles
    Vec2F[] betweeners = new Vec2F[circles.length+1];
    n=0;
    betweeners[0] = Vec2F.zero();
    for (j=0 ; j<(circles[0].y+1)/2 ; j++) if (centroid[j]!=null) { betweeners[0].eqPlus(centroid[j]); n++; }
    if (n>0) betweeners[0].eqDivide(n);
    else betweeners[0].eq(centers[0]);
    for (i=1 ; i<circles.length ; i++) {
      n=0;
      betweeners[i] = Vec2F.zero();
      for (j = (circles[i-1].x+circles[i-1].y+1)/2 ; j < (circles[i].x+circles[i].y+1)/2 ; j++) {
        if (centroid[j]!=null) { betweeners[i].eqPlus(centroid[j]); n++; }
      }
      if (n>0) betweeners[i].eqDivide(n);
      else betweeners[i].eq(centers[i-1]).eqPlus(centers[i]).eqTimes(0.5f);
    }
    n=0;
    betweeners[centers.length] = Vec2F.zero();
    for (j = (circles[circles.length-1].x+circles[circles.length-1].y+1)/2 ; j<centroid.length ; j++) {
      if (centroid[j]!=null) { betweeners[centers.length].eqPlus(centroid[j]); n++; }
    }
    if (n>0) betweeners[centers.length].eqDivide(n);
    else betweeners[centers.length].eq(centers[centers.length-1]);

    // Finally find angles between circles spaced two apart (adjacent ones may have weird jitter).
    float a,b,c,d;
    for (i=0;i<circles.length;i++) {
      if (i<2 || i>circles.length-3) for (j=circles[i].x;j<circles[i].y;j++) quantity[j] = 0.0f;
      else {
        u = centers[i].opMinus(centers[i-2]);
        v = centers[i+2].opMinus(centers[i]);
        a = u.unitDot(v);
        u = centers[i].opMinus(centers[i-1]);
        v = centers[i+1].opMinus(centers[i]);
        b = u.unitDot(v);
        u = betweeners[i].opMinus(betweeners[i-2]);
        v = betweeners[i+2].opMinus(betweeners[i]);
        c = u.unitDot(v);
        u = betweeners[i+1].opMinus(betweeners[i-1]);
        v = betweeners[i+3].opMinus(betweeners[i+1]);
        d = u.unitDot(v);
        if (Math.abs(a)<Math.abs(b)) a = b;
        if (Math.abs(c)<Math.abs(a)) c = a;
        if (Math.abs(d)<Math.abs(a)) d = a;
        for (j=circles[i].x ; j<(1+circles[i].x+circles[i].y)/2 ; j++) quantity[j] = c;
        for ( ; j<circles[i].y ; j++) quantity[j] = d;
      }
    }
  }

  public void findDirectionChangeUnsegmented(float speed_window , float[] t) {
    float accuracy_limit = (float)(noise_estimate.average * Statistic.invnormcdf_tail( 0.05f/centroid.length ));
    float big_scale = (float)Math.pow( body_length.average / accuracy_limit , 1.0/3.0 );
    float small_scale = (float)(big_scale*Math.sqrt(0.5));
    findDirectionChangeAtScale(speed_window,t,big_scale);
  }

  public void findDirectionChangeSegmented() {
    if (segmentation==null) findSegmentation();
    if (segmentation==null) return;
    prepareForData(false);
       
    Vec2F u = new Vec2F();
    Vec2F v = new Vec2F();
    Style s,ss;
    int g,h,i,j,k;
    float f;
    
    for (j=0 ; j<quantity.length ; j++) quantity[j] = 0;
    for (h=i=j=0 ; i<segmentation.length ; i=j) {
      s = segmentation[i];
      if (!s.hasDirection()) j++;
      else {
        if (s.hasSeveralDirections()) for (k=1 ; k<s.endpoints.length-1 ; k++) quantity[s.endpoints[k]] = 1.0f;
          
        for (j=i+1 ; j<segmentation.length ; j++) if (segmentation[j].hasDirection()) break;
        if (j<segmentation.length) {
          ss = segmentation[j];
          if (ss.hasDirection()) {
            s.finalVector(u);
            ss.initialVector(v);
            f = 0.5f*(1-u.unitDot(v));
            quantity[s.i1] = 0.5f*f;
            quantity[ss.i0] = 0.5f*f;
          }
        }
      }
    }
  }
  
  public void findDirectionChange(float speed_window,float[] t) {
    if (segmentation==null) findDirectionChangeUnsegmented(speed_window,t);
    else findDirectionChangeSegmented();
  }


  public void findPostureConfusion() {
    prepareForData(false);
    double mlws = 0.0;
    double olls = 0.0;
    double ollss = 0.0;
    int nm = 0;
    int no = 0;

    // Perimeter metric for folding back on self--outline gets shorter as at least part of animal gets wider
    //findSpineLength();
    //float[] spinelen = Arrays.copyOf(quantity,quantity.length);
    for (int i=0; i<quantity.length; i++) {
      if (centroid[i]==null || !loc_okay(centroid[i])) continue;
      if (spine!=null && spine[i]!=null && !Float.isNaN(spine[i].width(0))) {
        for (int j=1; j<spine[i].size()-1; j++) {
          mlws += spine[i].width(j);
          nm++;
        }
      }
      else {
        mlws += area[i]/Math.max(1.0f,extent[i].x);
        nm++;
      }
      if (outline!=null && outline[i]!=null) {
        Vec2F u = Vec2F.zero();
        Vec2F v = Vec2F.zero();
        float ol = 0.0f;
        outline[i].get(0, u);
        for (int j=5; j<outline[i].size(); j+=5) {
          ol += u.dist(outline[i].get(j, v));
          u.eq(v);
        }
        ol += u.dist(outline[i].get(0,v));
        quantity[i] = ol;
        olls += ol;
        ollss += ol*ol;
        no++;
      }
      else quantity[i] = 0.0f;
    }
    if (nm<1) nm = 1;
    if (no<1) no = 1;
    float wavg = (float)(mlws/nm);
    float wthick = wavg*1.5f;
    float oavg = (float)(olls/no);
    float oshort = 2.0f*oavg/3.0f + wavg;
    float odev = oavg - (short)Math.sqrt(ollss/no - oavg*oavg);
    if (odev < oshort) oshort = odev;

    Vec2F u = new Vec2F();
    Vec2F v = new Vec2F();
    Vec2F w = new Vec2F();

    // Only use perimeter metric if object is skinny enough for it to work geometrically--if we have no outline and/or spine at all, this should fail!
    if (wavg > 0 && wavg*5 < oavg && spine != null && outline != null) {
      for (int i=0; i<quantity.length; i++) {
        if (spine[i]==null || outline[i]==null) quantity[i]=1.0f;
        else {
          int m = 0;
          for (int j=1;j<spine[i].size()-1; j++) if (spine[i].width(j)>wthick) m++;
          if (2*m >= spine[i].size()-2 && quantity[i]<oshort) quantity[i]=1.0f;
          else if (i>0 && spine[i-1] != null) {
            // Check to make sure spine isn't jumping absurdly much
            m = 0;
            for (int j=1, k = spine[i].size()-2;j<spine[i].size()-1; j++,k--) {
              spine[i].get(j,u);
              if (Math.min(u.dist2(spine[i-1].get(j,v)), u.dist2(spine[i-1].get(k,w))) > wavg*wthick) m += 1;
            }
            if (3*m > spine[i].size()-2) quantity[i]=1.0f; else quantity[i]=0.0f;
          }
          else quantity[i] = 0.0f;
        }
      }
    }
    else if (spine!=null) {
      // Fall back to metric of endpoints jumping significantly far--works for folds, but not for very tight turns; sensitive to noise
      quantity[0] = 0.0f;
      // Find how far apart endpoints are along the best-fit line
      for (int i=0; i<spine.length; i++) {
        if (spine[i]==null) quantity[i] = 1.0f;
        else {
          spine[i].get(0,u);
          spine[i].get(spine[i].size()-1,v);
          w.eq(bearing[i]).eqNorm();
          float uw = u.dot(w);
          float vw = v.dot(w);
          u.eq(w).eqTimes(uw);
          v.eq(w).eqTimes(vw);
          quantity[i] = u.dist2(v)/Math.max(1.0f,extent[i].x*extent[i].x);
        }
      }
      for (int i=1; i<spine.length; i++) {
        if (quantity[i-1]<0.6f && quantity[i]>0.9f || quantity[i-1]>0.9f && quantity[i]<0.6f) {
          if (spine[i-1]!=null && spine[i]!=null) {
            float d0 = spine[i-1].get(0,u).dist(spine[i].get(0,v))/Math.max(1.0f,Math.min(extent[i-1].x,extent[i].x));
            float dn = spine[i-1].get(spine[i-1].size()-1,u).dist(spine[i].get(spine[i].size()-1,v))/Math.max(1.0f,Math.min(extent[i-1].x,extent[i].x));
            if (Math.max(d0,dn) > 0.3f) {
              quantity[i-1] = 1.0f;
              quantity[i] = 1.0f;
              i++;
            }
            else quantity[i-1] = 0.0f;
          }
          else quantity[i-1] = 0.0f;
        }
        else quantity[i-1] = 0.0f;
      }
    }
    else {
      // Fall back to big changes in bearing--bad idea, really unreliable, but what else can we do?
      quantity[0] = 0.0f;
      for (int i=1; i<bearing.length; i++) {
        if (Math.abs(bearing[i].unitDot(bearing[i-1]))<0.9f) { quantity[i] = quantity[i-1] = 0.0f; i++; }
        else quantity[i-1] = 0.0f;
      }
    }
    // Single frame glitches are okay--might be a tap or something.
    for (int i=1; i<quantity.length-1; i++) {
      if (quantity[i]>0.0f && quantity[i-1]==0.0f && quantity[i+1]==0.0f) quantity[i]=0.0f;
    }
  }

  public void findDirectionConfusion() {
    findPostureConfusion();
    if (segmentation==null) findSegmentation();
    double d = 0.0;
    int i, i0 = 0, i1 = 0;
    for (int j=0; j<segmentation.length; j++) {
      for (i=segmentation[j].i0; i<=segmentation[j].i1; i++) {
        if (quantity[i]==1.0f) break;
      }
      if (i<=segmentation[j].i1 || i>=quantity.length) {
        i1 = i-1;
        if (d > 2*body_length.average) for (i=i0;i<=i1;i++) quantity[i]=0.0f;
        else for (i=i0;i<=i1;i++) quantity[i]=1.0f;
        for (i=segmentation[j].i1; i>i1; i--) if (quantity[i]==1.0) break;
        i0 = i1+1;
        i1 = i;
        for (i=i0; i<=i1; i++) quantity[i]=1.0f;
        i0 = i;
        d = 0.0;
      }
      else d += segmentation[j].distanceTraversed();
    }
  }


  public void findCumulativePath(float speed_window,float[] t,float min_travel) {
    if (directions==null) quantityIsBias(t,speed_window,min_travel,false);
    double cud;
    Style seg;
    Vec2F u = new Vec2F();
    Vec2F v = new Vec2F();
    Vec2F w = new Vec2F();
    int i0,i1,segi = 0;
    for (i0=0; i0<area.length; i0=i1) {
      while (i0 < area.length && directions.isInvalid(i0)) quantity[i0++] = Float.NaN;
      if (i0 >= area.length) break;
      i1=i0;
      while (i1 < area.length && directions.isValid(i1)) i1++;
      cud = 0;
      quantity[i0] = (float)cud;
      seg = (segmentation==null) ? null : segmentation[segi = indexToSegment(i0)];
      for (int i=i0+1; i<i1; i++) {
        if (directions.isStill(i)) quantity[i] = (float)cud;
        if (seg!=null && seg.i1 < i) {
          segi++;
          if (segi<segmentation.length) seg = segmentation[segi]; else seg = null;
        }
        u.eq(centroid[i-1]);
        v.eq(centroid[i]);
        if (seg!=null) {
          if (seg.kind == Styled.Arc || seg.kind == Styled.Straight) {
            seg.snapToLine(u); seg.snapToLine(v);
            if (directions.isForward(i)) cud += v.dist(u);
            else cud -= v.dist(u);
          }
        }
        else {
          w.eq(bearing[i]).eqNorm();
          v.eqMinus(u);
          if (directions.isForward(i)) cud += Math.abs(v.dot(w));
          else cud -= Math.abs(v.dot(w));
        }
        quantity[i] = (float)cud;
      }
    }
  }
  
  
  public List<ReversalEvent> extractReversals(float speed_window,float[] t,boolean bias_loaded)
  {
//    if (!bias_loaded) findDirectionBias(speed_window,t);
    
    LinkedList<ReversalEvent> llre = new LinkedList<ReversalEvent>();
    
    if (quantity==null) return llre;
    
    boolean can_back_up = false;
    boolean in_reversal = false;
    int reversal_i0 = -1;
    for (int i=0;i<quantity.length;i++)
    {
      if (!in_reversal)
      {
        if (can_back_up && quantity[i] < -0.25)
        {
          in_reversal = true;
          can_back_up = false;
          reversal_i0 = i;
          llre.add( new ReversalEvent(ID,t[i+first_frame],t[i+first_frame],0) );
        }
        else if (quantity[i] >= -0.1) can_back_up=true;
      }
      else
      {
        if (quantity[i] >= -0.1)
        {
          ReversalEvent re = llre.getLast();
          re.t1 = t[i+first_frame];
          re.path_length = 0f;
          for (int j = reversal_i0 ; j<i-1 ; j++) re.path_length += centroid[j].dist(centroid[j+1]);
          in_reversal = false;
        }
      }
    }
    
    if (llre.size()>0 && llre.getLast().duration()==0) llre.removeLast();
    
    return llre;
  }
  public List<ReversalEvent> extractReversals(float speed_window,float[] t) { return extractReversals(speed_window,t,false); }
  
  public void findAbstractSpeed(float speed_window,float[] t,Vec2F[] location,Metric metric,float normalization)
  {
    prepareForData(false);
    
    int i,j,k;
    double a,b;
    float s,max_s;
    float frac = 0.0f;
    Vec2F u = new Vec2F();
    Vec2F v = new Vec2F();
    
    for (i=0 ; i<location.length ; i++)
    {
      quantity[i] = Float.NaN;
      if (location[i]==null || !loc_okay(centroid[i])) continue;
      
      a = seek(t[i+first_frame]-0.5f*speed_window , t);
      if (Double.isNaN(a)) continue;
      j = (int)Math.floor(a);
      if (j+1>i || location[j]==null || location[j+1]==null) continue;
      frac = (float)(a-j);
      u.eq(location[j]).eqWeightedSum(1.0f-frac , frac , location[j+1]);
      
      b = seek(t[i+first_frame]+0.5f*speed_window , t);
      if (Double.isNaN(b)) continue;
      k = (int)Math.ceil(b);
      if (k-1<i || location[k]==null || location[k-1]==null) continue;
      frac = (float)(k-b);
      v.eq(location[k]).eqWeightedSum(1.0f-frac , frac , location[k-1]);
      
      switch (metric) {
        case DIST:
        case DISTX:
        case DISTY:
        case CRAB:
          max_s = v.dist( u ); break;
        case ANGLE:
          max_s = 1.0f-0.5f*v.unitDot( u ); break;
        default:
          max_s = 0.0f;
      }
      
      j++;
      k--;
      while (j<k) {
        switch (metric) {
          case DIST:
          case DISTX:
          case DISTY:
          case CRAB:
            s = location[k].dist( location[j] ); break;
          case ANGLE: s = 1.0f-0.5f*location[k].unitDot( location[j] ); break;
          default:    s = 0.0f;
        }
        if (s>max_s) {
          max_s = s;
          switch (metric) {
            case DISTX:
            case DISTY:
            case CRAB:
              u.eq( location[j] );
              v.eq( location[k] );
              break;
            default:
          }
        }
        if (i-j > k-i) j++;
        else k--;
      }
      
      switch (metric) {
        case DISTX: quantity[i] = (v.x-u.x) * normalization; break;
        case DISTY: quantity[i] = (v.y-u.y) * normalization; break;
        case CRAB: v.eqMinus(u); u.eq( bearing[i] ).eqNorm(); u.eqTimes(v.dot(u)); v.eqMinus(u); quantity[i] = v.length()*normalization; break;
        default:    quantity[i] = max_s * normalization;
      }
    }
  }
  
  public void findEndWiggle()
  {
    prepareForData(true);
    if (spine==null) return;
    
    int i,j;
    Vec2F v = new Vec2F();
    Vec2F body_angle = new Vec2F();
    Vec2F front_angle = new Vec2F();
    Vec2F back_angle = new Vec2F();
    float front_wiggle;
    float back_wiggle;
    for (i=0 ; i<centroid.length ; i++)
    {
      if ( spine[i]==null || spine[i].size() < 3 || !loc_okay(centroid[i])) { quantity[i] = Float.NaN; continue; }

      spine[i].get(0,front_angle);
      j = (int)(spine[i].size()*0.2 + 0.5);
      if (j==0) j=1;
      front_angle.eqMinus(spine[i].get(j,v));
      j = (int)(spine[i].size()*0.33 + 0.5);
      if (j==0) j=1;
      spine[i].get(j,body_angle);
      body_angle.eqMinus( spine[i].get(spine[i].size()-1,v) );
      if (front_angle.length2()==0.0f || body_angle.length2()==0.0f) front_wiggle=1.0f;
      else front_wiggle = front_angle.unitDot( body_angle );

      spine[i].get(spine[i].size()-1, back_angle);
      j = (int)(spine[i].size()*0.8 + 0.5);
      if (j==spine[i].size()-1) j--;
      back_angle.eqMinus( spine[i].get(j,v) );
      j = (int)(spine[i].size()*0.67 + 0.5);
      if (j==spine[i].size()-1) j--;
      spine[i].get(j,body_angle);
      body_angle.eqMinus( spine[i].get(0,v) );
      if (back_angle.length2()==0.0f || body_angle.length2()==0.0f) back_wiggle=1.0f;
      else back_wiggle = back_angle.unitDot( body_angle );
      
      if (front_wiggle < back_wiggle) quantity[i] = (float)( Math.acos(front_wiggle) );
      else quantity[i] = (float)( Math.acos(back_wiggle) );
    }
  }
  
  public void findBodyWiggle()
  {
    prepareForData(true);
    if (spine==null) return;
    
    float angle_sum;
    float step;
    float ii;
    int i,j,k;
    int n;
    Vec2F u = new Vec2F();
    Vec2F v = new Vec2F();
    Vec2F w = new Vec2F();
    for (n=0 ; n<centroid.length ; n++)
    {
      Spine s = spine[n];
      if ( s==null || s.size() < 6 || !loc_okay(centroid[n])) { quantity[n] = Float.NaN; continue; }
      
      angle_sum = 0.0f;
      step = (s.size()-1)/5.0f;
      for (ii=0.0f ; Math.round(ii+2*step) < s.size() ; ii+=step)
      {
        i = Math.round(ii);
        j = Math.round(ii+step);
        k = Math.round(ii+2*step);
        s.get(j,u).eqMinus( s.get(i,w) ).eqNorm();
        s.get(k,v).eqMinus( s.get(j,w) ).eqNorm();
        angle_sum += (float)Math.acos( u.dot(v) );
      }
      quantity[n] = angle_sum / 4.0f;
    }
  }
  
  public void findSpineLength()
  {
    prepareForData(true);
    if (spine==null) return;
    
    float d;
    Vec2S u = Vec2S.zero();
    Vec2S v = Vec2S.zero();
    for (int i=0 ; i<centroid.length ; i++)
    {
      Spine s = spine[i];
      if ( s==null || s.size() < 2 || !loc_okay(centroid[i])) { quantity[i] = Float.NaN; continue; }
      d = 0.0f;
      for (int j=1 ; j<s.size() ; j++) d += Math.sqrt( s.get(j,v).dist2( s.get(j-1,u) ) );
      quantity[i] = d;
    }
  }
  public void normalizeSpineLength()
  {
    float[] temp = new float[quantity.length];
    System.arraycopy(quantity, 0, temp, 0, quantity.length);
    body_spine = new Statistic(temp,0,quantity.length);
    for (int i=0;i<quantity.length;i++) if (!Float.isNaN(quantity[i])) quantity[i] /= body_spine.average;
  }

  // The following two methods should be identical save for input data type
  public static float signedAngle(Vec2S a, Vec2S b) {
    float f = b.unitDot(a) + 1.0f;
    if (a.X(b) < 0) return -f; else return f;  // Negative cross product means curve is concave
  }
  public static float signedAngle(Vec2F a, Vec2F b) {
    float f = b.unitDot(a) + 1.0f;
    if (a.X(b) < 0) return -f; else return f; // Negative cross product means curve is concave
  }

  // The following three methods should be identical save for data type (be aware of need for conversion)
  public static float[] getBodyAngles(Vec2S[] pts,float[] angles,float bodyfraction,int len) {
    Vec2F u = new Vec2F(0,0);
    Vec2F v = new Vec2F(0,0);
    Vec2F w = new Vec2F(0,0);
    int i,j,k;
    
    if (angles==null || angles.length<pts.length) angles = new float[pts.length];
    
    int outlinesteps = Math.round(0.5f*len*bodyfraction);
    if (outlinesteps*2 >= len) outlinesteps = len/2 - 1;
    if (outlinesteps<1) outlinesteps = 1;
    
    for (j=0 ; j<len ; j++) {
      i = (j - outlinesteps);
      if (i==j) i--;
      if (i<0) i += len;
      k = (j + outlinesteps);
      if (k==j) k++;
      if (k>=len) k -= len;
      v.eq( pts[j] );
      u.eq( pts[i] ).eqMinus( v );
      w.eq( pts[k] ).eqMinus( v );
      angles[j] = signedAngle(u,w);
    }
    
    return angles;
  }
  public static float[] getBodyAngles(Vec2F[] pts, float[] angles, float bodyfraction, int len) {
    Vec2F u = new Vec2F(0,0);
    Vec2F w = new Vec2F(0,0);
    int i,j,k;

    if (angles==null || angles.length<pts.length) angles = new float[pts.length];

    int outlinesteps = Math.round(0.5f*len*bodyfraction);
    if (outlinesteps*2 >= len) outlinesteps = len/2 - 1;
    if (outlinesteps<1) outlinesteps = 1;

    for (j=0 ; j<len ; j++) {
      i = (j - outlinesteps);
      if (i==j) i--;
      if (i<0) i += len;
      k = (j + outlinesteps);
      if (k==j) k++;
      if (k>=len) k -= len;
      u.eq( pts[i] ).eqMinus( pts[j] );
      w.eq( pts[k] ).eqMinus( pts[j] );
      angles[j] = signedAngle(u,w);
    }

    return angles;
  }
  public static float[] getBodyAngles(float[] x, float[] y, float[] angles, float bodyfraction, int len) {
    Vec2F u = new Vec2F(0,0);
    Vec2F v = new Vec2F(0,0);
    Vec2F w = new Vec2F(0,0);
    int i,j,k;

    if (angles==null || angles.length<x.length) angles = new float[x.length];

    int outlinesteps = Math.round(0.5f*len*bodyfraction);
    if (outlinesteps*2 >= len) outlinesteps = len/2 - 1;
    if (outlinesteps<1) outlinesteps = 1;

    for (j=0 ; j<len ; j++) {
      i = (j - outlinesteps);
      if (i==j) i--;
      if (i<0) i += len;
      k = (j + outlinesteps);
      if (k==j) k++;
      if (k>=len) k -= len;
      v.eq( x[j], y[j] );
      u.eq( x[i], y[i] ).eqMinus( v );
      w.eq( x[k], y[k] ).eqMinus( v );
      angles[j] = signedAngle(u,w);
    }

    return angles;
  }
  
  public static Vec2I getBestEndpoints(float[] angles,int len) {
    int i,j;
    float f,g;
    float max_angle = angles[0];
    int max_i = 0;
    for (j=1 ; j<len ; j++) {
      if (angles[j]>max_angle) { max_i=j; max_angle = angles[j]; }
    }
    float second_angle = -2.0f;
    int second_i = 0;
    for (j=0 ; j<len ; j++) {
      g = Math.min( Math.abs(j-max_i) , len - Math.abs(j-max_i) )*(2.0f/len);  // Fraction of the max distance to first point
      f = angles[j]*g - 2.0f*(1.0f-g) ;  // Penalize towards worst possible score (-2) the closer you are to the first point
      if (f > second_angle) { second_i=j ; second_angle = f; }
    }
    return new Vec2I(max_i,second_i);
  }
    
  public void findOutlineWidth()
  {
    prepareForData(true);
    if (outline==null) return;
    
    float[] angles = null;
    Vec2S[] pts = null;
    int n = -1;
    int i,j,k;
    float f;
    for (int h = 0; h < outline.length; h++) {
      Outline o = outline[h];
      n++;
      if (o==null) { quantity[n] = Float.NaN; continue; }

      if (spine[h]!=null && spine[h].size()>4 && !Float.isNaN(spine[h].width(0))) {
        int skip = Math.max((int)Math.round(spine[h].size()*0.2),2);
        float sum = 0.0f;
        for (i = skip; i < spine[h].size()-skip; i++) sum += spine[h].width(i);
        quantity[n] = sum / (spine[h].size()-2*skip);
      }
      else {
        pts = o.unpack(pts);

        angles = getBodyAngles(pts,angles,endpoint_angle_fraction,o.size());
        Vec2I ep = getBestEndpoints(angles,o.size());

        float sum = 0.0f;
        for (f=0.2f ; f<0.85f ; f+=0.1f) {
          i = Math.round(f*ep.x + (1.0f-f)*ep.y);
          if (ep.x > ep.y) j = Math.round(f*ep.x + (1.0f-f)*(ep.y+o.size()));
          else j = Math.round(f*ep.x + (1.0f-f)*(ep.y-o.size()));
          if (j<0) j += o.size();
          else if (j>=o.size()) j -= o.size();
          sum += (float)Math.sqrt( (double)pts[i].dist2( pts[j] ) );
        }
        quantity[n] = sum / 7.0f;
      }
    }
  }

  // Qxfw is an undocumented output type used for testing.
  // It will vary wildly from build to build; do not trust it for anything!
  public void addRetro(float[] t,int i,float dt,float value) {
    quantity[i] += value;
    for (int j=i-1 ; j>=0 && t[i+first_frame]-t[j+first_frame] <= dt ; j--) quantity[j] += value;
  }
  public boolean hasBackwards(float[] bak,int i0,int i1) {
    for (int i=i0 ; i<=i1 ; i++) if (bak[i]>0) return true;
    return false;
  }
  public class Qxfw {
    public int i0;
    public int i1;
    public int j0;
    public double traveled;
    public boolean backwards;
    public Qxfw(int i,int j,float[] bias) {
      Style s = segmentation[i];
      i0 = i;
      j0 = j;
      if (s.directions()==0) { backwards = false; traveled = 0.0; }
      else if (s.directions()==1) { backwards = hasBackwards(bias , s.i0+1 , s.i1-1); traveled = s.distanceTraversed(0); }
      else { backwards = hasBackwards(bias , s.endpoints[j0]+1 , s.endpoints[j0+1]-1); traveled = s.distanceTraversed(j0); }
      i1 = i0;
      if (j0+1 >= s.directions() && i0+1<segmentation.length) {
        boolean different = false;
        do {
          i1++;
          Style ss = segmentation[i1];
          if (backwards && !ss.isLine()) { i1--; different=true; }
          else if (ss.dotWith(s)<-0.33f) { i1--; different=true; }
          else if (backwards != hasBackwards(bias , ss.i0+1 , (ss.endpoints==null || ss.endpoints.length<2) ? ss.i1-1 : ss.endpoints[1]-1)) {
            i1--; different=true;
          }
          else if (ss.directions()>1) {
            traveled += ss.distanceTraversed(0);
            different=true;
          }
          else if (ss.directions()>0) traveled += ss.distanceTraversed(0);
        } while (i1+1 < segmentation.length && !different);
      }
    }
    public int nextI() {
      if (segmentation[i1].directions() > j0+1) return i1;
      else return i1+1;
    }
    public int nextJ() {
      if (segmentation[i1].directions() > j0+1) return j0+1;
      else return 0;
    }
  }
  public void findQxfw(Choreography chore) {
    findPostureConfusion();
  }
  public void findQxfwFloating(Choreography chore) {
    prepareForData(false);
    Vec2F u = new Vec2F();
    Vec2F v = new Vec2F();
    Vec2F w = new Vec2F();
    for (int i=0; i<area.length ; i++) {
      if (spine==null) quantity[i] = Float.NaN;
      else if (spine[i]==null) quantity[i] = Float.NaN;
      spine[i].get(0,u);
      spine[i].get(spine[i].size()-1,v);
      if (w.dist2(u) < w.dist2(v)) w.eq(u);
      else w.eq(v);
      quantity[i] = w.x;
    }
  }
  public void findQxfwOld2(Choreography chore) {
    prepareForData(false);
    float excurse = maximumExcursion();
    for (int n=0 ; n<quantity.length ; n++) quantity[n] = excurse;
    if (excurse > -100000) return;
    
    int i,j,k;
    for (i=0; i<area.length; i++) quantity[i]=0;
    findDirectionBias(chore.speed_window,chore.times,chore.minTravelPx(this));
    float[] bias = new float[area.length];
    for (i=0; i<area.length ; i++) {
      bias[i] = Math.max(0,-quantity[i]);
      quantity[i] = -100.0f;
    }
    
    double traveled = 0.0;
    LinkedList<Qxfw> pieces = new LinkedList<Qxfw>();
    pieces.add( new Qxfw(0,0,bias) );
    while (pieces.peekLast().nextI() < segmentation.length) {
      pieces.add( new Qxfw( pieces.peekLast().nextI() , pieces.peekLast().nextJ() , bias ) );
    }
    int backwardsCount = 0;
    for (Qxfw q : pieces) {
      traveled += q.traveled;
      /*
      if (q.backwards) {
        backwardsCount++;
        i = (q.j0>0) ? segmentation[q.i0].endpoints[q.j0] : segmentation[q.i0].i0;
        if (q.i1==q.i0) k = (segmentation[q.i0].directions()>1) ? segmentation[q.i0].endpoints[q.j0+1] : segmentation[q.i0].i1;
        else k = (segmentation[q.i1].directions()>1) ? segmentation[q.i1].endpoints[1] : segmentation[q.i1].i1;
        for (j=i ; j<=k ; j++) quantity[j] = (float)q.traveled*chore.mm_per_pixel;
        //System.out.printf("%d  %.3f  %.2f %.2f\n",ID,q.traveled*chore.mm_per_pixel,chore.times[i+first_frame],chore.times[k+first_frame]);
      }
      */
    }
    for (i=0 ; i<quantity.length ; i++) quantity[i] = (float)traveled;
  }
  public void findQxfwOld() {
    prepareForData(false);
    
    int i,j,k;
    for (i=0; i<area.length; i++) quantity[i]=0;
    findDirectionBias(chore.speed_window,chore.times,chore.minTravelPx(this));
    float[] bias = new float[area.length];
    for (i=0; i<area.length ; i++) {
      bias[i] = Math.max(0,-quantity[i]);
      quantity[i] = 0.0f;
    }
    
    for (i=j=0 ; i<segmentation.length ; i=j) {
      Style s = segmentation[i];
      if (!s.isLine() && s.kind!=Styled.Dwell) { j++; continue; }
      Style ss = s;
      if (s.isLine()) {
        if (s.endpoints!=null && s.endpoints.length>2) {
          for (k=1;k<s.endpoints.length-1;k++) {
            if (hasBackwards(bias,s.endpoints[k]+1,s.endpoints[k+1]-1)) addRetro(chore.times,s.endpoints[k],1.0f,(float)s.distanceTraversed(k));
          }
        }
      }
      boolean internal = false;
      for (j++ ; j<segmentation.length ; j++) {
        ss = segmentation[j];
        if (s.isLine() && ss.isLine() && ss.dotWith(s)<-0.33f) break;
        if (ss.isLine() && ss.endpoints!=null && ss.endpoints.length>2) { internal=true; break; }
        if (s.isLine() && ss.kind==Styled.Dwell) break;
        if (s.kind==Styled.Dwell && ss.isLine()) break;
      }
      if (j<segmentation.length && ! internal) {
        if (hasBackwards(bias , ss.i0 , (ss.endpoints!=null && ss.endpoints.length>1) ? ss.endpoints[1] : ss.i1)) {
          double totalBackwards = ss.distanceTraversed(0);
          for (k=j+1 ; k<segmentation.length ; k++) {
            Style sss = segmentation[k];
            if (!sss.isLine()) break;
            if (hasBackwards(bias , sss.i0+1 , (sss.endpoints!=null && sss.endpoints.length>1) ? sss.endpoints[1]-1 : sss.i1-1)) {
              totalBackwards += sss.distanceTraversed(0);
              if (sss.endpoints!=null) break;
            }
            else break;
          }
          addRetro(chore.times,ss.i0,1.0f,(float)totalBackwards);
        }
      }
    }
    
    for (i=j=k=0 ; i<bias.length ; i=k) {
      j = i;
      while (j<bias.length && !Float.isNaN(bias[j])) j++;
      k = j;
      while (k<bias.length && Float.isNaN(bias[k])) k++;
      if (j<bias.length) {
        float t1 = t(j);
        j--;
        while (j>=0 && t1-t(j) < 1.0f) j--;
        j++;
        for (int h=j ; h<k ; h++) quantity[h] = Float.NaN;
      }
    }
  }

  public void allUnload() {
    prepareForData(false);
    loaded_jitter = 0.0f;
    loaded_time.already = false;
    loaded_frame.already = false;
    loaded_constant.already = false;
    loaded_area.already = false;
    loaded_speed.already = false;
    loaded_angular.already = false;
    loaded_length.already = false;
    loaded_width.already = false;
    loaded_aspect.already = false;
    loaded_midline.already = false;
    loaded_outlinewidth.already = false;
    loaded_kink.already = false;
    loaded_bias.already = false;
    loaded_path.already = false;
    loaded_curve.already = false;
    loaded_dirchange.already = false;
    loaded_phaseadvance.already = false;
    loaded_x.already = false;
    loaded_y.already = false;
    loaded_vx.already = false;
    loaded_vy.already = false;
    loaded_theta.already = false;
    loaded_crab.already = false;
    loaded_qxfw.already = false;
    loaded_stim1.already = false;
    loaded_stim2.already = false;
    loaded_stim3.already = false;
    loaded_stim4.already = false;
    for (Preloaded pc : loaded_custom) pc.already = false;
  }
  
  public void quantityMult(float f) {
    float oldj = loaded_jitter;
    allUnload();
    for (int i=0;i<quantity.length;i++) quantity[i] *= f;
    loaded_jitter = oldj * f;
  }
  
  public void quantityIs(float f) {
    if (loaded_constant.already && f==quantity[0]) return;
    allUnload();
    for (int i=0;i<quantity.length;i++) quantity[i] = f;
    loaded_constant.already = true;
  }
  
  public void quantityIsTime(float[] times,boolean jitting) {
    if (loaded_time.already) return;
    allUnload();
    for (int i=first_frame;i<=last_frame;i++) quantity[i-first_frame] = times[i];
    loaded_time.already = true;
    if (jitting) loaded_time.setJit();
  }
  
  public void quantityIsFrame() {
    if (loaded_frame.already) return;
    allUnload();
    for (int i=first_frame;i<=last_frame;i++) quantity[i-first_frame] = i;
    loaded_frame.already = true;
  }
  
  public void quantityIsArea(boolean jitting) {
    if (loaded_area.already) return;
    allUnload();
    for (int i=0;i<quantity.length;i++) quantity[i] = area[i];
    loaded_area.already = true;
    if (jitting) loaded_area.setJit();
  }
  
  public void quantityIsSpeed(float[] times , float speed_window , boolean normalize, boolean jitting) {
    if (loaded_speed.already) return;
    allUnload();
    findAbstractSpeed(speed_window,times,centroid,Metric.DIST,((normalize)?1.0f/meanBodyLengthEstimate():1.0f)/speed_window);
    loaded_speed.already = true;
    if (jitting) loaded_speed.setJit();
  }
  
  public void quantityIsAngularSpeed(float[] times,float speed_window, boolean jitting) {
    if (loaded_angular.already) return;
    allUnload();
    findAbstractSpeed(speed_window,times,bearing,Metric.ANGLE,1.0f);
    for (int i=0 ; i<bearing.length ; i++) {
      if (Float.isNaN(quantity[i])) continue;
      quantity[i] = (float)Math.acos( Math.min(1.0 , Math.max(-1.0 , 2.0*(1.0 - quantity[i])) ) )/speed_window;  // To radians
    }
    loaded_angular.already = true;
    if (jitting) loaded_angular.setJit();
  }
  
  public void quantityIsLength(boolean jitting) {
    if (loaded_length.already) return;
    allUnload();
    for (int i=0;i<quantity.length;i++) quantity[i] = (extent[i]==null) ? Float.NaN : extent[i].x;
    loaded_length.already = true;
    if (jitting) loaded_length.setJit();
  }
  
  public void quantityIsWidth(boolean jitting) {
    if (loaded_width.already) return;
    allUnload();
    for (int i=0;i<quantity.length;i++) quantity[i] = (extent[i]==null) ? Float.NaN : extent[i].y;
    loaded_width.already = true;
    if (jitting) loaded_width.setJit();
  }
  
  public void quantityIsAspect(boolean jitting) {
    if (loaded_aspect.already) return;
    allUnload();
    for (int i=0;i<quantity.length;i++) quantity[i] = (extent[i]==null || extent[i].x==0) ? Float.NaN : extent[i].y/extent[i].x;
    loaded_aspect.already = true;
    if (jitting) loaded_aspect.setJit();
  }
  
  public void quantityIsMidline(boolean jitting) {
    if (loaded_midline.already) return;
    allUnload();
    findSpineLength();
    loaded_midline.already = true;
    if (jitting) loaded_midline.setJit();
  }
  
  public void quantityIsOutlineWidth(boolean jitting) {
    if (loaded_outlinewidth.already) return;
    allUnload();
    findOutlineWidth();
    loaded_outlinewidth.already = true;
    if (jitting) loaded_outlinewidth.setJit();
  }
  
  public void quantityIsKink(boolean jitting) {
    if (loaded_kink.already) return;
    allUnload();
    findEndWiggle();
    loaded_kink.already = true;
    if (jitting) loaded_kink.setJit();
  }
  
  public void quantityIsBias(float[] times,float speed_window,float min_travel,boolean again) {
    if (loaded_bias.already && !again) {
      // System.out.println("Already got bias.");
      return;
    }
    allUnload();
    if (directions!=null && !again) {
      // System.out.println("Loading directions");
      loadDirections();
    }
    else {
      // System.out.println("Finding and storing directions");
      findDirectionBias(speed_window,times,min_travel);
      storeDirections();
    }
    loaded_bias.already = true;
  }
  
  public void quantityIsPath(float[] times,float speed_window,float min_travel) {
    if (loaded_path.already) return;
    findCumulativePath(speed_window,times,min_travel);
    allUnload();
    loaded_path.already = true;
  }

  public void quantityIsCurve(boolean jitting) {
    if (loaded_curve.already) return;
    allUnload();
    findBodyWiggle();
    loaded_curve.already = true;
    if (jitting) loaded_curve.setJit();
  }
  
  public void quantityIsDirectionChange(float[] times,float speed_window) {
    if (loaded_dirchange.already) return;
    allUnload();
    findDirectionChange(speed_window,times);
    loaded_dirchange.already = true;
  }
  
  public void quantityIsX(boolean jitting) {
    if (loaded_x.already) return;
    allUnload();
    for (int i=0;i<quantity.length;i++) quantity[i] = (centroid[i]==null) ? Float.NaN : centroid[i].x;
    loaded_x.already = true;
    if (jitting) loaded_x.setJit();
  }
  
  public void quantityIsY(boolean jitting) {
    if (loaded_y.already) return;
    allUnload();
    for (int i=0;i<quantity.length;i++) quantity[i] = (centroid[i]==null) ? Float.NaN : centroid[i].y;
    loaded_y.already = true;
    if (jitting) loaded_y.setJit();
  }
  
  public void quantityIsVx(float[] times,float speed_window,boolean normalize, boolean jitting) {
    if (loaded_vx.already) return;
    allUnload();
    findAbstractSpeed(speed_window,times,centroid,Metric.DISTX,((normalize)?1.0f/meanBodyLengthEstimate():1.0f)/speed_window);
    loaded_vx.already = true;
    if (jitting) loaded_vx.setJit();
  }
  
  public void quantityIsVy(float[] times,float speed_window,boolean normalize,boolean jitting) {
    if (loaded_vy.already) return;
    allUnload();
    findAbstractSpeed(speed_window,times,centroid,Metric.DISTY,((normalize)?1.0f/meanBodyLengthEstimate():1.0f)/speed_window);
    loaded_vy.already = true;
    if (jitting) loaded_vy.setJit();
  }
  
  public void quantityIsTheta(boolean jitting) {
    if (loaded_theta.already) return;
    allUnload();
    for (int i=0;i<quantity.length;i++) {
      if (bearing[i]==null || centroid[i]==null || !loc_okay(centroid[i])) quantity[i] = Float.NaN;
      else {
        float f = (float)Math.atan2(bearing[i].y,bearing[i].x);
        quantity[i] = f;
      }
    }
    loaded_theta.already = true;
    if (jitting) loaded_theta.setJit();
  }
  
  public void quantityIsCrab(float[] times,float speed_window,boolean normalize,boolean jitting) {
    if (loaded_crab.already) return;
    allUnload();
    findAbstractSpeed(speed_window,times,centroid,Metric.CRAB,((normalize)?1.0f/meanBodyLengthEstimate():1.0f)/speed_window);
    loaded_crab.already = true;
    if (jitting) loaded_crab.setJit();
  }
  
  public void quantityIsQxfw(Choreography chore,boolean jitting) {
    if (loaded_qxfw.already) return;
    allUnload();
    findQxfw(chore);
    loaded_qxfw.already = true;
    if (jitting) loaded_qxfw.setJit();
  }
  
  public void quantityIsStim(int[][] events,int n) {
    switch (n) {
      case 1: if (loaded_stim1.already) return; break;
      case 2: if (loaded_stim2.already) return; break;
      case 3: if (loaded_stim3.already) return; break;
      case 4: if (loaded_stim4.already) return; break;
      default: quantityIs(Float.NaN); return;
    }
    allUnload();
    for (int i=first_frame;i<=last_frame;i++) {
      quantity[i-first_frame] = 0;
      if (events[i]==null) continue;
      for (int j=0;j<events[i].length;j++) if (events[i][j]==n) quantity[i-first_frame] = 1;
    }
    switch (n) {
      case 1: loaded_stim1.already = true; break;
      case 2: loaded_stim2.already = true; break;
      case 3: loaded_stim3.already = true; break;
      case 4: loaded_stim4.already = true; break;
      default:
    }
  }

  public void ensureLoadedCustomSpace(int n) {
    if (loaded_custom.length <= n) {
      loaded_custom = Arrays.copyOf(loaded_custom,n+1);
      for (int i=0 ; i<loaded_custom.length ; i++) if (loaded_custom[i]==null) loaded_custom[i] = new Preloaded();
    }
  }
  
  public void quantityAlreadyIsCustom(int n,boolean jitting) {
    allUnload();
    ensureLoadedCustomSpace(n);
    loaded_custom[n].already = true;
    if (jitting) loaded_custom[n].setJit();
  }

  public void readyMultiscale(float mm_per_pixel)
  {
    if (multiscale_x==null || multiscale_y==null)
    {
      float[] all_x = new float[ centroid.length ];
      float[] all_y = new float[ centroid.length ];
      for (int i=0;i<centroid.length;i++)
      {
        all_x[i] = centroid[i].x * mm_per_pixel * 1000;
        all_y[i] = centroid[i].y * mm_per_pixel * 1000;
      }
      multiscale_x = new Fractionator(all_x,2,32);
      multiscale_y = new Fractionator(all_y,2,32);
      ranges_xy = new QuadRanger(all_x,all_y,64,8);
    }
    multiscale_q = new Fractionator(quantity,2,32);
  }

  public float[] extractTimes() { return Arrays.copyOfRange(chore.times, first_frame, last_frame+1); }

  public float[] extractCurrentQuantity() { return Arrays.copyOf(quantity,quantity.length); }
  
  public float[] extractCentroidPoints() {
    float[] vs = new float[2*centroid.length];
    for (int i=0; i<centroid.length; i++) {
      vs[i] = centroid[i].x;
      vs[i+centroid.length] = centroid[i].y;
    }
    return vs;
  }

  public boolean[] extractHeadFoundFlags() {
    boolean[] bs = new boolean[spine.length];
    for (int i=0; i<spine.length; i++) {
      if (spine[i]==null) bs[i] = false;
      else bs[i] = spine[i].oriented();
    }
    return bs;
  }
  
  public float[] extractNthSpinePoints(int n) {
    Vec2F v = Vec2F.zero();
    float[] vs = new float[2*spine.length];
    for (int i=0; i<spine.length ; i++) {
      if (spine[i] != null) spine[i].get(n,v);
      else { v.x = Float.NaN; v.y = Float.NaN; }
      vs[i] = v.x + centroid[i].x;
      vs[i+spine.length] = v.y + centroid[i].y;
    }
    return vs;
  }

  public float[] extractOutlineAtFrame(int frame) {
    Vec2F v = Vec2F.zero();
    int N = (outline[frame]==null) ? 0 : outline[frame].size();
    float[] vs = new float[2*N];
    for (int i=0; i<N; i++) {
      outline[frame].get(i,v);
      vs[i] = v.x;
      vs[i+N] = v.y;
    }
    return vs;
  }
}
