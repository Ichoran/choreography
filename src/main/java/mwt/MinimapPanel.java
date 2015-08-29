/* MinimapPanel.java - Responsible for rendering an independant view of an ImagePanel.
 * Copyright 2010 Howard Hughes Medical Institute and Nicholas Andrew Swierczek
 * Copyright 2015 Calico Life Sciences LLC (authored by Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

import mwt.numerics.*;


/**
 * A class which contains a image_view of the DataMapSource contained in an ImagePanel, with controls for syncing 
 * its image_view of the DataMapSource with the DataMapVisualizer main image_view and changing its resolution, in map units/pixel.
 * @author Nicholas Andrew Swieczek (swierczekn at janelia dot hhmi dot org)
 */
public class MinimapPanel extends JPanel {
    /**
     * Serializable identifier. Currently a dummy-value to make a warning go away.
     */
    private static final long serialVersionUID = 33L;

    private MinimapPanelListener listener;

    /**
     * The most recent image_view of the DataMapSource.
     */
    public ImagePanel image;

    /**
     * Panel container for control GUI elements.
     */
    private JPanel ctrls;
    
    /**
     * JButton for controlling whether or not this Minimap is synced to the DataMapVisualizer.
     */
    public  JButton focus;
    
    /**
     * JSlider for controlling the resolution, in map units/pixel, of this Minimap's image_view of the DataMapSource.
     */
    private JSlider jslider_zoom;

    /**
     * Point representing the center of this MinimapPanel, in screen coordinates.
     */
    public Vec2D origin = new Vec2D(125,125);

    /**
     * Integer index to the {@code resolution_mapping}, which is stored in the DataMapVisualizer, that 
     * represents the resolution of this MinimapPanels image_view of the DataMapSource.
     */
    private int zoom_level = 0;
    
    /**
     * Integer index in to the {@code resolution_mapping} ArrayList in this MinimapPanel's DataMapVisualizer
     * that was set prior to the current {@code zoom_level}.
     */
    // TODO: Fix the wording of these comments. They are clunky.
    private int last_zoom_level = 0;

    /**
     * Dimension representing the desired size in sceen coordinates for this MinimapPanel.
     */
    public Dimension dim_panel = new Dimension(250,250);
    
    /**
     * Dimension representing the desired size in screen coordinates for this MinimapPanel's JFrame.
     */
    public Dimension dim_frame = new Dimension(300,260);

    /**
     * Boolean flag which is set {@code true} if a position change event originated from this MinimapPanel.
     * This flag prevents this MinimapPanel from erroneously applying a position change that has already occured.
     */
    private boolean position_change_source = false;

    public double width_ratio = 1.0;    
    
    /**
     * Minimap reference to this MinimapPanel's enclosing JFrame.
     */
    private Minimap parent;
    
    /**
     * Reference to the DataMapSource, which is provided by the DataMapVisualizer.
     */
    private DataMapSource source;

    /**
     * Constructor for when a DataMapSource exists for this MinimapPanel.
     * @param p Reference to enclosing Minimap.
     * @param n Vector representation of this MinimapPanel's upper left DataMapSource image_view bounding box.
     * @param f Vector representation of this MinimapPanel's DataMapSource image_view bounding box size.
     * @param s Reference to a DataMapSource.
     * @param zoom ArrayList of valid zoom factors, which is used in building the JSlider in this MinimapPanel.
     */
    // TODO: Fix Javadoc
    public MinimapPanel(Minimap p, DataView v, DataMapSource s, DataView default_view, Dimension dim, double mf, double ratio, ArrayList<Double> zoom, int zInd, String units) {
        super(true);	
        source = s;
        parent = p;    
        
        if( dim != null ) {
            dim_panel = dim;
            origin = new Vec2D(dim_panel.width/2,dim_panel.height/2);
        }

        listener = new MinimapPanelListener();
        
        width_ratio = 1/parent.width_scale;
        focus = new JButton("Sync");
        focus.addActionListener(listener);
       
        initZoomSlider(zoom, zInd);
        initCtrlPanel(units);
        
        image = new ImagePanel(
                                    dim_panel,
                                    true, 
                                    v,                                    
                                    source.getBounds(),
                                    default_view,
                                    mf,
                                    ratio
                              );

        // NOTE: Using retrieveImage() here is inappropriate
        //       because we neet to create the ImagePanel's image.
        Vec2I size = new Vec2I(image.getWidth(),image.getHeight()); 
        image.fitToReticle(getPixelSize());
        image.defaultSize.eq(default_view.size());
        image.image = source.getView(image.image_view.pos(), size, getPixelSize(), null);
     
        this.setLayout(new BoxLayout(this,BoxLayout.LINE_AXIS));
        this.add(image);//,BorderLayout.WEST);
        this.add(ctrls);//,BorderLayout.EAST);

        this.addMouseWheelListener(listener);
        image.addMouseListener(listener);	 
        image.addMouseMotionListener(listener);
    }
    
