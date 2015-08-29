/** This file copyright 2008 Nicholas Swierczek, Rex Kerr, and the Howard Hughes Medical Institute.
  * Copyright 2015 Calico Life Sciences LLC (author Rex Kerr)
  * Distributed under the LGPL2.1 license (or GPL2.1 with classpath exception)
  */


package mwt.numerics;

import java.util.*;

// An array of floating point data backed by either by a float[] or a double[].
// Good for real-valued computations where data may come in in either format.
public class RealArray
{
  float[] dataF;
  double[] dataD;
  
  public RealArray(float[] f)
  {
    dataF = f;
    dataD = null;
  }
  public RealArray(double[] d)
  {
    dataF = null;
    dataD = d;
  }
  public double getSmart(int i)
  {
    if (dataD!=null && dataD.length>0)
    {
      if (i<0) i = dataD.length+i;
      if (i<0) i = 0;
      else if (i>=dataD.length) i = dataD.length-1;
      return dataD[i];
    }
    else if (dataF!=null && dataF.length>0)
    {
      if (i<0) i = dataF.length+i;
      if (i<0) i = 0;
      else if (i>=dataF.length) i = dataF.length-1;
      return (double)dataF[i];
    }
    else return Double.NaN;
  }
  public double getD(int i)
  {
    if (dataD!=null) return dataD[i];
    if (dataF!=null) return (double)dataF[i];
    return Double.NaN;
  }
  public float getF(int i)
  {
    if (dataD!=null) return (float)dataD[i];
    if (dataF!=null) return dataF[i];
    return Float.NaN;
  }
  public void set(int i,double d)
  {
    if (dataD!=null) dataD[i] = d;
    else if (dataF!=null) dataF[i] = (float)d;
  }
  public void set(int i,float f)
  {
    if (dataD!=null) dataD[i] = (double)f;
    else if (dataF!=null) dataF[i] = f;
  }
  public int length() { return (dataD!=null) ? dataD.length : ( (dataF!=null) ? dataF.length : 0 ); }
  public int binarySearch(double d)
  {
    if (dataD!=null) return Arrays.binarySearch(dataD,d);
    else if (dataF!=null) return Arrays.binarySearch(dataF,(float)d);
    else return 0;
  }
  public int binarySearch(float f)
  {
    if (dataD!=null) return Arrays.binarySearch(dataD,(double)f);
    else if (dataF!=null) return Arrays.binarySearch(dataF,f);
    else return 0;
  }
  public int binaryClosest(double d)
  {
    if (dataD!=null) {
      int i0,i1,i;
      for (i0=0 , i1=dataD.length-1 ; i1-i0 > 1 ; ) {
        i = (i0+i1)/2;
        if (dataD[i] < d) i0 = i;
        else i1 = i;
      }
      if (Math.abs(dataD[i0]-d)<Math.abs(dataD[i1]-d)) return i0;
      else return i1;
    }
    else if (dataF!=null) return binaryClosest((float)d);
    else return -1;
  }
  public int binaryClosest(float f)
  {
    if (dataD!=null) return binaryClosest((double)f);
    else if (dataF!=null) {
      int i0,i1,i;
      for (i0=0 , i1=dataF.length-1 ; i1-i0>1 ; ) {
        i = (i0+i1)/2;
        if (dataF[i]<f) i0 = i;
        else i1 = i;
      }
      if (Math.abs(dataF[i0]-f) < Math.abs(dataF[i1]-f)) return i0;
      else return i1;
    }
    else return -1;
  }
  public void sortAscending()
  {
    if (dataD!=null) Arrays.sort(dataD);
    if (dataF!=null) Arrays.sort(dataF);
  }
  public boolean isDouble() { return dataD!=null; }
  public boolean isFloat() { return dataF!=null; }
}

