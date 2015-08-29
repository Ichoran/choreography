/** This file copyright 2008 Rex Kerr, Nicholas Swierczek, and the Howard Hughes Medical Institute.
  * Copyright 2015 Calico Life Sciences LLC (author Rex Kerr)
  * Distributed under the LGPL2.1 license (or GPL2.1 with classpath exception)
  */

package mwt.numerics;

public class ArrayV2S {
  public final short[] data;
  public final int size;
  public int idx;
  public Vec2S v;
  
  public ArrayV2S(short[] ai) {
    data = ai;
    size = (ai==null) ? 0 : (ai.length/2);
    idx = 0;
    v = new Vec2S();
    if (size>0) {
      v.x = data[0];
      v.y = data[1];
    }
  }
  
  public Vec2S now() { return v; }
  public Vec2S next() {
    if (idx>=size) return null;
    else {
      idx += 1;
      if (idx>=size) return null;
      v.x = data[2*idx];
      v.y = data[2*idx+1];
      return v;
    }
  }
  public Vec2S prev() {
    if (idx<=0) return null;
    idx -= 1;
    v.x = data[2*idx];
    v.y = data[2*idx+1];
    return v;
  }
  public Vec2S at(int i) {
    idx = i;
    v.x = data[2*idx];
    v.y = data[2*idx+1];
    return v;
  }
  public Vec2S store() {
    data[2*idx] = v.x;
    data[2*idx+1] = v.y;
    return v;
  }
}

