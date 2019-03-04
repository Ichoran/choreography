/* Choreography.java - Main application file
 * Copyright 2010-2013 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015, 2018 Calico Life Sciences LLC (authored by Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */
 
package mwt;

import java.io.*;
import java.lang.reflect.*;
import java.nio.CharBuffer;
import java.util.*;
import java.util.zip.*;

import mwt.numerics.*;
import mwt.plugins.*;


/* This is the main application */
public class Choreography
{
  // Constants that the Kerr lab uses; they're as sensible for defaults as anything else would be.
  public static final float default_pixelsize = 0.0243f; // Was (54.72f+40.34f)/(2352+1726)==0.02331
  public static final float default_speed_window = 0.5f;
  public static final int DANCERS_PER_FILE = 1000;
  
  // These are set by parseInput and control what is produced and shown on screen
  boolean headless;
  public boolean quiet_operation;
  boolean write_timecourse;
  boolean tell_who;
  boolean interactive_mode;
  public boolean reject_duplicates;
  public boolean avoid_shadow;
  public boolean speed_over_length;
  boolean one_by_one;
  boolean view_graph;
  boolean view_datamap;
  boolean all_individuals;
  boolean static_trigger_mask;
  boolean blob_in_blobs;
  public boolean nanless = false;
  String print_header = null;
  public boolean segment_path;
  public String base_directory;
  
  // These restrict the analysis to a time range
  float select_t0;
  float select_t1;
  
  // These affect quantitative aspects of the processing
  public float mm_per_pixel;
  PhysicalLength min_move_mm;
  PhysicalLength min_move_bodylen;
  PhysicalLength min_move_directional;
  float min_time;
  public float speed_window;
  float output_time_chunk;
  
  // Times to trigger averaging
  public Triggerer triggers[];
  
  // Stuff for the plugin architecture
  PluginLoader plugloader;
  CustomComputation[] providedPlugins;   // For programmatic use--skip normal class-loading mechanism and use what we're given.
  public ArrayList<ComputationInfo> plugininfo;
  PlugMapper plug_map;
  LinkedList<CustomOutputModification> plugmods;
  LinkedList<CustomSegmentation> plugsegs;
  
  // Areas of the field of view from which to specifically include or exclude data
  public Dance.ReceptiveField attend[];
  public Dance.ReceptiveField shun[];
  
  // Various things relating to the mechanics of output
  public String file_prefix;  // findFiles may change this
  public String[] output_names;
  DataSpecifier[][] output_requests;
  HashSet<DataSource> computables;
  HashSet<Integer> id_table;
  
  
  // These are set by findFiles, and point to various relevant files
  File directory_file;
  File output_directory = null;
  File summary_file;
  File[] sitter_files;
  File[] dancer_files;
  File png_file;
  File[] png_set_files;
  ZipFile directory_zip;
  ZipEntry summary_zip;
  ZipEntry[] sitter_zips;
  ZipEntry[] dancer_zips;
  ZipEntry png_zip;
  ZipEntry[] png_zip_set;
  LinkedList<MultiFileInfo> dancer_multi_list;
  
  // These are set by loadData and contain the actual tracking data
  public FrameMap[] valid;
  public int[] frames;
  public float[] obid;
  public float[] times;
  public int[] numbers;
  public int[] indices;
  public int[][] events;
  public Dance[] refs;
  public Dance[] dances;
  HashMap<Integer,LinkedList<Ancestry>> geneology;  // Records how objects were created/destroyed
  Vector<LinkedList<Dance>> attendance;  // Lists which dancers were present at each timepoint
  HashSet<Integer> duplicate_frame_numbers;
  float[] trigger_start;
  float[] trigger_end;
  public Fitter global_position_noise;
  HashSet<DataSource> jittering_sources = new HashSet<DataSource>();
  
  // These are set by recomputeOnlineStatistics and are summary statistics for display and output
  int[] good_number;
  Statistic[] area;
  Statistic[] persistence;
  Statistic[] speed;
  Statistic[] angular_speed;
  Statistic[] length;
  Statistic[] rel_length;
  Statistic[] width;
  Statistic[] rel_width;
  Statistic[] aspect;
  Statistic[] rel_aspect;
  Statistic[] spine_length;
  Statistic[] spine_width;
  Statistic[] end_wiggle;
  Statistic[] bias;
  Statistic[] pathlen;
  Statistic[] curve;
  Statistic[] dir_change;
  Statistic[] loc_x;
  Statistic[] loc_y;
  Statistic[] vel_x;
  Statistic[] vel_y;
  Statistic[] orient;
  Statistic[] crab;
  Statistic[] qxfw;
  Statistic[][] custom;
  
  // Graphical output
  DataMapVisualizer dmv;

  float catchnan(Float f) { return (nanless && Float.isNaN(f)) ? 0.0f : f; }

  public static class SystemExit extends RuntimeException {
    public int exitValue;
    public SystemExit(int value) {
      exitValue = value;
    }
  }
  
  
  // Loader for plugins--very basic, just loads the requested file and sees if it implements the right interface
  public static class PluginLoader extends ClassLoader
  {
    HashMap<String,Class> load_list;
    
    public PluginLoader() { load_list = new HashMap<String,Class>(); }
    
    public synchronized Class loadClass(String classname,boolean resolve) throws ClassNotFoundException
    {
      Class loaded;
      try
      {
        loaded = super.findSystemClass(classname);
        if (loaded!=null) return loaded;
      }
      catch (ClassNotFoundException cnfe) { }
      
      if (load_list.containsKey(classname))
      {
        loaded = load_list.get(classname);
        if (loaded==null) throw new ClassNotFoundException("Cannot find plugin " + classname);
      }
      else
      {
        String filename = (classname.endsWith(".class")) ? classname : classname + ".class";
        File f = new File(filename);
        byte[] binary_class_data = null;
        try
        {
          FileInputStream fis = new FileInputStream(f);
          DataInputStream dis = new DataInputStream(fis);
          long true_file_length = f.length();
          int loadable_file_length = (true_file_length > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int)true_file_length;
          binary_class_data = new byte[loadable_file_length];
          dis.readFully(binary_class_data);
          dis.close();
        }
        catch (IOException ioe)
        {
          load_list.put(classname,null);
          throw new ClassNotFoundException("Could not read plugin from " + f + "\n  Error: " + ioe);
        }
        
        try
        {
          loaded = defineClass(classname,binary_class_data,0,binary_class_data.length);
          load_list.put(classname,loaded);
        }
        catch (ClassFormatError cfe)
        {
          load_list.put(classname,null);
          throw new ClassNotFoundException("Badly formatted plugin " + classname + "\n  " + cfe);
        }
      }
 
      if (!CustomComputation.class.isAssignableFrom(loaded))
      {
        throw new ClassNotFoundException("Required interface not implemented by plugin " + classname);
      }
      if (resolve) resolveClass(loaded);
      
      return loaded;
    }
    
    public synchronized void clearCache() { load_list.clear(); }
  }
  
  
  // The null plugin--only used to catch errors.
  public class NullPlugin implements CustomComputation {
    public NullPlugin() {}
    public void initialize(String args[],Choreography chore) throws IllegalArgumentException,IOException { throw new IllegalArgumentException("Missing plugin."); }
    public String desiredExtension() { return "Missing plugin."; }
    public boolean validateDancer(Dance d) { return (d!=null); }
    public int computeAll(File out_f) throws IOException { throw new IOException("Missing plugin."); }
    public int computeDancerSpecial(Dance d,File out_f) throws IOException { throw new IOException("Missing plugin."); }
    public int quantifierCount() { return 0; }
    public void computeDancerQuantity(Dance d,int which) throws IllegalArgumentException { throw new IllegalArgumentException("Missing plugin or more outputs asked than plugins will handle."); }
    public String quantifierTitle(int which) throws IllegalArgumentException { throw new IllegalArgumentException("Missing plugin or more outputs than plugins will handle."); }
  }
  
  
  // Handle mapping plugins to requests for custom output
  public class PlugMapper {
    public HashSet< DataMeasure > already;
    public ArrayList< DataSpecifier > out;
    public int plug_n;
    public int which_n;
    public PlugMapper() {
      plug_n = 0;
      which_n = -1;
      out = new ArrayList< DataSpecifier >();
    }
    public void registerOutput(DataSpecifier ds) throws IllegalArgumentException {
      out.add(ds);
      if (ds.extra == null)
      {
        if (plug_n < 0) throw new IllegalArgumentException("Custom outputs must either be referenced by plugin and number, or filled in in order; cannot mix the two.");

        // Different metrics on same output type are okay, except for NOP which will jump us forwards
        if (!already.isEmpty() && !already.contains(ds.measure) && !already.contains(DataMeasure.NOP)) {
          which_n--;
          already.add(ds.measure);
        }
        else {
          already.clear();
          already.add(ds.measure);
        }
        which_n += 1;
        if (plug_n >= plugininfo.size()) throw new IllegalArgumentException("Too many custom outputs");
        if (!plugininfo.get(plug_n).mapoutput || plugininfo.get(plug_n).plugin.quantifierCount() >= which_n) {
          which_n = 0;
          while (plug_n < plugininfo.size() && (!plugininfo.get(plug_n).mapoutput || plugininfo.get(plug_n).plugin.quantifierCount() >= which_n)) plug_n += 1;
          if (plug_n >= plugininfo.size()) throw new IllegalArgumentException("Too many custom inputs");
        }
        ds.plugnum = plug_n;
        ds.which = which_n;
      }
      else {
        if (plug_n>=0 && which_n>=0) throw new IllegalArgumentException("Custom outputs must either be referenced by plugin and number, or filled in in order; cannot mix the two.");
        plug_n = -1;
        String[] ss = ds.extra.split("::");
        if (ss.length > 2) throw new IllegalArgumentException("Custom outputs must specify the plugin name / nickname followed by the output number, separated by ::");
        for (int i = 0; i < plugininfo.size(); i++) {
          ComputationInfo ci = plugininfo.get(i);
          if (ci==null || !ci.mapoutput) continue;
          if (ss[0].equals(ci.nick)) {
            if (ci.plugin.quantifierCount()==0) throw new IllegalArgumentException("Plugin nicknamed '"+ci.nick+"' provides no outputs.");
            if (ss.length != 2 && ci.plugin.quantifierCount() != 1) throw new IllegalArgumentException("Output number or name required for plugin nicknamed '"+ci.nick+"'");
            int j = 0;
            if (ss.length==2) {
              try {
                j = Integer.parseInt(ss[1])-1;
                if (j<0 || j>=ci.plugin.quantifierCount()) throw new IllegalArgumentException("Plugin "+ci.name+" nicknamed '"+ci.nick+"' has no output number "+(j+1)+" (counting from 1)");
              }
              catch (NumberFormatException nfe) {
                j = 0;
                String colname = ss[1]+"@";
                while (j < ci.plugin.quantifierCount() && !ci.plugin.quantifierTitle(j).startsWith(colname)) j++;
                if (j >= ci.plugin.quantifierCount()) throw new IllegalArgumentException("Plugin "+ci.name+" nicknamed '"+ci.nick+"' has no output called "+ss[1]);
              }
            }
            ds.plugnum = i;
            ds.which = j;
            return;
          }
        }
        if (ss.length != 2) throw new IllegalArgumentException("Trying to look for plugin name but no output number specified (count from 0)");
        int idx = -1;
        try { idx = Integer.parseInt(ss[1]); } catch (NumberFormatException nfe) { throw new IllegalArgumentException("Custom output number was not actually a number: "+ss[1]+ " (Did you mean to use a nickname?)"); }
        for (int i=0 ; i<plugininfo.size() ; i++) {
          if (ss[0].equals(plugininfo.get(i).name)) {
            if (plugininfo.get(i).plugin.quantifierCount() <= idx) throw new IllegalArgumentException("Plugin "+plugininfo.get(i).name+" only has "+plugininfo.get(i).plugin.quantifierCount()+" outputs (counting up from 0)");
            ds.plugnum = i;
            ds.which = idx;
            return;
          }
        }
        throw new IllegalArgumentException("Could not find custom output "+ss[1]+" for plugin "+ss[0]);
      }
    }
  }

  public class ComputationInfo {
    public CustomComputation plugin;
    public String nick;
    public String name;
    public String[] arguments;
    public boolean mapoutput;
    public ComputationInfo(CustomComputation cc, String n, String nn, String[] args, boolean out) {
      plugin = cc; name = n; nick = nn; arguments = args; mapoutput = out;
    }
  }


  // A file-writing wrapper that is useful for plugins
  public static PrintWriter nfprintf(PrintWriter p,File f,String format,Object... args) throws IOException {
    if (p==null) p = new PrintWriter( new BufferedOutputStream( new FileOutputStream( f ) ) );
    p.printf(format,args);
    return p;
  }
  
  
  // Filters to pull out various filenames
  public class SummaryFilter implements FilenameFilter
  {
    public boolean accept(File f,String s) { return s.endsWith(".summary") && !s.equals(".summary"); }
  }
  public class SitterFilter implements FilenameFilter
  {
    String prefix;
    SitterFilter(String pre) { prefix=pre; }
    public boolean accept(File f,String s) { return s.startsWith(prefix) && s.endsWith(".ref"); }
  }
  public class DancerFilter implements FilenameFilter
  {
    String prefix;
    DancerFilter(String pre) { prefix=pre; }
    public boolean accept(File f,String s) { return s.startsWith(prefix) && (s.endsWith(".blob") || s.endsWith(".blobs")); }
  }
  public class PngFilter implements FilenameFilter
  {
    String prefix;
    PngFilter(String pre) { prefix=pre; }
    public boolean accept(File f,String s) { return s.equalsIgnoreCase(prefix + ".png") && s.startsWith(prefix); }
  }
  
  public class PngSetFilter implements FilenameFilter
  {
    String prefix;
    PngSetFilter(String pre) { prefix = pre; }
    public boolean accept(File f, String s) { return s.startsWith(prefix) && s.toLowerCase().endsWith(".png") && !s.equalsIgnoreCase(prefix+".png"); }
  }
  
  
  // Keep track of files in which to find dancers (when bundled)
  public class MultiFileInfo implements Comparable<MultiFileInfo> {
    int id;
    int fnum;
    long offset;
    public MultiFileInfo(int i,int fn,long os) { id=i; fnum=fn; offset=os; }
    @Override public int compareTo(MultiFileInfo mfi) {
      if (fnum<mfi.fnum) return -1;
      else if (fnum>mfi.fnum) return 1;
      else if (offset<mfi.offset) return -1;
      else if (offset>mfi.offset) return 1;
      else return 0;
    }
  }
  
  
  // Wrap RandomAccessFile in a BufferedReader--argh, stupid class structure!
  public class RandomAccessFileReader extends BufferedReader {
    RandomAccessFile raf;
    long mark;
    public RandomAccessFileReader(RandomAccessFile r) { 
      super(new StringReader("Dummy\nbuffer\n"));  // Fool our parent constructor 
      raf=r; mark = -1;
    }
    int readPartially(byte[] bbuf) throws IOException {
      long n = raf.getFilePointer();
      try { raf.readFully(bbuf); }
      catch (EOFException ee) { return (int)(raf.getFilePointer() - n); }
      return bbuf.length;
    }
    public void seek(long n) throws IOException { raf.seek(n); }
    @Override public void close() throws IOException { raf.close(); }
    @Override public void mark(int aheadness) throws IOException { mark=raf.getFilePointer(); }
    @Override public boolean markSupported() { return true; }
    @Override public int read() throws IOException { return raf.read(); }
    @Override public int read(char cbuf[]) throws IOException {
      byte[] bbuf = new byte[cbuf.length];
      int n = readPartially(bbuf);
      for (int i=0;i<n;i++) cbuf[i]=(char)bbuf[i];
      return n;
    }
    @Override public int read(char cbuf[],int off,int len) throws IOException {
      byte[] bbuf = new byte[len];
      int n = readPartially(bbuf);
      for (int i=0;i<n;i++) cbuf[i+off]=(char)bbuf[i];
      return n;
    }
    @Override public int read(CharBuffer cb) throws IOException {
      byte[] bbuf = new byte[cb.remaining()];
      int n = readPartially(bbuf);
      char[] cbuf = new char[n];
      for (int i=0;i<n;i++) cbuf[i]=(char)bbuf[i];
      cb.put(cbuf);
      return n;
    }
    @Override public String readLine() throws IOException { return raf.readLine(); }
    @Override public boolean ready() throws IOException {
      try { return raf.length() > raf.getFilePointer(); }
      catch (IOException ioe) { return false; }
    }
    @Override public void reset() throws IOException { if (mark!=-1) raf.seek(mark); }
    @Override public long skip(long n) throws IOException {
      long m = raf.getFilePointer();
      if (n+m<0) { raf.seek(0); return -m; }
      else if (n+m>raf.length()) { raf.seek(raf.length()); return raf.length()-m; }
      else { raf.seek(n+m); return n; }
    }
  }
  
  
  // Custom exceptions
  public class WrongFilesException extends IOException { WrongFilesException(String s) { super(s); } }
  public class LoadDataException extends IOException { LoadDataException(String s) { super(s); } }
  public class SaveDataException extends IOException { SaveDataException(String s) { super(s); } }
  
  
  // Statistics one can extract from the data
  public enum DataMeasure {
    AVG,MED,MAX,MIN,Q_4,Q_1,STD,SEM,VAR,ONE,NUM,JIT,NOP;
    private static final HashMap<String,DataMeasure> interpreter;
    private static final HashMap<DataMeasure,String> reterprenti;
    private static String mySingleLetterNames = null;
    private static String[] myLongNames = null;
    static
    {
      interpreter = new HashMap<String,DataMeasure>();
      interpreter.put("average",AVG);
      interpreter.put("mean",AVG);
      interpreter.put("median",MED);
      interpreter.put("-",MED);
      interpreter.put("maximum",MAX);
      interpreter.put("max",MAX);
      interpreter.put("^",MAX);
      interpreter.put("minimum",MIN);
      interpreter.put("min",MIN);
      interpreter.put("_",MIN);
      interpreter.put("last-quartile",Q_4);
      interpreter.put("fourth-quartile",Q_4);
      interpreter.put("p75",Q_4);
      interpreter.put("first-quartile",Q_1);
      interpreter.put("p25",Q_1);
      interpreter.put("deviation",STD);
      interpreter.put("std",STD);
      interpreter.put("stdev",STD);
      interpreter.put("*",STD);
      interpreter.put("sem",SEM);
      interpreter.put("var",VAR);
      interpreter.put("exists",ONE);
      interpreter.put("?",ONE);
      interpreter.put("number",NUM);
      interpreter.put("#",NUM);
      interpreter.put("jitter",JIT);
      interpreter.put("skip",NOP);
      interpreter.put("~",NOP);
      reterprenti = new HashMap<DataMeasure,String>();
      for (String s : interpreter.keySet())
      {
        if (reterprenti.containsKey( interpreter.get(s) ))
        {
          if (s.length() > reterprenti.get(interpreter.get(s)).length()) reterprenti.put( interpreter.get(s) , s );
        }
        else reterprenti.put( interpreter.get(s) , s );
      }
    }
    public static DataMeasure interpret(String s) throws IllegalArgumentException
    {
      if (!interpreter.containsKey(s)) throw new IllegalArgumentException(s + " is not a valid statistic to apply");
      return interpreter.get(s);
    }
    public static String toText(DataMeasure dm) { return reterprenti.get(dm); }
    public static String singleLetterNames() {
      if (mySingleLetterNames == null) {
        StringBuilder sb = new StringBuilder();
        for (String s: interpreter.keySet()) if (s.length() == 1) sb.append(s);
        mySingleLetterNames = sb.toString();
      }
      return mySingleLetterNames;
    }
    public static String[] longNames() {
      if (myLongNames == null) {
        ArrayList<String> ss = new ArrayList<String>();
        for (String s: interpreter.keySet()) if (s.length() > 1) ss.add(s);
        myLongNames = ss.toArray(new String[ss.size()]);
      }
      return myLongNames;
    }
  }
  
