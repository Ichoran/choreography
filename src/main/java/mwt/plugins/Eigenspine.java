/* Eigenspine.java - Plugin for Choreography to find Principal Components
 * Copyright 2010, 2011 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015 Calico Life Sciences LLC (author Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt.plugins;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

import mwt.*;
import mwt.numerics.*;

public class Eigenspine implements CustomComputation {
  public class EigenSpine extends Respine.FixedSpine {
    float theta;
    float[] angles;
    float[] pcs;
    public EigenSpine(Respine.FixedSpine rfs, float[] angles0) {
      theta = Float.NaN;
      quant = rfs.quant;
      x = rfs.x;
      y = rfs.y;
      w = rfs.w;
      angles = angles0;
      pcs = null;
    }
    void loadAngles(float[] angles1) {
      System.arraycopy(angles, 0, angles1, 0, angles.length);
    }
    float getPC(int n) {
      if (pcs==null) {
        pcs = new float[components.size()];
        for (int i=0; i<pcs.length; i++) {
          float[] pc = components.get(i);
          float f = 0.0f;
          for (int j=0; j<pc.length; j++) f += pc[j]*(angles[j]-mean[j])*idev[j];
          pcs[i] = f;
        }
      }
      return pcs[n];
    }
  }
  
  Choreography chore;
  boolean graphic = false;
  boolean give_data = false;
  int desired;
  float[] mean;
  float[] idev;
  ArrayList<float[]> components;
  float[] explained;
  HashMap<Integer,float[]> extras;
  ArrayList<File> sources;
  File external_pcs;
  Vec2F u = new Vec2F();
  Vec2F v = new Vec2F();
  Vec2F w = new Vec2F();
  Vec2F o = new Vec2F();
  int[] lrindex = null;
  float[] frac = null;
  float[] cudist = null;
  float[][] explicit = null;
  
  public Eigenspine() {
    chore = null;
    desired = 3;
    mean = null;
    idev = null;
    components = null;
    extras = new HashMap<Integer,float[]>();
    sources = new ArrayList<File>();
    external_pcs = null;
  }
  
  void printHelp() {
    //                  012345678911234567892123456789312345678941234567895123456789612345678971234567898
    System.out.println("Usage: --plugin Eigenspine[::n][::help][::graphic][::data]");
    System.out.println("                          [::extra=fname][::vector=fname]");
    System.out.println("  Eigenspine finds principle components of the spine of tracked objects.");
    System.out.println("  The spine is represented as the angles between each segment and the bearing.");
    System.out.println("  A file with the extension .eigen is created containing the eigenvalues");
    System.out.println("    The first column is the fraction of variance explained, components follow");
    System.out.println("    The first row is the mean, the second 1.0/(standard deviation), PC1 is next");
    System.out.println("    (Raw data should be transformed by (data - mean)/std, or (data-row1)*row2)");
    System.out.println("  n is the number of principal components to find (default 3, minimum 2).");
    System.out.println("  help prints this messsage");
    System.out.println("  graphic outputs a 63x63 image histogram of the first two components");
    System.out.println("    normalized by the standard deviation (1 sd = 10 pixels, max=white)");
    System.out.println("    color runs black->blue->yellow->red->white, normalized to max");
    System.out.println("  data outputs the raw angles used to compute the PCs into a file .eigen.data");
    System.out.println("  extra=fname uses the angles in the filename as extra data (may be repeated)");
    System.out.println("  vector=fname uses the vectors in the specified file instead of calculating");
    System.out.println("    If you use both vector and extra, the extra data will be used for graphics;");
    System.out.println("    otherwise, the extra data will only be used to compute the PCs.");
    System.out.println("    Instead of a filename, the vector can be supplied directly; separate");
    System.out.println("    components with , and vectors with ,,");
    System.out.println("  The plugin supplies one new output per component--the value of the component");
    System.out.println("    for an object at each timepoint, plus one extra for the residual error and");
    System.out.println("    one extra for the angle (polar coords) of the first two components.");
    System.out.println("  As a side effect, the spine will be flipped such that the head is first.");
  }

  public void initialize(String args[],Choreography chore) throws IllegalArgumentException,IOException,CustomHelpException{
    this.chore = chore;
    if (args!=null && args.length>0) for (String arg : args) {
      if (arg.equalsIgnoreCase("help")) { printHelp(); throw new CustomHelpException(); }
    }
    String[] requirements = {"Reoutline::exp","Respine","SpinesForward"};
    String[] available = requirements.clone();
    chore.requirePlugins(available);
    for (int i=0; i<available.length; i++) {
      if (available[i]==null || available[i].length()==0) {
        throw new IllegalArgumentException("Plugin Eigenspine requires but cannot find plugin "+requirements[i].split("::")[0]);
      }
    }

    if (args!=null && args.length>0) for (String arg : args) {
      if (arg.equalsIgnoreCase("graphic")) graphic = true;
      else if (arg.equalsIgnoreCase("data")) give_data = true;
      else if (arg.startsWith("extra=")) {
        File f = new File(arg.substring(6));
        if (!f.exists()) throw new IllegalArgumentException("Extra data file "+f.getPath()+" does not exist.");
        sources.add(f);
      }
      else if (arg.startsWith("vector=")) {
        try { parseAsVector(arg.substring(7)); }
        catch (NumberFormatException nfe) {
          explicit = null;
          external_pcs = new File(arg.substring(7));
          if (!external_pcs.exists()) throw new IllegalArgumentException("Principal component vector file "+external_pcs.getPath()+" does not exist.");
        }
      }
      else {
        try { desired = Integer.parseInt(args[0]); if (desired<2) desired = 2; }
        catch (NumberFormatException nfe) { printHelp(); throw new IllegalArgumentException("Mis-formatted number for number of principal components"); }
      }
    }
  }

  public void parseAsVector(String ss) throws NumberFormatException {
    String[] vecstrings = ss.split(",,");
    explicit = new float[vecstrings.length][];
    for (int i=0 ; i<vecstrings.length ; i++) {
      String[] comps = vecstrings[i].split(",");
      explicit[i] = new float[comps.length];
      for (int j=0 ; j<comps.length ; j++) {
        explicit[i][j] = Float.parseFloat(comps[j]);
      }
    }
  }

  public String desiredExtension() { return "eigen"; }

  public boolean validateDancer(Dance d) { return true; }

  public static ArrayList<float[]> doNIPALS(float[] X, int dims, int npc, boolean loud, float[] explained) {
    int rows = X.length/dims;
    ArrayList<float[]> alf = new ArrayList<float[]>();
    double t[] = new double[dims];
    double told[] = new double[dims];
    float p[] = new float[rows];
    for (int i=0; i<rows; i++) for (int j=0; j<dims; j++) p[i] += X[i*dims+j]*X[i*dims+j];
    float dot[] = Arrays.copyOf(p,p.length);
    float proj;
    if (loud) System.out.print("finding ");
    for (int h=0; h<npc; h++) {
      if (loud) { System.out.print(h+"..."); System.out.flush(); }
      int imax = 0;
      double pmax = p[0];
      for (int i=1; i<rows; i++) { if (pmax<p[i]) { imax=i; pmax=p[i]; } }
      for (int j=0; j<dims; j++) t[j] = X[imax*dims+j];
      double ei = 0.0;
      for (int j=0; j<dims; j++) ei += t[j]*t[j];
      double dif = Float.POSITIVE_INFINITY;
      int nit = 0;
      while (dif/ei > 1e-12f && dif > 1e-12f*dims && nit<100) {
        nit++;
        double pmag = 0.0;
        for (int i=0; i<rows; i++) {
          p[i] = 0.0f;
          for (int j=0; j<dims; j++) p[i] += t[j]*X[i*dims+j];
          pmag += p[i]*p[i];
        }
        float pfix = (float)(1.0/Math.sqrt(pmag));
        if (!Float.isNaN(pfix)) for (int i=0; i<rows; i++) p[i] *= pfix;
        ei = 0.0f;
        dif = 0.0f;
        for (int j=0; j<dims; j++) {
          told[j] = t[j];
          t[j] = 0.0f;
          for (int i=0; i<rows; i++) t[j] += p[i]*X[i*dims+j];
          ei += t[j]*t[j];
          dif += (t[j]-told[j])*(t[j]-told[j]);
        }
      }
      ei = (1.0/Math.sqrt(ei));
      if (Double.isNaN(ei)) ei = 1.0f;
      alf.add(new float[t.length]);
      for (int j=0; j<dims; j++) alf.get(alf.size()-1)[j] = (float)(ei*t[j]);
      if (explained==null) for (int i=0; i<rows; i++) for (int j=0; j<dims; j++) X[i*dims+j] -= p[i]*t[j];
      else {
        explained[h]=0.0f;
        float[] pc = alf.get(alf.size()-1);
        for (int i=0; i<rows; i++) {
          proj = 0.0f;
          for (int j=0; j<dims; j++) proj += X[i*dims+j]*pc[j];
          for (int j=0; j<dims; j++) X[i*dims+j] -= p[i]*t[j];
          if (dot[i]>0.0f) explained[h] += proj*proj/dot[i];
        }
      }
      if (explained!=null) explained[h] /= rows;
    }
    return alf;
  }

  EigenSpine loadAngles(Spine s, float[] angles, Vec2F bearing) {
    // Cumulative length computation--make it a block to hide temporary variables
    {
      s.get(0,u);
      cudist[0] = 0.0f;
      for (int i=1; i<cudist.length; i++) { v.eq(u); cudist[i] = s.get(i,u).dist(v); }
      for (int i=1; i<cudist.length; i++) cudist[i] += cudist[i-1];
    }
    // Find points that we should actually choose (not spine points) to equally split length of animal
    {
      int i = 0;
      for (int j=0;j<lrindex.length;j++) {
        float target = (cudist[cudist.length-1]*(j+1))/angles.length;
        while (i<cudist.length-1 && target > cudist[i]) i++;
        lrindex[j] = i;
        frac[j] = Math.max(0.0f,Math.min(1.0f,(target-cudist[i-1])/(cudist[i]-cudist[i-1])));
      }
    }
    // Now do the computation
    {
      o.eq(bearing);
      s.get(0,u).eqMinus(s.get(s.size()-1,v));
      if (o.dot(u) < 0) o.eqTimes(-1.0f);
      s.get(0,v);
      for (int j=0;j<lrindex.length;j++) {
        s.get(lrindex[j],u).eqTimes(frac[j]).eqPlus( s.get(lrindex[j]-1,w).eqTimes(1.0f-frac[j]) );
        v.eqMinus(u);
        float vdo = v.unitDot(o);
        if (Float.isNaN(vdo)) angles[j] = 0.0f;
        else {
          if (vdo>1.0f) vdo = 1.0f;
          else if (vdo < -1.0f) vdo = -1.0f;
          angles[j] = (float)Math.acos(vdo);
          if (v.X(o)<0) angles[j] = -angles[j];
        }
        v.eq(u);
      }
      s.get(s.size()-1,u);
      v.eqMinus(u);
      float vdo = v.unitDot(o);
      if (Float.isNaN(vdo)) angles[lrindex.length] = 0.0f;
      else {
        if (vdo>1.0f) vdo = 1.0f;
        else if (vdo < -1.0f) vdo = -1.0f;
        angles[lrindex.length] = (float)Math.acos(vdo);
        if (v.X(o)<0) angles[lrindex.length] = -angles[lrindex.length];
      }
    }
    return new EigenSpine((Respine.FixedSpine)s,Arrays.copyOf(angles,angles.length));
  }

  float[] loadEigens(File base_fname) throws IOException {
    boolean loud = !chore.quiet_operation;
    Vec2F u = new Vec2F();
    Vec2F v = new Vec2F();
    Vec2F w = new Vec2F();

    // We don't enforce that all spines have the same number of points, so we have to find the most common number of points (sigh)
    HashMap<Integer,int[]> most_common_spine = new HashMap<Integer,int[]>();
    if (loud) { System.out.print("Eigenvectors: preparing..."); System.out.flush(); }
    for (Dance d : chore.dances) {
      if (d==null) continue;
      if (d.spine==null) continue;
      for (Spine s : d.spine) {
        if (s==null) continue;
        int[] count = most_common_spine.get(s.size());
        if (count==null) {
          count = new int[2];
          count[1] = s.size();
          most_common_spine.put(s.size(), count);
        }
        count[0]++;
      }
    }
    int maxcount = 0;
    int maxverts = 0;
    for (int[] ia : most_common_spine.values()) {
      if (maxcount < ia[0]) {
        maxcount = ia[0];
        maxverts = ia[1];
      }
    }
    int bends = maxverts-1;
    double[] mean = new double[bends];
    double[] dev = new double[bends];
    
    // loadAngles uses these
    lrindex = new int[bends-1];
    frac = new float[bends-1];
    cudist = new float[bends+1];

    int good_spine_count = 0;
    for (Dance d : chore.dances) {
      if (d==null) continue;
      if (d.spine==null) continue;
      d.findDirectionBiasSegmented(chore.minTravelPx(d));
      for (int i=0; i<d.spine.length; i++) {
        if (d.spine[i]!=null && !Float.isNaN(d.quantity[i])) good_spine_count++;
      }
    }

    // Then we need to compute the mass of data that we will use to compute the PCs and remove the means
    float angles[] = new float[bends];
    float X[] = new float[bends*good_spine_count];
    int n=0;
    for (Dance d : chore.dances) {
      if (d==null) continue;
      if (d.spine==null) continue;
      for (int i=0; i<d.spine.length; i++) {
        if (d.spine[i]==null) continue;
        d.spine[i] = loadAngles(d.spine[i],angles,d.bearing[i]);
        if (Float.isNaN(d.quantity[i])) continue;
        for (int j=0; j<bends; j++) X[n*bends+j] = angles[j];
        n++;
      }
    }
    if (give_data) {
      if (loud) { System.out.print("writing..."); System.out.flush(); }
      File data_name = new File(base_fname.getPath()+".data");
      PrintWriter pw = new PrintWriter(data_name);
      for (int i=0; i<good_spine_count; i++) {
        for (int j=0; j<bends; j++) { pw.printf("%.4f ",X[i*bends+j]); } pw.println();
      }
      pw.close();
    }
    if (sources.size()>0) {
      if (loud) { System.out.print("reading..."); System.out.flush(); }
      float[][] blocks = new float[sources.size()+1][];
      blocks[0] = X;
      for (int i=0 ; i<sources.size(); i++) {
        ArrayList<float[]> data = new ArrayList<float[]>();
        BufferedReader br = new BufferedReader(new FileReader(sources.get(i)));
        String line;
        while ((line = br.readLine()) != null) {
          String[] bits = line.split(" ");
          if (bits.length != bends) { br.close(); throw new IOException("Expected "+bends+"-ary vector, found "+bits.length+" in "+line); }
          float[] sample = new float[bends];
          try { for (int j=0;j<bends;j++) sample[j] = Float.parseFloat(bits[j]); }
          catch (NumberFormatException nfe) { throw new IOException("Failed to parse floating point number in line "+line); }
          data.add(sample);
        }
        br.close();
        X = new float[data.size()*bends];
        for (int k=0;k<data.size();k++) {
          float[] sample = data.get(k);
          for (int j=0;j<bends;j++) X[k*bends+j] = sample[j];
        }
        blocks[i+1] = X;
      }
      int oldn = n;
      n = 0;
      for (int i=0;i<blocks.length; i++) n += blocks[i].length/bends;
      X = new float[n*bends];
      int h = 0;
      for (int i=0;i<blocks.length; i++) {
        System.arraycopy(blocks[i],0,X,h,blocks[i].length);
        h += blocks[i].length;
      }
    }
    for (int i=0; i<mean.length; i++) mean[i] = dev[i] = 0;
    for (int i=0; i<n; i++) {
      for (int j=0; j<bends; j++) { mean[j] += X[i*bends+j]; dev[j] += X[i*bends+j]*X[i*bends+j]; }
    }
    this.mean = new float[mean.length];
    this.idev = new float[dev.length];
    for (int i=0; i<angles.length; i++) {
      mean[i] = mean[i]/n;
      dev[i] = Math.sqrt(dev[i]/n - mean[i]*mean[i]);
      this.mean[i] = (float)mean[i];
      this.idev[i] = (dev[i] > 1e-6) ? (float)(1.0/dev[i]) : 1.0f;
    }
    for (int i=0; i<n; i++) {
      for (int j=0; j<bends; j++) X[i*bends+j] = (X[i*bends+j] - this.mean[j])*this.idev[j];
    }

    // Either use NIPALS (SVD would be faster, but we don't want to lug a SVD library around) to compute first few PCs, or load vectors from file
    explained = new float[bends];
    if (external_pcs==null && explicit==null) components = doNIPALS(X,bends,desired,loud,explained);
    else {
      components = new ArrayList<float[]>();
      if (external_pcs!=null) {
        if (loud) { System.out.print("loading..."); System.out.flush(); }
        BufferedReader br = new BufferedReader(new FileReader(external_pcs));
        br.readLine();  // Throw means away--we'll use our own
        br.readLine();  // Throw deviations away--we'll use our own
        String line;
        while ( (line=br.readLine())!=null && components.size()<desired ) {
          float[] pc = new float[bends];
          String bits[] = line.split(" ");
          if (bits.length < bends+1) throw new IOException("Not enough components in stored principal component vector!  Need "+bends+" but found "+(bits.length-1));
          try { for (int j=0; j<bends; j++) pc[j] = Float.parseFloat(bits[j+1]); }
          catch (NumberFormatException nfe) { throw new IOException("Could not read vector from line: "+line); }
          components.add(pc);
        }
        br.close();
      }
      else {
        for (int i=0; i<explicit.length; i++) {
          if (explicit[i].length != bends+1) throw new IOException("Explicit vector #"+i+" has "+explicit[i].length+" components but needs "+(bends+1));
          components.add(explicit[i]);
        }
      }
      if (components.size()<desired) throw new IOException("Ran out of lines to read; only got "+components.size()+" of "+desired+" principal component vectors.");
      for (int h=0; h<desired; h++) {
        float[] pc = components.get(h);
        double sum = 0.0f;
        for (int i=0; i<n; i++) {
          float dot = 0.0f;
          float proj = 0.0f;
          for (int j=0; j<bends; j++) {
            float f = X[i*bends+j];
            dot += f*f;
            proj += f*pc[j];
          }
          if (dot>0.0f) sum += proj*proj/dot;
        }
        explained[h] = (float)(sum/n);
      }
    }

    int agree = 0;
    // We're going to go through everyone to make sure our first PC is chosen such that positive angle = forward
    for (Dance d : chore.dances) {
      if (d==null) continue;
      float[] dir = Arrays.copyOf(d.quantity,d.quantity.length);
      computeDancerQuantity(d,desired+1);
      for (int i=1 ; i+1<d.quantity.length ; i++) {
        if (dir[i]>0.9) {
          if (d.quantity[i+1]-d.quantity[i-1] > 0) agree++;
          else if (d.quantity[i+1]-d.quantity[i-1] < 0) agree--;
        }
        else if (dir[i]<-0.9) {
          if (d.quantity[i+1]-d.quantity[i-1] < 0) agree++;
          else if (d.quantity[i+1]-d.quantity[i-1] > 0) agree--;
        }
      }
      d.allUnload();
    }
    if (agree < 0) {
      float[] pc1 = components.get(0);
      if (pc1 != null) for (int i=0; i<pc1.length; i++) { pc1[i] = -pc1[i]; }
      // Ugh, have to fix up all the backwards calculations now
      for (Dance d : chore.dances) {
        if (d==null) continue;
        float[] std = extras.get(d.ID);
        if (std != null) std[0] = -std[0];
        for (Spine s : d.spine) {
          if (s == null || !(s instanceof EigenSpine)) continue;
          EigenSpine es = (EigenSpine)s;
          es.theta = -es.theta;
          es.pcs[0] = -es.pcs[0];
        }
      }

    }

    if (loud) System.out.println("done.");
    if (external_pcs==null) return null; else return X;
  }

  int in8(double d) {
    int i =(int)Math.floor(d);
    if (i<0) return 0;
    if (i>255) return 255;
    return i;
  }

  public int computeAll(File out_f) throws IOException {
    float[] X = loadEigens(out_f);
    if (out_f==null) return 0;

    if (!graphic) X = null;

    PrintWriter pw = new PrintWriter(out_f);
    pw.print("0 "); for (float f : mean) { pw.printf("%e ",f); } pw.println();
    pw.print("0 "); for (float f : idev) { pw.printf("%e ",f); } pw.println();
    for (int i=0 ; i<components.size(); i++) {
      float[] ev = components.get(i);
      pw.printf("%.4f ",explained[i]); for (float f : ev) { pw.printf("%e ",f); } pw.println();
    }
    pw.close();

    if (graphic) {
      File png_f = new File(out_f.getPath() + ".png");
      double[][] hist = new double[63][];
      for (int i=0 ; i<63; i++) {
        hist[i] = new double[63];
        for (int j=0; j<63; j++) hist[i][j] = 0.0;
      }
      if (X!=null) {
        float[] pc0 = components.get(0);
        float[] pc1 = components.get(1);
        int cols = pc0.length;
        int rows = X.length/pc0.length;
        float E[] = new float[rows*2];
        float[] std = new float[4];
        for (int i=0; i<4 ; i++) std[i] = 0.0f;
        for (int i=0; i<rows; i++) {
          E[2*i] = E[2*i+1] = 0.0f;
          for (int j=0; j<cols ; j++) {
            E[2*i] += X[i*cols+j]*pc0[j];
            E[2*i+1] += X[i*cols+j]*pc1[j];
          }
          std[0] += E[2*i];
          std[1] += E[2*i]*E[2*i];
          std[2] += E[2*i+1];
          std[3] += E[2*i+1]*E[2*i+1];
        }
        std[0] /= rows; std[1] = (float)Math.sqrt(std[1]/rows - std[0]*std[0]);
        std[2] /= rows; std[3] = (float)Math.sqrt(std[3]/rows - std[2]*std[2]);
        for (int i=0; i<rows; i++) {
          int x = 31+(int)Math.round(10*(E[2*i]-std[0])/std[1]);
          int y = 31+(int)Math.round(10*(E[2*i+1]-std[2])/std[3]);
          if (x<0) x=0; else if (x>62) x=62;
          if (y<0) y=0; else if (y>62) y=62;
          hist[y][x] += 1.0;
        }
      }
      else for (Dance d : chore.dances) {
        if (d==null || d.spine==null) continue;
        // Note--normalization is already loaded because we checked angle in loadEigens
        //computeDancerQuantity(d,desired+1); // Angle--will load normalization as a side effect.
        //d.quantityIsSpeed(chore.times,0.5f,false,false);
        float[] std = extras.get(d.ID);
        for (int i=0; i<d.spine.length; i++) {
          if (d.spine[i]==null || !(d.spine[i] instanceof EigenSpine)) continue;
          EigenSpine es = (EigenSpine)d.spine[i];
          int x = 31+(int)Math.round(10*(es.getPC(0)-std[0])/std[1]);
          int y = 31+(int)Math.round(10*(es.getPC(1)-std[2])/std[3]);
          if (x<0) x=0; else if (x>62) x=62;
          if (y<0) y=0; else if (y>62) y=62;
          hist[y][x] += 1.0;
        }
      }
      double mh = 0.0;
      for (int i=0; i<63; i++) for (int j=0; j<63; j++) mh = Math.max(mh,hist[i][j]);
      if (mh>0) mh = 1.0/mh;
      for (int i=0; i<63; i++) for (int j=0; j<63; j++) hist[i][j] *= mh;
      BufferedImage bi = new BufferedImage(63,63,BufferedImage.TYPE_INT_RGB);
      for (int i=0; i<63; i++) for (int j=0; j<63; j++) {
        int rgb = in8(255.999*hist[i][j]);
        bi.setRGB(i,62-j,rgb+(rgb<<8)+(rgb<<16));
      }
      javax.imageio.ImageIO.write(bi, "PNG", png_f);
    }
    return 3;
  }

  public int computeDancerSpecial(Dance d,File out_f) throws IOException { return 0; }

  public int quantifierCount() { return desired+2; }
  public void computeDancerQuantity(Dance d,int which) throws IllegalArgumentException {
    if (which<0 || which>=quantifierCount()) throw new IllegalArgumentException("Invalid index into custom outputs.");
    if (components==null) throw new IllegalArgumentException("Somehow the eigenvalues are being used before they've been initialized?");
    if (which==desired) {
      float[] angles = new float[components.get(0).length];
      for (int i=0; i<d.spine.length; i++) {
        if (d.spine[i]==null) { d.quantity[i] = Float.NaN; continue; }
        EigenSpine es = (EigenSpine)d.spine[i];
        es.loadAngles(angles);
        for (int j=0; j<angles.length; j++) angles[j] = (angles[j]-mean[j])*idev[j];
        float osq = 0.0f;
        for (float a : angles) osq += a*a;
        for (int j=0; j<components.size(); j++) {
          float[] pc = components.get(j);
          float len = es.getPC(j);
          for (int k=0; k<angles.length; k++) angles[k] -= len*pc[k];
        }
        float nsq = 0.0f;
        for (float a : angles) nsq += a*a;
        d.quantity[i] = (float)Math.sqrt(nsq/Math.max(1e-6f,osq));
      }
    }
    else if (which==desired+1) {
      if (!extras.containsKey(d.ID)) {
        float[] angles = new float[components.get(0).length];
        float[] std = new float[4];
        int n = 0;
        computeDancerQuantity(d,0);
        Statistic s1 = new Statistic(d.quantity);
        computeDancerQuantity(d,1);
        Statistic s2 = new Statistic(d.quantity);
        std[0] = (float)s1.average;
        std[1] = (float)s1.deviation;
        std[2] = (float)s2.average;
        std[3] = (float)s2.deviation;
        extras.put(d.ID,std);
      }
      float[] std = extras.get(d.ID);
      if (d.quantity==null || d.quantity.length != d.area.length) d.quantity = new float[d.area.length];
      for (int i=0; i<d.quantity.length; i++) {
        if (d.spine[i]==null || !(d.spine[i] instanceof EigenSpine)) d.quantity[i] = Float.NaN;
        else {
          EigenSpine es = (EigenSpine)d.spine[i];
          if (Float.isNaN(es.theta)) {
            float x = (es.getPC(0) - std[0]) / std[1];
            float y= (es.getPC(1) - std[2]) / std[3];
            es.theta = (float)Math.atan2(y,x);
          }
          d.quantity[i] = es.theta;
        }
      }
    }
    else {
      float[] pc = components.get(which);
      float[] angles = new float[pc.length];
      if (d.quantity==null || d.quantity.length != d.area.length) d.quantity = new float[d.area.length];
      for (int i=0; i<d.spine.length; i++) {
        if (d.spine[i]==null) { d.quantity[i] = Float.NaN; continue; }
        d.quantity[i] = ((EigenSpine)d.spine[i]).getPC(which);
      }
    }
  }
  public String quantifierTitle(int which) throws IllegalArgumentException {
    if (which<0 || which>=quantifierCount()) throw new IllegalArgumentException("Invalid index into custom outputs.");
    if (which==desired) return "PC Residual";
    else if (which==desired+1) return "PC1,2 theta";
    else return "PC"+which;
  }

}
