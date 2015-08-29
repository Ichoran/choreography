/* MeasureOmega.java - Measures parameters relevant to omega turns
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

public class MeasureOmega implements CustomComputation
{
  Choreography chore = null;
  int eig3idx = -1;
  int eigthetaidx = -1;
  PrintWriter omega_output = null;
  byte[] currentbias = null;
  Dance currentdance = null;
  float omegathreshold = (float)Math.PI;
  float pc1jitter = 0.0f;
  float pc2jitter = 0.0f;
  HashMap< MeasureReversal.Reversal, ReversalBends > bendsinrev = new HashMap< MeasureReversal.Reversal, ReversalBends >();

  public class OmegaEntry {
    Dance who;
    float when0;
    float when1;
    int[] idxlabel;
    int[] open;
    float[] maxcumangle;
    int[] closed;
    float prereversaltime;
    float prereversalphase;
    Vec2F beforebearing;
    Vec2F afterbearing;

    public OmegaEntry() {}
  }

  public class ReversalBends {
    float bends;
    int omega_after;
    int omega_last;
    MeasureReversal.Reversal reversal;
    public ReversalBends(float b, int oa, int ol, MeasureReversal.Reversal r) { bends = b; omega_after = oa; omega_last = ol; reversal = r; }
  }
  public ReversalBends countBends(float[] phases, float[] omegic, MeasureReversal.Reversal r) {
    int i0 = r.index0();
    int i1 = r.index1();
    for (int i=i0; i<=i1 ; i++) if (Float.isNaN(phases[i])) return null;
    int j,k;
    for (j=i1; j>=i0 && j+30>=i1; j--) if (omegic[j]>0) {
      k=j;
      while (k<omegic.length && omegic[k]>0) k++;
      return new ReversalBends(phases[i1]-phases[i0],j-i1,k-i1,r);
    }
    for (j=i1; j<omegic.length && j-90<=i1; j++) if (omegic[j]>0) {
      k=j;
      while (k<omegic.length && omegic[k]>0) k++;
      return new ReversalBends(phases[i1]-phases[i0],j-i1,k-i1,r);
    }
    return null;
  }

  public MeasureOmega() { }

  public void printHelp() throws CustomHelpException {
   //                  012345678911234567892123456789312345678941234567895123456789612345678971234567898
    System.out.println("WARNING: This plugin is undocumented, unsupported, and doesn't do what it says.");
    System.out.println("  It will be revised or removed in a subsequent release.");
    System.out.println("Usage: --plugin MeasureOmega");
    System.out.println("  MeasureOmega finds and quantifies strongly curved postures.");
    System.out.println("  The second custom output marks omega postures--try using that for now?");
    System.out.println("  This requires eigenvectors--if you have good ones, pass them in to Eigenspine");
  }

  // Called at the end of command-line parsing, before any data has been read
  public void initialize(String args[],Choreography chore) throws IllegalArgumentException,IOException,CustomHelpException {
   this.chore = chore;
    if (args!=null && args.length>0) for (String arg : args) {
      if (arg.equalsIgnoreCase("help")) { printHelp(); throw new CustomHelpException(); }
    }
    String[] requirements = {"Reoutline::exp","Respine","Eigenspine::3","MeasureReversal"};
    String[] available = requirements.clone();
    chore.requirePlugins(available);
    for (String s : available[2].split("::")) {
      if (s.matches("\\d+")) {
        int idx = Integer.parseInt(s);
        if (idx>=3) {
          eig3idx = 2;
          eigthetaidx = idx+1;
        }
      }
    }
    if (eig3idx<0 || eigthetaidx<0) throw new IllegalArgumentException("MeasureOmega requires Eigenspine with at least three components");
  }
  
  // Called before any method taking a File as an output target--this sets the extension
  public String desiredExtension() { return "omega"; }
  
  // Called on freshly-read objects to test them for validity (after the normal checks are done).
  public boolean validateDancer(Dance d) { return true; }
  
  // Called before any regular output is produced.  Returns true if it actually created the file.
  public int computeAll(File out_f) throws IOException {
    omega_output = new PrintWriter(out_f);
    return 1;
  }

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

  byte[] getBias(Dance d) {
    if (d==currentdance) return currentbias;
    currentdance = d;
    d.findDirectionBiasSegmented(chore.minTravelPx(d));
    byte[] bias = new byte[d.quantity.length];
    for (int i=0; i < d.quantity.length; i++) {
      if (Float.isNaN(d.quantity[i])) bias[i] = (byte)0x40;
      else if (d.quantity[i]>0.9) bias[i] = 1;
      else if (d.quantity[i]<-0.9) bias[i] = -1;
      else bias[i] = 0;
    }
    currentdance = d;
    currentbias = bias;
    return bias;
  }

  boolean[] getOmegaShapes(Dance d, float[] bendies, float[] eig3) {
    boolean[] omegic = new boolean[bendies.length];
    for (int i=0; i<bendies.length; i++) {
      omegic[i] = (bendies[i] > omegathreshold);
    }
    return omegic;
  }

  void computeAllForReal() throws IOException {
    try {
      Eigenspine ei = null;
      for (Choreography.ComputationInfo ci : chore.plugininfo) {
        if (ci.plugin.getClass().getName().equals("Eigenspine")) { ei = (Eigenspine)ci.plugin; break; }
      }
      if (ei != null) {
        int n = 0;
        for (Dance d : chore.dances) {
          if (d==null) continue;
          n++;
          ei.computeDancerQuantity(d,0);
          pc1jitter += d.estimateNoise();
          ei.computeDancerQuantity(d,1);
          pc2jitter += d.estimateNoise();
        }
        pc1jitter *= Statistic.invnormcdf_tail(0.05f)/n;
        pc2jitter *= Statistic.invnormcdf_tail(0.05f)/n;
      }
      for (Dance d : chore.dances) {
        if (d==null) continue;
        d.allUnload();
        byte[] bias = getBias(d);
        computeDancerQuantity(d,0);
        float[] bendies = Arrays.copyOf(d.quantity, d.quantity.length);
      }
    }
    finally {
      omega_output.close();
      omega_output = null;
    }
  }
  
  // Also called before any regular output is produced (right after computeAll).  Returns true if it created a file.
  public int computeDancerSpecial(Dance d,File out_f) throws IOException {
    if (omega_output==null) return 0;
    computeAllForReal();
    return 0;
  }
  
  // Called when the C output option is given to figure out how many custom quantifications (output types) this plugin provides. 
  public int quantifierCount() { return 3; }
  
  // This is called whenever the plugin is required to handle a custom quantification.
  public void computeDancerQuantity(Dance d,int which) throws IllegalArgumentException {
    if (which<0 || which>=quantifierCount()) throw new IllegalArgumentException("Invalid index into custom outputs.");
    float[] confusion = null;
    Eigenspine ei = null;
    if (which>0) {
      float midlength = d.meanBodyLengthEstimate();  // Needed for printout only
      d.findPostureConfusion();
      confusion = Arrays.copyOf(d.quantity,d.quantity.length);
      for (Choreography.ComputationInfo ci : chore.plugininfo) {
        if (ci.plugin.getClass().getName().equals("Eigenspine")) { ei = (Eigenspine)ci.plugin; break; }
      }
      if (which==1) {
        ei.computeDancerQuantity(d,2);
      }
      else {
        ei.computeDancerQuantity(d,0);
        float[] pc1 = Arrays.copyOf(d.quantity, d.quantity.length);
        ei.computeDancerQuantity(d,1);
        float[] pc2 = Arrays.copyOf(d.quantity, d.quantity.length);
        ei.computeDancerQuantity(d,ei.desired+1);
        for (int i=0 ; i<d.quantity.length ; i++) {
          if (confusion[i] > 0 || (Math.abs(pc1[i]) < pc1jitter && Math.abs(pc2[i]) < pc2jitter)) d.quantity[i] = Float.NaN;
        }
        float theta = Float.NaN;
        float last = Float.NaN;
        for (int i=0; i<d.quantity.length; i++) {
          if (Float.isNaN(d.quantity[i])) { theta = last = Float.NaN; }
          else if(Float.isNaN(theta)) {
            theta = 0.0f;
            last = d.quantity[i];
            d.quantity[i] = theta;
          }
          else {
            float delta = d.quantity[i] - last;
            while (delta > Math.PI) delta -= (float)(2*Math.PI);
            while (delta < -Math.PI) delta += (float)(2*Math.PI);
            theta += delta;
            last = d.quantity[i];
            d.quantity[i] = theta;
          }
        }
        MeasureReversal mr = null;
        for (Choreography.ComputationInfo ci : chore.plugininfo) {
          if (ci.plugin.getClass().getName().equals("MeasureReversal")) mr = (MeasureReversal)ci.plugin;
        }
        if (mr==null) return;
        float[] cuml = Arrays.copyOf(d.quantity,d.quantity.length);
        computeDancerQuantity(d,1);
        // Fill in holes less than 1 sec long
        for (int i=0 ; i<d.quantity.length ; i++) {
          int j = i;
          while (j<d.quantity.length && !(d.quantity[j]>0)) j++;
          int k = j+1;
          while (k<d.quantity.length && d.quantity[k]>0) k++;
          int l = k+1;
          while (l<d.quantity.length && !(d.quantity[l]>0)) l++;
          if (l<d.quantity.length && d.dt(k,l) < 1.0) {
            for (int a=k; a<l; a++) d.quantity[a] = Math.max(d.quantity[k-1],d.quantity[l]);
          }
          i=l-1;
        }
        for (MeasureReversal.Reversal r : mr.lookup.get(d)) {
          ReversalBends rb = countBends(cuml,d.quantity,r);
          if (rb == null || !r.backwards) continue;
          if (rb.omega_after > 0 && d.centroid[rb.reversal.index1()].dist(d.centroid[rb.reversal.index1()+rb.omega_after]) > midlength/2) continue;
          bendsinrev.put(r, rb);
          System.out.printf("%d %d %d %.3f %.3f %.3f %d %d %.3f\n",d.ID,r.index0(),r.index1(),r.traveled * (r.backwards ? -1 : 1),rb.bends,midlength,rb.omega_after,rb.omega_last,d.t((rb.omega_after+rb.omega_last)/2+r.index1()));
        }
      }
    }
    else if (d.quantity == null || d.quantity.length != d.area.length) d.quantity = new float[d.area.length];
    for (int i=0 ; i<d.quantity.length ; i++) {
      if (d.spine[i] instanceof Eigenspine.EigenSpine) {
        float straight = getStraightness((Eigenspine.EigenSpine)d.spine[i]);
        if (which==1) {
          if (confusion[i]>0.5f) d.quantity[i] = 1.5f;
          else {
            float a = (float)Math.pow(0.3,((d.spine[i].size()-1)/2)*0.2);
            float e3 = Math.abs(d.quantity[i]);
            if (straight > a) d.quantity[i] = 0.0f;
            else if (e3*0.2f + straight/a < 1) d.quantity[i] = 0.0f;
            else if (4*straight > a*e3) d.quantity[i] = 0.0f;
            else d.quantity[i] =1.0f;
          }
        }
        else d.quantity[i] = straight;
      }
      else d.quantity[i] = Float.NaN;
    }
  }

  // This is called when a custom quantification is graphed to provide a title for it.
  public String quantifierTitle(int which) throws IllegalArgumentException {
    switch(which) {
      case 0: return "Straightness";
      case 1: return "In Omega";
      case 2: return "Body Bends";
      default: throw new IllegalArgumentException("Invalid index into custom outputs");
    }
  }
};

