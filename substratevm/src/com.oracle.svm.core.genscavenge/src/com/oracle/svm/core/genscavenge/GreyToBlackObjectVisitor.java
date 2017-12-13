/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.log.Log;

/**
 * Run an ObjectReferenceVisitor ({@link GreyToBlackObjRefVisitor}) over any interior object
 * references in the Object, turning this Object from grey to black.
 *
 * This visitor is used during GC and so it must be constructed during native image generation.
 *
 * The vanilla visitObject method is not inlined, but there is a visitObjectInline available for
 * performance critical code.
 */
public final class GreyToBlackObjectVisitor implements ObjectVisitor {

    @Platforms(Platform.HOSTED_ONLY.class)
    public static GreyToBlackObjectVisitor factory(final ObjectReferenceVisitor objRefVisitor) {
        return new GreyToBlackObjectVisitor(objRefVisitor);
    }

    /** Visit the interior Pointers of an Object. */
    @Override
    public boolean visitObject(final Object o) {
        return visitObjectInline(o);
    }

    @Override
    @AlwaysInline("GC performance")
    public boolean visitObjectInline(final Object o) {
        final Log trace = Log.noopLog();
        // TODO: Why would this be passed a null Object?
        if (o == null) {
            return true;
        }
        trace.string("[GreyToBlackObjectVisitor:").string("  o: ").object(o);
        DiscoverableReferenceProcessing.discoverDiscoverableReference(o);
        InteriorObjRefWalker.walkObjectInline(o, objRefVisitor);
        trace.string("]").newline();
        return true;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private GreyToBlackObjectVisitor(final ObjectReferenceVisitor objRefVisitor) {
        super();
        this.objRefVisitor = objRefVisitor;
    }

    // Immutable state.
    private final ObjectReferenceVisitor objRefVisitor;
}
