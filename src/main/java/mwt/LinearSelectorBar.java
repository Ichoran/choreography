/* LinearSelectorBar.java - Custom widget used to control timeline in maps
 * Copyright 2010 Howard Hughes Medical Institute and Rex Kerr
 * Copyright 2015 Calico Life Sciences LLC (authored by Rex Kerr)
 * This file is a part of Choreography and is distributed under the
 * terms of the GNU Lesser General Public Licence version 2.1 (LGPL 2.1).
 * For details, see http://www.gnu.org/licences
 */

package mwt;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.text.*;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.event.*;
import java.beans.PropertyChangeEvent;

import mwt.numerics.*;
import mwt.plugins.*;

public class LinearSelectorBar extends Box
{
  public static class NumberListModel extends SpinnerNumberModel
  {
    RealArray basis;
    NumberListModel limitBelow;
    NumberListModel limitAbove;
    
    // Constructors and helper methods
    public NumberListModel(double min,double max,double step)
    {
      super(min,min,max,step);
      basis = null;
      limitBelow = null;
      limitAbove = null;
    }
    public NumberListModel(float[] series)
    {
      super(0.0,0.0,1.0,1.0);
      basis = null;
      if (series==null || series.length<2) return;
      basis = new RealArray(series);
      initSuperFromBasis();
    }
    public NumberListModel(double[] series)
    {
      super(0.0,0.0,1.0,1.0);
      basis = null;
      if (series==null || series.length<2) return;
      basis = new RealArray(series);
      initSuperFromBasis();
    }
    public NumberListModel(RealArray series)
    {
      super(0.0,0.0,1.0,1.0);
      basis = null;
      if (series==null || series.length()<2) return;
      basis = series;
      initSuperFromBasis();
    }
    void initSuperFromBasis()
    {
      super.setMinimum( basis.getSmart(0) );
      super.setMaximum( basis.getSmart(-1) );
      super.setValue( basis.getSmart(0) );
      super.setStepSize( (basis.getSmart(-1)-basis.getSmart(0))/(basis.length()-1) );
      limitBelow = null;
      limitAbove = null;
    }
    public void linkLowerLimit(NumberListModel nlm) { limitBelow = nlm; }
    public void linkUpperLimit(NumberListModel nlm) { limitAbove = nlm; }
    
    // Methods that fix up SpinnerNumberModel
    @Override public Object getNextValue()
    {
      if (basis==null) return super.getNextValue();
      return new Double(constrain( getShifted(getNumber().doubleValue(),1) ));
    }
    @Override public Object getPreviousValue()
    {
      if (basis==null) return super.getPreviousValue();
      return new Double(constrain( getShifted(getNumber().doubleValue(),-1) ));
    }
    @Override public void setValue(Object value)
    {
      Number n = (Number)value;
      Double d = new Double(constrain( getClosest(n.doubleValue()) ));
      super.setValue(d);
    }
    
    // Methods that keep the values to what they should be
    public double constrain(double value)
    {
      if (limitBelow!=null)
      {
        double lo = limitBelow.getNumber().doubleValue();
        if (value < lo) value = lo;
      }
      if (limitAbove!=null)
      {
        double hi = limitAbove.getNumber().doubleValue();
        if (value > hi) value = hi;
      }
      return value;
    }
    public double getShifted(double entry,int shift)
    {
      if (basis==null) return entry;
      int i = basis.binarySearch(entry);
      if (i<0)
      {
        i = -1-i;
        if (i>=basis.length()) i = basis.length()-1;
        if (i>0)
        {
          if ( Math.abs(basis.getD(i)-entry) > Math.abs(basis.getD(i-1)-entry) ) i--;
        }
      }
      i += shift;
      if (i<0) i=0;
      else if (i>=basis.length()) i = basis.length()-1;
      return basis.getD(i);
    }
    public double getClosest(double entry) { return getShifted(entry,0); }
  }
  
  
  public static class RangeSpinner extends JPanel implements ChangeListener
  {
    RealArray basis;
    
    JSpinner left;
    JSpinner center;
    JSpinner right;
    
    AbstractSet< ChangeListener > listeners;
    
