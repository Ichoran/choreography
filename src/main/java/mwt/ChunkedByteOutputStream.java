/* This file copyright 2008 Rex Kerr and the Howard Hughes Medical Institute
 * Distributed under the LGPL 2.1 (or GPL2.1 with classpath exception)
 *
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package mwt;


import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;


/**
 *
 * @author kerrr
 */
public class ChunkedByteOutputStream extends OutputStream {
  private static final int MIN_CHUNK_SIZE = 16;
  
  int chunk_size;
  int last_byte;
  ArrayList< byte[] > data;

  public ChunkedByteOutputStream(int csize) {
    if (csize<MIN_CHUNK_SIZE) chunk_size = MIN_CHUNK_SIZE;
    else chunk_size = csize;
    last_byte = 0;
    data = new ArrayList< byte[] >();
    data.add( new byte[chunk_size] );
  }

  @Override
  public void write(int b) throws IOException {
    if (last_byte>=chunk_size) {
      data.add( new byte[chunk_size] );
      last_byte=0;
    }
    data.get( data.size()-1 )[last_byte++] = (byte)(0xFF&b);
  }

  @Override
  public void write(byte[] b,int off,int len) {
    while (len>0) {
      if (len>=chunk_size-last_byte) {
        if (last_byte==0) {
          data.add( data.get(data.size()-1) );
          data.set( data.size()-2 , Arrays.copyOfRange(b,off,off+chunk_size) );
          len -= chunk_size;
        }
        else {
          while (last_byte < chunk_size) {
            data.get( data.size()-1 )[last_byte++] = b[off++];
            len--;
          }
          data.add( new byte[chunk_size] );
          last_byte = 0;
        }
      }
      else {
        while (len>0) {
          data.get( data.size()-1 )[last_byte++] = b[off++];
          len--;
        }
      }
    }
  }

  public int chunkCount() { return (last_byte==0) ? data.size()-1 : data.size(); }

  public byte[] getChunkBuffer(int index) { return data.get(index); }

  public int getChunkSize(int index) { return (index<data.size()-1) ? chunk_size : last_byte; }

  public byte[] getSizedChunkBuffer(int index) {
    return (index<data.size()-1) ? data.get(index) : Arrays.copyOfRange(data.get(index),0,last_byte);
  }
}