  // Types of data one can request (both global and per object)
  public enum DataSource
  {
    EMPT,TIME,FNUM,OBID,NUMB,GOOD,AREA,PERS,SPED,ASPD,LENG,RLEN,WIDT,RWID,ASPC,RASP,MIDL,OUTW,KINK,BIAS,PATH,DIRC,CURV,PHAS,VELX,VELY,LOCX,LOCY,ORNT,CRAB,QXFW,CUST,STI1,STI2,STI3,STI4;
    private static final HashMap<String,DataSource> interpreter;
    private static final HashMap<DataSource,String> reterprenti;
    private static String mySingleLetterNames = null;
    private static String[] myLongNames = null;
    static
    {
      interpreter = new HashMap<String,DataSource>();
      interpreter.put("empty",EMPT);
      interpreter.put("time",TIME);
      interpreter.put("t",TIME);
      interpreter.put("frame",FNUM);
      interpreter.put("f",FNUM);
      interpreter.put("id",OBID);
      interpreter.put("D", OBID);
      interpreter.put("number",NUMB);
      interpreter.put("n",NUMB);
      interpreter.put("goodnumber",GOOD);
      interpreter.put("N",GOOD);
      interpreter.put("area",AREA);
      interpreter.put("e",AREA);
      interpreter.put("persistence",PERS);
      interpreter.put("p",PERS);
      interpreter.put("speed",SPED);
      interpreter.put("s",SPED);
      interpreter.put("angular",ASPD);
      interpreter.put("S",ASPD);
      interpreter.put("length",LENG);
      interpreter.put("l",LENG);
      interpreter.put("rellength",RLEN);
      interpreter.put("L",RLEN);
      interpreter.put("width",WIDT);
      interpreter.put("w",WIDT);
      interpreter.put("relwidth",RWID);
      interpreter.put("W",RWID);
      interpreter.put("aspect",ASPC);
      interpreter.put("a",ASPC);
      interpreter.put("relaspect",RASP);
      interpreter.put("A",RASP);
      interpreter.put("midline",MIDL);
      interpreter.put("m",MIDL);
      interpreter.put("morphwidth",OUTW);
      interpreter.put("M",OUTW);
      interpreter.put("kink",KINK);
      interpreter.put("k",KINK);
      interpreter.put("bias",BIAS);
      interpreter.put("b",BIAS);
      interpreter.put("pathlen",PATH);
      interpreter.put("P",PATH);
      interpreter.put("dir",DIRC);
      interpreter.put("d",DIRC);
      interpreter.put("curve",CURV);
      interpreter.put("c",CURV);
      interpreter.put("loc_x",LOCX);
      interpreter.put("x",LOCX);
      interpreter.put("loc_y",LOCY);
      interpreter.put("y",LOCY);
      interpreter.put("vel_x",VELX);
      interpreter.put("u",VELX);
      interpreter.put("vel_y",VELY);
      interpreter.put("v",VELY);
      interpreter.put("orient",ORNT);
      interpreter.put("o",ORNT);
      interpreter.put("crab",CRAB);
      interpreter.put("r",CRAB);
      interpreter.put("qxfw",QXFW);
      interpreter.put("Q",QXFW);
      interpreter.put("custom",CUST);
      interpreter.put("C",CUST);
      interpreter.put("tap",STI1);
      interpreter.put("1",STI1);
      interpreter.put("puff",STI2);
      interpreter.put("2",STI2);
      interpreter.put("stim3",STI3);
      interpreter.put("3",STI3);
      interpreter.put("stim4",STI4);
      interpreter.put("4",STI4);
      reterprenti = new HashMap<DataSource,String>();
      for (String s : interpreter.keySet())
      {
        if (reterprenti.containsKey( interpreter.get(s) ))
        {
          if (s.length() > reterprenti.get(interpreter.get(s)).length()) reterprenti.put( interpreter.get(s) , s );
        }
        else reterprenti.put( interpreter.get(s) , s );
      }
    }
    public static DataSource interpret(String s) throws IllegalArgumentException
    {
      if (!interpreter.containsKey(s)) throw new IllegalArgumentException(s + " is not a valid data type");
      return interpreter.get(s);
    }
    public static String toText(DataSource ds) { return reterprenti.get(ds); }
    public static String singleLetterNames() {
      if (mySingleLetterNames == null) {
        StringBuffer sb = new StringBuffer();
        for (String s: interpreter.keySet()) if (s.length() == 1) sb.append(s);
        mySingleLetterNames = sb.toString();
      }
      return mySingleLetterNames;
    }
    public static String[] longNames() {
      if (myLongNames == null) {
        ArrayList<String> ss = new ArrayList<String>();
        for (String s: interpreter.keySet()) if (s.length() > 1) ss.add(s);
        myLongNames = ss.toArray(new String[ss.size()]);
      }
      return myLongNames;
    }
    public boolean alwaysCompute()
    {
      switch (this)
      {
        case TIME:
        case FNUM:
        case OBID:
        case NUMB:
        case GOOD:
        case AREA:
        case PERS:
        case LENG:
        case WIDT:
        case STI1:
        case STI2:
        case STI3:
        case STI4:
          return true;
        default:
          return false;
      }
    }
  }
  
  // Formats the output data prettily (or at least not atrociously)
  public class DataPrinter implements Graphs.FunctionData
  {
    boolean count_events;
    int[] i_data;
    float[] f_data;
    Statistic[] s_data;
    public DataMeasure what;
    String fformat;
    float mult;
    String title;
    float[] times;
    public DataPrinter(float[] t)
    {
      count_events=false;
      i_data=null;
      f_data=null;
      s_data=null;
      what=DataMeasure.AVG; 
      fformat="%.2f";
      mult=1.0f;
      title = "";
      times = t;
    }
    // Setup methods
    public DataPrinter setI(int[] i) { i_data=i; return this; }
    public DataPrinter setF(float[] f) { f_data=f; return this; }
    public DataPrinter setS(Statistic[] s) { s_data=s; return this; }
    public DataPrinter setDig(int dig) { fformat = "%." + dig + "f"; return this; }
    public DataPrinter setMult(double d) { mult=(float)d; return this; }
    public DataPrinter countOn() { count_events=true; return this; }
    public DataPrinter countOff() { count_events=false; return this; }
    public DataPrinter setT(String t) { title = t; return this;  }
    // Used primarily for graphing
    public String getTitle()
    {
      String pre;
      if (s_data!=null)
      {
        switch (what)
        {
          case AVG: pre = ""; break;
          case MED: pre = "Median "; break;
          case MAX: pre = "Maximum "; break;
          case MIN: pre = "Minimum "; break;
          case Q_1:
          case Q_4: pre = "Quartiles of "; break;
          case STD: pre = "Deviation of "; break;
          case SEM: pre = "Error in "; break;
          case VAR: pre = "Variance of "; break;
          case NUM: pre = "Sample size for "; break;
          case ONE: pre = "Existence of "; break;
          case JIT: pre = "Intrinsic noise in "; break;
          default: pre = "Unknown Metric for ";
        }
      }
      else pre = "";
      return pre + title;
    }
    public boolean isSingleValued() // If true, draw a line graph.  If false, draw a range.
    {
      return (s_data==null || ! (what==DataMeasure.STD || what==DataMeasure.SEM || what==DataMeasure.Q_1 || what==DataMeasure.Q_4));
    }
    public boolean isSimilarClass(Graphs.FunctionData fd) // Graphs that naturally go together
    {
      return ( title.equals( ((DataPrinter)fd).title ) && ((what==DataMeasure.NUM) == (((DataPrinter)fd).what==DataMeasure.NUM)) );
    }
    public int length() { return times.length; }
    // Various bookkeeping methods
    public float[] timeBase() { return times; }
    public float[] limits(float[] fa)
    {
      if (fa==null || fa.length<2) fa = new float[2];
      fa = value(0,times.length-1,fa);
      if (fa[0]>0) fa[0] = 0;
      if (fa[1]<0) fa[1] = 0;
      if (Math.abs(fa[1]-fa[0])<1e-6)
      {
        fa[0] -= 0.1;
        fa[1] += 0.1;
      }
      return fa;
    }
    public float timeOf(int i) { return (i<0 || i>= times.length) ? Float.NaN : times[i]; }
    public int indexOf(float t)
    {
      int lo=0;
      int hi=times.length-1;
      int mid;
      if (t < times[lo]) return -1;
      if (t > times[hi]) return -1;
      while (hi-lo > 1)
      {
        mid = (hi+lo)/2;
        if (times[mid] < t) lo=mid;
        else hi=mid;
      }
      if (times[hi]-t < t-times[lo]) return hi;
      else return lo;
    }
    // Actually get the data at a certain index
    public float value(int i)
    {
      float multiplier = mult;  // By default, use the mult member variable
      float f;
      if (s_data!=null)
      {
        switch (what)
        {
          case AVG: f = (float)s_data[i].average; break;
          case MED: f = (float)s_data[i].median; break;
          case MAX: f = (float)s_data[i].maximum; break;
          case MIN: f = (float)s_data[i].minimum; break;
          case Q_1:  f = (float)s_data[i].first_quartile; break;
          case Q_4:  f = (float)s_data[i].last_quartile; break;
          case SEM: multiplier /= Math.sqrt(s_data[i].n); // Fall through to STD case
          case STD: f = (float)s_data[i].deviation; break;
          case VAR: f = (float)(s_data[i].deviation*s_data[i].deviation); break;
          case ONE: f = (Double.isNaN(s_data[i].average) || s_data[i].n==0) ? 0.0f : 1.0f; multiplier=1.0f; break;
          case NUM: f = (float)s_data[i].n; multiplier=1.0f; break;
          case JIT: f = (float)s_data[i].jitter; break;
          default: f = Float.NaN;
        }
      }
      else if (f_data!=null) f = f_data[i];
      else if (i_data!=null) f = i_data[i];
      else return Float.NaN;
      return f*multiplier;
    }
    public float value(float t) { int i = indexOf(t); return (i<0) ? Float.NaN : value(i); }
    // The data with a statistic computed across a range of times
    public float[] value(int i,int j,float[] fa)
    {
      boolean no_mult = false;
      float f;
      if (fa==null || fa.length<2) fa = new float[2];
      if (j<i)
      {
        fa[0] = fa[1] = Float.NaN;
        return fa;
      }
      if (s_data!=null)
      {
        if (what!=DataMeasure.NUM)
        {
          while (i<=j && s_data[i].n<=0) i++;
          if (j<i)
          {
            fa[0] = fa[1] = Float.NaN;
            return fa;
          }
        }
        switch (what)
        {
          case AVG:
            fa[0] = fa[1] = (float)s_data[i].average;
            for ( ; i<=j ; i++)
            {
              if (s_data[i].n<=0) continue;
              f = (float)s_data[i].average;
              if (f<fa[0]) fa[0] = f;
              else if (f>fa[1]) fa[1] = f;
            }
            break;
          case MED:
            fa[0] = fa[1] = (float)s_data[i].median;
            for ( ; i<=j ; i++)
            {
              if (s_data[i].n<=0) continue;
              f = (float)s_data[i].median;
              if (f<fa[0]) fa[0] = f;
              else if (f>fa[1]) fa[1] = f;
            }
            break;
          case JIT:
            fa[0] = fa[1] = (float)s_data[i].jitter;
            for ( ; i<=j ; i++) {
              if (s_data[i].n<=0) continue;
              f = (float)s_data[i].jitter;
              if (f<fa[0]) fa[0] = f;
              else if (f>fa[1]) fa[1] = f;
            }
            break;
          case MAX:
            fa[0] = fa[1] = (float)s_data[i].maximum;
            for ( ; i<=j ; i++)
            {
              if (s_data[i].n<=0) continue;
              f = (float)s_data[i].maximum;
              if (f<fa[0]) fa[0] = f;
              else if (f>fa[1]) fa[1] = f;
            }
            break;
          case MIN:
            fa[0] = fa[1] = (float)s_data[i].minimum;
            for ( ; i<=j ; i++)
            {
              if (s_data[i].n<=0) continue;
              f = (float)s_data[i].minimum;
              if (f<fa[0]) fa[0] = f;
              else if (f>fa[1]) fa[1] = f;
            }
            break;
          case Q_1:
          case Q_4:
            fa[0] = (float)s_data[i].first_quartile;
            fa[1] = (float)s_data[i].last_quartile;
            for ( ; i<=j ; i++)
            {
              if (s_data[i].n <= 0) continue;
              f = (float) s_data[i].first_quartile;
              if (f < fa[0]) fa[0] = f;
              f = (float) s_data[i].last_quartile;
              if (f > fa[1]) fa[1] = f;
            }
            break;
          case STD:
            while (i<=j && Double.isNaN(s_data[i].deviation)) i++;
            if (j<i)
            {
              fa[0] = fa[1] = Float.NaN;
              break;
            }
            fa[0] = (float)(s_data[i].average - s_data[i].deviation);
            fa[1] = (float)(s_data[i].average + s_data[i].deviation);
            for ( ; i<=j ; i++)
            {
              if (s_data[i].n <= 0 || Double.isNaN(s_data[i].deviation)) continue;
              f = (float)(s_data[i].average - s_data[i].deviation);
              if (f < fa[0]) fa[0] = f;
              f = (float)(s_data[i].average + s_data[i].deviation);
              if (f > fa[1]) fa[1] = f;
            }
            break;
          case SEM:
            while (i<=j && Double.isNaN(s_data[i].deviation)) i++;
            if (j<0)
            {
              fa[0] = fa[1] = Float.NaN;
              break;
            }
            fa[0] = (float)(s_data[i].average - s_data[i].deviation/Math.sqrt(s_data[i].n));
            fa[1] = (float)(s_data[i].average + s_data[i].deviation/Math.sqrt(s_data[i].n));
            for ( ; i<=j ; i++)
            {
              if (s_data[i].n <= 0 || Double.isNaN(s_data[i].deviation)) continue;
              f = (float)(s_data[i].average - s_data[i].deviation/Math.sqrt(s_data[i].n));
              if (f < fa[0]) fa[0] = f;
              f = (float)(s_data[i].average + s_data[i].deviation/Math.sqrt(s_data[i].n));
              if (f > fa[1]) fa[1] = f;
            }
            break;
          case VAR:
            while (i<=j && Double.isNaN(s_data[i].deviation)) i++;
            if (j<0)
            {
              fa[0] = fa[1] = Float.NaN;
              break;
            }
            fa[0] = (float)(s_data[i].deviation*s_data[i].deviation);
            fa[1] = (float)(s_data[i].deviation*s_data[i].deviation);
            for ( ; i<=j ; i++)
            {
              if (s_data[i].n <= 0 || Double.isNaN(s_data[i].deviation)) continue;
              f = (float)(s_data[i].deviation*s_data[i].deviation);
              if (f < fa[0]) fa[0] = f;
              f = (float)(s_data[i].deviation*s_data[i].deviation);
              if (f > fa[1]) fa[1] = f;
            }
            break;
          case ONE:
            fa[0] = fa[1] = -1;
            while (i<=j)
            {
              if ( Double.isNaN(s_data[i].average) || s_data[i].n==0 ) fa[0] = 0;
              else fa[1] = 1;
              i++;
            }
            if (fa[0]==-1) fa[0] = fa[1];
            if (fa[1]==-1) fa[1] = fa[0];
            break;
          case NUM:
            no_mult = true;
            fa[0] = fa[1] = (float)s_data[i].n;
            for ( ; i<=j ; i++)
            {
              f = (float)s_data[i].n;
              if (f < fa[0]) fa[0] = f;
              else if (f > fa[1]) fa[1] = f;
            }
            break;
          default:
            fa[0] = fa[1] = Float.NaN;
        }
      }
      else if (f_data!=null)
      {
        fa[0] = fa[1] = f_data[i];
        for (i=i+1 ; i<=j ; i++)
        {
          f = f_data[i];
          if (f < fa[0]) fa[0] = f;
          else if (f > fa[1]) fa[1] = f;
        }
      }
      else if (i_data!=null)
      {
        fa[0] = fa[1] = i_data[i];
        for (i=i+1 ; i<=j ; i++)
        {
          f = i_data[i];
          if (f < fa[0]) fa[0] = f;
          else if (f > fa[1]) fa[1] = f;
        }
      }
      else fa[0] = fa[1] = Float.NaN;
      if (!no_mult) { fa[0] *= mult; fa[1] *= mult; }
      return fa;
    }
    public float[] value(float t0,float t1,float[] fa)
    {
      int i = indexOf(t0);
      int j = indexOf(t1);
      if (i<0 || j<0)
      {
        if (fa==null || fa.length<2) fa = new float[2];
        fa[0] = fa[1] = Float.NaN;
        return fa;
      }
      else return value(i,j,fa);
    }
    // Get the value and return it as a string instead of a number
    public String printValue(float one_value) { return String.format(fformat,catchnan(one_value)*mult); }
    public String print(int i)
    {
      if (s_data!=null || f_data!=null) return String.format(fformat,catchnan(value(i)));
      else if (i_data!=null)
      {
        if (mult==1.0f) return Integer.toString( i_data[i] );
        else return Integer.toString( (int)Math.round( i_data[i]*mult ) );
      }
      else if (nanless) return "0";
      else return "NaN";
    }
    public String print(int i,int j)
    {
      if (i>=j || !count_events || s_data!=null || f_data!=null) return print(j);
      else
      {
        int count = 0;
        for (int k=i;k<=j;k++) if (i_data[k]!=0) count++;
        return Integer.toString(count);
      }
    }
    public String printAvg(int i,int j)
    {
      int n_tot = 0;
      float v_tot = 0.0f;
      for (int k=i ; k<=j ; k++)
      {
        if (s_data!=null) n_tot += s_data[k].n;
        else n_tot++;
        if (s_data!=null && what!=DataMeasure.NUM && what!=DataMeasure.ONE) v_tot += s_data[k].n * value(k);
        else v_tot += value(k);
      }
      if (s_data==null || (what!=DataMeasure.NUM && what!=DataMeasure.ONE)) v_tot /= n_tot;
      if (s_data!=null || f_data!=null) return String.format(fformat,catchnan(v_tot));
      else if (i_data!=null) return Integer.toString( Math.round(v_tot) );
      else if (nanless) return "0";
      else return "NaN";
    }
  }
  
