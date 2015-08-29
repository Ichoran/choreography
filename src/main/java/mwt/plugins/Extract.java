/* Extract.java - Plugin for Choreography to write spines & outlines to file
 * Copyright 2010 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015 Calico Life Sciences LLC (author Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt.plugins;

import java.io.*;

import mwt.*;
import mwt.numerics.*;

public class Extract implements CustomComputation {
  public Choreography chore;
  public boolean extract_spine;
  public boolean extract_outline;
  public boolean extract_path;
  
  public Extract() {
    chore = null;
  }
  
  public void initialize(String args[],Choreography chore0) throws IllegalArgumentException,CustomHelpException {
    chore = chore0;
    for (String s : args) {
      if (s.equalsIgnoreCase("spine")) extract_spine = true;
      else if (s.equalsIgnoreCase("outline")) extract_outline = true;
      else if (s.equalsIgnoreCase("path")) extract_path = true;
      else if (s.equalsIgnoreCase("help")) {
        System.out.println("Usage: --plugin Extract[::spine][::outline]");
        System.out.println("  spine points are in global coordinates and have extension .spine");
        System.out.println("    Columns: time x0 y0 x1 y1 x2 y2 ... xn yn");
        System.out.println("  outline points are in global coordinates and have extension .outline");
        System.out.println("    Columns: time x0 y0 x1 y1 x2 y2 ... xn yn");
        throw new CustomHelpException();
      }
      else throw new IllegalArgumentException("Don't know how to extract " + s);
    }
  }
  
  public String desiredExtension() { return ""; }
  
  public boolean validateDancer(Dance d) { return true; }
  
  public int computeAll(File out_f) { return 0; }
  
  public boolean extractSpine(Dance d,File out_f) throws IOException {
    PrintWriter pw = null;
    if (d.spine==null) throw new IOException("Object "+d.ID+" has no spine to extract.");
    Vec2F v = Vec2F.zero();
    for (int i = 0 ; i < d.spine.length ; i++) {
      if (d.spine[i]==null) continue;
      pw = Choreography.nfprintf(pw,out_f,"%.3f",d.t(i));
      for (int j = 0 ; j < d.spine[i].size() ; j++) {
        d.spine[i].get(j,v);
        pw = Choreography.nfprintf(
          pw,out_f," %.3f %.3f",
          (v.x+d.centroid[i].x)*chore.mm_per_pixel,
          (v.y+d.centroid[i].y)*chore.mm_per_pixel
        );
      }
      pw = Choreography.nfprintf(pw,out_f,"\n");
    }
    if (pw!=null) pw.close();
    return (pw!=null);
  }
  public boolean extractOutline(Dance d,File out_f) throws IOException {
    PrintWriter pw = null;
    Vec2S pts[] = null;
    if (d.outline==null) throw new IOException("Object "+d.ID+" has no outline to extract.");
    for (int i = 0 ; i < d.outline.length ; i++) {
      if (d.outline[i]==null) continue;
      pw = Choreography.nfprintf(pw,out_f,"%.3f",d.t(i));
      pts = d.outline[i].unpack(pts);
      for (int j = 0 ; j < d.outline[i].size() ; j++) {
        pw = Choreography.nfprintf(
          pw,out_f," %.3f %.3f",
          pts[j].x*chore.mm_per_pixel,
          pts[j].y*chore.mm_per_pixel
        );
      }
      pw = Choreography.nfprintf(pw,out_f,"\n");
    }
    if (pw!=null) pw.close();
    return (pw!=null);
  }
  public boolean extractPath(Dance d,File out_f) throws IOException {
    throw new IOException("Path extraction is not yet implemented.");
  }
  public int computeDancerSpecial(Dance d,File out_f) throws IOException {
    boolean wrote = false;
    if (extract_spine) wrote |= extractSpine(d,new File(out_f.getAbsolutePath()+"spine"));
    if (extract_outline) wrote |= extractOutline(d,new File(out_f.getAbsolutePath()+"outline"));
    if (extract_path) wrote |= extractPath(d,new File(out_f.getAbsolutePath()+"path"));
    return wrote ? 1 : 0;
  }
  
  public int quantifierCount() { return 0; }
  public void computeDancerQuantity(Dance d,int which) { }
  public String quantifierTitle(int which) throws IllegalArgumentException { throw new IllegalArgumentException("Extract defines no quantifiers."); }
}