    /**
     * Builds the JSlider. Helper function to constructors. 
     * NOTE: Given the mechanics of JSliders, making this display in a pretty fashion for arbitrarily large and 
     * small ArrayLists is beyond my desire to implement, and I am unappologetic for that. :)
     * @param zoom ArrayList of valid zoom factors.
     */
    private void initZoomSlider(ArrayList<Double> zoom, int i) {
        jslider_zoom = new JSlider(JSlider.VERTICAL,0,zoom.size()-1,0);
        jslider_zoom.setValue(i);
        zoom_level = i;
        int ind = 0;       
        Hashtable<Integer, JLabel> labels = new Hashtable<Integer, JLabel>();
        for( Double val : zoom ) {
            labels.put(ind++, new JLabel(val.toString()));
        }
        
        jslider_zoom.setLabelTable(labels);
        jslider_zoom.setPaintLabels(true);
        jslider_zoom.setPaintTicks(true);
        jslider_zoom.setPaintTrack(true);
        jslider_zoom.setSnapToTicks(true);
        jslider_zoom.setMajorTickSpacing(1);
        jslider_zoom.addChangeListener(listener);
        jslider_zoom.addMouseListener(listener);
    }

    /**
     * Builds and laysout the GUI controls panel. Helper function to constructors.
     */
    private void initCtrlPanel(String units) {
        ctrls = new JPanel();
        ctrls.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.gridwidth = GridBagConstraints.REMAINDER;
        ctrls.add(focus,c);
        ctrls.add(jslider_zoom,c);
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.LAST_LINE_END;        
        ctrls.add(new JLabel(units),c);
    }

    private double getPixelRatio() {
        return (parent.getMainWindowDimension().getWidth()/image.base_reticle_dimension.getWidth());
    }
    
    /**
     * 
     * @return
     */
    // TODO: Javadoc me
    private double getPixelSize() {          
        return parent.getResolution(getSliderValue()) * getPixelRatio();
    }
    
    private double getPixelSize(int val) {
        return parent.getResolution(val) * getPixelRatio();
    }
    
    /**
     * Helper function to check what generated a particular Event.
     * @param e The Event to check.
     * @return {@code true} if the source of the specified Event is the {@code jslider_zoom} control, otherwise {@code false}.
     */
    public boolean eventInZoomSlider( EventObject e ) { return e.getSource() == jslider_zoom; }
	
    /**
     * Helper function to check what generated a particular Event.
     * @param e The Event to check.
     * @return {@code true} if the source of the specified Event is the {@code focus} control, otherwise {@code false}.
     */    
    public boolean eventInFocusButton( EventObject e ) { return e.getSource() == focus; }
	
    /**
     * @return A String containing the title of the enclosing Minimap.
     */
    public String getTitle() { return parent.title; }

    /**
     * @return {@code true} if the enclosing Minimap is currently synced to the DataMapVisualizer, otherwise {@code false}.
     */
    public boolean isSynced() { return parent.synced; }

