/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.heap;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

/**
 * Means for accessing object references, explicitly distinguishing between compressed and
 * uncompressed references.
 */
public interface ReferenceAccess {
    @Fold
    static ReferenceAccess singleton() {
        return ImageSingletons.lookup(ReferenceAccess.class);
    }

    /**
     * Read the absolute address of the object referenced by the object reference at address
     * {@code p} and return it as a word which is not tracked by garbage collection.
     */
    Word readObjectAsUntrackedPointer(Pointer p, boolean compressed);

    /**
     * Read the object reference at address {@code p} and return it.
     */
    Object readObjectAt(Pointer p, boolean compressed);

    /**
     * Write the location of object {@code value} to the object reference at address {@code p}.
     */
    void writeObjectAt(Pointer p, Object value, boolean compressed);

    /*
     * Using a write barrier, write the object reference at offset {@code offsetInObject} within
     * object {@code object} so that it refers to object {@code value}.
     */
    void writeObjectBarrieredAt(Object object, UnsignedWord offsetInObject, Object value, boolean compressed);

    /**
     * Return the compressed representation of an object reference.
     */
    UnsignedWord getCompressedRepresentation(Object obj);

    /**
     * Get an object reference from its compressed representation.
     */
    Object uncompressReference(UnsignedWord ref);

    /**
     * Returns true iff compressed references are available.
     */
    boolean haveCompressedReferences();

    /**
     * Returns the default compression encoding.
     */
    CompressEncoding getCompressEncoding();
}
