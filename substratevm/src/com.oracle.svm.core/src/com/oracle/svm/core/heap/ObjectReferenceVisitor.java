/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.word.Pointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.util.VMError;

/**
 * Visitor for object references in Java heap objects, Java stack frames, or off-heap data
 * structures.
 */
public interface ObjectReferenceVisitor {
    /**
     * Visits a sequence of object references. Implementors of this method must loop over the
     * references, which involves some boilerplate code. This code duplication is intentional as it
     * reduces the number of virtual dispatches.
     *
     * @param firstObjRef Address where the first object reference is stored.
     * @param compressed true if the references are regular Java references (like an instance
     *            field), false if they are absolute word-sized pointers (like an uncompressed
     *            pointer on the stack).
     * @param referenceSize size in bytes of one reference
     * @param holderObject The object containing the reference, or {@code null} if the reference is
     *            not part of a Java object (e.g., the reference is on the stack or in a data
     *            structure that is located in native memory).
     * @param count The number of object references.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, reason = "Some implementations allocate.")
    void visitObjectReferences(Pointer firstObjRef, boolean compressed, int referenceSize, Object holderObject, int count);

    /**
     * Visits a derived reference. Derived references can only be on the stack or in a
     * {@link StoredContinuation}.
     *
     * @param baseObjRef Address where the base reference is stored.
     * @param derivedObjRef Address where the derived reference is stored.
     * @param holderObject The object containing the reference, or {@code null} if the reference is
     *            not part of a Java object (e.g., the reference is on the stack or in a data
     *            structure that is located in native memory).
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    default void visitDerivedReference(@SuppressWarnings("unused") Pointer baseObjRef, @SuppressWarnings("unused") Pointer derivedObjRef, @SuppressWarnings("unused") Object holderObject) {
        throw VMError.shouldNotReachHere("Derived references are not supported by this visitor.");
    }
}