    /**
     * Causes a change in this MinimapPanel's parent Minimap, potentially triggering a cascade of behaviors in both the parent and
     * the DataMapVisualizer.
     * @param b boolean value to which to set the {@code active} flag. If {@code true}, will cause this MinimapPanel's enclosing Minimap
     *                     to become synced to the DataMapVisualizer, desyncing the currently active Minimap. 
     *                     If {@code false}, will restore the original title of this MinimapPanel's enclosing Minimap.
     */
    public void setAndUpdateActive(boolean b) { 
        // WARNING: The order here is important. Calling this before calling desyncAllMinimaps() will cause the user interface
        //          to report all Minimaps as "desynced." This has no effect on behavior, only appearance.
        parent.desyncAllMinimaps();
        parent.setSynced(b);
        image.makeSynced(parent.getMainWindowAspectRatio(),getPixelSize());

        retrieveImage();
        
        
        
        parent.setMainWindowZoomLevel(zoom_level);
        setMainWindowData();        
        //parent.setMainWindowPixelSize();
    }
		
    /**
     * Causes the DataMapVisualizer to match its data map image_view bounding box to this MinimapPanel's Viewbox.
     */
    public void setMainWindowData() {
        parent.setMainWindowData(image.reticle_view);
    }

    /**
     * @param p Point to check
     * @return {@code true} if this MinimapPanel's ImagePanel Viewbox contains the specified point, otherwise {@code false}.
     */
    public boolean curViewContains( Point p ) {
        return image.staticReticle.contains(p);
    }	
	
    /**
     * Moves this MinimapPanel's ViewBox by the specified screen coordinates, without changing its data coordinates.
     * @param c Vector to translate by.
     */
    public void shiftViewBoxLocation( Vec2D c ) {
        // Move this ImagePanel's viewbox screen coordinates by the amount moved in pixels.
        // Since this is the only thing that should change about the viewbox, that's all we change.
        c.eqTimes(getPixelSize());
        int x = image.staticReticle.getLocation().x + (int)Math.round(c.x);
        int y = image.staticReticle.getLocation().y + (int)Math.round(c.y);
        image.staticReticle.setLocation(x,y);
        image.dynamicReticle.setLocation(x,y);
    }
	
    /**
     * Calls this MinimapPanel's ImagePanel's Viewox's {@code shiftViewBoxes} method with the specified vector and specified dimensions.
     * @param c Vector to tranlate by.
     * @param dim Dimensions.
     * @return specified vector {@code c}, or {@code null} if a new DataMapSource image_view does not need to be generated.
     */
    public Vec2I shiftViewBoxes( Vec2I c, Dimension dim ) {        
        return image.shiftReticlePosition(c,dim);
    }
	
    /**
     * Calls this MinimapPanel's ImagePanel's {@code shiftImage} method with the specified vector.
     * Causes a new image_view of the DataMapSource to be generated with the resulting updated image_view bounding box.
     * @param c Vector to translate by.
     */
    public void shiftImage( Vec2I c ) {        
        Vec2D t = c.toD();
        t.eqTimes(getPixelSize());

        image.image_view.pos().eqMinus(t);
        retrieveImage();
    }

    /**
     * Shifts this MinimapPanel's ImagePanel DataView by the specified screen coordinates translated in to data coordinates.
     * Updates the DataMapVisualizer if this Minimap is synced, otherwise it moves the screen coordinates of the ViewBox
     * @param c Vec2I by which to shift.
     */
    public void shiftView( Vec2D c, boolean update_dynamic  ) {
        //t.eqTimes(image.getReticlePixelSize());
        //t.eqTimes(image.getImagePixelSize());
        c.eqTimes(parent.getResolution(getSliderValue()));
               
        if( isSynced() ) { // In a synced minimap, the Viewbox's screen location does not change while the image does, so we change the viewbox's data location and update the main image_view.
            image.reticle_view.pos().eqMinus(c);
            image.fitToReticle((getPixelSize()));
            setMainWindowData();
        }
        else { // however, here the screen location of the viewbox should change, but not its data location.
            image.image_view.pos().eqMinus(c);
            setReticleScreenLocation(update_dynamic);
        }
        retrieveImage();
    }
    
    /**
     * Changes this MinimapPanel's aspect ratio to the specified value, with significant consequences.
     * @param ratio Aspect ratio to set on this MinimapPanel's ImagePanel's ViewBox.
     */
    public void setAspectRatio( double ratio, DataView v, Vec2D delta_size ) {
        image.setAspectRatio(ratio, isSynced(), v, delta_size);
    }

    
    /**
     * Sets the current position of this MinimapPanel's scale factor slider to the specifed location,
     * without changing the DataViews, as a shortcut for maintaining consistency of feedback.
     * @param index position to set slider to.
     */
    