  // A little wrapper class to associate the requested data with the requested statistic
  public class DataSpecifier
  {
    public DataSource source = DataSource.EMPT;
    public DataMeasure measure = DataMeasure.NOP;
    public int plugnum = -1;    // For CUST only
    public int which = -1;      // For CUST only
    public String extra = "";   // For CUST only
    public DataSpecifier() {}
    public DataSpecifier(DataSource s,DataMeasure m) { source = s; measure = m; }
    public DataSpecifier(DataSource s, DataMeasure m, String ex) { source = s; measure = m; extra = ex; }
  }
  public DataSpecifier makeDataSpecifier(DataSource s, DataMeasure m) { return new DataSpecifier(s,m); }
  public DataSpecifier makeDataSpecifier(DataSource s, DataMeasure m, String ex) { return new DataSpecifier(s,m,ex); }
  
  // Data we need to know to trigger on specific events
  public static class Triggerer implements Comparable< Triggerer >
  {
    public float dt;
    public float time1,time2;
    public DataSource source;
    public Triggerer(float dt,float t1) { this.dt=dt; time1=t1; time2=Float.NaN; source=null; }
    public Triggerer(float dt,float t1,float t2,DataSource ds) { this.dt=dt; time1=t1; time2=t2; source=ds; }
    public int compareTo(Triggerer t) 
    {
      if (source==null && t.source==null) return ((time1<t.time1) ? -1 : ( (time1>t.time1) ? 1 : 0 ));
      else if (source==null && t.source!=null) return -1;
      else if (source!=null && t.source==null) return 1;
      else return DataSource.toText(source).compareTo(DataSource.toText(t.source));
    }
  }
  
  // Data we need to know to tell if a frame is valid or not (and what index we should map it to)
  public static class FrameMap implements Comparable< FrameMap >
  {
    public int stated;
    public int actual;
    public boolean okay;
    public FrameMap(int s,int a,boolean o) { stated = s; actual = a; okay = o; }
    public int compareTo(FrameMap fm)
    {
      if (actual < fm.actual) return -1;
      else if (actual > fm.actual) return 1;
      else return (stated<fm.stated) ? -1 : ( (stated>fm.stated) ? 1 : 0 );
    }
  }

  
  // Interpreting lines of text files
  public class SummaryLine
  {
    int frame;
    int n_frames;
    float time;
    float last_time;
    int number;
    LinkedList<Integer> events;
    Ancestry ancestry;
    String[] tokens;
    
    public SummaryLine() { frame=-1; events=null; ancestry=null; tokens=null; }
    
    public boolean isSame(String[] tok)
    {
      if (tokens==null || tok==null) return false;
      if (tokens.length<14 || tok.length<14) return false;
      for (int i=2;i<14;i++)
      {
        if (!tokens[i].equals(tok[i])) return false;
        if ( (i%2)==1 ) i++;  // Skip 4,6,8... as they're relative, and relative stuff updates unfairly
      }
      return true;
    }
    
    public void initialParse(String[] tok) throws NumberFormatException
    {
      tokens = tok;
      if (tokens==null || tokens.length<3) return;
      
      frame = Integer.parseInt( tokens[0] );
      n_frames = 1;
      time = Float.parseFloat( tokens[1] );
      last_time = time; 
      number = Integer.parseInt( tokens[2] );
      events = null;
      ancestry = null;
    }
    
    public void duplicateParse(String[] tok) throws NumberFormatException  // Used if a line seems to be a copy of a previous line (no new data)
    {
      tokens = tok;
      if (tokens==null || tokens.length<3) return;
      int i = Integer.parseInt(tokens[0]);
      n_frames = 1 + i - frame;
      if (n_frames<1) n_frames = 1;
      last_time = Float.parseFloat( tokens[1] );
    }
    
    public void extraParse() throws NumberFormatException  // Used to parse stimulus events and object creation/destruction
    {
      if (tokens==null || tokens.length < 14) return;
      int i = 0;
      while (i < tokens.length) {
        if (tokens[i].startsWith("%")) break;
        i++;
      }
      if (i >= tokens.length) return;

      // Check for list of events
      if (tokens[i].equals("%"))
      {
        int ev;
        
        if (events==null) events = new LinkedList<Integer>();
        for (i++ ; i < tokens.length && !tokens[i].startsWith("%") && !tokens[i].startsWith("@"); i++)
        {
          if (tokens[i].startsWith("0x")) {
            ev = Integer.parseInt( tokens[i].substring(2) , 16 );
            for (int j=1 ; ev!=0 ; j++) {
              if ((ev&0x1) != 0) events.add(j);
              ev >>=1;
            }
          }
          else {
            ev = Integer.parseInt( tokens[i] );
            events.add(ev);
          }
        }
      }
      if (i>=tokens.length) return;
      
      // Check for list of what's found/lost
      if (tokens[i].equals("%%"))
      {
        int ori,fate;
        
        if (ancestry==null) ancestry = new Ancestry(-1);
        for (i++ ; i+1 < tokens.length ; i+=2)
        {
          if (tokens[i].equals("%%%") || tokens[i].equals("@")) break;
          ori = Integer.parseInt( tokens[i] );
          fate = Integer.parseInt( tokens[i+1] );
          ancestry.addOriginFate(ori,fate);
        }
      }
      if (i>=tokens.length) return;
      
      // Check for list of where blobs files are found
      if (tokens[i].equals("%%%")) {
        int id;
        int fnum;
        long offset;
        for (i++ ; i+1<tokens.length ; i+=2) {
          if (tokens[i].equals("@")) break;
          id = Integer.parseInt(tokens[i]);
          int j = tokens[i+1].indexOf('.');
          if (j<0) { fnum = Integer.parseInt(tokens[i+1]); offset=0; }
          else {
            fnum = Integer.parseInt( tokens[i+1].substring(0,j) );
            offset = Long.parseLong( tokens[i+1].substring(j+1) );
          }
          dancer_multi_list.add( new MultiFileInfo(id,fnum,offset) );
        }
      }
    }
    
    public void completeParse() { tokens = null; }
  }

  public enum PrefixSI {
    ATTO(-18,'a'),
    FEMTO(-15,'f'),
    PICO(-12,'p'),
    NANO(-9,'n'),
    MICRO(-6,'u'),
    MILLI(-3,'m'),
    CENTI(-2,'c'),
    UNO(0,'\u0000'),
    KILO(3,'k'),
    MEGA(6,'M'),
    GIGA(9,'G'),
    TERA(12,'T'),
    PETA(15,'P'),
    EXA(18,'E');
    public int power;
    public char prefix;
    PrefixSI(int pow, char pre) {
      power = pow; prefix = pre;
    }
    public static double parseDouble(String s) {
      if (s.length()<2) return Double.parseDouble(s);
      char c = s.charAt(s.length()-1);
      PrefixSI psi = UNO;
      for (PrefixSI p : PrefixSI.values()) {
        if (p.prefix == c) psi = p;
      }
      if (psi != UNO) return Double.parseDouble(s.substring(0,s.length()-1)+"e"+psi.power);
      else return Double.parseDouble(s);
    }
  }

  public enum LengthUnit {
    PX, MM, M, BL, ERR
  }
  public final class PhysicalLength {
    double value;
    LengthUnit unit;
    public PhysicalLength(double x, LengthUnit lu) { value = x; unit = lu; }
    public PhysicalLength(String s, LengthUnit default_unit) {
      String t = s.toLowerCase();
      String u = t;
      if (t.endsWith("px")) { unit = LengthUnit.PX; u = t.substring(0,t.length()-2); }
      else if (t.endsWith("mm")) { unit = LengthUnit.MM; u = t.substring(0,t.length()-2); }
      else if (t.endsWith("m")) { unit = LengthUnit.M; u = t.substring(0,t.length()-1); }
      else if (t.endsWith("bl")) { unit = LengthUnit.BL; u = t.substring(0,t.length()-2); }
      else { unit = default_unit; u = t; }
      try {
        value = (unit==LengthUnit.MM) ? Double.parseDouble(u) : PrefixSI.parseDouble(u);
      }
      catch (NumberFormatException nfe) {
        value = Double.NaN;
        unit = LengthUnit.ERR;
      }
    }
    public final double getPx(Dance d) { switch(unit) {
      case PX: return value;
      case MM: return value / mm_per_pixel;
      case M: return value / (mm_per_pixel / 1000);
      case BL: if (d==null) return value / mm_per_pixel; else return value * d.meanBodyLengthEstimate();
      default: return Double.NaN;
    } }
    public final double getMm(Dance d) { switch(unit) {
      case PX: return value * mm_per_pixel;
      case MM: return value;
      case M: return value * 1000;
      case BL: if (d==null) return value; else return value * d.meanBodyLengthEstimate() * mm_per_pixel;
      default: return Double.NaN;
    } }
    public final double getM(Dance d) { switch(unit) {
      case PX: return value * mm_per_pixel / 1000;
      case MM: return value / 1000;
      case M: return value;
      case BL: if (d==null) return value / 1000; else return value * d.meanBodyLengthEstimate() * mm_per_pixel / 1000;
      default: return Double.NaN;
    } }
    public final double getBl(Dance d) {
      if (d == null) return getMm(d);
      else switch(unit) {
        case PX: return value / d.meanBodyLengthEstimate();
        case MM: return value / (mm_per_pixel * d.meanBodyLengthEstimate());
        case M: return value / (mm_per_pixel * 0.001 * d.meanBodyLengthEstimate());
        case BL: return value;
        default: return Double.NaN;
      }
    }
    public final void setPx(double x) { value = x; unit = LengthUnit.PX; }
    public final void setMm(double x) { value = x; unit = LengthUnit.MM; }
    public final void setM(double x) { value = x; unit = LengthUnit.M; }
    public final void setBl(double x) { value = x; unit = LengthUnit.BL; }
    public final boolean isAbsolute() { switch(unit) {
      case PX:
      case MM:
      case M: return true;
      default: return false;
    } }
    public final boolean isRelative() { return unit == LengthUnit.BL; }
    public final boolean isError() { return unit == LengthUnit.ERR || Double.isNaN(value); }
    public final PhysicalLength ensure() { if (isError()) throw new NumberFormatException("Improper length."); return this; }
  }
  public PhysicalLength parsePhysicalPx(String s) throws NumberFormatException {
    PhysicalLength pl = new PhysicalLength(s, LengthUnit.PX);
    if (!pl.isAbsolute()) throw new NumberFormatException("Failed to parse an absolute length from "+s);
    return pl;
  }
  public PhysicalLength parsePhysicalMm(String s) throws NumberFormatException {
    PhysicalLength pl = new PhysicalLength(s, LengthUnit.MM);
    if (!pl.isAbsolute()) throw new NumberFormatException("Failed to parse an absolute length from "+s);
    return pl;
  }

  public enum AngularUnit {
    DEG, RAD, CIR, ERR
  }
  public final class PhysicalAngle {
    double value;
    AngularUnit unit;
    public PhysicalAngle(double x, AngularUnit au) { value = x; unit = au; }
    public PhysicalAngle(String s, AngularUnit default_unit) {
      String t = s.toLowerCase();
      String u = t;
      if (t.endsWith("deg")) { unit = AngularUnit.DEG; u = t.substring(0,t.length()-3); }
      else if (t.endsWith("rad")) { unit = AngularUnit.RAD; u = t.substring(0,t.length()-3); }
      else if (t.endsWith("circ")) { unit = AngularUnit.CIR; u = t.substring(0,t.length()-4); }
      else { unit = default_unit; }
      try {
        value = PrefixSI.parseDouble(u);
      }
      catch (NumberFormatException nfe) {
        value = Double.NaN;
        unit = AngularUnit.ERR;
      }
    }
    public final double getDeg() { switch(unit) {
      case DEG: return value;
      case RAD: return value*180.0/Math.PI;
      case CIR: return value*360.0;
      default: return Double.NaN;
    } }
    public final double getRad() { switch(unit) {
      case DEG: return value*Math.PI/180.0;
      case RAD: return value;
      case CIR: return value*2*Math.PI;
      default: return Double.NaN;
    } }
    public final double getCir() { switch(unit) {
      case DEG: return value/360.0;
      case RAD: return value/(2*Math.PI);
      case CIR: return value;
      default: return Double.NaN;
    } }
    public final void setDeg(double x) { value = x; unit = AngularUnit.DEG; }
    public final void setRad(double x) { value = x; unit = AngularUnit.RAD; }
    public final void setCir(double x) { value = x; unit = AngularUnit.CIR; }
    public final boolean isError() { return unit == AngularUnit.ERR || Double.isNaN(value); }
    public final PhysicalAngle ensure() { if (isError()) throw new NumberFormatException("Improper angle."); return this; }
  }

  public enum TemporalUnit {
    SEC, MIN, HOUR, DAY, FR, ERR
  }
  public final class PhysicalTime {
    double value;
    TemporalUnit unit;
    public PhysicalTime(double x, TemporalUnit tu) { value = x; unit = tu; }
    public PhysicalTime(String s, TemporalUnit default_unit) {
      String t = s.toLowerCase();
      String u = t;
      if (t.endsWith("s")) { unit = TemporalUnit.SEC; u = t.substring(0,t.length()-1); }
      else if (t.endsWith("sec")) { unit = TemporalUnit.SEC; u = t.substring(0,t.length()-3); }
      else if (t.endsWith("m")) { unit = TemporalUnit.MIN; u = t.substring(0,t.length()-1); }
      else if (t.endsWith("min")) { unit = TemporalUnit.MIN; u = t.substring(0,t.length()-3); }
      else if (t.endsWith("h")) { unit = TemporalUnit.HOUR; u = t.substring(0,t.length()-1); }
      else if (t.endsWith("hr")) { unit = TemporalUnit.HOUR; u = t.substring(0,t.length()-2); }
      else if (t.endsWith("d")) { unit = TemporalUnit.DAY; u = t.substring(0,t.length()-1); }
      else if (t.endsWith("day")) { unit = TemporalUnit.DAY; u = t.substring(0,t.length()-3); }
      else if (t.endsWith("fr")) { unit = TemporalUnit.FR; u = t.substring(0,t.length()-2); }
      else { unit = default_unit; }
      try {
        value = (unit == TemporalUnit.FR) ? Double.parseDouble(u) : PrefixSI.parseDouble(u);
      }
      catch (NumberFormatException nfe) {
        value = Double.NaN;
        unit = TemporalUnit.ERR;
      }
    }
    public final double getSec() { switch(unit) {
      case SEC: return value;
      case MIN: return value*60;
      case HOUR: return value*3600;
      case DAY: return value*86400;
      case FR: return value*(times[times.length-1]-times[0])/(times.length-1);
      default: return Double.NaN;
    } }
    public final double getMin() { switch(unit) {
      case SEC: return value/60;
      case MIN: return value;
      case HOUR: return value*60;
      case DAY: return value*1440;
      case FR: return value*(times[times.length-1]-times[0])/(60.0*(times.length-1));
      default: return Double.NaN;
    } }
    public final double getHour() { switch(unit) {
      case SEC: return value/3600;
      case MIN: return value/60;
      case HOUR: return value;
      case DAY: return value*24;
      case FR: return value*(times[times.length-1]-times[0])/(3600.0*(times.length-1));
      default: return Double.NaN;
    } }
    public final double getDay() { switch(unit) {
      case SEC: return value/86400;
      case MIN: return value/3600;
      case HOUR: return value/60;
      case DAY: return value;
      case FR: return value*(times[times.length-1]-times[0])/(86400.0*(times.length-1));
      default: return Double.NaN;
    } }
    public final double getFr() { switch(unit) {
      case SEC: return value*(times.length-1)/(times[times.length-1]-times[0]);
      case MIN: return value*(60.0*(times.length-1))/(times[times.length-1]-times[0]);
      case HOUR: return value*(3600.0*(times.length-1))/(times[times.length-1]-times[0]);
      case DAY: return value*(86400.0*(times.length-1))/(times[times.length-1]-times[0]);
      case FR: return value;
      default: return Double.NaN;
    } }
    public final void setSec(double x) { value = x; unit = TemporalUnit.SEC; }
    public final void setMin(double x) { value = x; unit = TemporalUnit.MIN; }
    public final void setHour(double x) { value = x; unit = TemporalUnit.HOUR; }
    public final void setDay(double x) { value = x; unit = TemporalUnit.DAY; }
    public final void setFr(double x) { value = x; unit = TemporalUnit.FR; }
    public final boolean isAbsolute() { return unit != TemporalUnit.FR; }
    public final boolean isRelative() { return unit == TemporalUnit.FR; }
    public final boolean isError() { return unit == TemporalUnit.ERR || Double.isNaN(value); }
    public final PhysicalTime ensure() { if (isError()) throw new NumberFormatException("Improper time."); return this; }
  }

  
  
