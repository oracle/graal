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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.graal.meta.SubstrateBasicLoweringProvider;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.word.Word;

/**
 * Means for accessing object references, explicitly distinguishing between compressed and
 * uncompressed references.
 * <p>
 * Accessing hub references involves the reserved GC bits, compression shift and object alignment
 * and is defined by {@link SubstrateBasicLoweringProvider#createReadHub}.
 * <p>
 * Regular references just require the heapbase register (for -H:+SpawnIsolates) and compression
 * shift (for -H:+UseCompressedReferences)
 * </p>
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

    /**
     * Returns a compile-time constant for {@link CompressEncoding#getShift()}.
     */
    int getCompressionShift();

    /**
     * Returns the maximum size that the Java heap address space can have at run-time (e.g., based
     * on the reference size).
     */
    UnsignedWord getMaxAddressSpaceSize();
}
