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

import org.graalvm.word.Pointer;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.util.VMError;

/** Visitor for object references. */
public interface ObjectReferenceVisitor {
    /**
     * Visit an object reference.
     *
     * @param objRef Address of object reference to visit (not address of the referenced object).
     * @param compressed True if the reference is in compressed form, false otherwise.
     * @param holderObject The object containing the reference, or {@code null} if the reference is
     *            not part of an object.
     * @return {@code true} if visiting should continue, {@code false} if visiting should stop.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, overridesCallers = true, reason = "Some implementations allocate.")
    boolean visitObjectReference(Pointer objRef, boolean compressed, Object holderObject);

    /**
     * @param innerOffset If the reference is a {@linkplain CodeReferenceMapDecoder derived
     *            reference}, a positive integer that must be subtracted from the address to which
     *            the object reference points in order to get the start of the referenced object.
     */
    @AlwaysInline("GC performance")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, overridesCallers = true, reason = "Some implementations allocate.")
    default boolean visitObjectReferenceInline(Pointer objRef, int innerOffset, boolean compressed, Object holderObject) {
        VMError.guarantee(innerOffset == 0, "visitor does not support derived references");
        return visitObjectReference(objRef, compressed, holderObject);
    }
}