  /* Class methods begin here */
  
  
  public Choreography() { }
  
  
  public void parseInput(String[] args) throws IllegalArgumentException,CustomHelpException
  {
    OptionParser op = OptionParser.create();
    String[] plain_arguments;
    
    Vector<Double> pixelsize_array = new Vector<Double>();
    Vector<String> minmovemm_array = new Vector<String>();
    Vector<String> minmovebody_array = new Vector<String>();
    Vector<Double> speedwin_array = new Vector<Double>();
    Vector<Double> mintime_array = new Vector<Double>();
    Vector<String> minbiased_array = new Vector<String>();
    Vector<Double> starttime_array = new Vector<Double>();
    Vector<Double> stoptime_array = new Vector<Double>();
    Vector<String> prefix_array = new Vector<String>();
    Vector<String> output_array = new Vector<String>();
    Vector<String> outname_array = new Vector<String>();
    Vector<Double> outtime_array = new Vector<Double>();
    Vector<String> header_array = new Vector<String>();
    Vector<String> trigger_array = new Vector<String>();
    Vector<String> worm_id_array = new Vector<String>();
    Vector<String> worm_id2_array = new Vector<String>();
    Vector<String> in_array = new Vector<String>();
    Vector<String> out_array = new Vector<String>();
    Vector<String> plugin_array = new Vector<String>();
    Vector<String> target_array = new Vector<String>();
    
    op.addOption("?","help");
    
    op.addOption("I","interactive");
    op.addOption("no-timecourse");
    op.addOption("no-repeat");
    op.addOption("shadowless");
    op.addOption("body-length-units");
    op.addOption("graph");
    op.addOption("map");
    op.addOption("who");
    op.addOption("nanless");
    op.addOption("ignore-outside-triggers");
    op.addOption("q","quiet");
    op.addOption("S","segment");
    
    op.addDouble("p","pixelsize").setStorage(pixelsize_array);
    
    op.addString("m","minimum-move-mm").setStorage(minmovemm_array);
    op.addString("M","minimum-move-body").setStorage(minmovebody_array);
    op.addString("minimum-biased").setStorage(minbiased_array);
    op.addDouble("t","minimum-time").setStorage(mintime_array);
    
    op.addDouble("from").setStorage(starttime_array);
    op.addDouble("to").setStorage(stoptime_array);
    
    op.addDouble("s","speed-window").setStorage(speedwin_array);
    
    op.addString("prefix").setStorage(prefix_array);

    op.addString("o","output").setStorage(output_array);
    op.addString("O","output-name").setStorage(outname_array);
    op.addDouble("T","output-time").setStorage(outtime_array);
    op.addString("header").setStorage(header_array);
    
    op.addString("trigger").setStorage(trigger_array);
    op.addString("n","id").setStorage(worm_id_array);
    op.addString("N","each-id").setStorage(worm_id2_array);
    
    op.addString("in").setStorage(in_array);
    op.addString("out").setStorage(out_array);
    
    op.addString("plugin").setStorage(plugin_array);
    op.addString("target").setStorage(target_array);
    
    plain_arguments = op.parse(args);
    
    if (op.optionFound("?"))
    {
      for (String s : plain_arguments)
      {
        if (s.equalsIgnoreCase("output"))
        {
          printHelpMessage(true);
          throw new SystemExit(0);
        }
      }
      printHelpMessage(false);
      throw new SystemExit(0);
    }

    interactive_mode = op.optionFound("I");
    quiet_operation = op.optionFound("q");
    write_timecourse = !(op.optionFound("no-timecourse"));
    reject_duplicates = op.optionFound("no-repeat");
    avoid_shadow = op.optionFound("shadowless");
    speed_over_length = op.optionFound("body-length-units");
    tell_who = op.optionFound("who");
    nanless = op.optionFound("nanless");
    view_graph = op.optionFound("graph");
    view_datamap = op.optionFound("map");
    static_trigger_mask = op.optionFound("ignore-outside-triggers");
    segment_path = op.optionFound("S");
    
    if (interactive_mode)
    {
      view_graph = true;
      view_datamap = false;
    }

    if (headless) {
      view_graph = view_datamap = false;
    }
    
    if (pixelsize_array.isEmpty()) mm_per_pixel = default_pixelsize;
    else if (pixelsize_array.lastElement().floatValue() <= 0.0f) throw new IllegalArgumentException("Pixel size must be positive");
    else mm_per_pixel = pixelsize_array.lastElement().floatValue();
    
    if (minmovemm_array.isEmpty()) min_move_mm = new PhysicalLength(0.0, LengthUnit.MM);
    else {
      min_move_mm = new PhysicalLength(minmovemm_array.lastElement(), LengthUnit.MM);
      if (min_move_mm.unit == LengthUnit.ERR) throw new IllegalArgumentException("Invalid movement distance threshold: "+minmovemm_array.lastElement());
      if (min_move_mm.unit == LengthUnit.BL) throw new IllegalArgumentException("Movement distance threshold must not be in relative units; found "+minmovemm_array.lastElement());
      if (min_move_mm.value < 0) min_move_mm.value = 0;
    }
    
    if (minmovebody_array.isEmpty()) min_move_bodylen = new PhysicalLength(0.0, LengthUnit.BL);
    else {
      min_move_bodylen = new PhysicalLength(minmovebody_array.lastElement(), LengthUnit.BL);
      if (min_move_bodylen.unit == LengthUnit.ERR) throw new IllegalArgumentException("Invalid relative movement threshold: "+minmovebody_array.lastElement());
      if (min_move_bodylen.unit != LengthUnit.BL) throw new IllegalArgumentException("Relative movement threshold must be in relative units; found "+minmovebody_array.lastElement());
      if (min_move_bodylen.value < 0) min_move_bodylen.value = 0;
    }
    
    if (minbiased_array.isEmpty()) min_move_directional = new PhysicalLength(-1.0, LengthUnit.PX);
    else {
      min_move_directional = new PhysicalLength(minbiased_array.lastElement(), LengthUnit.MM);
      if (min_move_mm.unit == LengthUnit.ERR) throw new IllegalArgumentException("Invalid directional distance threshold: "+minbiased_array.lastElement());
    }
    
    if (mintime_array.isEmpty() || mintime_array.lastElement().floatValue() <= 0.0f) min_time = 0.0f;
    else min_time = mintime_array.lastElement().floatValue();
    
    if (starttime_array.isEmpty() || starttime_array.lastElement().floatValue() < 0.0f) select_t0 = 0.0f;
    else select_t0 = starttime_array.lastElement().floatValue();
    if (stoptime_array.isEmpty() || stoptime_array.lastElement().floatValue() < 0.0f) select_t1 = 1e20f;
    else select_t1 = stoptime_array.lastElement().floatValue();
    
    if (speedwin_array.isEmpty() || speedwin_array.lastElement().floatValue() <= 0.0f) speed_window = default_speed_window;
    else speed_window = speedwin_array.lastElement().floatValue();
    
    if (outtime_array.isEmpty() || outtime_array.lastElement().floatValue() <= 0.0f) output_time_chunk = 0.0f;
    else output_time_chunk = outtime_array.lastElement().floatValue();

    if (!header_array.isEmpty()) print_header = header_array.lastElement();

    // Target directories need to exist
    for (String s : target_array) {
      File f = new File(s);
      if (f.exists() && f.isDirectory()) { output_directory = f; break; }
    }
    if (target_array.size() > 0 && output_directory==null) {
      throw new IllegalArgumentException(String.format("Tried using specified target director%s exist.",(target_array.size()==1) ? "y but it doesn't" : "ies but none of them"));
    }
    
    // Plugins need to have the plugin name split apart from the plugin's arguments.
    plugininfo = new ArrayList<ComputationInfo>();
    plugloader = (plugin_array.size() > 0) ? new PluginLoader() : null;
    int h = -1;
    for (String s : plugin_array)
    {
      h++;
      String classname;
      String[] classargs;
      int i = s.indexOf("::");
      if (i<0)
      {
        classname = s;
        classargs = new String[0];
      }
      else if (s.length() > i+2)
      {
        classname = s.substring(0,i);
        classargs = s.substring(i+2).split("::",-1);
      }
      else throw new IllegalArgumentException("Missing plugin name.");

      String nickname = "";
      int ati = classname.indexOf("@");
      if (ati >= 0) {
        nickname = classname.substring(0,ati);
        classname = classname.substring(ati+1);
      }
      
      try { loadComputationPlugin(classname, nickname, classargs, true, h); }
      catch (IllegalArgumentException iae) {
        throw new IllegalArgumentException("Invalid argument to plugin " + classname + "\n  Args: " + Arrays.toString(classargs) + "\nError: " + iae);
      }
      catch (IOException ioe) {
        throw new IllegalArgumentException("Could not load plugin " + classname + "\n IO Error: " + ioe);
      }
    }
    plug_map = new PlugMapper();  // The mapper must be instantiated after all plugins are loaded but before any output directives are processed
    plugmods = new LinkedList<CustomOutputModification>();
    for (ComputationInfo ci : plugininfo) {
      if (ci.plugin instanceof CustomOutputModification) plugmods.add((CustomOutputModification)ci.plugin);
    }
    plugsegs = new LinkedList<CustomSegmentation>();
    for (ComputationInfo ci : plugininfo) {
      if (ci.plugin instanceof CustomSegmentation) plugsegs.add((CustomSegmentation)ci.plugin);
    }

    // Check to make sure nicknames are unique
    HashSet<String> plugnicks = new HashSet<String>();
    for (ComputationInfo ci : plugininfo) {
      if (DataSource.interpreter.containsKey(ci.nick)) {
        throw new IllegalArgumentException("Nickname '"+ci.nick+"' is a built-in output type.");
      }
      if (!ci.nick.isEmpty() && plugnicks.contains(ci.nick)) {
        throw new IllegalArgumentException("Another plugin already has nickname '"+ci.nick+"'");
      }
      plugnicks.add(ci.nick);
    }

    
    // Now that plugins have had a chance to give help messages and such, we need a file to continue
    if (plain_arguments.length != 1) throw new IllegalArgumentException("Exactly one filename required");
    
    // Parse regions to include and exclude
    if (in_array.isEmpty()) attend = new Dance.ReceptiveField[0];
    else
    {
      Dance.ReceptiveField drf = null;
      Vector<Dance.ReceptiveField> vdrf = new Vector<Dance.ReceptiveField>();
      for (String s : in_array)
      {
        drf = Dance.ReceptiveField.makeField(s);
        if (drf==null) throw new IllegalArgumentException("Field format must be cx,cy,r or x0,y0,x1,y1.");
        vdrf.add(drf);
      }
      attend = vdrf.toArray( new Dance.ReceptiveField[ vdrf.size() ] );
    }
    if (out_array.isEmpty()) shun = new Dance.ReceptiveField[0];
    else
    {
      Dance.ReceptiveField drf = null;
      Vector<Dance.ReceptiveField> vdrf = new Vector<Dance.ReceptiveField>();
      for (String s : out_array)
      {
        drf = Dance.ReceptiveField.makeField(s);
        if (drf==null) throw new IllegalArgumentException("Field format must be cx,cy,r or x0,y0,x1,y1.");
        vdrf.add(drf);
      }
      shun = vdrf.toArray( new Dance.ReceptiveField[ vdrf.size() ] );
    }

    
    // Parse request for trigger-averaged output
    if (trigger_array.isEmpty()) triggers = null;
    else
    {
      Vector<Triggerer> trig = new Vector<Triggerer>();
      int n = -1;
      float f;
      float duration = 0.0f;
      DataSource ds;
      Vec2F v;
      for (String arg : trigger_array)
      {
        n++;
        String[] csv_tokens = arg.split(",");
        if (csv_tokens.length<2) throw new IllegalArgumentException("--trigger needs at least two arguments (duration and time)");
        try { duration = Float.parseFloat(csv_tokens[0]); }
        catch (IllegalArgumentException iae) { throw new IllegalArgumentException("--trigger should start with a duration (numeric)"); }
        if (duration<0.0f) throw new IllegalArgumentException("--trigger duration should be a positive number");
        
        for (int i=1 ; i<csv_tokens.length ; i++)
        {
          f = Float.NaN;
          try { f = Float.parseFloat(csv_tokens[i]); } catch (IllegalArgumentException iae) { }
          if (!Float.isNaN(f) && f>=0.0) trig.add( new Triggerer(duration,f) );
          else
          {
            String[] colon_tok = csv_tokens[i].split(":",4);
            if (colon_tok.length!=3) throw new IllegalArgumentException("--trigger times should be positive numbers or stim:before:after triples.");
            ds = DataSource.TIME;
            try { ds = DataSource.interpret( colon_tok[0] ); }
            catch (IllegalArgumentException iae){ throw new IllegalArgumentException("--trigger category '" + colon_tok[0] + "' not known."); }
            if (ds!=DataSource.STI1 && ds!=DataSource.STI2 && ds!=DataSource.STI3 && ds!=DataSource.STI4)
            {
              throw new IllegalArgumentException("--trigger stimulus must be tap, puff, stim3, or stim4");
            }
            try
            {
              v = new Vec2F();
              if (colon_tok[1].length()==0) v.x = Float.NaN; else v.x = Float.parseFloat(colon_tok[1]);
              if (colon_tok[2].length()==0) v.y = Float.NaN; else v.y = Float.parseFloat(colon_tok[2]);
            }
            catch (IllegalArgumentException iae) { throw new IllegalArgumentException("--trigger stimulus must be followed by before & after times"); }
            trig.add( new Triggerer(duration,v.x,v.y,ds) );
          }
        }
      }
      triggers = new Triggerer[ trig.size() ];
      n=0;
      for (Triggerer tg : trig) triggers[n++] = tg;
    }
    
    // Parse lists of object IDs, if given
    all_individuals = false;
    if (worm_id_array.isEmpty() && worm_id2_array.isEmpty())
    {
      id_table = null;
      one_by_one = false;
    }
    else
    {
      if (worm_id_array.size()>0 && worm_id2_array.size()>0) throw new IllegalArgumentException("Cannot mix -n and -N; use only one.");
      if (worm_id_array.isEmpty())
      {
        worm_id_array = worm_id2_array;
        one_by_one = true;
      }
      else one_by_one = false;
      
      LinkedList<Integer> id_list = new LinkedList<Integer>();
      int id;
      for (String arg : worm_id_array)
      {
        if (one_by_one && arg.equalsIgnoreCase("all"))
        {
          all_individuals = true;
          break;
        }
        String[] csv_tokens = arg.split(",");
        for (String tok : csv_tokens)
        {
          try { id = Integer.parseInt(tok); }
          catch (IllegalArgumentException iae) { throw new IllegalArgumentException("--id numbers must be integers"); }
          id_list.add(id);
        }
      }
      if (id_list.size()==0) id_table = null;
      else
      {
        id_table = new HashSet<Integer>(id_list.size()*2);  // Be generous with hash table size; not likely to be a memory-hog!
        for (Integer i : id_list) id_table.add(i);
      }
    }
    
    // Output stuff--needs to be last as it depends on other stuff being initialized (e.g. plugins)
    if (prefix_array.isEmpty()) file_prefix = null;
    else file_prefix = prefix_array.lastElement();
    
    if (output_array.isEmpty() && outname_array.size()>0) throw new IllegalArgumentException("-O requires -o");
    
    if (output_array.isEmpty()) { output_names = null; output_requests = null; }
    else if (outname_array.isEmpty())
    {
      output_names = new String[1];
      output_names[0] = "";
    }
    else
    {
      if (outname_array.size()==1)
      {
        output_names = new String[1];
        output_names[0] = "." + outname_array.lastElement();
      }
      else if (outname_array.size()==output_array.size())
      {
        output_names = new String[ outname_array.size() ];
        for (int i=0 ; i<output_names.length ; i++) output_names[i] = "." + outname_array.get(i);
      }
      else throw new IllegalArgumentException("Either have one -O per -o or one -O for all -o's.");
    }
    
    computables = new HashSet<DataSource>();
    for (DataSource ds : DataSource.values()) if (ds.alwaysCompute()) computables.add(ds);

    if (output_array.size() > 0)
    {
      Vector<Vector<DataSpecifier>> outputs = new Vector<Vector<DataSpecifier>>();
      for (String s : output_array) outputs.add( parseOutputSpecification(s) );
      
      if (outname_array.size()==1)
      {
        Vector<DataSpecifier> merged_outputs = new Vector<DataSpecifier>();
        for (Vector<DataSpecifier> vds : outputs) merged_outputs.addAll( vds );
        
        boolean has_time = false;
        for (DataSpecifier ds : merged_outputs)
        {
          computables.add(ds.source);
          if (ds.source == DataSource.TIME) has_time=true;
        }
        
        if (!has_time) merged_outputs.add( 0 , new DataSpecifier(DataSource.TIME,DataMeasure.AVG) );
        
        output_requests = new DataSpecifier[1][];
        output_requests[0] = merged_outputs.toArray( new DataSpecifier[ merged_outputs.size() ] );
      }
      else
      {
        output_requests = new DataSpecifier[ outputs.size() ][];
        for (int i=0 ; i<output_requests.length ; i++)
        {
          boolean has_time = false;
          for (DataSpecifier ds : outputs.get(i))
          { 
            computables.add(ds.source);
            if (ds.source==DataSource.TIME) has_time=true;
          }
          if (!has_time) outputs.get(i).add( 0 , new DataSpecifier(DataSource.TIME,DataMeasure.AVG) );
          output_requests[i] = outputs.get(i).toArray( new DataSpecifier[ outputs.get(i).size() ] );
        }
      }
    }
    
    base_directory = plain_arguments[0];
  }
  
  
  public Vector<DataSpecifier> parseOutputSpecification(String request) throws IllegalArgumentException
  {
    if (request.equals("all")) {
      //request = "ftnNpsSlLwWaAmkbcd1234";
      throw new IllegalArgumentException("The 'all' flag for output is ambiguous and outdated and no longer works.  Please use ftnNpsSlLwWaAmkbcd1234 to get the historical behavior of 'all'.");
    }
    
    Vector<DataSpecifier> spec = new Vector<DataSpecifier>();
    
    boolean has_commas = (request.indexOf(',') != -1);
    boolean has_colon;
    
    if (has_commas)
    {
      String[] columns = request.split(",");
      for (String s : columns)
      {
        if (s==null || s.length()==0) continue;
        String[] ss = s.split("(?<!:):(?!:)");  // "Bug" in indexOf means even if we can split, indexOf returns -1.  So we split regardless.
        boolean custom = false;
        for (ComputationInfo ci : plugininfo) {
          if (ci==null || !ci.mapoutput || ci.nick.isEmpty()) continue;
          if (s.startsWith(ci.nick)) { custom = true; break; }
        }
        if (custom) {
          if (ss.length > 1) spec.add( new DataSpecifier(DataSource.CUST, DataMeasure.interpret(ss[1]), ss[0]) );
          else spec.add( new DataSpecifier(DataSource.CUST, DataMeasure.AVG, s) );
        }
        else if(ss.length > 1)
        {
          int icolcol = ss[0].indexOf("::");
          if (icolcol != -1) spec.add( new DataSpecifier( DataSource.interpret(ss[0].substring(0,icolcol)), DataMeasure.interpret(ss[1]), ss[1].substring(icolcol+2) ) );
          else spec.add(new DataSpecifier(DataSource.interpret(ss[0]), DataMeasure.interpret(ss[1])));
        }
        else {
          int icolcol = s.indexOf("::");
          if (icolcol != -1) spec.add( new DataSpecifier( DataSource.interpret(s.substring(0,icolcol)), DataMeasure.AVG, s.substring(icolcol+2) ) );
          else spec.add(new DataSpecifier(DataSource.interpret(s), DataMeasure.AVG));
        }
        if (spec.lastElement().source==DataSource.CUST) plug_map.registerOutput( spec.lastElement() );
        if (spec.lastElement().measure==DataMeasure.JIT) jittering_sources.add(spec.lastElement().source);
      }
    }
    else
    {
      String c,c2;
      for (int i=0 ; i<request.length() ; i++)
      {
        c = request.substring(i,i+1);
        if (i+1<request.length()) c2 = request.substring(i+1,i+2);
        else c2 = " ";
        if ("^_#-*?~".contains(c2))
        {
          spec.add( new DataSpecifier( DataSource.interpret(c) , DataMeasure.interpret(c2) ) );
          i++;
        }
        else spec.add( new DataSpecifier( DataSource.interpret(c) , DataMeasure.AVG ) );
        if (spec.lastElement().source==DataSource.CUST) plug_map.registerOutput( spec.lastElement() );
        if (spec.lastElement().measure==DataMeasure.JIT) jittering_sources.add(spec.lastElement().source);
      }
    }
    custom = new Statistic[plug_map.out.size()][];
    for (int i=0;i<custom.length;i++) custom[i] = null;

    return spec;
  }
  
  
  public File targetDir() {
    if (output_directory != null) return output_directory;
    else return directory_file;
  }

  
  public void requirePlugins(String[] requirements) throws IllegalArgumentException {
    for (int i=0; i<requirements.length; i++) {
      String name = requirements[i].split("::")[0];
      boolean found = false;
      int j = 0;
      StringBuilder sb = new StringBuilder();
      for (ComputationInfo ci : plugininfo) {
        if (ci.plugin.getClass().getName().equals(name)) {
          found = true;
          sb.append(ci.getClass().getName());
          for (String s : ci.arguments) { sb.append("::"); sb.append(s); }
          requirements[i] = sb.toString();
          break;
        }
        j += 1;
      }
      if (!found) {
        try {
          String[] parts = requirements[i].split("::");
          String[] args = Arrays.copyOfRange(parts, 1, parts.length);
          loadComputationPlugin(name,"",args,false,-1);
        }
        catch (Exception e) {
          String msg = e.getMessage();
          throw new IllegalArgumentException("Required plugin "+name+" could not be called" + ((msg.length()>0) ? (" because: "+e.getMessage()) : "."));
        }
      }
    }
  }

