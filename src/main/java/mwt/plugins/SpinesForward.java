/* SpinesForward.java - Flips spines so the head is forwards when possible
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

public class SpinesForward implements CustomComputation
{
  Choreography chore = null;
  boolean rebias = false;

  public SpinesForward() { }

  // Called at the end of command-line parsing, before any data has been read
  public void initialize(String args[],Choreography chore) throws IllegalArgumentException,IOException,CustomHelpException {
    this.chore = chore;
    for (String arg : args) {
      if (arg.toLowerCase().equals("help")) {
        //                  012345678911234567892123456789312345678941234567895123456789612345678971234567898
        System.out.println("Usage: --plugin SpinesForward[::rebias]");
        System.out.println("  SpinesForward aligns the first element of the spine with the head.");
        System.out.println("  The 'rebias' option will then fix the bias measures to agree with the");
        System.out.println("    head/tail decision made by SpinesForward.  This is generally useful for");
        System.out.println("    inflexible objects that can move sideways, whereas the original bias is");
        System.out.println("    usually superior for long objects that slide along a track.");
        throw new CustomHelpException();
      }
      else if (arg.toLowerCase().equals("rebias")) rebias = true;
      else throw new IllegalArgumentException("Invalid argument to SpinesForward: "+arg);
    }
  }
  
  // Called before any method taking a File as an output target--this sets the extension
  public String desiredExtension() { return ""; }
  
  // Called on freshly-read objects to test them for validity (after the normal checks are done).
  public boolean validateDancer(Dance d) { return true; }
  
  // Called before any regular output is produced.  Returns true if it actually created the file.
  public int computeAll(File out_f) throws IOException {
    Vec2F u = new Vec2F();
    Vec2F v = new Vec2F();
    Vec2F w = new Vec2F();
    boolean rebiased = false;
    for (Dance d : chore.dances) {
      if (d==null) continue;
      if (d.spine==null) continue;
      if (d.segmentation==null) d.findSegmentation();
      d.findPostureConfusion();
      boolean[] confusion = new boolean[d.quantity.length];
      for (int i=0; i<confusion.length; i++) confusion[i] = d.quantity[i]>0;
      d.findDirectionBiasSegmented(chore.minTravelPx(d));
      int i = 0;
      int j = 0;
      for (; i<d.spine.length; i=j) {
        j = i;
        while (j<d.spine.length && (Float.isNaN(d.quantity[j]) || confusion[j])) j++;
        int k0 = j;
        while (j<d.spine.length && !(Float.isNaN(d.quantity[j]) || confusion[j])) j++;
        int k1 = j;
        while (j<d.spine.length && (Float.isNaN(d.quantity[j]) || confusion[j])) j++;
        if (j>k1+1 && j<d.spine.length) {
          float maxerr = 0.0f;
          int mei = k1;
          for (int k=k1;k<j-1;k++) {
            float derr = 0.0f;
            if (d.spine[k]==null || d.spine[k+1]==null) continue;
            for (int l=0; l<d.spine[k].size(); l++) {
              derr += d.spine[k].get(l,u).dist2(d.spine[k].get(l,v));
            }
            if (derr>maxerr) { maxerr = derr; mei = k; }
          }
          j = mei+1;
        }
        float o = 0.0f;
        for (int k=k0; k<k1; k++) {
          if (d.spine[k]==null) continue;
          d.spine[k].get(0,u).eqMinus(d.spine[k].get(d.spine[k].size()-1, v));
          d.getSegmentedDirection(k,w);
          if (u.dot(w)!=0.0f) o += u.unitDot(w)*d.quantity[k];
        }
        if (o<0) {
          for (int k=i;k<j;k++) if (d.spine[k]!=null) d.spine[k].flip();
        }
        for (int k=i; k<j; k++) {
          if (!confusion[k] && !Float.isNaN(d.quantity[k]) && d.spine[k]!=null) d.spine[k].headfirstKnown(true);
        }
      }

      if (rebias) {
        d.allUnload();  // Make sure nobody believes old bias values
        DirectionSet ds = new DirectionSet(d.area.length);
        for (i=0; i<d.area.length; i++) {
          if (d.spine[i] != null && d.spine[i].oriented()) {
            int c0 = d.spine[i].size()/3;
            int c1 = d.spine[i].size()-(c0+1);
            if (c1<=c0) { c0--; c1++; }
            d.spine[i].get(c0,u).eqMinus(d.spine[i].get(c1, v));
            d.getSegmentedDirection(i,w);
            float x = u.dot(w);
            if (x>0) ds.set(i, DirectionSet.FWD);
            else if (x<0) ds.set(i, DirectionSet.BKW);
            else ds.set(i, DirectionSet.STOP);
          }
          else ds.set(i, DirectionSet.INVALID);
        }
        d.directions = ds;
        rebiased = true;
      }
    }

    return 2;
  }
  
  // Also called before any regular output is produced (right after computeAll).  Returns true if it created a file.
  public int computeDancerSpecial(Dance d,File out_f) throws IOException { return 0; }
  
  // Called when the C output option is given to figure out how many custom quantifications (output types) this plugin provides. 
  public int quantifierCount() { return 0; }
  
  // This is called whenever the plugin is required to handle a custom quantification.
  public void computeDancerQuantity(Dance d,int which) throws IllegalArgumentException {
    throw new IllegalArgumentException("SpinesForward quantifies nothing.");
  }
  
  // This is called when a custom quantification is graphed to provide a title for it.
  public String quantifierTitle(int which) throws IllegalArgumentException {
    throw new IllegalArgumentException("SpinesForward quantifies nothing.");
  }
};

