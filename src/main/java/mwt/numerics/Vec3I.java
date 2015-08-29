/** This file copyright 2008 Rex Kerr, Nicholas Swierczek, and the Howard Hughes Medical Institute.
  * Copyright 2015 Calico Life Sciences LLC (author Rex Kerr)
  * Distributed under the LGPL2.1 license (or GPL2.1 with classpath exception)
  */

package mwt.numerics;

public class Vec3I {
  public int x,y,z;
  
  public static Vec3I zero() { return new Vec3I(); }
  
  public Vec3I() { x=y=z=0; }
  public Vec3I(int X,int Y,int Z) { x=X; y=Y; z=Z; }
  public Vec3I(Vec3I v) { x=v.x; y=v.y; z=v.z; }
  public Vec3I(String A,String B, String C) throws NumberFormatException { x = Integer.parseInt(A); y = Integer.parseInt(B); z = Integer.parseInt(C); }
  
  public boolean isSame(Vec3I v) { return x==v.x && y==v.y && z==v.z; }
  public boolean isSimilar(Vec3I v,int far) {
    int dx = x - v.x;
    if (dx<0) dx=-dx;
    int dy = y - v.y;
    if (dy<0) dy=-dy;
    int dz = z - v.z;
    if( dz<0) dz=-dz;
    if (far<1) far=1;
    return dx<far && dy<far && dz<far;
  }
  
  @Override
  public boolean equals(Object o) {
    return (o instanceof Vec3I) && isSame( (Vec3I)o );
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 53 * hash + this.x;
    hash = 53 * hash + this.y;
    hash = 53 * hash + this.z;
    return hash;
  }

  public Vec3I copy() { return new Vec3I(x,y,z); }
  
  public Vec3S toS() { return new Vec3S((short)(x+0.5),(short)(y+0.5),(short)(z+0.5)); }
  public Vec3D toD() { return new Vec3D((double)(x),(double)(y),(double)(z)); }
  public Vec3F toF() { return new Vec3F((float)x,(float)y,(float)z); }

  public Vec3I opPlus(int f) { return new Vec3I(f+x,f+y,f+z); }
  public Vec3I opPlus(Vec3I v) { return new Vec3I(x+v.x,y+v.y,z+v.z); }
  public Vec3I opMinus() { return new Vec3I(-x,-y,-z); }
  public Vec3I opMinus(int f) { return new Vec3I(x-f,y-f,z-f); }
  public Vec3I opMinus(Vec3I v) { return new Vec3I(x-v.x,y-v.y,z-v.z); }
  public Vec3I opTimes(int f) { return new Vec3I(x*f,y*f,z*f); }
  public Vec3I opTimes(Vec3I v) { return new Vec3I(x*v.x , y*v.y,z*v.z); }
  public Vec3I opDivide(int f) { return new Vec3I(x/f,y/f,z/f); }
  public Vec3I opDivide(Vec3I v) { return new Vec3I(x/v.x , y/v.y, z/v.z); }
  public Vec3I opCross(Vec3I v) { return new Vec3I(y*v.z - z*v.y, z*v.x - x*v.z, x*v.y - y*v.x); }
  public Vec3I opNorm() { int L=(int)(Math.sqrt(x*x + y*y + z*z)+0.5); if (L>0) { return new Vec3I(x/=L, y/=L, z/=L); } else { return new Vec3I(0,0,0); } }
  public Vec3I opRound() { return new Vec3I( Math.round(x) , Math.round(y), Math.round(z) ); }
  
  public Vec3I eq(Vec3D v) { x=(int)(v.x+0.5); y=(int)(v.y+0.5); z=(int)(v.z+0.5); return this; }
  public Vec3I eq(Vec3F v) { x=(int)(v.x+0.5); y=(int)(v.y+0.5); z=(int)(v.z+0.5); return this; }
  public Vec3I eq(Vec3I v) { x=v.x; y=v.y; return this; }
  public Vec3I eq(Vec3S v) { x=v.x; y=v.y; return this; }
  public Vec3I eq(int X,int Y,int Z) { x=X; y=Y; z=Z; return this; }
  public Vec3I eqPlus(int f) { x+=f; y+=f; z+=f; return this; }
  public Vec3I eqPlus(Vec3I v) { x+=v.x; y+=v.y; z+=v.z; return this; }
  public Vec3I eqMinus() { x=-x; y=-y; z=-z; return this; }
  public Vec3I eqMinus(int f) { x-=f; y-=f; z-=f; return this; }
  public Vec3I eqMinus(Vec3I v) { x-=v.x; y-=v.y; z-=v.z; return this; }
  public Vec3I eqTimes(int f) { x*=f; y*=f; z*=f; return this; }
  public Vec3I eqTimes(Vec3I v) { x*=v.x; y*=v.y; z*=v.z; return this; }
  public Vec3I eqDivide(int f) { x/=f; y/=f; z/=f; return this; }
  public Vec3I eqDivide(Vec3I v) { x/=v.x; y/=v.y; z/=v.z; return this; }
  public Vec3I eqCross(Vec3I v) { int xx = x, yy = y; x = yy*v.z - z*v.y; y = z*v.x - xx*v.z; z = xx*v.y - yy*v.x; return this; }
  public Vec3I eqNorm() { int L=(int)(Math.sqrt(x*x + y*y + z*z)+0.5); if (L>0) { x/=L; y/=L; z/=L; } else { x=y=z=0; } return this; }
  public Vec3I eqRound() { x = Math.round(x); y = Math.round(y); z = Math.round(z); return this; }
  
  public int dot(Vec3I v) { return x*v.x + y*v.y + z*v.z; }
  public int length2() { return x*x + y*y + z*z; }
  public int length() { return (int)(Math.sqrt(x*x + y*y + z*z)+0.5); }
  public int dist2(Vec3I v) { return (x-v.x)*(x-v.x) + (y-v.y)*(y-v.y) + (z-v.z)*(z-v.z); }
  public int dist(Vec3I v) { return (int)(0.5 + Math.sqrt( (x-v.x)*(x-v.x) + (y-v.y)*(y-v.y) + (z-v.z)*(z-v.z) )); }
  public int unitDot(Vec3I v) { return (int)(0.5 + (x*v.x+y*v.y+z*v.z)/Math.sqrt( (x*x+y*y+z*z) * (v.x*v.x + v.y*v.y + v.z*v.z) )); } 

  @Override
  public String toString() { return "(" + x + "," + y + "," + z + ")"; }
}

