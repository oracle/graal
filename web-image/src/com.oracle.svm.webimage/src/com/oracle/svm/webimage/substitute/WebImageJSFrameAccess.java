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

package com.oracle.svm.webimage.substitute;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.webimage.platform.WebImageJSPlatform;
import com.oracle.svm.webimage.platform.WebImageWasmGCPlatform;

import jdk.graal.compiler.debug.GraalError;

/**
 * Stub for frame access. Since we cannot access stack frames in JS, everything throws an error.
 */
public class WebImageJSFrameAccess extends FrameAccess {
    @Override
    public CodePointer readReturnAddress(IsolateThread thread, Pointer sourceSp) {
        throw GraalError.unimplemented("readReturnAddress"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public CodePointer readReturnAddress(StoredContinuation continuation, Pointer sourceSp) {
        throw GraalError.unimplemented("readReturnAddress"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public void writeReturnAddress(IsolateThread thread, Pointer sourceSp, CodePointer newReturnAddress) {
        throw GraalError.unimplemented("writeReturnAddress"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public UnsignedWord getReturnAddressLocation(IsolateThread thread, Pointer sourceSp) {
        throw GraalError.unimplemented("getReturnAddressLocation"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public UnsignedWord getReturnAddressLocation(StoredContinuation continuation, Pointer sourceSp) {
        throw GraalError.unimplemented("getReturnAddressLocation"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public CodePointer unsafeReadReturnAddress(Pointer sourceSp) {
        throw GraalError.unimplemented("unsafeReadReturnAddress"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public Pointer unsafeReturnAddressLocation(Pointer sourceSp) {
        throw GraalError.unimplemented("unsafeReturnAddressLocation"); // ExcludeFromJacocoGeneratedReport
    }
}

@AutomaticallyRegisteredFeature
@Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
class JSFrameAccessFeature implements InternalFeature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(FrameAccess.class, new WebImageJSFrameAccess());
    }
}
