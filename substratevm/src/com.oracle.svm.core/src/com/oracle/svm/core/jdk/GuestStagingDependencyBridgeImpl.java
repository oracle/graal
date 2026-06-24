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
package com.oracle.svm.core.jdk;

import com.oracle.svm.core.IsolateArgumentParser;
import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.heap.HeapSizeVerifier;
import com.oracle.svm.guest.staging.GuestStagingDependencyBridge;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

/**
 * Provides core implementations for runtime services that guest-staging code cannot yet access
 * directly.
 */
@AutomaticallyRegisteredImageSingleton(GuestStagingDependencyBridge.class)
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
final class GuestStagingDependencyBridgeImpl implements GuestStagingDependencyBridge {

    @Override
    public void verifyIsolateArgumentOptionValues() {
        IsolateArgumentParser.singleton().verifyOptionValues();
    }

    @Override
    public void verifyHeapOptions() {
        HeapSizeVerifier.verifyHeapOptions();
    }

    @Override
    public boolean isCurrentFirstIsolate() {
        return Isolates.isCurrentFirst();
    }

    @Override
    public void runJavaShutdownHooks() {
        Target_java_lang_Shutdown.shutdown();
    }

    @Override
    public void runLogManagerShutdownHook() {
        Util_java_lang_Shutdown.runLogManagerShutdownHook();
    }
}
