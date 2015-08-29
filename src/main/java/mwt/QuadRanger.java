/* QuadRanger.java - Puts x,y data into a quad-tree for easier lookup
 * Copyright 2010 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015 Calico Life Sciences LLC (authored by Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt;
 
import java.util.*;

import mwt.numerics.*;

public class QuadRanger
{
  
  // Will use our own custom interval so we can get touch/fuse methods
  public static class Interval implements Comparable<Interval>
  {
    int x0;
    int x1;
    public Interval(int a,int b) { x0=a; x1=b; }
    public Interval(Interval iv) { x0=iv.x0; x1=iv.x1; }
    public Interval(Interval iv1,Interval iv2) { x0 = Math.min(iv1.x0,iv2.x0); x1 = Math.max(iv1.x1,iv2.x1); }
    public int compareTo(Interval iv)
    {
      if (x0 < iv.x0) return -1;
      else if (x0 > iv.x0) return 1;
      else if (x1 < iv.x1) return -1;
      else if (x1 > iv.x1) return 1;
      else return 0;
    }
    public boolean touches(Interval iv) { return !(x1<iv.x0 || iv.x1<x0); }
    public void fuse(Interval iv) { x0 = Math.min(x0,iv.x0); x1 = Math.max(x1,iv.x1); }
    public boolean fuseTouching(Interval iv)
    {
      if (touches(iv)) { fuse(iv); return true; }
      else return false;
    }
  }
  
  List<Interval> included; // Indices of raw x,y data that are within this quad
  Vec2F xy0;  // lower bounds
  Vec2F xy1;  // upper bounds
  QuadRanger parent;    // Parent quad if any
  QuadRanger[] children;   // Child quads if any
  
  // Create a new quad around the data given with the x and y ranges specified.
  // Subdivide into subquads until each one has no more than max_pts, or max_depth is reached.
  public QuadRanger(float[] x,float[] y,int max_pts,int max_depth,QuadRanger ancestor,Vec2F x_range,Vec2F y_range,List<Interval> valid_bits)
  {
    included = new ArrayList<Interval>();
    xy0 = new Vec2F();
    xy1 = new Vec2F();
    parent = ancestor;
    children = null;
    
    initialize(x,y,max_pts,max_depth,x_range,y_range,valid_bits);
  }
  
  // Simpler constructor that takes all the x,y data (not just that within specified ranges)
  public QuadRanger(float[] x,float[] y,int max_pts,int max_depth)
  {
    included = new ArrayList<Interval>();
    xy0 = new Vec2F();
    xy1 = new Vec2F();
    parent = null;
    children = null;
    
    initialize(x,y,max_pts,max_depth,null,null,null);
  }
  
  // This actually does the work of finding the indices of x,y that fit within each quad.
  void initialize(float[] x,float[] y,int max_pts,int max_depth,Vec2F x_range,Vec2F y_range,List<Interval> valid_bits)
  {
    if (x==null || y==null) return;
    if (x.length==0 || y.length==0) return;
    
    // If we're not given the x and y ranges, we'll create them
    if (x_range==null || y_range==null)
    {
      x_range = new Vec2F(x[0],x[0]);
      y_range = new Vec2F(y[0],y[0]);
      for (float f : x)
      {
        if (Float.isNaN(f)) continue;
        else if (f < x_range.x) x_range.x = f;
        else if (f > x_range.y) x_range.y = f;
      }
      for (float f : y)
      {
        if (Float.isNaN(f)) continue;
        else if (f < y_range.x) y_range.x = f;
        else if (f > y_range.y) y_range.y = f;
      }
    }
    xy0.x = x_range.x;  // Min value of x coord
    xy0.y = y_range.x;  // Min value of y coord
    xy1.x = x_range.y;  // Max value of x coord
    xy1.y = y_range.y;  // Max value of y coord
    
    // If we're not given valid intervals, we'll assume the whole thing is valid
    if (valid_bits==null)
    {
      valid_bits = new ArrayList<Interval>();
      valid_bits.add( new Interval(0 , Math.min(x.length,y.length)) );
    }
    
    // Inefficient to run through this if we made everything in range, but easier to do it anyway
    int count = 0;
    for (Interval iv : valid_bits)
    {
      Interval valid = null;
      for (int i=iv.x0 ; i<iv.x1 ; i++)
      {
        if (Float.isNaN(x[i]) || Float.isNaN(y[i]) || x[i] < xy0.x || x[i] > xy1.x || y[i] < xy0.y || y[i] > xy1.y)
        {
          if (valid!=null)
          {
            valid.x1 = i;
            included.add(valid);
            valid = null;
          }
        }
        else
        {
          count++;
          if (valid!=null) valid.x1 = i;
          else valid = new Interval(i,i);
        }
      }
      if (valid!=null)
      {
        valid.x1 = iv.x1;
        included.add(valid);
      }
    }
    
    // Make children if we need to
    if (max_depth > 0 && count > max_pts)
    {
      Vec2F middle;
      middle = new Vec2F(xy0).eqPlus(xy1).eqTimes(0.5f);   // middle = (xy0+xy1)/2
      children = new QuadRanger[4];
      children[0] = new QuadRanger(x,y,max_pts,max_depth-1,this,new Vec2F(xy0.x,middle.x),new Vec2F(xy0.y,middle.y),included);
      children[1] = new QuadRanger(x,y,max_pts,max_depth-1,this,new Vec2F(middle.x,xy1.x),new Vec2F(xy0.y,middle.y),included);
      children[2] = new QuadRanger(x,y,max_pts,max_depth-1,this,new Vec2F(xy0.x,middle.x),new Vec2F(middle.y,xy1.y),included);
      children[3] = new QuadRanger(x,y,max_pts,max_depth-1,this,new Vec2F(middle.x,xy1.x),new Vec2F(middle.y,xy1.y),included);
    }
  }
  
  public double boundedArea()
  {
    return (xy1.x-xy0.x)*(xy1.y-xy0.y);
  }
  
  // Add any new valid index ranges onto the existing list in valid_bits
  public List<Interval> accumulateValid(Vec2F xy_lo,Vec2F xy_hi,List<Interval> valid_bits)
  {
    if (xy_lo.x > xy1.x || xy_lo.y > xy1.y || xy_hi.x < xy0.x || xy_hi.y < xy0.y)  // Test range does not overlap us at all
    {
      if (valid_bits!=null) return valid_bits;  // No change to existing list if it exists
      else return new LinkedList<Interval>();   // If not, throw back an empty list
    }
    else
    {
      List<Interval> liv = (valid_bits==null) ? new LinkedList<Interval>() : valid_bits;
      
      if (included.size()==0) return liv;  // Nothing to add here in any case
      
      // Entirely contained or no children
      if (children==null || (xy_lo.x <= xy0.x && xy_lo.y <= xy0.y && xy_hi.x >= xy1.x && xy_hi.y >= xy1.y))
      {
        for (Interval iv : included) liv.add( new Interval(iv) );  // Add every interval we've got
        Collections.sort(liv);
        
        // Merge all the intervals
        ListIterator<Interval> iiv = liv.listIterator();
        if (iiv.hasNext())
        {
          Interval old_iv = iiv.next();
          Interval new_iv;
          while (iiv.hasNext())
          {
            new_iv = iiv.next();
            if (old_iv.fuseTouching(new_iv)) iiv.remove();
            else old_iv = new_iv;
          }
        }
      }
      else // From previous if, know that children!=null; let them add themselves
      {
        for (int i=0;i<children.length;i++) children[i].accumulateValid(xy_lo,xy_hi,liv);        
      }
      
      return liv;
    }
  }
  
  public String toText(String prefix)
  {
    String result = String.format("%s(%.3f,%.3f) (%.3f,%.3f)\n",prefix,xy0.x,xy0.y,xy1.x,xy1.y);
    if (included!=null)
    {
      String line = null;
      for (Interval iv : included)
      {
        if (line==null) line = String.format("%s# %d->%d",prefix,iv.x0,iv.x1);
        else line = line + String.format(" %d->%d",iv.x0,iv.x1);
        if (line.length() > 64)
        {
          result = result + line + "\n";
          line = null;
        }
      }
      if (line != null) result = result + line + "\n";
    }
    if (children != null)
    {
      for (int i=0 ; i<children.length ; i++)
      {
        result = result + children[i].toText( String.format("%s%d ",prefix,i) );
      }
    }
    return result;
  }
  public String toText() { return toText(""); }
  
  public static void unitTest() throws Exception
  {
    float x_test[] = new float[32];
    float y_test[] = new float[32];
    x_test[0] = 0.0f;  y_test[0] = 0.0f;
    x_test[1] = 0.0f;  y_test[1] = 1.0f;
    x_test[2] = 1.0f;  y_test[2] = 0.0f;
    x_test[3] = 1.0f;  y_test[3] = 1.0f;
    for (int i=4 ; i<32 ; i++)
    {
      x_test[i] = (i*i*0.779f) - (float)Math.floor(i*i*0.779f);
      y_test[i] = (i*i*i*0.3113f) - (float)Math.floor(i*i*i*0.3113f);
    }
    
    for (int i=0;i<32;i++) System.out.printf("  %02d  %5.3f  %5.3f\n",i,x_test[i],y_test[i]);
      
    QuadRanger qr = new QuadRanger(x_test , y_test , 4 , 10);
    List<Interval> liv = qr.accumulateValid( new Vec2F(0.4f,0.4f) , new Vec2F(0.6f,0.6f) , null );
    System.out.println( qr.toText() );
    for (Interval iv : liv) System.out.printf("...%d->%d\n",iv.x0,iv.x1);
    
    if (liv.size() != 7) throw new Exception("Expected 7 points near range, found " + liv.size());
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

