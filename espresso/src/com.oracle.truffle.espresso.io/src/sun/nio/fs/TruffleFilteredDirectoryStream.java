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
 * DirectoryStream implementation by setting a host iterator and stream as hidden host reference
 * then proxying all methods to the native world where we retrieve the host object and do the
 * semantics of the function. Note this file must be compatible with 21+.
 */
final class TruffleFilteredDirectoryStream implements DirectoryStream<Path> {
    private final TrufflePath truffleDir;
    private final DirectoryStream<String> stream;
    private final DirectoryStream.Filter<? super Path> filter;

    /**
     * Thin wrapper for a foreign (host) {@code Iterator<String>}, cannot have any fields.
     */
    private static final class ForeignIterator implements Iterator<String> {

        private ForeignIterator() {
            // only foreign wrappers allowed
        }

        @Override
        public boolean hasNext() {
            return hasNext0(this);
        }

        @Override
        public String next() {
            return next0(this);
        }
    }

    /**
     * Thin wrapper for a foreign (host) DirectoryStream, cannot have any fields.
     */
    private static final class ForeignDirectoryStream implements DirectoryStream<String> {

        private ForeignDirectoryStream() {
            // only foreign wrappers
        }

        @Override
        public void close() throws IOException {
            close0(this);
        }

        @Override
        public Iterator<String> iterator() {
            return iterator0(this);
        }
    }

    static DirectoryStream<Path> create(TrufflePath truffleDir, Filter<? super Path> filter) throws IOException {
        if (!Files.isDirectory(truffleDir)) {
            throw new NotDirectoryException(truffleDir.toString());
        }
        return new TruffleFilteredDirectoryStream(truffleDir, directoryStream0(truffleDir), filter);
    }

    private TruffleFilteredDirectoryStream(TrufflePath dir, DirectoryStream<String> stream, Filter<? super Path> filter) {
        this.truffleDir = Objects.requireNonNull(dir);
        this.filter = Objects.requireNonNull(filter);
        this.stream = Objects.requireNonNull(stream);
        assert Files.isDirectory(dir);
    }

    @Override
    public Iterator<Path> iterator() {
        return MapFilterIterator.mapThenFilter(stream.iterator(), tf -> new TrufflePath((TruffleFileSystem) truffleDir.getFileSystem(), truffleDir.resolve(tf).toString()),
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

    private static native DirectoryStream<String> directoryStream0(TrufflePath dir) throws IOException;

    private static native boolean hasNext0(Iterator<String> iterator);

    private static native String next0(Iterator<String> iterator);

    private static native void close0(DirectoryStream<String> directoryStream) throws IOException;

    private static native Iterator<String> iterator0(DirectoryStream<String> directoryStream);

    // endregion native methods
}
