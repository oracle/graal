/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jdk;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.misc.Unsafe;

/**
 * The atomic field updaters access fields using {@code Unsafe}. The static analysis needs to know
 * about all these fields, so we need to find the original field (the updater only stores the field
 * offset) and mark it as unsafe accessed.
 */
@AutomaticallyRegisteredFeature
class AtomicFieldUpdaterFeature implements InternalFeature {

    private boolean sealed = false;

    @Override
    public void duringSetup(DuringSetupAccess access) {
        FeatureImpl.DuringSetupAccessImpl config = (FeatureImpl.DuringSetupAccessImpl) access;
        config.registerObjectReachableCallback(AtomicReferenceFieldUpdater.class, this::processFieldUpdater);
        config.registerObjectReachableCallback(AtomicIntegerFieldUpdater.class, this::processFieldUpdater);
        config.registerObjectReachableCallback(AtomicLongFieldUpdater.class, this::processFieldUpdater);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        sealed = true;
    }

    /*
     * This code runs multi-threaded during the static analysis. It must not be called after static
     * analysis, because that would mean that we missed an atomic field updater during static
     * analysis.
     */
    private void processFieldUpdater(DuringAnalysisAccess access, Object updater, ObjectScanner.ScanReason reason) {
        VMError.guarantee(!sealed, "New atomic field updater found after static analysis");

        Class<?> updaterClass = updater.getClass();
        Class<?> tclass = ReflectionUtil.readField(updaterClass, "tclass", updater);
        long searchOffset = ReflectionUtil.readField(updaterClass, "offset", updater);
        // search the declared fields for a field with a matching offset
        for (Field f : tclass.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                long fieldOffset = Unsafe.getUnsafe().objectFieldOffset(f);
                if (fieldOffset == searchOffset) {
                    ((FeatureImpl.DuringAnalysisAccessImpl) access).registerAsUnsafeAccessed(f, reason);
                    return;
                }
            }
        }
        throw VMError.shouldNotReachHere("unknown field offset class: " + tclass + ", offset = " + searchOffset);
    }
}