    public RangeSpinner(RealArray range,String L,String C,String R)
    {
      super();
      if (range==null || range.length()<2)
      {
        double[] d_range = new double[2];
        d_range[0] = 0.0;
        d_range[1] = 1.0;
        basis = new RealArray(d_range);
      }
      else basis = range;
      
      NumberListModel left_model = new NumberListModel(basis);
      NumberListModel center_model = new NumberListModel(basis);
      NumberListModel right_model = new NumberListModel(basis);
      center_model.linkLowerLimit(left_model);
      center_model.linkUpperLimit(right_model);
      left_model.linkUpperLimit(right_model);
      right_model.linkLowerLimit(left_model);
      
      left = new JSpinner( left_model );
      center = new JSpinner( center_model );
      right = new JSpinner( right_model );
      right.setValue( basis.getSmart(-1) );
      
      // Note, labels are stacked left=top and right=bottom; names refer to the position along the basis
      JLabel left_label = new JLabel(L);
      JLabel center_label = new JLabel(C);
      JLabel right_label = new JLabel(R);
      left.setMaximumSize(left.getPreferredSize());
      center.setMaximumSize(center.getPreferredSize());
      right.setMaximumSize(right.getPreferredSize());
      GroupLayout layout = new GroupLayout(this);
      setLayout(layout);
      
      GroupLayout.SequentialGroup horz = layout.createSequentialGroup();
      horz.addGroup( layout.createParallelGroup().addComponent(left).addComponent(center).addComponent(right) );
      horz.addGroup( layout.createParallelGroup().addComponent(left_label).addComponent(center_label).addComponent(right_label) );
      layout.setHorizontalGroup(horz);
      
      GroupLayout.SequentialGroup vert = layout.createSequentialGroup();
      vert.addGroup( layout.createParallelGroup().addComponent(left).addComponent(left_label) );
      vert.addGroup( layout.createParallelGroup().addComponent(center).addComponent(center_label) );
      vert.addGroup( layout.createParallelGroup().addComponent(right).addComponent(right_label) );
      layout.setVerticalGroup(vert);
      
      left.addChangeListener(this);
      right.addChangeListener(this);
      center.addChangeListener(this);
      
      listeners = new LinkedHashSet< ChangeListener >();
    }
    
    public void addChangeListener(ChangeListener cl) { listeners.add(cl); }
    public ChangeListener[] getChangeListeners() { return listeners.toArray( new ChangeListener[ listeners.size() ] ); }
    public void removeChangeListener(ChangeListener cl) { listeners.remove(cl); }
    protected void fireStateChanged() {
      ChangeEvent ce = new ChangeEvent(this);
      for (ChangeListener cl : listeners) { cl.stateChanged(ce); }
    }
    
    public void stateChanged(ChangeEvent e) {
      try {
        if (e.getSource() == left) {
          if ( ((Double)center.getValue()).doubleValue() < ((Double)left.getValue()).doubleValue() ) {
            center.setValue( left.getValue() );
            center.commitEdit();
          }
        }
        else if (e.getSource() == right) {
          if ( ((Double)center.getValue()).doubleValue() > ((Double)right.getValue()).doubleValue() ) {
            center.setValue( right.getValue() );
            center.commitEdit();
          }
        }
        
        Object o = e.getSource();
        if (o==left || o==right || o==center) {
          fireStateChanged();
        }
      }
      catch (ParseException pe) {}  // Don't worry about it if there's something wrong with parsing--can't sensibly update.
    }
    
    public double leftValue() { return ((Double)left.getValue()).doubleValue(); }
    public double rightValue() { return ((Double)right.getValue()).doubleValue(); }
    public double centerValue() { return ((Double)center.getValue()).doubleValue(); }
    public Vec2D rangeValue() { return new Vec2D( leftValue() , rightValue() ); }
    public void setLeft(double d) {
      if (d>=basis.getSmart(0) && d<=basis.getSmart(-1) && basis.binaryClosest(d)!=basis.binaryClosest(leftValue())) {
        try {
          left.setValue(d); left.commitEdit();
          if (rightValue()<leftValue()) { right.setValue(leftValue()); right.commitEdit(); }
          if (centerValue()<leftValue()) { center.setValue(leftValue()); center.commitEdit(); }
        }
        catch (ParseException pe) {}
        fireStateChanged();
      }
    }
    public void setRight(double d) {
      if (d>=basis.getSmart(0) && d<=basis.getSmart(-1) && basis.binaryClosest(d)!=basis.binaryClosest(rightValue())) {
        try {
          right.setValue(d); right.commitEdit();
          if (leftValue()>rightValue()) { left.setValue(rightValue()); left.commitEdit(); }
          if (centerValue()>rightValue()) { center.setValue(rightValue()); center.commitEdit(); }
        }
        catch (ParseException pe) {}
        fireStateChanged();
      }
    }
    public void setCenter(double d) {
      if (d>=basis.getSmart(0) && d<=basis.getSmart(-1) && basis.binaryClosest(d)!=basis.binaryClosest(centerValue())) {
        if (d>=leftValue() && d<=rightValue()) {
          try { center.setValue(d); center.commitEdit(); }
          catch (ParseException pe) {}
          fireStateChanged();
        }
      }
    }
  }
  
