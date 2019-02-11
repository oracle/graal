/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.graalvm.component.installer;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ByteChannel;
import java.util.Enumeration;

/**
 * Simple abstraction over an archive, so that both JAR and RPMs (or other packaging) can be read
 * @author sdedic
 */
public interface Archive extends Iterable<Archive.FileEntry>, Closeable {
    /**
     * Provides access to archive entries
     * @return enumeration of entries
     */
    public Enumeration<Archive.FileEntry> entries();
    
    /**
     * Opens an input stream for the entry.
     * @param e file entry
     * @return the input stream
     * @throws IOException on I/O error
     */
    public InputStream getInputStream(FileEntry e) throws IOException;
    
    /**
     * Checks that contents of the entry matches the given file. If the archive stores checksums,
     * the method may not need do byte comparison but can only compute checksum/hash on the supplied
     * content and compare to archive's info.
     * 
     * @param bc the existing content
     * @param entry archive entry
     * @return true if the content is the same
     * @throws IOException on I/O error
     */
    public boolean checkContentsMatches(ByteChannel bc, FileEntry entry) throws IOException;
    
    /**
     * Represents a single entry in the archive
     */
    public interface FileEntry {
        /**
         * Returns name of the entry. Directory names should end with "/".
         * @return entry name
         */
        public String getName();
        
        /**
         * @return  true, if the entry represents a directory
         */
        public boolean isDirectory();
        
        /**
         * @return True, if the entry represents a symbolic link
         */
        public boolean isSymbolicLink();
        
        /**
         * Link target for symbolic links
         * @return 
         */
        public String getLinkTarget();
        
        /**
         * @return size of the content, only valid for regular files.
         */
        public long getSize();
    }
}
