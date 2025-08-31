/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package sun.nio.fs;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Objects;

/**
 * This file must be compatible with 21+.
 */
final class TruffleFilteredDirectoryStream implements DirectoryStream<Path> {
    private final TrufflePath truffleDir;
    private final DirectoryStream<Object> stream;
    private final DirectoryStream.Filter<? super Path> filter;

    /**
     * Thin wrapper for a foreign (host) {@code Iterator<Object>}, cannot have any fields.
     */
    private static final class ForeignIterator implements Iterator<Object> {

        private ForeignIterator() {
            // only foreign wrappers allowed
        }

        @Override
        public boolean hasNext() {
            return hasNext0(this);
        }

        @Override
        public Object next() {
            return next0(this);
        }
    }

    /**
     * Thin wrapper for a foreign (host) DirectoryStream, cannot have any fields.
     */
    private static final class ForeignDirectoryStream implements DirectoryStream<Object> {

        private ForeignDirectoryStream() {
            // only foreign wrappers
        }

        @Override
        public void close() throws IOException {
            close0(this);
        }

        @Override
        public Iterator<Object> iterator() {
            return iterator0(this, ForeignIterator.class);
        }
    }

    static DirectoryStream<Path> create(TrufflePath truffleDir, Filter<? super Path> filter) throws IOException {
        if (!Files.isDirectory(truffleDir)) {
            throw new NotDirectoryException(truffleDir.toString());
        }
        return new TruffleFilteredDirectoryStream(truffleDir, directoryStream0(truffleDir, ForeignDirectoryStream.class), filter);
    }

    private TruffleFilteredDirectoryStream(TrufflePath dir, DirectoryStream<Object> stream, Filter<? super Path> filter) {
        this.truffleDir = Objects.requireNonNull(dir);
        this.filter = Objects.requireNonNull(filter);
        this.stream = Objects.requireNonNull(stream);
        assert Files.isDirectory(dir);
    }

    @Override
    public Iterator<Path> iterator() {
        return MapFilterIterator.mapThenFilter(stream.iterator(), tf -> toTrufflePath0(tf, truffleDir.getTruffleFileSystem()),
                        path -> {
                            try {
                                return filter.accept(path);
                            } catch (IOException e) {
                                throw sneakyThrow(e);
                            }
                        });
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable ex) throws T {
        throw (T) ex;
    }

    // region native methods

    private static native DirectoryStream<Object> directoryStream0(TrufflePath dir, Class<ForeignDirectoryStream> directoryStreamClass) throws IOException;

    private static native boolean hasNext0(Iterator<Object> iterator);

    private static native Object next0(Iterator<Object> iterator);

    private static native void close0(DirectoryStream<Object> directoryStream) throws IOException;

    private static native Iterator<Object> iterator0(DirectoryStream<Object> directoryStream, Class<ForeignIterator> iteratorClass);

    private static native TrufflePath toTrufflePath0(Object truffleFile, TruffleFileSystem truffleFileSystem);

    // endregion native methods
}
