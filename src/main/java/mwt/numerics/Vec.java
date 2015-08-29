/** This file copyright 2008 Rex Kerr, Nicholas Swierczek, and the Howard Hughes Medical Institute.
  * Copyright 2015 Calico Life Sciences LLC (author Rex Kerr)
  * Distributed under the LGPL2.1 license (or GPL2.1 with classpath exception)
  */

package mwt.numerics;

import java.awt.Dimension;
import java.awt.geom.Dimension2D;
import java.awt.Point;
import java.awt.geom.Point2D;

public class Vec
{
    // Vec2 methods
  public static Vec2D toD(Vec2F v) { return v.toD(); }
  public static Vec2D toD(Vec2I v) { return v.toD(); }
  public static Vec2D toD(Vec2S v) { return v.toD(); }
  public static Vec2D toD(Dimension d) { return new Vec2D( d.getWidth() , d.getHeight() ); }
  public static Vec2D toD(Dimension2D d) { return new Vec2D( d.getWidth() , d.getHeight() ); }
  public static Vec2D toD(Point p) { return new Vec2D( p.getX() , p.getY() ); }
  public static Vec2D toD(Point2D p) { return new Vec2D( p.getX() , p.getY() ); }
  public static Vec2D toD(Point2D.Double p) { return new Vec2D( p.getX() , p.getY() ); }
  public static Vec2D toD(Point2D.Float p) { return new Vec2D( p.getX() , p.getY() ); }

  public static Vec2F toF(Vec2D v) { return v.toF(); }
  public static Vec2F toF(Vec2I v) { return v.toF(); }
  public static Vec2F toF(Vec2S v) { return v.toF(); }
  public static Vec2F toF(Dimension d) { return new Vec2F( (float)d.getWidth() , (float)d.getHeight() ); }
  public static Vec2F toF(Dimension2D d) { return new Vec2F( (float)d.getWidth() , (float)d.getHeight() ); }
  public static Vec2F toF(Point p) { return new Vec2F( (float)p.getX() , (float)p.getY() ); }
  public static Vec2F toF(Point2D p) { return new Vec2F( (float)p.getX() , (float)p.getY() ); }
  public static Vec2F toF(Point2D.Double p) { return new Vec2F( (float)p.getX() , (float)p.getY() ); }
  public static Vec2F toF(Point2D.Float p) { return new Vec2F( (float)p.getX() , (float)p.getY() ); }

  public static Vec2I toI(Vec2D v) { return v.toI(); }
  public static Vec2I toI(Vec2F v) { return v.toI(); }
  public static Vec2I toI(Vec2S v) { return v.toI(); }
  public static Vec2I toI(Dimension d) { return new Vec2I( (int)(d.getWidth()+0.5) , (int)(d.getHeight()+0.5) ); }
  public static Vec2I toI(Dimension2D d) { return new Vec2I( (int)(d.getWidth()+0.5) , (int)(d.getHeight()+0.5) ); }
  public static Vec2I toI(Point p) { return new Vec2I( (int)(p.getX()+0.5) , (int)(p.getY()+0.5) ); }
  public static Vec2I toI(Point2D p) { return new Vec2I( (int)(p.getX()+0.5) , (int)(p.getY()+0.5) ); }
  public static Vec2I toI(Point2D.Double p) { return new Vec2I( (int)(p.getX()+0.5) , (int)(p.getY()+0.5) ); }
  public static Vec2I toI(Point2D.Float p) { return new Vec2I( (int)(p.getX()+0.5) , (int)(p.getY()+0.5) ); }

  public static Vec2S toS(Vec2D v) { return v.toS(); }
  public static Vec2S toS(Vec2F v) { return v.toS(); }
  public static Vec2S toS(Vec2I v) { return v.toS(); }
  public static Vec2S toS(Dimension d) { return new Vec2S( (short)(d.getWidth()+0.5) , (short)(d.getHeight()+0.5) ); }
  public static Vec2S toS(Dimension2D d) { return new Vec2S( (short)(d.getWidth()+0.5) , (short)(d.getHeight()+0.5) ); }
  public static Vec2S toS(Point p) { return new Vec2S( (short)(p.getX()+0.5) , (short)(p.getY()+0.5) ); }
  public static Vec2S toS(Point2D p) { return new Vec2S( (short)(p.getX()+0.5) , (short)(p.getY()+0.5) ); }
  public static Vec2S toS(Point2D.Double p) { return new Vec2S( (short)(p.getX()+0.5) , (short)(p.getY()+0.5) ); }
  public static Vec2S toS(Point2D.Float p) { return new Vec2S( (short)(p.getX()+0.5) , (short)(p.getY()+0.5) ); }
  
  public static Dimension toDim(Vec2D v) { return new Dimension( (int)(v.x+0.5) , (int)(v.y+0.5) ); }
  public static Dimension toDim(Vec2F v) { return new Dimension( (int)(v.x+0.5f) , (int)(v.y+0.5f) ); }
  public static Dimension toDim(Vec2I v) { return new Dimension(v.x,v.y); }
  public static Dimension toDim(Vec2S v) { return new Dimension(v.x,v.y); }
  public static Dimension toDim(Dimension2D d) { return new Dimension( (int)(d.getWidth()+0.5) , (int)(d.getHeight()+0.5) ); }
  public static Dimension toDim(Point p) { return new Dimension( (int)(p.getX()+0.5) , (int)(p.getY()+0.5) ); }
  public static Dimension toDim(Point2D p) { return new Dimension( (int)(p.getX()+0.5) , (int)(p.getY()+0.5) ); }
  public static Dimension toDim(Point2D.Double p) { return new Dimension( (int)(p.getX()+0.5) , (int)(p.getY()+0.5) ); }
  public static Dimension toDim(Point2D.Float p) { return new Dimension( (int)(p.getX()+0.5) , (int)(p.getY()+0.5) ); }

