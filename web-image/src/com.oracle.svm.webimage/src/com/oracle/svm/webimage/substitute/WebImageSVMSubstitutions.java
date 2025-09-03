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

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.stack.JavaFrame;
import com.oracle.svm.webimage.platform.WebImageJSPlatform;
import com.oracle.svm.webimage.platform.WebImageWasmGCPlatform;

import jdk.vm.ci.meta.SpeculationLog;

public class WebImageSVMSubstitutions {
}

@SuppressWarnings("all")
@TargetClass(com.oracle.svm.core.deopt.Deoptimizer.class)
@Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
final class Target_com_oracle_svm_core_deopt_Deoptimizer {

    @Substitute
    public static DeoptimizedFrame checkEagerDeoptimized(IsolateThread thread, Pointer sourceSp) {
        throw new Error("Deoptimization not supported...");
    }

    @Substitute
    public static DeoptimizedFrame checkEagerDeoptimized(JavaFrame frame) {
        throw new Error("Deoptimization not supported...");
    }

    @Substitute
    public static boolean checkLazyDeoptimized(IsolateThread thread, Pointer sourceSp) {
        throw new Error("Deoptimization not supported...");
    }

    @Substitute
    public static boolean checkLazyDeoptimized(JavaFrame frame) {
        throw new Error("Deoptimization not supported...");
    }

    @Substitute
    public static void invalidateMethodOfFrame(IsolateThread thread, Pointer sourceSp, SpeculationLog.SpeculationReason speculation) {
        throw new Error("Deoptimization not supported...");
    }

    @Substitute
    public static void deoptimizeFrame(Pointer sourceSp, boolean ignoreNonDeoptimizable, SpeculationLog.SpeculationReason speculation) {
        throw new Error("Deoptimization not supported...");
    }

    @Substitute
    public static void deoptimizeFrameEagerly(Pointer sourceSp, boolean ignoreNonDeoptimizable, SpeculationLog.SpeculationReason speculation) {
        throw new Error("Deoptimization not supported...");
    }

    @Substitute
    public void deoptSourceFrameLazily(CodePointer pc, boolean ignoreNonDeoptimizable) {
        throw new Error("Deoptimization not supported...");
    }

    @Substitute
    public DeoptimizedFrame deoptSourceFrameEagerly(CodePointer pc, boolean ignoreNonDeoptimizable) {
        throw new Error("Deoptimization not supported...");
    }
}
