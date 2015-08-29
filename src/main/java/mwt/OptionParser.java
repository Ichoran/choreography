/** This file copyright 2008 Nicholas Swierczek and the Howard Hughes Medical Institute.
  * Also Copyright 2015 Calico Life Sciences LLC (authored by Rex Kerr)
  * Distributed under the LGPL2.1 license (or GPL2.1 with classpath exception)
  */

package mwt;

import java.util.*;

public class OptionParser
{
  public static interface TextParser<T>
  {
    public T parse(String s) throws IllegalArgumentException;
    public T retrieve();
    public void reset();
    public int valuesNeeded();
  }
  public static class VoidParser implements TextParser<Void>
  {
    public VoidParser() { }
    public Void parse(String s) throws IllegalArgumentException { return null; }
    public Void retrieve() { return null; }
    public void reset() { }
    public int valuesNeeded() { return 0; }
  }
  public static class BooleanParser implements TextParser<Boolean>
  {
    Boolean b;
    public BooleanParser() { b=null; }
    public Boolean parse(String s) throws IllegalArgumentException
    {
      if (s==null) throw new IllegalArgumentException("null",new NullPointerException());
      s = s.toLowerCase();
      if (s.equals("true") || s.equals("t") || s.equals("yes") || s.equals("y") || s.equals("on") || s.equals("1")) b = new Boolean(true);
      else if (s.equals("false") || s.equals("f") || s.equals("no") || s.equals("n") || s.equals("off") || s.equals("0")) b = new Boolean(false);
      else throw new IllegalArgumentException("Could not parse '" + s + "'as boolean.");
      return b;
    }
    public Boolean retrieve() { return b; }
    public void reset() { b=null; }
    public int valuesNeeded() { return 1; }
  }
  public static class IntegerParser implements TextParser<Integer>
  {
    Integer i;
    public IntegerParser() { i=null; }
    public Integer parse(String s) throws IllegalArgumentException
    {
      if (s==null) throw new IllegalArgumentException("null",new NullPointerException());
      try { i = new Integer(s); }
      catch (IllegalArgumentException iae) { throw new IllegalArgumentException("Could not parse '" + s + "' as integer.",iae); }
      return i;
    }
    public Integer retrieve() { return i; }
    public void reset() { i=null; }
    public int valuesNeeded() { return 1; }
  }
  public static class DoubleParser implements TextParser<Double>
  {
    Double d;
    public DoubleParser() { d=null; }
    public Double parse(String s) throws IllegalArgumentException
    {
      if (s==null)throw new IllegalArgumentException("null",new NullPointerException());
      try { d = new Double(s); }
      catch (IllegalArgumentException iae) { throw new IllegalArgumentException("Could not parse '" + s + "' as floating-point value.",iae); }
      return d;
    }
    public Double retrieve() { return d; }
    public void reset() { d=null; }
    public int valuesNeeded() { return 1; }
  }
  public static class StringParser implements TextParser<String>
  {
    String c;
    public StringParser() { c=null; }
    public String parse(String s) throws IllegalArgumentException
    {
      if (s==null) throw new IllegalArgumentException("null",new NullPointerException());
      return (c=new String(s));
    }
    public String retrieve() { return c; }
    public void reset() { c=null; }
    public int valuesNeeded() { return 1; }
  }
  
