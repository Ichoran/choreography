/* Prefix Lister.java - Utility app to find files with a given prefix
 * Copyright 2010 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015 Calico Life Sciences LLC (authored by Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt;

import java.io.*;
import java.util.*;

class PrefixLister
{
  LinkedList<File> dirs;
  
  public PrefixLister()
  {
    dirs = new LinkedList<File>();
  }
  
  public void run(File root,LinkedList<File> results)
  {    
    File[] listing = root.listFiles();
    for (File f : listing)
    {
      if (f.getName().matches("\\d{8}_\\d{6}"))
      {
        File[] sublisting = f.listFiles();
        for (File sf : sublisting) { if (sf.getName().endsWith(".summary")) results.add(sf); }
      }
      else if (f.isDirectory()) run(f,results);
    }
  }
  
  public static void main(String[] args)
  {
    if (args.length != 1)
    {
      System.out.println("Use exactly two arguments: the root directory from which to search");
      return;
    }
    
    PrefixLister pl = new PrefixLister();
    pl.run(new File(args[0]),pl.dirs);
    for (File f : pl.dirs)
    {
      System.out.println(f.getAbsolutePath());
    }
  }
}