  public static Point toPt(Vec2D v) { return new Point( (int)(v.x+0.5) , (int)(v.y+0.5) ); }
  public static Point toPt(Vec2F v) { return new Point( (int)(v.x+0.5f) , (int)(v.y+0.5f) ); }
  public static Point toPt(Vec2I v) { return new Point(v.x,v.y); }
  public static Point toPt(Vec2S v) { return new Point(v.x,v.y); }
  public static Point toPt(Dimension d) { return new Point( (int)(d.getWidth()+0.5) , (int)(d.getHeight()+0.5) ); }
  public static Point toPt(Dimension2D d) { return new Point( (int)(d.getWidth()+0.5) , (int)(d.getHeight()+0.5) ); }
  public static Point toPt(Point p) { return new Point( (int)(p.getX()+0.5) , (int)(p.getY()+0.5) ); }
  public static Point toPt(Point2D p) { return new Point( (int)(p.getX()+0.5) , (int)(p.getY()+0.5) ); }
  public static Point toPt(Point2D.Double p) { return new Point( (int)(p.getX()+0.5) , (int)(p.getY()+0.5) ); }
  public static Point toPt(Point2D.Float p) { return new Point( (int)(p.getX()+0.5) , (int)(p.getY()+0.5) ); }

  public static Point2D.Double toPtD(Vec2D v) { return new Point2D.Double(v.x,v.y); }
  public static Point2D.Double toPtD(Vec2F v) { return new Point2D.Double(v.x,v.y); }
  public static Point2D.Double toPtD(Vec2I v) { return new Point2D.Double(v.x,v.y); }
  public static Point2D.Double toPtD(Vec2S v) { return new Point2D.Double(v.x,v.y); }
  public static Point2D.Double toPtD(Dimension d) { return new Point2D.Double( d.getWidth() , d.getHeight() ); }
  public static Point2D.Double toPtD(Dimension2D d) { return new Point2D.Double( d.getWidth() , d.getHeight() ); }
  public static Point2D.Double toPtD(Point p) { return new Point2D.Double( p.getX() , p.getY() ); }
  public static Point2D.Double toPtD(Point2D p) { return new Point2D.Double( p.getX() , p.getY() ); }
  public static Point2D.Double toPtD(Point2D.Float p) { return new Point2D.Double( p.getX() , p.getY() ); }

  public static Point2D.Float toPtF(Vec2D v) { return new Point2D.Float((float)v.x,(float)v.y); }
  public static Point2D.Float toPtF(Vec2F v) { return new Point2D.Float(v.x,v.y); }
  public static Point2D.Float toPtF(Vec2I v) { return new Point2D.Float(v.x,v.y); }
  public static Point2D.Float toPtF(Vec2S v) { return new Point2D.Float(v.x,v.y); }
  public static Point2D.Float toPtF(Dimension d) { return new Point2D.Float( (float)d.getWidth() , (float)d.getHeight() ); }
  public static Point2D.Float toPtF(Dimension2D d) { return new Point2D.Float( (float)d.getWidth() , (float)d.getHeight() ); }
  public static Point2D.Float toPtF(Point p) { return new Point2D.Float( (float)p.getX() , (float)p.getY() ); }
  public static Point2D.Float toPtF(Point2D p) { return new Point2D.Float( (float)p.getX() , (float)p.getY() ); }
  public static Point2D.Float toPtF(Point2D.Double p) { return new Point2D.Float( (float)p.getX() , (float)p.getY() ); }
  
  // Vec3 methods
  // NOTE: The below will be expanded as classes get standardized.
  public static Vec3D toD(Vec3F v) { return v.toD(); }
  public static Vec3D toD(Vec3I v) { return v.toD(); }
  public static Vec3D toD(Vec3S v) { return v.toD(); }
  public static Vec2D to2D( Vec3D v ) { return new Vec2D( v.x, v.y ); }
  
  public static Vec3F toF(Vec3D v) { return v.toF(); }
  public static Vec3F toF(Vec3I v) { return v.toF(); }
  public static Vec3F toF(Vec3S v) { return v.toF(); }
  public static Vec2F to2F( Vec3F v ) { return new Vec2F( v.x, v.y ); }
  
  public static Vec3I toI(Vec3D v) { return v.toI(); }
  public static Vec3I toI(Vec3F v) { return v.toI(); }
  public static Vec3I toI(Vec3S v) { return v.toI(); }
  public static Vec2I to2I( Vec3I v ) { return new Vec2I( v.x, v.y ); }
  
  public static Vec3S toS(Vec3D v) { return v.toS(); }
  public static Vec3S toS(Vec3F v) { return v.toS(); }
  public static Vec3S toS(Vec3I v) { return v.toS(); }
  public static Vec2S to2S( Vec3S v ) { return new Vec2S( v.x, v.y ); }
}
