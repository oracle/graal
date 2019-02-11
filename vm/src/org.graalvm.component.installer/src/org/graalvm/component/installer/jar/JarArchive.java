/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.graalvm.component.installer.jar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.CRC32;
import org.graalvm.component.installer.Archive;

/**
 *
 * @author sdedic
 */
public class JarArchive implements Archive {
    private static final int CHECKSUM_BUFFER_SIZE = 1024 * 32;

    private final JarFile jarFile;

    public JarArchive(JarFile jarFile) {
        this.jarFile = jarFile;
    }
    
    public FileEntry getEntry(String path) {
        return getJarEntry(path);
    }
    
    public FileEntry getJarEntry(String path) {
        JarEntry e = jarFile.getJarEntry(path);
        return e == null ? null : new JarEntryImpl(e);
    }

    @Override
    public Iterator<FileEntry> iterator() {
        return new EntryIterator(jarFile.entries());
    }

    @Override
    public void close() throws IOException {
        jarFile.close();
    }

    @Override
    public Enumeration<FileEntry> entries() {
        return new EntryIterator(jarFile.entries());
    }

    @Override
    public InputStream getInputStream(FileEntry e) throws IOException {
        return jarFile.getInputStream(((JarEntryImpl)e).e);
    }

    @Override
    public boolean checkContentsMatches(ByteChannel bc, FileEntry entry) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(CHECKSUM_BUFFER_SIZE);
        CRC32 crc = new CRC32();
        while (bc.read(bb) >= 0) {
            bb.flip();
            crc.update(bb);
            bb.clear();
        }
        return crc.getValue() == ((JarEntryImpl)entry).e.getCrc();
    }
    
    private static class EntryIterator implements Iterator<FileEntry>, Enumeration<FileEntry> {
        private final Enumeration<JarEntry> enEntries;

        public EntryIterator(Enumeration<JarEntry> enEntries) {
            this.enEntries = enEntries;
        }

        @Override
        public boolean hasMoreElements() {
            return hasNext();
        }

        @Override
        public FileEntry nextElement() {
            return next();
        }

        @Override
        public boolean hasNext() {
            return enEntries.hasMoreElements();
        }

        @Override
        public JarEntryImpl next() {
            return new JarEntryImpl(enEntries.nextElement());
        }
    }
    
    private static class JarEntryImpl implements FileEntry {
        private final JarEntry e;

        public JarEntryImpl(JarEntry e) {
            this.e = e;
        }
        
        @Override
        public String getName() {
            return e.getName();
        }

        @Override
        public boolean isDirectory() {
            return e.isDirectory();
        }

        @Override
        public long getSize() {
            return e.getSize();
        }

        @Override
        public boolean isSymbolicLink() {
            return false;
        }

        @Override
        public String getLinkTarget() {
            return null;
        }
    }
}
