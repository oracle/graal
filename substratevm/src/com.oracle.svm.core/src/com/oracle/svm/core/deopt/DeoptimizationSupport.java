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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownPrimitiveField;

public class DeoptimizationSupport {

    @UnknownPrimitiveField private CFunctionPointer deoptStubPointer;

    @Platforms(Platform.HOSTED_ONLY.class)
    public DeoptimizationSupport() {
    }

    /**
     * Returns true if the image build was configured with support for deoptimization. In most
     * cases, that happens when support for runtime compilation using Graal is used. However, we
     * also have a few low-level unit tests that test deoptimization in isolation, without Graal
     * compilation.
     */
    @Fold
    public static boolean enabled() {
        return ImageSingletons.contains(DeoptimizationSupport.class);
    }

    @Fold
    static DeoptimizationSupport get() {
        return ImageSingletons.lookup(DeoptimizationSupport.class);
    }

    /**
     * Initializes the pointer to the code of {@link Deoptimizer#deoptStub}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setDeoptStubPointer(CFunctionPointer deoptStub) {
        assert get().deoptStubPointer == null : "multiple deopt stub methods registered";
        get().deoptStubPointer = deoptStub;
    }

    /**
     * Returns a pointer to the code of {@link Deoptimizer#deoptStub}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static CFunctionPointer getDeoptStubPointer() {
        return get().deoptStubPointer;
    }
}
