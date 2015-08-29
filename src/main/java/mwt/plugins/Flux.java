/* Flux.java - Plugin for Choreography that detects animals crossing boundaries
 * Copyright 2010, 2011 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015 Calico Life Sciences LLC (author Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt.plugins;

import java.util.*;
import java.io.*;
import java.awt.image.*;

import mwt.*;
import mwt.numerics.*;

public class Flux implements CustomOutputModification {
  abstract class Shape {
    boolean inside_out;
    Vec2F c;
    Vec2F r;
    abstract boolean check(Vec2F p);
    boolean contains(Vec2F p) { return inside_out ^ check(p); }
    int crosses(Vec2F p1, Vec2F p0) {
      return (contains(p1) ? 1 : 0) - (contains(p0) ? 1 : 0);
    }
    Vec2F positiveRadius(Vec2F r0) {
      if (r0.x < 1e-6f) r0.x = 1e-6f;
      if (r0.y < 1e-6f) r0.y = 1e-6f;
      return r0;
    }
  }
  class Ellip extends Shape {
    private Vec2F v = Vec2F.zero();
    Ellip(Vec2F c0, Vec2F r0, boolean inin) {
      inside_out = !inin;
      c = c0.copy();
      r = positiveRadius(r0.copy());
    }
    boolean check(Vec2F p) {
      v.eq(p).eqMinus(c).eqDivide(r);
      return (v.length2() < 1.0f);
    }
  }
  class Rect extends Shape {
    Rect(Vec2F c0, Vec2F r0, boolean inin) {
      inside_out = !inin;
      c = c0.copy();
      r = positiveRadius(r0.copy());
    }
    boolean check(Vec2F p) {
      return (c.x - r.x < p.x && p.x < c.x + r.x && c.y - r.y < p.y && p.y < c.y + r.y);
    }
  }
  Shape parseShape(String s) throws IllegalArgumentException {
    String ss[] = s.split(",");
    if (ss.length != 5) throw new IllegalArgumentException("Bad shape format.  Expect (+/-)E,x,y,a,b or (+/-)R,x,y,a,b; got "+s);
    boolean rect;
    boolean inin;
    if (ss[0].equalsIgnoreCase("+e") || ss[0].equalsIgnoreCase("e")) { rect = false; inin = true; }
    else if (ss[0].equalsIgnoreCase("-e")) { rect = false; inin = false; }
    else if (ss[0].equalsIgnoreCase("+r") || ss[0].equalsIgnoreCase("r")) { rect = true; inin = true; }
    else if (ss[0].equalsIgnoreCase("-r")) { rect = true; inin = false; }
    else throw new IllegalArgumentException("Shape must be one of E, +E, -E, R, +R, or -R, not "+ss[0]);
    Vec2F c = null;
    Vec2F r = null;
    try {
      c = new Vec2F( (float)chore.parsePhysicalPx(ss[1]).getPx(null) , (float)chore.parsePhysicalPx(ss[2]).getPx(null) );
      r = new Vec2F( (float)chore.parsePhysicalPx(ss[3]).getPx(null) , (float)chore.parsePhysicalPx(ss[4]).getPx(null) );
    }
    catch (NumberFormatException nfe) { }
    if (c==null || r==null) throw new IllegalArgumentException("Numeric format error on coordinates.");
    if (rect) return new Rect(c,r,inin);
    else return new Ellip(c,r,inin);
  }
  boolean inside(Vec2F p, ArrayList<Shape> als) {
    for (Shape s : als) if (!s.contains(p)) return false;
    return true;
  }
  boolean inside_one(Vec2F p, ArrayList< ArrayList<Shape> > als_als) {
    for (ArrayList<Shape> als : als_als) if (inside(p,als)) return true;
    return false;
  }
  int crosses(ArrayList< ArrayList<Shape> > als_als, Vec2F p1, Vec2F p0) {
    return (inside_one(p1,als_als) ? 1 : 0) - (inside_one(p0,als_als) ? 1 : 0);
  }

  class Event implements Comparable<Event> {
    float t;
    int sign;
    int id;
    Vec2F where;
    Event(float t0, int s0, int id0, Vec2F w0) {
      t = t0; sign = s0; id = id0; where = w0.copy();
    }

    public int compareTo(Event e) {
      if (t < e.t) return -1;
      if (t > e.t) return 1;
      if (id < e.id) return -1;
      if (id > e.id) return 1;
      if (sign > e.sign) return -1;
      if (sign < e.sign) return 1;
      return 0;
    }
  }

  Choreography chore;
  ArrayList< ArrayList<Shape> > shapes;
  String postfix;
  boolean flux;
  boolean report;
  boolean gate;

  public Flux() {
    chore = null;
    shapes = new ArrayList< ArrayList<Shape> >();
    postfix = "";
    flux = true;
    report = false;
  }

  void printHelp() {
    //                  012345678911234567892123456789312345678941234567895123456789612345678971234567898
    System.out.println("Usage: --plugin Flux::{+|-}{E|R},x,y,a,b[::{count|flux}]");
    System.out.println("                    [::gate][::report][::postfix=tag]]");
    System.out.println("  Flux can report when objects are in regions or cross boundaries");
    System.out.println("  Regions can be either elliptical or rectangular (E or R) pieces");
    System.out.println("  + means that the worm must be inside this region; - means must be outside");
    System.out.println("  Coordinates are x,y,a,b where a and b are radii for x and y");
    System.out.println("    Units can be specified on any value you wish (default is px if you do not");
    System.out.println("    specify).  Example: in +E,1100,703,2mm,2mm the values 1100 & 703 are pixels.");
    System.out.println("    Valid units are px, um, mm, cm.");
    System.out.println("  If multiple regions are given:");
    System.out.println("    The object must be inside everything with + and nothing with -");
    System.out.println("    If E or R is specified without a + or -, this starts of a new group;");
    System.out.println("    that shape counts as +, and everything thereafter is part of that group.");
    System.out.println("    The object must be within at least one group.");
    System.out.println("  Flags govern how the regions are used for custom output; flux is the default:");
    System.out.println("    count outputs 0 when an object is out of a region, 1 when in");
    System.out.println("    flux outputs 1 when an object passes into a region, -1 when it passes out,");
    System.out.println("      0 at all other times");
    System.out.println("  A single custom output is available, displaying either flux or count");
    System.out.println("  gate causes all output from Choreography to be clipped by the region");
    System.out.println("     (values outside the region will return NaN) _except_ for output");
    System.out.println("     from this plugin or those that perform similar modifications");
    System.out.println("  report outputs all events of interest sorted by time.  The format is:");
    System.out.println("      time flux object-id x-pos y-pos");
    System.out.println("    flux is +1 for entry, -1 for exit, +2 if born inside, -2 if dies inside");
    System.out.println("    positions are specified in physical units (mm), not pixels");
    System.out.println("  postfix=tag adds a specific tag to the report output (will be .tag.flux) and");
    System.out.println("    labels the custom output with tag as the name of the region");
    System.out.println("  The plugin may be called multiple times to get multiple outputs, but gating");
    System.out.println("    multiple times will apply all gates simultaneously.");
  }

  public void initialize(String args[],Choreography chore) throws CustomHelpException,IllegalArgumentException {
    this.chore = chore;
    if (args==null || args.length==0) { printHelp(); throw new CustomHelpException(); }
    for (String arg : args) {
      if (arg.equalsIgnoreCase("count")) flux = false;
      else if (arg.equalsIgnoreCase("flux")) flux = true;
      else if (arg.equalsIgnoreCase("gate")) gate = true;
      else if (arg.equalsIgnoreCase("report")) report = true;
      else if (arg.startsWith("postfix=")) postfix = arg.substring(8);
      else if (arg.equalsIgnoreCase("help")) { printHelp(); throw new CustomHelpException(); }
      else {
        try { 
          Shape shape = parseShape(arg);
          if (!(arg.startsWith("+") || arg.startsWith("-")) || shapes.isEmpty()) {
            shapes.add( new ArrayList<Shape>() );
          }
          shapes.get(shapes.size()-1).add(shape);
        }
        catch (IllegalArgumentException iae) {
          System.out.println("Error in arguments: "+iae+"\n");
          printHelp();
          throw new CustomHelpException();
        }
      }
    }
  }

  public String desiredExtension() { return (postfix.equals("")) ? "flux" : (postfix+".flux"); }

  public boolean validateDancer(Dance d) { return true; }

  ArrayList<Event> findDancerEvents(Dance d) {
    Vec2F v = new Vec2F();
    ArrayList<Event> ale = new ArrayList<Event>();
    if ( inside_one(d.centroid[0],shapes) ) ale.add( new Event(d.t(0),2,d.ID,d.centroid[0]) );
    if (d.segmentation==null) {
      for (int i=1; i<d.centroid.length; i++) {
        int j = crosses(shapes,d.centroid[i],d.centroid[i-1]);
        v.eq(d.centroid[i]).eqPlus(d.centroid[i-1]).eqTimes(0.5f);
        if ( j!=0 ) ale.add( new Event(0.5f*(d.t(i)+d.t(i-1)),j,d.ID,v) );
      }
    }
    else {
      for (int i=0; i<d.segmentation.length; i++) {
        int i0=d.segmentation[i].i0;
        int i1=d.segmentation[i].i1;
        int n = (d.segmentation[i].endpoints==null) ? 1 : (d.segmentation[i].endpoints.length-1);
        for (int k=0; k<n; k++) {
          if (d.segmentation[i].endpoints!=null) {
            i0 = d.segmentation[i].endpoints[k];
            i1 = d.segmentation[i].endpoints[k+1];
          }
          int j = crosses(shapes,d.centroid[i1],d.centroid[i0]);
          if (j!=0) {
            float t = 0.5f*(d.t(i1) + d.t(i0));
            v.eq(d.centroid[i0]).eqPlus(d.centroid[i1]).eqTimes(0.5f);
            ale.add( new Event(t,j,d.ID,v) );
          }
        }
        if (i+1<d.segmentation.length && d.segmentation[i].i1 < d.segmentation[i+1].i0) {
          int j = crosses(shapes,d.centroid[d.segmentation[i+1].i0],d.centroid[d.segmentation[i].i1]);
          if (j!=0) {
            float t = 0.5f*(d.t(d.segmentation[i+1].i0) + d.t(d.segmentation[i].i1));
            v.eq(d.centroid[d.segmentation[i+1].i0]).eqPlus(d.centroid[d.segmentation[i].i1]).eqTimes(0.5f);
            ale.add( new Event(t,j,d.ID,v) );
          }
        }
      }
    }
    if ( inside_one(d.centroid[d.centroid.length-1],shapes) ) ale.add( new Event(Math.nextUp(d.t(d.centroid.length-1)),-2,d.ID,d.centroid[d.centroid.length-1]) );
    return ale;
  }

  public int computeAll(File f) throws IOException {
    if (!report) return 0;
    ArrayList<Event> master = new ArrayList<Event>();
    for (Dance d : chore.dances) {
      if (d==null) continue;
      master.addAll( findDancerEvents(d) );
    }
    PrintWriter pw = new PrintWriter(new FileOutputStream(f));
    Collections.sort(master);
    for (Event e : master) {
      pw.printf("%.3f %d %d %.3f %.3f\n",e.t,e.sign,e.id,e.where.x*chore.mm_per_pixel,e.where.y*chore.mm_per_pixel);
    }
    pw.close();
    return gate ? 3 : 1;
  }

  public int computeDancerSpecial(Dance d, File f) { return 0; }

  public void check(int which) throws IllegalArgumentException {
    if (which<0 || which>=quantifierCount()) throw new IllegalArgumentException("Flux plugin asked for quantity that it does not supply.");
  }

  public int quantifierCount() { return shapes.isEmpty() ? 0 : 1; }
  public void computeDancerQuantity(Dance d, int which) throws IllegalArgumentException {
    check(which);
    if (d.quantity==null || d.quantity.length != d.area.length) d.quantity = new float[d.area.length];
    ArrayList<Event> events = findDancerEvents(d);
    float value = 0.0f;
    int j=0;
    for (int i=0; i<d.area.length; i++) {
      float t = d.t(i);
      if (j<events.size() && events.get(j).t <= t) {
        Event e = events.get(j);
        j++;
        if (flux) {
          if (Math.abs(e.sign)==1) value = e.sign;
        }
        else value += Math.signum(e.sign);
      }
      d.quantity[i] = value;
      if (flux) value = 0.0f;
    }
  }
  public String quantifierTitle(int which) throws IllegalArgumentException {
    check(which);
    String pre;
    if (flux) return "Flux into " + (postfix.equals("") ? "region" : postfix);
    else return "Count in " + (postfix.equals("") ? "region" : postfix);
  }

  public boolean modifyQuantity(Dance d, Choreography.DataSource ds) {
    if (!gate) return false;
    if (d.quantity==null || d.quantity.length != d.area.length) return false;
    ArrayList<Event> events = findDancerEvents(d);
    boolean okay = false;
    int j=0;
    for (int i=0; i<d.quantity.length; i++) {
      float t = d.t(i);
      if (j<events.size() && events.get(j).t <= t) {
        Event e = events.get(j);
        j++;
        okay = (e.sign>0);
      }
      if (!okay) d.quantity[i] = Float.NaN;
    }
    return true;
  }
  public boolean modifyMapDisplay(Vec2D position, Vec2I dimensions, double pixelSize, BufferedImage buffer, double time, DataMapper dm) { return false; }
}
