/** This file copyright 2008 Nicholas Swierczek and the Howard Hughes Medical Institute.
  * Also copyright 2015 Calico Life Sciences LLC (authored by Rex Kerr)
  * Distributed under the LGPL2.1 license (or GPL2.1 with classpath exception)
  */

package mwt;

import java.io.*;
import java.awt.*;
import java.awt.image.*;
import java.net.*;
import javax.imageio.*;
import mwt.numerics.*;

public class CursorTools
{
  public static final int MAX_CURSOR_SIZE = 256;
  
  // Reads a cursor image from a file, making sure it's ARGB and everything.
  public static BufferedImage readCursorImage(URL u) throws IOException
  {
    BufferedImage bi = ImageIO.read(u);
    if (bi.getWidth() > MAX_CURSOR_SIZE || bi.getHeight() > MAX_CURSOR_SIZE) return null;
    ColorModel cm = bi.getColorModel();
    ColorModel srgb = ColorModel.getRGBdefault();
    
    // See if color model is close enough for us to use; if not, copy into ARGB image instead
    if (cm.hasAlpha()!=srgb.hasAlpha() || cm.getNumColorComponents()!=srgb.getNumColorComponents() || cm.getPixelSize()!=srgb.getPixelSize())
    {
      BufferedImage with_alpha = new BufferedImage(bi.getWidth(),bi.getHeight(),BufferedImage.TYPE_INT_ARGB);
      for (int y=0 ; y<bi.getHeight() ; y++)
      {
        for (int x=0 ; x<bi.getWidth() ; x++)
        {
          with_alpha.setRGB( x , y , bi.getRGB(x,y) );
        }
      }
      bi = with_alpha;
    }
    
    return bi;
  }
  
  public static BufferedImage readCursorImage(String s) throws IOException,MalformedURLException
  {
    return readCursorImage( new URL(s) );
  }
  
  
  public static BufferedImage sizeCursorForWindowManager(BufferedImage cursor,Vec2I hotspot,Toolkit tk)
  {
    Vec2I actual = new Vec2I(cursor.getWidth(),cursor.getHeight());
    Vec2I best = Vec.toI( tk.getBestCursorSize( actual.x , actual.y ) );
    if (best.isSame(actual)) return cursor;
    
    Vec2D hot = hotspot.toD();
    hot.eqTimes( best.toD() ).eqDivide( actual.toD() );
    Vec2I shift = hot.toI().eqMinus(hotspot);
    
    BufferedImage resized = new BufferedImage( best.x , best.y , BufferedImage.TYPE_INT_ARGB );
    // Do pixel-by-pixel copying to make sure we get the right transparency in new regions
    Vec2I i_re = new Vec2I();
    Vec2I i_cu = new Vec2I();
    for (i_re.y = 0 ; i_re.y<best.y ; i_re.y++)
    {
      for (i_re.x = 0 ; i_re.x<best.x ; i_re.x++)
      {
        i_cu.eq(i_re).eqMinus(shift);
        if (i_cu.x<0 || i_cu.y<0 || i_cu.x >= actual.x || i_cu.y >= actual.y) resized.setRGB(i_re.x,i_re.y,0);
        else resized.setRGB(i_re.x,i_re.y,cursor.getRGB(i_cu.x,i_cu.y));
      }
    }
    hotspot.eq(hot); // Hotspot now matches new image
    return resized;
  }

  
  public static Cursor makeSensibleCursor(URL u,Vec2I hotspot,String cursor_name) throws IOException
  {
    if (hotspot==null) hotspot = Vec2I.zero();
    Toolkit tk = Toolkit.getDefaultToolkit();
    BufferedImage bi = sizeCursorForWindowManager( readCursorImage(u) , hotspot , tk );
    return tk.createCustomCursor( bi , Vec.toPt( hotspot ) , cursor_name );
  }
  
  public static Cursor makeSensibleCursor(String s,Vec2I hotspot,String cursor_name) throws IOException,MalformedURLException
  {
    return makeSensibleCursor(new URL(s) , hotspot , cursor_name);
  }
  
  
  public static Cursor makeSafeSensibleCursor(URL u,Vec2I hotspot,String cursor_name)
  {
    Cursor c;
    try { c = makeSensibleCursor(u,hotspot,cursor_name); }
    catch (IOException ioe) { c = new Cursor(Cursor.DEFAULT_CURSOR); }
    return c;
  }
  
  public static Cursor makeSafeSensibleCursor(String s,Vec2I hotspot,String cursor_name)
  {
    Cursor c;
    try { c = makeSafeSensibleCursor(new URL(s),hotspot,cursor_name); }
    catch (MalformedURLException mue) { c = new Cursor(Cursor.DEFAULT_CURSOR); }
    return c;
  }
}
