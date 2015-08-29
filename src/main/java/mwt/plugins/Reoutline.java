/* Reoutline.java - Plugin for Choreography to smooth and fix outlines
 * Copyright 2010 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015 Calico Life Sciences LLC (author Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt.plugins;

import java.io.*;
import java.util.Arrays;
import java.util.ArrayList;

import mwt.*;
import mwt.numerics.*;

public class Reoutline implements CustomComputation {
  private static final float SCORE_TARGET = 0.7f;
  private static final float CONCAVE_DEFAULT = -0.02f;
  private static final float CONVEX_DEFAULT = 0.8f;

  public static class FixedOutline implements Outline {
    int length;
    float[] x,y;
    Vec2F[] xy;
    Vec2S[] sxy;
    public FixedOutline(Outline ro) {
      sxy = ro.unpack(true);
      length = sxy.length;
      xy = null;
      x = new float[sxy.length];
      y = new float[sxy.length];
      for (int i=0 ; i<sxy.length ; i++) {
        x[i] = sxy[i].x;
        y[i] = sxy[i].y;
      }
      sxy = null;
    }
    void load(Vec2F v, int i) { v.eq(x[i],y[i]); }
    void load(Vec2S v, int i) { v.eq((short)Math.round(x[i]),(short)Math.round(y[i])); }
    Vec2F fget(int i) { return new Vec2F(x[i],y[i]); }
    Vec2S sget(int i) { return new Vec2S((short)Math.round(x[i]),(short)Math.round(y[i])); }
    void a2v() { xy = new Vec2F[length]; for (int i=0; i<length; i++) xy[i] = new Vec2F(x[i],y[i]); }
    void a2s() { sxy = new Vec2S[length]; for (int i=0; i<length; i++) sxy[i] = new Vec2S((short)Math.round(x[i]),(short)Math.round(y[i])); }

    public int size() { return length; }
    public boolean quantized() { return false; }
    public void compact() {
      if (sxy != null) sxy = null;
      if (xy != null) {
        x = new float[xy.length];
        y = new float[xy.length];
        for (int i=0; i<xy.length; i++) {
          x[i] = xy[i].x;
          y[i] = xy[i].y;
        }
        xy = null;
      }
    }
    public Vec2S get(int i, Vec2S buf) { return buf.eq( (short)Math.round(x[i]), (short)Math.round(y[i]) ); }
    public Vec2F get(int i, Vec2F buf) { return buf.eq( x[i], y[i] ); }
    public Vec2F[] unpack(Vec2F[] storage) {
      if (storage == null) {
        if (xy == null) a2v();
        return xy;
      }
      else {
        if (storage.length < length) storage = new Vec2F[length];
        for (int i=0; i<length; i++) {
          if (storage[i]==null) storage[i] = fget(i);
          else load(storage[i],i);
        }
        return storage;
      }
    }
    public Vec2S[] unpack(Vec2S[] storage) {
      if (storage == null) {
        if (sxy == null) a2s();
        return sxy;
      }
      else {
        if (storage.length < length) storage = new Vec2S[length];
        for (int i=0; i<length; i++) {
          if (storage[i]==null) storage[i] = sget(i);
          else load(storage[i],i);
        }
        return storage;
      }
    }
    public Vec2S[] unpack(boolean store) {
      return unpack( (store) ? unpack((Vec2S[])null) : unpack(new Vec2S[length]));
    }
  }

  int despike;
  float convex = CONVEX_DEFAULT;
  float concave = CONCAVE_DEFAULT;
  float[] blur;
  Choreography chore;

  public Reoutline() {
    despike = 0;
    blur = new float[1];
    blur[0] = 1.0f;
  }

  // Not particularly fast for large blurs, but we expect small blurs (n = 3-5).
  void convolve(float[] a) {
    int n = 1+blur.length/2;
    float[] b = Arrays.copyOf(a,a.length);
    for (int i=0; i<a.length; i++) {
      a[i] *= blur[n-1];
      for (int j=1; j<n; j++) {
        int k0 = i-j;
        if (k0<0) k0 += a.length;
        int k1 = i+j;
        if (k1>=a.length) k1 -= a.length;
        a[i] += blur[n-j-1]*a[k0] + blur[n+j-1]*a[k1];
      }
    }
  }

  public void initialize(String args[], Choreography chore) throws CustomHelpException,IllegalArgumentException {
    this.chore = chore;
    int n = 0;
    float[] weights = null;
    boolean found_smooth = false;
    for (String arg: args) {
      boolean was_number = true;
      int i = 0;
      try { i = Integer.parseInt(arg); } catch (IllegalArgumentException iae) { was_number = false; }
      if (was_number && i<1) throw new IllegalArgumentException("Reoutline smoothing length must be at least 1");
      if (was_number) n = i;
      else if (arg.toLowerCase().startsWith("box")) {
        if (found_smooth) throw new IllegalArgumentException("Reoutline needs one smoothing type only.  Extra: "+arg);
        else found_smooth = true;
        weights = new float[0]; // Hacky signal that it's a box
        if (!arg.equals("box")) throw new IllegalArgumentException("Reoutline smoothing type box has no options.");
      }
      else if (arg.toLowerCase().startsWith("tri")) {
        if (found_smooth) throw new IllegalArgumentException("Reoutline needs one smoothing type only.  Extra: "+arg);
        else found_smooth = true;
        if (!arg.equals("tri")) throw new IllegalArgumentException("Reoutline smoothing type tri has no options.");
      }
      else if (arg.toLowerCase().startsWith("exp")) {
        if (found_smooth) throw new IllegalArgumentException("Reoutline needs one smoothing type only.  Extra: "+arg);
        else found_smooth = true;
        weights = new float[1];  // This hackily indicates it's exponential decay
        weights[0] = (float)Math.sqrt(0.5);
        if (arg.length() > 3) {
          if (arg.charAt(3)!='=' || arg.length()==4) throw new IllegalArgumentException("Reoutline exp option is exp=number");
          String s = arg.substring(4);
          float f = 0.0f;
          was_number = true;
          try { f = Float.parseFloat(s); } catch (IllegalArgumentException iae) { was_number = false; }
          if (was_number && 0.0f<f && f<1.0f) weights[0] = f;
          else throw new IllegalArgumentException("Reoutline exp option should be a number between 0 and 1, not "+s);
        }
      }
      else if (arg.toLowerCase().startsWith("num")) {
        if (found_smooth) throw new IllegalArgumentException("Reoutline needs one smoothing type only.  Extra: "+arg);
        else found_smooth = true;
        if (arg.length()<7) throw new IllegalArgumentException("Reoutline num needs 2+ comma-separated numbers (no space), e.g. num=4,2,1");
        String[] ss = arg.substring(4).split(",");
        if (ss.length<2) throw new IllegalArgumentException("Reoutline num needs 2+ comma-separated numbers (no space), e.g. num=4,2,1");
        ArrayList<Float> alf = new ArrayList<Float>();
        for (String s : ss) {
          was_number = true;
          float f = 0.0f;
          try { f = Float.parseFloat(s); } catch (IllegalArgumentException iae) { was_number = false; }
          if (was_number) alf.add(f);
        }
        if (alf.size() != ss.length) throw new IllegalArgumentException("Reoutline num had a malformed list of numbers.");
        weights = new float[alf.size()];
        i = 0;
        for (float f : alf) { weights[i++] = f; }
      }
      else if (arg.toLowerCase().startsWith("despike")) {
        despike = 11;
        if (!arg.equalsIgnoreCase("despike")) {
          String s = (arg.length()<9) ? "" : arg.substring(8);
          was_number = true;
          try { i = Integer.parseInt(s); } catch (IllegalArgumentException iae) { was_number = false; }
          if (!was_number) throw new IllegalArgumentException("Reoutline despike option should be a single positive integer");
          despike = i;
        }
      }
      else if (arg.equalsIgnoreCase("help")) {
        System.out.println("Usage: --plugin Reoutline::n::[{box|tri|exp|num}=options][::despike]");
        System.out.println("Reoutline converts outlines from a pixel path to a smooth contour.");
        System.out.println("This requires additional memory (2 bits/contour point -> 64 bits/point).");
        System.out.println("If you also use Respine, Reoutline must be called first to help Respine!");
        System.out.println("Options:");
        System.out.println("  n = half width of smoothing window, min = default = 1");
        System.out.println("  Four different types of smoothing are provided (default=tri):");
        System.out.println("    box - rectangular (boxcar) window (no options; default n=3)");
        System.out.println("    tri - triangular window (no options; default n=4)");
        System.out.println("    exp[=k] - exponential decay (k/step, default k=1/sqrt(2); default n=5)");
        System.out.println("    num= - user-specified numbers starting from center");
        System.out.println("         Example: Reoutline::4::num=5,3,2,1");
        System.out.println("  despike will attempt to remove small, sharp protrusions");
        System.out.println("    despike=N will do so in a way suited for N spine points (default 11)");
        System.out.println("    (especially useful when combined with Respine)");
        throw new CustomHelpException();
      }
      else throw new IllegalArgumentException("Reoutline plugin does not understand option: "+arg);
    }
    if (n<1) {
      if (weights==null) n = 4;
      else if (weights.length==0) n = 3;
      else if (weights.length==1) n = 5;
    }
    if (n>1 && weights!=null && weights.length>1 && weights.length!=n) throw new IllegalArgumentException("Reoutline window size doesn't match number of points.");
    blur = new float[2*n-1];
    if (n==1 || (weights!=null && weights.length==0)) {
      for (int i=0; i<blur.length; i++) blur[i] = (float)(1.0/blur.length);
    }
    else if (weights==null) {
      for (int i=0; i<n; i++) blur[n+i-1] = blur[n-i-1] = (float)(n-i);
    }
    else if (weights.length==1) {
      blur[n-1] = 1.0f;
      for (int i=1; i<n; i++) blur[n+i-1] = blur[n-i-1] = (float)(weights[0]*blur[n-i]);
    }
    else for (int i=0; i<n; i++) blur[n+i-1] = blur[n-i-1] = weights[i];
    double d = 0.0;
    for (float f: blur) d += f;
    if (d > 0) d = 1.0/d; else d = 1.0;
    for (int i=0; i<blur.length; i++) blur[i] = (float)(blur[i]*d);
  }

  public String desiredExtension() { return "outline"; }

  private int ringD(int a, int b, int l) {
    int c = a-b;
    if (c<0) return c+l; else return c;
  }
  private int ringR(int a, int l) {
    if (a+1==l) return 0;
    else return a+1;
  }
  private int ringR(int a, int l, int n) {
    if (a+n>=l) return a+n-l;
    else return a+n;
  }
  private int ringL(int a, int l) {
    if (a-1<0) return l-1;
    else return a-1;
  }
  private int ringL(int a, int l, int n) {
    if (a-n<0) return l+a-n;
    else return a-n;
  }
  private float sq(float f) { return f*f; }
  public boolean validateDancer(Dance d) {
    if (d.outline==null) return false;
    for (int h=0; h<d.outline.length; h++) {
      if (d.outline[h]==null) continue;
      FixedOutline fxo = new FixedOutline(d.outline[h]);
      if (blur!=null && blur.length>1) {
        convolve(fxo.x);
        convolve(fxo.y);
        fxo.compact();
      }
      if (despike > 1) {
        boolean changed = true;
        ArrayList<Integer> localMax = new ArrayList<Integer>();
        while (changed) {
          changed = false;
          int L = fxo.length;
          localMax.clear();
          float[] ang = Dance.getBodyAngles(fxo.x, fxo.y, null, 1.0f/(despike-1), fxo.length);
          for (int i=0; i<ang.length; i++) {
            int i0 = i-1;
            if (i0 < 0) i0 = ang.length-1;
            int i1 = i+1;
            if (i1 >= ang.length) i1=0;
            if (ang[i] > ang[i0] && ang[i] >= ang[i1] && ang[i]>convex) localMax.add(i);
          }
          if (localMax.size()>2) {
            Vec2F u = new Vec2F(0,0);
            Vec2F v = new Vec2F(0,0);
            Vec2F vv = new Vec2F(0,0);
            Vec2F w = new Vec2F(0,0);
            Vec2F ww = new Vec2F(0,0);
            ArrayList<Integer> localMin = new ArrayList<Integer>();
            for (int i=0; i<ang.length; i++) {
              int i0 = i-1;
              if (i0 < 0) i0 = ang.length-1;
              int i1 = i+1;
              if (i1 >= ang.length) i1=0;
              if (ang[i] < ang[i0] && ang[i] <= ang[i1] && ang[i]<concave) localMin.add(i);
            }
            int[] lmax = new int[localMax.size()];
            int[] lmin = new int[localMin.size()];
            int[] cuspl = new int[lmax.length];
            int[] cuspr = new int[lmax.length];
            float[] cusps = new float[lmax.length];
            int M = lmin.length;
            int j = 0;
            if (M>1) {
              for (int k : localMax) lmax[j++] = k;
              j = 0;
              for (int k : localMin) lmin[j++] = k;
 
              // Weight spikes by how central they are (central = worse)
              float dists[] = new float[lmax.length];
              float steps[] = new float[lmax.length];
              for (int i=0; i<lmax.length; i++) {
                u.eq(fxo.x[lmax[i]],fxo.y[lmax[i]]);
                for (j=i+1; j<lmax.length; j++) {
                  v.eq(fxo.x[lmax[j]],fxo.y[lmax[j]]);
                  float duv = u.dist(v);
                  dists[i] += duv;
                  dists[j] += duv;
                  float suv = Math.min(ringD(lmax[i],lmax[j],L),ringD(lmax[j],lmax[i],L));
                  steps[i] += suv;
                  steps[j] += suv;
                }
              }
              float sum = 0.0f;
              for (int i=0; i<dists.length; i++) { dists[i] *= dists[i]*steps[i]*steps[i]*steps[i]; sum += dists[i]; }
              float fix = (sum>0.0f) ? dists.length/sum : 0.0f;
              for (int i=0; i<dists.length; i++) { dists[i] *= fix; dists[i] = 1.0f/dists[i]; }

              for (j=0; j<lmax.length; j++) {
                u.eq( fxo.x[lmax[j]], fxo.y[lmax[j]] );
                int k,kk;
                for (k=0; k<lmin.length; k++) if (lmin[k]>lmax[j]) break;
                if (k>=lmin.length) k=0;
                int kr0 = k;
                kk = ringL(k,M);
                int farR = (ringD(lmax[ringR(j,lmax.length)],lmax[j],L)*2)/3;
                for (int m=0; m<M && ringD(lmin[k],lmax[j],L) < farR; m+=1, k = ringR(k,M)) {}
                int kr1 = k;
                k = kk;
                int kl0 = k;
                int farL = (ringD(lmax[j],lmax[ringL(j,lmax.length)],L)*2)/3;
                for (int m=0; m<M && ringD(lmax[j],lmin[k],L) < farL; m+=1, k = ringL(k,M)) {}
                int kl1 = k;
                int kl = -1;
                int kr = -1;
                double score = 0.0;
                double s,ss;
                for (k = kr0; k!=kr1; k = ringR(k,M)) {
                  v.eq( fxo.x[lmin[k]], fxo.y[lmin[k]] );
                  for (kk = kl0; kk!=kl1; kk = ringL(kk,M)) {
                    w.eq( fxo.x[lmin[kk]], fxo.y[lmin[kk]] );
                    s = Math.min(u.dist2(v), u.dist2(w)) / Math.max(1.0, v.dist2(w));
                    if (s>0.5) {
                      int i = ringR(lmin[k],L,Math.max(3,L/(4*despike-1)));
                      vv.eq( fxo.x[i], fxo.y[i] ).eqMinus(v);
                      i = ringL(lmin[kk],L,Math.max(3,L/(4*despike-1)));
                      ww.eq( fxo.x[i], fxo.y[i] ).eqMinus(w);
                      v.eqMinus(u);
                      w.eqMinus(u);
                      ss = Math.sqrt(Math.min(1.0f, 1.0f - Math.min( v.unitDot(vv) , w.unitDot(ww))))*sq(1.0f-vv.unitDot(ww))*dists[j];
                      ss *= ((2.0f-ang[lmin[k]])*(2.0f-ang[lmin[k]])/9.0f)*sq((L-2*ringD(lmin[k],lmin[kk],L))/(L*(despike-2.0f)/despike));
                      if (ss > 0.1 && s*ss>score) { score = s*ss; kr = k; kl = kk; }
                    }
                  }
                }
                cuspr[j] = (score<=SCORE_TARGET) ? -1 : lmin[kr];
                cuspl[j] = (score<=SCORE_TARGET) ? -1 : lmin[kl];
                cusps[j] = (float)score;
              }
            }
            else for (j=0; j<cusps.length; j++) cusps[j] = cuspl[j] = cuspr[j] = -1;

            int best = 0;
            for (j=1; j<cusps.length; j++) if (cusps[j]>cusps[best]) best=j;
            int cr,cl;
            if (cusps[best]>SCORE_TARGET) {
              cr = cuspr[best];
              cl = cuspl[best];
              v.eq(fxo.x[cr],fxo.y[cr]);
              w.eq(fxo.x[cl],fxo.y[cl]);
              int m = Math.max(1,Math.round(v.dist(w)));
              v.eqMinus(w).eqTimes(1.0f/m);
              int n = cr-cl;
              if (n<0) n += L;
              float[] xnu = new float[L+m-n];
              float[] ynu = new float[xnu.length];
              j = 0;
              if (cl < cr) {
                for (int i=0; i<L; i++) {
                  if (i <= cl || i >= cr) { xnu[j] = fxo.x[i]; ynu[j] = fxo.y[i]; j++; }
                  if (i == cl) {
                    for (int k=1; k<m; k++) {
                      w.eqPlus(v); xnu[j] = w.x; ynu[j] = w.y; j++;
                    }
                  }
                }
              }
              else {
                for (int i=cr; i<=cl; i++) { xnu[j] = fxo.x[i]; ynu[j] = fxo.y[i]; j++; }
                for (int k=1; k<m ; k++) { w.eqPlus(v); xnu[j] = w.x; ynu[j] = w.y; j++; }
              }
              fxo.length = xnu.length;
              fxo.x = xnu;
              fxo.y = ynu;
              changed = true;
            }
          }
        }
        ArrayList<Vec2F> points = new ArrayList<Vec2F>();
        Vec2F u,v,w;
        float f,g;
        int M = localMax.size();
        for (int m=0; m < M; m++) {
          int j0 = localMax.get(m);
          int j1 = localMax.get((m+1>=localMax.size())?0:m+1);
          u = new Vec2F( fxo.x[j0] , fxo.y[j0] );
          if (points.size()>0 && points.get(points.size()-1).dist2(u) < 0.01f) points.remove(points.size()-1);
          points.add(u);
          v = u.copy();
          w = v.copy();
          for (int j=ringR(j0,fxo.length); j != j1; j = ringR(j,fxo.length)) {
            w.eq(v);
            v.eq( fxo.x[j], fxo.y[j] );
            g = u.dist2(v);
            if (g > 1.0) {
              f = (g - 1.0f)/Math.max(0.01f,g - u.dist2(w));
              u = v.copy().eqTimes(1.0f - f);
              w.eqTimes(f);
              u.eqPlus(w);
              points.add(u);
              j = ringL(j,fxo.length);
              v.eq(u);
            }
          }
        }
        if (points.size()>despike) {
          if (fxo.length != points.size()) {
            fxo.length = points.size();
            fxo.x = new float[fxo.length];
            fxo.y = new float[fxo.length];
          }
          int i = 0;
          for (Vec2F p : points) {
            fxo.x[i] = p.x; fxo.y[i] = p.y; i++;
          }
        }
      }
      d.outline[h] = fxo;
    }
    return true;
  }

  public int computeAll(File out_f) throws IOException { return 0; }
  public int computeDancerSpecial(Dance d, File out_f) throws IOException { return 0; }

  public int quantifierCount() { return 0; }
  public void computeDancerQuantity(Dance d,int which) throws IllegalArgumentException {
    if (which>=0) throw new IllegalArgumentException("No registered quantifiers for dancer computation.");
  }
  public String quantifierTitle(int which) { throw new IllegalArgumentException("No registered quantifiers for dancer computation."); }
}
