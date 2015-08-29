/* CustomOutputModification.java - Specialized type of plugin to alter output
 * Copyright 2010, 2013 Howard Hughes Medical Institute and Rex Kerr
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt.plugins;

import java.awt.image.*;

import mwt.*;
import mwt.numerics.*;

public interface CustomOutputModification extends CustomComputation {
  public boolean modifyQuantity(Dance d, Choreography.DataSource ds);
  public boolean modifyMapDisplay(Vec2D position, Vec2I dimensions, double pixelSize, BufferedImage buffer, double time, DataMapper dm);
}
