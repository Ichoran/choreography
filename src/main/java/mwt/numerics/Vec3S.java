/** This file copyright 2008 Rex Kerr, Nicholas Swierczek, and the Howard Hughes Medical Institute.
  * Copyright 2015 Calico Life Sciences LLC (author Rex Kerr)
  * Distributed under the LGPL2.1 license (or GPL2.1 with classpath exception)
  */

package mwt.numerics;

public class Vec3S {
  public short x,y,z;
  
  public static Vec3S zero() { return new Vec3S(); }
  
  public Vec3S() { x=y=z=0; }
  public Vec3S(short X,short Y,short Z) { x=X; y=Y; z=Z; }
  public Vec3S(Vec3S v) { x=v.x; y=v.y; z=v.z; }
  public Vec3S(String A,String B, String C) throws NumberFormatException { x = Short.parseShort(A); y = Short.parseShort(B); z = Short.parseShort(C); }
  
  public boolean isSame(Vec3S v) { return x==v.x && y==v.y && z==v.z; }
  public boolean isSimilar(Vec3S v,short far)
  {
    int dx = x - v.x;
    if (dx<0) dx=-dx;
    int dy = y - v.y;
    if (dy<0) dy=-dy;
    int dz = z - v.z;
    if( dz<0) dz=-dz;
    if (far<1) far=1;
    return (short)dx<far && (short)dy<far && (short)dz<far;
  }
  
  @Override
  public boolean equals(Object o)
  {
    return (o instanceof Vec3S) && isSame( (Vec3S)o );
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 29 * hash + this.x;
    hash = 29 * hash + this.y;
    hash = 29 * hash + this.z;
    return hash;
  }
  
  public Vec3S copy() { return new Vec3S(x,y,z); }
  
  public Vec3I toI() { return new Vec3I(x,y,z); }
  public Vec3D toD() { return new Vec3D((double)(x),(double)(y),(double)(z)); }
  public Vec3F toF() { return new Vec3F((float)x,(float)y,(float)z); }

  public Vec3S opPlus(short f) { return new Vec3S((short)(f+x),(short)((short)(f+y)),(short)(f+z)); }
  public Vec3S opPlus(Vec3S v) { return new Vec3S((short)(x+v.x),(short)(y+v.y),(short)(z+v.z)); }
  public Vec3S opMinus() { return new Vec3S((short)-x,(short)-y,(short)-z); }
  public Vec3S opMinus(short f) { return new Vec3S((short)(x-f),(short)(y-f),(short)(z-f)); }
  public Vec3S opMinus(Vec3S v) { return new Vec3S((short)(x-v.x),(short)(y-v.y),(short)(z-v.z)); }
  public Vec3S opTimes(short f) { return new Vec3S((short)(x*f),(short)(y*f),(short)(z*f)); }
  public Vec3S opTimes(Vec3S v) { return new Vec3S((short)(x*v.x) , (short)(y*v.y),(short)(z*v.z)); }
  public Vec3S opDivide(short f) { return new Vec3S((short)(x/f),(short)(y/f),(short)(z/f)); }
  public Vec3S opDivide(Vec3S v) { return new Vec3S((short)(x/v.x) , (short)(y/v.y), (short)(z/v.z)); }
  public Vec3S opCross(Vec3S v) { return new Vec3S((short)(y*v.z - z*v.y), (short)(z*v.x - x*v.z), (short)(x*v.y - y*v.x)); }
  public Vec3S opNorm() { short L=(short)(Math.sqrt(x*x + y*y + z*z)+0.5); if (L>0) { return new Vec3S(x/=L, y/=L, z/=L); } else { return new Vec3S((short)0,(short)0,(short)0); } }
  
  public Vec3S eq(Vec3D v) { x=(short)(v.x+0.5); y=(short)(v.y+0.5); z=(short)(v.z+0.5); return this; }
  public Vec3S eq(Vec3F v) { x=(short)(v.x+0.5); y=(short)(v.y+0.5); z=(short)(v.z+0.5); return this; }
  public Vec3S eq(Vec3I v) { x=(short)v.x; y=(short)v.y; z=(short)v.z; return this; }
  public Vec3S eq(Vec3S v) { x=v.x; y=v.y; z=v.z; return this; }
  public Vec3S eq(short X,short Y,short Z) { x=X; y=Y; z=Z; return this; }
  public Vec3S eqPlus(short f) { x+=f; y+=f; z+=f; return this; }
  public Vec3S eqPlus(Vec3S v) { x+=v.x; y+=v.y; z+=v.z; return this; }
  public Vec3S eqMinus() { x=(short)-x; y=(short)-y; z=(short)-z; return this; }
  public Vec3S eqMinus(short f) { x-=f; y-=f; z-=f; return this; }
  public Vec3S eqMinus(Vec3S v) { x-=v.x; y-=v.y; z-=v.z; return this; }
  public Vec3S eqTimes(short f) { x*=f; y*=f; z*=f; return this; }
  public Vec3S eqTimes(Vec3S v) { x*=v.x; y*=v.y; z*=v.z; return this; }
  public Vec3S eqDivide(short f) { x/=f; y/=f; z/=f; return this; }
  public Vec3S eqDivide(Vec3S v) { x/=v.x; y/=v.y; z/=v.z; return this; }
  public Vec3S eqCross(Vec3S v) { short xx = x, yy = y; x = (short)(yy*v.z - z*v.y); y = (short)(z*v.x - xx*v.z); z = (short)(xx*v.y - yy*v.x); return this; }
  public Vec3S eqNorm() { short L=(short)(Math.sqrt(x*x + y*y + z*z)+0.5); if (L>0) { x/=L; y/=L; z/=L; } else { x=y=z=0; } return this; }
  
  public short dot(Vec3S v) { return (short)(x*v.x + y*v.y + z*v.z); }
  public short length2() { return (short)(x*x + y*y + z*z); }
  public short length() { return (short)(Math.sqrt(x*x + y*y + z*z)+0.5); }
  public short dist2(Vec3S v) { return (short)((x-v.x)*(x-v.x) + (y-v.y)*(y-v.y) + (z-v.z)*(z-v.z)); }
  public short dist(Vec3S v) { return (short)(0.5 + Math.sqrt( (x-v.x)*(x-v.x) + (y-v.y)*(y-v.y) + (z-v.z)*(z-v.z) )); }
  public short unitDot(Vec3S v) { return (short)(0.5 + (x*v.x+y*v.y+z*v.z)/Math.sqrt( (x*x+y*y+z*z) * (v.x*v.x + v.y*v.y + v.z*v.z) )); } 

  @Override
  public String toString() { return "(" + x + "," + y + "," + z + ")"; }
}

