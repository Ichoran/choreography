/* ImagePanel.java - Stores an image requested from a DataMapSource.
 * Copyright 2010 Howard Hughes Medical Institute and Nicholas Andrew Swierczek
 * Also copyright 2015 Calico Life Sciences LLC (authored by Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import mwt.numerics.*;


/**
 * A class that represents a image_view of the underlying DataMapSource, with an optional Viewbox 
 * that represents which part of this ImagePanel's image_view is currently being displayed in the DataMapVisualizer frame.
 * @author Nicholas Andrew Swieczek (swierczekn at janelia dot hhmi dot org)
 */
public class ImagePanel extends JPanel {
    /**
     * An ImageIcon representation of the displayed image.
     */
    public BufferedImage image = null;
           
    /**
     * DataView presented in this ImagePanel.
     */
    public DataView image_view;
    
    /**
     * Rectangle representing the size and location of this ImagePanel in screen coordinates.
     * This object is a static image_view of the DataMapSource, which can be changed to match {@code new_view}
     * as a result of user interaction.
     */
    public Rectangle staticReticle = null;

    /**
     * Rectangle representing the size and location of a new image_view of the DataMapSource.
     * This object's position is updated while mouse events are being processed.
     */
    public Rectangle dynamicReticle = null;
        

    public Dimension base_reticle_dimension = null;
    // TODO: Javadoc me
    
    
    public DataView reticle_view;
    
    /**
     * Fraction of this ImagePanel's screen dimensions that its ViewBox is constrained to.
     */
    public double viewbox_size_fraction = 0.5;
    
    public double pixelSize = 0.0;
    
    /**
     * Vector representing the maximum size of the DataMapSource valid data image_view..
     */
    public Vec2D defaultSize = Vec2D.zero();
    
    /**
     * Dimensions of this ImagePanel
     */
    public Dimension dim;

    /**
     * ViewBox that defines a subview of this ImagePanel's DataMapSource image_view.
     */
    //public ViewBox viewbox;
    
    /**
     * Basic constructor that gives an empty ViewBox.
     * @param d Size to attempt to give this ImagePanel as a Swing element.
     */
    public ImagePanel( Dimension d ) {
        super(true);
        dim = d;
        this.setPreferredSize(dim);
        this.setSize(dim);
//        viewbox = null;
    }

	/**
         * Constructor with knowledge of a DataMapSource.
         * If {@code has_viewbox == true}, DataView is calculated for this ImagePanel as the difference in size between the size of the ViewBox, which is
         * defined to be smaller than the ImagePanel in screen coordinates by the specified fraction and of the specified aspect ratio, and the ImagePanel, 
         * transformed in to DataMapSource coordinates.
         * @param d Size and Preferred Size to give this ImagePanel as a Swing element.
         * @param has_viewbox A flag for if a ViewBox should be created or not. 
         *                                      If {@code true}, a ViewBox is created and the specified DataView is for the ViewBox and must be non-null.
         *                                      If {@code false}, no ViewBox is created and the DataView refers to the image_view displayed.
         * @param v image_view of data for this ImagePanel, stored either in its ViewBox, if {@code has_view == true}, or as its image_view otherwise.
         * @param bounds The valid data bounds from the underlying DataMapSource.
         * @param mf Fraction of this ImagePanel's screen dimensions its ViewBox is not allowed to exceed.
         * @param ratio Aspect Ratio for this ViewBox, which is ignored if has_viewbox is false.
         */
	public ImagePanel(Dimension d, boolean has_viewbox, DataView v, Rectangle2D.Double bounds, DataView default_view, double mf, double ratio) {
            super(true);
            //pixelSize = pxSz;
            dim = d;
            this.setPreferredSize(dim);
            this.setSize(dim);
            viewbox_size_fraction = mf;
            reticle_view = v.copy();
            if( has_viewbox ) {
                int width, height;
                // Calculate reticle size
                // One dimension is constrained to be a defined fraction of this ImagePanel,
                // the other is the appropriate ratio of the constrained dimension.
                // Wow, that's an awful sentence. 
                // NOTE: Due to rounding to integers, the resulting width/height will not equal ratio.
                if( ratio >= 1.0 ) { // width > height
                    width = (int)Math.round(this.getWidth() * mf);
                    height = (int)Math.round(width/ratio);
                }
                else { // height > width                
                    height = (int)Math.round(this.getHeight() * mf);
                    width = (int)Math.round(height*ratio);                
                }            
                // Calculate ImagePanel center and reticle center
                Vec2D pos = new Vec2D(getWidth()/2,getHeight()/2);
                Vec2D retPos = new Vec2D(width/2,height/2);
                // Shift reticle coordinate by difference in center.
                // Since the origin in 0,0, its an assignment.
                pos.eqMinus(retPos);
                staticReticle = new Rectangle((int)Math.round(pos.x),(int)Math.round(pos.y),width,height);
                dynamicReticle = new Rectangle(staticReticle);
                base_reticle_dimension = new Dimension(width,height);
                reticle_view = v.copy();
                defaultSize.eq(reticle_view.size());
            }
	}
	
