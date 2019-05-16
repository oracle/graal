/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.component.installer.jar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.CRC32;
import org.graalvm.component.installer.Archive;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.model.ComponentInfo;

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
    }

    @Override
    public InputStream getInputStream(FileEntry e) throws IOException {
        return jarFile.getInputStream(((JarEntryImpl) e).e);
    }

    @Override
    public boolean checkContentsMatches(ReadableByteChannel bc, FileEntry entry) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(CHECKSUM_BUFFER_SIZE);
        CRC32 crc = new CRC32();
        while (bc.read(bb) >= 0) {
            bb.flip();
            crc.update(bb);
            bb.clear();
        }
        return crc.getValue() == ((JarEntryImpl) entry).e.getCrc();
    }

    @Override
    public void completeMetadata(ComponentInfo info) throws IOException {
        // no op
    }

    /**
     * Always returns true. Signer JARs are automatically verified on open
     * 
     * @return true
     */
    @Override
    public boolean verifyIntegrity(CommandInput input) {
        return true;
    }

    private static class EntryIterator implements Iterator<FileEntry> {
        private final Enumeration<JarEntry> enEntries;

        EntryIterator(Enumeration<JarEntry> enEntries) {
            this.enEntries = enEntries;
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

        JarEntryImpl(JarEntry e) {
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
        public String getLinkTarget() throws IOException {
            throw new IllegalStateException("Not a symbolic link");
        }
    }
}
