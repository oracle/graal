/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted;

import java.io.InputStream;
import java.io.PrintStream;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.SystemInOutErrSupport;
import com.oracle.svm.core.layeredimagesingleton.FeatureSingleton;
import com.oracle.svm.hosted.imagelayer.CrossLayerConstantRegistry;

import jdk.internal.access.SharedSecrets;
import jdk.vm.ci.meta.JavaConstant;

/**
 * We use an {@link Feature.DuringSetupAccess#registerObjectReplacer object replacer} because the
 * streams can be cached in other instance and static fields in addition to the fields in
 * {@link System}. We do not know all these places, so we do now know where to place
 * {@link RecomputeFieldValue} annotations.
 */
@AutomaticallyRegisteredFeature
public class SystemInOutErrFeature implements InternalFeature, FeatureSingleton {
    private final InputStream hostedIn;
    private final PrintStream hostedOut;
    private final PrintStream hostedErr;
    private final InputStream hostedInitialIn;
    private final PrintStream hostedInitialErr;

    public SystemInOutErrFeature() {
        hostedIn = System.in;
        NativeImageSystemIOWrappers wrappers = NativeImageSystemIOWrappers.singleton();
        hostedOut = wrappers.outWrapper;
        hostedErr = wrappers.errWrapper;
        hostedInitialIn = SharedSecrets.getJavaLangAccess().initialSystemIn();
        hostedInitialErr = SharedSecrets.getJavaLangAccess().initialSystemErr();
    }

    private SystemInOutErrSupport runtime;

    private static final String SYSTEM_IN_KEY_NAME = "System#in";
    private static final String SYSTEM_ERR_KEY_NAME = "System#err";
    private static final String SYSTEM_OUT_KEY_NAME = "System#out";
    private static final String SYSTEM_INITIAL_IN_KEY_NAME = "System#initialIn";
    private static final String SYSTEM_INITIAL_ERR_KEY_NAME = "System#initialErr";

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (ImageLayerBuildingSupport.firstImageBuild()) {
            runtime = new SystemInOutErrSupport();
            ImageSingletons.add(SystemInOutErrSupport.class, runtime);
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        NativeImageSystemIOWrappers.singleton().verifySystemOutErrReplacement();
        if (ImageLayerBuildingSupport.firstImageBuild()) {
            if (ImageLayerBuildingSupport.buildingInitialLayer()) {
                var registry = CrossLayerConstantRegistry.singletonOrNull();
                registry.registerHeapConstant(SYSTEM_IN_KEY_NAME, runtime.in());
                registry.registerHeapConstant(SYSTEM_OUT_KEY_NAME, runtime.out());
                registry.registerHeapConstant(SYSTEM_ERR_KEY_NAME, runtime.err());
                registry.registerHeapConstant(SYSTEM_INITIAL_IN_KEY_NAME, runtime.initialIn());
                registry.registerHeapConstant(SYSTEM_INITIAL_ERR_KEY_NAME, runtime.initialErr());
            }
            access.registerObjectReplacer(this::replaceStreamsWithRuntimeObject);
        } else {
            var registry = CrossLayerConstantRegistry.singletonOrNull();
            ((FeatureImpl.DuringSetupAccessImpl) access).registerObjectToConstantReplacer(obj -> (ImageHeapConstant) replaceStreamsWithLayerConstant(registry, obj));
        }
    }

    @Override
    public void cleanup() {
        NativeImageSystemIOWrappers.singleton().verifySystemOutErrReplacement();
    }

    Object replaceStreamsWithRuntimeObject(Object object) {
        if (object == hostedIn) {
            return runtime.in();
        } else if (object == hostedOut) {
            return runtime.out();
        } else if (object == hostedErr) {
            return runtime.err();
        } else if (object == hostedInitialErr) {
            return runtime.initialErr();
        } else if (object == hostedInitialIn) {
            return runtime.initialIn();
        } else {
            return object;
        }
    }

    JavaConstant replaceStreamsWithLayerConstant(CrossLayerConstantRegistry registry, Object object) {
        if (object == hostedIn) {
            return registry.getConstant(SYSTEM_IN_KEY_NAME);
        } else if (object == hostedOut) {
            return registry.getConstant(SYSTEM_OUT_KEY_NAME);
        } else if (object == hostedErr) {
            return registry.getConstant(SYSTEM_ERR_KEY_NAME);
        } else if (object == hostedInitialErr) {
            return registry.getConstant(SYSTEM_INITIAL_ERR_KEY_NAME);
        } else if (object == hostedInitialIn) {
            return registry.getConstant(SYSTEM_INITIAL_IN_KEY_NAME);
        } else {
            return null;
        }
    }
}
