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

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.jdk.SystemInOutErrSupport;

/**
 * We use an {@link Feature.DuringSetupAccess#registerObjectReplacer object replacer} because the
 * streams can be cached in other instance and static fields in addition to the fields in
 * {@link System}. We do not know all these places, so we do now know where to place
 * {@link RecomputeFieldValue} annotations.
 */
@AutomaticFeature
public class SystemInOutErrFeature implements Feature {
    private final InputStream hostedIn;
    private final PrintStream hostedOut;
    private final PrintStream hostedErr;

    public SystemInOutErrFeature() {
        hostedIn = System.in;
        NativeImageSystemIOWrappers wrappers = NativeImageSystemIOWrappers.singleton();
        hostedOut = wrappers.outWrapper;
        hostedErr = wrappers.errWrapper;
    }

    private SystemInOutErrSupport runtime;

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        runtime = new SystemInOutErrSupport();
        ImageSingletons.add(SystemInOutErrSupport.class, runtime);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        NativeImageSystemIOWrappers.singleton().verifySystemOutErrReplacement();
        access.registerObjectReplacer(this::replaceStreams);
    }

    @Override
    public void cleanup() {
        NativeImageSystemIOWrappers.singleton().verifySystemOutErrReplacement();
    }

    Object replaceStreams(Object object) {
        if (object == hostedIn) {
            return runtime.in();
        } else if (object == hostedOut) {
            return runtime.out();
        } else if (object == hostedErr) {
            return runtime.err();
        } else {
            return object;
        }
    }
}
