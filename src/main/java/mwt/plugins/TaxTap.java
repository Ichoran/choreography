/* TaxTap.java - Extracts combined chemotaxis/tap habituation parameters
 * Copyright 2011 Howard Hughes Medical Institute and Rex Kerr
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

public class TaxTap implements CustomComputation
{
  Choreography chore = null;
  String postfix = "";
  boolean absolute = true;
  boolean radially = false;
  boolean collect = false;
  boolean noheader = false;
  Vec2F c = new Vec2F(0,0);
  Vec2F u = new Vec2F(0,0);
  Vec2F v = new Vec2F(0,0);
  float r0 = 0.0f;
  float r1 = 1e6f;
  float dt = 1.0f;
  float step = 1.0f/1.9f;
  float tiny = 0.03f;
  PrintWriter pw = null;
  EventSeek eseek = null;

  public TaxTap() { }

  public void printHelp() throws CustomHelpException {
  //System.out.println("12345678911234567892123456789312345678941234567895123456789612345678971234567898");
    System.out.println("Usage: --plugin TaxTap[::x,y][::option1][::option2]...");
    System.out.println("  x = center of plate along x axis in mm");
    System.out.println("  y = center of plate along y axis in mm");
    System.out.println("  All output will be relative to (x,y); default is (0,0)");
    System.out.println("Options: r0=radius r1=radius dt=time step=time postfix=string collect noheader");
    System.out.println("  r0 sets an inner radius (in mm); any events inside this radius are ignored");
    System.out.println("  r1 sets an outer radius (in mm); any events outside this radius are ignored");
    System.out.println("  dt sets a time window for what events count as stimulus-triggered; default=1s");
    System.out.println("  step is the interval over which turning is measured; default=1 movement cycle");
    System.out.println("    (one cycle is assumed to be 1/1.9 of a body length)");
    System.out.println("  postfix sets a string to add to the output filename before the extension .ctp");
    System.out.println("  collect instructs the plugin to put all output in a single file");
    System.out.println("  noheader instructs the plugin to omit file header (# datestamp prefix)");
    System.out.println("Output, one file per animal by default:");
    System.out.println("  One row per chunk of data, 15 columns:");
    System.out.println("    ID t0 t1 dist evn evdt x0 y0 x1 y1 u0 v0 u1 v1");
    System.out.println("  ID = worm ID number");
    System.out.println("  t0, t1 = time of initiation and end of movement");
    System.out.println("    This is guaranteed to be the entire movement for backwards movements");
    System.out.println("    but forward movements will be cut up into pieces of ~step in size");
    System.out.println("  dist = distance traveled, signed (- = backwards); zero indicates missing data");
    System.out.println("  evn = number of previous event (start of trace = 0)");
    System.out.println("  evdt = time to previous event (negative for reversals interrupted by event!)");
    System.out.println("  x0, y0, x1, y1 = position (relative to x,y if given) at start/end of movement");
    System.out.println("  u0, v0, u1, v1 = direction of motion vectors at start/end of movement");
    throw new CustomHelpException();
  }

  public void seekHelp(String args[]) throws CustomHelpException {
    for (String s: args) if (s.equalsIgnoreCase("help") || s.equals("?")) printHelp();
  }

  // Called at the end of command-line parsing, before any data has been read
  public void initialize(String args[],Choreography chore) throws IllegalArgumentException,IOException,CustomHelpException {
    this.chore = chore;
    seekHelp(args);

    String[] requirements = {"Reoutline::exp","Respine"/*,"Eigenspine::3"*/};
    String[] available = requirements.clone();
    chore.requirePlugins(available);
    /*for (String s : available[2].split("::")) {
      if (s.matches("\\d+")) {
        int idx = Integer.parseInt(s);
        if (idx<3) throw new IllegalArgumentException("At least three eigenvectors must be found");
      }
    }*/
    
    for (String a : args) {
      if (a.toLowerCase().startsWith("postfix=")) postfix = a.substring(8);
      else if (a.toLowerCase().startsWith("r0=")) {
        try { r0 = Float.parseFloat(a.substring(3))/chore.mm_per_pixel; radially = true; }
        catch (NumberFormatException nfe) { throw new IllegalArgumentException("Malformed radius: "+a.substring(3)); }
      }
      else if (a.toLowerCase().startsWith("r1=")) {
        try { r1 = Float.parseFloat(a.substring(3))/chore.mm_per_pixel; radially = true; }
        catch (NumberFormatException nfe) { throw new IllegalArgumentException("Malformed radius: "+a.substring(3)); }
      }
      else if (a.toLowerCase().startsWith("dt=")) {
        try { dt = Float.parseFloat(a.substring(3)); }
        catch (NumberFormatException nfe) { throw new IllegalArgumentException("Malformed time: "+a.substring(3)); }
      }
      else if (a.toLowerCase().startsWith("step=")) {
        try { step = Float.parseFloat(a.substring(5))/1.9f; }
        catch (NumberFormatException nfe) { throw new IllegalArgumentException("Malformed time: "+a.substring(3)); }
      }
      else if (a.toLowerCase().equals("collect")) collect = true;
      else if (a.toLowerCase().equals("noheader")) noheader = true;
      else {
        try { String[] xy = a.split(","); c.eq(Float.parseFloat(xy[0]), Float.parseFloat(xy[1])).eqDivide(chore.mm_per_pixel); absolute = false; }
        catch (NumberFormatException nfe) { throw new IllegalArgumentException("Malformed x,y coordinate pair: "+a); }
        catch (ArrayIndexOutOfBoundsException aioobe) { throw new IllegalArgumentException("Coordinates must be separated by comma (no space)."); }
      }
    }
    if (r0 < 0 || r1 < 0) throw new IllegalArgumentException("Radii must not be negative");
    if (dt <= 0) throw new IllegalArgumentException("Event triggering window must be positive");
    if (r1 < r0) throw new IllegalArgumentException("Outer radius must not be smaller than inner radius");
    if (radially && absolute) throw new IllegalArgumentException("Must set center coordinate for radii to make sense");
  }

  // Called before any method taking a File as an output target--this sets the extension
  public String desiredExtension() { return "ctp"; }

  String filenameFix(String fn) {
    if (postfix==null || postfix.length()==0) return (fn.endsWith("."+desiredExtension())) ? fn : fn+"."+desiredExtension();
    else if (!fn.endsWith("."+desiredExtension())) return fn+"."+postfix+"."+desiredExtension();
    else return fn.substring(0,fn.length()-(1+desiredExtension().length()))+"."+postfix+"."+desiredExtension();
  }

  // Called on freshly-read objects to test them for validity (after the normal checks are done).
  public boolean validateDancer(Dance d) { return true; }

  public static final float moddotshift = (float)(2+Math.sqrt(2));
  public static final float moddotmax = (float)(moddotshift+Math.sqrt(2));
  float getStraightness(Eigenspine.EigenSpine ees) {
    Vec2F u = new Vec2F();
    Vec2F v = new Vec2F();
    Vec2F w = new Vec2F();
    float minE = 1.0f;
    for (int i=1; i+1<ees.size(); i++) {
      float myE = 1.0f;
      for (int j=i-1; j>=0 && 2*i-j<ees.size(); j--) {
        ees.get(i,v);
        ees.get(j,u);
        ees.get(2*i-j,w);
        u.eqMinus(v);
        v.eqMinus(w);
        float nrg = Math.min(moddotmax, u.unitDot(v)+moddotshift)/moddotmax;
        myE *= nrg;
      }
      if (myE < minE) minE = myE;
    }
    return minE;
  }

   class BiasSeek {
    DirectionSet bias;
    int i0,i1 = -1;
    private int i = -1;
    int current = 0;
    boolean findNaN = false;
    private byte hased = 0;
    public BiasSeek(DirectionSet ds) {
      bias = ds;
    }
    void mimic(BiasSeek bs) {
      if (bias == bs.bias) {
        i0 = bs.i0;
        i1 = bs.i1;
        current = bs.current;
        findNaN = bs.findNaN;
      }
    }
    boolean hasNext() {
      i = i1+1;
      while (i < bias.length) {
        if (bias.isMoving(i) || (findNaN && !bias.isStill(i))) break;
        i += 1;
      }
      hased = 1;
      return (i < bias.length);
    }
    void toNext() {
      if (hased!=1) hasNext();
      if (i < bias.length) {
        i0 = i;
        current = bias.rawGet(i);
        i1 = i0;
        while (i1+1 < bias.length && bias.rawGet(i1+1)==current) i1 += 1;
      }
      hased = 0;
    }
    boolean hasPrev() {
      i = i0-1;
      while (i >= 0) {
        if (bias.isMoving(i) ||(findNaN && !bias.isStill(i))) break;
        i -= 1;
      }
      hased = -1;
      return (i >= 0);
    }
    void toPrev() {
      if (hased!=-1) hasPrev();
      if (i >= 0) {
        i1 = i;
        current = bias.rawGet(i);
        i0 = i1;
        while (i0 > 0 && bias.rawGet(i0-1)==current) i0 -= 1;
      }
      hased = 0;
    }
    int dir() { return current; }
    int size() { return 1 + (i1-i0); }
  }

  // Handle events--start of recording and any stimuli
  class Eventful {
    byte what;
    int index;
    int id;
    float time;
    public Eventful(int i, float t, int id0) {
      what = 0;
      index = i;
      id = id0;
      time = t;
    }
    public int clipIdx(Moveish m) { return Math.max(0, Math.min(m.d.area.length-1, index-m.d.first_frame)); }
  }
  Eventful[] getEvents() {
    ArrayList<Eventful> events = new ArrayList<Eventful>();
    events.add(new Eventful(0,chore.times[0],0));
    for (int i=0; i<chore.events.length; i++) {
      if (chore.events[i] == null) continue;
      Eventful e = new Eventful(i, chore.times[i], events.size());
      for (int j : chore.events[i]) e.what |= 1<<(j-1);
      events.add(e);
    }
    return events.toArray(new Eventful[events.size()]);
  }
  class EventSeek {
    public Eventful[] events;
    public EventSeek(Eventful[] e) { events = e; }
    Eventful find(float t) {
      int i0 = 0;
      int i1 = events.length-1;
      while (i1-i0 > 1) {
        int i = (i1+i0)/2;
        if (t < events[i].time) i1 = i;
        else i0 = i;
      }
      if (t >= events[i1].time) return events[i1];
      else return events[i0];
    }
    Eventful find(float t, float dt) {
      Eventful e = find(t);
      if (e.id+1 < events.length && events[e.id+1].time <= t+dt) return events[e.id+1];
      else return e;
    }
  }

  class Moveish {
    public byte what;
    public int i0;
    public int i1;
    public int id;
    public Dance d;
    public float t0;
    public float t1;
    public Moveish(int w, int i00, int i10, int id0, Dance d0) {
      what = (byte)Math.signum(w);
      i0 = i00;
      i1 = i10;
      id = id0;
      d = d0;
      t0 = d.t(i0);
      t1 = d.t(i1);
    }
    public Moveish reid(int id0) { id = id0; return this; }

    public boolean okay() {
      if (absolute || !radially) return true;
      float rSqA = d.centroid[i0].dist2(c);
      float rSqB = d.centroid[i1].dist2(c);
      return (r0*r0 <= rSqA && rSqA <= r1*r1 && r0*r0 <= rSqB && rSqB <= r1*r1);
    }
    public int id() { return d.ID; }
    public int dir() { return (okay()) ? what : 0; }
    public float t0() { return t0; }
    public float t1() { return t1; }
    public float x0() { if (absolute) return d.centroid[i0].x; else return d.centroid[i0].x - c.x; }
    public float y0() { if (absolute) return d.centroid[i0].y; else return d.centroid[i0].y - c.y; }
    public float x1() { if (absolute) return d.centroid[i1].x; else return d.centroid[i1].x - c.x; }
    public float y1() { if (absolute) return d.centroid[i1].y; else return d.centroid[i1].y - c.y; }
    public Vec2F v0(Vec2F vv) { d.getSegmentedDirection(i0,vv); vv.eqNorm(); return vv; }
    public Vec2F v1(Vec2F vv) { d.getSegmentedDirection(i1,vv); vv.eqNorm(); return vv; }
    public float dist(float[] path) { return Math.abs(path[i1]-path[i0]); }
    public int nev(EventSeek ev) { return ev.find(t0, t1-t0).id; }
    public float dtev(EventSeek ev) { return t0 - ev.find(t0, t1-t0).time; }

    public String output(float[] path, EventSeek ev) {
      float k = chore.mm_per_pixel;
      v0(u);
      v1(v);
      return String.format(
        "%d %.3f %.3f %.3f %d %.3f %.3f %.3f %.3f %.3f %.4f %.4f %.4f %.4f",
        id(),t0(),t1(),k*dist(path)*dir(),nev(ev),dtev(ev),k*x0(),k*y0(),k*x1(),k*y1(),u.x,u.y,v.x,v.y
      );
    }
  }
  Moveish[] getMoves(Dance d, float[] path) {
    ArrayList<Moveish> moves = new ArrayList<Moveish>();
    BiasSeek seek = new BiasSeek(d.directions);
    seek.findNaN = true;
    BiasSeek explore = new BiasSeek(d.directions);
    explore.findNaN = true;
    while (seek.hasNext()) {
      seek.toNext();
      int i1 = seek.i1;
      float wrong = 0f;
      explore.mimic(seek);
      while (explore.hasNext()) {
        explore.toNext();
        if (seek.current!=explore.current) {
          if (seek.current==0x40 || explore.current==0x40) break;
          wrong += Math.abs(path[explore.i1]-path[explore.i0]);
          if (wrong > tiny*d.body_length.average) break;
        }
        else if (seek.current==-1) {
          if (d.dt(i1, explore.i0) > dt) break;
        }
        i1 = explore.i1;
      }
      moves.add( new Moveish((seek.current==(byte)0x40) ? 0 : seek.current, seek.i0, i1, moves.size(), d) );
      while (i1 > seek.i1) seek.toNext();
    }
    return moves.toArray(new Moveish[moves.size()]);
  }
  class MoveSeek {
    public Moveish[] moves;
    public MoveSeek(Moveish[] m) { moves = m; }
    Moveish find(float t) {
      int i0 = 0;
      int i1 = moves.length-1;
      while (i1-i0 > 1) {
        int i = (i1+i0)/2;
        if (t < moves[i].t0) i1 = i;
        else i0 = i;
      }
      if (t >= moves[i1].t0) return moves[i1];
      else return moves[i0];
    }
  }

  MoveSeek refineMoves(MoveSeek sought, EventSeek ev, float[] path) {
    ArrayList<Moveish> all = new ArrayList<Moveish>();
    for (Moveish m : sought.moves) {
      float C = (float)m.d.body_length.average;
      Eventful e = ev.find(m.t1);
      if (ev.find(m.t0) == e) all.add(m.reid(all.size()));
      else if (m.what<=0) all.add(m.reid(all.size()));
      else {
        if (Math.abs(path[e.clipIdx(m)]-path[m.i0]) > tiny*C) {
          m.i1 = e.clipIdx(m)-1; m.t1 = m.d.t(m.i1); all.add(m.reid(all.size()));
        }
        if (m.t1 - e.time > dt) all.add(new Moveish(m.what, e.clipIdx(m), m.i1, all.size(), m.d));
      }
    }
    sought = new MoveSeek(all.toArray(new Moveish[all.size()]));
    all.clear();
    for (Moveish m : sought.moves) {
      float C = (float)m.d.body_length.average;
      float L = Math.abs(path[m.i1]-path[m.i0]);
      if (m.what <= 0) all.add(m.reid(all.size()));
      else if (L < step*C) all.add(m.reid(all.size()));
      else {
        int n = (int)Math.floor(L/(step*C));
        float extra = L - n*step*C;
        if (extra > C-(1+n)*tiny*C) { n += 1; extra = 0; }
        else if (extra < (1+n)*tiny*C) { extra = 0; }
        if (extra==0) {
          int i=m.i0;
          int j=i-1;
          int k=0;
          while (j<m.i1) {
            j = i;
            k++;
            while (i<m.i1 && Math.abs(path[i]-path[m.i0])<k*(L/n)) i++;
            if (Math.abs(path[i]-path[j])>tiny*C) all.add(new Moveish(m.what, j, i, all.size(), m.d));
            //System.out.printf("%d %d %d %d %d %.3f %d %.3f %.3f\n",m.d.ID,i,j,k,m.i1,L,n,extra,Math.abs(path[i]-path[m.i0]));
          }
        }
        else if ((n%2)==1) {
          int i0 = m.i0;
          while (i0<m.i1 && Math.abs(path[i0]-path[m.i0])<C*extra/2) i0++;
          if (Math.abs(path[i0]-path[m.i0])>tiny*C) all.add(new Moveish(m.what, m.i0, i0, all.size(), m.d));
          int i = i0;
          int j = i;
          int k = 0;
          while (k<n) {
            j = i;
            k++;
            while (i<m.i1 && Math.abs(path[i]-path[i0])<k*step*C) i++;
            if (Math.abs(path[i]-path[j])>tiny*C) all.add(new Moveish(m.what, j, i, all.size(), m.d));
          }
          if (Math.abs(path[m.i1]-path[i])>tiny*C) all.add(new Moveish(m.what, i, m.i1, all.size(), m.d));
        }
        else {
          int i = m.i0;
          int j = i;
          int k = 0;
          while (k<n/2) {
            j = i;
            k++;
            while (i<m.i1 && Math.abs(path[i]-path[m.i0])<k*step*C) i++;
            if (Math.abs(path[i]-path[j])>tiny*C) all.add(new Moveish(m.what, j, i, all.size(), m.d));
          }
          j = i;
          while (i<m.i1 && Math.abs(path[i]-path[m.i0])<(k*step+extra)*C) i++;
          if (Math.abs(path[i]-path[j])>tiny*C) all.add(new Moveish(m.what, j, i, all.size(), m.d));
          while (k>0) {
            j = i;
            k--;
            while (i<m.i1 && Math.abs(path[m.i1]-path[i])>k*step*C) i++;
            if (Math.abs(path[i]-path[j])>tiny*C) all.add(new Moveish(m.what, j, i, all.size(), m.d));
          }
        }
      }
    }
    return new MoveSeek(all.toArray(new Moveish[all.size()]));
  }

  // Called before any regular output is produced.  Returns true if it actually created the file.  If opened, file wont' be closed.
  public int computeAll(File out_f) throws IOException {
    eseek = new EventSeek( getEvents() );
    if (collect) {
      pw = new PrintWriter(new File(out_f.getParentFile(), filenameFix(out_f.getName())));
      if (!noheader) pw.printf("# %s %s\n", chore.file_prefix, chore.targetDir().getName(), chore.file_prefix);
      return 1;
    }
    else return 0;
  }

  // Also called before any regular output is produced (right after computeAll).  Returns true if it created a file.
  public int computeDancerSpecial(Dance d, File out_f) throws IOException {
    float min_travel = chore.minTravelPx(d);
    if (d.directions==null) d.quantityIsBias(chore.times,chore.speed_window,min_travel,false);
    d.quantityIsPath(chore.times, chore.speed_window, min_travel);
    float[] path = Arrays.copyOf(d.quantity, d.quantity.length);

    Moveish[] moves = getMoves(d, path);
    MoveSeek seek = refineMoves(new MoveSeek(moves), eseek, path);

    int i;
    for (i=0; i<seek.moves.length; i++) if (seek.moves[i].okay()) break;
    if (i >= seek.moves.length) return 0;

    PrintWriter p = (pw == null) ? (new PrintWriter(new File(out_f.getParentFile(), filenameFix(out_f.getName())))) : pw;
    if (pw==null && !noheader) p.printf("# %s %s\n", chore.file_prefix, chore.targetDir().getName(), chore.file_prefix);
    for (Moveish m : seek.moves) p.println(m.output(path, eseek));

    if (pw==null) p.close(); else p.flush();
    return (pw==null) ? 1 : 0;
  }

  // Called when the C output option is given to figure out how many custom quantifications (output types) this plugin provides.
  public int quantifierCount() { return 0; }

  // This is called whenever the plugin is required to handle a custom quantification.
  public void computeDancerQuantity(Dance d,int which) throws IllegalArgumentException { throw new IllegalArgumentException("TaxTap computes no graphed quantities"); }

  // This is called when a custom quantification is graphed to provide a title for it.
  public String quantifierTitle(int which) throws IllegalArgumentException { throw new IllegalArgumentException("TaxTap produces no graphed quantities"); }
};