  public void loadComputationPlugin(String classname, String nickname, String arguments[], boolean mapoutput, int index) throws IOException,IllegalArgumentException,CustomHelpException
  {
    if (classname.equalsIgnoreCase("help")) {
      System.out.println("The basic syntax for plugins is");
      System.out.println("  --plugin PluginClassName::arg1::arg2::...::argN");
      System.out.println("The argument string must contain no spaces.");
      System.out.println("To load multiple plugins, use the above command multiple times.");
      System.out.println();
      System.out.println("Note that the .class extension of the file should NOT be given.");
      System.out.println("If the plugin class is not in the execution directory, add");
      System.out.println("that directory to the classpath (java -cp /my/plugin/dir)");
      System.out.println();
      System.out.println("To get usage information for a specific plugin, try one of");
      System.out.println("  --plugin PluginClassName::help");
      System.out.println("  --plugin PluginClassName::?");
      System.out.println();
      System.out.println("Plugins may produce their own output or may provide new output types.");
      System.out.println("If the plugin provides new output types, they are accessed using");
      System.out.println("  -o C  (long form: -o custom,)");
      System.out.println("Repeating the request for C with a different statistic requested will");
      System.out.println("  give the same output again with a different statistic.");
      System.out.println("Requesting C again with the same statistic will give the plugin's next");
      System.out.println("  custom output type.");
      System.out.println("If one plugin runs out of output types, the next plugin will be queried.");
      System.out.println("If all plugins run out of output types, further C's are syntax errors.");
      System.out.println();
      System.out.println("Plugins can be given nicknames by specifying the name (with no spaces)");
      System.out.println("  in front of the name: nick@PluginClassName.  This name can then be");
      System.out.println("  used in long form output requests: -o nick,");
      System.out.println("Nicknamed plugins can also have specific outputs requested by specifying");
      System.out.println("  ::n (a number) after the name:  -o nick::3, would be the 3rd output.");
      System.out.println("  Plugins may also specify descriptive names for the outputs (see");
      System.out.println("  the help for that plugin).");
      System.out.println("Nicknames cannot be used in short form.  Nicknames and nameless plugins");
      System.out.println("  cannot be mixed.");
      throw new CustomHelpException();
    }
    if (plugloader==null) throw new IOException("Failed to load plugin manager.");

    CustomComputation cc = null;
    if (providedPlugins == null || index<0 || index >= providedPlugins.length) {
      Class loaded = null;
      try { loaded = plugloader.loadClass(classname,true); }
      catch (ClassNotFoundException cfne) { throw new IOException("Failed to load plugin.\n" + cfne); }
      if (loaded==null) { throw new IOException("Failed to load plugin " + classname); }

      try { cc = (CustomComputation)loaded.newInstance(); }
      catch (Exception e) { throw new IOException("Could not create plugin " + classname + "\nbecause " + e); }
      if (cc==null) { throw new IOException("Could not create plugin " + classname); }
    }
    else cc = providedPlugins[index];
    
    cc.initialize(arguments,this);
    for (int i = 0; i<plugininfo.size(); i++) {
      ComputationInfo ci = plugininfo.get(i);
      if (ci.plugin.getClass().getName().equals(cc.getClass().getName()) && !ci.mapoutput) {
        while (!plugininfo.get(i).mapoutput) i++;
        throw new IllegalArgumentException(
          "Plugin "+plugininfo.get(i).plugin.getClass().getName()+" requires plugin "+cc.getClass().getName()+"; please change the order so the latter is first!"
        );
      }
    }
    plugininfo.add(new ComputationInfo(cc, classname, nickname, arguments, mapoutput));
  }
  
  public void printHelpMessage(boolean show_output)
  {
    System.out.println(ChoreographyVersion.CURRENT_IDENTIFIER);
    System.out.println("Usage:  java Choreography [options] directory");
    System.out.println("     or java -jar Chore.jar [options] directory");
    
    if (!show_output)
    {
    System.out.println("Options:");
    //                  012345678911234567892123456789312345678941234567895123456789612345678971234567898
    System.out.println("  -?  --help               This message (use -? output for help on output type)");
    System.out.println("      --body-length-units  Speeds are in units of body lengths (default is mm)");
    System.out.println("      --from               Time from which to read data (in seconds, default 0)");
    System.out.println("      --graph              Bring up GUI to graph population data");
    System.out.println("      --header #           Write header for data file with # as comment char");
    System.out.println("  -I (--interactive)       Bring up GUI (same as --graph)");
    System.out.println("      --in                 Only use data points inside specified shape");
    System.out.println("      --ignore-outside-triggers   Ignore all data except near explicit triggers");
    System.out.println("  -m (--minimum-move-mm)   How far an object must move (in mm) to count");
    System.out.println("  -M (--minimum-move-body)   (same thing, except unit is object-lengths)");
    System.out.println("      --minimum-biased     If object travels this far, it's mostly forwards");
    System.out.println("      --map                Use GUI to display the data as a browsable map");
    System.out.println("  -n (--id)                Only use listed object IDs (use commas: -n 1,5,22)");
    System.out.println("  -N (--each-id)           Write one output file for each ID listed");
    System.out.println("      --no-output          Don't write any output");
    System.out.println("      --no-repeat          Remove any frames that appear to be repeated");
    System.out.println("      --out                Data must be outside specified shape.");
    System.out.println("  -o (--output)            Write specified output data (-? output for syntax)");
    System.out.println("  -O (--output-name)       Add an identifier to output");
    System.out.println("  -p (--pixelsize)         Size of one pixel, in mm");
    System.out.println("      --plugin             Use plugin; --plugin help gives generic help");
    System.out.println("      --prefix             Specify data file prefix explicitly");
    System.out.println("  -q (--quiet)             Don't print progress information to console");
    System.out.println("  -s (--speed-window)      Time window (in seconds) to average velocity");
    System.out.println("  -S (--segment)           Shape analysis of path: lines, arcs, etc.");
    System.out.println("      --shadowless         Only count objects after they move a body length");
    System.out.println("      --skip-zeros         Omit timepoints with zero objects found");
    System.out.println("      --spine-from-outline (Re)compute spine more robustly given outline");
    System.out.println("  -t (--minimum-time)      How long an object must last (in seconds) to count");
    System.out.println("  -T (--output-rate)       Time between output data points (in seconds)");
    System.out.println("      --to                 Time after which to ignore data (in seconds)");
    System.out.println("      --target             Place all output in specified directory (must exist)");
    System.out.println("      --trigger            Report a stimulus-triggered average to .trig file");
    System.out.println("      --trig-only          Only write triggered averages, not regular output");
    System.out.println("      --who                Print out object ID numbers that pass criteria");
    }
    System.out.println("Format:");
    if (!show_output)
    {
    //                  012345678911234567892123456789312345678941234567895123456789612345678971234567898
    System.out.println("  directory must contain a MWT .summary file");
    System.out.println("  A .zip file containing the data can be specified instead of the directory.");
    System.out.println("    The corresponding directory will be created for output purposes.");
    System.out.println("  -m,M,p,s,t,--from,--to expect a floating-point value as an argument");
    System.out.println("  -O name turns output from prefix.dat to prefix.name.dat");
    System.out.println("    If only one -O is given, it will change the .pos file name also.");
    System.out.println("    If multiple -O's are given, only .dat files are changed, and there must be");
    System.out.println("      the same number of -o's and -O's (and will correspond in order)");
    System.out.println("  --trigger is followed by the duration of the averaging window (in seconds),");
    System.out.println("    a comma, and then comma-separated list containing either the time at which");
    System.out.println("    to trigger or the tap, puff, stim3, or stim4 keywords followed by a colon,");
    System.out.println("    the time before to take a measurement, a colon, and the time after to");
    System.out.println("    take a measurement.  (Numbers may be left blank; colons are required.)");
    System.out.println("    Multiple trigger statements are okay (each adds more columns to the file).");
    System.out.println("  -n or --id can be entered multiple times, and/or can contain multiple id");
    System.out.println("    numbers; all IDs are accumulated.  Numbers must be separated by commas");
    System.out.println("    with no spaces.  IDs that do not exist or fail criteria are excluded.");
    System.out.println("    The -N or --each-id variant appends a five-digit object ID number to");
    System.out.println("    the prefix, and creates one set of files for each object.");
    System.out.println("    -N all means output separately every object meeting the criteria.");
    System.out.println("    -n and -N are not compatible.  Use only one.");
    System.out.println("  --in and --out should be followed by either a center and radius (circle)");
    System.out.println("    as x,y,r, or two corners of a rectangle as x1,y1,x2,y2.");
    System.out.println("  --minimum-biased can accept units: 20px, 1.5mm, 0.7bl (bl=body lengths)");
    System.out.println("    (default is mm; note no space between number and units)");
    System.out.println("  --header creates a tab-delimited header that starts with whatever string");
    System.out.println("    is specified (be careful to quote spaces if you need them in the header!)");
    }
    else
    {
    //                  012345678911234567892123456789312345678941234567895123456789612345678971234567898
    System.out.println("  -o requires an argument specifying columns (separate long form with commas)");
    System.out.println("    t time -- always the first column unless included again");
    System.out.println("    f frame -- the frame number");
    System.out.println("    D id -- the object ID");
    System.out.println("    n number -- the number of objects tracked");
    System.out.println("    N goodnumber -- the number of objects passing the criteria given");
    System.out.println("    p persistence -- length of time object is tracked");
    System.out.println("    e area");
    System.out.println("    s speed");
    System.out.println("    S angular -- angular speed");
    System.out.println("    l length -- measured along major axis, not curve of object");
    System.out.println("    L rellength -- instantaneous length/average length");
    System.out.println("    w width");
    System.out.println("    W relwidth -- instantaneous width/average width");
    System.out.println("    a aspect -- length/width");
    System.out.println("    A relaspect -- instantaneous aspect/average aspect");
    System.out.println("    m midline -- length measured along the curve of object");
    System.out.println("    M morphwidth -- mean width of body about midline");
    System.out.println("    k kink -- head/tail angle difference from body (in degrees)");
    System.out.println("    b bias -- fractional excess of time spent moving one way");
    System.out.println("    P pathlen -- distance traveled forwards (backwards=negative)");
    System.out.println("    c curve -- average angle (in degrees) between body split into 5 segments");
    System.out.println("    d dir -- consistency of direction of motion");
    System.out.println("    x loc_x -- x coordinate of object (mm)");
    System.out.println("    y loc_y -- y coordinate of object (mm)");
    System.out.println("    u vel_x -- x velocity (mm/sec)");
    System.out.println("    v vel_y -- y velocity (mm/sec)");
    System.out.println("    o orient -- orientation of body (degrees, only guaranteed modulo pi)");
    System.out.println("    r crab -- speed perpendicular to body orientation");
    System.out.println("    C custom -- calls plugin");
    System.out.println("    1 tap -- whether a tap (stimulus 1) has occurred");
    System.out.println("    2 puff -- whether a puff (stimulus 2) has occurred");
    System.out.println("    3 stim3 -- whether the first custom stimulus has occurred");
    System.out.println("    4 stim4 -- whether the second custom stimulus has occurred.");
    System.out.println("      all -- Deprecated, prints error message instead");
    System.out.println("  The output items can be followed by the statistic to report");
    System.out.println("    (default is to output the mean)");
    System.out.println("    ^ :max -- maximum value");
    System.out.println("    _ :min -- minimum value");
    System.out.println("    # :number -- number of items considered in this statistic");
    System.out.println("    - :median -- median value");
    System.out.println("    * :std -- standard deviation");
    System.out.println("      :sem -- standard error");
    System.out.println("      :var -- variance");
    System.out.println("    ? :exists -- 1 if the value exists, 0 otherwise");
    System.out.println("      :p25 -- 25th percentile");
    System.out.println("      :p75 -- 75th percentile");
    System.out.println("      :jitter -- estimate of measurement precision");
    System.out.println("    ~ :skip -- Do not output (primarily for custom in short format)");
    System.out.println("  Long format items need at least one comma (add trailing comma if needed)");
    System.out.println("  In long format, custom can refer to plugin and trace number as follows:");
    System.out.println("    custom::PluginName::2  (for trace 2; numbering starts at 0)");
    System.out.println("    If the same plugin is called multiple times, the numbering for the second");
    System.out.println("    begins after the first is exhausted.");
    }
    System.out.println("Examples:");
    if (show_output)
    {
    //                  012345678911234567892123456789312345678941234567895123456789612345678971234567898
    System.out.println("  -o a_ and -o aspect:min, are the same thing");
    System.out.println("  -o fnww* and -o frame,number,width,width:std also are the same");
    System.out.println("  -o xy will give positions (useful in conjunction with -N option)");
    System.out.println("  -o uv will give velocity vectors (also useful with -N)");
    System.out.println("  -o CCCCC will run through five different plugin-computed quantities, in order");
    System.out.println("    (advancing to the next plugin when the previous has computed all it can)");
    System.out.println("  -o CC*CC* will run through two but compute mean and SD of each.");
    System.out.println("  -o C~C~C will discard two plugin-computed quantities and display the third");
    }
    else
    {
    //                  012345678911234567892123456789312345678941234567895123456789612345678971234567898
    System.out.println("  --trigger 1.0,5,tap:0.25:0.5,750 will average from 5-6 s after");
    System.out.println("    the start of recording, from 1.25 to 0.25 s before each tap, from 0.5 s");
    System.out.println("    to 1.5 s after each tap, and once more from 750-751 s.");
    System.out.println("  --trigger 0.2,tap::0.2 will average from 0.2 to 0.4 seconds after each tap");
    System.out.println("  --trigger 0.5,tap:0: will average from 0.5s before to 0 s before each tap");
    System.out.println("  --in 1,1,100,50 --out 25,25,5 would only take data from an elongated");
    System.out.println("    rectangle with a hole missing from its left side.");    
    }
  }
  
  
  public void findFiles() throws WrongFilesException
  {
    Enumeration zip_entry_list;
    ZipEntry ze;
    directory_zip = null;
    summary_zip = null;
    sitter_zips = null;
    dancer_zips = null;
    
    directory_file = new File(base_directory);
    
    if (!directory_file.exists())  // Maybe we can find a zip file?
    {
      boolean recovered = false;
      if (!directory_file.getName().toLowerCase().endsWith(".zip"))
      {
        directory_file = new File(directory_file.getPath()+".zip");
        recovered = directory_file.exists();
      }
      if (!recovered) throw new WrongFilesException("Cannot find directory " + base_directory);
    }
    
    if (!directory_file.isDirectory())  // Zip file, perhaps?  Load it, or report a mistake.
    {
      if (!directory_file.getName().toLowerCase().endsWith(".zip")) throw new WrongFilesException(base_directory + " is not a directory");
      
      try { directory_zip = new ZipFile(directory_file); }
      catch (IOException ioe) { throw new WrongFilesException(directory_file.getPath() + " could not be opened for reading as a zip file.\n" + ioe.toString()); }
      
      // We only have a zip file, better make ourselves a directory!
      String s = directory_file.getPath();
      directory_file = new File(s.substring(0,s.length()-4));
      if (!directory_file.exists())
      {
        if (!directory_file.mkdir()) throw new WrongFilesException("Could not create output directory " + directory_file.getName());
      }
      else if (!directory_file.isDirectory()) throw new WrongFilesException("Output directory " + directory_file.getName() + " exists but is not a directory.");
    }

    File[] summary_files = null;
    ZipEntry[] summary_zips = null;
    if (directory_file!=null) summary_files = directory_file.listFiles(new SummaryFilter());
    if (summary_files==null || summary_files.length==0)
    {
      // No file in directory.  Maybe we have or can find a .zip file.
      // Look for same name inside current directory or just above it.
      if (directory_zip==null)
      {
        File temp_file = new File(directory_file.getPath() , directory_file.getName()+".zip");
        if (!temp_file.exists()) temp_file = new File(directory_file.getParentFile() , directory_file.getName()+".zip");
        if (temp_file.exists())
        {
          try { directory_zip = new ZipFile(directory_file); }
          catch (IOException ioe) { directory_zip = null; }
        }
      }
      
      // If we didn't find a zip file, we're doomed.
      if (directory_zip==null) throw new WrongFilesException("No .summary file found in " + base_directory);
      
      // If we did, try to find a .summary file in it to save us from doom.
      LinkedList<ZipEntry> llze = new LinkedList<ZipEntry>();
      zip_entry_list = directory_zip.entries();
      while (zip_entry_list.hasMoreElements())
      {
        ze = (ZipEntry)zip_entry_list.nextElement();
        if (ze.getName().endsWith(".summary")) llze.add(ze);
      }
      if (llze.size()==0) throw new WrongFilesException("No .summary file found in " + directory_zip.getName());
      summary_zips = llze.toArray(new ZipEntry[llze.size()]);
      summary_files = null;
    }
    
    // Find the right summary file (either in or out of zip file).
    summary_file = null;
    summary_zip = null;
    if (file_prefix != null)  // Need to match prefix name to the one supplied
    {
      if (summary_files != null)
      {
        for (File f : summary_files)
        {
          if (f.getName().equals(file_prefix + ".summary")) summary_file = f;
        }
      }
      else if (summary_zips != null)
      {
        for (ZipEntry z : summary_zips)
        {
          File temp_file = new File(z.getName());
          if (temp_file.getName().equals(file_prefix + ".summary")) summary_zip = z;
        }
      }
      if (summary_file==null && summary_zip==null) throw new WrongFilesException("No " + file_prefix + ".summary file found in " + base_directory);
    }
    else if (summary_files!=null && summary_files.length==1) summary_file = summary_files[0];
    else if (summary_zips!=null && summary_zips.length==1) summary_zip = summary_zips[0];
    else throw new WrongFilesException("More than one .summary file found in " + base_directory);
    
    // Read the file prefix off of the summary file
    int i = -1;
    if (summary_file != null) i = summary_file.getName().lastIndexOf(".summary");
    else if (summary_zip != null) i = summary_zip.getName().lastIndexOf(".summary");
    if (i<0) throw new WrongFilesException("Summary file does not end in .summary");
    if (summary_file != null) file_prefix = summary_file.getName().substring(0,i);
    else
    {
      File temp_file = new File(summary_zip.getName());
      i = temp_file.getName().lastIndexOf(".summary");
      file_prefix = temp_file.getName().substring(0,i);
    }
    
    // Find dancer and sitter files
    FilenameFilter sit_filter = new SitterFilter(file_prefix);
    FilenameFilter dance_filter = new DancerFilter(file_prefix);
    FilenameFilter png_filter = new PngFilter(file_prefix);
    FilenameFilter png_set_filter = new PngSetFilter(file_prefix);
    if (summary_file != null)
    {
      sitter_files = directory_file.listFiles(sit_filter);
      dancer_files = directory_file.listFiles(dance_filter);
      File[] png_files = directory_file.listFiles(png_filter);
      png_file = (png_files==null || png_files.length<1) ? null : png_files[0];
      png_set_files = directory_file.listFiles(png_set_filter);
      sitter_zips = null;
      dancer_zips = null;
      png_zip = null;
      png_zip_set = new ZipEntry[0];
    }
    else
    {
      LinkedList<ZipEntry> llze_sit = new LinkedList<ZipEntry>();
      LinkedList<ZipEntry> llze_dance = new LinkedList<ZipEntry>();
      LinkedList<ZipEntry> llze_png = new LinkedList<ZipEntry>();
      png_zip = null;
      File temp_file;
      zip_entry_list = directory_zip.entries();
      while (zip_entry_list.hasMoreElements())
      {
        ze = (ZipEntry)zip_entry_list.nextElement();
        temp_file = new File(ze.getName());
        if (sit_filter.accept(temp_file.getParentFile(),temp_file.getName())) llze_sit.add(ze);
        if (dance_filter.accept(temp_file.getParentFile(),temp_file.getName())) llze_dance.add(ze);
        if (png_zip==null && png_filter.accept(temp_file.getParentFile(),temp_file.getName())) png_zip = ze;
        if (png_set_filter.accept(temp_file.getParentFile(),temp_file.getName())) llze_png.add(ze);
      }
      sitter_files = null;
      dancer_files = null;
      png_file = null;
      png_set_files = null;
      sitter_zips = llze_sit.toArray(new ZipEntry[llze_sit.size()]);
      dancer_zips = llze_dance.toArray(new ZipEntry[llze_dance.size()]);
      png_zip_set = llze_png.toArray(new ZipEntry[llze_png.size()]);
    }
    
    if (!quiet_operation)
    {
      System.out.println("Reading files: 1 summary, " + 
        ( (sitter_files!=null) ? sitter_files.length : sitter_zips.length ) + " reference, " +
        ( (dancer_files!=null) ? dancer_files.length : dancer_zips.length ) + " object.");
    }
    
    if (dancer_files!=null) for (File f : dancer_files) if (f.getName().endsWith("blobs")) blob_in_blobs = true;
    if (dancer_zips!=null) for (ZipEntry z : dancer_zips) if (z.getName().endsWith("blobs")) blob_in_blobs = true;
  }
  
  
  public boolean computeAndCheck(Dance candidate)
  {
    float f;
    
    if (!candidate.hasData()) return false;  // Empty file
    
    if (candidate.totalT() + candidate.ignored_dt < min_time) return false;
    //if (candidate.has_holes) return false;
    
    f = Math.max( candidate.maximumExcursion() , candidate.ignored_travel );
    if (f < min_move_mm.getPx(candidate)) return false;
    
    boolean anything_left = candidate.calcBasicStatistics(avoid_shadow);
    if (!anything_left) return false;
    if (f < min_move_bodylen.getPx(candidate)) return false;
    if (candidate.body_length.average == 0.0f) return false;
    
    for (ComputationInfo ci : plugininfo) if (!ci.plugin.validateDancer(candidate)) return false;
    
    return true;
  }
  