        public void makeSynced( double aspect_ratio, double pixelSize ) {
            int width, height;
            if( aspect_ratio >= 1.0 ) { // width > height
                width = (int)Math.round(this.getWidth() * viewbox_size_fraction);
                height = (int)Math.round(width/aspect_ratio);
            }
            else { // height > width                
                height = (int)Math.round(this.getHeight() * viewbox_size_fraction);
                width = (int)Math.round(height*aspect_ratio);                
            }            

            // Calculate ImagePanel center and reticle center
            Vec2D pos = new Vec2D(getWidth()/2,getHeight()/2);
            Vec2D retPos = new Vec2D(width/2,height/2);
            // Shift reticle coordinate by difference in center.
            // Since the origin in 0,0, its an assignment.
            pos.eqMinus(retPos);
            staticReticle = new Rectangle((int)Math.round(pos.x),(int)Math.round(pos.y),width,height);
            pos.eqTimes(pixelSize);
            dynamicReticle = new Rectangle(staticReticle);
            reticle_view.pos().eq(getReticlePositionInDataSpace(pixelSize));
            base_reticle_dimension = new Dimension(width,height);            
            fitToReticle(pixelSize);
        }
        
        /**
         * Recalculates the aspect ratio for this ImagePanel's ViewBox.
         * TODO: Insert specifics here, once they are clarified.
         * @param ratio Aspect ratio desired for the ViewBox.
         * @param synced boolean flag for whether or not the containing MinimapPanel is synced to the DataMapVisualizer.
         */
        // TODO: Fix Javadoc
        public void setAspectRatio( double ratio, boolean synced, DataView v, Vec2D delta_size ) {
            int width, height;
            if( ratio >= 1.0 ) { // width > height
                width = (int)Math.round(this.getWidth() * viewbox_size_fraction);
                height = (int)Math.round(width/ratio);
            }
            else { // height > width                
                height = (int)Math.round(this.getHeight() * viewbox_size_fraction);
                width = (int)Math.round(height*ratio);                
            }            
            
            defaultSize.eqPlus(delta_size);

            // Make this ViewBox's center coincident with its parent ImagePanel's center.
            Vec2D a = new Vec2D(getWidth()/2,getHeight()/2);
            Vec2D b = new Vec2D(width/2,height/2);
            a.eqMinus(b);
            staticReticle.x = dynamicReticle.x = (int)Math.round(a.x);
            staticReticle.y = dynamicReticle.y = (int)Math.round(a.y);
            staticReticle.width = dynamicReticle.width = width;
            staticReticle.height = dynamicReticle.height = height;
            base_reticle_dimension = new Dimension(width,height);
        }
        
        public double getImagePixelSize() {
            return image_view.size().x/dim.width;
        }

        // TODO: Javadoc me
        // NOTE: This method is only used by minimaps.
        public double getReticlePixelSize() {
            if( reticle_view.valid() && base_reticle_dimension.width > 0 ) {                
                return reticle_view.size().x/base_reticle_dimension.width;
            }
            else return -1.0;
        }
        
        // TODO: Javadoc me
        public Vec2D getViewPosition() {
            return image_view.pos();
        }
        
        // TODO: Javadoc me
        public Vec2D getReticlePositionInDataSpace(double pixelSize) {
            
            if( staticReticle == null ) {
                return image_view.pos();
            }
            else {
                Vec2D a = new Vec2D(staticReticle.getX(),staticReticle.getY());
                Vec2D t = image_view.pos().opPlus(a.opTimes(pixelSize));
                return t;
            }
        }
        
        public void shiftReticleLocation(Vec2D loc) {
            Vec2I t = loc.toI();
            int x = t.x + staticReticle.x;
            int y = t.y + staticReticle.y;
            staticReticle.setLocation(x,y);
            dynamicReticle.setLocation(x,y);
        }

    /**
     * Causes an update in either the dynamicReticle rectangle's location or the cur_view rectangle's location, 
     * depending on how drastic of a shift it is.
     * @param v Vector to shift the ViewBox by.
     * @param dim_panel Dimension of containing panel.
     * @return specified vector {@code v}, if the shift would cause this ViewBox to fall outside of the 
     *                 bounds of {@code dim_panel}, or {@code null}.
     */
    public Vec2I shiftReticlePosition( Vec2I v, Dimension dim_panel ) {
        // the move would take us outside the panel, such that we'd want to shift the map position...		
        if( (dynamicReticle.getX() < 0 && v.x < 0) || 
            (dynamicReticle.getX() + dynamicReticle.getWidth() > dim_panel.getWidth() && v.x > 0) || 
            (dynamicReticle.getY() < 0 && v.y < 0) || 
            (dynamicReticle.getY() + dynamicReticle.getHeight() > dim_panel.getHeight() && v.y > 0 ) ) { 
                Vec2I tmp = new Vec2I(staticReticle.x,staticReticle.y);                
                tmp.eqMinus(v);
                staticReticle.setLocation(tmp.x,tmp.y);
                return v;
        }
        else {
            Vec2I tmp = new Vec2I(dynamicReticle.x,dynamicReticle.y);                
            tmp.eqPlus(v);
            dynamicReticle.setLocation(tmp.x,tmp.y);
            return null;
        }		
    }        
        
