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
        VirtualThreads impl;
        if (JavaContinuations.isSupported()) {
            if (LoomSupport.isEnabled()) {
                impl = new LoomVirtualThreads();
            } else {
                impl = new SubstrateVirtualThreads();
            }
        } else {
            impl = new NoVirtualThreads();
            UserError.guarantee(!SubstrateOptions.UseLoom.getValue(), SubstrateOptionsParser.commandArgument(SubstrateOptions.UseLoom, "+") + " cannot be enabled without option " +
                            SubstrateOptionsParser.commandArgument(SubstrateOptions.SupportContinuations, "+"));
        }
        ImageSingletons.add(VirtualThreads.class, impl);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (JavaContinuations.isSupported()) {
            access.registerAsInHeap(StoredContinuation.class);

            if (LoomSupport.isEnabled()) {
                RuntimeReflection.register(ReflectionUtil.lookupMethod(ForkJoinPool.class, "compensatedBlock", ForkJoinPool.ManagedBlocker.class));
            }
        } else {
            access.registerReachabilityHandler(a -> UserError.abort(
                            "Continuation support is used, but not enabled. Use options " +
                                            SubstrateOptionsParser.commandArgument(SubstrateOptions.SupportContinuations, "+") +
                                            " or " + SubstrateOptionsParser.commandArgument(SubstrateOptions.UseLoom, "+") + "."),
                            StoredContinuationImpl.class,
                            ReflectionUtil.lookupMethod(NoVirtualThreads.class, "unreachable"));
        }
    }
}
