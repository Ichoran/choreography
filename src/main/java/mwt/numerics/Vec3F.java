/** This file copyright 2008 Rex Kerr, Nicholas Swierczek, and the Howard Hughes Medical Institute.
  * Copyright 2015 Calico Life Sciences LLC (author Rex Kerr)
  * Distributed under the LGPL2.1 license (or GPL2.1 with classpath exception)
  */

package mwt.numerics;

public class Vec3F {
  public float x,y,z;
  
  public static Vec3F zero() { return new Vec3F(); }
  
  public Vec3F() { x=y=z=0; }
  public Vec3F(float X,float Y,float Z) { x=X; y=Y; z=Z; }
  public Vec3F(Vec3F v) { x=v.x; y=v.y; z=v.z; }
  public Vec3F(String A,String B, String C) throws NumberFormatException { x = Float.parseFloat(A); y = Float.parseFloat(B); z = Float.parseFloat(C); }
  
  public boolean isSame(Vec3F v) { return x==v.x && y==v.y && z==v.z; }
  public boolean isSimilar(Vec3F v,float far) {
    float dx = x - v.x;
    if (dx<0) dx=-dx;
    float dy = y - v.y;
    if (dy<0) dy=-dy;
    float dz = z - v.z;
    if( dz<0) dz=-dz;
    if (far<Math.ulp(dx)) far=Math.ulp(dx);
    if (far<Math.ulp(dy)) far=Math.ulp(dy);
    if (far<Math.ulp(dz)) far=Math.ulp(dz);
    return dx<far && dy<far && dz<far;
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof Vec3F) && isSame( (Vec3F)o );
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 97 * hash + Float.floatToIntBits(this.x);
    hash = 97 * hash + Float.floatToIntBits(this.y);
    hash = 97 * hash + Float.floatToIntBits(this.z);
    return hash;
  }
  
  public Vec3F copy() { return new Vec3F(x,y,z); }
  
  public Vec3S toS() { return new Vec3S((short)(x+0.5),(short)(y+0.5),(short)(z+0.5)); }
  public Vec3I toI() { return new Vec3I((int)(x+0.5),(int)(y+0.5),(int)(z+0.5)); }
  public Vec3D toD() { return new Vec3D((double)x,(double)y,(double)z); }

  public Vec3F opPlus(float f) { return new Vec3F(f+x,f+y,f+z); }
  public Vec3F opPlus(Vec3F v) { return new Vec3F(x+v.x,y+v.y,z+v.z); }
  public Vec3F opMinus() { return new Vec3F(-x,-y,-z); }
  public Vec3F opMinus(float f) { return new Vec3F(x-f,y-f,z-f); }
  public Vec3F opMinus(Vec3F v) { return new Vec3F(x-v.x,y-v.y,z-v.z); }
  public Vec3F opTimes(float f) { return new Vec3F(x*f,y*f,z*f); }
  public Vec3F opTimes(Vec3F v) { return new Vec3F(x*v.x , y*v.y,z*v.z); }
  public Vec3F opDivide(float f) { return new Vec3F(x/f,y/f,z/f); }
  public Vec3F opDivide(Vec3F v) { return new Vec3F(x/v.x , y/v.y, z/v.z); }
  public Vec3F opCross(Vec3F v) { return new Vec3F(y*v.z - z*v.y, z*v.x - x*v.z, x*v.y - y*v.x); }
  public Vec3F opNorm() { float L=(float)Math.sqrt(x*x + y*y + z*z); if (L>0) { return new Vec3F(x/=L, y/=L, z/=L); } else { return new Vec3F(0,0,0); } }
  public Vec3F opRound() { return new Vec3F( Math.round(x) , Math.round(y), Math.round(z) ); }
  
  public Vec3F eq(Vec3D v) { x=(float)v.x; y=(float)v.y; z=(float)v.z; return this; }
  public Vec3F eq(Vec3F v) { x=v.x; y=v.y; z=v.z; return this; }
  public Vec3F eq(Vec3I v) { x=v.x; y=v.y; z=v.z; return this; }
  public Vec3F eq(Vec3S v) { x=v.x; y=v.y; z=v.z; return this; }
  public Vec3F eq(float X,float Y,float Z) { x=X; y=Y; z=Z; return this; }
  // TODO: Find out if the below method is useful and what it is in 3D if it is.
  //public Vec3F eqAngle(float f) { x=Math.cos(f); y=Math.sin(f); return this; }
  public Vec3F eqPlus(float f) { x+=f; y+=f; z+=f; return this; }
  public Vec3F eqPlus(Vec3F v) { x+=v.x; y+=v.y; z+=v.z; return this; }
  public Vec3F eqMinus() { x=-x; y=-y; z=-z; return this; }
  public Vec3F eqMinus(float f) { x-=f; y-=f; z-=f; return this; }
  public Vec3F eqMinus(Vec3F v) { x-=v.x; y-=v.y; z-=v.z; return this; }
  public Vec3F eqTimes(float f) { x*=f; y*=f; z*=f; return this; }
  public Vec3F eqTimes(Vec3F v) { x*=v.x; y*=v.y; z*=v.z; return this; }
  public Vec3F eqDivide(float f) { x/=f; y/=f; z/=f; return this; }
  public Vec3F eqDivide(Vec3F v) { x/=v.x; y/=v.y; z/=v.z; return this; }
  public Vec3F eqCross(Vec3F v) { float xx = x, yy = y; x = yy*v.z - z*v.y; y = z*v.x - xx*v.z; z = xx*v.y - yy*v.x; return this; }
  public Vec3F eqNorm() { float L=(float)Math.sqrt(x*x + y*y + z*z); if (L>0) { x/=L; y/=L; z/=L; } else { x=y=z=0; } return this; }
  public Vec3F eqRound() { x = (float)Math.round(x); y = (float)Math.round(y); z = (float)Math.round(z); return this; }
  
  public float dot(Vec3F v) { return x*v.x + y*v.y + z*v.z; }
  public float length2() { return x*x + y*y + z*z; }
  public float length() { return (float)Math.sqrt(x*x + y*y + z*z); }
  public float dist2(Vec3F v) { return (x-v.x)*(x-v.x) + (y-v.y)*(y-v.y) + (z-v.z)*(z-v.z); }
  public float dist(Vec3F v) { return (float)Math.sqrt( (x-v.x)*(x-v.x) + (y-v.y)*(y-v.y) + (z-v.z)*(z-v.z) ); }
  public float unitDot(Vec3F v) { return (float)((x*v.x+y*v.y+z*v.z)/Math.sqrt( (x*x+y*y+z*z) * (v.x*v.x + v.y*v.y + v.z*v.z) )); } 

  @Override
  public String toString() { return "(" + x + "," + y + "," + z + ")"; }
}

