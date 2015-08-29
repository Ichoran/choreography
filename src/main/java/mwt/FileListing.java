/** This file copyright 2008 Nicholas Swierczek and the Howard Hughes Medical Institute.
  * Also copyright 2015 Calico Life Sciences LLC (authored by Rex Kerr)
  * Distributed under the LGPL2.1 license (or GPL2.1 with classpath exception)
  */

package mwt;

import java.util.*;
import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
* Recursive file listing under a specified directory.
*  
* @author javapractices.com
* @author Alex Wong
* @author Nicholas Andrew Swierczek <swierczekn@janelia.hhmi.org>
*/
public final class FileListing { 
    private static Matcher matcher;
    private static final Pattern dir_pattern = Pattern.compile("\\d{8}_\\d{6}");
    private static final Pattern file_pattern = Pattern.compile("\\d{8}_\\d{6}\\.transferred");
    private static final Pattern zip_pattern = Pattern.compile("\\d{8}_\\d{6}\\.zip");
    private static final Pattern spool_pattern = Pattern.compile(".+\\.spl");

    /**
    * Recursively walk a directory tree and return a List of all
    * Files found; the List is sorted using File.compareTo().
    *
    * @param aStartingDir is a valid directory, which can be read.
    */
    static public List<File> getDataDirectoryListing( File aStartingDir ) throws FileNotFoundException {
        validateDirectory(aStartingDir);
        List<File> result = getDataDirectoryListingNoSort(aStartingDir);
        Collections.sort(result);
        return result;
    }
    
    // PRIVATE //
    static private List<File> getDataDirectoryListingNoSort( File aStartingDir ) throws FileNotFoundException {
        Matcher dm;
        Matcher fm;
        List<File> result = new ArrayList<File>();
        File[] filesAndDirs = aStartingDir.listFiles();
        List<File> filesDirs = Arrays.asList(filesAndDirs);
        for(File file : filesDirs) {
            if( file.isDirectory() ) {
                dm = dir_pattern.matcher(file.getName() );
                
                if( dm.matches() ) {
                    result.add(file);
                }
                else {
                    List<File> deeperList = getDataDirectoryListingNoSort(file);
                    result.addAll(deeperList);
                }
            }
        }
        return result;
    }

    /**
    * Recursively walk a directory tree and return a List of all
    * Files found; the List is sorted using File.compareTo().
    *
    * @param aStartingDir is a valid directory, which can be read.
    */
    static public List<File> getTransferredArchiveListing( File aStartingDir ) throws FileNotFoundException {
        validateDirectory(aStartingDir);
        List<File> result = getTransferredArchiveListingNoSort(aStartingDir);
        Collections.sort(result);
        return result;
    }

    // PRIVATE //
    static private List<File> getTransferredArchiveListingNoSort( File aStartingDir ) throws FileNotFoundException {
        Matcher m;
        List<File> result = new ArrayList<File>();
        File[] filesAndDirs = aStartingDir.listFiles();
        List<File> filesDirs = Arrays.asList(filesAndDirs);
        for(File file : filesDirs) {
            if( file.isFile() ) {
                m = file_pattern.matcher(file.getName());
                if( m.matches() )
                    result.add(file);
            }
            else {
            //result.add(file); //always add, even if directory
            //if ( ! file.isFile() ) {
                //must be a directory
                //recursive call!
                List<File> deeperList = getTransferredArchiveListingNoSort(file);
                result.addAll(deeperList);
            }
        }
        return result;
    }

  /**
    * Recursively walk a directory tree and return a List of all
    * Files found; the List is sorted using File.compareTo().
    *
    * @param aStartingDir is a valid directory, which can be read.
    */
    static public List<File> getArchiveListing( File aStartingDir ) throws FileNotFoundException {
        validateDirectory(aStartingDir);
        List<File> result = getArchiveListingNoSort(aStartingDir);
        Collections.sort(result);
        return result;
    }

    // PRIVATE //
    static private List<File> getArchiveListingNoSort( File aStartingDir ) throws FileNotFoundException {
        Matcher m;
        List<File> result = new ArrayList<File>();
        File[] filesAndDirs = aStartingDir.listFiles();
        List<File> filesDirs = Arrays.asList(filesAndDirs);
        for(File file : filesDirs) {
            if( file.isFile() ) {
                m = zip_pattern.matcher(file.getName());
                if( m.matches() )
                    result.add(file);

            }
            else {
                if( file.isDirectory() ) {
                m = dir_pattern.matcher(file.getName());
                if( m.matches() )
                    result.add(file);
                }
                else {
                    List<File> deeperList = getArchiveListingNoSort(file);
                    result.addAll(deeperList);
                }
            }
        }
        return result;
    }

  /**
    * Recursively walk a directory tree and return a List of all
    * Files found; the List is sorted using File.compareTo().
    *
    * @param aStartingDir is a valid directory, which can be read.
    */
    static public List<File> getSpoolFileListing( File aStartingDir ) throws FileNotFoundException {
        validateDirectory(aStartingDir);
        List<File> result = getSpoolFileListingNoSort(aStartingDir);
        Collections.sort(result);
        return result;
    }

    // PRIVATE //
    static private List<File> getSpoolFileListingNoSort( File aStartingDir ) throws FileNotFoundException {
        Matcher m;
        List<File> result = new ArrayList<File>();
        File[] filesAndDirs = aStartingDir.listFiles();
        List<File> filesDirs = Arrays.asList(filesAndDirs);
        for(File file : filesDirs) {
            if( file.isFile() ) {
                m = spool_pattern.matcher(file.getName());
                if( m.matches() ) {
                    result.add(file);
                }
            }
            else {
//                if( file.isDirectory() ) {
                    List<File> deeperList = getSpoolFileListingNoSort(file);
                    result.addAll(deeperList);
//                }
            }
        }
        return result;
    }


    /**
    * Directory is valid if it exists, does not represent a file, and can be read.
    */
    static private void validateDirectory ( File aDirectory ) throws FileNotFoundException {
        if (aDirectory == null) {
            throw new IllegalArgumentException("Directory should not be null.");
        }
        if (!aDirectory.exists()) {
            throw new FileNotFoundException("Directory does not exist: " + aDirectory);
        }
        if (!aDirectory.isDirectory()) {
            throw new IllegalArgumentException("Is not a directory: " + aDirectory);
        }
        if (!aDirectory.canRead()) {
            throw new IllegalArgumentException("Directory cannot be read: " + aDirectory);
        }
    }
} 