  public static class RangeSlider extends JPanel implements ChangeListener,MouseListener,MouseMotionListener
  {
    public static final int PAD = 2;
    public static final double PAD_FRAC = 0.5;
    public enum ZoomTarget { LEFT,RIGHT,CENTER,RANGE };
    public enum Dragging { NONE,TOP,BOTTOM };
    
    Cursor normal;
    Cursor left;
    Cursor right;
    Cursor shift;
    Cursor move;
    
    AtomicBoolean paint_waiting;
    RangeSpinner spinner;
    double min_delta;
    double max_spin;
    int required_digits;
    String label_format_string;
    
    ZoomTarget who_zoom;
    ZoomTarget who_drag;
    Dragging drag_state;
    Vec2I drag_origin;
    Vec2I dragged_to;
    
    Color bar_body;
    Color bar_border;
    Color bar_select;
    Color bar_select_move;
    Color bar_range;
    Color bar_range_end;
    Color bar_range_move;
    Color zoom_body;
    Color zoom_edge;
    
    Font label_font;
    
    double rL;
    double rR;
    double rC;
    int irL;
    int irR;
    int irC;
    int zrL;
    int zrR;
    int zrC;
    int zF;
    
    int last_W;
    int h0;
    int h1;
    int h2;
    int h3;
    int h4;
    
    Path2D pL;
    Path2D pR;
    Path2D pC;
    Path2D qL;
    Path2D qR;
    Path2D qC;
    
    public RangeSlider(RangeSpinner rs)
    {
      super();
      
      paint_waiting = null;
      spinner = rs;
      max_spin = Math.max( Math.abs(rs.basis.getSmart(0)) , Math.abs(rs.basis.getSmart(-1)) );
      min_delta = 0;
      double delta;
      for (int i=1 ; i<rs.basis.length() ; i++) {
        delta = rs.basis.getD(i) - rs.basis.getD(i-1);
        if (min_delta==0) min_delta = delta;
        else if (delta!=0 && delta<min_delta) min_delta = delta;
      }
      required_digits = Math.max(1 , 1+(int)Math.floor(Math.log10(max_spin)));
      if (min_delta>0.0 && min_delta<1.0) {
        int j = 1 + Math.max(1 , -(int)Math.floor(Math.log10(min_delta)));
        required_digits += j;
        label_format_string = String.format("%%.%df",j);
      }
      else label_format_string = "%f";
      if (rs.basis.getSmart(0)<0 || rs.basis.getSmart(-1)<0) required_digits++;
      required_digits += PAD + (int)Math.floor(required_digits*PAD_FRAC);
      
      normal = new Cursor( Cursor.DEFAULT_CURSOR );
      left = normal;
      right = normal;
      shift = normal;
      move = normal;
      setMinimumSize( new Dimension(7,18) );
      
      bar_body = new Color(192,192,255);
      bar_border = Color.BLACK;
      bar_select = Color.WHITE;
      bar_select_move = new Color(1.0f,1.0f,1.0f,0.4f);
      bar_range = new Color(128,128,255);
      bar_range_end = Color.BLUE;
      bar_range_move = new Color(0.0f,0.0f,1.0f,0.4f);
      zoom_body = new Color(208,208,208);
      zoom_edge = new Color(144,144,144);
      
      label_font = new Font(Font.MONOSPACED,Font.PLAIN,10);
      
      pL = pR = pC = null;
      qL = qR = qC = null;
      who_zoom = ZoomTarget.CENTER;
      who_drag = ZoomTarget.CENTER;
      drag_state = Dragging.NONE;
      drag_origin = Vec2I.zero();
      dragged_to = Vec2I.zero();
      
      setFromSpinner(7,18);
      h0 = h1 = h2 = h3 = h4 = 0;
      
      spinner.addChangeListener(this);
      addMouseListener(this);
      addMouseMotionListener(this);
    }
    
