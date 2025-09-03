/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.deopt;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.BuildPhaseProvider.ReadyForCompilation;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.UnknownPrimitiveField;

import jdk.graal.compiler.api.replacements.Fold;

public class DeoptimizationSupport {

    @UnknownPrimitiveField(availability = ReadyForCompilation.class) private CFunctionPointer eagerDeoptStubPointer;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class) private CFunctionPointer lazyDeoptStubPrimitiveReturnPointer;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class) private CFunctionPointer lazyDeoptStubObjectReturnPointer;

    @Platforms(Platform.HOSTED_ONLY.class)
    public DeoptimizationSupport() {
    }

    /**
     * Returns true if the image build was configured with support for deoptimization. In most
     * cases, that happens when support for runtime compilation using Graal is used. However, we
     * also have a few low-level unit tests that test deoptimization in isolation, without Graal
     * compilation.
     *
     * This method can be called as early as during {@link Feature#afterRegistration}.
     */
    @Fold
    public static boolean enabled() {
        return ImageSingletons.contains(DeoptimizationCanaryFeature.class);
    }

    @Fold
    static DeoptimizationSupport get() {
        return ImageSingletons.lookup(DeoptimizationSupport.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setEagerDeoptStubPointer(CFunctionPointer ptr) {
        assert get().eagerDeoptStubPointer == null : "multiple eagerDeoptStub methods registered";
        get().eagerDeoptStubPointer = ptr;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setLazyDeoptStubPrimitiveReturnPointer(CFunctionPointer ptr) {
        assert get().lazyDeoptStubPrimitiveReturnPointer == null : "multiple lazyDeoptStubPrimitiveReturn methods registered";
        assert Deoptimizer.Options.LazyDeoptimization.getValue() : "lazy deoptimization not enabled";
        get().lazyDeoptStubPrimitiveReturnPointer = ptr;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setLazyDeoptStubObjectReturnPointer(CFunctionPointer ptr) {
        assert get().lazyDeoptStubObjectReturnPointer == null : "multiple lazyDeoptStubObjectReturn methods registered";
        assert Deoptimizer.Options.LazyDeoptimization.getValue() : "lazy deoptimization not enabled";
        get().lazyDeoptStubObjectReturnPointer = ptr;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static CFunctionPointer getEagerDeoptStubPointer() {
        CFunctionPointer ptr = get().eagerDeoptStubPointer;
        assert ptr.rawValue() != 0;
        return ptr;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static CFunctionPointer getLazyDeoptStubPrimitiveReturnPointer() {
        assert Deoptimizer.Options.LazyDeoptimization.getValue() : "lazy deoptimization not enabled";
        CFunctionPointer ptr = get().lazyDeoptStubPrimitiveReturnPointer;
        assert ptr.rawValue() != 0;
        return ptr;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static CFunctionPointer getLazyDeoptStubObjectReturnPointer() {
        assert Deoptimizer.Options.LazyDeoptimization.getValue() : "lazy deoptimization not enabled";
        CFunctionPointer ptr = get().lazyDeoptStubObjectReturnPointer;
        assert ptr.rawValue() != 0;
        return ptr;
    }
}