  public void takeAttendance()
  {
    if (dances!=null)
    {
      attendance = new Vector<LinkedList<Dance>>( frames.length );
      for (int i=0 ; i<frames.length ; i++) attendance.add(new LinkedList<Dance>());
      for (Dance d : dances)
      {
        if (d==null) continue;
        for (int i = d.first_frame ; i <= d.last_frame ; i++)
        {
          attendance.get(i).add(d);
        }
      }
    }
  }

  public float minTravelPx(Dance d) {
    if (min_move_directional.value < 0) return (float)Math.max(min_move_bodylen.getPx(d), min_move_mm.getPx(d));
    else return (float)min_move_directional.getPx(d);
  }
  
  public void loadSummaryToArrays(LinkedList<FrameMap> valid_frames,LinkedList<SummaryLine> summaries) {
    valid = new FrameMap[ valid_frames.size() ];
    int i = 0;
    for (FrameMap fm : valid_frames) valid[i++] = fm;
    frames = new int[ summaries.size() ];
    times = new float[ summaries.size() ];
    numbers = new int[ summaries.size() ];
    events = new int[ summaries.size() ][];
    i = 0;
    for (SummaryLine sl : summaries)
    {
      frames[i] = sl.frame;
      times[i] = sl.time;
      numbers[i] = sl.number;
      if (sl.events==null || sl.events.size()==0) events[i] = null;
      else
      {
        events[i] = new int[ sl.events.size() ];
        int j = 0;
        for (Integer ii : sl.events) events[i][j++] = ii.intValue();
      }
      if (sl.ancestry!=null) sl.ancestry.frame = i;
      i++;
    }
  }
  public void loadTriggerTimes() {
    if (triggers!=null)
    {
      int i;
      Vector<Vec2F> trig_times = new Vector<Vec2F>();
      for (Triggerer tr : triggers)
      {
        if (tr.source==null) trig_times.add( new Vec2F(tr.time1 , tr.time1+tr.dt) );
        else
        {
          i = -1;
          for (int[] ev : events)
          {
            i++;
            if (ev==null || ev.length==0) continue;
            for (int ee : ev)
            {
              if ((ee==1 && tr.source==DataSource.STI1) || (ee==2 && tr.source==DataSource.STI2) 
                  || (ee==3 && tr.source==DataSource.STI3) || (ee==4 && tr.source==DataSource.STI4))
              {
                if (!Float.isNaN(tr.time1)) trig_times.add( new Vec2F( times[i] - tr.time1 - tr.dt , times[i] - tr.time1 ) );
                if (!Float.isNaN(tr.time2)) trig_times.add( new Vec2F( times[i] + tr.time2 , times[i] + tr.time2 + tr.dt ) );
              }
            }
          }
        }
      }
      if (trig_times.isEmpty()) trigger_start = trigger_end = null;
      else
      {
        trigger_start = new float[ trig_times.size() ];
        trigger_end = new float[ trig_times.size() ];
        i = -1;
        for (Vec2F v : trig_times) { i++; trigger_start[i] = v.x; trigger_end[i] = v.y; }
      }
    }
    else trigger_start = trigger_end = null;
  }
  public void loadGeneologyTable(LinkedList<SummaryLine> summaries,int n_dancers) {
    geneology = new HashMap<Integer , LinkedList<Ancestry>>( n_dancers );
    LinkedList<Ancestry> lla;
    int i = 0;
    for (SummaryLine sl : summaries)
    {
      if (sl.ancestry==null) continue;
      for (Vec2I orifate : sl.ancestry.orifates)
      {
        lla = geneology.get( orifate.x );  // x = origin
        if (lla == null) { lla = new LinkedList<Ancestry>(); geneology.put(orifate.x , lla); }
        if (!lla.contains(sl.ancestry)) lla.add(sl.ancestry);
        lla = geneology.get( orifate.y );  // y = fate
        if (lla == null) { lla = new LinkedList<Ancestry>(); geneology.put(orifate.y , lla); }
        if (!lla.contains(sl.ancestry)) lla.add(sl.ancestry);
      }
    }
  }
  public void loadData(String out_name) throws LoadDataException
  {
    // First read summary file
    BufferedReader summary_data;
    if (summary_file!=null)
    {
      try { summary_data = new BufferedReader( new InputStreamReader( new FileInputStream( summary_file ) ) ); }
      catch (IOException ioe) { throw new LoadDataException("Cannot open file " + summary_file.getPath()); }
    }
    else
    {
      try { summary_data = new BufferedReader(new InputStreamReader(directory_zip.getInputStream(summary_zip))); }
      catch (IOException ioe) { throw new LoadDataException("Cannot open " + summary_zip.getName() + " in " + directory_zip.getName()); }
    }
    
    int i,j;
    int n_lines;
    String input_line;
    String[] tokens;
    SummaryLine summary = null;
    LinkedList<SummaryLine> summaries = new LinkedList<SummaryLine>();
    LinkedList<FrameMap> valid_frames = new LinkedList<FrameMap>();
    dancer_multi_list = new LinkedList<MultiFileInfo>();

    
    // Set up for masking out data at certain times
    int n_triggers = 0;
    if (triggers!=null) for (Triggerer tr : triggers) if (tr.source==null) n_triggers++;
    Triggerer windows[] = new Triggerer[n_triggers];
    for (i=0 ; i<windows.length ; i++) windows[i] = null;
    i = 0;
    if (triggers!=null) for (Triggerer tr : triggers) if (tr.source==null) windows[i++] = tr;
    Arrays.sort(windows);
    LinkedList<Vec2D> window_list = new LinkedList<Vec2D>();
    for (Triggerer tr : windows) {
      if (window_list.size()==0) window_list.add( new Vec2D( tr.time1 - 4.0*speed_window , tr.time1+tr.dt+4.0*speed_window ) );
      else if (window_list.getLast().y >= tr.time1 - 4.0*speed_window) window_list.getLast().y = tr.time1+tr.dt+4.0*speed_window;
      else window_list.add( new Vec2D( tr.time1 - 4.0*speed_window , tr.time1+tr.dt+4.0*speed_window ) );
    }
    Vec2D[] window_array = window_list.toArray( new Vec2D[window_list.size()] );
    
    // Time for file I/O
    n_lines = 0;
    try
    {
      String[] old_tokens = null;
      int n_valid = 0;
      while (true) // Read summary data; break from loop when done
      {
        boolean is_duplicate = false;
        try { input_line = summary_data.readLine(); }
        catch (IOException ioe) { throw new LoadDataException("Unable to access file " + summary_file.getPath()); }
        
        if (input_line==null) break;  // Out of input
        
        n_lines++;
        if (input_line.length()==0) continue;  // Ignore blank lines
        if (input_line.startsWith("#")) continue; // Comment character
        
        tokens = input_line.split("\\s+");
        
        if (tokens.length < 2)
        {
          try { summary_data.close(); } catch (IOException ioe) { }
          throw new LoadDataException("Too few tokens on line " + n_lines + " of " + summary_file.getPath());
        }
        
        if (reject_duplicates && summary!=null && summary.isSame(tokens)) is_duplicate = true;
        else is_duplicate = false;
        
        if (!is_duplicate)
        {
          if (summary != null) summary.completeParse();
          summary = new SummaryLine();
        }
        
        try
        {
          if (is_duplicate) summary.duplicateParse(tokens);
          else
          {
            summary.initialParse(tokens);
            if (summary.time < select_t0 || summary.time > select_t1)
            {
              valid_frames.add( new FrameMap(summary.frame,-1,false) );
              continue;   // We're ignoring this data, read next line
            }
            else if (static_trigger_mask)
            {
              int i0 = 0;
              int i1 = window_array.length-1;
              while (i0+1 < i1)
              {
                i = (i0+i1)/2;
                if (window_array[i].x > summary.time) i1 = i;
                else i0 = i;
              }
              if (! ((window_array[i0].x <= summary.time && window_array[i0].y >= summary.time) ||
                     (window_array[i1].x <= summary.time && window_array[i1].y >= summary.time)) )
              {
                valid_frames.add( new FrameMap(summary.frame,-1,false) );
                continue;  // Data not close to a trigger, and we're ignoring those
              }
            }
            valid_frames.add( new FrameMap(summary.frame,n_valid++,true) );
            summaries.add(summary);
          }
        
          summary.extraParse();
        }
        catch (NumberFormatException nfe) { throw new LoadDataException("Error in parsing number on line " + n_lines); }
      }
    }
    catch (LoadDataException lde) { throw lde; }
    finally
    {
      if (summary!=null) summary.completeParse();
      try { summary_data.close(); } catch(IOException ioe) { } // If we can't close it, don't worry about it
    }
    
    // Put the summary data in arrays (pretty easy)
    loadSummaryToArrays(valid_frames,summaries);
    
    // Convert any trigger requests to actual times now that we can find them
    loadTriggerTimes();
    
    // May need to know how many sitters and dancers we expect
    int n_sitters = (sitter_files!=null) ? sitter_files.length : ( (sitter_zips!=null) ? sitter_zips.length : 0);
    int n_dancers = (dancer_files!=null) ? dancer_files.length : ( (dancer_zips!=null) ? dancer_zips.length : 0);
    if (blob_in_blobs) n_dancers *= DANCERS_PER_FILE;
    
    // Object fates need to be looked up by ID, so stuff them into a hash map by ID
    loadGeneologyTable(summaries,n_dancers);

    if (!quiet_operation) System.out.println("  Summary file has " + frames.length + " data points.");
    
    // Now read files for each reference object and moving object
    int id;
    String s;
    InputStream is = null;
    BufferedReader br = null;
    if (n_sitters == 0) refs=null;
    else
    {
      refs = new Dance[ n_sitters ];
      for (i=0 ; i<n_sitters ; i++)
      {
        s = (sitter_files!=null) ? sitter_files[i].getName() : sitter_zips[i].getName();
        s = s.substring( s.lastIndexOf('_')+1 , s.lastIndexOf('.') );
        try { id = Integer.parseInt(s); }
        catch (NumberFormatException nfe) { throw new LoadDataException("Can't read ID number of " + s); }
        
        try { is = (sitter_files!=null) ? new FileInputStream(sitter_files[i]) : directory_zip.getInputStream(sitter_zips[i]); }
        catch (IOException ioe) { throw new LoadDataException("Can't read file " + s); }
        
        refs[i] = new Dance(id, this, attend, shun);
        try { br = new BufferedReader(new InputStreamReader(is)); refs[i].readInputStream( br , valid ); }
        catch (Dance.DancerFileException d_dfe) { throw new LoadDataException("Error reading " + s + "\n  " + d_dfe); }
        
        try { is.close(); } catch (IOException ioe) {}  // Don't worry if we can't close the file
      }
    }
    int good_dancer_count = 0;
    if (n_dancers==0) dances = new Dance[0];
    else if (!blob_in_blobs)
    {
      boolean good;
      
      dances = new Dance[ n_dancers ];
      for (i=0 ; i<n_dancers ; i++)
      {
        s = (dancer_files!=null) ? dancer_files[i].getName() : dancer_zips[i].getName();
        s = s.substring( s.lastIndexOf('_')+1 , s.lastIndexOf('.') );
        try { id = Integer.parseInt(s); }
        catch (NumberFormatException nfe) { throw new LoadDataException("Can't read ID number of " + s); }
        
        if (id_table!=null && !id_table.contains(id))
        {
          dances[i] = null;
          continue;
        }
        
        try { is = (dancer_files!=null) ? new FileInputStream(dancer_files[i]) : directory_zip.getInputStream(dancer_zips[i]); }
        catch (IOException ioe) { throw new LoadDataException("Can't read file " + s); }
        
        dances[i] = new Dance(id, this, attend, shun);
        try { br = new BufferedReader(new InputStreamReader(is)); dances[i].readInputStream( br , valid ); }
        catch (Dance.DancerFileException d_dfe) { throw new LoadDataException("Error reading " + s + "\n  " + d_dfe); }
        
        try { is.close(); } catch (IOException ioe) {}  // Don't worry if we can't close the file

        good = computeAndCheck( dances[i] );
        if (!good) dances[i] = null;
        else
        {
          dances[i].findOriginsFates( geneology );
          good_dancer_count++;
        }
      }
    }
    else {
      LinkedList<Dance> dance_list = new LinkedList<Dance>();
      Dance one_dance;
      int n_files = (dancer_files==null) ? dancer_zips.length : dancer_files.length;
      
      for (i=0 ; i<n_files ; i++) {
        int cr_count = 1;
        boolean found_eol = false;
        char c;
        String id_line;
        String f_name = null;
        try { 
          if (dancer_files==null) { f_name = dancer_zips[i].getName(); is = directory_zip.getInputStream(dancer_zips[i]); }
          else { f_name = dancer_files[i].getName(); is = new FileInputStream(dancer_files[i]); }
        }
        catch (IOException ioe) { throw new LoadDataException("Can't read file " + f_name); }
        try { 
          br = new BufferedReader(new InputStreamReader(is));
          id_line = br.readLine();
        }
        catch (IOException ioe) { throw new LoadDataException("Can't find data in " + f_name); }
        if (id_line.length()<3 || id_line.charAt(0) != '%') throw new LoadDataException("Malformed blobs file " + f_name);
        while (id_line != null && id_line.length()>0) {
          s = id_line.substring(2);
          try { id = Integer.parseInt(s); }
          catch (NumberFormatException nfe) { throw new LoadDataException("In " + f_name + " can't read ID: '" + s + "'"); }
          one_dance = new Dance(id, this, attend, shun);
          try { id_line = one_dance.readInputStream(br,valid); }
          catch (Dance.DancerFileException d_dfe) { throw new LoadDataException("Error reading " + id + " from " + f_name + "\n  " + d_dfe); }
          if (id_table!=null && !id_table.contains(id)) one_dance=null;  // Have to read it and then throw it away to advance file
          else if (!computeAndCheck(one_dance)) one_dance=null;
          else {
            one_dance.findOriginsFates(geneology);
            good_dancer_count++;
            dance_list.add(one_dance);
          }
        }
        
        try { is.close(); } catch (IOException ioe) {}  // Don't worry if we can't close the file        
      }
      
      n_dancers = 0;
      for (Dance d : dance_list) if (n_dancers <= d.ID) n_dancers = d.ID+1;
      dances = new Dance[n_dancers];
      for (i=0 ; i<dances.length ; i++) dances[i] = null;
      for (Dance d : dance_list) dances[d.ID] = d;
    }
    
    // Note which dancers were present in which timepoint
    takeAttendance();    
    if (!quiet_operation) System.out.println("  " + good_dancer_count + " out of " + ((dances==null)?0:dances.length) + " objects meet criteria.");
    
    // Everyone who is valid is now loaded; now calculate position error statistics and let everyone know about it
    global_position_noise = new Fitter();
    for (Dance d : dances) {
      if (d==null) continue;
      global_position_noise.addL( d.body_area.average , d.noise_estimate.average );
    }
    if (global_position_noise.n > 2) global_position_noise.line.fit();
    for (Dance d : dances) {
      if (d==null) continue;
      d.global_position_noise = global_position_noise;
      //System.out.println(d.body_area.average + " " + d.noise_estimate.average + " " + d.positionNoiseEstimate());
    }
    
    // If they asked for worm numbers, tell them here
    if (tell_who)
    {
      String spaces = "  ";
      if (!quiet_operation) System.out.println("Worm IDs meeting criteria:");
      else spaces = "";
      
      for (Dance d : dances)
      {
        if (d==null) continue;
        System.out.println(spaces + d.ID);
      }
    }

    // Finally count object IDs
    obid = new float[frames.length];
    int[] denom = new int[frames.length];
    for (Dance d : dances) {
      if (d==null) continue;
      for (i=d.first_frame; i<d.last_frame; i++) {
        obid[i] += d.ID;
        denom[i]++;
      }
    }
    for (i=0; i<obid.length; i++) {
      if (denom[i]>0) obid[i] /= denom[i];
      else obid[i] = Float.NaN;
    }
  }
  
  
  public float[] recomputeOnlineStatistics()
  {
    int i;
    
    // Count number of objects that pass our criteria
    int max_good = 0;
    good_number = new int[ frames.length ];
    for (i=0 ; i<good_number.length ; i++) good_number[i] = 0;
    for (Dance d : dances)
    {
      if (d==null) continue;
      for (int j=0 ; j<d.centroid.length ; j++)
      {
        if (d.centroid[j]!=null && d.loc_okay(d.centroid[j])) good_number[d.first_frame + j]++;
      }
    }
    for (i=0 ; i<attendance.size() ; i++) if (max_good < attendance.get(i).size()) max_good = attendance.get(i).size();
    
    int n;
    float f;
    float A;
    float[] data = new float[ max_good ];
    
    // How big are they?  (Note--may be some spurious zeros, just throw them out.)
    for (Dance d : dances) if (d!=null) d.quantityIsArea( jittering_sources.contains(DataSource.AREA) );
    area = new Statistic[ frames.length ];
    computeDataSkipJunk(data,area);
    
    // How long do they last, on average?
    persistence = new Statistic[ frames.length ];
    for (i=0 ; i<frames.length ; i++)
    {
      n = 0;
      for (Dance d : attendance.get(i))
      {
        if (d==null || d.centroid[i-d.first_frame]==null || !d.loc_okay(d.centroid[i-d.first_frame])) continue;
        data[n++] = d.totalT();
      }
      persistence[i] = new Statistic(data,0,n);
    }
    
    // Which way do they go?
    if (segment_path) {
      for (Dance d : dances) if (d!=null) d.findSegmentation();
    }
    if (!computables.contains(DataSource.BIAS)) bias = null;
    else
    {
      for (Dance d : dances) {
        if (d==null) continue;
        d.quantityIsBias(times,speed_window,minTravelPx(d),false);
      }
      bias = new Statistic[ frames.length ];
      computeDataSkipJunk(data,bias);
    }
    if (!computables.contains(DataSource.PATH)) pathlen = null;
    else
    {
      for (Dance d : dances) {
        if (d==null) continue;
        d.quantityIsPath(times,speed_window,minTravelPx(d));
      }
      pathlen = new Statistic[ frames.length ];
      computeDataSkipJunk(data,pathlen);
    }
    if (!computables.contains(DataSource.DIRC)) dir_change = null;
    else
    {
      for (Dance d : dances) if (d!=null) d.quantityIsDirectionChange(times,speed_window);
      dir_change = new Statistic[ frames.length ];
      computeDataSkipJunk(data,dir_change);
    }
    
    // How fast do they go?
    if (!computables.contains(DataSource.SPED)) speed = null;
    else
    {
      for (Dance d : dances) if (d!=null) d.quantityIsSpeed(times,speed_window,speed_over_length,jittering_sources.contains(DataSource.SPED));
      speed = new Statistic[ frames.length ];
      computeDataSkipJunk(data,speed);
    }
    
    // How fast do they turn?
    if (!computables.contains(DataSource.ASPD)) angular_speed = null;
    else
    {
      for (Dance d : dances) if (d!=null) d.quantityIsAngularSpeed(times,speed_window,jittering_sources.contains(DataSource.ASPD));
      angular_speed = new Statistic[ frames.length ];
      computeDataSkipJunk(data,angular_speed);
    }
    
    // What size and shape are they?
    if (computables.contains(DataSource.LENG) || computables.contains(DataSource.RLEN)) {
      for (Dance d : dances) if (d!=null) d.quantityIsLength(jittering_sources.contains(DataSource.LENG) || jittering_sources.contains(DataSource.RLEN));
      if (computables.contains(DataSource.LENG)) {
        length = new Statistic[ frames.length ];
        computeDataSkipJunk(data,length);
      }
      else length = null;
      if (computables.contains(DataSource.RLEN)) {
        for (Dance d : dances) if (d!=null) d.quantityMult( (d.body_length.average==0.0f) ? 0.0f : 1.0f/(float)d.body_length.average );        
        rel_length = new Statistic[ frames.length ];
        computeDataSkipJunk(data,rel_length);
      }
      else rel_length = null;
    }
    else length = rel_length = null;
    if (computables.contains(DataSource.WIDT) || computables.contains(DataSource.RWID)) {
      for (Dance d : dances) if (d!=null) d.quantityIsWidth(jittering_sources.contains(DataSource.WIDT) || jittering_sources.contains(DataSource.RWID));
      if (computables.contains(DataSource.WIDT)) {
        width = new Statistic[ frames.length ];
        computeDataSkipJunk(data,width);
      }
      else width = null;
      if (computables.contains(DataSource.RWID)) {
        for (Dance d : dances) if (d!=null) d.quantityMult( (d.body_width.average==0.0f) ? 0.0f : 1.0f/(float)d.body_width.average );        
        rel_length = new Statistic[ frames.length ];
        computeDataSkipJunk(data,rel_width);
      }
      else rel_width = null;
    }
    else width = rel_width = null;
    if (computables.contains(DataSource.ASPC) || computables.contains(DataSource.RASP)) {
      for (Dance d : dances) if (d!=null) d.quantityIsAspect(jittering_sources.contains(DataSource.ASPC) || jittering_sources.contains(DataSource.RASP));
      if (computables.contains(DataSource.ASPC)) {
        aspect = new Statistic[ frames.length ];
        computeDataSkipJunk(data,aspect);
      }
      else length = null;
      if (computables.contains(DataSource.RASP)) {
        for (Dance d : dances) if (d!=null) d.quantityMult( (d.body_aspect.average==0.0f) ? 0.0f : 1.0f/(float)d.body_aspect.average );        
        rel_aspect = new Statistic[ frames.length ];
        computeDataSkipJunk(data,rel_aspect);
      }
      else rel_aspect = null;
    }
    else aspect = rel_aspect = null;
    
    // How long are they as drawn along the curve of the object?
    if (computables.contains(DataSource.MIDL))
    {
      for (Dance d : dances) if (d!=null) d.quantityIsMidline(jittering_sources.contains(DataSource.MIDL));
      spine_length = new Statistic[ frames.length ];
      computeDataSkipJunk(data,spine_length);
    }
    else spine_length = null;
    
    if (computables.contains(DataSource.OUTW))
    {
      for (Dance d : dances) if (d!=null) d.quantityIsOutlineWidth(jittering_sources.contains(DataSource.OUTW));
      spine_width = new Statistic[ frames.length ];
      computeDataSkipJunk(data,spine_width);
    }
    else spine_width = null;
    
    // How kinked are their heads or tails?
    if (!computables.contains(DataSource.KINK)) end_wiggle = null;
    else
    {
      for (Dance d : dances) if (d!=null) d.quantityIsKink(jittering_sources.contains(DataSource.KINK));
      end_wiggle = new Statistic[ frames.length ];
      computeDataSkipJunk(data,end_wiggle);
    }
    
    // How straight is the whole body?
    if (!computables.contains(DataSource.CURV)) curve = null;
    else
    {
      for (Dance d : dances) if (d!=null) d.quantityIsCurve(jittering_sources.contains(DataSource.CURV));
      curve = new Statistic[ frames.length ];
      computeDataSkipJunk(data,curve);
    }
    
    // Location X coordinate
    if (!computables.contains(DataSource.LOCX)) loc_x = null;
    else {
      for (Dance d : dances) if (d!=null) d.quantityIsX(jittering_sources.contains(DataSource.LOCX));
      loc_x = new Statistic[ frames.length ];
      computeDataSkipJunk(data,loc_x);
    }

    // Location Y coordinate
    if (!computables.contains(DataSource.LOCY)) loc_y = null;
    else {
      for (Dance d : dances) if (d!=null) d.quantityIsY(jittering_sources.contains(DataSource.LOCY));
      loc_y = new Statistic[ frames.length ];
      computeDataSkipJunk(data,loc_y);
    }
    
    // Velocity X coordinate
    if (!computables.contains(DataSource.VELX)) vel_x = null;
    else {
      for (Dance d : dances) if (d!=null) d.quantityIsVx(times,speed_window,speed_over_length,jittering_sources.contains(DataSource.VELX));
      vel_x = new Statistic[ frames.length ];
      computeDataSkipJunk(data,vel_x);
    }
    
    // Velocity Y coordinate
    if (!computables.contains(DataSource.VELY)) vel_y = null;
    else {
      for (Dance d : dances) if (d!=null) d.quantityIsVy(times,speed_window,speed_over_length,jittering_sources.contains(DataSource.VELY));
      vel_y = new Statistic[ frames.length ];
      computeDataSkipJunk(data,vel_y);
    }
    
    // Orientation
    if (!computables.contains(DataSource.ORNT)) orient = null;
    else {
      for (Dance d : dances) if (d!=null) d.quantityIsTheta(jittering_sources.contains(DataSource.ORNT));
      orient = new Statistic[ frames.length ];
      computeDataSkipJunk(data,orient);
    }
    
    // Crab speed
    if (!computables.contains(DataSource.CRAB)) crab = null;
    else {
      for (Dance d : dances) if (d!=null) d.quantityIsCrab(times,speed_window,speed_over_length,jittering_sources.contains(DataSource.ORNT));
      crab = new Statistic[ frames.length ];
      computeDataSkipJunk(data,crab);
    }
    
    // Random weird testing
    if (!computables.contains(DataSource.QXFW)) qxfw = null;
    else {
      for (Dance d : dances) if (d!=null) d.quantityIsQxfw(this,jittering_sources.contains(DataSource.QXFW));
      qxfw = new Statistic[ frames.length ];
      computeDataSkipJunk(data,qxfw);
    }

    return data;
  }
  