    public void stateChanged(ChangeEvent e) {
      Object o = e.getSource();
      if (o==spinner) repaint();
    }
    
    public void mouseClicked(MouseEvent me) {
      if (me.getButton()==MouseEvent.BUTTON3) {
        if (getCursor()==right) who_zoom = ZoomTarget.RIGHT;
        else if (getCursor()==left) who_zoom = ZoomTarget.LEFT;
        else if (getCursor()==move) who_zoom = ZoomTarget.CENTER;
        else return;
        repaint();
      }
    }
    public void mouseEntered(MouseEvent me) {}
    public void mouseExited(MouseEvent me) {}
    public void mousePressed(MouseEvent me) {
      if (getCursor()==normal) return;
      if (me.getButton()!=MouseEvent.BUTTON1) return;
      
      Vec2I m = new Vec2I(me.getX() , me.getY());
      if ( Math.abs(m.y-(h0+h1)/2) < Math.abs(m.y-(h2+h3)/2) ) drag_state = Dragging.TOP;
      else drag_state = Dragging.BOTTOM;
      drag_origin.eq(m);
      dragged_to.eq(m);
      
      if (getCursor()==move) {
        who_drag = ZoomTarget.CENTER;
        drag_origin.x = (drag_state==Dragging.TOP) ? irC : zrC;
      }
      else if (getCursor()==left) {
        who_drag = ZoomTarget.LEFT;
        drag_origin.x = (drag_state==Dragging.TOP) ? irL : zrL;
      }
      else if (getCursor()==right) {
        who_drag = ZoomTarget.RIGHT;
        drag_origin.x = (drag_state==Dragging.TOP) ? irR : zrR;
      }
      else if (getCursor()==shift) who_drag = ZoomTarget.RANGE;
      else return;
      
      repaint();
    }
    public void mouseReleased(MouseEvent me) {
      if (drag_state==Dragging.NONE) return;
      
      Vec2I m = new Vec2I(me.getX() , me.getY());
      if (m.x==drag_origin.x) {
        drag_state = Dragging.NONE;
        repaint();
        return;
      }
      
      double value = (drag_state==Dragging.TOP) ? pxToValue(last_W-2,m.x-1) : pxzToValue(last_W-2,m.x-1);
      if (who_drag==ZoomTarget.CENTER) spinner.center.setValue( new Double(value) );
      else if (who_drag==ZoomTarget.LEFT) spinner.left.setValue( new Double(value) );
      else if (who_drag==ZoomTarget.RIGHT) spinner.right.setValue( new Double(value) );
      else {
        double old_value = (drag_state==Dragging.TOP) ? pxToValue(last_W-2,drag_origin.x-1) : pxzToValue(last_W-2,drag_origin.x-1);
        double delta = value - old_value;
        if (delta>0) {
          spinner.right.setValue(new Double( ((Double)spinner.right.getValue()).doubleValue() + delta ));
          spinner.center.setValue(new Double( ((Double)spinner.center.getValue()).doubleValue() + delta ));
          spinner.left.setValue(new Double( ((Double)spinner.left.getValue()).doubleValue() + delta ));
        }
        else {
          spinner.left.setValue(new Double( ((Double)spinner.left.getValue()).doubleValue() + delta ));
          spinner.center.setValue(new Double( ((Double)spinner.center.getValue()).doubleValue() + delta ));
          spinner.right.setValue(new Double( ((Double)spinner.right.getValue()).doubleValue() + delta ));
        }
      }
      
      drag_state = Dragging.NONE;
      repaint();
    }
    
