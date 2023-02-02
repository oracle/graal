/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk.resources;

import java.io.IOException;
import java.nio.file.ClosedDirectoryStreamException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class NativeImageResourceDirectoryStream implements DirectoryStream<Path> {

    private final NativeImageResourceFileSystem fileSystem;
    private final DirectoryStream.Filter<? super Path> filter;
    private final NativeImageResourcePath dir;
    private volatile boolean isClosed = false;
    private Iterator<Path> directoryIterator;

    public NativeImageResourceDirectoryStream(NativeImageResourcePath dir, Filter<? super Path> filter) throws IOException {
        this.fileSystem = dir.getFileSystem();
        this.dir = dir;
        this.filter = filter;
        if (!fileSystem.isDirectory(dir.getResolvedPath())) {
            throw new NotDirectoryException(dir.toString());
        }
    }

    @Override
    public Iterator<Path> iterator() {
        if (isClosed) {
            throw new ClosedDirectoryStreamException();
        }
        if (directoryIterator != null) {
            throw new IllegalStateException("Iterator has already been returned");
        }

        try {
            directoryIterator = fileSystem.iteratorOf(dir, filter);
        } catch (IOException ioException) {
            throw new DirectoryIteratorException(ioException);
        }

        return new Iterator<>() {

            @Override
            public boolean hasNext() {
                if (isClosed) {
                    return false;
                }
                return directoryIterator.hasNext();
            }

            @Override
            public Path next() {
                if (isClosed) {
                    throw new NoSuchElementException();
                }
                return directoryIterator.next();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public void close() throws IOException {
        if (!isClosed) {
            isClosed = true;
        }
    }
}
