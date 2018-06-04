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

import com.oracle.svm.core.annotate.RestrictHeapAccess;

/**
 * Supply a closure to be applied to Objects.
 *
 */
public interface ObjectVisitor {

    /**
     * Called before any Objects are visited. For example, from the client who creates the
     * ObjectVisitor.
     *
     * @return true if visiting should continue, false if visiting should stop.
     */
    default boolean prologue() {
        return true;
    }

    /**
     * Visit an Object.
     *
     * @param o The Object to be visited.
     * @return true if visiting should continue, false if visiting should stop.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while visiting the heap.")
    boolean visitObject(Object o);

    /** Like visitObject(Object), but inlined for performance. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while visiting the heap.")
    default boolean visitObjectInline(Object o) {
        return visitObject(o);
    }

    /**
     * Called after all Objects have been visited. For example, from the client who creates the
     * ObjectVisitor. If visiting terminates because a visitor returned false, this method might not
     * be called.
     *
     * @return true if the epilogue executed successfully, false otherwise.
     */
    default boolean epilogue() {
        return true;
    }
}
