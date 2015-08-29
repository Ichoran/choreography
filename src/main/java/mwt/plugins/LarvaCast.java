/* LarvaCast.java - Measures head casting for Drosophila larvae
 * Copyright 2011 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015 Calico Life Sciences LLC (author Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt.plugins;

import java.io.*;

import mwt.*;
import mwt.numerics.*;

public class LarvaCast implements CustomComputation
{
  Choreography chore;
  boolean isAngle = false;
  boolean isFit = false;
  Vec2F origin = null;
  Dance buffered = null;
  float casts[] = null;
  Vec2F heads[] = null;
  Vec2F tails[] = null;
  Vec2F toorig[] = null;
  int head0 = -1;
  int head1 = -1;
  int tail0 = -1;
  int tail1 = -1;
  double fractionalh0 = 0.0;
  double fractionalh1 = 0.2;
  double fractionalt0 = 0.33;
  double fractionalt1 = 1.0;
  boolean headflipped,tailflipped;

  public LarvaCast() {
    chore = null;
  }

  void printHelp() {
    //                  012345678911234567892123456789312345678941234567895123456789612345678971234567898
    System.out.println("Usage: --plugin LarvaCast[::head=a,b][::tail=c,d][::max|fit|angle][::origin=x,y]");
    System.out.println("  LarvaCast makes a custom output type, head cast, available.");
    System.out.println("  head specifies the (inclusive) range of spine points to be used for the head");
    System.out.println("    use integers starting from 0, or fractions [0.0-0.1]; default is 0.0,0.2");
    System.out.println("  tail specifies the (inclusive) range of spine points to be used for the tail");
    System.out.println("    use integers for spine points or fractions [0.0-1.0]; default is 0.5,1.0");
    System.out.println("  max (the default) measures the distance between the farthest head point and");
    System.out.println("    the line defined by least squares fit to the tail points; left is positive");
    System.out.println("  fit first fits a line to the head points, then measures the distance between");
    System.out.println("    that line and the tail-fit line at its most distant point, if the head-fit");
    System.out.println("    line crosses the tail-line over the range of the head points, or the lateral");
    System.out.println("    displacement along the head points if not (left is positive)");
    System.out.println("  angle fits a line to the head points and the tail points and computes the");
    System.out.println("    angle between them (ccw from tail line to head line is positive)");
    System.out.println("  Seven outputs are available (NaN where head/tail are confused):");
    System.out.println("    - distance or angle to tail-line");
    System.out.println("    - x coordinate of most distant point of head (mm) or head x vector for angle");
    System.out.println("    - y coordinate of most distant point of head (mm) or head y vector for angle");
    System.out.println("    - x coordinate of vector pointing forward along tail (in mm)");
    System.out.println("    - y coordinate of vector pointing forward along tail (in mm)");
    System.out.println("    - x coordinate of center of tail (in mm)");
    System.out.println("    - y coordinate of center of tail (in mm)");
    System.out.println("  origin allows a coordinate origin to be defined (default units: mm);");
    System.out.println("    this causes coordinates and angles to be given relative to that origin");
    System.out.println("  This plugin requires the SpinesForward plugin and will call it automatically");
    System.out.println("    if needed.");
    System.out.println("  Note that --plugin LarvaCast::head=0.0,0.2::tail=0.33,1.0::angle will produce");
    System.out.println("    a signed equivalent to the standard 'kink' output; these values for head and");
    System.out.println("    tail are the defaults if no values are specified.  (angle is not default)");
  }

   // Called at the end of command-line parsing, before any data has been read
  public void initialize(String args[],Choreography chore) throws IllegalArgumentException,IOException,CustomHelpException {
    this.chore = chore;
    if (args!=null && args.length>0) for (String arg : args) {
      if (arg.equalsIgnoreCase("help")) { printHelp(); throw new CustomHelpException(); }
    }
    String[] requirements = {"SpinesForward"};
    String[] available = requirements.clone();
    chore.requirePlugins(available);
    for (String arg : args) {
      String ar = arg.toLowerCase();
      if (ar.equals("angle")) isAngle = true;
      else if (ar.equals("fit")) { isAngle = false; isFit = true; }
      else if (ar.equals("max")) { isAngle = false; isFit = false; }
      else if (ar.startsWith("origin=")) {
        String[] as = ar.substring(7).split(",");
        if (as.length != 2) throw new IllegalArgumentException("Origin should be two numbers separated by a comma.");
        try {
          origin = new Vec2F(Float.parseFloat(as[0]),Float.parseFloat(as[1]));
        }
        catch (NumberFormatException nfe) {
          throw new IllegalArgumentException("Malformed number in origin");
        }
      }
      else if (ar.startsWith("head=") || ar.startsWith("tail=")) {
        String[] as = ar.substring(5).split(",");
        if (as.length != 2) throw new IllegalArgumentException(ar.substring(0,4) + " should be two numbers separated by a comma.");
        int a = -1, b = -1;
        try {
          a = Integer.parseInt(as[0]);
          b = Integer.parseInt(as[1]);
        }
        catch (NumberFormatException nfe) {}
        if (b >= 0) {
          if (ar.startsWith("head")) { head0 = Math.min(a,b); head1 = Math.max(a,b); headflipped = (a>b); }
          else { tail0 = Math.min(a,b); tail1 = Math.max(a,b); tailflipped = (a>b); }
        }
        else {
          try {
            if (ar.startsWith("head")) { fractionalh0 = Double.parseDouble(as[0]); fractionalh1 = Double.parseDouble(as[1]); }
            else { fractionalt0 = Double.parseDouble(as[0]); fractionalt1 = Double.parseDouble(as[1]); }
          }
          catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Malformed number in "+ar.substring(0,4));
          }
        }
      }
    }
  }

  // Called before any method taking a File as an output target--this sets the extension
  public String desiredExtension() { return "lcast"; }

  // Called on freshly-read objects to test them for validity (after the normal checks are done).
  public boolean validateDancer(Dance d) { return true; }

  // Called before any regular output is produced.  Returns true if it actually created the file.
  public int computeAll(File out_f) throws IOException { return 0; }

  // Also called before any regular output is produced (right after computeAll).  Returns true if it created a file.
  public int computeDancerSpecial(Dance d,File out_f) throws IOException { return 0; }

  // Called when the C output option is given to figure out how many custom quantifications (output types) this plugin provides.
  public int quantifierCount() { return 7; }

  void verifyRanges(Dance d, int i) throws IllegalArgumentException {
    int n = d.spine[i].size();
    if (head0<0 || head1<0) {
      head0 = (int)Math.round((n-1)*Math.min(fractionalh0, fractionalh1));
      head1 = (int)Math.round((n-1)*Math.max(fractionalh0, fractionalh1));
      headflipped = (head0<head1 && fractionalh0>fractionalh1);
    }
    if (tail0<0 || tail1<0) {
      tail0 = (int)Math.round((n-1)*Math.min(fractionalt0, fractionalt1));
      tail1 = (int)Math.round((n-1)*Math.max(fractionalt0, fractionalt1));
      tailflipped = (tail0<tail1 && fractionalt0>fractionalt1);
    }
    if (head0<0) throw new IllegalArgumentException("LarvaCast head index out of range: "+head0);
    if (tail0<0) throw new IllegalArgumentException("LarvaCast tail index out of range: "+tail0);
    if (head1>=n) throw new IllegalArgumentException("LarvaCast head index out of range: "+head1);
    if (tail1>=n) throw new IllegalArgumentException("LarvaCast tail index out of range: "+tail1);
    if (tail0==tail1) throw new IllegalArgumentException("LarvaCast tail must be at least two spine points"+n+" "+tail0+" "+fractionalt0+" "+fractionalt1);
    if ((isAngle || isFit) && (head0==head1)) throw new IllegalArgumentException("LarvaCast head must be at least two spine points unless max method is used");
  }

  void computeAllQuantities(Dance d) throws IllegalArgumentException {
    boolean verified = false;
    if (casts==null || casts.length < d.area.length) {
      casts = new float[d.area.length];
      heads = new Vec2F[d.area.length];
      tails = new Vec2F[d.area.length];
      toorig = new Vec2F[d.area.length];
    }
    Fitter ft = new Fitter();
    Fitter fh = new Fitter();
    Vec2F u = new Vec2F();
    Vec2F v = new Vec2F();
    Vec2F w = new Vec2F();
    ft.shiftZero(false);
    fh.shiftZero(false);
    if (!isAngle && isFit) d.quantityIsMidline(false);
    for (int i=0; i<d.area.length; i++) {
      if (d.spine==null || d.spine[i]==null || !d.spine[i].oriented()) {
        casts[i] = Float.NaN;
        heads[i] = null;
        tails[i] = null;
        toorig[i] = null;
        continue;
      }

      if (!verified) { verifyRanges(d,i); verified=true; }

      ft.reset();
      for (int j=tail0; j<=tail1; j++) {
        d.spine[i].get(j,u);
        ft.addL(u.x, u.y);
      }

      ft.spot.fit();
      if (toorig[i] == null) toorig[i] = new Vec2F();
      toorig[i].eq((float)ft.spot.params.x0, (float)ft.spot.params.y0).eqPlus(d.centroid[i]);
      if (origin != null) toorig[i].eqMinus(origin);

      ft.line.fit();
      u.x = -(float)ft.line.params.a;
      u.y = (float)ft.line.params.b;
      u.eqNorm();
      d.spine[i].get(tail0,v);
      double l = ft.line.parallelCoord(v.x, v.y);
      d.spine[i].get(tail1,v);
      l -= ft.line.parallelCoord(v.x,v.y);
      if (tailflipped) l = -l;
      u.eqTimes((float)l);
      if (tails[i] == null) tails[i] = u.copy(); else tails[i].eq(u);

      if (isAngle) {
        fh.reset();
        for (int j=head0; j<=head1; j++) {
          d.spine[i].get(j,v);
          fh.addL(v.x, v.y);
        }
        fh.line.fit();
        u.eq(-(float)fh.line.params.a,(float)fh.line.params.b).eqNorm();
        d.spine[i].get(head0,v);
        double ll = fh.line.parallelCoord(v.x, v.y);
        d.spine[i].get(head1,v);
        ll -= fh.line.parallelCoord(v.x,v.y);
        if (headflipped) ll = -ll;
        u.eqTimes((float)ll);
        if (heads[i] == null) heads[i] = u.copy(); else heads[i].eq(u);
        v.eq(tails[i]);
        double theta = Math.acos(Math.max(-1.0,Math.min(1.0,u.eqNorm().dot(v.eqNorm()))));
        if (u.X(v)<0) theta = -theta;
        casts[i] = (float)(theta * 180 / Math.PI);
      }
      else if (isFit) {
        fh.reset();
        for (int j=head0; j<=head1; j++) {
          d.spine[i].get(j,v);
          fh.addL(v.x, v.y);
        }
        fh.spot.fit();
        fh.line.fit();
        float pdo = Math.abs((float)ft.line.perpendicularCoord(fh.spot.params.x0, fh.spot.params.y0));
        float hml = d.quantity[i]*(head1 - head0 - 1)/d.spine[i].size();

        // Unit vector pointing in head direction (in v; u destroyed)
        v.eq(-(float)fh.line.params.a,(float)fh.line.params.b).eqNorm();
        d.spine[i].get(head0,u).eqMinus(d.spine[i].get(head1,w));
        if ((u.dot(v) < 0) != headflipped) v.eqMinus();

        // Store vector to expected position of head (in heads[i], u destroyed)
        if (heads[i]==null) heads[i] = u.eq((float)fh.spot.params.x0,(float)fh.spot.params.y0).copy(); else heads[i].eq((float)fh.spot.params.x0,(float)fh.spot.params.y0);
        u.eq(v).eqTimes(hml*0.5f);
        heads[i].eqPlus(u).eqPlus(d.centroid[i]);
        if (origin != null) heads[i].eqMinus(origin);

        // Figure out whether line crosses or not; if yes, use head estimate, otherwise just angle (w,u destroyed)
        w.eq(tails[i]).eqNorm();
        float pdl = hml * Math.abs(1-w.dot(v));
        u.eq(heads[i]).eqMinus(d.centroid[i]);
        if (pdl < pdo*2) casts[i] = (float)ft.line.perpendicularCoord(u.x,u.y); else casts[i] = pdl;
        if (v.X(w) < 0) casts[i] = -casts[i];
      }
      else {
        double maxd = -1;
        int maxj = -1;
        for (int j = head0; j <= head1; j++) {
          d.spine[i].get(j,v);
          double dd = ft.line.perpendicularCoord(v.x, v.y);
          if (dd > maxd) {
            maxd = dd;
            maxj = j;
          }
        }
        d.spine[i].get(maxj,v).eqPlus(d.centroid[i]);
        if (origin != null) v.eqMinus(origin);
        if (heads[i] == null) heads[i] = v.copy(); else heads[i].eq(v);
        d.spine[i].get(maxj,v);
        u.eq((float)ft.spot.params.x0, (float)ft.spot.params.y0);
        v.eqMinus(u);
        if (v.X(tails[i]) < 0) maxd = -maxd;
        casts[i] = (float)maxd;
      }
    }
  }

  float xOrNaN(Vec2F v) { return (v==null) ? Float.NaN : v.x; }
  float yOrNaN(Vec2F v) { return (v==null) ? Float.NaN : v.y; }

  // This is called whenever the plugin is required to handle a custom quantification.
  public void computeDancerQuantity(Dance d,int which) throws IllegalArgumentException {
    if (which<0 || which>=quantifierCount()) throw new IllegalArgumentException("LarvaCast only computes one value (0); "+which+" asked for on object "+d.ID);
    if (buffered != d) computeAllQuantities(d);
    switch(which) {
      case 0:
        float scale = (isAngle) ? 1.0f : chore.mm_per_pixel;
        for (int i=0;i<d.area.length;i++) d.quantity[i] = scale*casts[i];
        break;
      case 1: for (int i=0;i<d.area.length;i++) d.quantity[i] = xOrNaN(heads[i]); break;
      case 2: for (int i=0;i<d.area.length;i++) d.quantity[i] = yOrNaN(heads[i]); break;
      case 3: for (int i=0;i<d.area.length;i++) d.quantity[i] = xOrNaN(tails[i]); break;
      case 4: for (int i=0;i<d.area.length;i++) d.quantity[i] = yOrNaN(tails[i]); break;
      case 5: for (int i=0;i<d.area.length;i++) d.quantity[i] = xOrNaN(toorig[i]); break;
      case 6: for (int i=0;i<d.area.length;i++) d.quantity[i] = yOrNaN(toorig[i]); break;
    }
  }

  // This is called when a custom quantification is graphed to provide a title for it.
  public String quantifierTitle(int which) throws IllegalArgumentException {
    switch (which) {
      case 0: return "Head Cast";
      case 1: return (isAngle) ? "Head Vector X" : "Head Coordinate X";
      case 2: return (isAngle) ? "Head Vector Y" : "Head Coordinate Y";
      case 3: return "Tail Vector X";
      case 4: return "Tail Vector Y";
      case 5: return "Tail Center X";
      case 6: return "Tail Center Y";
      default: throw new IllegalArgumentException("LarvaCast computes values 0-"+(quantifierCount()-1)+"; asked for "+which);
    }
  }
};


