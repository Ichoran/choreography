/** This file copyright 2008 Rex Kerr, Nicholas Swierczek, and the Howard Hughes Medical Institute.
  * Copyright 2015 Calico Life Sciences LLC (author Rex Kerr)
  * Distributed under the LGPL2.1 license (or GPL2.1 with classpath exception)
  */

package mwt.numerics;

public class Vec2S {
  public short x,y;
 
  public static Vec2S zero() { return new Vec2S(); }
  
  public Vec2S() { x=y=0; }
  public Vec2S(short X,short Y) { x=X; y=Y; }
  public Vec2S(Vec2S v) { x=v.x; y=v.y; }
  public Vec2S(String A,String B) throws NumberFormatException { x = Short.parseShort(A); y = Short.parseShort(B); }
  
  public boolean isSame(Vec2S v) { return x==v.x && y==v.y; }
  public boolean isSimilar(Vec2S v,short far) {
    int dx = x - v.x;
    if (dx<0) dx=-dx;
    int dy = y - v.y;
    if (dy<0) dy=-dy;
    if (far<1) far=1;
    return (short)dx<far && (short)dy<far;
  }
  public boolean equals(Object o) {
    return (o instanceof Vec2S) && isSame( (Vec2S)o );
  }

  public Vec2S copy() { return new Vec2S(x,y); }
  
  public Vec2I toI() { return new Vec2I(x,y); }
  public Vec2F toF() { return new Vec2F(x,y); }
  public Vec2D toD() { return new Vec2D(x,y); }
  
  public Vec2S opPlus(short f) { return new Vec2S((short)(f+x),(short)(f+y)); }
  public Vec2S opPlus(Vec2S v) { return new Vec2S((short)(x+v.x),(short)(y+v.y)); }
  public Vec2S opMinus() { return new Vec2S((short)(-x),(short)(-y)); }
  public Vec2S opMinus(short f) { return new Vec2S((short)(x-f),(short)(y-f)); }
  public Vec2S opMinus(Vec2S v) { return new Vec2S((short)(x-v.x),(short)(y-v.y)); }
  public Vec2S opTimes(short f) { return new Vec2S((short)(x*f),(short)(y*f)); }
  public Vec2S opTimes(Vec2S v) { return new Vec2S((short)(x*v.x),(short)(y*v.y)); }
  public Vec2S opDivide(short f) { return new Vec2S((short)(x/f),(short)(y/f)); }
  public Vec2S opDivide(Vec2S v) { return new Vec2S((short)(x/v.x),(short)(y/v.y)); }
  public Vec2S opCross(Vec2S v) { return new Vec2S((short)(x*v.y),(short)(-y*v.x)); }
  
  public Vec2S eq(Vec2D v) { x=(short)Math.round(v.x); y=(short)Math.round(v.y); return this; }
  public Vec2S eq(Vec2F v) { x=(short)Math.round(v.x); y=(short)Math.round(v.y); return this; }
  public Vec2S eq(Vec2I v) { x=(short)v.x; y=(short)v.y; return this; }
  public Vec2S eq(Vec2S v) { x=v.x; y=v.y; return this; }
  public Vec2S eq(short X,short Y) { x=X; y=Y; return this; }
  public Vec2S eqPlus(short f) { x+=f; y+=f; return this; }
  public Vec2S eqPlus(Vec2S v) { x+=v.x; y+=v.y; return this; }
  public Vec2S eqMinus() { x=(short)-x; y=(short)-y; return this; }
  public Vec2S eqMinus(short f) { x-=f; y-=f; return this; }
  public Vec2S eqMinus(Vec2S v) { x-=v.x; y-=v.y; return this; }
  public Vec2S eqTimes(short f) { x*=f; y*=f; return this; }
  public Vec2S eqTimes(Vec2S v) { x*=v.x; y*=v.y; return this; }
  public Vec2S eqDivide(short f) { x/=f; y/=f; return this; }
  public Vec2S eqDivide(Vec2S v) { x/=v.x; y/=v.y; return this; }
  public Vec2S eqCross(Vec2S v) { x *= v.y; y*=-v.x; return this; }
  public Vec2S eqNorm() { short L=(short)Math.round(Math.sqrt(x*x + y*y)); if (L>0) { x/=L; y/=L; } else { x=y=0; } return this; }
  
  public short dot(Vec2S v) { return (short)( x*v.x + y*v.y ); }
  public short X(Vec2S v) { return (short)( x*v.y - y*v.x ); }
  public short length2() { return (short)( x*x + y*y ); }
  public short length() { return (short)Math.round(Math.sqrt(x*x + y*y)); }
  public short dist2(Vec2S v) { return (short)( (x-v.x)*(x-v.x) + (y-v.y)*(y-v.y) ); }
  public short dist(Vec2S v) { return (short)Math.round(Math.sqrt( (x-v.x)*(x-v.x) + (y-v.y)*(y-v.y) )); }
  public short unitDot(Vec2S v) { return (short)Math.round(( (x*v.x+y*v.y)/Math.sqrt( (x*x+y*y) * (v.x*v.x + v.y*v.y) ) )); }
  public short unitX(Vec2S v) { return (short)Math.round(( (x*v.y-y*v.x)/Math.sqrt( (x*x+y*y) * (v.x*v.x + v.y*v.y) ) )); }

  @Override public String toString() { return "(" + x + "," + y + ")"; }
}

