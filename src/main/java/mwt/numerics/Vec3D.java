/** This file copyright 2008 Rex Kerr, Nicholas Swierczek, and the Howard Hughes Medical Institute.
  * Copyright 2015 Calico Life Sciences LLC (author Rex Kerr)
  * Distributed under the LGPL2.1 license (or GPL2.1 with classpath exception)
  */

package mwt.numerics;

public class Vec3D {
  public double x,y,z;
  
  public static Vec3D zero() { return new Vec3D(); }
  
  public Vec3D() { x=y=z=0; }
  public Vec3D(double X,double Y,double Z) { x=X; y=Y; z=Z; }
  public Vec3D(Vec3D v) { x=v.x; y=v.y; z=v.z; }
  public Vec3D(String A,String B, String C) throws NumberFormatException { x = Double.parseDouble(A); y = Double.parseDouble(B); z = Double.parseDouble(C); }
  
  public boolean isSame(Vec3D v) { return x==v.x && y==v.y && z==v.z; }
  public boolean isSimilar(Vec3D v,double far) {
    double dx = x - v.x;
    if (dx<0) dx=-dx;
    double dy = y - v.y;
    if (dy<0) dy=-dy;
    double dz = z - v.z;
    if( dz<0) dz=-dz;
    if (far<Math.ulp(dx)) far=Math.ulp(dx);
    if (far<Math.ulp(dy)) far=Math.ulp(dy);
    if (far<Math.ulp(dz)) far=Math.ulp(dz);
    return dx<far && dy<far && dz<far;
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof Vec3D) && isSame( (Vec3D)o );
  }

  @Override
  public int hashCode() {
    int hash = 3;
    hash = 59 * hash + (int) (Double.doubleToLongBits(this.x) ^ (Double.doubleToLongBits(this.x) >>> 32));
    hash = 59 * hash + (int) (Double.doubleToLongBits(this.y) ^ (Double.doubleToLongBits(this.y) >>> 32));
    hash = 59 * hash + (int) (Double.doubleToLongBits(this.z) ^ (Double.doubleToLongBits(this.z) >>> 32));
    return hash;
  }
  
  public Vec3D copy() { return new Vec3D(x,y,z); }
  
  public Vec3S toS() { return new Vec3S((short)(x+0.5),(short)(y+0.5),(short)(z+0.5)); }
  public Vec3I toI() { return new Vec3I((int)(x+0.5),(int)(y+0.5),(int)(z+0.5)); }
  public Vec3F toF() { return new Vec3F((float)x,(float)y,(float)z); }

  public Vec3D opPlus(double f) { return new Vec3D(f+x,f+y,f+z); }
  public Vec3D opPlus(Vec3D v) { return new Vec3D(x+v.x,y+v.y,z+v.z); }
  public Vec3D opMinus() { return new Vec3D(-x,-y,-z); }
  public Vec3D opMinus(double f) { return new Vec3D(x-f,y-f,z-f); }
  public Vec3D opMinus(Vec3D v) { return new Vec3D(x-v.x,y-v.y,z-v.z); }
  public Vec3D opTimes(double f) { return new Vec3D(x*f,y*f,z*f); }
  public Vec3D opTimes(Vec3D v) { return new Vec3D(x*v.x , y*v.y,z*v.z); }
  public Vec3D opDivide(double f) { return new Vec3D(x/f,y/f,z/f); }
  public Vec3D opDivide(Vec3D v) { return new Vec3D(x/v.x , y/v.y, z/v.z); }
  public Vec3D opCross(Vec3D v) { return new Vec3D(y*v.z - z*v.y, z*v.x - x*v.z, x*v.y - y*v.x); }
  public Vec3D opNorm() { double L=Math.sqrt(x*x + y*y + z*z); if (L>0) { return new Vec3D(x/=L, y/=L, z/=L); } else { return new Vec3D(0,0,0); } }
  public Vec3D opRound() { return new Vec3D( Math.round(x) , Math.round(y), Math.round(z) ); }
  
  public Vec3D eq(Vec3D v) { x=v.x; y=v.y; z=v.z; return this; }
  public Vec3D eq(Vec3F v) { x=v.x; y=v.y; z=v.z; return this; }
  public Vec3D eq(Vec3I v) { x=v.x; y=v.y; z=v.z; return this; }
  public Vec3D eq(Vec3S v) { x=v.x; y=v.y; z=v.z; return this; }
  public Vec3D eq(double X,double Y,double Z) { x=X; y=Y; z=Z; return this; }
  // TODO: See if the method below makes sense in 3D and what its equivalent is.
//  public Vec3D eqAngle(double f) { x=Math.cos(f); y=Math.sin(f); return this; }
  public Vec3D eqPlus(double f) { x+=f; y+=f; z+=f; return this; }
  public Vec3D eqPlus(Vec3D v) { x+=v.x; y+=v.y; z+=v.z; return this; }
  public Vec3D eqMinus() { x=-x; y=-y; z=-z; return this; }
  public Vec3D eqMinus(double f) { x-=f; y-=f; z-=f; return this; }
  public Vec3D eqMinus(Vec3D v) { x-=v.x; y-=v.y; z-=v.z; return this; }
  public Vec3D eqTimes(double f) { x*=f; y*=f; z*=f; return this; }
  public Vec3D eqTimes(Vec3D v) { x*=v.x; y*=v.y; z*=v.z; return this; }
  public Vec3D eqDivide(double f) { x/=f; y/=f; z/=f; return this; }
  public Vec3D eqDivide(Vec3D v) { x/=v.x; y/=v.y; z/=v.z; return this; }
  public Vec3D eqCross(Vec3D v) { double xx = x, yy = y; x = yy*v.z - z*v.y; y = z*v.x - xx*v.z; z = xx*v.y - yy*v.x; return this; }
  public Vec3D eqNorm() { double L=Math.sqrt(x*x + y*y + z*z); if (L>0) { x/=L; y/=L; z/=L; } else { x=y=z=0; } return this; }
  public Vec3D eqRound() { x = Math.round(x); y = Math.round(y); z = Math.round(z); return this; }
  
  public double dot(Vec3D v) { return x*v.x + y*v.y + z*v.z; }
  public double length2() { return x*x + y*y + z*z; }
  public double length() { return Math.sqrt(x*x + y*y + z*z); }
  public double dist2(Vec3D v) { return (x-v.x)*(x-v.x) + (y-v.y)*(y-v.y) + (z-v.z)*(z-v.z); }
  public double dist(Vec3D v) { return Math.sqrt( (x-v.x)*(x-v.x) + (y-v.y)*(y-v.y) + (z-v.z)*(z-v.z) ); }
  public double unitDot(Vec3D v) { return (x*v.x+y*v.y+z*v.z)/Math.sqrt( (x*x+y*y+z*z) * (v.x*v.x + v.y*v.y + v.z*v.z) ); } 

  @Override
  public String toString() { return "(" + x + "," + y + "," + z + ")"; }
}

