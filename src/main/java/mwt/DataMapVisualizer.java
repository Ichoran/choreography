/* DataMapVisualizer.java - Core GUI for the Data Map Visualizer.
 * Copyright 2010 Howard Hughes Medical Institute and Nicholas Andrew Swierczek
 * Also copyright 2015 Calico Life Sciences LLC (authored by Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt;

import java.text.*;
import javax.swing.*;
import java.awt.Dimension;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Point;
import javax.swing.event.*;
import java.awt.event.*;
// Silly ambiguity. I must now enumerate the individual packages from java.util to avoid ambiguous declaration of List.
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.imageio.ImageIO;

import mwt.numerics.*;


/**
 * A class that coordinates, generates, and displays the GUI.
 * @author Nicholas Andrew Swieczek (swierczekn at janelia dot hhmi dot org)
 */
public class DataMapVisualizer extends JFrame implements DataReadyListener {
    /**
     * A reference to a class that handles all desired input events
     */
    private DataMapVisualizerListener listener;	

    // TODO Javadoc me
    public int pixel_step_x;
    
    // TODO Javadoc me
    public int pixel_step_y;
    
    public Vec2D home = Vec2D.zero();
    
    /**
     * A reference to the DataMapSource.
     */
    public DataMapSource source;

    /**
     * A list of possible scaler values by which to multiply the pixel size (map units/pixel).
     */
    public ArrayList<Double> scale_factors = new ArrayList<Double>(10);
    
    // TODO: Javadoc me
    public String scale_units = "";
    
    
    // TODO Javadoc me
    private Cursor cursor;
    
    
    /**
     * The size, in pixels, of the ImagePanel contained within this DMV.
     */
    public Dimension dim_panel = new Dimension(500,500);
    
    /**
     * The size, in pixels, of the frame.
     */
    public Dimension dim_frame;
    
    /**
     * The size, in pixels, for Minimap's ImagePanels.
     */
    public Dimension dim_minimap = new Dimension(250,250);
    
    /**
     * Fraction of Minimap ImagePanel screen dimensions by which to constrain ViewBoxes
     */
    public double minimapFraction = 1.0;
    
    /**
     * The size, in pixels, of the users' monitor resolution.
     */
    public Dimension screen_dim;
    
    /**
     * Stack used for storing next-available minimap ids.
     */
    private Stack<Integer> minimap_ids = new Stack<Integer>();

    /**
     * A JPanel reference to the data currently displayed in this, the main frame.
     */
    private JPanel content;
    
    /**
     * A JPanel reference to the status bar, which is used to relay important information to the user.
     */
    private JPanel status;
    
    /**
     * A JLabel for relaying mouse cursor position information, while the mouse is within the boundaries of the frame.
     */
    private JLabel cursor_pos = new JLabel("(NA,NA)");
    
    /**
     * A JLabel for relaying status information about the cursor location
     * from the source.
     */
    private JLabel status_label = new JLabel();
    
    /**
     * Reference to GUI element retrieved from DataMapSource at creation.
     */
    private JComponent source_gui_element = null;
    
    /**
     * A reference to the vertical ruler displayed on this GUI.
     */
    private Ruler vertical;
    
    /**
     * A reference to the horizontal ruler displayed on this GUI.
     */
    private Ruler horizontal;

    /**
     * A reference to the DataMapSource image_view currently displayed.
     */
    public ImagePanel image;

    /**
     * A List containing all of the currently active minimaps.
     */
    public List<Minimap> minimaps; 

    /**
     * An integer that stores the index in to the {@code scale_factors} ArrayList for the resolution of the currently displayed image.
     */
    public int zoom_index = 0;

    /**
     * A reference to this GUIs menubar.
     */   
    private JMenuBar menubar;
    
    /**
     * A reference to all of the menu items contained in this GUIs menubar.
     */
    private JMenu system;
    
    //private JMenu ;
    
    /**
     * A reference to all of the supported menu commands.
     */
    private JMenuItem exit, add_minimap, keyboard_shortcuts, help;

    /**
     * A number formating object, used for rounding.
     */
    private DecimalFormat df = new DecimalFormat("0.##");
    
    /**
     * String of HTML used to display help.
     **/
    private String help_string = null;

