/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging.core.heap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.guest.staging.util.AbstractImageHeapList;
import com.oracle.svm.guest.staging.util.ImageHeapList;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public class GCCauseSupport {
    final AbstractImageHeapList<GCCause> gcCauses = ImageHeapList.create(GCCause.class, null);

    @Platforms(Platform.HOSTED_ONLY.class)
    public GCCauseSupport() {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void install(GCCauseSupport support) {
        if (!ImageSingletons.contains(GCCauseSupport.class)) {
            ImageSingletons.add(GCCauseSupport.class, support);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    void installGCCause(GCCause gcCause) {
        synchronized (gcCauses) {
            int id = gcCause.getId();
            while (gcCauses.size() <= id) {
                gcCauses.add(null);
            }
            var existing = gcCauses.set(id, gcCause);
            if (existing != null && existing != gcCause) {
                throw VMError.shouldNotReachHere("Two GCCause objects have the same id " + id + ": " + gcCause.getName() + ", " + existing.getName());
            }
        }
    }
}