    public void mouseDragged(MouseEvent me) {
      if (drag_state != Dragging.NONE) {
        Vec2I m = new Vec2I( me.getX() , me.getY() );
        if (!dragged_to.isSame(m))
        {
          dragged_to.eq(m);
          repaint();
        }
      }
    }
    public void mouseMoved(MouseEvent me) {
      Vec2I m = new Vec2I(me.getX() , me.getY());
      if (m.y>h0 && m.y<h1-2 && Math.abs(m.x-irC)<3) setCursor(move);
      else if (m.y>h2 && m.y<h3-2 && Math.abs(m.x-zrC)<3) setCursor(move);
      else if (m.y>h0 && m.y<h1-2 && irL-m.x>=3 && irL-m.x<7) setCursor(left);
      else if (m.y>h2 && m.y<h3-2 && zrL-m.x>=3 && zrL-m.x<7) setCursor(left);
      else if (m.y>h0 && m.y<h1-2 && m.x-irR>=3 && m.x-irR<7) setCursor(right);
      else if (m.y>h2 && m.y<h3-2 && m.x-zrR>=3 && m.x-zrR<7) setCursor(right);
      else if (m.y>h0 && m.y<h1-2 && Math.abs(m.x-irL)<4) setCursor(left);
      else if (m.y>h2 && m.y<h3-2 && Math.abs(m.x-zrL)<4) setCursor(left);
      else if (m.y>h0 && m.y<h1-2 && Math.abs(m.x-irR)<4) setCursor(right);
      else if (m.y>h2 && m.y<h3-2 && Math.abs(m.x-zrR)<4) setCursor(right);
      else if (m.y>=h0 && m.y<=h1+2 && irL<=m.x && m.x<=irR) setCursor(shift);
      else if (m.y>=h2 && m.y<=h3+2 && zrL<=m.x && m.x<=zrR) setCursor(shift);
      else if (getCursor()!=normal) setCursor(normal);
    }
    
    
    void setFromSpinner(int W,int H) {
      rL = ((Double)spinner.left.getValue()).doubleValue();
      rR = ((Double)spinner.right.getValue()).doubleValue();
      rC = ((Double)spinner.center.getValue()).doubleValue();
      
      irL = valueToPx(W,rL);
      irR = valueToPx(W,rR);
      irC = valueToPx(W,rC);
      
      zF = (int)Math.ceil(Math.max(1,Math.sqrt(W)));
      zrL = valueToPxz(W,rL);
      zrR = valueToPxz(W,rR);
      zrC = valueToPxz(W,rC);
    }
    
    double pxToValue(int W,int x) {
      double frac = (W<1) ? 0.0 : x/(double)(W-1);
      return spinner.basis.getSmart(0) + frac*(spinner.basis.getSmart(-1)-spinner.basis.getSmart(0));
    }
    
    double pxzToValue(int W,int x) {
      double frac = (W<1) ? 0.0 : (x-W/2)/(double)(W-1);
      frac /= zF;
      double r = (who_zoom==ZoomTarget.CENTER) ? rC : ((who_zoom==ZoomTarget.LEFT) ? rL : rR);
      return r + frac*(spinner.basis.getSmart(-1)-spinner.basis.getSmart(0));
    }
    
    int valueToPx(int W,double v) {
      double frac = (v - spinner.basis.getSmart(0))/(spinner.basis.getSmart(-1)-spinner.basis.getSmart(0));
      return (int)Math.round(frac*(W-1)); 
    }
    
    int valueToPxz(int W,double v) {
      double r = (who_zoom==ZoomTarget.CENTER) ? rC : ((who_zoom==ZoomTarget.LEFT) ? rL : rR);
      double frac = (v - r)/(spinner.basis.getSmart(-1)-spinner.basis.getSmart(0));
      return W/2 + (int)Math.round((W-1)*frac*zF);
    }
    
    public void setNormalCursor(Cursor c) { normal = c; }
    public void setLeftCursor(Cursor c) { left = c; }
    public void setRightCursor(Cursor c) { right = c; }
    public void setShiftCursor(Cursor c) { shift = c; }
    public void setMoveCursor(Cursor c) { move = c; }
    
