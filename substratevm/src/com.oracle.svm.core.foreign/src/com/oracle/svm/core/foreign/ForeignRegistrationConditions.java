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
package com.oracle.svm.core.foreign;

import java.lang.constant.DirectMethodHandleDesc;
import java.lang.foreign.FunctionDescriptor;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;

import com.oracle.svm.core.foreign.ForeignFunctionsRuntime.DirectUpcallKey;
import com.oracle.svm.guest.staging.configure.RuntimeDynamicAccessMetadata;
import com.oracle.svm.guest.staging.util.ImageHeapMap;
import com.oracle.svm.shared.singletons.MultiLayeredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.MultiLayer;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.SubstrateUtil;

/**
 * Records the conditions under which downcalls, upcalls and direct upcalls were registered, keyed
 * like the stubs: a {@link NativeEntryPointInfo}, a {@link JavaEntryPointInfo} or a
 * {@link DirectUpcallKey}. A stub whose registrations are all guarded by unsatisfied run-time
 * conditions is treated as if it was not registered. A stub without recorded conditions, e.g., one
 * created for a method handle that was linked at build time, stays available. Each layer records
 * its own registrations, and a stub is available if any layer has a satisfied registration for it.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = MultiLayer.class)
public final class ForeignRegistrationConditions {
    private final EconomicMap<Object, RuntimeDynamicAccessMetadata> conditions = ImageHeapMap.createNonLayeredMap();

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void create() {
        ImageSingletons.add(ForeignRegistrationConditions.class, new ForeignRegistrationConditions());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static ForeignRegistrationConditions currentLayer() {
        return ImageSingletons.lookup(ForeignRegistrationConditions.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void addDowncall(NativeEntryPointInfo nep, AccessCondition condition) {
        addCondition(nep, condition);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void addUpcall(JavaEntryPointInfo jep, AccessCondition condition) {
        addCondition(jep, condition);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void addDirectUpcall(DirectMethodHandleDesc desc, JavaEntryPointInfo jep, FunctionDescriptor functionDescriptor, AccessCondition condition) {
        addCondition(new DirectUpcallKey(desc, jep, functionDescriptor), condition);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private void addCondition(Object key, AccessCondition condition) {
        synchronized (conditions) {
            conditions.put(key, RuntimeDynamicAccessMetadata.addCondition(conditions.get(key), condition, true));
        }
    }

    /**
     * Returns {@code false} if the stub identified by {@code key} was registered only under
     * conditions that are all unsatisfied, i.e., if it must be treated as not registered.
     */
    static boolean isAvailable(Object key) {
        if (SubstrateUtil.HOSTED) {
            /* Stubs are looked up at build time only for handles linked at build time. */
            return true;
        }
        boolean registered = false;
        for (ForeignRegistrationConditions layer : MultiLayeredImageSingleton.getAllLayers(ForeignRegistrationConditions.class)) {
            RuntimeDynamicAccessMetadata metadata = layer.conditions.get(key);
            if (metadata != null) {
                if (metadata.satisfied()) {
                    return true;
                }
                registered = true;
            }
        }
        return !registered;
    }
}
