/* Curvaceous.java - Plugin for Choreography that finds curves in movement path
 * Copyright 2010, 2011 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015 Calico Life Sciences LLC (author Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt.plugins;

import java.io.*;
import java.util.*;

import mwt.*;
import mwt.numerics.*;

public class Curvaceous implements CustomComputation {
  static class DanceData {
    float[] curve;
    float[] dist;
    float[] dirx;
    float[] diry;
  }

  float span = 0.0f;
  float disrupt = 0.0f;
  Choreography chore = null;
  HashMap< Dance , DanceData > cache = new HashMap< Dance, DanceData>();

  public Curvaceous() { }

  void printHelp() {
    //                  012345678911234567892123456789312345678941234567895123456789612345678971234567898
    System.out.println("WARNING: Thus plugin is undocumented, unsupported, and probably doesn't work.");
    System.out.println("Usage: --plugin Curvaceous[::<scale>[::<interrupt>]]");
    System.out.println("  Curvaceous reports how an animal's path curves as it moves");
    System.out.println("    <scale> is a number that specifies how much path to average over (in mm)");
    System.out.println("    <interrupt> specifies a threshold below which to ignore direction changes");
    System.out.println("    defaults are 0--just use the underlying path segmentation");
    System.out.println("  There are four custom outputs provided:");
    System.out.println("    Curvature (left = positive, right = negative)");
    System.out.println("    Cumulative distance traveled (forward=positive, backwards=negative)");
    System.out.println("    Instantaneous X direction of motion (as if following curved path)");
    System.out.println("    Instantaneous Y direction of motion (as if following curved path)");
    System.out.println("  Call the plugin multiple times to get multiple scales.");
  }

  public void initialize(String args[],Choreography chore) throws IllegalArgumentException,IOException,CustomHelpException {
    this.chore = chore;
    for (String arg: args) {
      if (arg.equalsIgnoreCase("help")) { printHelp(); throw new CustomHelpException(); }
      try {
        float f = Float.parseFloat(arg);
        if (f<=0) throw new IllegalArgumentException("Scale and interrupt parameters must be positive");
        if (span==0) span = f;
        else if (disrupt==0) disrupt = f;
        else throw new IllegalArgumentException("Can only specify scale and interrupt once");
      }
      catch (NumberFormatException nfe) { printHelp(); throw new IllegalArgumentException("Expected a numeric argument but didn't get one"); }
    }
  }

  public String desiredExtension() { return ""; }

  public boolean validateDancer(Dance d) { return true; }

  public int computeAll(File out_f) throws IOException { return 0; }

  public int computeDancerSpecial(Dance d,File out_f) throws IOException { return 0; }

  public int quantifierCount() { return 4; }

  public void computeDancerQuantity(Dance d,int which) throws IllegalArgumentException {
    if (d.quantity==null || d.quantity.length != d.area.length) d.quantity = new float[d.area.length];
    if (cache.get(d)!=null) {
      switch (which) {
        case 0: System.arraycopy(cache.get(d).curve, 0, d.quantity, 0, d.quantity.length); break;
        case 1: System.arraycopy(cache.get(d).dist, 0, d.quantity, 0, d.quantity.length); break;
        case 2: System.arraycopy(cache.get(d).dirx, 0, d.quantity, 0, d.quantity.length); break;
        case 3: System.arraycopy(cache.get(d).diry, 0, d.quantity, 0, d.quantity.length); break;
        default: throw new IllegalArgumentException("Curvaceous plugin only supplies three new outputs");
      }
      return;
    }
    DanceData dd = new DanceData();
    dd.curve = new float[d.quantity.length];
    dd.dist = new float[d.quantity.length];
    dd.dirx = new float[d.quantity.length];
    dd.diry = new float[d.quantity.length];
    for (int i=0;i<dd.curve.length;i++) dd.curve[i] = dd.dist[i] = dd.dirx[i] = dd.diry[i] = Float.NaN;
    if (d.segmentation==null) d.findSegmentation();
    d.findDirectionBiasSegmented(chore.minTravelPx(d));
    float[] bias = Arrays.copyOf(d.quantity,d.quantity.length);
    float last_dist = 0.0f;
    Vec2F u = new Vec2F();
    Vec2F v = new Vec2F();
    Vec2F w = new Vec2F();
    int n = 0;
    Style ds = d.segmentation[n];
    for (int i=0; i<bias.length ; i++) {
      if (i>ds.i1 && n<d.segmentation.length) { n++; ds = d.segmentation[n]; }
      if (Float.isNaN(bias[i]) || i<ds.i0 || i>ds.i1 || !d.loc_okay(d.centroid[i])) { u.eq(0,0); continue; }
      else if (bias[i]==0.0f || !ds.isLine()) { u.eq(0,0); dd.dirx[i] = dd.diry[i] = 0.0f; dd.dist[i] = last_dist; continue; }
      ds.snapToLine(v.eq(d.centroid[i]));
      if (u.x==0 && u.y==0) {
        dd.dirx[i] = dd.diry[i] = 0.0f;
        dd.dist[i] = last_dist;
        w.eq(0,0);
      }
      else {
        w.eq(v).eqMinus(u).eqNorm();
        last_dist += v.dist(u)*bias[i];
        dd.dist[i] = last_dist;
        dd.dirx[i] = w.x;
        dd.diry[i] = w.y;
      }
      if (ds.kind==Style.Styled.Arc) u.eq((float)ds.fit.circ.params.x0 - d.centroid[i].x,(float)ds.fit.circ.params.y0 - d.centroid[i].y);
      else u.eq(0,0);
      float sgn = (w.X(u)>0) ? -1.0f : ((w.X(u)<0) ? 1.0f : 0.0f);
      dd.curve[i] = (ds.kind==Style.Styled.Arc) ? sgn/(float)ds.fit.circ.params.R : 0;
      u.eq(v);
    }
    for (int i=0;i<bias.length;i++) {
      dd.curve[i] /= chore.mm_per_pixel;
      dd.dist[i] *= chore.mm_per_pixel;
    }
    if (span > 0) {
      for (int i=0;i<d.quantity.length;i++) d.quantity[i] = Float.NaN;
      Fitter f = new Fitter();
      f.automove = true;
      int i0=0;
      int i1=0;
      int limit=0;
      int j=0;
      boolean fwd = true;
      boolean escape = false;
      boolean last_escape;
      for (int i=0;i<bias.length;i++) {
        last_escape = escape;
        escape = false;
        if (Float.isNaN(dd.dist[i])) { limit=i0=i1=i+1; f.reset(); fwd=true; continue; }
        if (f.n==0) f.addC(d.centroid[i].x,d.centroid[i].y);
        else {
          w.eq((float)f.Ox,(float)f.Oy).eqMinus(d.centroid[i]).eqTimes(-1);
          if (w.length()*chore.mm_per_pixel > 2*span) f.moveBy(w.x,w.y);
        }
        while (Math.abs(dd.dist[i]-dd.dist[i0]) > 0.5*span && i0<i-1 && !Float.isNaN(dd.dist[i0+1])) { f.subC(d.centroid[i0].x, d.centroid[i0].y); i0++; }
        while (Math.abs(dd.dist[i]-dd.dist[i0]) < 0.5*span && i0>limit) { i0--; f.addC(d.centroid[i0].x,d.centroid[i0].y); }
        while (Math.abs(dd.dist[i]-dd.dist[i1]) < 0.5*span && i1<bias.length-1 && !Float.isNaN(dd.dist[i1+1])) {
          i1++;
          if (fwd) {
            if (dd.dist[i1]>dd.dist[j]) j = i1;
            else if (dd.dist[j]-dd.dist[i1] > disrupt) {
              f.reset();
              limit = i0 = j;
              if (i<j) i=j;
              fwd = false;
              for (int k=i0; k<=i1; k++) {
                f.addC(d.centroid[k].x,d.centroid[k].y);
                if (dd.dist[k]<dd.dist[j]) j=k;
              }
              escape = true;
              break;
            }
          }
          else {
            if (dd.dist[i1]<dd.dist[j]) j = i1;
            else if (dd.dist[i1]-dd.dist[j] > disrupt) {
              f.reset();
              limit = i0 = j;
              if (i<j) i=j;
              fwd = true;
              for (int k=i0; k<=i1; k++) {
                f.addC(d.centroid[k].x,d.centroid[k].y);
                if (dd.dist[k]>dd.dist[j]) j=k;
              }
              escape = true;
              break;
            }
          }
          f.addC(d.centroid[i1].x,d.centroid[i1].y);
        }
        if (escape) {
          if (last_escape) {
            limit=i0=i1=i+1; f.reset(); fwd=true;
          }
          else i--;
          continue;
        }
        if (Math.abs(dd.dist[i1]-dd.dist[i]) < 0.5*span || Math.abs(dd.dist[i]-dd.dist[i0]) < 0.25*span || i1-i0 < 6) continue;
        else {
          f.circ.fit();
          v.eq(dd.dirx[i],dd.diry[i]);
          w.eq((float)f.circ.params.x0 - d.centroid[i].x, (float)f.circ.params.y0 - d.centroid[i].y);
          float sgn = (v.X(w)>0) ? -1.0f : ((v.X(w)<0) ? 1.0f : 0.0f);
          d.quantity[i] = sgn/(float)(f.circ.params.R*chore.mm_per_pixel);
        }
      }
      System.arraycopy(d.quantity,0,dd.curve,0,dd.curve.length);
    }
    cache.put(d,dd);
    computeDancerQuantity(d,which);
  }

  public String quantifierTitle(int which) throws IllegalArgumentException {
    switch (which) {
      case 0: return "Path curve";
      case 1: return "Path length";
      case 2: return "Path direction X";
      case 3: return "Path direction Y";
      default: throw new IllegalArgumentException("Curvaceous plugin only supplies four new outputs");
    }
  }
}
