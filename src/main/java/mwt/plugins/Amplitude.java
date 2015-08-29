/* Amplitude.java - Measures approximate amplitude of body bends in worms.
 * Copyright 2013 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015 Calico Life Sciences LLC (author Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt.plugins;

import java.io.*;
import mwt.*;
import mwt.numerics.*;

public class Amplitude implements CustomComputation {
  public Choreography chore;

  public void initialize(String args[],Choreography chore) throws IllegalArgumentException,IOException,CustomHelpException {
    this.chore = chore;
  }

  public String desiredExtension() { return "none"; }

  public boolean validateDancer(Dance d) { return (d.spine != null); }

  public int computeAll(File out_f) throws IOException { return 0; }

  public int computeDancerSpecial(Dance d,File out_f) throws IOException { return 0; }

  // Called when the C output option is given to figure out how many custom quantifications (output types) this plugin provides.
  public int quantifierCount() { return 1; }

  // This is called whenever the plugin is required to handle a custom quantification.
  public void computeDancerQuantity(Dance d,int which) throws IllegalArgumentException {
    d.allUnload();
    if (d.segmentation==null) d.findSegmentation();
    if (d.spine==null || d.segmentation==null) { d.prepareForData(true); return; }

    Vec2F v = Vec2F.zero();
    Vec2F dir = Vec2F.zero();
    Vec2F pt = Vec2F.zero();
    Fitter fit = new Fitter();
    for (int i = 0; i < d.quantity.length; i ++) {
      int k = d.indexToSegment(i);
      if (d.segmentation[k]==null || d.spine[i]==null || d.centroid[i]==null) { d.quantity[i] = Float.NaN; continue; }
      d.getSegmentedDirection(i, dir).eqNorm();
      int n = d.spine[i].size();
      fit.resetAt(d.centroid[i].x, d.centroid[i].y);
      for (int j = 0; j < n; j++) {
        d.spine[i].get(j,pt);
        fit.addL(pt.x, pt.y);
      }
      fit.line.fit();
      float Dm = 0f;
      for (int j = 0; j < n; j++) {
        d.spine[i].get(j,pt);
        float dx = (float)Math.abs(fit.line.perpendicularCoord(pt.x, pt.y));
        Dm = Math.max(Dm, dx);
      }
      /*
      int c = n/2;
      int h = Math.min(c, Math.max(1,c/2));
      float Dm = 0f;
      for (int j = c-h; j <= c+h; j++) {
        d.spine[i].get(j, pt);
        float l = pt.dot(dir);
        float D = (float)Math.sqrt( Math.max(0, pt.length2() - l*l));
        Dm = Math.max(Dm, D);
      }
      */
      d.quantity[i] = Dm*chore.mm_per_pixel;
    }
  }
  
  // This is called when a custom quantification is graphed to provide a title for it.
  public String quantifierTitle(int which) throws IllegalArgumentException { return "bendAmpl"; }
};