  public static class Option<T>
  {
    boolean parse_completed;
    T default_value;
    LinkedList<Integer> index_list;
    LinkedList<T> working_results;
    LinkedList<String> strings_used;
    Vector<T> results;
    TextParser<T> type_parser;
    public Option(TextParser<T> p)
    {
      parse_completed = false;
      index_list = new LinkedList<Integer>();
      working_results = new LinkedList<T>();
      strings_used = new LinkedList<String>();
      results = null;
      type_parser = p;
    }
    public Option(TextParser<T> p,Vector<T> v)
    {
      parse_completed = false;
      index_list = new LinkedList<Integer>();
      working_results = new LinkedList<T>();
      strings_used = new LinkedList<String>();
      results = v;
      type_parser=p;
    }
    public Option<T> setStorage(Vector<T> v) { results=v; return this; }
    public Option<T> setParser(TextParser<T> p) { type_parser=p; return this; }
    public Option<T> defaultTo(T def_val) { default_value=def_val; return this; }
    public boolean readyToParse() { return (type_parser!=null); }
    public boolean parseComplete() { return parse_completed; }
    public T get()
    {
      if (!parseComplete()) return null;
      return working_results.getLast();
    }
    public int howMany()
    {
      if (!parseComplete()) return 0;
      return index_list.size();
    }
    public Vector<T> getAll()
    {
      if (!parseComplete()) return null;
      if (results!=null) return results;
      return new Vector<T>(working_results);
    }
    public int[] getIndices()
    {
      if (!parseComplete()) return null;
      int ilist[] = new int[index_list.size()];
      int i=0;
      for (Integer ii : index_list) { ilist[i++] = ii; }
      return ilist;
    }
    public String[] getStrings()
    {
      if (!parseComplete()) return null;
      return index_list.toArray(new String[0]);
    }
    public boolean found() { return (parseComplete() && howMany()>0); }
    public boolean needsValue() { return (type_parser.valuesNeeded()>0); }
    public void reset()
    {
      parse_completed=false;
      working_results.clear();
      index_list.clear();
      strings_used.clear();
      if (results!=null) results.clear();
    }
    public void ensureDefault()
    {
      if (default_value!=null && working_results.size()==0)
      {
	working_results.add( default_value );
	index_list.add( new Integer(-1) );
      }
    }
    public void parse(String s,String arg,int idx) throws IllegalArgumentException
    {
      working_results.add( type_parser.parse(s) );
      index_list.add( new Integer(idx) );
      strings_used.add( arg );
      parse_completed=true;
    }
    public void collectResults()
    {
      if (results==null) return;
      results.clear();
      results.ensureCapacity(working_results.size());
      int i=0;
      for (T t : working_results) results.add(i++,t);
    }
  }

  boolean parse_completed;
  LinkedList<String> optionsDuplicated;
  HashMap<String,String> optionNames;
  HashMap<String,Option<?>> optionValues;
  Vector<String> unassignedValues;
  
  OptionParser()
  {
    parse_completed=false;
    optionsDuplicated = new LinkedList<String>();
    optionNames = new HashMap<String,String>();
    optionValues = new HashMap<String,Option<?>>();
    unassignedValues = new Vector<String>();
  }
  