   public void recomputeCustomOnlineStatistics(float[] data) {
    // Custom via plugins
    if (!computables.contains(DataSource.CUST)) custom = null;
    else {
      for (int i=0 ; i<custom.length ; i++) {
        custom[i] = new Statistic[ frames.length ];
        for (Dance d : dances) {
          if (d==null) continue;
          plugininfo.get(plug_map.out.get(i).plugnum).plugin.computeDancerQuantity(d , plug_map.out.get(i).which);
          d.quantityAlreadyIsCustom(i,jittering_sources.contains(DataSource.CUST));
        }
        computeDataSkipJunk(data,custom[i]);
      }
    }
  }

  public Style[] recomputeSegmentation(Dance d, Style[] segmentation, double credibleDistSq) {
    for (CustomSegmentation cs : plugsegs) {
      Style[] fixed = cs.resegment(d, segmentation, credibleDistSq);
      if (fixed != null) segmentation = fixed;
    }
    return segmentation;
  }
 
  public void computeDataSkipJunk(float[] data,Statistic[] answer)
  {
    int n;
    for (int i=0 ; i<frames.length ; i++)
    {
      n = 0;
      float jitsum = 0.0f;
      for (Dance d : attendance.get(i))
      {
        if ( Float.isNaN(d.quantity[ i - d.first_frame ]) ) continue;  // Some data needed for the calc was missing, skip it
        data[n++] = d.quantity[ i - d.first_frame ];
        jitsum += d.loaded_jitter;
      }
      answer[i] = new Statistic(data,0,n);
      answer[i].jitter = jitsum / Math.max(1.0f , answer[i].n);
    }
  }

  private String eatAt(String s) {
    int i = s.indexOf('@');
    if (i < 0) return s;
    else return s.substring(i);
  }
  
  // Returns the multiplier used for output data
  public double loadDancerWithData(Dance d,DataSpecifier ds)
  {
    if (segment_path && d.segmentation==null) d.findSegmentation();
    
    double multiplier = 1.0;
    try {
      switch (ds.measure)
      {
        case NUM:
        case ONE: d.quantityIs(1.0f); return multiplier;
        case VAR:
        case SEM:
        case STD: d.quantityIs(0.0f); return multiplier;
        default:
      }
      boolean jitty = jittering_sources.contains(ds.source);
      switch (ds.source)
      {
        case TIME: d.quantityIsTime(times,jitty); break;
        case FNUM: d.quantityIsFrame(); break;
        case OBID: d.quantityIs(d.ID); break;
        case NUMB: d.quantityIs(1.0f); break;
        case GOOD: d.quantityIs(1.0f); break;
        case AREA: d.quantityIsArea(jitty); multiplier = mm_per_pixel*mm_per_pixel; break;
        case PERS: d.quantityIs( times[d.last_frame] - times[d.first_frame] ); break;
        case SPED: d.quantityIsSpeed(times,speed_window,speed_over_length,jitty); if (!speed_over_length) { multiplier = mm_per_pixel; } break;
        case ASPD: d.quantityIsAngularSpeed(times,speed_window,jitty); multiplier = 180/Math.PI; break;
        case LENG: d.quantityIsLength(jitty); multiplier = mm_per_pixel; break;
        case RLEN: d.quantityIsLength(jitty); d.quantityMult( (float)(1.0/d.body_length.average) ); break;
        case WIDT: d.quantityIsWidth(jitty); multiplier = mm_per_pixel; break;
        case RWID: d.quantityIsWidth(jitty); d.quantityMult( (float)(1.0/d.body_width.average) ); break;
        case ASPC: d.quantityIsAspect(jitty); break;
        case RASP: d.quantityIsAspect(jitty); d.quantityMult( (float)(1.0/d.body_aspect.average) ); break;
        case MIDL: d.quantityIsMidline(jitty); multiplier = mm_per_pixel; break;
        case OUTW: d.quantityIsOutlineWidth(jitty); multiplier = mm_per_pixel; break;
        case KINK: d.quantityIsKink(jitty); multiplier = 180/Math.PI; break;
        case BIAS: d.quantityIsBias(times,speed_window,minTravelPx(d),false);
                   break;
        case PATH: d.quantityIsPath(times,speed_window,minTravelPx(d));
                   multiplier = mm_per_pixel;
                   break;
        case CURV: d.quantityIsCurve(jitty); multiplier = 180/Math.PI; break;
        case DIRC: d.quantityIsDirectionChange(times,speed_window); break;
        case LOCX: d.quantityIsX(jitty); multiplier = mm_per_pixel; break;
        case LOCY: d.quantityIsY(jitty); multiplier = mm_per_pixel; break;
        case VELX: d.quantityIsVx(times,speed_window,speed_over_length,jitty); if (!speed_over_length) { multiplier = mm_per_pixel; } break;
        case VELY: d.quantityIsVy(times,speed_window,speed_over_length,jitty); if (!speed_over_length) { multiplier = mm_per_pixel; } break;
        case ORNT: d.quantityIsTheta(jitty); multiplier = 180/Math.PI; break;
        case CRAB: d.quantityIsCrab(times,speed_window,speed_over_length,jitty); if (!speed_over_length) { multiplier = mm_per_pixel; } break;
        case QXFW: d.quantityIsQxfw(this,jitty); break;
        case CUST: int i = plug_map.out.indexOf(ds);
                   if (i < 0 || d.loaded_custom.length <= i || !d.loaded_custom[i].already) {
                     plugininfo.get(ds.plugnum).plugin.computeDancerQuantity(d,ds.which);
                     d.quantityAlreadyIsCustom(i,jitty);
                   }
                   break;
        case STI4: d.quantityIsStim(events,4); break;
        case STI3: d.quantityIsStim(events,3); break;
        case STI2: d.quantityIsStim(events,2); break;
        case STI1: d.quantityIsStim(events,1); break;
        default: d.quantityIs(Float.NaN);
      }
      if (ds.measure == DataMeasure.JIT) d.quantityIs( d.loaded_jitter );
      return multiplier;
    }
    finally {
      if (ds.measure != DataMeasure.JIT && (ds.plugnum < 0 || !(plugininfo.get(ds.plugnum).plugin instanceof CustomOutputModification))) {
        boolean modified = false;
        for (CustomOutputModification com : plugmods) modified |= com.modifyQuantity(d, ds.source);
        if (modified) d.allUnload();
      }
    }
  }
  public double loadDancerWithData(Dance d,DataSource ds,DataMeasure dm) {
    return loadDancerWithData(d,new DataSpecifier(ds,dm));
  }
  
  public DataPrinter prepareSingle(Dance d,DataSpecifier ds,DataPrinter old_dp)
  {
    if (old_dp==null) d.quantityIsTime(times,jittering_sources.contains(DataSource.TIME));
    DataPrinter dp = preparePrinting( ds , (old_dp==null) ? Arrays.copyOf(d.quantity,d.quantity.length) : old_dp.timeBase() );
    loadDancerWithData(d,ds);
    dp.setS(null).setF(Arrays.copyOf(d.quantity,d.quantity.length));
    return dp;
  }
  
