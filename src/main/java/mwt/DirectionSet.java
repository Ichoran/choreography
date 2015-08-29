
/* DirectionSet.java - Stores direction information in two bits per timepoint
 * Copyright 2011 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015 Calico Life Sciences LLC (authored by Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt;
 
public class DirectionSet {
  public static final int STOP = 0;
  public static final int FWD = 1;
  public static final int BKW = 3;
  public static final int INVALID = 2;
  int bits[];
  public int length;
  public DirectionSet(int n) {
    length = n;
    bits = new int[Math.max(n/4,0)+1];
  }
  public void set(int i, int value) {
    int j = i >> 4;
    int k = 2*(i & 0xF);
    value &= 0x3;
    bits[j] = (bits[j] & (0xFFFFFFFF - (0x3<<k))) | (value << k);
  }
  public void set(int i, float value) {
    if (Float.isNaN(value)) set(i,-2);
    else set(i,Math.round(value));
  }
  public int rawGet(int i) {
    int j = i >> 4;
    int k = 2*(i & 0xF);
    return (bits[j] >>> k) & 0x3;
  }
  public int get(int i) {
    int x = rawGet(i);
    if ((x&0x2)!=0) return x|0xFFFFFFFC; else return x;
  }
  public float getFloat(int i) {
    switch (rawGet(i)) {
      case 0: return 0f;
      case 1: return 1f;
      case 3: return -1f;
      default: return Float.NaN;
    }
  }
  public boolean isForward(int i) {
    return rawGet(i)==1;
  }
  public boolean isBackwards(int i) {
    return rawGet(i)==3;
  }
  public boolean isMoving(int i) {
    return (rawGet(i)&0x1)==1;
  }
  public boolean isStill(int i) {
    return rawGet(i)==0;
  }
  public boolean isInvalid(int i) {
    return rawGet(i)==2;
  }
  public boolean isValid(int i) {
    return rawGet(i)!=2;
  }
}