/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import java.lang.reflect.Field;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationAccess;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

@AutomaticallyRegisteredFeature
public class ContinuationsFeature implements InternalFeature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (!Continuation.isSupported()) {
            if (JavaVersionUtil.JAVA_SPEC >= 19) {
                // Can still get initialized and fail the image build despite substitution, so defer
                RuntimeClassInitialization.initializeAtRunTime("jdk.internal.vm.Continuation");
            }
            return;
        }

        if (LoomSupport.isEnabled()) {
            VMError.guarantee(JavaVersionUtil.JAVA_SPEC >= 19);
            ImageSingletons.add(VirtualThreads.class, new LoomVirtualThreads());
        } else if (JavaVersionUtil.JAVA_SPEC == 17) {
            ImageSingletons.add(VirtualThreads.class, new SubstrateVirtualThreads());
        } else {
            /*
             * GR-37518: on 11, ForkJoinPool syncs on a String which doesn't have its own monitor
             * field, and unparking a virtual thread in additionalMonitorsLock.unlock causes a
             * deadlock between carrier thread and virtual thread. 17 uses a ReentrantLock.
             *
             * We intentionally do not advertise non-Loom continuation support on 17.
             */
            throw UserError.abort("Virtual threads are supported only on JDK 19 with preview features enabled (--enable-preview).");
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (Continuation.isSupported()) {
            if (!ImageSingletons.contains(ContinuationSupport.class)) {
                ImageSingletons.add(ContinuationSupport.class, new ContinuationSupport());
            }

            Field ipField = ReflectionUtil.lookupField(StoredContinuation.class, "ip");
            access.registerAsAccessed(ipField);

            access.registerReachabilityHandler(a -> access.registerAsInHeap(StoredContinuation.class),
                            ReflectionUtil.lookupMethod(StoredContinuationAccess.class, "allocate", int.class));
        } else {
            access.registerReachabilityHandler(a -> abortIfUnsupported(), StoredContinuationAccess.class);
            if (JavaVersionUtil.JAVA_SPEC >= 19) {
                access.registerReachabilityHandler(a -> abortIfUnsupported(),
                                ReflectionUtil.lookupMethod(Thread.class, "ofVirtual"),
                                ReflectionUtil.lookupMethod(Thread.class, "startVirtualThread", Runnable.class));
            }
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        if (Continuation.isSupported()) {
            Field ipField = ReflectionUtil.lookupField(StoredContinuation.class, "ip");
            long offset = access.objectFieldOffset(ipField);
            ContinuationSupport.singleton().setIPOffset(offset);
        }
    }

    static void abortIfUnsupported() {
        if (!Continuation.isSupported()) {
            throw UserError.abort("Virtual threads are used in code, but are not currently available or active. Use JDK 19 with preview features enabled (--enable-preview).",
                            SubstrateOptionsParser.commandArgument(SubstrateOptions.SupportContinuations, "+"));
        }
    }
}
