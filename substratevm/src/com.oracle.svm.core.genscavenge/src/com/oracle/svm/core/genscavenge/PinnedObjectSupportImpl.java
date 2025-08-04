/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.AbstractPinnedObjectSupport;
import com.oracle.svm.core.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.core.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.core.traits.SingletonTraits;

import jdk.graal.compiler.nodes.NamedLocationIdentity;

/** Support for pinning objects to a memory address with {@link PinnedObject}. */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public final class PinnedObjectSupportImpl extends AbstractPinnedObjectSupport {
    @Platforms(Platform.HOSTED_ONLY.class)
    public PinnedObjectSupportImpl() {
    }

    @Override
    @Uninterruptible(reason = "Ensure that pinned object counts and PinnedObjects are consistent.", callerMustBe = true)
    protected void pinObject(Object object) {
        modifyPinnedObjectCount(object, 1);
    }

    @Override
    @Uninterruptible(reason = "Ensure that pinned object counts and PinnedObjects are consistent.", callerMustBe = true)
    protected void unpinObject(Object object) {
        modifyPinnedObjectCount(object, -1);
    }

    @Uninterruptible(reason = "Ensure that pinned object counts and PinnedObjects are consistent.", callerMustBe = true)
    private static void modifyPinnedObjectCount(Object object, int delta) {
        Pointer pinnedObjectCount = HeapChunk.getEnclosingHeapChunk(object).addressOfPinnedObjectCount();
        int oldValue;
        do {
            oldValue = pinnedObjectCount.readInt(0);
        } while (!pinnedObjectCount.logicCompareAndSwapInt(0, oldValue, oldValue + delta, NamedLocationIdentity.OFF_HEAP_LOCATION));

        assert oldValue < Integer.MAX_VALUE;
    }
}
