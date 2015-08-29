/* PrefixChanger.java - Utility app to rename files
 * Copyright 2010 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015 Calico Life Sciences LLC (authored by Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt;

import java.io.*;

class PrefixChanger
{
  String preprefix;
  String afterprefix;
  
  public PrefixChanger() { preprefix = null; afterprefix = null; }
  
  public void run(String[] args)
  {
  
    if (args.length != 2)
    {
      System.out.println("Use exactly two arguments: the prefix to change and the prefix it should become.");
      return;
    }
    
    preprefix = args[0];
    afterprefix = args[1];
    
    File directory = new File("./");
    File[] listing = directory.listFiles();
    for (File f : listing)
    {
      if (f.getName().startsWith(preprefix))
      {
        f.renameTo( new File(f.getName().replace(preprefix,afterprefix)) );
      }
    }
  }
  
  public static void main(String[] args)
  {
    PrefixChanger pc = new PrefixChanger();
    pc.run(args);
  }
}
