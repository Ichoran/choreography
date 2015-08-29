/** This file copyright 2008 Rex Kerr, Nicholas Swierczek, and the Howard Hughes Medical Institute.
  * Copyright 2015 Calico Life Sciences LLC (author Rex Kerr)
  * Distributed under the LGPL2.1 license (or GPL2.1 with classpath exception)
  */

package mwt.numerics;

public class Vec2F {
  public float x,y;
  
  public static Vec2F zero() { return new Vec2F(); }
  
  public Vec2F() { x=y=0; }
  public Vec2F(float X,float Y) { x=X; y=Y; }
  public Vec2F(Vec2F v) { x=v.x; y=v.y; }
  public Vec2F(String A,String B) throws NumberFormatException { x = Float.parseFloat(A); y = Float.parseFloat(B); }
  
  public boolean isSame(Vec2F v) { return x==v.x && y==v.y; }
  public boolean isSimilar(Vec2F v,float far)
  {
    float dx = x - v.x;
    if (dx<0) dx=-dx;
    float dy = y - v.y;
    if (dy<0) dy=-dy;
    if (far<Math.ulp(dx)) far=Math.ulp(dx);
    if (far<Math.ulp(dy)) far=Math.ulp(dy);
    return dx<far && dy<far;
  }
  public boolean equals(Object o)
  {
    return (o instanceof Vec2F) && isSame( (Vec2F)o );
  }
  
  public Vec2F copy() { return new Vec2F(x,y); }
  
  public Vec2S toS() { return new Vec2S((short)Math.round(x),(short)Math.round(y)); }
  public Vec2I toI() { return new Vec2I(Math.round(x),Math.round(y)); }
  public Vec2D toD() { return new Vec2D(x,y); }

  public Vec2F opPlus(float f) { return new Vec2F(f+x,f+y); }
  public Vec2F opPlus(Vec2F v) { return new Vec2F(x+v.x,y+v.y); }
  public Vec2F opMinus() { return new Vec2F(-x,-y); }
  public Vec2F opMinus(float f) { return new Vec2F(x-f,y-f); }
  public Vec2F opMinus(Vec2F v) { return new Vec2F(x-v.x,y-v.y); }
  public Vec2F opTimes(float f) { return new Vec2F(x*f,y*f); }
  public Vec2F opTimes(Vec2F v) { return new Vec2F(x*v.x , y*v.y); }
  public Vec2F opDivide(float f) { return new Vec2F(x/f,y/f); }
  public Vec2F opDivide(Vec2F v) { return new Vec2F(x/v.x , y/v.y); }
  public Vec2F opCross(Vec2F v) { return new Vec2F(x*v.y,-y*v.x); }
  public Vec2F opRound() { return new Vec2F( Math.round(x) , Math.round(y) ); }
  public Vec2F opWeightedSum(float w1,float w2,Vec2F v) { return new Vec2F(w1*x+w2*v.x , w1*y+w2*v.y); }
  
  public Vec2F eq(Vec2D v) { x=(float)v.x; y=(float)v.y; return this; }
  public Vec2F eq(Vec2F v) { x=v.x; y=v.y; return this; }
  public Vec2F eq(Vec2I v) { x=v.x; y=v.y; return this; }
  public Vec2F eq(Vec2S v) { x=v.x; y=v.y; return this; }
  public Vec2F eq(float X, float Y) { x=X; y=Y; return this; }
  public Vec2F eqAngle(float f) { x=(float)Math.cos(f); y=(float)Math.sin(f); return this; }
  public Vec2F eqPlus(float f) { x+=f; y+=f; return this; }
  public Vec2F eqPlus(Vec2F v) { x+=v.x; y+=v.y; return this; }
  public Vec2F eqMinus() { x=-x; y=-y; return this; }
  public Vec2F eqMinus(float f) { x-=f; y-=f; return this; }
  public Vec2F eqMinus(Vec2F v) { x-=v.x; y-=v.y; return this; }
  public Vec2F eqTimes(float f) { x*=f; y*=f; return this; }
  public Vec2F eqTimes(Vec2F v) { x*=v.x; y*=v.y; return this; }
  public Vec2F eqDivide(float f) { x/=f; y/=f; return this; }
  public Vec2F eqDivide(Vec2F v) { x/=v.x; y/=v.y; return this; }
  public Vec2F eqCross(Vec2F v) { x *= v.y; y*=-v.x; return this; }
  public Vec2F eqNorm() { float L=(float)Math.sqrt(x*x + y*y); if (L>0) { x/=L; y/=L; } else { x=y=0; } return this; }
  public Vec2F eqRound() { x = Math.round(x); y = Math.round(y); return this; }
  public Vec2F eqWeightedSum(float w1,float w2,Vec2F v) { x = w1*x + w2*v.x; y = w1*y + w2*v.y; return this; }
  
  public float dot(Vec2F v) { return x*v.x + y*v.y; }
  public float X(Vec2F v) { return x*v.y - y*v.x; }
  public float length2() { return x*x + y*y; }
  public float length() { return (float)Math.sqrt(x*x + y*y); }
  public float angle() { return (float)Math.atan2(y,x); }
  public float dist2(Vec2F v) { return (x-v.x)*(x-v.x) + (y-v.y)*(y-v.y); }
  public float dist(Vec2F v) { return (float)Math.sqrt( (x-v.x)*(x-v.x) + (y-v.y)*(y-v.y) ); }
  public float unitDot(Vec2F v) { return (x*v.x+y*v.y)/(float)Math.sqrt( (x*x+y*y) * (v.x*v.x + v.y*v.y) ); }
  public float unitX(Vec2F v) { return (x*v.y-y*v.x)/(float)Math.sqrt( (x*x+y*y) * (v.x*v.x + v.y*v.y) ); }

  @Override public String toString() { return "(" + x + "," + y + ")"; }
}