  public static OptionParser create() { return new OptionParser(); }
  public Option<Void> addOption(String... s)
  {
    String anchor = null;
    for (String i : s)
    {
      if (anchor==null) { anchor=i; if (optionNames.containsKey(anchor)) optionsDuplicated.add(anchor); }
      optionNames.put(i,anchor);
    }
    Option<Void> ov = new Option<Void>(new VoidParser());
    optionValues.put(anchor,ov);
    return ov;
  }
  public Option<Boolean> addBoolean(Vector<Boolean> vb,String... s)
  {
    String anchor = null;
    for (String i : s)
    {
      if (anchor==null) { anchor=i; if (optionNames.containsKey(anchor)) optionsDuplicated.add(anchor); }
      optionNames.put(i,anchor);
    }
    Option<Boolean> ob = new Option<Boolean>(new BooleanParser(),vb);
    optionValues.put(anchor,ob);
    
    return ob;
  }
  public Option<Boolean> addBoolean(String... s) { return addBoolean(new Vector<Boolean>(),s); }
  public Option<Integer> addInteger(Vector<Integer> vb,String... s)
  {
    String anchor = null;
    for (String i : s)
    {
      if (anchor==null) { anchor=i; if (optionNames.containsKey(anchor)) optionsDuplicated.add(anchor); }
      optionNames.put(i,anchor);
    }
    Option<Integer> ob = new Option<Integer>(new IntegerParser(),vb);
    optionValues.put(anchor,ob);
    
    return ob;
  }
  public Option<Integer> addInteger(String... s) { return addInteger(new Vector<Integer>(),s); }
  public Option<Double> addDouble(Vector<Double> vb,String... s)
  {
    String anchor = null;
    for (String i : s)
    {
      if (anchor==null) { anchor=i; if (optionNames.containsKey(anchor)) optionsDuplicated.add(anchor); }
      optionNames.put(i,anchor);
    }
    Option<Double> ob = new Option<Double>(new DoubleParser(),vb);
    optionValues.put(anchor,ob);
    
    return ob;
  }
  public Option<Double> addDouble(String... s) { return addDouble(new Vector<Double>(),s); }
  public Option<String> addString(Vector<String> vs,String... s)
  {
    String anchor = null;
    for (String i : s)
    {
      if (anchor==null) { anchor=i; if (optionNames.containsKey(anchor)) optionsDuplicated.add(anchor); }
      optionNames.put(i,anchor);
    }
    Option<String> os = new Option<String>(new StringParser(),vs);
    optionValues.put(anchor,os);
    return os;
  }
  public Option<String> addString(String... s) { return addString(new Vector<String>(),s); }
  public <T> Option<T> addCustom(TextParser<T> tpt,Vector<T> vt,String... s)
  {
    String anchor = null;
    for (String i : s)
    {
      if (anchor==null) { anchor=i; if (optionNames.containsKey(anchor)) optionsDuplicated.add(anchor); }
      optionNames.put(i,anchor);
    }
    Option<T> ot = new Option<T>(tpt,vt);
    optionValues.put(anchor,ot);
    return ot;
  }
  public <T> Option<T> addCustom(TextParser<T> tpt,String... s) { return addCustom(tpt,new Vector<T>(),s); }
  
