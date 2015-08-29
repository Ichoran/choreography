/* Filter.java - Plugin for Choreography to select animals to include for analysis.
 * Copyright 2013 Howard Hughes Medical Institute and Rex Kerr
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

public class Filter implements CustomComputation{
  float[] t;
  float speed_window;
  float mm_per_pixel;
  int[][] events;
  ArrayList<Condition> conditions;

  abstract class F { F() {}; abstract float value(); }
  F one = new F() { float value() { return 1; } };
  F pix = new F() { float value() { return mm_per_pixel; } };
  F pxq = new F() { float value() { return mm_per_pixel*mm_per_pixel; } };
  F rad = new F() { float value() { return (float)(180/Math.PI); } };


  // WARNING WARNING WARNING SHARED MUTABLE STATE WARNING WARNING WARNING
  abstract class Q {
    Choreography.DataSource ds;
    float offs;  
    float mult = Float.NaN;
    F lazymult;
    Q(Choreography.DataSource ds, F lazymult) { this.ds = ds; this.lazymult = lazymult; this.offs = 0; }
    void using(Dance d) { mult = lazymult.value(); load(d); }
    abstract void load(Dance d);
    float value(Dance d, int i) { return (d.quantity[i] - offs)*mult; }
  }
  Q[] canFilter = {
    new Q(Choreography.DataSource.TIME,one) { void load(Dance d) { d.quantityIsTime(t,false); } },
    new Q(Choreography.DataSource.FNUM,one) { void load(Dance d) { d.quantityIsFrame(); } },
    new Q(Choreography.DataSource.OBID,one) { void load(Dance d) { d.quantityIs(d.ID); } },
    new Q(Choreography.DataSource.AREA,pxq) { void load(Dance d) { d.quantityIsArea(false); } },
    new Q(Choreography.DataSource.PERS,one) { void load(Dance d) { offs = t[d.first_frame]; d.quantityIsTime(t,false); } },
    new Q(Choreography.DataSource.SPED,pix) { void load(Dance d) { d.quantityIsSpeed(t, speed_window, false, false); } },
    new Q(Choreography.DataSource.ASPD,rad) { void load(Dance d) { d.quantityIsAngularSpeed(t, speed_window, false); } },
    new Q(Choreography.DataSource.LENG,pix) { void load(Dance d) { d.quantityIsLength(false); } },
    new Q(Choreography.DataSource.RLEN,one) { void load(Dance d) { d.quantityIsLength(false); double l=0.0; for (int i=0;i<d.centroid.length;i++) l+=d.quantity[i]; mult = (float)(1/l); } },
    new Q(Choreography.DataSource.WIDT,pix) { void load(Dance d) { d.quantityIsWidth(false); } },
    new Q(Choreography.DataSource.RWID,one) { void load(Dance d) { d.quantityIsWidth(false); double w=0.0; for (int i=0;i<d.centroid.length;i++) w+=d.quantity[i]; mult = (float)(1/w); } },
    new Q(Choreography.DataSource.ASPC,one) { void load(Dance d) { d.quantityIsAspect(false); } },
    new Q(Choreography.DataSource.RASP,one) { void load(Dance d) { d.quantityIsAspect(false); double a=0.0; for (int i=0;i<d.centroid.length;i++) a+=d.quantity[i]; mult = (float)(1/a); } },
    new Q(Choreography.DataSource.LOCX,pix) { void load(Dance d) { d.quantityIsX(false); } },
    new Q(Choreography.DataSource.LOCY,pix) { void load(Dance d) { d.quantityIsY(false); } },
    new Q(Choreography.DataSource.VELX,pix) { void load(Dance d) { d.quantityIsVx(t, speed_window, false, false); } },
    new Q(Choreography.DataSource.VELY,pix) { void load(Dance d) { d.quantityIsVy(t, speed_window, false, false); } },
    new Q(Choreography.DataSource.ORNT,pxq) { void load(Dance d) { d.quantityIsTheta(false); } },
    new Q(Choreography.DataSource.STI1,one) { void load(Dance d) { d.quantityIsStim(events, 1); } },
    new Q(Choreography.DataSource.STI2,one) { void load(Dance d) { d.quantityIsStim(events, 2); } },
    new Q(Choreography.DataSource.STI3,one) { void load(Dance d) { d.quantityIsStim(events, 3); } },
    new Q(Choreography.DataSource.STI4,one) { void load(Dance d) { d.quantityIsStim(events, 4); } }
  };

  class Bound {
    Q qf;
    boolean above;
    float value;
    Bound(Q qf, boolean above, float value) {
      this.qf = qf;
      this.above = above;
      this.value = value;
    }
    int obeysAvg(Dance d, int tally) {
      qf.using(d);
      double sum = 0.0;
      int n = 0;
      for (int i = 0; i < d.centroid.length; i++) {
        float v = qf.value(d,i);
        if (!Float.isNaN(v)) { sum += v; n += 1; }
      }
      float v = (float)(sum/n);
      if (Float.isNaN(v)) return Integer.MIN_VALUE;
      else if (above && v >= value)  return tally+1;
      else if (!above && v <= value) return tally+1;
      else return tally;
    }
    int[] obeys(Dance d, int[] tally) {
      qf.using(d);
      for (int i = 0; i < d.centroid.length; i++) {
        float v = qf.value(d,i);
        if (Float.isNaN(v)) tally[i] = Integer.MIN_VALUE;
        if (above && v >= value) tally[i]++;
        else if (!above && v <= value) tally[i]++;
      }
      return tally;
    }
  }

  abstract class Condition {
    Bound[] bounds;
    int[] getTally(Dance d) {
      int[] tally = new int[d.centroid.length];
      for (Bound b : bounds) b.obeys(d, tally);
      return tally;
    }
    abstract boolean obeys(Dance d);
  }
  class InCondition extends Condition {
    InCondition(Bound[] bounds) { this.bounds = bounds; }
    boolean obeys(Dance d) {
      for (int n : getTally(d)) if (n==0) return false;
      return true;
    }
  }
  class OutCondition extends Condition {
    OutCondition(Bound[] bounds) { this.bounds = bounds; }
    boolean obeys(Dance d) {
      for (int n : getTally(d)) if (n==0) return true;
      return false;
    }
  }
  class AvgCondition extends Condition {
    AvgCondition(Bound[] bounds) { this.bounds = bounds; }
    boolean obeys(Dance d) {
      int n = 0;
      for (Bound b : bounds) n = b.obeysAvg(d, n);
      return n > 0;
    }
  }

  public Filter() {
    t = null;
    speed_window = 0.5f;
    mm_per_pixel = 0.025f;
    events = null;
    conditions = null;
    System.out.println("Filter constructor");
  }

  public void printHelp(boolean exception) throws CustomHelpException {
    //                  012345678911234567892123456789312345678941234567895123456789612345678971234567898
    System.out.println("Usage: --plugin Filter::<condition>[::condition][::condition]...");
    System.out.println("  A condition has the form <in|out|avg>=<bound>[,bound][,bound]...");
    System.out.println("  A bound has the form <parameter><+|-><value>");
    System.out.println("A bound with + means that the parameter must be equal to or above the value");
    System.out.println("  - means the parameter must be equal to or below the value");
    System.out.println("An IN condition is fulfilled if ANY of the bounds is met at every timepoint");
    System.out.println("  An OUT condition is failed if ANY of the bounds are met at every timepoint");
    System.out.println("  An AVG condition is fulfilled if the averages meet ANY of the bounds");
    System.out.println("Valid animals must fulfill ALL in/avg conditions and fail no out conditions");
    System.out.println();
    System.out.println("Examples:");
    System.out.println("--plugin Filter::in=e+0.1,l+0.8");
    System.out.println("    animals must everywhere either have 0.1 mm^2 area or be 0.8mm long");
    System.out.println("--plugin Filter::in=e+0.1::in=l+0.8");
    System.out.println("    animals must everwyhere both have 0.1 mm^2 area and be 0.8mm long");
    System.out.println("--plugin Filter::out=e-0.1,l-0.8");
    System.out.println("    rejects animals always either smaller than 0.1 mm^2 or shorter than 0.8 mm");
    System.out.println("--plugin Filter::out=e-0.1::out=l-0.8");
    System.out.println("    rejects animals always smaller than 0.1 mm^2 or always shorter than 0.8 mm");
    System.out.println("--plugin Filter::avg=e+0.1::avg=l+0.8");
    System.out.println("    animals must have average area of 0.1 mm^2 and average length of 0.8 mm");
    System.out.println("Either long or short form for parameters can be used (e.g. e or area).");
    System.out.println("Only the following simple parameters are available to be used:");
    System.out.print(" ");
    int n = 1;
    for (Q qf : canFilter) {
      String text = " "+Choreography.DataSource.toText(qf.ds);
      if (text.length() + n >= 80) {
        System.out.print("\n ");
        n = 1;
      }
      n += text.length();
      System.out.print(text);
    }
    System.out.println();
    System.out.println("Parameters from plugins are not available.");
    System.out.println("Only the default units can be used.  Alternates (e.g. body lengths) cannot.");
    if (exception) throw new CustomHelpException();
  }

  public void initialize(String[] args, Choreography chore) throws CustomHelpException, IllegalArgumentException {
    t = chore.times;
    mm_per_pixel = chore.mm_per_pixel;
    speed_window = chore.speed_window;
    events = chore.events;
    conditions = new ArrayList<Condition>();
    for (String a : args) if (a.toLowerCase().equals("help")) printHelp(true);
    for (String a : args) {
      String[] splitA = a.split("=");
      if (splitA.length != 2) throw new IllegalArgumentException("Argument '"+a+"' was not of form param=bounds-list");
      int which;
      String io = splitA[0].toLowerCase();
      if (io.equals("in")) which = 1;
      else if (io.equals("out")) which = -1;
      else if (io.equals("avg")) which = 0;
      else throw new IllegalArgumentException("Argument started with '"+splitA[0]+"' instead of 'in' or 'out'");
      String[] bs = splitA[1].split(",");
      ArrayList<Bound> bounds = new ArrayList<Bound>();
      for (String b : bs) {
        int ip = b.indexOf('+');
        int in = b.indexOf('-');
        int i = (ip < 0) ? in : (in < 0) ? ip : Math.min(ip,in);
        if (i<0) throw new IllegalArgumentException("No + or - found in '"+b+"' from "+splitA[1]);
        String param = b.substring(0,i);
        char pm = b.charAt(i);
        String quant = b.substring(i+1);
        Choreography.DataSource ds = Choreography.DataSource.interpret(param);
        Q q = null;
        for (Q qi : canFilter) if (qi.ds == ds) { q = qi; break; }
        if (q == null) throw new IllegalArgumentException("Data specifier not supported: "+param);
        float v = Float.NaN;
        try { v = Float.parseFloat(quant); }
        catch (NumberFormatException nfe) { throw new IllegalArgumentException("Not actually a float: '"+quant+"' in "+b); }
        bounds.add(new Bound(q, pm=='+', v));
      }
      Bound[] bounded = bounds.toArray(new Bound[bounds.size()]);
      conditions.add((which==1) ? new InCondition(bounded) : (which==0) ? new AvgCondition(bounded) : new OutCondition(bounded));
    }
  }

  public boolean validateDancer(Dance d) {
    for (Condition c : conditions) if (!c.obeys(d)) return false;
    return true;
  }

  public String desiredExtension() { return ""; }

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

