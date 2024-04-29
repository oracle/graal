/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.compacting;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import java.lang.ref.Reference;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

/** Visits surviving objects before compaction to update their references. */
public final class ObjectFixupVisitor implements ObjectVisitor {
    private final ObjectRefFixupVisitor refFixupVisitor;

    public ObjectFixupVisitor(ObjectRefFixupVisitor refFixupVisitor) {
        this.refFixupVisitor = refFixupVisitor;
    }

    @Override
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean visitObjectInline(Object obj) {
        DynamicHub hub = KnownIntrinsics.readHub(obj);
        if (probability(SLOW_PATH_PROBABILITY, hub.isReferenceInstanceClass())) {
            // update Target_java_lang_ref_Reference.referent
            Reference<?> dr = (Reference<?>) obj;
            refFixupVisitor.visitObjectReferenceInline(ReferenceInternals.getReferentFieldAddress(dr), 0, true, dr);
        }
        InteriorObjRefWalker.walkObjectInline(obj, refFixupVisitor);
        return true;
    }

    @Override
    public boolean visitObject(Object o) {
        throw VMError.shouldNotReachHere("for performance reasons");
    }
}