    /**
     * Basic constructor.
     * @param s The DataMapSource for this Visualizer.
     * @param res An ArrayList of possible resolutions, as {@code map units/pixel}.
     * @param ind Index in to resolution ArrayList to use on load.
     * @param minimapSize Dimensions of ImagePanel to use for all Minimaps.
     *                                       If {@code null}, a size of {@code 250,250} is used.
     * @param mf Fraction of {@code minimapSize} to use for synched Minimaps.
     * @param listen_on_image flag to decide if mouse events should be attached to the Frame or to the ImagePanel contained in the frame.
     *                                            This has consequences only for GUI behavior, specifically from where in screen coordinates are mouse events allowed
     *                                            to originate.
     */
    // TODO: Fix javadoc
    public DataMapVisualizer(DataMapSource s, ArrayList<Double> res, int ind, Dimension minimapSize, double mf, boolean listen_on_image, int px_step_x, int px_step_y, int decimal_places, String scale_units, Vec2D h, String title ) {
        super( (title==null) ? "Data Map Visualizer (v1.2.0)" : title );

        // Set home, or leave it at 0,0 if null is passed.
        if( h != null ) {
            home.eq(h);
        }
        
        // Load custom cursor, if it exists.
        Toolkit tk = Toolkit.getDefaultToolkit();       
        try{
            BufferedImage img = ImageIO.read(this.getClass().getResourceAsStream("/DMV_cursor.png"));
            cursor = tk.createCustomCursor(img,new Point(img.getWidth()/2,img.getHeight()/2),"DMV cursor");
        }
        catch(Exception ex){
            cursor = Cursor.getDefaultCursor();
        }
        
        // Set the minimap size, or leave it at defaults
        if( minimapSize != null ) {
            dim_minimap = minimapSize;
            minimapFraction = mf;
        }
        
        // Sanity check pixel step input, and assign.
        pixel_step_x = (px_step_x > 0 ) ? px_step_x : 1; 
        pixel_step_y = (px_step_y > 0 ) ? px_step_y : 1; 
        
        // Set # decimal places, or leave it at default if a nonsense value was passed
        // NOTE: Assumes we actually never have integers.
        if( decimal_places > 0 ) {
            StringBuffer buf = new StringBuffer("0.");
            for( int i = 0; i < decimal_places; i++ ) {
                buf.append("#");
            }
            df = new DecimalFormat(buf.toString());
        }
        
        source = s;
        
        // Create minimap storage and initialize minimap id stack.
        minimaps = Collections.synchronizedList(new ArrayList<Minimap>(10) );
        minimap_ids.push(1);
        
        // Create the rulers, assuming that the default desired size should be dim_panel.
        vertical = new Ruler(Ruler.VERTICAL,dim_panel, s.getBounds().x, s.getBounds().y, s.getBounds().width,s.getBounds().height,decimal_places);
        horizontal = new Ruler(Ruler.HORIZONTAL,dim_panel, s.getBounds().x, s.getBounds().y, s.getBounds().width,s.getBounds().height,decimal_places);
        
        // Only used for minimap screen location sanity.
        screen_dim = Toolkit.getDefaultToolkit().getScreenSize();

        // Create the event listener.
        listener = new DataMapVisualizerListener();
        // Create the image, using non-viewboxed constructor.
        image = new ImagePanel(dim_panel);
        // Attach the event listener based on where events should be processed.
        if( listen_on_image ) {
            image.addMouseListener(listener);
            image.addMouseMotionListener(listener);
            image.addMouseWheelListener(listener);
        }
        else {
            this.addMouseListener(listener);
            this.addMouseMotionListener(listener);
            this.addMouseWheelListener(listener);
        }
        // Attach keyboard listener.
        this.addKeyListener(listener);        

        content = new JPanel();
        content.setLayout(new BorderLayout());
        this.setContentPane(content);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Initialize pixel size arraylist.
        loadZoomFactors(res,ind,scale_units);
        // Initialize the image
        initImage();
        // Ditto the status bar
        initStatusBar();		
        // Ditto the menu bar
        initMenuBar();
        
        // Bundle the bottom elements to maintain layout		
        Container c = new Container();
        c.setLayout(new BorderLayout());		
        c.add(horizontal, BorderLayout.NORTH);
        c.add(status, BorderLayout.CENTER);
        source_gui_element = source.getGUIElement();
        if( source_gui_element != null ) {
            c.add(source_gui_element,BorderLayout.SOUTH);
        }
        // Slap everything in the frame.
        content.add(c, BorderLayout.SOUTH);
        content.add(vertical,BorderLayout.EAST);
        content.add(image, BorderLayout.CENTER);           
    }
    
    /**
     * Set new help string.
     **/
    public void setHelpText(String new_help) { help_string = new_help; } 
   
    /**
     * Generates the dimensions of this JFrame from Java-created data, sets the size and preffered size of this frame, and sets it visible for rendering.
     */
    public void makeVisible() {
        this.setVisible(true);
        this.pack();
        /* NOTE: Until this point, we cannot trust that anything has been rendered at all. In fact, it's not even clear that we can trust this to exist now, but the desired
         *       values should remain static from here on out, so here's where we grab them.
         *
         */
        dim_frame = new Dimension((int)(image.dim.getWidth()+
                                                                (vertical.getWidth()+
                                                                 getInsets().left+
                                                                 getInsets().right
                                                                )
                                                               ),
                                                        (int)(image.dim.getHeight()+
                                                                (status.getHeight()+
                                                                 horizontal.getHeight()+
                                                                 ((source_gui_element == null ) ? 0 : source_gui_element.getHeight())+
                                                                 menubar.getHeight()+
                                                                 getInsets().top+
                                                                 getInsets().bottom
                                                                )
                                                               )
                                                       );         
        setSize(dim_frame);
        setPreferredSize(dim_frame);
        // Finally, start listening for resize events. This must be attached here as otherwise it will cause
        // a race-condition between this function and the window resize callbacks.
        this.addComponentListener(listener);     
    }

