/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.os;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.shared.Uninterruptible;

/**
 * Provides functionality for low-level IO operations used by the registered
 * {@link AuxiliaryImageProvider}.
 */
public interface AuxiliaryImageIOProvider {

    interface FileDesc extends ComparableWord {
    }

    /**
     * Opens the file at the given path for reading. On error, returns {@link Word#zero()},
     * otherwise provides an opaque value to pass to other methods of this interface.
     */
    @Uninterruptible(reason = "Called during isolate initialization.")
    FileDesc open(CCharPointer filePath);

    /**
     * Closes the file with the open file description associated with the given file descriptor.
     */
    @Uninterruptible(reason = "Called during isolate initialization.")
    void close(FileDesc fd);

    /**
     * Reads up to {@code nbytes} from the given file descriptor at a given offset into the buffer
     * starting at {@code buf}.
     *
     * @return if positive, the number of read bytes; zero indicates end of file; negative value
     *         indicates an error.
     */
    @Uninterruptible(reason = "Called during isolate initialization.")
    SignedWord pread(FileDesc fd, PointerBase buf, UnsignedWord nbytes, SignedWord offset);

    /**
     * Maps a region of the file into memory. This method both uses and conforms to the
     * specification of {@link VirtualMemoryProvider#mapFile}, but takes {@link FileDesc} instead.
     */
    @Uninterruptible(reason = "Called during isolate initialization.")
    Pointer mapFile(PointerBase start, UnsignedWord nbytes, FileDesc desc, UnsignedWord offset, int access);
}
