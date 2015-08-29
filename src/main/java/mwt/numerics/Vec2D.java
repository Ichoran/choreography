/** This file copyright 2008 Rex Kerr, Nicholas Swierczek, and the Howard Hughes Medical Institute.
  * Distributed under the LGPL2.1 license (or GPL2.1 with classpath exception)
  */

package mwt.numerics;

public class Vec2D {
  public double x,y;
  
  public static Vec2D zero() { return new Vec2D(); }
  
  public Vec2D() { x=y=0; }
  public Vec2D(double X,double Y) { x=X; y=Y; }
  public Vec2D(Vec2D v) { x=v.x; y=v.y; }
  public Vec2D(String A,String B) throws NumberFormatException { x = Double.parseDouble(A); y = Double.parseDouble(B); }
  
  public boolean isSame(Vec2D v) { return x==v.x && y==v.y; }
  public boolean isSimilar(Vec2D v,double far)
  {
    double dx = x - v.x;
    if (dx<0) dx=-dx;
    double dy = y - v.y;
    if (dy<0) dy=-dy;
    if (far<Math.ulp(dx)) far=Math.ulp(dx);
    if (far<Math.ulp(dy)) far=Math.ulp(dy);
    return dx<far && dy<far;
  }
  public boolean equals(Object o)
  {
    return (o instanceof Vec2D) && isSame( (Vec2D)o );
  }
  
  public Vec2D copy() { return new Vec2D(x,y); }
  
  public Vec2S toS() { return new Vec2S((short)Math.round(x),(short)Math.round(y)); }
  public Vec2I toI() { return new Vec2I((int)Math.round(x),(int)Math.round(y)); }
  public Vec2F toF() { return new Vec2F((float)x,(float)y); }

  public Vec2D opPlus(double f) { return new Vec2D(f+x,f+y); }
  public Vec2D opPlus(Vec2D v) { return new Vec2D(x+v.x,y+v.y); }
  public Vec2D opMinus() { return new Vec2D(-x,-y); }
  public Vec2D opMinus(double f) { return new Vec2D(x-f,y-f); }
  public Vec2D opMinus(Vec2D v) { return new Vec2D(x-v.x,y-v.y); }
  public Vec2D opTimes(double f) { return new Vec2D(x*f,y*f); }
  public Vec2D opTimes(Vec2D v) { return new Vec2D(x*v.x , y*v.y); }
  public Vec2D opDivide(double f) { return new Vec2D(x/f,y/f); }
  public Vec2D opDivide(Vec2D v) { return new Vec2D(x/v.x , y/v.y); }
  public Vec2D opCross(Vec2D v) { return new Vec2D(x*v.y,-y*v.x); }
  public Vec2D opRound() { return new Vec2D( Math.round(x) , Math.round(y) ); }
  public Vec2D opWeightedSum(double w1,double w2,Vec2D v) { return new Vec2D(w1*x+w2*v.x , w1*y+w2*v.y); }
  
  public Vec2D eq(Vec2D v) { x=v.x; y=v.y; return this; }
  public Vec2D eq(Vec2F v) { x=v.x; y=v.y; return this; }
  public Vec2D eq(Vec2I v) { x=v.x; y=v.y; return this; }
  public Vec2D eq(Vec2S v) { x=v.x; y=v.y; return this; }
  public Vec2D eq(double X,double Y) { x=X; y=Y; return this; }
  public Vec2D eqAngle(double f) { x=Math.cos(f); y=Math.sin(f); return this; }
  public Vec2D eqPlus(double f) { x+=f; y+=f; return this; }
  public Vec2D eqPlus(Vec2D v) { x+=v.x; y+=v.y; return this; }
  public Vec2D eqMinus() { x=-x; y=-y; return this; }
  public Vec2D eqMinus(double f) { x-=f; y-=f; return this; }
  public Vec2D eqMinus(Vec2D v) { x-=v.x; y-=v.y; return this; }
  public Vec2D eqTimes(double f) { x*=f; y*=f; return this; }
  public Vec2D eqTimes(Vec2D v) { x*=v.x; y*=v.y; return this; }
  public Vec2D eqDivide(double f) { x/=f; y/=f; return this; }
  public Vec2D eqDivide(Vec2D v) { x/=v.x; y/=v.y; return this; }
  public Vec2D eqCross(Vec2D v) { x *= v.y; y*=-v.x; return this; }
  public Vec2D eqNorm() { double L=Math.sqrt(x*x + y*y); if (L>0) { x/=L; y/=L; } else { x=y=0; } return this; }
  public Vec2D eqRound() { x = Math.round(x); y = Math.round(y); return this; }
  public Vec2D eqWeightedSum(double w1,double w2,Vec2D v) { x = w1*x + w2*v.x; y = w1*y + w2*v.y; return this; }
  
  public double dot(Vec2D v) { return x*v.x + y*v.y; }
  public double X(Vec2D v) { return x*v.y - y*v.x; }
  public double length2() { return x*x + y*y; }
  public double length() { return Math.sqrt(x*x + y*y); }
  public double angle() { return Math.atan2(y,x); }
  public double dist2(Vec2D v) { return (x-v.x)*(x-v.x) + (y-v.y)*(y-v.y); }
  public double dist(Vec2D v) { return Math.sqrt( (x-v.x)*(x-v.x) + (y-v.y)*(y-v.y) ); }
  public double unitDot(Vec2D v) { return (x*v.x+y*v.y)/Math.sqrt( (x*x+y*y) * (v.x*v.x + v.y*v.y) ); }
  public double unitX(Vec2D v) { return (x*v.y-y*v.x)/Math.sqrt( (x*x+y*y) * (v.x*v.x + v.y*v.y) ); }

  @Override public String toString() { return "(" + x + "," + y + ")"; }
}

