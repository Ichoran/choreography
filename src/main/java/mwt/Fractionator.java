/* Fractionator.java - Subdivides data for rapid display in graphical maps
 * Copyright 2010 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015 Calico Life Sciences LLC (authored by Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt;
 
import java.util.*;

public class Fractionator
{
  public float data[][];
  public float diffsize[];
  int binsize;
  
  public Fractionator(float original[],int binning,int max_n)
  {
    binsize = binning;
    if (binsize < 2) binsize = 2;
    if (max_n < 1) max_n = 1;
    
    // Want to bin original until length is no more than max_n...count how many this is
    int n_levels = 1;
    int current_length = original.length;
    while ( max_n < current_length ) { n_levels++; current_length = (current_length + binsize - 1)/binsize; }
    
    data = new float[n_levels][];
    diffsize = new float[n_levels];
    data[0] = original;
    
    double sum_value;
    float temp_value;
    int n_value;
    int count;
    for (int i=1 ; i<n_levels ; i++)
    {
      data[i] = new float[ (data[i-1].length + binsize - 1)/binsize ];
      for (int j=0 ; j<data[i].length ; j++)
      {
        sum_value = 0.0;
        count = 0;
        for (n_value = 0 ; n_value<binsize && binsize*j + n_value < data[i-1].length ; n_value++)
        {
          temp_value = data[i-1][binsize*j + n_value];
          if (!Float.isNaN(temp_value))
          {
            sum_value += temp_value;
            count++;
          }
        }
        if (count>0) data[i][j] = (float)( sum_value / n_value );
        else data[i][j] = Float.NaN;
      }
    }
    for (int i=0 ; i<n_levels ; i++)
    {
      sum_value = 0.0;
      for (int j=1 ; j<data[i].length ; j++) sum_value += Math.abs( data[i][j] - data[i][j-1] );
      diffsize[i] = (data[i].length>1) ? (float)(sum_value/(data[i].length-1)) : 0.0f;
    }
  }
  
  public int depth() { return data.length; }
  public float[] getLevel(int level) { return data[level]; }
  public int binFactor(int level) { return (int) Math.round( Math.pow(binsize , level) ); }
  
  public static void unitTest() throws Exception
  {
    float[] raw = new float[64];
    
    for (int i=0;i<64;i++) raw[i] = i/63.0f;
    
    Fractionator f = new Fractionator(raw,2,4);
    for (int j=0 ; j<f.data.length ; j++)
    {
       for (int i=0 ; i<f.data[j].length ; i++) System.out.printf("%.3f ",f.data[j][i]);
       System.out.println();
    }
    
    if (f.data.length != 5) throw new Exception("Expected a hierarchy 5 deep but actually found " + f.data.length);
  }
  
  /*
  public static void main(String args[])
  {
    try { unitTest(); }
    catch (Exception e)
    {
      System.out.println("FAILED");
      System.out.println(e);
    }
  }
  */
}