    public void paintComponent(Graphics g) {
      super.paintComponent(g);
      
      Graphics2D g2 = (Graphics2D)g;
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
      g.setFont(label_font);
      FontMetrics fm = g.getFontMetrics();
      int W = getWidth();
      int H = getHeight();
      int R = fm.getHeight() + 2;
      
      if (W<4) W=4;
      if (H<16) H=16;
      last_W = W;
      h0 = 0;
      h2 = H/2;
      h4 = H;
      h1 = Math.max(4 , h2 - R);
      h3 = h1+h2;
      
      setFromSpinner(W-2,H);
      irL++; irR++; irC++;
      zrL++; zrR++; zrC++;
      
      // Note that +1 is used with lower & right points since fillPolygon hits fills borders only on the upper and left sides.
      g.setColor(bar_border); g.drawRect(0,0,W-1,h1);
      g.setColor(bar_body); g.fillRect(1,1,W-2,h1-1);
      g.setColor(bar_range); g.fillRect(irL,1,irR-irL,h1-1);
      g.setColor(bar_range_end);
      pL = new Path2D.Float();
      pL.moveTo(irL,h1-1); pL.lineTo(irL,1); pL.lineTo(irL+4,1); pL.lineTo(irL,Math.min(5,h1)); g2.fill(pL); g2.draw(pL);
      pR = new Path2D.Float();
      pR.moveTo(irR,h1-1); pR.lineTo(irR,1); pR.lineTo(irR-4,1); pR.lineTo(irR,Math.min(5,h1)); g2.fill(pR); g2.draw(pR);
      //g.drawLine(irL,h1-1,irR,h1-1); g.drawLine(irL,h1-2,irR,h1-2);
      g.setColor(bar_select);
      pC = new Path2D.Float();
      pC.moveTo(irC,h1-3); pC.lineTo(irC,Math.min(4,h1)); pC.lineTo(irC-3,1); pC.lineTo(irC+3,1); pC.lineTo(irC,Math.min(4,h1));
      g2.fill(pC); g2.draw(pC);
      
      Path2D p = new Path2D.Float();
      int around = (who_zoom==ZoomTarget.CENTER) ? irC : ((who_zoom==ZoomTarget.LEFT) ? irL : irR);
      p.moveTo(around-zF/2,h1+1); p.lineTo(1,h2); p.lineTo(W-1,h2); p.lineTo(around+zF/2,h1+1);
      g.setColor(zoom_body);
      g2.fill(p);
      g.setColor(zoom_edge);
      g2.draw(p);
      g.drawLine(around-zF/2,h0,around-zF/2,h1); g.drawLine(around+zF/2,h0,around+zF/2,h1);
      
      g.setColor(bar_border); g.drawRect(0,h2,W-1,h1);
      g.setColor(bar_body); g.fillRect(1,h2+1,W-2,h1-1);
      if (zrR < 1 || zrL > W-2) {}  // Bar out of view; should never happen as originally written
      else {
        g.setColor(bar_range);
        g.fillRect( Math.max(1,zrL) , h2+1 , Math.min(W-2,zrR)-Math.max(1,zrL) , h1-1 );
        if (zrL >= 1 && zrL <= W-2) {
          g.setColor(bar_range_end);
          qL = new Path2D.Float();
          qL.moveTo(zrL,h3-1); qL.lineTo(zrL,h2+1); qL.lineTo(zrL+4,h2+1); qL.lineTo(zrL,Math.min(h2+5,h3)); g2.fill(qL); g2.draw(qL);
        } else qL = null;
        if (zrR >=1 && zrR <= W-2) {
          g.setColor(bar_range_end);
          qR = new Path2D.Float();
          qR.moveTo(zrR,h3-1); qR.lineTo(zrR,h2+1); qR.lineTo(zrR-4,h2+1); qR.lineTo(zrR , Math.min(h2+5,h3)); g2.fill(qR); g2.draw(qR);
        } else qR = null;
        if (zrC >= 1 && zrC <= W-2) {
          g.setColor(bar_select);
          qC = new Path2D.Float();
          qC.moveTo(zrC,h3-3); qC.lineTo(zrC,Math.min(4+h2,h3)); qC.lineTo(zrC-3,h2+1); qC.lineTo(zrC+3,h2+1); qC.lineTo(zrC,Math.min(4+h2,h3));
          g2.fill(qC); g2.draw(qC);
        } else qC = null;
      }
      
      g.setColor(Color.BLACK);
      int max_label_width = fm.charWidth('M')*required_digits;
      int n_labels = W / max_label_width;
      int last_index = -1;
      int index;
      double fraction;
      for (int i=0 ; i<n_labels ; i++) {
        index = spinner.basis.binaryClosest(
          spinner.basis.getSmart(0) + (spinner.basis.getSmart(-1)-spinner.basis.getSmart(0))*i/((double)(n_labels-1))
        );
        if (index==last_index) continue;
        int x = 1 + valueToPx(W-2,spinner.basis.getSmart(index));
        g.drawLine(x,h1-2,x,h1+2);
        String label = String.format(label_format_string,spinner.basis.getSmart(index));
        int label_width = fm.stringWidth(label);
        x -= label_width/2;
        if (x + label_width >= W) x = W-label_width-1;
        if (x<0) x = 0;
        g.drawString(label,x,h2-3);
        last_index = index;
      }
    
      last_index = -1;
      int root_index = spinner.basis.binaryClosest(
        (who_zoom==ZoomTarget.CENTER)  ?
        ((Double)spinner.center.getValue()).doubleValue() :
        ( (who_zoom==ZoomTarget.LEFT) ? ((Double)spinner.left.getValue()).doubleValue() : ((Double)spinner.right.getValue()).doubleValue() )
      );
      if (root_index<0) root_index = -root_index-1;
      if (root_index>=spinner.basis.length()) index = spinner.basis.length()-1;
      for (int i=0 ; i<n_labels ; i++) {
        index = spinner.basis.binaryClosest(
          (i<=n_labels/2) ?
          spinner.basis.getD(root_index) + i*(spinner.basis.getSmart(-1)-spinner.basis.getSmart(0))/(n_labels*(double)zF) :
          spinner.basis.getD(root_index) - (n_labels-i)*(spinner.basis.getSmart(-1)-spinner.basis.getSmart(0))/(n_labels*(double)zF)
        );
        if (index==last_index || (index==root_index && i!=0)) continue;
        if (last_index>=0 && Math.abs(valueToPxz(W-2,spinner.basis.getD(index))-valueToPxz(W-2,spinner.basis.getD(last_index))) < (2*max_label_width)/3) continue;
        if (i!=0 && Math.abs(valueToPxz(W-2,spinner.basis.getD(index))-valueToPxz(W-2,spinner.basis.getD(root_index))) < (2*max_label_width)/3) continue;
        int x = 1+valueToPxz(W-2 , spinner.basis.getSmart(index));
        g.drawLine(x,h3-2,x,h3+2);
        String label = String.format(label_format_string,spinner.basis.getSmart(index));
        int label_width = fm.stringWidth(label);
        x -= label_width/2;
        if (x+label_width >= W) x = W-label_width-1;
        if (x<0) x = 0;
        g.drawString(label,x,h4-3);
        last_index=index;
      }
      
      if (drag_state!=Dragging.NONE) {
        int dx = dragged_to.x - drag_origin.x;
        int dxz = dx;
        if (drag_state==Dragging.TOP) dxz *= zF;
        else dx = (int)Math.round(dxz/(double)zF);

        if (who_drag==ZoomTarget.CENTER || who_drag==ZoomTarget.RANGE) {
          g.setColor(bar_select_move);
          if (dxz>0) {
            g.fillRect(irC,h0,dx,h2-1-h0);
            g.fillRect(zrC,h2,dxz,h4-1-h2);
          } else {
            g.fillRect(irC+dx,h0,-dx,h2-1-h0);
            g.fillRect(zrC+dxz,h2,-dxz,h4-1-h2);
          }
          p = new Path2D.Float();
          p.moveTo(irC,h0); p.lineTo(irC,h2-1); p.moveTo(irC+dx,h2-1); p.lineTo(irC+dx,h0);
          p.moveTo(zrC,h2); p.lineTo(zrC,h4-1); p.moveTo(zrC+dxz,h4-1); p.lineTo(zrC+dxz,h2);
          g.setColor(bar_select); g2.draw(p);
        }
        if (who_drag==ZoomTarget.LEFT || who_drag==ZoomTarget.RANGE) {
          g.setColor(bar_range_move);
          if (dxz>0) {
            g.fillRect(irL,h0,dx,h2-1-h0);
            g.fillRect(zrL,h2,dxz,h4-1-h2);
          } else {
            g.fillRect(irL+dx,h0,-dx,h2-1-h0);
            g.fillRect(zrL+dxz,h2,-dxz,h4-1-h2);
          }
          p = new Path2D.Float();
          p.moveTo(irL,h0); p.lineTo(irL,h2-1); p.moveTo(irL+dx,h2-1); p.lineTo(irL+dx,h0);
          p.moveTo(zrL,h2); p.lineTo(zrL,h4-1); p.moveTo(zrL+dxz,h4-1); p.lineTo(zrL+dxz,h2);
          g.setColor(bar_range_end); g2.draw(p);
        }
        if (who_drag==ZoomTarget.RIGHT || who_drag==ZoomTarget.RANGE) {
          g.setColor(bar_range_move);
          if (dxz>0) {
            g.fillRect(irR,h0,dx,h2-1-h0);
            g.fillRect(zrR,h2,dxz,h4-1-h2);
          } else {
            g.fillRect(irR+dx,h0,-dx,h2-1-h0);
            g.fillRect(zrR+dxz,h2,-dxz,h4-1-h0);
          }
          p = new Path2D.Float();
          p.moveTo(irR,h0); p.lineTo(irR,h2-1); p.moveTo(irR+dx,h2-1); p.lineTo(irR+dx,h0);
          p.moveTo(zrR,h2); p.lineTo(zrR,h4-1); p.moveTo(zrR+dxz,h4-1); p.lineTo(zrR+dxz,h2);
          g.setColor(bar_range_end); g2.draw(p);
        }
      }
      
      if (paint_waiting!=null) paint_waiting.set(false);
    }
  }
  
  
  public Cursor leftBound;
  public Cursor rightBound;
  public Cursor moveRange;
  public Cursor movePoint;
  
