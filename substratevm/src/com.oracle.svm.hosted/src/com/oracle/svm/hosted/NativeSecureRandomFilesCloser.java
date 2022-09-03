/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.security.SecureRandom;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.PosixSunSecuritySubstitutions;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;

/**
 * The {@code NativePRNG} backend for {@link SecureRandom} on Linux and Darwin opens file handles
 * for {@code /dev/random} and {@code /dev/urandom} without ever closing them. This leak can cause a
 * native image to hit the open files limit when it repeatedly spawns isolates, so we close these
 * handles on isolate tear-down.
 *
 * As of Java 11, there is only a dummy implementation of {@code NativePRNG} on Windows which does
 * not open file descriptors that would need to be closed.
 */
@AutomaticallyRegisteredFeature
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
public class NativeSecureRandomFilesCloser implements InternalFeature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        access.registerReachabilityHandler(this::registerShutdownHook, sun.security.provider.NativePRNG.class);
    }

    private void registerShutdownHook(DuringAnalysisAccess a) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;
        RuntimeSupport.Hook hook = PosixSunSecuritySubstitutions.getTearDownHook();
        RuntimeSupport.getRuntimeSupport().addTearDownHook(hook);
        access.rescanObject(hook);
    }
}