    /**
     * A helper function that asks for a image_view from the DataMapSource, performs the necessary calculations to generate a proper ruler, and sets the correct status text.
     */
    // TODO refactor me
    private void initImage() {
        Rectangle2D.Double dataBounds = source.getBounds();
        Vec2D near = new Vec2D(dataBounds.x,dataBounds.y);
        Vec2I size = new Vec2I(dim_panel.width,dim_panel.height); 
        Vec2D displayed_vals = size.toD();
        Vec2D data_size = new Vec2D(dataBounds.width,dataBounds.height);
        image.image_view = new DataView(near,data_size);
        image.dim = dim_panel;       
        image.defaultSize.eq(data_size);
        image.image = source.getView(near, size, getPixelSize(), null);

        vertical.view.pos().eq(near);
        displayed_vals.eqTimes(getPixelSize());
        vertical.view.size().eq(displayed_vals);
        horizontal.view.pos().eq(near);
        horizontal.view.size().eq(displayed_vals);

        setPixelSize(getResolution(zoom_index), new Vec2D(0,0));
    }

    /**
     * A helper function that creates the {@code status} JPanel, sets its layout, and adds the status JLabels to it.
     */
    private void initStatusBar() {
        status = new JPanel();
        status.setLayout(new FlowLayout(FlowLayout.LEADING));
        status.add(cursor_pos);
        status.add(status_label);
    }