    public void setZoomIndex( int index ) {
        // Sanity check input
        if( index < jslider_zoom.getMinimum() || index > jslider_zoom.getMaximum() ) return;
        // Set state variables.
        last_zoom_level = zoom_level; 
        zoom_level = index;
        // Set GUI output
        jslider_zoom.removeChangeListener(listener);
        jslider_zoom.setValue(index);        
        jslider_zoom.addChangeListener(listener);
    }
    
    /**
     * In a synced Minimap, causes the ImagePanel's ViewBox's DataView to equal the specified one,
     * and updates the ImagePanel's DataView to fit it and a new Image to be retrieved from the DataMapSource.
     * In an unsynced Minimap, calls setReticleScreenLocation with the specified DataView.
     * @param v DataView to set.
     */
    public void setView( DataView v ) {
        //image.reticle_view.equal(v);           
        if( isSynced() ) {
            image.reticle_view.equal(v);      
            image.fitToReticle(getPixelSize());            
        }
        else { 
            image.reticle_view.equal(v);
            setReticleScreenLocation(true);            
        }
        retrieveImage();
    }
    
    /**
     * Causes a new image to be retrieved from the DataMapSource. Calculates the current pixel size.
     */
    public void retrieveImage() {
        Vec2I size = new Vec2I(image.getWidth(),image.getHeight()); 
        try{ 
                double pxSz = getPixelSize();
                image.image = source.getView(image.image_view.pos(), size, pxSz, image.image);
        }
        catch( IllegalArgumentException ex ) {
            System.out.println("WARNING: Minimap \"" + getTitle() + "\" - Viewable area too small.");
        }
        this.repaint();        
    }
    
    /**
     * Generates a new image_view from the DataMapSource. If {@code position_change_source} is false,
     * it will also translate this Minimap's vector representations of the image_view by the specified vector.
     * @param v Vector to translate by.
     */
    public void changeLocation( Vec2D v ) {      
        if( position_change_source ) position_change_source = false;
        else {
            if( isSynced() ) {
                v.eqTimes(image.getReticlePixelSize());
                image.image_view.pos().eqMinus(v);	
                image.reticle_view.pos().eqMinus(v);
            }
            else {
                image.shiftReticleLocation(v);
                //setReticleScreenLocation(null);
            }
        }
        retrieveImage();          
    }

    /**
     * Causes this Minimap to change its resolution of its image_view of the DataMapSource to that specified by the index.
     * @param level index in to DataMapVisualizer's {@code resolution_mapping} ArrayList for this Minimap's DataMapSource image_view.
     */
    public void snapToZoomLevel(int level) {
     //The intention of this function is for it to be called externally, hence its configuration.   
        setZoomLevel(level);
    }

    /**
     * Changes the Viewbox DataView for this MinimapPanel to the specified one, then recalculates the screen representation of that Viewbox.
     * @param v DataView to set.
     */
    public void setReticleScreenLocation( boolean change_dynamic ) {     
        // The reticle exists in the image_view data space, so its size in screen
        // coordinates is related to that, rather than the reticle data space

        //double pixelSize = image.image_view.size().x/dim_panel.width;
        //double pixelSize = image.getReticlePixelSize();
        double pixelSize =  getPixelSize();

        Vec2D pos = new Vec2D(image.reticle_view.pos());
        Vec2D sz = new Vec2D(image.reticle_view.size());
        
        pos.eqMinus(image.image_view.pos());
        pos.eqDivide(pixelSize);

        sz.eqDivide(pixelSize);

        int x = (int)Math.round(pos.x), y = (int)Math.round(pos.y) , w = (int)Math.round(sz.x), h = (int)Math.round(sz.y);
        
        image.staticReticle.x = x;
        image.staticReticle.y = y;
        image.staticReticle.width = w;
        image.staticReticle.height = h;

        if( change_dynamic ) {
            image.dynamicReticle.x = x;
            image.dynamicReticle.y = y;
            image.dynamicReticle.width = w;
            image.dynamicReticle.height = h;            
        }
        repaint();
    }
    
