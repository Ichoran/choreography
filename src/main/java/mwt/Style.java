/* Style.java - Computations on individual moving objects
 * Copyright 2010, 2011 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015, 2018 Calico Life Sciences LLC and Rex Kerr
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt;

import java.util.*;

import mwt.numerics.*;

public final class Style {
  public enum Styled { Weird,Dwell,Clutter,Straight,Arc }
  public static final Styled zeroStyle = Styled.Dwell;

  public Styled kind;
  public Vec2F[] centroid;
  public int dir;
  public int i0,i1;
  public Fitter fit;
  public int[] endpoints = null;
  public LinkedList<Style> children = null;
  
  public Style(Vec2F[] cs, Styled s,int i) {
    kind = s;
    centroid = cs;
    dir = 0;
    i0 = i1 = i;
    fit = null;
  }
  public Style(Vec2F[] cs, Styled s,int ia,int ib,Fitter f) {
    kind = s;
    centroid = cs;
    dir = 0;
    i0 = ia; i1 = ib;
    fit = f;
  }
  public Style(Vec2F[] cs, Styled s,Style old) {
    kind = s;
    dir = 0;
    i0 = old.i0; i1 = old.i1;
    fit = new Fitter(old.fit);
  }
  public Style(Vec2F[] cs, LinkedList<Style> adoptables) {
    if (adoptables==null || adoptables.size()==0) {
      kind = Styled.Weird;
      centroid = cs;
      dir = 0;
      i0 = i1 = -1;
      fit = null;
    }
    else if (adoptables.size()==1) mimic(adoptables.pop());
    else {
      mimic( adoptables.peek() );
      fit = null;
      children = new LinkedList<Style>();
      while (!adoptables.isEmpty() && adoptables.peek().kind==kind) {
        Style s = adoptables.pop();
        children.add( s );
        i1 = s.i1;
      }
    }
  }
  
  public int size() { return 1+i1-i0; }
  public void mimic(Style s) {
    kind = s.kind;
    centroid = s.centroid;
    dir = s.dir;
    i0 = s.i0;
    i1 = s.i1;
    fit = s.fit;
    endpoints = s.endpoints;
    children = s.children;
  }
  public int id() {
    return kind.ordinal() - zeroStyle.ordinal();
  }
  public boolean isLine() { return (kind==Styled.Straight || kind==Styled.Arc); }
  public void fit() {
    if (fit==null) return;
    if (kind==Styled.Dwell) fit.spot.fit();
    else if (kind==Styled.Straight) fit.line.fit();
    else if (kind==Styled.Arc) fit.circ.fit();
  }
  public double sqError( Vec2F v ) {
    if (fit==null) return Double.NaN;
    if (kind==Styled.Arc) return fit.circ.sqError(v.x,v.y);
    if (kind==Styled.Straight) return fit.line.sqError(v.x,v.y);
    if (kind==Styled.Dwell) return fit.spot.sqError(v.x,v.y);
    return Double.NaN;
  }
  public void addRightSimply( int i ) { i1 = i; fit.addC(centroid[i].x,centroid[i].y); }
  public void addLeftSimply( int i ) { i0 = i; fit.addC(centroid[i].x,centroid[i].y); }
  public int addRight( int i ) { addRightSimply(i); i++; while (i<centroid.length && centroid[i]==null) i++; return i; }
  public int addLeft( int i ) { addLeftSimply(i); i--; while (i>=0 && centroid[i]==null) i--; return i ; }
  public void subRight() { fit.subC(centroid[i1].x,centroid[i1].y); i1--; while (i1>=0 && centroid[i1]==null) i1--; }
  public void subLeft() { fit.subC(centroid[i0].x,centroid[i0].y); i0++; while (i0<centroid.length && centroid[i0]==null) i0++; }
  public int shiftRight( int i ) { subLeft(); return addRight(i); }
  public void deltaVector(Vec2F delta,Vec2F u,Vec2F v) {
    if (kind==Styled.Straight) {
      delta.eq(v).eqMinus(u);
      float l = (float)((fit.line.params.b*delta.y - fit.line.params.a*delta.x)/fit.line.varianceAngleBias());
      delta.x = -(float)fit.line.params.a * l;
      delta.y = (float)fit.line.params.b * l;
    }
    else if (kind==Styled.Arc) {
      double x = 2.0*fit.circ.params.y0-(u.y + v.y);
      double y = u.x+v.x - 2.0*fit.circ.params.x0;
      delta.eq(v).eqMinus(u);
      double l = (delta.x*x + delta.y*y)/(x*x + y*y);
      delta.x = (float)(x*l);
      delta.y = (float)(y*l);
    }
  }
  public boolean hasDirection() {
    return (isLine() && (endpoints==null || endpoints.length>1));
  }
  public boolean hasSeveralDirections() {
    return (isLine() && endpoints!=null && endpoints.length>2);
  }
  public int directions() {
    if (!isLine()) return 0;
    if (endpoints==null) return 1;
    else if (endpoints.length==0) return 0;
    else return endpoints.length-1;
  }
  public void initialVector(Vec2F direction) {
    if (endpoints==null || endpoints.length==0) deltaVector(direction,centroid[i0],centroid[i1]);
    else deltaVector(direction,centroid[endpoints[0]],centroid[endpoints[1]]);
  }
  public void finalVector(Vec2F direction) {
    if (endpoints==null || endpoints.length<2) deltaVector(direction,centroid[i0],centroid[i1]);
    else deltaVector(direction,centroid[endpoints[endpoints.length-2]],centroid[endpoints[endpoints.length-1]]);
  }
  public void pickVector(Vec2F direction,int n) {
    deltaVector(direction,centroid[endpoints[n]],centroid[endpoints[n+1]]);
  }
  public float dotWith(Style s) {
    Vec2F mydir = new Vec2F();
    Vec2F theirdir = new Vec2F();
    if (s.i0 < i0) {
      s.finalVector(theirdir);
      initialVector(mydir);
    }
    else {
      s.initialVector(theirdir);
      finalVector(mydir);
    }
    return mydir.unitDot(theirdir);
  }
  public void snapToLine(Vec2D stray) {  // Make sure this stays identical to Vec2F version!
    if (kind==Styled.Arc) {
      stray.x -= fit.circ.params.x0;
      stray.y -= fit.circ.params.y0;
      stray.eqNorm().eqTimes(fit.circ.params.R);
      stray.x += fit.circ.params.x0;
      stray.y += fit.circ.params.y0;
    }
    else if (kind==Styled.Straight) {
      double off = fit.line.params.a*stray.y + fit.line.params.b*stray.x + fit.line.params.c;
      if (off>0) {
        double shift = -off/(fit.line.params.a*fit.line.params.a + fit.line.params.b*fit.line.params.b);
        stray.x += shift * fit.line.params.b;
        stray.y += shift * fit.line.params.a;
      }
    }
  }
  public void snapToLine(Vec2F stray) {  // Make sure this stays identical to Vec2D version!
    if (kind==Styled.Arc) {
      stray.x -= (float)fit.circ.params.x0;
      stray.y -= (float)fit.circ.params.y0;
      stray.eqNorm().eqTimes((float)fit.circ.params.R);
      stray.x += (float)fit.circ.params.x0;
      stray.y += (float)fit.circ.params.y0;
    }
    else if (kind==Styled.Straight) {
      double off = fit.line.params.a*stray.y + fit.line.params.b*stray.x + fit.line.params.c;
      if (off>0) {
        double shift = -off/(fit.line.params.a*fit.line.params.a + fit.line.params.b*fit.line.params.b);
        stray.x += (float)(shift * fit.line.params.b);
        stray.y += (float)(shift * fit.line.params.a);
      }
    }
  }
  public double parameterize(Vec2D v) {
    if (kind==Styled.Arc) return fit.circ.arcCoord(v.x,v.y);
    else if (kind==Styled.Straight) return fit.line.parallelCoord(v.x,v.y);
    else return 0.0;
  }
  public double distanceTraversed(int n) {
    int j0;
    int j1;
    if (endpoints==null) {
      if (n!=0) return 0.0;
      else { j0=i0; j1=i1; }
    }
    else {
      if (n<0 || n>=endpoints.length-1) return 0.0;
      else { j0=endpoints[n]; j1=endpoints[n+1]; }
    }
    if (kind!=Styled.Arc) return centroid[j1].dist(centroid[j0]);
    else return Math.abs(fit.circ.params.R * fit.circ.arcDeltaCoord(centroid[j0].x,centroid[j0].y,centroid[j1].x,centroid[j1].y));
  }
  public float distanceTraversed() {
    if (endpoints==null) return (float)distanceTraversed(0);
    else {
      double cumulator = 0.0;
      for (int i=0 ; i<endpoints.length-1 ; i++) cumulator += distanceTraversed(i);
      return (float)cumulator;
    }
  }
}
