/* MeasureReversal.java - Tap habituation plugin for Choreography
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

public class MeasureReversal implements CustomComputation {
  public Choreography chore;
  public double dt = 1.0;
  public double idt = 0.0;
  public ArrayList<EventTrigger> triggers = new ArrayList<EventTrigger>();
  public PrintWriter pw = null;
  public boolean separate_files = true;
  public boolean global_phase = false;
  public boolean send_to_file = true;
  public boolean output_coordinates = false;
  public double toobrief = 0.0;
  public double toosmall = 0.0;
  double time_hist = 0.0;
  public ArrayList<Result> results = null;
  public HashMap< Dance , Reversal[] > lookup = new HashMap< Dance , Reversal[] >();
  private String postfix = "rev";
  
  public class Result {
    public int event_index;
    public Dance d;
    public double distance;
    public double duration;
    public Result(int ei,Dance d0,double dist,double dur) {
      event_index = ei;
      d = d0;
      distance = dist;
      duration = dur;
    }
  }

  public static class EventTrigger {
    public Choreography.DataSource event = Choreography.DataSource.CUST;
    public Vec2D range = new Vec2D(0,Double.POSITIVE_INFINITY);
    public EventTrigger() {}
    public EventTrigger(Choreography.DataSource ev, double r0, double r1) {
      event = ev;
      range.x = r0;
      range.y = r1;
    }
  }
  
  public MeasureReversal() {
  }
  
  public void printHelp() throws CustomHelpException {
    System.out.println("Usage: --plugin MeasureReversal[::option1][::option2]...");
    System.out.println("Options: <event>[+lo][-hi] dt=<time>  idt=<time>  collect[=tm]  coords");
    System.out.println("         toobrief=<time>  toosmall=<dist (pixels)>  postfix=<string>");
    System.out.println("  Measures reversals within dt of an event.");
    System.out.println("  Default event is ::all, which measures all reversals.");
    System.out.println("  Any output type save custom can be used as a trigger event (speed, tap, etc.)");
    System.out.println("    Adding +lo triggers when output goes above lo (default is lo==0)");
    System.out.println("    Adding -hi triggers when output goes below hi (default is hi==infinity)");
    System.out.println("    For example, ::speed+0.2 looks for reversals after animal reaches 0.2mm/s");
    System.out.println("    Stimuli like ::tap work without needing to specify limits.");
    System.out.println("    If you want to trigger off of any of multiple events, just list them");
    System.out.println("    If you mix 'all' with specific events, the specific ones are ignored");
    System.out.println("  Reversals starting within dt after the event are counted (default=1s)");
    System.out.println("    If a reversal ends and another begins within dt, it is appended to the next");
    System.out.println("    A reversal already in progress before the event is ignored.");
    System.out.println("  Alternatively, any pair of events with less than time idt between them");
    System.out.println("    are merged into a single event (default = 0)");
    System.out.println("  Reversals that take too little time or space can be excluded");
    System.out.println("    using toobrief and toosmall (defaults = 0)");
    System.out.println("  ::collect places everything in one file; default is one file per object.");
    System.out.println("    =tm gives a histogram of the results, grouped in windows tm wide (tm>0)");
    System.out.println("    If trigger events are used, the histogram bins by trigger time");
    System.out.println("    Don't use ::collect with -N.");
    System.out.println("  coords appends the x,y position and initial u,v direction of the reversal");
    System.out.println("    to the output (single events only; collect= data is not averaged)");
    System.out.println("  Format:");
    System.out.println("    all ->        object_id  t_reversal  reversal_distance  reversal_duration");
    System.out.println("    others ->     object_id  t_event     reversal_distance  reversal_duration");
    System.out.println("    collect=tm -> time #wrongway #nothing #respond <dist_data> <duration_data>");
    System.out.println("      <data> is   avg  std  sem  min  25th%  median  75th%  max");
    System.out.println("    coords adds four columns:    x_pos  y_pos  x_dir  y_dir");
    System.out.println("  If you enter multiple options of the same type, only the last takes effect.");
    System.out.println("    You may, however, invoke the plugin multiple times with different options.");
    throw new CustomHelpException();
  }
  
  public void seekHelp(String args[]) throws CustomHelpException {
    for (String s: args) if (s.equalsIgnoreCase("help")) printHelp();
  }
  
  public static double parseDoubleOption(String s, String tag, String err,double limit) throws IllegalArgumentException {
    String untagged = s.substring(tag.length()+1);
    try {
      double value = Double.parseDouble(untagged);
      if (value < limit) throw new IllegalArgumentException();
      return value;
    }
    catch (IllegalArgumentException iae) { throw new IllegalArgumentException("Not a valid " + err + ": " + untagged); }
  }
      
  
  public void initialize(String args[], Choreography chore0) throws IOException,IllegalArgumentException,CustomHelpException {
    chore=chore0;
    if (!chore.segment_path) throw new IllegalArgumentException("Reversals can only be measured with path segmentation.");
    
    seekHelp(args);
    
    for (String s : args) {
      String slo = s.toLowerCase();
      if (slo.startsWith("collect=")) {
        try { time_hist = Double.parseDouble(s.substring(8)); }
        catch (IllegalArgumentException iae) { throw new IllegalArgumentException("Bad value for time window for histogram: " + s.substring(8)); }
        if (time_hist <= 0) throw new IllegalArgumentException("Time window for histogram must be greater than 0.0");
        separate_files = false;
        send_to_file = false;
        results = new ArrayList<Result>();
      }
      else if (slo.equalsIgnoreCase("collect")) separate_files = false;
      else if (slo.equals("coords")) output_coordinates = true;
      else if (slo.startsWith("dt=")) dt = parseDoubleOption(slo,"dt","time",Float.MIN_NORMAL);
      else if (slo.startsWith("idt=")) idt = parseDoubleOption(slo,"idt","interval time",0);
      else if (slo.startsWith("toobrief=")) toobrief = parseDoubleOption(slo,"toobrief","minimum reversal duration",0);
      else if (slo.startsWith("toosmall=")) toosmall = parseDoubleOption(slo,"toosmall","minimum reversal distance",0);
      else if (slo.startsWith("postfix=")) { postfix = slo.substring(8); }
      else if (slo.equals("all")) { triggers = null; }
      else {
        int plus_loc = s.indexOf('+');
        int minus_loc = s.indexOf('-');
        String plus_str = "0.0";
        String minus_str = "Infinity";
        if (plus_loc>0) {
          if (minus_loc>0) {
            if (minus_loc > plus_loc) {
              plus_str = s.substring(plus_loc+1,minus_loc);
              minus_str = s.substring(minus_loc+1);
            }
            else {
              plus_str = s.substring(plus_loc+1);
              minus_str = s.substring(minus_loc+1,plus_loc);
            }
          }
          else plus_str = s.substring(plus_loc+1);
        }
        else if (minus_loc>0) {
          minus_str = s.substring(minus_loc+1);
        }
        double plus,minus;
        try { plus = Double.parseDouble(plus_str); }
        catch (IllegalArgumentException iae) { throw new IllegalArgumentException("Not a valid event lower bound: " + plus_str); }
        try { minus = Double.parseDouble(minus_str); }
        catch (IllegalArgumentException iae) { throw new IllegalArgumentException("Not a valid event upper bound: " + minus_str); }
        int event_loc = s.length();
        if (plus_loc>0) event_loc = Math.min(event_loc,plus_loc);
        if (minus_loc>0) event_loc = Math.min(event_loc,minus_loc);
        String event_str = s.substring(0,event_loc);
        if (triggers != null) {
          EventTrigger et = new EventTrigger(Choreography.DataSource.interpret(event_str), plus, minus);
          triggers.add(et);
        }
      }
    }
  }
  
  public String desiredExtension() { return postfix; }
  
  public boolean validateDancer(Dance d) {
    if (d.outline == null) return false;
    if (d.spine == null) return false;
    return true;
  }
  
  public float[] arraylistdouble2floatarray(ArrayList<Double> ald , double scale) {
    float[] f = new float[ald.size()];
    int n = 0;
    for (double d : ald) f[n++] = (float)(d * scale);
    return f;
  }
  public int computeAll(File out_f) throws IOException {
    if (separate_files || out_f == null) return 0;
    global_phase = true;
    boolean did_something = false;
    for (Dance d : chore.dances) {
      if (d==null) continue;
      did_something |= (computeDancerSpecial(d,out_f) != 0);
    }
    if (pw != null) {
      pw.close();
      pw = null;
    }
    global_phase = false;
    if (results != null && results.size()>0) {
      double[] timearray = new double[ results.size() ];
      int n = 0;
      for (Result r : results) timearray[n++] = r.d.t(r.event_index);
      Arrays.sort(timearray);
      ArrayList<Double> timebins = new ArrayList<Double>();
      timebins.add( timearray[0] );
      for (n = 1 ; n < timearray.length ; n++) {
        if (timearray[n] - timebins.get( timebins.size()-1 ) > time_hist) timebins.add(timearray[n]);
      }
      double[] times = new double[timebins.size()];
      n = 0;
      for (double d : timebins) times[n++] = d;
      ArrayList< ArrayList<Double> > timeset = new ArrayList< ArrayList<Double> >(times.length);
      ArrayList< ArrayList<Double> > distset = new ArrayList< ArrayList<Double> >(times.length);
      ArrayList< ArrayList<Double> > duraset = new ArrayList< ArrayList<Double> >(times.length);
      for (n = 0 ; n < times.length ; n++) {
        timeset.add(new ArrayList<Double>());
        distset.add(new ArrayList<Double>());
        duraset.add(new ArrayList<Double>());
      }
      for (Result r : results) {
        n = Arrays.binarySearch(times , r.d.t(r.event_index) );
        if (n<0) n = (-n)-2;
        if (n<0) n = 0;
        timeset.get(n).add( (double)r.d.t(r.event_index) );
        distset.get(n).add( r.distance );
        duraset.get(n).add( r.duration );
      }
      for (n = 0 ; n < times.length ; n++) {
        if (timeset.get(n).size()==0) continue;
        Statistic tstat = new Statistic( arraylistdouble2floatarray( timeset.get(n) , 1.0 ) );
        float[] distarr = arraylistdouble2floatarray( distset.get(n) , chore.mm_per_pixel );  
        float[] duraarr = arraylistdouble2floatarray( duraset.get(n) , 1.0 ); 
        Arrays.sort( distarr );  // Sort these to divide values into negative, zero, positive
        Arrays.sort( duraarr );  // Scrambled w.r.t. distance, but sign(distance)==sign(duration), so it's okay (we're just averaging the + ones)
        int inega = 0;
        int izero = 0;
        int iposi = 0;
        while (izero < distarr.length && distarr[izero]<0) izero++;
        iposi = izero;
        while (iposi < distarr.length && distarr[iposi]<=0) iposi++;
        Statistic distat = (iposi<distarr.length) ? new Statistic(distarr , iposi , distarr.length) : null;
        Statistic dustat = (iposi<duraarr.length) ? new Statistic(duraarr , iposi , duraarr.length) : null;
        pw = Choreography.nfprintf(pw, out_f, "%.3f  %d %d %d", tstat.average, izero-inega, iposi-izero, distarr.length-iposi);
        if (distat==null || dustat==null || distarr.length-iposi <= 0) {
          pw = Choreography.nfprintf(pw,out_f,"   0 0 0  0 0 0 0 0   0 0 0  0 0 0 0 0\n");
        }
        else {
          pw = Choreography.nfprintf(pw, out_f, "   %.3f %.3f %.3f  %.3f %.3f %.3f %.3f %.3f",
            distat.average, distat.deviation, distat.deviation/Math.sqrt(Math.max(distat.n-1,1)),
            distat.minimum, distat.first_quartile, distat.median, distat.last_quartile, distat.maximum);
          pw = Choreography.nfprintf(pw, out_f, "   %.2f %.2f %.2f  %.2f %.2f %.2f %.2f %.2f\n",
            dustat.average, dustat.deviation, dustat.deviation/Math.sqrt(Math.max(dustat.n-1,1)),
            dustat.minimum, dustat.first_quartile, dustat.median, dustat.last_quartile, dustat.maximum);
        }
      }
      if (pw!=null) {
        did_something = true;
        pw.close();
        pw = null;
      }
    }
    return did_something ? 1 : 0;
  }

  static class TempResult implements Comparable<TempResult> {
    Dance d;
    int ei;
    double dist;
    double dur;
    Vec2F pos;
    Vec2F dir;
    public TempResult(Dance d0, int ei0, double dist0, double dur0, Vec2F pos0, Vec2F dir0) {
      d = d0; ei = ei0; dist = dist0; dur = dur0; pos = pos0; dir = dir0;
    }
    public int compareTo(TempResult tr) {
      if (ei + d.first_frame < tr.ei + tr.d.first_frame) return -1;
      else if(ei + d.first_frame > tr.ei + tr.d.first_frame) return 1;
      else if (dur > tr.dur) return -1;
      else if (dur < tr.dur) return 1;
      else return 0;
    }
  }
  private ArrayList<TempResult> temp_results = new ArrayList<TempResult>();
  public void save(Dance d,int ei,double dist,double dur,Vec2F pos,Vec2F dir) {
    temp_results.add( new TempResult(d, ei, dist, dur, pos, dir) );
  }
  public void sort_and_write(File out_f) throws IOException {
    for (int i=1,j=0; i<temp_results.size(); i++) {
      TempResult a = temp_results.get(j);
      TempResult b = temp_results.get(i);
      if (a.d.ID == b.d.ID && a.ei == b.ei) temp_results.set(i, null);
      else j = i;
    }
    for (TempResult tr: temp_results) {
      if (tr == null) continue;
      if (results != null) {
        results.add( new Result(tr.ei,tr.d,tr.dist,tr.dur) );
      }
      if (send_to_file) {
        if (output_coordinates) {
          pw = Choreography.nfprintf(pw, out_f, "%05d %.2f  %.3f %.3f  %.3f %.3f  %.3f %.3f\n",
                                     tr.d.ID, tr.d.t(tr.ei), tr.dist * chore.mm_per_pixel, tr.dur,
                                     tr.pos.x*chore.mm_per_pixel, tr.pos.y*chore.mm_per_pixel, tr.dir.x, tr.dir.y);
        }
        else pw = Choreography.nfprintf(pw, out_f, "%05d %.2f  %.3f %.3f\n", tr.d.ID, tr.d.t(tr.ei), tr.dist * chore.mm_per_pixel, tr.dur);
      }
    }
    temp_results.clear();
  }
  public void addRetro(Dance d,int i,float dt,float value) {
    d.quantity[i] += value;
    for (int j=i-1 ; j>=0 && d.dt(j,i) <= dt ; j--) d.quantity[j] += value;
  }
  public boolean hasBackwards(float[] bak,int i0,int i1) {
    for (int i=i0 ; i<=i1 ; i++) if (bak[i]>0) return true;
    return false;
  }
  public class Reversal {
    public int i0;
    public int i1;
    public int j0;
    public double traveled;
    public boolean backwards;
    public Dance d;
    public Reversal(Dance d0,int i,int j,float[] bias) {
      d = d0;
      Dance.Style s = d.segmentation[i];
      i0 = i;
      j0 = j;
      if (s.directions()==0) { backwards = false; traveled = 0.0; }
      else if (s.directions()==1) { backwards = hasBackwards(bias , s.i0+1 , s.i1-1); traveled = s.distanceTraversed(0); }
      else { backwards = hasBackwards(bias , s.endpoints[j0]+1 , s.endpoints[j0+1]-1); traveled = s.distanceTraversed(j0); }
      i1 = i0;
      if (j0+1 >= s.directions() && i0+1<d.segmentation.length) {
        boolean different = false;
        do {
          i1++;
          Dance.Style ss = d.segmentation[i1];
          if (backwards && !ss.isLine()) { i1--; different=true; }
          else if (ss.dotWith(s) < -0.33f) { i1--; different=true; }
          else if (backwards != hasBackwards(bias , ss.i0+1 , (ss.endpoints==null || ss.endpoints.length<2) ? ss.i1-1 : ss.endpoints[1]-1)) {
            i1--; different=true;
          }
          else if (ss.directions()>1) {
            traveled += ss.distanceTraversed(0);
            different=true;
          }
          else if (ss.directions()>0) traveled += ss.distanceTraversed(0);
        } while (i1+1 < d.segmentation.length && !different);
      }
    }
    public int nextI() {
      if (d.segmentation[i1].directions() > j0+1) return i1;
      else return i1+1;
    }
    public int nextJ() {
      if (d.segmentation[i1].directions() > j0+1) return j0+1;
      else return 0;
    }
    public int index0() {
      return (j0 > 0) ? d.segmentation[i0].endpoints[j0] : d.segmentation[i0].i0;
    }
    public int index1() {
      if (i1==i0) return (d.segmentation[i0].directions()>1) ? d.segmentation[i0].endpoints[j0+1] : d.segmentation[i0].i1;
      else return (d.segmentation[i1].directions()>1) ? d.segmentation[i1].endpoints[1] : d.segmentation[i1].i1;
    }
    public float time0() { return d.t(index0()); }
    public float time1() { return d.t(index1()); }
    public float lasted() {
      return time1() - time0();
    }
    public Vec2F xy0(Vec2F v) { return v.eq(d.centroid[index0()]); }
    public Vec2F xy1(Vec2F v) { return v.eq(d.centroid[index1()]); }
    public Vec2F uv0(Vec2F v) {
      if (j0 > 0) d.segmentation[i0].pickVector(v, j0); else d.segmentation[i0].initialVector(v);
      v.eqNorm();
      return v;
    }
  }
  public int computeDancerSpecial(Dance d,File out_f) throws IOException {
    if (separate_files==global_phase && out_f!=null) return 0;
    if (d.segmentation==null) return 0;
    if (d.quantity==null || d.quantity.length != d.area.length) d.quantity = new float[d.area.length];
    
    int i,j,k;
    boolean did_something = false;
    for (i=0; i<d.area.length; i++) d.quantity[i]=0;
    d.findDirectionBias(chore.speed_window,chore.times,chore.minTravelPx(d));
    float[] bias = new float[d.area.length];
    for (i=0; i<d.area.length ; i++) {
      bias[i] = Math.max(0,-d.quantity[i]);
      d.quantity[i] = -100.0f;
    }

    LinkedList<Reversal> pieces = new LinkedList<Reversal>();
    if (d.segmentation.length > 0) {
      pieces.add( new Reversal(d,0,0,bias) );
      while (pieces.peekLast().nextI() < d.segmentation.length) {
        pieces.add( new Reversal( d , pieces.peekLast().nextI() , pieces.peekLast().nextJ() , bias ) );
      }
    }
    if (toobrief > 0 || toosmall > 0) {
      Iterator<Reversal> ir = pieces.iterator();
      while (ir.hasNext()) {
        Reversal r = ir.next();
        if (r.traveled < toosmall || r.lasted() < toobrief) ir.remove();
      }
    }
    int bkw = 0;
    for (Reversal r : pieces) { /*if (r.backwards)*/ bkw++; }
    Reversal[] saved = new Reversal[bkw];
    bkw = 0;
    for (Reversal r : pieces) { /*if (r.backwards)*/ saved[bkw++] = r; }
    lookup.put(d, saved);
    if (out_f==null || separate_files==global_phase) {
      return 0;
    }
    if (triggers == null || triggers.size()==0) {
      Vec2F xy = new Vec2F();
      Vec2F uv = new Vec2F();
      for (Reversal q : pieces) {
        if (q.backwards) {
          did_something = true;
          save(d,q.index0(),q.traveled,q.lasted(),q.xy0(xy),q.uv0(uv));
        }
      }
    }
    else for (EventTrigger et : triggers) {
      double mult = chore.loadDancerWithData(d,et.event,Choreography.DataMeasure.AVG);
      float lo = (float)(et.range.x/mult);  // User units -> internal units
      float hi = (float)(et.range.y/mult);  // User units -> internal units
      boolean found = false;
      boolean in = false;
      LinkedList<Integer> lli = new LinkedList<Integer>();
      for (i = 0 ; i < d.area.length ; i++) {
        if (Float.isNaN(d.quantity[i])) continue;
        if (!found) { found = true; in = d.quantity[i]>lo && d.quantity[i]<=hi; }
        else {
          found = d.quantity[i]>lo && d.quantity[i]<=hi;
          if (found && !in) lli.add( i-1 );
          in = found;
          found = true;
        }
      }
      int[] events = new int[lli.size()];
      k = 0;
      for (int h : lli) events[k++] = h;
      k = 0;
      for (Reversal r : pieces) if (r.backwards) k++;
      Reversal[] revs = new Reversal[k];
      k = 0;
      for (Reversal r : pieces) if (r.backwards) revs[k++] = r;
      k = 0;
      Vec2F xy = new Vec2F();
      Vec2F uv = new Vec2F();
      for (i = 0 ; i < events.length ; i++) {
        while (k<revs.length && revs[k].index1() < events[i]) k++;
        if (k>=revs.length ||
            (revs[k].time0() - d.t(events[i]) > dt &&
             (!(k+1<revs.length && revs[k+1].time0() - revs[k].time1() < idt)))) {
          did_something = true;
          save(d,events[i],0,0,xy.eq(0,0),uv.eq(0,0));
        }
        else if (d.t(revs[k].index0()) < d.t(events[i])) {
          did_something = true;
          save(d,events[i],-revs[k].traveled,-revs[k].lasted(),revs[k].xy0(xy),revs[k].uv0(uv));
        }
        else {
          int kk = k;
          while ( (kk+1<revs.length && revs[kk+1].time0() - d.t(events[i]) < dt) ||
                  (kk+2<revs.length && revs[kk+2].time0() - revs[kk+1].time1() < idt) ) {
            kk++;
          }
          double distance = 0.0;
          double duration = 0.0;
          for (j = k ; j<=kk ; j++) {
            distance += revs[j].traveled;
            duration += revs[j].lasted();
          }
          did_something = true;
          save(d,events[i],distance,duration,revs[k].xy0(xy),revs[k].uv0(uv));
        }
      }
    }
    sort_and_write(out_f);
    
    if (pw != null && separate_files) {
      pw.close();
      pw = null;
    }
    
    return did_something ? 1 : 0;
  }
  
  public int quantifierCount() { return 0; }
  public void computeDancerQuantity(Dance d,int which) throws IllegalArgumentException {
    if (which>=0) throw new IllegalArgumentException("No registered dancer quantifiers in MeasureReversal.");
  }
  public String quantifierTitle(int which) { throw new IllegalArgumentException("No registered dancer quantifiers in MeasureReversal."); }
}
