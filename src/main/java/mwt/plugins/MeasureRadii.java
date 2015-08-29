/* MeasureRadii.java - Measures curvature in spines
 * Copyright 2010 Howard Hughes Medical Institute and Rex Kerr
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

public class MeasureRadii implements CustomComputation
{
  Choreography chore;
  int N = 2;
  int M = 5;
  HashMap<Dance, Radiic[]> library = new HashMap<Dance, Radiic[]>();
  boolean internal = false;

  public class Radiic {
    short[] i0;
    short[] i1;
    float[] x;
    float[] y;
    float[] R;

    public Radiic(int N) {
      i0 = new short[N];
      i1 = new short[N];
      x = new float[N];
      y = new float[N];
      R = new float[N];
    }
  }


  public MeasureRadii() { }

  public void printHelp() throws CustomHelpException {
   //                  012345678911234567892123456789312345678941234567895123456789612345678971234567898
    System.out.println("WARNING: This plugin is undocumented, incomplete, and probably doesn't work.");
    System.out.println("Usage: --plugin MeasureRadii[::n][::min=m][::internal]");
    System.out.println("  MeasureRadii finds and quantifies the tightest radii of curvature in spines.");
    System.out.println("  n specifies how many radii to search for (default 2)");
    System.out.println("  min=m specifies the smallest number of spine points to include (default 5)");
    System.out.println("  internal prevents the radii from being written to a .radii file");
    System.out.println("Output will by default end up in a .radii file for each object.");
    System.out.println("  The first column is time.  Thereafter, each radius is given by five columns:");
    System.out.println("    i0 i1 x y R");
    System.out.println("  which specify the first and last spine point fit by a circle (inclusive),");
    System.out.println("  the center (relative to animal centroid) in mm, and the radius, in mm.");
    System.out.println("  If no decent fit was found, i0 and i1 will be -1 and x, y, and R will be 0");
  }

  // Called at the end of command-line parsing, before any data has been read
  public void initialize(String args[],Choreography chore) throws IllegalArgumentException,IOException,CustomHelpException {
    this.chore = chore;
    for (String arg: args) {
      if (arg.toLowerCase().equals("help")) { printHelp(); throw new CustomHelpException(); }
      else if (arg.matches("\\d+")) {
        try { N = Integer.parseInt(arg); }
        catch (NumberFormatException nfe) { N = -1; }
        if (N<=0) throw new IllegalArgumentException("Number of radii must be a positive integer");
      }
      else if (arg.toLowerCase().startsWith("min=")) {
        try { M = Integer.parseInt(arg.substring(4)); }
        catch (NumberFormatException nfe) { M = -1; }
        if (M<=3) throw new IllegalArgumentException("Minimum number of points must be an integer no less than 3");
      }
      else if (arg.toLowerCase().equals("internal")) {
        internal = true;
      }
      else throw new IllegalArgumentException("Bad argument for MeasureRadii: '"+arg+"'");
    }
    String[] req = { "Respine","SpinesForward" };
    chore.requirePlugins(req);
  }
  
  // Called before any method taking a File as an output target--this sets the extension
  public String desiredExtension() { return "radii"; }
  
  // Called on freshly-read objects to test them for validity (after the normal checks are done).
  public boolean validateDancer(Dance d) { return true; }
  
  // Called before any regular output is produced.  Returns true if it actually created the file.
  public int computeAll(File out_f) throws IOException { return 0; }
  
  // Also called before any regular output is produced (right after computeAll).  Returns true if it created a file.
  public int computeDancerSpecial(Dance d,File out_f) throws IOException {
    if (d.spine==null) return 0;
    Fitter[] fits = new Fitter[N];
    Radiic[] rads = new Radiic[N];
    Vec2F v = new Vec2F();
    int sp = 0;
    for (Spine s : d.spine) { if (s!=null) sp = Math.max(sp,s.size()); }
    Radiic trial = new Radiic(sp);
    for (int i=0 ; i<N ; i++) {
      fits[i] = new Fitter();
      rads[i] = new Radiic(d.spine.length);
    }
    for (int n=0; n<d.spine.length; n++) {
      Spine s = d.spine[n];
      if (s==null) continue;
      fits[0].reset();
      int i = 0;
      for (; i<M ; i++) {
        s.get(i,v);
        fits[0].addC(v.x,v.y);
      }
      while (true) {
        fits[0].circ.fit();
        trial.x[i-M] = (float)fits[0].circ.params.x0;
        trial.y[i-M] = (float)fits[0].circ.params.y0;
        trial.R[i-M] = (float)fits[0].circ.params.R;
        s.get(i-M,v);
        fits[0].subC(v.x,v.y);
        i++;
        if (i>=s.size()) break;
        s.get(i,v);
        fits[0].addC(v.x,v.y);
      }
      int k = 0;
      for (int j = k+1; j+M<s.size(); j++) { if (Math.abs(trial.R[j]) < Math.abs(trial.R[k])) k = j; }
      rads[0].x[n] = trial.x[k];
      rads[0].y[n] = trial.y[k];
      rads[0].R[n] = 1.0f/trial.R[k];
    }
    library.put(d,rads);
    return 0;
  }
  
  // Called when the C output option is given to figure out how many custom quantifications (output types) this plugin provides. 
  public int quantifierCount() { return 1; }
  
  // This is called whenever the plugin is required to handle a custom quantification.
  public void computeDancerQuantity(Dance d,int which) throws IllegalArgumentException {
    Radiic rad = library.get(d)[0];
    for (int i=0; i<d.quantity.length; i++) d.quantity[i] = rad.R[i];
  }
  
  // This is called when a custom quantification is graphed to provide a title for it.
  public String quantifierTitle(int which) throws IllegalArgumentException {
    if (which>=0 && which < N) return String.format("Radius of curvature %d (mm)",which);
    throw new IllegalArgumentException("Asked for radius of curvature quantification that doesn't exist.");
  }
};

