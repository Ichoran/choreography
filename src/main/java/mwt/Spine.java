/* Spine.java - Represents the midline of an animal (skeleton, spine)
 * Copyright 2010,2011 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015 Calico Life Sciences (authored by Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt;

import mwt.numerics.*;

/**
 *
 * @author kerrr
 */

public interface Spine {
  public int size();                     // How many points (pixels) are in the outline
  public boolean quantized();            // If true, prefer shorts; otherwise, may as well use floats
  public boolean oriented();             // If true, we believe that the head is at low indices and tail high
  public void headfirstKnown(boolean b); // Sets the return value for oriented (if allowed)
  public void flip();                    // Turn skeleton in opposite direction (head <-> tail)
  public Vec2S get(int i);               // May return internal stored copy--user should not modify
  public Vec2S get(int i, Vec2S buf);    // Fills buf, not internal copy, so safe to modify return value
  public Vec2F get(int i, Vec2F buf);    // Fills buf, not internal copy, so safe to modify return value
  public float width(int i);             // Returns NaN if width is not available with spine--must agree (NaN or not) for all points
}