    /**
     * Causes a new image_view of the DataMapSource to be generated at the resolution specified by {@code level}. Also updates
     * GUI elements to reflect the current state.
     * @param level index in to DataMapVisualizer's {@code resolution_mapping} ArrayList for this Minimap's DataMapSource image_view.
     */
    public void setZoomLevel(int level) {
        // Sanity check input
        if( level < jslider_zoom.getMinimum() || level > jslider_zoom.getMaximum() ) return;
        // Set state variables.        
        double previousPixelSize = getPixelSize();
        last_zoom_level = zoom_level; 
        zoom_level = level;
        // Set GUI output
        jslider_zoom.removeChangeListener(listener);
        jslider_zoom.setValue(level);
        jslider_zoom.addChangeListener(listener);
        // If synced, Viewbox's Dataview is scaled, but its screen coordinates remain the same.
        if( isSynced() ) {
            parent.setMainWindowPixelSize(level);            
        }
        // Other wise, ImagePanel's DataView is scaled, and the Viewbox is sized in screen coordinates to match where its DataView is.
        else {            
            Vec2D a = new Vec2D(dim_panel.width/2,dim_panel.height/2);
            Vec2D pt = new Vec2D(a);
        // Calculate point's location in data space.
            pt.eqTimes(previousPixelSize);
            pt.eqPlus(image.image_view.pos()); // screen location in data space.
            ImagePanel.scaleView(image.image_view.pos(), image.image_view.size(), image.defaultSize, parent.getResolution(getSliderValue()));        
        
        // Calculate where point is in the new data space
            a.eqTimes(getPixelSize());
            a.eqPlus(image.image_view.pos()); // screen location in data space.       
        // calculate the difference.
            a.eqMinus(pt);
        // Shift the image_view location so that the specified screen coordinate is at the same data coordinate.
            image.image_view.pos().eqMinus(a);
            Vec2D b = new Vec2D(dim_panel.width/2,dim_panel.height/2);
            b.eqTimes(getPixelSize());
            b.eqPlus(image.image_view.pos());
            
            Vec2I size = new Vec2I(dim_panel.width,dim_panel.height); 
            Vec2D displayed_vals = size.toD();
            displayed_vals.eqTimes(getPixelSize());
        

            image.image_view.size().eq(displayed_vals);
            setReticleScreenLocation(true);
        }
        retrieveImage();    
    } 

    /**
     * @return current value of {@code zoom_level}.
     */
    public int getZoomLevel() { return zoom_level; }

    /**
     * 
     * @return current value of this Minimap's {@code jslider_zoom}.
     */
    public int getSliderValue() { return jslider_zoom.getValue(); }

    /**
     * @return reference to this Minimap's jslider_zoom.
     */
    public JSlider getZoomSlider() { return jslider_zoom; }
	
    /**
     * Calls parent DataMapVisualizer's {@code changeView} method with the specified vector, setting the
     * {@code position_change_source} flag to true so that this translation is not applied twice.
     * @param v Vector to translate by.
     */
    public void changeView(Vec2I v) {
        position_change_source = true;
        parent.changeView(v);
    }
   
    // TODO: Javadoc me
    public void processMove(Vec2I v, boolean move_image) {
        Vec2D shift = v.toD();
        shift.eqTimes(getPixelRatio());

        if( isSynced() ) {
            shiftView(shift,true);
        }
        else {
            if( move_image ) {
                shift.eqTimes(getPixelSize());
                image.reticle_view.pos().eqMinus(shift);
                setReticleScreenLocation(false);
                parent.setMainWindowLocation(image.reticle_view.pos());                                                                                
            }
            else {
                shiftView(shift, true);
            }
        }
        
    }
    
