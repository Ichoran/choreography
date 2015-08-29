/* DataView.java - Stores all necessary data for describing a view.
 * Copyright 2010 Howard Hughes Medical Institute and Nicholas Andrew Swierczek
 * Copyright 2015 Calico Life Sciences LLC (authored by Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt;

import mwt.numerics.*;

/**
 * A class which represents the necessary information about a view of a data map source.
 * @author Nicholas Andrew Swierczek (swierczekn at janelia dot hhmi dot org)
 */
public class DataView {
    /**
     * Vec2D representing the location of this view rectangle in data space.
     */
    public Vec2D near = Vec2D.zero();
    
    /**
     * Vec2D representing the size of this view rectangle in data space.
     */
    public Vec2D size = Vec2D.zero();
    
    /**
     * Primitive consructor
     * @param x double representing x-coordinate of position in data space
     * @param y double representing y-coordinate of position in data space
     * @param width double representing width of bounding rectangle in data space
     * @param height double representing height of bounding rectangle in data space
     */
    public DataView( double x, double y, double width, double height) {
        near = new Vec2D(x,y);
        size = new Vec2D(width,height);
    }
   
    /**
     * Vec2D constructor.
     * @param n Vec2D representing position in data space
     * @param s Vec2D representing size in data space
     */
    public DataView( Vec2D n, Vec2D s) {
        if( n != null ) near.eq(n);
        if( s != null ) size.eq(s);
    }
    
    /**
     * Copy Constructor.
     * @param v DataView to copy.
     */
    public DataView( DataView v ) {
        near.eq(v.pos());
        size.eq(v.size());
    }
    
    /**
     * Causes this DataView's data to equal the specified view.
     * @param v DataView to copy.
     */
    public void equal(DataView v) {
        near.eq(v.pos());
        size.eq(v.size());
    }
    
    /**
     * @return A new DataView with the same values as this DataView.
     */
    public DataView copy() {
        return new DataView(new Vec2D(near.x,near.y),new Vec2D(size.x,size.y));
    }
    
    /**
     * Returns the size of this data view. To be clear, this is to encapsulate the size, disconnecting it from what 
     * may actually be the internal representation of the view. 
     * @return Vec2D representation of the size of this DataView.
     */
    public Vec2D size() { return size; }

    /**
     * Returns the position of this data view. To be clear, this is to encapsulate the position, disconnecting it from what 
     * may actually be the internal representation of the view. 
     * @return Vec2D representation of the position of this DataView.
     */    
    public Vec2D pos() { return near; }
  
    /**
     * A valid DataView has a size larger than 0 in both dimensions, and has a non-null position.
     * @return {@code true} if this is a valid dataview, otherwise false.
     */
    public boolean valid() {
        return pos() != null && size() != null && size().x > 0.0 && size().y > 0.0;
    }
    
    /**
     * @return String representating of this DataView.
     */
    @Override
    public String toString() {
        return pos().toString() + "|" + size().toString();
    }
}
