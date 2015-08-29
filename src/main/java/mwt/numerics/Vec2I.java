/** This file copyright 2008 Rex Kerr, Nicholas Swierczek, and the Howard Hughes Medical Institute.
  * Copyright 2015 Calico Life Sciences LLC (author Rex Kerr)
  * Distributed under the LGPL2.1 license (or GPL2.1 with classpath exception)
  */

package mwt.numerics;

public class Vec2I {
  public int x,y;
  
  public static Vec2I zero() { return new Vec2I(); }
  
  public Vec2I() { x=y=0; }
  public Vec2I(int X,int Y) { x=X; y=Y; }
  public Vec2I(Vec2I v) { x=v.x; y=v.y; }
  public Vec2I(String A,String B) throws NumberFormatException { x = Integer.parseInt(A); y = Integer.parseInt(B); }
  
  public boolean isSame(Vec2I v) { return x==v.x && y==v.y; }
  public boolean isSimilar(Vec2I v,int far)
  {
    int dx = x - v.x;
    if (dx<0) dx=-dx;
    int dy = y - v.y;
    if (dy<0) dy=-dy;
    if (far<1) far=1;
    return dx<far && dy<far;
  }
  public boolean equals(Object o)
  {
    return (o instanceof Vec2I) && isSame( (Vec2I)o );
  }
  
  public Vec2I copy() { return new Vec2I(x,y); }
  
  public Vec2S toS() { return new Vec2S((short)x,(short)y); }
  public Vec2F toF() { return new Vec2F(x,y); }
  public Vec2D toD() { return new Vec2D(x,y); }
  
  public Vec2I opPlus(int f) { return new Vec2I(f+x,f+y); }
  public Vec2I opPlus(Vec2I v) { return new Vec2I(x+v.x,y+v.y); }
  public Vec2I opMinus() { return new Vec2I(-x,-y); }
  public Vec2I opMinus(int f) { return new Vec2I(x-f,y-f); }
  public Vec2I opMinus(Vec2I v) { return new Vec2I(x-v.x,y-v.y); }
  public Vec2I opTimes(int f) { return new Vec2I(x*f,y*f); }
  public Vec2I opTimes(Vec2I v) { return new Vec2I(x*v.x , y*v.y); }
  public Vec2I opDivide(int f) { return new Vec2I(x/f,y/f); }
  public Vec2I opDivide(Vec2I v) { return new Vec2I(x/v.x , y/v.y); }
  public Vec2I opCross(Vec2I v) { return new Vec2I(x*v.y,-y*v.x); }
  
  public Vec2I eq(Vec2D v) { x=(int)Math.round(v.x); y=(int)Math.round(v.y); return this; }
  public Vec2I eq(Vec2F v) { x=Math.round(v.x); y=Math.round(v.y); return this; }
  public Vec2I eq(Vec2I v) { x=v.x; y=v.y; return this; }
  public Vec2I eq(Vec2S v) { x=v.x; y=v.y; return this; }
  public Vec2I eq(int X,int Y) { x=X; y=Y; return this; }
  public Vec2I eqPlus(int f) { x+=f; y+=f; return this; }
  public Vec2I eqPlus(Vec2I v) { x+=v.x; y+=v.y; return this; }
  public Vec2I eqMinus() { x=-x; y=-y; return this; }
  public Vec2I eqMinus(int f) { x-=f; y-=f; return this; }
  public Vec2I eqMinus(Vec2I v) { x-=v.x; y-=v.y; return this; }
  public Vec2I eqTimes(int f) { x*=f; y*=f; return this; }
  public Vec2I eqTimes(Vec2I v) { x*=v.x; y*=v.y; return this; }
  public Vec2I eqDivide(int f) { x/=f; y/=f; return this; }
  public Vec2I eqDivide(Vec2I v) { x/=v.x; y/=v.y; return this; }
  public Vec2I eqCross(Vec2I v) { x *= v.y; y*=-v.x; return this; }
  public Vec2I eqNorm() { int L=(int)Math.round(Math.sqrt(x*x + y*y)); if (L>0) { x/=L; y/=L; } else { x=y=0; } return this; }
  
  public int dot(Vec2I v) { return x*v.x + y*v.y; }
  public int X(Vec2I v) { return x*v.y - y*v.x; }
  public int length2() { return x*x + y*y; }
  public int length() { return (int)Math.round(Math.sqrt(x*x + y*y)); }
  public int dist2(Vec2I v) { return (x-v.x)*(x-v.x) + (y-v.y)*(y-v.y); }
  public int dist(Vec2I v) { return (int)Math.round(Math.sqrt( (x-v.x)*(x-v.x) + (y-v.y)*(y-v.y) )); }
  public int unitDot(Vec2I v) { return (int)Math.round(( (x*v.x+y*v.y)/Math.sqrt( (x*x+y*y) * (v.x*v.x + v.y*v.y) ) )); }
  public int unitX(Vec2I v) { return (int)Math.round(( (x*v.y-y*v.x)/Math.sqrt( (x*x+y*y) * (v.x*v.x + v.y*v.y) ) )); }

  @Override public String toString() { return "(" + x + "," + y + ")"; }
}

