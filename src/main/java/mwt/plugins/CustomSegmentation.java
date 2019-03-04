/* CustomSegmentation.java - Specialized type of plugin to alter path segmentations
 * Copyright 2018 Calico Life Sciences and Rex Kerr
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt.plugins;

import java.awt.image.*;

import mwt.*;
import mwt.numerics.*;

public interface CustomSegmentation extends CustomComputation {
  public Style[] resegment(Dance d, Style[] segmentation, double credibleDistSq);
}