    /**
     * A helper function that generates a proper, populated, JMenuBar and assigns it to this Visualizer.
     */
    private void initMenuBar() {
        menubar = new JMenuBar();
        system = new JMenu("System");
        system.setMnemonic(KeyEvent.VK_S);
        exit = new JMenuItem("Exit");
        exit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, ActionEvent.CTRL_MASK));
        exit.setMnemonic(KeyEvent.VK_X);
        exit.addActionListener(listener);
        add_minimap = new JMenuItem("Add Minimap...");
        add_minimap.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, ActionEvent.CTRL_MASK));
        add_minimap.setMnemonic(KeyEvent.VK_A);
        add_minimap.addActionListener(listener);
        system.add(add_minimap);
        system.add(exit);
        menubar.add(system);
        
        help = new JMenuItem("Help");
        help.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H,ActionEvent.ALT_MASK));
        help.addActionListener(listener);
        menubar.add(help);
        this.setJMenuBar(menubar);
    }

    /**
     * Creates and makes visible a new minimap.
     * @param id The ID to use as the JFrame window title
     * @param x The X-coordinate on the users' screen where this minimap will be positioned at creation.
     * @param y The Y-coordinate on the sures' screen where this minimap will be positioned at creation.
     * @param n The upper left coordinate of the image_view of the Data Source this minimap will display.
     * @param f The lower right coordinate of the image_view of the Data Source this minimap will display.
     * @param zoom An index in to the {@code scale_factors} ArrayList for the starting resolution of this minimap.
     * @param active A flag for whether or not this minimap will control the main window image_view at creation.
     */
    // TODO: Fix Javadoc
    private void createMinimap(int id, int x, int y, DataView v, int zoom, boolean active ) {
        int width = this.getWidth()-(vertical.getWidth()+
                                     getInsets().left+
                                     getInsets().right);
        int height = this.getHeight()-(status.getHeight()+
                                       horizontal.getHeight()+
                                       ((source_gui_element == null ) ? 0 : source_gui_element.getHeight())+
                                       menubar.getHeight()+
                                       getInsets().top+
                                       getInsets().bottom);

        Vec2D new_size = new Vec2D(width,height);
        new_size.eqTimes(image.defaultSize.x/dim_panel.getSize().width);

        Vec2D delta_size = new_size.opMinus(image.defaultSize);
        
        DataView newView = new DataView(v.pos(),image.defaultSize.opPlus((delta_size)));
        Minimap newmap = new Minimap(id,x,y,v,zoom,active,this,listener,source,newView, dim_minimap,minimapFraction,(double)width/height,scale_factors,scale_units);
        newmap.makeVisible();   
        
        synchronized(minimaps) {	
            minimaps.add(newmap);
        }        
    }
        
    /**
     * Creates a new minimap, calculating both the default position for it and shifting this JFrame's screen location accordingly.
     */
    public void createNewMinimap() {
        int t_x = 0, t_y = this.getY();
        // The following decides if we're going to go off the bottom of the screen and moves to the next column if so.
        if( minimaps.size() > 0 ) { 
            t_x = minimaps.get(minimaps.size()-1).getX();
            t_y = minimaps.get(minimaps.size()-1).getY() + minimaps.get(minimaps.size()-1).getHeight();
            
            if( t_y+minimaps.get(minimaps.size()-1).getHeight() > screen_dim.getHeight() ) {
                t_x += minimaps.get(minimaps.size()-1).getWidth();
                t_y = this.getY();
            }			
        }
        
        // Prepare minimap id stack for next value. Note that if the stack is not empty,
        // it means a previously used id has been freed and should be used.
        int id = minimap_ids.pop().intValue();
        if( minimap_ids.empty() ) {
            minimap_ids.push(id+1);
        }
        
        createMinimap(	id,
                        t_x,
                        t_y,
                        image.image_view,
                        zoom_index,
                        minimaps.size() == 0
//                        false
                     );
        // Shift main window over, to keep a nice graphical arrangement. Note that
        // this will stop working after a certain number of minimaps have been 
        // created.
        int tmp_x = minimaps.get(minimaps.size()-1).getX()+minimaps.get(minimaps.size()-1).getWidth();

        if( tmp_x > this.getX() &&
            (minimaps.get(minimaps.size()-1).getY()+minimaps.get(minimaps.size()-1).getHeight()) >= this.getY() 
          ) {
            this.setLocation( 
                                tmp_x,
                                this.getY()
                            );
        }
    }	    
    
    public double getAspectRatio() {       
        return dim_panel.getWidth()/dim_panel.getHeight();
    }
    
    /**
     * Recalculates all GUI element sizes in response to a window resizing event, as well as requesting a new image_view from {@code source}.
     */
    // TODO: Refactor me
    public void setAspectRatio() {       
        try {
            // calculate the new size, subtracting frame insets.
            int width = this.getWidth()-(vertical.getWidth()+
                                         getInsets().left+
                                         getInsets().right);
            int height = this.getHeight()-(status.getHeight()+
                                           horizontal.getHeight()+
                                           ((source_gui_element == null ) ? 0 : source_gui_element.getHeight())+
                                           menubar.getHeight()+
                                           getInsets().top+
                                           getInsets().bottom);

            // Set the new data space size by translating the above width and height in to data coordinates.
            setSize( new Vec2D(
                                width*(getPixelSize()),
                                height*(getPixelSize())
                              ));
  
            
            Vec2D new_size = new Vec2D(width,height);
            new_size.eqTimes(image.defaultSize.x/dim_panel.getSize().width);

            dim_panel.setSize(width,height);		
            vertical.setDimension(dim_panel);
            horizontal.setDimension(dim_panel);

            image.dim.setSize(dim_panel);    
            Vec2D delta_size = new_size.opMinus(image.defaultSize);

            DataView tmp = new DataView(image.image_view);
            ImagePanel.scaleView(tmp.pos(), tmp.size(), image.defaultSize, 1.0);        
            
            image.defaultSize.eq(new_size);

            retrieveImage();

            setAllMinimapAspectRatios((double)width/(double)height, tmp, delta_size);
        }
        catch( NullPointerException e ) {
            return;
        }
    }
	
    /**
     * Calls setAspectRatio for all Minimaps currently in the Minimap List.
     * @param width_height_ratio Aspect ratio to set in all Minimaps.
     */   
    public void setAllMinimapAspectRatios(double width_height_ratio, DataView v, Vec2D delta_size) {
        synchronized(minimaps) {
            for( Minimap map : minimaps ) {
                map.setAspectRatio(width_height_ratio,v,minimapFraction,delta_size);
            }
        }
    }
    
    /**
     * Iterates over all existing {@code minimaps}, setting any active flags to {@code false}.
     */
    public void desyncAllMinimaps() {
        synchronized(minimaps) {
            for( Minimap map : minimaps ) {
                if( map.synced ) {
                    map.setSynced(false);
                }
            }
        }
    }

    public DataView getMainView() {
        return image.image_view;
    }
    
    /**
     * Changes the current resolution to the specified value from this DataMapVisualizer's {@code scale_factors}.
     * @param level The index to set, clipped to  {@code 0 <= level < scale_factors.size()}.
     */
    public void setMainWindowPixelSize(int level) {
        // Clip level to be within the bounds of the valid resolutions
        int previous_zoom = zoom_index;
        if( level < 0 ) zoom_index = 0;
        else if( level > this.scale_factors.size()-1 ) zoom_index = this.scale_factors.size()-1;
        else zoom_index = level;
        setPixelSize(getResolution(previous_zoom), new Vec2D((int)(dim_panel.getWidth()/2),(int)(dim_panel.getHeight()/2)));
    }
	
    /**
     * Retrieves a new image_view from {@code source} based on the specified vectors.
     * @param n Vector specifying the upper left corner of the new image_view.
     * @param f Vector specifying the lower right corner of the new image_view.
     */ 
    public void setMainViewDataVectors(Vec2D n, Vec2D f) {
        setNear(n);
        
        Vec2I size = new Vec2I(dim_panel.width,dim_panel.height); 
        Vec2D displayed_vals = size.toD();
        displayed_vals.eqTimes(getPixelSize());
        
        setSize(displayed_vals);
        
        retrieveImage();
        
        //System.out.println("SET VIEW " + image.image_view);
        
        // NOTE: Because this call comes from the synced Minimap, its image has
        // already been updated and we just need to update the screen representation 
        // of the Reticles.
        setMinimapViews(image.image_view);
     }

    /**
     * Calls setDataView for all non-synced Minimaps currently in the Minimap List.
     * @param v DataView to set.
     */   
    public void setMinimapViews(DataView v) {
        for( Minimap m : minimaps ) {
            if( !m.synced ) {
                m.setView(v);
            }
        }
    }
        
    /**
     * Changes the {@code near} vector for this Visualizers' image_view from {@code source}, updating the horizontal ruler, vertical ruler, and status display as appropriate.
     * @param n Vector specifying the upper left corner of the image_view bounding box.
     */
    public void setNear( Vec2D n ) {        
        image.image_view.pos().eq(n);
        horizontal.view.pos().eq(n);
        vertical.view.pos().eq(n);
    }
	
    /**
     * Changes the {@code far} vector for this Visualizers' image_view from {@code source}, updating the horizontal ruler, vertical ruler, and status display as appropriate.
     * @param f Vector specifying the lower right corner of the image_view boundind box.
     */
    public void setSize( Vec2D f ) {
        image.image_view.size().eq(f);
        horizontal.view.size().eq(f);
        vertical.view.size().eq(f);        
    }
	
    /**
     * Causes the current image_view of the data source displayed in this Visualizer to be changed by the specified {@code delta_amount}.
     * @param delta_level   A value, either positive or negative, to change the current index {@code zoom_index} of {@code scale_factors} by. 
     *                                     If {@code zoom_index + delta_level } causes {@code 0 <= zoom_index < scale_factors.size()} to be false, 
    *                             no changes are made to this Visualizer. Otherwise, a new image_view at the specified resolution is generated.
    * @param pt data point represented by the specified point will be in the same location in the new image_view.
    */
    // TODO: Fix javadoc. Bad wording.
    public void setPixelSize(double previousPixelSize, Vec2D pt) {
        Vec2D a = new Vec2D(pt);
        Vec2D tmp = new Vec2D(pt);

        
        // Calculate point's location in data space.
        pt.eqTimes(previousPixelSize);
        pt.eqPlus(image.image_view.pos()); // screen location in data space.
        ImagePanel.scaleView(image.image_view.pos(), image.image_view.size(), image.defaultSize, getPixelSize());        
        
        // Calculate where point is in the new data space
        a.eqTimes(getPixelSize());
        a.eqPlus(image.image_view.pos()); // screen location in data space.       
        // calculate the difference.
        a.eqMinus(pt);
        // Shift the image_view location so that the specified screen coordinate is at the same data coordinate.
        image.image_view.pos().eqMinus(a);
        tmp.eqTimes(getPixelSize());
        tmp.eqPlus(image.image_view.pos());
        setNear(image.image_view.pos());

        Vec2I size = new Vec2I(dim_panel.width,dim_panel.height); 
        Vec2D displayed_vals = size.toD();
        displayed_vals.eqTimes(getPixelSize());
        
        setSize(displayed_vals);

        retrieveImage();

        setSyncedMinimapPixelSize(zoom_index);    
        setAllMinimapViews(image.image_view);
    }
	
    /**
     * Changes the text of {@code cursor_pos} to display the specified values. This function assumes the content of {@link String}s {@code x and y} were calculated elsewhere.
     * @param x String representing the x-coordinate.
     * @param y String representing the y-coordinate.
     */
    public void updateStatus(String x, String y) {
        cursor_pos.setText("(" + x + "," + y + ")");
    }
	
    /**
     * Translates the specified x and y coordinates from screen coordinates in to data coordinates, updating {@code cursor_pos} appropriately.
     * @param x x coordinate
     * @param y y coordinate
     */
    // TODO: Refactor me
    public void updateStatus(int x, int y) {
        Vec2D loc = new Vec2D(x,y);
        
        double pxSz = getPixelSize();
        
        loc.eqTimes(pxSz);
        loc.eqPlus(image.image_view.pos());

        String xx = df.format(loc.x);       
        xx = ( xx.compareTo("-0") == 0 ) ? "0" : xx;
        String yy = df.format(loc.y);
        yy = ( yy.compareTo("-0") == 0 ) ? "0" : yy;
        cursor_pos.setText("(" + xx + "," + yy + ")");
        
        status_label.setText(
                                source.getStatus(image.image_view.pos(), 
                                                 new Vec2I(image.dim.width,image.dim.height), 
                                                 pxSz, 
                                                 loc)
                            );
    }

    /**
     * Removes the specified Minimap from this DataMapVisualizer's Minimap list.
     * @param map
     */
    public void removeMinimap(Minimap map) {       
        int ind;
        ind = Integer.parseInt(map.getID());
        minimap_ids.push(ind);
        
        if( map != null && minimaps.size() > 0 ) minimaps.remove(map);
    }
	
    /**
     * Causes all existing Minimaps to have their image_view-defining vectors shifted by the specified vector.
     * @param v Vector to shift image_view bounding box vectors by.
     */
    public void shiftAllMinimapViewLocations(Vec2D v) {
        synchronized( minimaps ) {
            for( Minimap m : minimaps ) {
                m.changeLocation(v);
            }
        }
    }
    
    
    /**
     * Calls updateImage for all Minimaps currently in the Minimap List.
     */
    public void updateAllMinimapImages() {
        synchronized(minimaps) {
            for( Minimap m : minimaps ) {
                m.updateImage();
            }
        }        
    }
    
    /**
     * Calls setView for all Minimaps currently in the Minimap List.
     * @param v DataView to set.
     */
    public void setAllMinimapViews(DataView v) {
        synchronized(minimaps) {
            for( Minimap m : minimaps ) {
                m.setView(v);
            }
        }
    }
    
    // TODO: Possibly refactor me to always store reference to Synced minimap.
    /**
     * Traverses the Minimap List to locate the (assumed) only synced one and calls setZoomIndex on it with the specified index.
     * @param index integer index to set.
     */
    public void setSyncedMinimapPixelSize( int index ) {
        synchronized( minimaps ) {
            for( Minimap m : minimaps ) {
                if( m.synced ) {
                    m.setZoomIndex(index);
                    return;
                }
            }
        }
    }
       
    /**
     * Remaps the specified vector in to this DataMapVisualizer's resolution, then translates the image_view-defining vectors for this 
     * DataMapVisualizer by that amount, updating all minimaps and status displays as appropriate.
     * @param v Vector, assumed to have a {@code map unit/pixel} value of 1, to translate by. 
     */
    public void shiftViewLocation( Vec2I v ) {
        Vec2D t = v.toD();
        t.eqTimes(getPixelSize());
        setNear(image.image_view.pos().opMinus(t));
        
        retrieveImage();
        setAllMinimapViews(image.image_view);
    }

    // TODO: Javadoc me.
    /**
     * Causes this DataMapVisualizers ImagePanel DataView position to equal the specified Vec2D, updating GUI feedback, and generating a new image
     * from the DataMapSource.
     * @param v Vec2D to set as the data location
     */
    public void setViewLocation( Vec2D v ) {
        image.image_view.pos().eq(v);
        setNear(image.image_view.pos());
        setAllMinimapViews(image.image_view);
        retrieveImage();
    }
    
    
    /**
     * Helper function to check what generated a particular Event.
     * @param e The Event to check.
     * @return {@code true} if the source of the specified Event is the {@code add_minimap} MenuItem, otherwise {@code false}.
     */
    public boolean eventInAddMinimap(EventObject e) {
        return e.getSource() == add_minimap;
    }
	
    /**
     * Helper function to check what generated a particular Event.
     * @param e The Event to check.
     * @return {@code true} if the source of the specified Event is the {@code exit} MenuItem, otherwise {@code false}.
     */
    public boolean eventInExit( EventObject e ) {
        return e.getSource() == exit;
    }
    
    public boolean eventInHelp( EventObject e ) {
        return e.getSource() == help;
    }
		
    /**
     * Retrieves the {@code map unit/pixel} value for the specified index.
     * @param index Index to retrieve. 
     * @return double value from this DataMapVisualizer's {@code scale_factors}, or -1.0 if {@code index < 0 || index >= scale_factors.size()}.
     */
    public double getResolution( int index ) {
        try {
            return scale_factors.get(index).doubleValue();
        }
        catch( IndexOutOfBoundsException ex ) {
            return -1.0d;
        }
    }
	
    /**
     * Transfers the values from the specified ArrayList in to this DataMapVisualizer's {@code scale_factors}, 
     * or generates a generic set of resolutions from the data map if {@code null} is passed in.
     * @param list List of values to add.
     */
    public void loadZoomFactors(ArrayList<Double> list, int starting_index,String units) {
        if( list != null ) {
            scale_factors.addAll(list);
            zoom_index = starting_index;
        }
        else {
            // NOTE: Not sure that there's a prettier way to do this.
            scale_factors.add(0.5);
            scale_factors.add(0.6);
            scale_factors.add(0.7);
            scale_factors.add(0.8);
            scale_factors.add(0.9);
            scale_factors.add(1.0);
            scale_factors.add(1.1);		
            scale_factors.add(1.2);
            scale_factors.add(1.3);
            scale_factors.add(1.4);
            scale_factors.add(1.5);
            zoom_index = starting_index;
        }               
        scale_units = units;
    }
	
    /**
     * 
     * @param val
     */
    // TODO: Javadoc me
    public void setFocusedMinimap( int val ) {
        int ind;
        synchronized(minimaps) {
            for( Minimap m : minimaps ) {
                ind = Integer.parseInt(m.getID());
                if( ind == val ) {
                    m.requestFocus();
                    break;
                }
            }
        }
    }
    
    // TODO: Javadoc me
    public double getPixelSize() {
        //return image.image_view.size().x/image.dim.width;
        //System.out.println("RES " + getResolution(zoom_index));
        return getResolution(zoom_index);
    }
    
    // NOTE: Should we make home user-settable at runtime?
    // TODO: Javadoc me
    public void centerView() {
        double pixelSize = getPixelSize();

        Vec2D data_center = Vec2D.zero();
        Vec2D screen_center = new Vec2D(dim_panel.width/2, dim_panel.height/2);
        screen_center.eqTimes(pixelSize);
        screen_center.eqPlus(image.image_view.pos()); // we have the screen center in data space.
        
        data_center.eq(home);
        data_center.eqMinus(screen_center);
        
        setNear(image.image_view.pos().opPlus(data_center));
        
        retrieveImage();
        setAllMinimapViews(image.image_view);
    }
    
    /**
     * Causes a new image to be retrieved from the DataMapSource. Calculates the current pixel size.
     */
    public void retrieveImage() {             
        Vec2I image_size = new Vec2I(image.dim.width,image.dim.height); 
        try {
            image.image = source.getView(image.image_view.pos(), image_size, getPixelSize(), image.image);
        }
        catch( IllegalArgumentException e ) {
            System.out.println("WARNING: DataMapVisualizer - Viewable area too small.");
        }
        repaint();
    }    
    
    /**
     * Event handler for when a new image_view from this DataMapVisualizer's {@code source} is ready.
     * Moves the main window to a data coordinate if the event contains one.
     * @param e DataReadyEvent to process.
     */
    public void dataReady(DataReadyEvent e) {
        if (e.data_position != null) {
          double pixelSize = getPixelSize();
          Vec2D screen_center = new Vec2D(dim_panel.width/2 , dim_panel.height/2);
          screen_center.eqTimes(pixelSize);
          Vec2D target = e.data_position.opMinus(screen_center);
          if (target.dist(image.image_view.pos()) > 1.5*pixelSize) {
            setNear(target);
            setAllMinimapViews(image.image_view);
          }
        }
        retrieveImage();
        synchronized( minimaps ) {
            for( Minimap m : minimaps ) {
                m.updateImage();
            }
        }
    }
    
    /**
     * Multi-purpose event handling class for the DataMapVisualizer.
     * @author Nicholas Andrew Swieczek (swierczekn at janelia dot hhmi dot org)
     */
    private class DataMapVisualizerListener implements WindowListener, MouseInputListener, MouseWheelListener, ActionListener, ComponentListener, KeyListener {
        /**
         * Reference to the mouse event location that was processed when that event handler was last invoked.
         */
        private Vec2I prev_click = new Vec2I();

        /**
         *  Reference to the mouse event location that is being processed during the current invokation of the event handler.
         */
        private Vec2I cur_click = new Vec2I();

        /**
         * Causes the cursor location status JLabel to display NA.
         * @param e MouseEvent to process.
         */
        public void mouseExited(MouseEvent e) {
            updateStatus("NA","NA");
            setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }  	

        /**
         * Updates the cursor location status JLabel to display the current cursor location.
         * @param e MouseEvent to process.
         */
        public void mouseMoved(MouseEvent e) {
            updateStatus(e.getX(),e.getY());
        }	

        /**
         * Updates the cursor location status JLabel to display current cursor location and tries 
         * to ensure this DataMapVisualizer has input focus.
         * @param e MouseEvent to process.
         */
        public void mouseEntered(MouseEvent e) {
            updateStatus(e.getX(),e.getY());

            
            setCursor(cursor);
        }	

        /**
         * Updates {@code prev_click} to the location of MouseEvent {@code e} in order to establish
         * the first reference point for a subsequent {@code mouseDragged} event.
         * @param e MouseEvent to process.
         */
        public void mousePressed(MouseEvent e) {
            requestFocus();
            double pixelSize = getPixelSize();
            Vec2D loc = new Vec2D(e.getX(),e.getY());

            Vec2D zero = image.image_view.pos().opMinus(); // amount to subtract from image_view.pos to get data 0,0.
            zero.eqDivide(pixelSize); // zero's position in screen coordinates.

            Vec2D delta = loc.opMinus(zero);

            delta.eq(Math.round(delta.x),Math.round(delta.y));

            delta.eqTimes(pixelSize);

            zero.eqTimes(pixelSize);
            zero.eqPlus(delta);
            zero.eqPlus(image.image_view.pos());

            if( e.isPopupTrigger() ) {    
                JPopupMenu tmp = source.getContextMenu(image.image_view.pos(), new Vec2I(image.dim.width,image.dim.height), pixelSize, zero);
                if( tmp != null ) {
                    tmp.show(content, e.getX(), e.getY());
                }
            } 
            prev_click.eq(e.getX(),e.getY());
        }

        /**
         * An event this DataMapVisualizer does not care about.
         * @param me MouseEvent to process.
         */        
        // TODO: Refactor me
        public void mouseReleased(MouseEvent e) {
            double pixelSize = getPixelSize();
            Vec2D loc = new Vec2D(e.getX(),e.getY());

            Vec2D zero = image.image_view.pos().opMinus(); // amount to subtract from image_view.pos to get data 0,0.
            zero.eqDivide(pixelSize); // zero's position in screen coordinates.

            Vec2D delta = loc.opMinus(zero);

            delta.eq(Math.round(delta.x),Math.round(delta.y));

            delta.eqTimes(pixelSize);

            zero.eqTimes(pixelSize);
            zero.eqPlus(delta);
            zero.eqPlus(image.image_view.pos());

            if( e.isPopupTrigger() ) {               
                JPopupMenu tmp = source.getContextMenu(image.image_view.pos(), new Vec2I(image.dim.width,image.dim.height), pixelSize, zero);
                if( tmp != null ) {
                    tmp.show(content, e.getX(), e.getY());
                }
            } 
        }         
        
        /**
         * Calculates the difference between {@code prev_click} and the current location of the MouseEvent {@code e}, 
         * then calls {@code shiftViewLocation} on this DataMapVisualizer with the resulting vector.
         * @param e MouseEvent to process.
         */
        public void mouseDragged(MouseEvent e) {
            cur_click.eq(e.getX(),e.getY());
            cur_click.eqMinus(prev_click);
            shiftViewLocation(cur_click);
            prev_click.eq(e.getX(),e.getY());
        } // end public void mouseDragged(MouseEvent e)

        /**
         * Increments or decrements this DataMapVisualizer's {@code scale_factors} index by 1, 
         * by calling {@code changeActiveMinimapZoomLevel}, depending on the direction of the MouseWheelEvent.
         * @param e MouseWheelEvent to process.
         */
        public void mouseWheelMoved(MouseWheelEvent e) { 
            if( e.getWheelRotation() < 0 ) {
                if(zoom_index+1 < scale_factors.size()) {
                    zoom_index++;
                    setPixelSize(getResolution(zoom_index-1), new Vec2D(e.getPoint().x,e.getPoint().y));
                }                
            }
            else {
                if(zoom_index-1 >= 0) {
                    zoom_index--;
                    setPixelSize(getResolution(zoom_index+1), new Vec2D(e.getPoint().x,e.getPoint().y));
                }
            }           
        }	
        
        /**
         * Causes appropriate behaviors to be executed from the JMenuBar by this DataMapVisualizer depending on 
         * the source of the specified ActionEvent.
         * @param e ActionEvent to process.
         */
        public void actionPerformed(ActionEvent e) {
            if( eventInAddMinimap(e) ) {
                createNewMinimap();
            }
            else if( eventInExit(e) ) {
                System.exit(0);
            }
            else if( eventInHelp(e) ) {
                new HelpWindow(getTitle() + " help",help_string);
            }
        }
      
        /**
         * Causes the Minimap window that generated the specified WindowEvent to be removed from 
         * this DataMapVisualizer's Minimap list.
         * @param e WindowEvent to process.
         */
        public void windowClosing(WindowEvent e) {
                removeMinimap((Minimap)e.getSource());
        }

        /**
         * Causes this DataMapVisualizer's {@code setAspectRatio()} method to be called.
         * @param e ComponentEvent to process.
         */
        public void componentResized(ComponentEvent e) {
            setAspectRatio();
        }

        public void keyTyped(KeyEvent arg0) { }

        /**
         * An event this DataMapVisualizer does not care about.
         * @param arg0 KeyEvent to process.
         */
        public void keyPressed(KeyEvent arg0) {
            char c = arg0.getKeyChar();
            if( c >= 48 && c <= 57 ) {
                setFocusedMinimap(c-48);
            }
            else if( c == 43 ) {
                if(zoom_index+1 < scale_factors.size()) {
                    zoom_index++;
                    setPixelSize(getResolution(zoom_index-1), new Vec2D((int)(dim_panel.getWidth()/2),(int)(dim_panel.getHeight()/2)));
                }                
            }
            else if( c == 45 ) {
                if(zoom_index-1 >= 0) {
                    zoom_index--;
                    setPixelSize(getResolution(zoom_index+1), new Vec2D((int)(dim_panel.getWidth()/2),(int)(dim_panel.getHeight()/2)));
                }
            }
            else {
                switch( arg0.getKeyCode() ) {
                    case KeyEvent.VK_LEFT : {
                        shiftViewLocation(new Vec2I(-pixel_step_x,0));
                        break;
                    }
                    case KeyEvent.VK_KP_LEFT : {
                        shiftViewLocation(new Vec2I(-pixel_step_x,0));
                        break;
                    }
                    case KeyEvent.VK_RIGHT : {
                        shiftViewLocation(new Vec2I(pixel_step_x,0));
                        break;
                    }
                    case KeyEvent.VK_KP_RIGHT : {
                        shiftViewLocation(new Vec2I(pixel_step_x,0));
                        break;
                    }
                    case KeyEvent.VK_UP : {
                        shiftViewLocation(new Vec2I(0,-pixel_step_y));
                        break;
                    }
                    case KeyEvent.VK_KP_UP : {
                        shiftViewLocation(new Vec2I(0,-pixel_step_y));
                        break;
                    }
                    case KeyEvent.VK_DOWN : {
                        shiftViewLocation(new Vec2I(0,pixel_step_y));
                        break;
                    }
                    case KeyEvent.VK_KP_DOWN : {
                        shiftViewLocation(new Vec2I(0,pixel_step_y));
                        break;
                    }
                    case KeyEvent.VK_HOME : {
                        centerView();
                        break;
                    }

                    default: break;
                }
            }
        }

        /***************************************************
         | NOTE: Below this comment are all the events that     |
         | we do not care about at all.                                         |
         ***************************************************/
        
        /**
         * An event this DataMapVisualizer does not care about.
         * @param me MouseEvent to process.
         */
        public void mouseClicked(MouseEvent e) {
//            System.out.println("~~~~~~~ " + image.image_view);
//            System.out.println("~~~~~~~ " + image.defaultSize);
        }       
        
        /**
         * An event this DataMapVisualizer does not care about.
         * @param e WindowEvent to process.
         */        
        public void windowActivated(WindowEvent e) { }

        /**
         * An event this DataMapVisualizer does not care about.
         * @param e WindowEvent to process.
         */                
        public void windowClosed(WindowEvent e) { }       
        
        /**
         * An event this DataMapVisualizer does not care about.
         * @param e ComponentEvent to process.
         */        
        public void windowDeactivated(WindowEvent e) { }
        
        /**
         * An event this DataMapVisualizer does not care about.
         * @param e ComponentEvent to process.
         */        
        public void windowDeiconified(WindowEvent e) { }
        
        /**
         * An event this DataMapVisualizer does not care about.
         * @param e ComponentEvent to process.
         */        
        public void windowIconified(WindowEvent e) { 
        }
        
        /**
         * An event this DataMapVisualizer does not care about.
         * @param e ComponentEvent to process.
         */        
        public void windowOpened(WindowEvent e) { }

        /**
         * An event this DataMapVisualizer does not care about.
         * @param e ComponentEvent to process.
         */        
        public void componentHidden(ComponentEvent e) {}
        
        /**
         * An event this DataMapVisualizer does not care about.
         * @param e ComponentEvent to process.
         */        
        public void componentMoved(ComponentEvent e) {}

        /**
         * An event this DataMapVisualizer does not care about.
         * @param e ComponentEvent to process.
         */        
        public void componentShown(ComponentEvent e) {}
  
        /**
         * An event this DataMapVisualizer does not care about.
         * @param arg0 KeyEvent to process.
         */
        public void keyReleased(KeyEvent arg0) { }
    }
    
} // end public class DataMapVisualizer extends JFrame implements MinimapFocusChangeListener, MouseInputListener, MouseWheelListener, ChangeListener, ActionListener
