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
package com.oracle.svm.core.hub.registry;

import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.BuildPhaseProvider.AfterCompilation;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.ApplicationLayerOnly;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.VMError;

/** Keeps track of type ID information at run-time (see {@link DynamicHub#getTypeID()}). */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = ApplicationLayerOnly.class)
public class TypeIDs {
    private final AtomicInteger nextTypeId = new AtomicInteger();
    @UnknownPrimitiveField(availability = AfterCompilation.class) //
    private int firstRuntimeTypeId;

    @Platforms(Platform.HOSTED_ONLY.class)
    TypeIDs() {
    }

    public static TypeIDs singleton() {
        return ImageSingletons.lookup(TypeIDs.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    void initialize() {
        assert firstRuntimeTypeId == 0 && nextTypeId.get() == 0;
        firstRuntimeTypeId = DynamicHubSupport.currentLayer().getMaxTypeId() + 1;
        nextTypeId.set(firstRuntimeTypeId);
    }

    /** The type id that is used for the first type that is loaded at run-time. */
    public int getFirstRuntimeTypeId() {
        assert firstRuntimeTypeId > 0;
        return firstRuntimeTypeId;
    }

    public int nextTypeId() {
        int result = nextTypeId.getAndIncrement();
        VMError.guarantee(result > 0);
        return result;
    }

    public int getNumTypeIds() {
        return nextTypeId.get();
    }
}

@AutomaticallyRegisteredFeature
class TypeIdsFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.lastImageBuild();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageSingletons.add(TypeIDs.class, new TypeIDs());
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        TypeIDs.singleton().initialize();
    }
}
