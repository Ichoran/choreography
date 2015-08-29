/* Respine.java - Plugin for Choreography to improve spine quality
 * Copyright 2010, 2011 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015 Calico Life Sciences LLC (author Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt.plugins;

import java.io.*;

import mwt.*;
import mwt.numerics.*;

public class Respine implements CustomComputation
{
  public static class FixedSpine implements Spine {
    boolean quant;
    boolean orient = false;
    float[] x;
    float[] y;
    float[] w;
    protected FixedSpine() {
      quant = false;
      x = y = w = null;
    }
    public FixedSpine(Vec2F[] points, float[] widths, boolean q) {
      quant = q;
      x = new float[points.length];
      y = new float[x.length];
      w = widths;
      int i = 0;
      for (Vec2F p : points) { x[i] = p.x; y[i] = p.y; i++; }
    }
    public int size() { return x.length; }
    public boolean quantized() { return quant; }
    public boolean oriented() { return orient; }
    public void headfirstKnown(boolean b) { orient = b; }
    public void flip() {
      float temp;
      int n = x.length/2;
      for (int i=0,j=x.length-1 ; i<n ; i++,j--) {
        temp = x[i]; x[i] = x[j]; x[j] = temp;
        temp = y[i]; y[i] = y[j]; y[j] = temp;
        if (w!=null) { temp = w[i]; w[i] = w[j]; w[j] = temp; }
      }
    }
    public Vec2S get(int i) { return new Vec2S( (short)Math.round(x[i]), (short)Math.round(y[i])); }
    public Vec2S get(int i, Vec2S buf) { buf.eq( (short)Math.round(x[i]), (short)Math.round(y[i])); return buf; }
    public Vec2F get(int i, Vec2F buf) { buf.eq( x[i], y[i] ); return buf; }
    public float width(int i) { if (w==null) return Float.NaN; else return w[i]; }
  }

  public int spines;
  public float fraction;
  public boolean subpixel;
  public float taper_distance;
  public float taper_alpha;
  public float taper_beta;
  public Choreography chore;
  
  public Respine() {
    spines = 11;
    fraction = 2.0f/(spines-1);
    taper_distance = fraction;
    taper_alpha = 1.0f;
    taper_beta = 0.0f;
    subpixel = false;
  }
  
  public void initialize(String args[],Choreography chore) throws CustomHelpException,IllegalArgumentException {
    this.chore = chore;
    boolean spine_request = false;
    boolean frac_request = false;
    boolean was_something;
    String whatswrong = "Mysterious error";
    int newspines = -1;
    float newfrac = -1.0f;
    for (String arg : args) {
      if (arg.equalsIgnoreCase("subpixel")) {
        subpixel = true;
        continue;
      }
      was_something = false;
      try {
        newspines = Integer.parseInt(arg);
        if (newspines < 3) throw new IllegalArgumentException("Argument can't be negative");
        spines = newspines;
        spine_request = true;
        was_something = true;
      }
      catch (IllegalArgumentException iae) { whatswrong = iae.getMessage(); }
      if (was_something) continue;
      try {
        newfrac = Float.parseFloat(arg);
        if (newfrac <= 0 || newfrac >=1) throw new IllegalArgumentException("Argument must be in (0,1)");
        fraction = newfrac;
        frac_request = true;
        was_something = true;
      }
      catch (IllegalArgumentException iae) { whatswrong = iae.getMessage(); }
      if (was_something) continue;
      if (arg.toLowerCase().startsWith("tapered")) {
        taper_beta = 2f;
        if (arg.length() > "tapered".length()) {
          try {
            String[] ss = arg.substring(8).split(",");
            if (ss.length != 3) throw new IllegalArgumentException("Tapered requires three parameters");
            taper_distance = Float.parseFloat(ss[0]);
            taper_alpha = Float.parseFloat(ss[1]);
            taper_beta = Float.parseFloat(ss[2]);
            if (taper_distance>0.5) throw new IllegalArgumentException("Taper distance must be no more than 0.5.");
            if (taper_alpha <= 0) throw new IllegalArgumentException("Power for angle must be positive.");
            if (taper_beta < 0) throw new IllegalArgumentException("Power for taper must be nonnegative.");
            was_something = true;
          }
          catch (IllegalArgumentException iae) { whatswrong = iae.getMessage(); }
        }
        else was_something = true;
      }
      if (was_something) continue;
      //                  012345678911234567892123456789312345678941234567895123456789612345678971234567898
      System.out.println("Usage: --plugin Respine::n::0.x[::subpixel][::tapered[=f,alpha,beta]]");
      System.out.println("  n = number of points in spine (must be at least 3)");
      System.out.println("      default = 11");
      System.out.println("  0.x = fraction of object length to measure angle");
      System.out.println("        smallest angles are chosen as endpoints");
      System.out.println("        default = 1/(n-1)");
      System.out.println("  Specify subpixel to allow spine to vary smoothly");
      System.out.println("    (otherwise spine points will be rounded to the nearest pixel).");
      System.out.println("  Specify tapered to also require the endpoints to be vaguely linearly tapered");
      System.out.println("  Optionally, three numbers can be provided as well:");
      System.out.println("    f is the fraction of object length used to check taper (default 2/(n-1))");
      System.out.println("    score is (angle)^alpha*(taper score)^beta");
      System.out.println("      defaults are alpha=1, beta=2");
      if (arg.equalsIgnoreCase("help")) throw new CustomHelpException();
      else throw new IllegalArgumentException("Invalid arguments to Respine plugin: "+whatswrong);
    }
    if (spine_request && !frac_request) fraction = 2.0f/(spines-1);
  }
  
  public String desiredExtension() { return "spine"; }

  protected static int modup(int i, int m) { if (i >= m) return (i-m); else return i; }
  protected static int moddn(int i, int m) { if (i<0) return (i+m); else return i; }
  protected void weightByTaper(Dance d, int index, float[] weights) {
    Outline o = d.outline[index];
    Vec2F u = new Vec2F();
    Vec2F v = new Vec2F();
    Vec2F w = new Vec2F();
    int L = o.size();
    int N = Math.round(L*taper_distance);
    int n1 = Math.round(N/4);
    int n2 = Math.round(N/2);
    int n3 = Math.round((3*N)/4);
    if (n1<1) n1 = 1;
    if (n2<n1) n2 = n1+1;
    if (n3<n2) n3 = n2+1;
    if (N<n3) N = n3+1;
    for (int i=0; i<L; i++) {
      float d1 = o.get(modup(i+n1,L),v).dist(o.get(moddn(i-n1,L),u));
      float d2 = o.get(modup(i+n2,L),v).dist(o.get(moddn(i-n2,L),u));
      float d3 = o.get(modup(i+n3,L),v).dist(o.get(moddn(i-n3,L),u));
      float D = o.get(modup(i+N,L),v).dist(o.get(moddn(i-N,L),u));

      // This gives a score between 0 and 1 (lower is better) for tapering vs. bulbousness
      float big = 3*Math.max(Math.max(d1,d2),Math.max(d3,D));
      float tap = (Math.max(0,d1-D) + Math.max(0,d2-D) + Math.max(0,d3-D) + Math.max(0,d1-d3) + Math.max(0,d2-d3) + Math.max(0,d1-d2))/big;

      weights[i] = (float)(4*Math.pow(Math.max(0,(weights[i]+2)*0.25),taper_alpha)*Math.pow(Math.max(0,1.0-tap),taper_beta) - 2);
    }
  }
  
  protected static int clip(int i,int L) {
    if (i<0) return i+L;
    else if (i>=L) return i-L;
    else return i;
  }
  protected static int rotR(int i,int L) {
    if (i+1 >= L) return 0;
    else return i+1;
  }
  protected static int rotL(int i,int L) {
    if (i<=0) return L-1;
    else return i-1;
  }
  public boolean validateDancer(Dance d) {
    if (d.outline == null) return false;
    if (d.spine==null) d.spine = new Spine[d.outline.length];
    
    Vec2F u = new Vec2F(0,0);
    Vec2F v = new Vec2F(0,0);
    Vec2F w = new Vec2F(0,0);
    Vec2S[] pts = null;
    float[] wid = new float[spines];
    Vec2I ep = null;
    float[] angles = null;
    Vec2F[] points = new Vec2F[spines];
    for (int j=0 ; j<spines ; j++) points[j] = new Vec2F();
    
    for (int i=0 ; i<d.outline.length ; i++) {
      if (d.outline[i]==null) { d.spine[i]=null; continue; }
      if (d.outline[i].quantized()) {
        pts = d.outline[i].unpack(pts);
        angles = Dance.getBodyAngles(pts,angles,fraction,d.outline[i].size());
        if (taper_alpha != 1.0f || taper_beta != 0.0f) weightByTaper(d,i,angles);
        ep = Dance.getBestEndpoints(angles,d.outline[i].size());

        int farside = d.outline[i].size() - Math.abs(ep.x - ep.y);
        if (ep.x<=ep.y) farside = -farside;

        points[0].eq( u.eq(pts[ep.x]).eqMinus(d.centroid[i]) );
        wid[0] = 0.0f;
        for (int j=1 ; j<spines-1 ; j++) {
          float f0 = (1.0f*(j-0.5f))/(spines-1);
          float f1 = (1.0f*(j+0.5f))/(spines-1);
          int iL0 = Math.round(ep.x*(1.0f-f0)+ep.y*f0);
          int iL1 = Math.round(ep.x*(1.0f-f1)+ep.y*f1);
          int iLn = 0;
          int iR0 = clip( ep.x + Math.round(f0*farside) , d.outline[i].size());
          int iR1 = clip( ep.x + Math.round(f1*farside) , d.outline[i].size());
          int iRn = 0;
          int iLinc = (iL0<=iL1) ? 1 : -1;
          int iRinc = -iLinc;
          u.eq(0,0);
          for (int k=iL0 ; ; k=clip(k+iLinc,d.outline[i].size())) {
            u.eqPlus( w.eq(pts[k]) );
            iLn++;
            if (iLn>d.outline[i].size()) {
              System.out.printf("Um, %d %d from %d to %d?\n",k,clip(k+iLinc,d.outline[i].size()),iL0,iL1);
              System.exit(1);
            }
            if (k==iL1) break;
          }
          v.eq(0,0);
          for (int k=iR0 ; ; k=clip(k+iRinc,d.outline[i].size())) {
            v.eqPlus( w.eq(pts[k]) );
            iRn++;
            if (k==iR1) break;
          }
          u.eqTimes( 1.0f/iLn );
          v.eqTimes( 1.0f/iRn );
          wid[j] = u.dist(v);
          u.eqPlus(v).eqTimes(0.5f).eqMinus(d.centroid[i]);
          points[j].eq(u);
        }
        points[spines-1].eq( u.eq(pts[ep.y]).eqMinus(d.centroid[i]) );
      }
      else {
        Outline o = d.outline[i];
        int L = o.size();
        if (d.outline[i] instanceof Reoutline.FixedOutline) {
          Reoutline.FixedOutline rfo = (Reoutline.FixedOutline)d.outline[i];
          angles = Dance.getBodyAngles(rfo.x,rfo.y,angles,fraction,rfo.length);
        }
        else {
          angles = Dance.getBodyAngles(o.unpack((Vec2F[])null),angles,fraction,L);
        }
        if (taper_alpha != 1.0f || taper_beta != 0.0f) weightByTaper(d,i,angles);
        ep = Dance.getBestEndpoints(angles,L);
        int i0 = Math.min(ep.x,ep.y);
        int i1 = Math.max(ep.x,ep.y);
        int nr = clip(i1-i0,L);
        int nl = L-nr;
        o.get(i0, points[0]).eqMinus(d.centroid[i]);
        o.get(i1, points[spines-1]).eqMinus(d.centroid[i]);
        wid[spines-1] = wid[0] = 0.0f;
        int ir = i0;
        int il = i0;
        for (int j = 1; j < spines-1; j++) {
          int step = Math.max(1, Math.min(nl,nr)/(spines-j) - 1);
          ir = clip(ir+nr/(spines-j),L);
          il = clip(il-nl/(spines-j),L);
          float bestd = o.get(ir,u).dist2(o.get(il,v));
          int bestk = 0;
          for (int k=1; k<step; k++) {
            float f;
            int kk;
            kk = clip(ir+k,L); o.get(kk,u);
            kk = clip(il+k,L); o.get(kk,v);
            f = u.dist2(v); if (f < bestd) { bestd=f; bestk=k; }
            kk = clip(ir-k,L); o.get(kk,u);
            kk = clip(il-k,L); o.get(kk,v);
            f = u.dist2(v); if (f < bestd) { bestd=f; bestk=-k; }
          }
          int bir = clip(ir+bestk,L);
          int bil = clip(il+bestk,L);
          ir = clip(bir-2,L);
          o.get(ir,u);
          for (int k=1;k<5;k++) { ir=rotR(ir,L); u.eqPlus(o.get(ir,w)); }
          il = clip(bil+2,L);
          o.get(il,v);
          for (int k=1;k<5;k++) { il=rotL(il,L); v.eqPlus(o.get(il,w)); }
          w.eq(u).eqPlus(v).eqTimes(0.1f);
          points[j].eq(w).eqMinus(d.centroid[i]);
          wid[j] = u.eqTimes(0.2f).dist(v.eqTimes(0.2f));
          ir = bir;
          nr = clip(i1-ir,L);
          il = bil;
          nl = clip(il-i1,L);
        }
      }
      d.spine[i] = new FixedSpine(points,wid,!subpixel);
      wid = new float[spines];
    }
    d.alignAllSpines();
    d.endpoint_angle_fraction = fraction;
    return true;
  }
  
  public int computeAll(File out_f) throws IOException {
    return 0;
  }
  public int computeDancerSpecial(Dance d,File out_f) throws IOException {
    return 0;
  }
  
  public int quantifierCount() { return 0; }
  public void computeDancerQuantity(Dance d,int which) throws IllegalArgumentException {
    if (which>=0) throw new IllegalArgumentException("No registered quantifiers for dancer computation.");
  }
  public String quantifierTitle(int which) throws IllegalArgumentException{
    throw new IllegalArgumentException("No registered quantifiers for dancer computation.");
  }
}

