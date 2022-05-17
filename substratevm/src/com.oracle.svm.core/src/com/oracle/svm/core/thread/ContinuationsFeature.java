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

import java.util.concurrent.ForkJoinPool;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationImpl;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.util.ReflectionUtil;

@AutomaticFeature
@Platforms(Platform.HOSTED_ONLY.class)
public class ContinuationsFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (Continuation.isSupported()) {
            VirtualThreads impl;
            if (LoomSupport.isEnabled()) {
                impl = new LoomVirtualThreads();
            } else {
                /*
                 * GR-37518: ForkJoinPool on 11 syncs on a String which doesn't have its own monitor
                 * field, and unparking a virtual thread in additionalMonitorsLock.unlock causes a
                 * deadlock between carrier thread and virtual thread. 17 uses a ReentrantLock.
                 */
                UserError.guarantee(JavaVersionUtil.JAVA_SPEC >= 17, "Continuations (%s) are currently supported only on JDK 17 and later.",
                                SubstrateOptionsParser.commandArgument(SubstrateOptions.SupportContinuations, "+"));

                impl = new SubstrateVirtualThreads();
            }
            ImageSingletons.add(VirtualThreads.class, impl);
        } else {
            UserError.guarantee(!SubstrateOptions.UseLoom.getValue(), "%s cannot be enabled without option %s.",
                            SubstrateOptionsParser.commandArgument(SubstrateOptions.UseLoom, "+"),
                            SubstrateOptionsParser.commandArgument(SubstrateOptions.SupportContinuations, "+"));
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (Continuation.isSupported()) {
            access.registerReachabilityHandler(a -> access.registerAsInHeap(StoredContinuation.class),
                            ReflectionUtil.lookupMethod(StoredContinuationImpl.class, "allocate", int.class));

            if (LoomSupport.isEnabled()) {
                RuntimeReflection.register(ReflectionUtil.lookupMethod(ForkJoinPool.class, "compensatedBlock", ForkJoinPool.ManagedBlocker.class));
            }
        } else {
            access.registerReachabilityHandler(a -> abortIfUnsupported(), StoredContinuationImpl.class);
        }
    }

    static void abortIfUnsupported() {
        if (!Continuation.isSupported()) {
            throw UserError.abort("Continuation support is used, but not enabled. Use options %s or %s.",
                            SubstrateOptionsParser.commandArgument(SubstrateOptions.SupportContinuations, "+"),
                            SubstrateOptionsParser.commandArgument(SubstrateOptions.UseLoom, "+"));
        }
    }
}