    public void centerView( Vec2D home ) {              
        double pixelSize = getPixelSize();

        Vec2D data_center = Vec2D.zero();
        Vec2D screen_center = new Vec2D(dim_panel.width/2, dim_panel.height/2);
        screen_center.eqTimes(pixelSize);
        screen_center.eqPlus(image.image_view.pos()); // we have the screen center in data space.
        
        data_center.eq(home);
        data_center.eqMinus(screen_center);
        
        if( isSynced() ) { // In a synced minimap, the Viewbox's screen location does not change while the image does, so we change the viewbox's data location and update the main image_view.
            image.reticle_view.pos().eqPlus(data_center);
            parent.setMainWindowLocation(image.reticle_view.pos());
        }
        else { // however, here the screen location of the viewbox should change, but not its data location.
            image.image_view.pos().eqPlus(data_center);
            setReticleScreenLocation(true);
        }
        retrieveImage();
    }
    
    /**
     * 
     */
    private class MinimapPanelListener implements ChangeListener, ActionListener,MouseInputListener, MouseWheelListener {
        /**
         * Reference to the mouse event location that was processed when that event handler was last invoked.
         */
        private Vec2I prev_click = new Vec2I();
        
        /**
         *  Reference to the mouse event location that is being processed during the current invokation of the event handler.
         */
        private Vec2I cur_click = new Vec2I();
        
        /**
         * boolean flag for if a mouse click occured within this MinimapPanel's JSlider
         */
        private boolean slider_click = false;
        
        /**
         * boolean flag for if a mouse click occured within this MinimapPanel's ImagePanel's Viewbox.
         */
        private boolean box_click = false;
        
        /**
         * boolean flag for if a mouse click occured within this MinimapPanel's ImagePanel but outside said ImagePanel's viewbox.
         */
        private boolean map_click = false;

        /**
         * Determins within which GUI element the specified MouseEvent occurred, then sets {@code prev_click} to the specified
         * event's screen coordinates.
         * @param me MouseEvent to process. 
         */
        public void mousePressed(MouseEvent e) {
            requestFocus();
            // If the click was in the cur_view box
            if( eventInZoomSlider(e) ) {
                slider_click = true;
            }
            else {
                // If the viewbox contains the mouse click or we're doing something to the active parent, we want to update our image_view.
                if( isSynced() ) {
                    box_click = false;
                    map_click = true;		                    
                }
                else if( curViewContains(e.getPoint()) ) { // if the click was within the viewbox, there are two option-paths
                    // first, if the viewbox is larger than the minimap panel... . If the click is not in the border region, it is a movement of the image.
                    if( image.staticReticle.getX() < 0 && (image.staticReticle.getX() + image.staticReticle.getWidth() > dim_panel.getWidth())
                        && image.staticReticle.getY() < 0 && (image.staticReticle.getY() + image.staticReticle.getHeight() > dim_panel.getHeight())
                      ) {                        
                        //and the click is in the viewbox indicator region (10 pixel border around minimap) it is a movement of the viewbox
                        if( e.getX() < 10 || e.getX() > dim_panel.getWidth() - 10 || e.getY() < 10 || e.getY() > dim_panel.getHeight()-10 ) {
                            box_click = true;
                            map_click = false;  
                        }
                        // otherwise, it is a movement of the minimap
                        else {
                            box_click = false;
                            map_click = true;                           
                        }
                    }
                    // otherwise, it is just a movement 
                    else {
                        box_click = true;
                        map_click = false;                        
                    }

                }
                // if the click was outside of any existing box, meaning the "image" was clicked on, we just want to update the image without changing the active image_view.
                else {
                    box_click = false;
                    map_click = true;		
                }	
                prev_click.eq(e.getX(),e.getY());
            }		
        }

        /**
         * Causes an updated image_view to be generated from the DataMapSource. If this Minimap is synced to the DataMapVisualizer, a new image_view will also be
         * generated for that. The newly generated image_view will either be a location update or a resolution change, depending on the state.
         * @param me MouseEvent to process.
         */
        public void mouseReleased(MouseEvent e) {
            if( box_click ) {
                box_click = false;
                Vec2D v = new Vec2D(image.staticReticle.x - image.dynamicReticle.x, image.staticReticle.y - image.dynamicReticle.y);
                v.eqTimes(image.getImagePixelSize());
                image.reticle_view.pos().eqMinus(v);
                parent.setMainWindowLocation(image.reticle_view.pos());
//                image.staticReticle.setLocation(image.dynamicReticle.getLocation());
            }
            else if( map_click ) {
                map_click = false;
            }
            else if( slider_click ) {
                slider_click = false;
                // NOTE: We do not catch change events on the slider, so we can
                //       set its value with impunity.
                // NOTE: The structure here is to preserve the state transitions
                //       applied by setZoomLevel, specifically which pixelSize is
                //       available.
                int tmp = getSliderValue();
                jslider_zoom.setValue(getZoomLevel());
                setZoomLevel(tmp);
            }
            repaint();
        } 

