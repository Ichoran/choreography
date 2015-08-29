/* HelpWindow.java - Generates a JFrame displaying useful program usage documentation.
 * Copyright 2010 Howard Hughes Medical Institute and Nicholas Andrew Swierczek
 * Also copyright 2015 Calico Life Sciences LLC (authored by Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt;

import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
  
public class HelpWindow extends JFrame implements ActionListener{
    private final int width = 600;
    private final int height = 400;
    private JEditorPane editorpane;
    
    public static final String default_text =
        "<html>" +
        "<body>" +
        "<LH>Keyboard commands" +
        "<dl>" +
        "<DT>1-9" +
        "<DD>Changes window focus to that numbered Minimap. Please note that it is possible to have more than 9 minimaps, however they will not be keyboard-focusable." +
        "<DT>Space" +
        "<DD>Changes window focus to main window." +
        "<DT>Up/Keypad Up [+shift]" +
        "<DD>For the main window and synchronized minimap<sup>*</sup>, causes the viewed location to move up one step. For unsynchronized minimaps, it causes the image" +
        " to move up one step, unless shift is held down, in which case the view location is moved." +
        "<DT>Up/Keypad Down [+shift]" +
        "<DD>For the main window and synchronized minimap<sup>*</sup>, causes the viewed location to move down one step. For unsynchronized minimaps, it causes the image" +
        " to move down one step, unless shift is held down, in which case the view location is moved." +
        "<DT>Up/Keypad Left [+shift]" +
        "<DD>For the main window and synchronized minimap<sup>*</sup>, causes the viewed location to move left one step. For unsynchronized minimaps, it causes the image" +
        " to move left one step, unless shift is held down, in which case the view location is moved." +
        "<DT>Up/Keypad Right [+shift]" +
        "<DD>For the main window and synchronized minimap<sup>*</sup>, causes the viewed location to move right one step. For unsynchronized minimaps, it causes the image" +
        " to move right one step, unless shift is held down, in which case the view location is moved." +
        "<DT>+" +
        "<DD>Increments scale in the window with focus." +
        "<DT>-" +
        "<DD>Decrements scale in the window with focus." + 
        "<DT>Home" +
        "<DD>Changes view of the window with focus so that a predefined point in data space is at the center pixel." + 
        "<dT>Ctrl + a" +
        "<DD>Opens a new minimap, regardless of which window has focus." +
        "<dT>Ctrl + q" +
        "<DD>Exits the program, regardless of which window has foucs." +
        "<dT>Alt + h" +
        "<DD>If the main window has focus, causes this help window to be displayed." +                    
        "</dl>" +
        "* synchronized minimaps are those which are slaved to the main window." +
        "</body>" +
        "</html>";

    public HelpWindow(String title,String text) {
        super(title);

        if (text==null) text = default_text;
        editorpane = new JEditorPane("text/html",text);

        editorpane.setEditable(false);
        editorpane.setCaretPosition(0);
        getContentPane().add(new JScrollPane(editorpane));
        addButtons();
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        calculateLocation();
        setVisible(true);
        
    }

    public void actionPerformed(ActionEvent e) {
        String strAction = e.getActionCommand();
        if (strAction.compareTo("Close") == 0 )  {
            processWindowEvent(new WindowEvent(this,
            WindowEvent.WINDOW_CLOSING));
        }
    }

    private void addButtons() {
        JButton btnclose = new JButton("Close");
        btnclose.addActionListener(this);
        
        JPanel panebuttons = new JPanel();
        panebuttons.add(btnclose);
        
        getContentPane().add(panebuttons, BorderLayout.SOUTH);
    }

    private void calculateLocation() {
        Dimension screendim = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(new Dimension(width, height));
        int locationx = (screendim.width - width) / 2;
        int locationy = (screendim.height - height) / 2;
        setLocation(locationx, locationy);
    }
}


