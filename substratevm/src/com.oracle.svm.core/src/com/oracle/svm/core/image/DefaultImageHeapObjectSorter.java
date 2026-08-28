/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.image;

import com.oracle.svm.core.classinitialization.ClassInitializationInfo;
import com.oracle.svm.core.hub.DynamicHubCompanion;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

import java.util.concurrent.locks.AbstractOwnableSynchronizer;

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
public class DefaultImageHeapObjectSorter extends BasicImageHeapObjectSorter {

    @Override
    protected int compareGroup(ImageHeapObject a, ImageHeapObject b) {
        int s = super.compareGroup(a, b);
        if (s != 0) {
            return s;
        }

        /*
         * Hub companions contain writable fields for reflection data and other values that are
         * lazily decoded or computed at runtime. Grouping them can significantly reduce dirtied
         * (copy on write) image heap pages, which improves sharing between isolates and processes.
         */
        boolean aIsHubCompanion = a.getObjectClass() == DynamicHubCompanion.class;
        boolean bIsHubCompanion = b.getObjectClass() == DynamicHubCompanion.class;
        if (aIsHubCompanion != bIsHubCompanion) {
            return aIsHubCompanion ? -1 : 1;
        }

        /*
         * ClassInitializationInfo objects are written when a class is initialized or first reached
         * at runtime. They also use locks in the image heap so that they are readily available at
         * runtime. Grouping CII and lock synchronizers can also reduce dirtied image heap pages.
         */
        boolean aIsClassInitInfo = a.getObjectClass() == ClassInitializationInfo.class;
        boolean bIsClassInitInfo = b.getObjectClass() == ClassInitializationInfo.class;
        if (aIsClassInitInfo != bIsClassInitInfo) {
            return aIsClassInitInfo ? -1 : 1;
        }
        boolean aIsSynchronizer = AbstractOwnableSynchronizer.class.isAssignableFrom(a.getObjectClass());
        boolean bIsSynchronizer = AbstractOwnableSynchronizer.class.isAssignableFrom(b.getObjectClass());
        if (aIsSynchronizer != bIsSynchronizer) {
            return aIsSynchronizer ? -1 : 1;
        }

        return 0;
    }
}