        /**
         * Calculates the difference between the specified MouseEvent screen coordinates and {@code prev_click}. 
         * If the drag behavior was initiated within one of the image_view-controlling elements, as specified by the state variables 
         * {@code box_click} and {@code map_click}, the image_view-defining vectors are translated and a new image_view is generated.
         * @param me MouseEvent to process.
         */
        public void mouseDragged(MouseEvent e) {
            cur_click.eq(e.getX(),e.getY());	// set click vector
            cur_click.eqMinus(prev_click);		// cur = cur - prev
            Vec2D t = new Vec2D();
            if( box_click ) {
                Vec2I res = shiftViewBoxes(cur_click, dim_panel);
                if( res != null ) {
                    t.eq(res.toD());
                    t.eqTimes(getPixelRatio());
                    shiftView(t.opMinus(), false); // if shiftViewBoxes returns non-null, it means we moved outside the boundaries of our panel and so should shift the map by an appropriate opposite amount.
                }
            }
            else if( map_click ) {
                t.eq(cur_click.toD());
                t.eqTimes(getPixelRatio());
                shiftView(t, true); // move image position by the difference in mouse event locations
            }
            prev_click.eq(e.getX(),e.getY());
            repaint();
        } // end public void mouseDragged(MouseEvent e)

        /**
         * Causes the index to the DataMapVisualizer's {@code resolution_mapping} ArrayList to be incremented or decremented,
         * depending on the direction of the MouseWheel movement.
         * @param e MouseWheelEvent to process.
         */
        public void mouseWheelMoved(MouseWheelEvent e) {
            if( e.getWheelRotation() < 0 ) { // the wheel is being moved up
                setZoomLevel(getZoomSlider().getValue()+1);
            }
            else { // the wheel is being moved down
                setZoomLevel(getZoomSlider().getValue()-1);
            }
            repaint();
        }	

        /**
         * Causes this Minimap to become the active one, syncronizing it to the DataMapVisualizer.
         * @param e ActionEvent to process.
         */
        public void actionPerformed(ActionEvent e) {
            if( eventInFocusButton(e) ) {		
                if( !isSynced() ) { 	
                    setAndUpdateActive(true);
                    parent.requestFocus();
                }
            }
        }

        /**
         * Causes the index to the DataMapVisualizer's {@code resolution_mapping} ArrayList to be set to the 
         * current value of this MinimapPanel's JSlider, unless the state variable {@code slider_click} indicates
         * this event was generated as part of a mouse drag initiated on said slider.
         * @param e ChangeEvent to process.
         */
        // TODO: Remove me; I may be totally useless.
        public void stateChanged( ChangeEvent e ) {	
            if( eventInZoomSlider(e) ) {	                
                if( !slider_click ) {
                    parent.requestFocus();
                }
            }
        }	
        
        /***************************************************
         | NOTE: Below this comment are all the events that     |
         | we do not care about at all.                                         |
         ***************************************************/
        
        /**
         * An event this MinimapPanel does not care about.
         * @param me MouseEvent to process.
         */        
        public void mouseExited(MouseEvent me) {}  	

        /**
         * An event this MinimapPanel does not care about.
         * @param me MouseEvent to process.
         */        
        public void mouseMoved(MouseEvent me) {}	
        
        /**
         * An event this MinimapPanel does not care about.
         * @param me MouseEvent to process.
         */
        public void mouseClicked(MouseEvent me) {
        }
        
        /**
         * An event this MinimapPanel does not care about.
         * @param me MouseEvent to process.
         */
        public void mouseEntered(MouseEvent me) {}	
    }

} // end public class MinimapPanel extends JPanel implements MouseMotionListener

