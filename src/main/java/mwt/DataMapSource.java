/* DataMapSource.java - Interface defining a data source for the Data Map Visualizer.
 * Copyright 2010 Howard Hughes Medical Institute and Nicholas Andrew Swierczek
 * Also copyright 2015 Calico Life Sciences LLC (authored by Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;

import mwt.numerics.*;

/**
 * This interface defines what it is to be a data source for an instance of the Data Map Visualizer.
 * @author Nicholas A. Swierczek (swierczekn at janelia dot hhmi dot org)
 */
public interface DataMapSource {   
    /**
     * Attaches the specified {@code {@link DataReadyListener}} to this DataMapSource.
     * @param drl the listener
     * @throws UnsupportedOperationException if this {@code DataMapSource } does not support {@code DataReadyListener}s.
     */
    public void addDataReadyListener(DataReadyListener drl);
    
    /**
     * Removes the specified {@code DataReadyListener} from this DataMapSource.
     * @param drl the listener
     * @throws UnsupportedOperationException if this {@code DataMapSource } does not support {@code DataReadyListener}s.
     */
    public void removeDataReadyListener(DataReadyListener drl);
    
    /**
     * Returns a string that displays an implementors-specified "status string" for the
     * given mouse location.
     * @param position location in data space of the upper left corner of the current view.
     * @param dimensions dimensions, width and height, in pixel coordinates of the current view.
     * @param pixelSize the resolution, in map units/pixel, of the current view.
     * @param cursor_position position of mouse cursor in data space.
     * @return String containing desired status information.
     */
    public String getStatus(Vec2D position, Vec2I dimensions, double pixelSize, Vec2D cursor_position);      
    
    /**
     * Returns a JComponent that will be included in an instance of the Data Map Visualizer.
     * @return JComponent to be added to the DMV GUI.
     */
    public JComponent getGUIElement();   

    /**
     * Creates and returns a JPopupMenu.
     * @param position location in data space of the upper left corner of the current view.
     * @param dimensions dimensions, width and height, in pixel coordinates of the current view.
     * @param pixelSize the resolution, in map units/pixel, of the current view.
     * @param cursor_position position of mouse cursor in data space.
     * @return JPopupMenu for the desired context.
     */
    public JPopupMenu getContextMenu(Vec2D position, Vec2I dimensions, double pixelSize, Vec2D cursor_position);
    
    /**
     * Returns the dimensions of valid data, in map units.
     * @return a {@code Rectangle2D.Double} representation of the valid bounds of this data map.
     */  
    public Rectangle2D.Double getBounds();
  
    /**
     * Returns an image representation of a view of the underlying data at the specified location and resolution and of the specified dimensions.
     * The bounds of this view are from {@code near} to {@code near + (dimensions / pixelSize)}.  The returned BufferedImage's dimensions
     * are equal to the specified dimensions.
     * @param position location in the data space of the upper left corner of the desired view.
     * @param dimensions, width and height, in pixel coordinates of the desired view. 
     * @param pixelSize the resolution, in map units/pixel, of the desired view.
     * @param buffer a buffer in which to write the data for the new view, if it exists and is large enough; otherwise a new BufferedImage is allocated.
     * @return a BufferedImage representation of the desired view.
     * @throws IllegalArgumentException if {@code dimensions <= 0} or {@code pixelSize <= 0.00}.
    */
    public BufferedImage getView(Vec2D position, Vec2I dimensions, double pixelSize, BufferedImage buffer) throws IllegalArgumentException;
};