  public String[] getRemainingArguments()
  {
    if (!parse_completed) return null;
    return unassignedValues.toArray(new String[unassignedValues.size()]);
  }
  String[] parse(String[] args,boolean safe)  throws IllegalArgumentException
  {
    if (!optionValues.containsKey("")) addString(unassignedValues,"");
    
    if (optionsDuplicated.size()>0 && !safe) throw new IllegalArgumentException("Duplicated options in parse list: "+optionsDuplicated);
    for (Option<?> o : optionValues.values()) o.reset();
    
    int i=0;
    int n=0;
    String parse_this=null;
    String raw_name=null;
    String arg_name=null;
    String aftereq[]=null;
    boolean done=false;
    while (i<args.length)
    {
      parse_this=args[i++];
      
      if (done)
      {
	arg_name = optionNames.get("");
	try { optionValues.get(arg_name).parse(parse_this,"",n++); }
	catch (IllegalArgumentException iae) { if (!safe) throw iae; }
	arg_name=null;
      }
      else // Keep parsing
      {
	if (arg_name!=null) // We needed a value for the option
	{
	  if (parse_this.charAt(0)=='-' && parse_this.length()>1) // Except we didn't get one
	  {
	    if (!safe) throw new IllegalArgumentException("Argument " + raw_name + " requires a value");
	  }
	  else
	  {
	    try { optionValues.get(arg_name).parse(parse_this,raw_name,n++); }
	    catch (IllegalArgumentException iae) { if (!safe) throw iae; }
	  }
	  arg_name=raw_name=null;
	}
	else if (parse_this.startsWith("--")) // Long option
	{
	  if (parse_this.equals("--")) // Options are done
	  {
	    done=true;
	  }
	  else // Parse the option--be sure to strip off leading "--" e.g. with substring(2)
	  {
	    if (parse_this.indexOf("=")==-1) // No value assigned
	    {
	      raw_name = parse_this;
	      arg_name = optionNames.get(parse_this.substring(2));
	      if (!safe && arg_name==null) throw new IllegalArgumentException("Unknown argument: " + parse_this);
	      
	      try
	      { 
	        if (!optionValues.get(arg_name).needsValue()) // Don't need a value, so parse what we've got
		{
		  optionValues.get(arg_name).parse(null,raw_name,n++);
		  arg_name=raw_name=null;
		}
	      }
	      catch (IllegalArgumentException iae) { arg_name=raw_name=null; if (!safe) throw iae; }
	    }
	    else // Value assigned
	    {
	      aftereq=parse_this.substring(2).split("=",2);
	      arg_name = optionNames.get(aftereq[0]);
	      if (!safe && arg_name==null) throw new IllegalArgumentException("Unknown argument: " + parse_this);
	      parse_this=(aftereq.length>1)?aftereq[1]:"";
	      
	      try
	      {
		if (optionValues.get(arg_name).needsValue()) optionValues.get(arg_name).parse(parse_this,aftereq[0],n++);
		else if (safe) optionValues.get(arg_name).parse(null,aftereq[0],n++);
		else throw new IllegalArgumentException("Option " + aftereq[0] + " takes no value");
		arg_name=raw_name=null;
	      }
	      catch (IllegalArgumentException iae) { arg_name=raw_name=null; if (!safe) throw iae; }
	    }
	  }
	}
	else if (parse_this.charAt(0)=='-' && parse_this.length()>1)
	{
	  for (int j=1;j<parse_this.length();j++)
	  {
	    raw_name = parse_this.substring(j,j+1);
	    arg_name = optionNames.get(raw_name);
	    if (!safe && arg_name==null) throw new IllegalArgumentException("Unknown argument: " + parse_this);
	    try
	    {
	      if (!optionValues.get(arg_name).needsValue())
	      {
		optionValues.get(arg_name).parse(null,raw_name,n++);
		arg_name=raw_name=null;
	      }
	      else if (j+1<parse_this.length())
	      {
		optionValues.get(arg_name).parse(parse_this.substring(j+1),raw_name,n++);
		arg_name=raw_name=null;
		break;
	      }
	    }
	    catch (IllegalArgumentException iae) { arg_name=raw_name=null; if (!safe) throw iae; }
	  }
	}
	else // Spare argument, save it
	{
	  arg_name = optionNames.get("");
	  try { optionValues.get(arg_name).parse(parse_this,"",n++); }
	  catch (IllegalArgumentException iae) { if (!safe) throw iae; }
	  arg_name=null;	  
	}
      }
    }
    parse_completed=true;
    for (Option<?> o : optionValues.values()) o.collectResults();
    return getRemainingArguments();
  }
  public String[] parse(String[] args) throws IllegalArgumentException { return parse(args,false); }
  public String[] safeParse(String[] args)
  {
    try { return parse(args,true); }
    catch (IllegalArgumentException iae) {}
    return null;
  }
  public boolean optionFound(String key)
  {
    if (!parse_completed) return false;
    String lookup = optionNames.get(key);
    if (lookup==null) return false;
    return optionValues.get(lookup).found();
  }
  
  public static void testMe()
  {
    OptionParser op = new OptionParser();
    Vector<String> file_options = new Vector<String>();
    String[] remaining_options;
    Vector<Integer> line_options = new Vector<Integer>();
    op.addString("f","filename").setStorage(file_options);
    op.addOption("v","verbose");
    op.addOption("q","quiet");
    op.addInteger("l","lines").setStorage(line_options);
    String[] args = new String[] { "-f","hi","goose","-vl7","-l","51","--","-goose","--goose" };
    remaining_options = op.parse(args);
    System.out.print("File options:  ["); for (String s : file_options) { System.out.print(" '" + s + "'"); } System.out.println(" ]");
    System.out.println("Verbose option:" + op.optionFound("v"));
    System.out.println("Quiet option:  " + op.optionFound("q"));
    System.out.print("Lines option:  ["); for (Integer i : line_options) { System.out.print(" " + i); } System.out.println(" ]");
    System.out.print("Remainder:     ["); for (String s : remaining_options) { System.out.print(" '" + s + "'"); } System.out.println(" ]");
  }
  
  public static void main(String args[])
  {
    testMe();
  }
}