        public void fitToReticle(double pixelSize) {
            Vec2D p = new Vec2D(
                                dim.getWidth()-staticReticle.getWidth(),
                                dim.getHeight()-staticReticle.getHeight()
                               );
            double px = getReticlePixelSize();
            p.eqTimes(px);
            // Set size to be larger than the viewbox by the difference in their size in pixels translated in to data coordinates.
            image_view = new DataView(reticle_view.pos(),reticle_view.size().opPlus(p));
            // Find their respective mid-points
            Vec2D pos = Vec.toD(staticReticle.getLocation());
            pos.eqTimes(pixelSize);
            image_view.pos().eqMinus(pos);
        }        
        
        
    @Override
    public Dimension getPreferredSize() {
        return dim;
    }
    
    @Override
    public Dimension getMinimumSize() {
        return dim;
    }
    
    @Override
    public Dimension getMaximumSize() {
        return dim;
    }

        
    /**
      * Cause all non-null graphical element to be drawn on the specified {@link Graphics} object.
      * @param g Graphics object to be used for drawing.
      */ 
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D)g;
        Color c = g2d.getColor();
        if( image != null ) {
            g2d.drawImage( image, 0, 0, null, null );
        }
        if( staticReticle != null ) {
            if( staticReticle.getX() < 0 && (staticReticle.getX() + staticReticle.getWidth() > dim.getWidth())
                && staticReticle.getY() < 0 && (staticReticle.getY() + staticReticle.getHeight() > dim.getHeight())
              ) {                
                g2d.setColor(Color.BLUE);               
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.25f));	
                // Draw a 10-pixel wide border around the image.
                g2d.fillRect(0, 0, (int)dim.getWidth(), 10); // top bar
                g2d.fillRect(0, 10, 10, (int)dim.getHeight()-10); // left bar
                g2d.fillRect((int)dim.getWidth()-10, 10, 10, (int)dim.getHeight()-20); // right bar
                g2d.fillRect(10, (int)dim.getHeight()-10, (int)dim.getWidth()-10, 10); // bottom bar
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1.0f));	
            } 
            else { 

                if( staticReticle.getX() > dim.getWidth() ) {                                   
                    g2d.setColor(Color.BLUE);               
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.25f));	
                    g2d.fillRect((int)dim.getWidth()-10, 0, 10, (int)dim.getHeight()); // right bar
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1.0f));	
                } 
                if(staticReticle.getX() < -staticReticle.getWidth() ) {
                    g2d.setColor(Color.BLUE);               
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.25f));	
                    g2d.fillRect(0, 0, 10, (int)dim.getHeight()); // left bar
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1.0f));	
                } 
                if( staticReticle.getY() > dim.getHeight() ) { 
                    g2d.setColor(Color.BLUE);               
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.25f));	
                    g2d.fillRect(0, (int)dim.getHeight()-10, (int)dim.getWidth(), 10); // bottom bar
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1.0f));	
                } 
                if( staticReticle.getY() < -staticReticle.getHeight() ) {
                    g2d.setColor(Color.BLUE);               
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.25f));	
                    g2d.fillRect(0, 0, (int)dim.getWidth(), 10); // top bar
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1.0f));	
                }
                
                // NOTE: It is possible for the viewbox to be drawn when it does not appear on screen.
                //       I imagine the performance hit is not significant, so I am willing to let it slide.
                g2d.setColor(Color.BLUE);
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.75f));
                g2d.draw(dynamicReticle);
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.25f));			
                g2d.setColor(Color.BLUE);
                g2d.fill(staticReticle);
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1.0f));
//                g2d.draw(new Rectangle(63,63,125,125));
            }
        }
        g2d.setColor(c); // restore original color
    }
    
    /**
     * Calculates the data map vectors for a image_view that is of size {@code scale_factor*defaultSize}.
     * The new near is such that the center of the new, scaled, image_view is coincident with the center of the 
     * passed in image_view. The specified near and size vectors are overwritten by this method.
     * @param near Vector specifying the upper-left corner of a data map image_view.
     * @param size Vector specifying the size of a data map image_view.
     * @param defaultSize Vector specifying the desired size of a data map image_view to scale.
     * @param scale_factor double value by which to scale.
     */
    // TODO: Fix above comment
    public static void scaleView(Vec2D near, Vec2D size, Vec2D defaultSize, double scale_factor) {
        // scale the max resolution. 
        Vec2D new_size = defaultSize.opTimes(scale_factor); 
        // Calculate the center of our data space.
        Vec2D pre_center = size.opDivide(2).opPlus(near); // (size/2) + near 
        // Calculate where our new resolutions center is, assuming its origin in (0,0).
        Vec2D new_center = new_size.opDivide(2); 
        // We want the two centers to be coincident. How far away are they from eachother?
        Vec2D delta_center = pre_center.opMinus(new_center);    
        // Our new near is that difference, because it represents the translation of (0,0), the origin of the new data image_view,
        // and (near), the origin of the previous data image_view.
        near.eq(delta_center);
        size.eq(new_size);
    }    
}
