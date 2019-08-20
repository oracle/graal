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
package org.graalvm.component.installer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ReadableByteChannel;
import org.graalvm.component.installer.model.ComponentInfo;

/**
 * Simple abstraction over an archive, so that both JAR and RPMs (or other packaging) can be read.
 * 
 * @author sdedic
 */
public interface Archive extends Iterable<Archive.FileEntry>, AutoCloseable {
    /**
     * Opens an input stream for the entry.
     * 
     * @param e file entry
     * @return the input stream
     * @throws IOException on I/O error
     */
    InputStream getInputStream(FileEntry e) throws IOException;

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
    boolean checkContentsMatches(ReadableByteChannel bc, FileEntry entry) throws IOException;

    /**
     * Verifies integrity of the archive.
     * 
     * @param input options for verificaion
     * @return true, if the archive has been verified
     * @throws IOException
     */
    boolean verifyIntegrity(CommandInput input) throws IOException;

    /**
     * Completes metadata in `info' with information within the archive's contents. This method may
     * need to iterate through files in the archive.
     * 
     * @param info
     * @throws IOException
     */
    void completeMetadata(ComponentInfo info) throws IOException;

    @Override
    void close() throws IOException;

    /**
     * Represents a single entry in the archive.
     */
    interface FileEntry {
        /**
         * Returns name of the entry. Directory names should end with "/".
         * 
         * @return entry name
         */
        String getName();

        /**
         * @return true, if the entry represents a directory
         */
        boolean isDirectory();

        /**
         * True, if the entry is a symbolic link.
         * 
         * @return True, if the entry represents a symbolic link
         */
        boolean isSymbolicLink();

        /**
         * Link target for symbolic links.
         * 
         * @return target path
         * @throws java.io.IOException if the link's target could not be read
         * @throws IllegalStateException if the entry is not {@link #isSymbolicLink()}.
         */
        String getLinkTarget() throws IOException;

        /**
         * @return size of the content, only valid for regular files.
         */
        long getSize();
    }
}