  public DataPrinter preparePrinting(DataSpecifier ds,float[] t)
  {
    int nchars = (int)( -Math.log10( mm_per_pixel*1e-3 ) );
    if (nchars>6) nchars=6;
    if (nchars<1) nchars=1;
    
    int nsqchars = (int)( - Math.log10( mm_per_pixel*mm_per_pixel*1e-3 ) );
    if (nsqchars>8) nsqchars=8;
    if (nsqchars<1) nsqchars=1;

    int trace[];
    int stim_number = 1;  // Trick to use fall-through of case statement to pick out correct stimulus
    DataPrinter dp = new DataPrinter(t);
    switch (ds.source)
    {
      case TIME: dp.setT("Time (seconds)").setF(t).setDig(3); break;
      case FNUM: dp.setT("Frame (#)").setI(frames); break;
      case OBID: dp.setT("Object ID").setF(obid).setDig(0); break;
      case NUMB: dp.setT("Number Tracked").setI(numbers); break;
      case GOOD: dp.setT("Number meeting Criteria").setI(good_number); break;
      case AREA: dp.setT("Area (mm^2)").setS(area).setMult(mm_per_pixel*mm_per_pixel).setDig(nsqchars); break;
      case PERS: dp.setT("Lifetime (s)").setS(persistence).setDig(1); break;
      case SPED: if (speed_over_length) dp.setT("Speed (lengths/s)").setS(speed).setDig(nchars);
                 else dp.setT("Speed (mm/s)").setS(speed).setMult( mm_per_pixel ).setDig(nchars);
                 break;
      case ASPD: dp.setT("Turning rate (degrees/s)").setS(angular_speed).setMult(180/Math.PI).setDig(1); break;
      case LENG: dp.setT("Length (mm)").setS(length).setMult(mm_per_pixel).setDig(nchars); break;
      case RLEN: dp.setT("Normalized Length (1.0=mean)").setS(rel_length).setDig(3); break;
      case WIDT: dp.setT("Width (mm)").setS(width).setMult(mm_per_pixel).setDig(nchars); break;
      case RWID: dp.setT("Normalized Width (1.0=mean)").setS(rel_width).setDig(3); break;
      case ASPC: dp.setT("Aspect (width/length)").setS(aspect).setDig(3); break;
      case RASP: dp.setT("Normalized Aspect (1.0=mean)").setS(rel_aspect).setDig(3); break;
      case MIDL: dp.setT("Spine Length (mm)").setS(spine_length).setMult(mm_per_pixel).setDig(nchars); break;
      case OUTW: dp.setT("Width About Spine (mm)").setS(spine_width).setMult(mm_per_pixel).setDig(nchars); break;
      case KINK: dp.setT("Head/Tail Angle (degrees)").setS(end_wiggle).setMult(180/Math.PI).setDig(1); break;
      case BIAS: dp.setT("Movement Direction (1=always forward)").setS(bias).setDig(3); break;
      case PATH: dp.setT("Path length (mm)").setS(pathlen).setDig(3).setMult(mm_per_pixel); break;
      case CURV: dp.setT("Body Curve (degrees/20% of body)").setS(curve).setMult(180/Math.PI).setDig(1); break;
      case DIRC: dp.setT("Direction Change (1=no change)").setS(dir_change).setDig(3); break;
      case LOCX: dp.setT("X location (mm)").setS(loc_x).setMult(mm_per_pixel).setDig(nchars); break;
      case LOCY: dp.setT("Y location (mm)").setS(loc_y).setMult(mm_per_pixel).setDig(nchars); break;
      case VELX: if (speed_over_length) dp.setT("X velocity (lengths/s)").setS(vel_x).setDig(nchars);
                 else dp.setT("X velocity (mm/s)").setS(vel_x).setMult(mm_per_pixel).setDig(nchars);
                 break;
      case VELY: if (speed_over_length) dp.setT("Y velocity (lengths/s)").setS(vel_y).setDig(nchars);
                 else dp.setT("Y velocity (mm/s)").setS(vel_y).setMult(mm_per_pixel).setDig(nchars);
                 break;
      case ORNT: dp.setT("Orientation (degrees)").setS(orient).setMult(180/Math.PI).setDig(1); break;
      case CRAB: if (speed_over_length) dp.setT("Crab speed (lengths/s)").setS(crab).setDig(nchars);
                 else dp.setT("Crab speed (mm/s)").setS(crab).setMult(mm_per_pixel).setDig(nchars);
                 break;
      case QXFW: dp.setT("Qxfw").setS(qxfw).setDig(nchars); break;
      case CUST: dp.setT( eatAt(plugininfo.get(ds.plugnum).plugin.quantifierTitle(ds.which)) ).setS( custom[plug_map.out.indexOf(ds)] ).setDig(3); break;
      case STI4: stim_number++;
      case STI3: stim_number++;
      case STI2: stim_number++;
      case STI1: trace = new int[ frames.length ];
                 for (int i=0 ; i<trace.length ; i++)
                 {
                   trace[i] = 0;
                   if (events[i]!=null) for (int j=0;j<events[i].length;j++) if (events[i][j]==stim_number) trace[i] = 1;
                 }
                 dp.setI(trace).countOn();
                 dp.setT( ((stim_number==1) ? "Tap " : ((stim_number==2) ? "Puff " : ("Custom"+((stim_number==3)?" ":"2 ")))) + "Stimulus" );
                 break;
      default: trace = new int[ frames.length ];
               for (int i=0 ; i<trace.length ; i++) trace[i] = 0;
               dp.setI(trace);
               dp.setT("Zero");
               break;
    }
    dp.what = ds.measure;
    
    return dp;
  }

  public DataSpecifier[] filterNopData(DataSpecifier[] out_data_unfiltered) {
    if (out_data_unfiltered == null) return null;
    else if (out_data_unfiltered.length == 0) return out_data_unfiltered;
    else {
      int n = 0;
      for (int i = 0; i < out_data_unfiltered.length; i++) {
        if (out_data_unfiltered[i].measure != DataMeasure.NOP) n++;
      }
      if (n == out_data_unfiltered.length) return out_data_unfiltered;
      DataSpecifier out_data[] = new DataSpecifier[n];
      n = 0;
      for (int i=0; i < out_data_unfiltered.length; i++) {
        if (out_data_unfiltered[i].measure != DataMeasure.NOP) {
          out_data[n] = out_data_unfiltered[i]; n++;
        }
      }
      return out_data;
    }
  }
  
  public DataPrinter[] writeStatistics(DataSpecifier[] out_data_unfiltered, String out_name, Dance d) throws Dance.DancerFileException,SaveDataException
  {
    DataSpecifier[] out_data = null;
    boolean do_trigger = !(trigger_start==null || trigger_end==null);
    do_trigger = do_trigger && !(trigger_end[0] < times[0] || trigger_start[trigger_start.length-1] > times[times.length-1]);
    if (out_data_unfiltered.length==0) write_timecourse=false;
    else out_data = filterNopData(out_data_unfiltered);
    
    String bit = (d==null) ? "" : String.format(".%05d",d.ID);
    DataPrinter[] data = new DataPrinter[ out_data.length ];
    if (d==null) for (int i=0 ; i<out_data.length ; i++) data[i] = preparePrinting( out_data[i] , times );
    else for (int i=0 ; i<out_data.length ; i++) data[i] = prepareSingle(d , out_data[i] , (i==0)?null:data[0]);

    BufferedWriter underlying_stream;
    PrintWriter data_file;
    float[] t = data[0].timeBase();

    // Raw statistics timecourse part
    if (write_timecourse)
    {
      File target = new File(targetDir(),file_prefix + out_name + bit + ".dat");
      
      if (!quiet_operation) System.out.println("Writing summary file " + target.getPath());
      
      try { underlying_stream = new BufferedWriter(new FileWriter( target )); }
      catch (IOException ioe) { throw new SaveDataException("Could not open " + target.getPath() + " for output."); }
    
      data_file = new PrintWriter( underlying_stream );
      int last_printed = -1;
      float next_p_time = output_time_chunk;
      if (print_header != null) {
        if (print_header.length()>0) data_file.print(print_header);
        for (int j=0; j<data.length; j++) {
          if (j>0 || print_header.length()>0) data_file.print("\t");
          data_file.print(data[j].title);
        }
        data_file.println();
      }
      for (int i=0 ; i<t.length ; i++)
      {
        if (t[i]*(1+1e-6) < next_p_time) continue;
        
        for (int j=0 ; j<data.length ; j++)
        {
          if (j>0) data_file.print(" ");
          data_file.print( data[j].print(last_printed+1,i) );
        }
        data_file.println();
        
        last_printed = i;
        if (output_time_chunk > 0)
        {
          next_p_time += output_time_chunk;
          if (next_p_time < t[i]) next_p_time = output_time_chunk * (float)Math.ceil( t[i]/output_time_chunk );
        }
        else next_p_time = t[i];
      }
      if (data_file.checkError()) { throw new SaveDataException("Unable to write to " + target.getPath()); }
      
      try { underlying_stream.close(); } catch (IOException ioe) { }   // Don't worry if we can't close the file; things are pretty much OK anyway
      
      if (!quiet_operation) System.out.println("  Write successful.");
    }
    
    // Triggered statistics part
    if (do_trigger) {
      File target = new File(targetDir() , file_prefix + out_name + bit + ".trig");
      
      if (!quiet_operation) System.out.println("Writing triggered averages to " + target.getName());
      
      try { underlying_stream = new BufferedWriter(new FileWriter( target )); }
      catch (IOException ioe) { throw new SaveDataException("Could not open " + target.getPath() + " for output."); }
      
      data_file = new PrintWriter( underlying_stream );
      
      int a,b;
      int hi,lo,mid;
      for (int i=0 ; i<trigger_start.length ; i++)
      {
        lo = 0;
        hi = t.length-1;
        while (hi-lo>1)
        {
          mid = (hi+lo)/2;
          if (trigger_start[i] < t[mid]) hi = mid;
          else lo = mid;
        }
        a = hi;
        lo = 0;
        hi = t.length-1;
        while (hi-lo>1)
        {
          mid = (hi+lo)/2;
          if (trigger_end[i] < t[mid]) hi = mid;
          else lo = mid;
        }
        b = lo;
        if (b<a) b = a;
        
        for (int j=0 ; j<data.length ; j++)
        {
          if (j>0) data_file.print(" ");
          data_file.print( data[j].printAvg(a,b) );
        }
        data_file.println();
      }
      if (data_file.checkError()) { throw new SaveDataException("Unable to write to " + target.getPath()); }
      
      try { underlying_stream.close(); } catch (IOException ioe) { }
      
      if (!quiet_operation) System.out.println("  Write successful.");
    }
    
    return data;
  }
  
  
  public void showStatistics(DataSpecifier[] out_data)
  {
    DataPrinter[] data = new DataPrinter[out_data.length-1];
    for (int i=1 ; i<out_data.length ; i++) data[i-1] = preparePrinting( out_data[i] , times );
    Graphs g = new Graphs(data);
    g.run("Graph View - "+ChoreographyVersion.CURRENT_IDENTIFIER.split("build")[0]);
  }
  
  
  public void showDataMap(DataPrinter[] dps,DataSpecifier[] dss)
  {
    LinkedList<Double> zoom_fac_temp = new LinkedList<Double>();
    ArrayList<Double> zoom_factors = new ArrayList<Double>();
    double base = 0.01;
    for (int i=0 ; i<5 ; i++)
    {
      zoom_fac_temp.push( base );
      zoom_fac_temp.push( base*2.0 );
      zoom_fac_temp.push( base*5.0 );
      base *= 10.0;
    }
    for (Double d : zoom_fac_temp) zoom_factors.add( d );
    
    for (Dance d : dances)
    {
      if (d==null) continue;
      d.readyMultiscale(mm_per_pixel);
    }
    System.out.println("Ready to show data map.");
    
    DataMapper dm = new DataMapper(this,dps,dss);
    
    dmv = new DataMapVisualizer(dm , zoom_factors , 4 , DataMapper.MINIMAP_SIZE , 0.5 , true , 1,1,2 , "" , new Vec2D(0,0), "Map View - "+ChoreographyVersion.CURRENT_IDENTIFIER.split("build")[0]);
    dm.addDataReadyListener( dmv );
    try
    {
      java.awt.EventQueue.invokeAndWait(
        new Runnable() { public void run() { dmv.setLocation(50, 50); dmv.makeVisible(); } }
      );
    }
    catch (InterruptedException ie)
    {
      // Do nothing--just quit
    }
    catch (InvocationTargetException ite)
    {
      // Also do nothing
    }
  }
  
  public int indexNear(float t)
  {
    int lo=0;
    int hi=times.length-1;
    int mid;
    if (t < times[lo]) return lo;
    if (t > times[hi]) return hi;
    while (hi-lo > 1)
    {
      mid = (hi+lo)/2;
      if (times[mid] < t) lo=mid;
      else hi=mid;
    }
    if (times[hi]-t < t-times[lo]) return hi;
    else return lo;
  }

  public float[] extractTimes() { return Arrays.copyOf(times, times.length); }

  public Dance[] extractReasonableDancers() {
    int n = 0;
    for (Dance d : dances) {
      if (d != null && d.outline != null && d.spine != null && d.spine.length > 0) n++;
    }
    Dance[] ds = new Dance[n];
    n = 0;
    for (Dance d : dances) {
      if (d != null && d.outline != null && d.spine != null && d.spine.length > 0) { ds[n] = d; n++; }
    }
    return ds;
  }

  public DataSpecifier extractOutputSpecification(String source, String measure) {
    return new DataSpecifier(DataSource.interpret(source),DataMeasure.interpret(measure));
  }
  
  public static Choreography doEverything(String[] args, CustomComputation[] providedPlugins, boolean isHeadless)
  {
    Choreography chore = new Choreography();
    chore.headless = isHeadless;
    chore.providedPlugins = providedPlugins;
    
    try { chore.parseInput(args); }
    catch (CustomHelpException che) {
      throw new SystemExit(1);
    }
    catch (IllegalArgumentException iae)
    {
      if (args.length==0) chore.printHelpMessage(false);
      else {
        System.out.println("Error in Arguments\n  " + iae.getMessage());
        System.out.println("  Use --help to list valid options.");
      }
      throw new SystemExit(1);
    }
    
    try { chore.findFiles(); }
    catch (WrongFilesException wfe)
    {
      System.out.println("Error Finding Files\n  " + wfe.getMessage() + "\n");
      throw new SystemExit(1);
    }

    long t0 = System.nanoTime();
    try { chore.loadData( (chore.output_names!=null && chore.output_names.length==1) ? chore.output_names[0] : "" ); }
    catch (LoadDataException sre)
    {
      System.out.println("Error Reading Data\n  " + sre.getMessage() + "\n");
      throw new SystemExit(1);
    }
    long t1 = System.nanoTime();
    System.out.printf("Took %.3f seconds to load data\n",(t1-t0)*1e-9);
    
    HashMap<Integer,Integer> id_to_index = new HashMap<Integer,Integer>();
    if (chore.all_individuals)
    {
      int n = 0;
      for (Dance d : chore.dances) if (d!=null) n++;
      chore.id_table = new HashSet<Integer>(2+3*n/2);
      for (int i=0 ; i<chore.dances.length ; i++) if (chore.dances[i]!=null) chore.id_table.add( chore.dances[i].ID );
    }
    if (chore.one_by_one)
    {
      for (int i=0 ; i<chore.dances.length ; i++) {
        if (chore.dances[i]!=null) id_to_index.put(chore.dances[i].ID , i);
      }
    }

    float[] for_custom = null;
    if (!chore.one_by_one) for_custom = chore.recomputeOnlineStatistics();

    // Custom computations
    if (chore.plugininfo!=null && chore.plugininfo.size()>0) {
      int wrote = 0;
      for (ComputationInfo ci : chore.plugininfo) {
        try {
          wrote |= ci.plugin.computeAll((ci.mapoutput) ? new File(chore.targetDir() , chore.file_prefix + "." + ci.plugin.desiredExtension()) : null);
          for (Dance d : chore.dances) {
            if (d==null) continue;
            wrote |= ci.plugin.computeDancerSpecial( d ,
              (ci.mapoutput) ? new File(chore.targetDir() , chore.file_prefix + "." + String.format("%05d",d.ID) + "." + ci.plugin.desiredExtension()) : null
            );
          }
        }
        catch (IOException ioe) {
          System.out.println("IO error in custom computation:");
          System.out.println(ioe.getMessage());
          throw new SystemExit(1);
        }
      }
      if ((wrote & 2) != 0) if (!chore.one_by_one) for_custom = chore.recomputeOnlineStatistics();
    }
    if (!chore.one_by_one) chore.recomputeCustomOnlineStatistics(for_custom);
    
    if (chore.output_names!=null)
    {
      DataPrinter[] dps = null;
      DataSpecifier[] dss = null;
      for (int i=0 ; i<chore.output_names.length ; i++)
      {
        DataPrinter[] temp = null;
        try
        {
          if (!chore.one_by_one) temp = chore.writeStatistics(chore.output_requests[i],chore.output_names[i],null);
          else {
            for (int id : chore.id_table) {
              int index = id_to_index.get(id).intValue();
              if (index<0 || index>=chore.dances.length) continue;
              if (chore.dances[index]==null) continue;
              DataPrinter[] temp2 = chore.writeStatistics(chore.output_requests[i],chore.output_names[i],chore.dances[index]);
              if (temp==null) temp=temp2;
            }
          }
          if (dps==null) { dps=temp; dss=chore.output_requests[i]; }
        }
        catch (SaveDataException sde)
        {
          System.out.println("Error Saving Summary Data\n  " + sde.getMessage() + "\n");
          throw new SystemExit(1);
        }
        catch (Dance.DancerFileException dfe)
        {
          System.out.println("Error Saving Data\n  " + dfe.getMessage() + "\n");
          throw new SystemExit(1);
        }
      }
      if ( (chore.interactive_mode || chore.view_graph || chore.view_datamap))
      {
        if (chore.view_graph)
        {
          if (chore.one_by_one) System.out.println("Cannot view graphs of individual objects.");
          else for (int i=0 ; i<chore.output_names.length ; i++) chore.showStatistics(chore.filterNopData(chore.output_requests[i]));
        }
        if (chore.view_datamap)
        {
          chore.showDataMap(dps,chore.filterNopData(dss));
        }
      }
    }
    return chore;
  }
  
  public static void main(String[] args) { 
    try { doEverything(args, null, false); }
    catch (SystemExit se) { System.exit(se.exitValue); }
  }
}