  RangeSpinner spin;
  RangeSlider slide;
  
  public LinearSelectorBar(RealArray range)
  {
    super(BoxLayout.X_AXIS);
    spin = new RangeSpinner(range," start"," now"," end");
    slide = new RangeSlider(spin);
    Toolkit tk = Toolkit.getDefaultToolkit();
    cursorCreator(tk);
    slide.setLeftCursor(leftBound);
    slide.setRightCursor(rightBound);
    slide.setShiftCursor(moveRange);
    slide.setMoveCursor(movePoint);
    add(slide);
    add(spin);
  }
  void cursorCreator(Toolkit tk)
  {
    leftBound = CursorTools.makeSafeSensibleCursor( this.getClass().getResource("CursorLeftBound.png") , new Vec2I(2,11) , "Left Bound" );
    rightBound = CursorTools.makeSafeSensibleCursor( this.getClass().getResource("CursorRightBound.png") , new Vec2I(10,11) , "Right Bound" );
    moveRange = CursorTools.makeSafeSensibleCursor( this.getClass().getResource("CursorSlideLeftRight.png") , new Vec2I(11,8) , "Move Bounds" );
    movePoint = CursorTools.makeSafeSensibleCursor( this.getClass().getResource("CursorPointLeftRight.png") , new Vec2I(10,11) , "Move Key Point" );
  }
  public void setRepaintWaiter(AtomicBoolean ab) { if (slide!=null) slide.paint_waiting = ab; }
  public RangeSpinner getTriSpinner() { return spin; }
  
  public static void main(String args[])
  {
    SwingUtilities.invokeLater(
      new Runnable()
      {
        public void run()
        {
          double d[] = new double[10];
          for (int i=0;i<d.length;i++) d[i] = i/(double)d.length + i*i*1.1/((double)d.length*d.length);
          double dd[] = new double[100];
          for (int i=0;i<dd.length;i++) dd[i] = i/(double)dd.length + i*i*1.1/((double)dd.length*dd.length);
          double ddd[] = new double[100000];
          for (int i=0;i<ddd.length;i++) ddd[i] = 3000*( i/(double)ddd.length + i*(i*1.1)/(((double)ddd.length)*ddd.length) );
          double dddd[] = {0.007,0.043,0.076,0.109,760.67};
          LinearSelectorBar lsb = new LinearSelectorBar(new RealArray(dddd));
          JFrame frame = new JFrame("Linear Selector Bar Demo");
          frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
          frame.getContentPane().add(lsb);
          frame.pack();
          frame.setLocation(64,64);
          frame.setVisible(true);
        }
      }
    );    
  }
}

