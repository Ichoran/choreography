/* Outline.java - Representation of the outline of an animal
 * Copyright 2010 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015 Calico Life Sciences LLC (authored by Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt;
 
import mwt.numerics.*;

/**
 *
 * @author kerrr
 * The contract for unpacking when passing arrays is:
 *   - If a null array is passed, you expect to get a buffered copy back
 *   - If a non-null array of sufficient size is passed, you expect to get the data at the beginning of this array (and you expect all vectors to already exist)
 *   - If a non-null array of insufficient size is passed, you expect to get a newly-allocated array with newly-allocated vectors in it
 */

public interface Outline {
  public int size();                    // How many points (pixels) are in the outline
  public boolean quantized();           // If true, prefer shorts; otherwise, may as well use floats
  public void compact();                // Discard any buffered data (if buffering is implemented)
  public Vec2S get(int i, Vec2S buf);   // Load short vector with outline
  public Vec2F get(int i, Vec2F buf);   // Load floating-point vector with outline
  public Vec2S[] unpack(boolean store); // Return outline pixels, using buffer if requested (and implemented)
  public Vec2S[] unpack(Vec2S[] store); // Return outline pixels in the array provided
  public Vec2F[] unpack(Vec2F[] store); // Return outline pixels with floating point resolution
}
