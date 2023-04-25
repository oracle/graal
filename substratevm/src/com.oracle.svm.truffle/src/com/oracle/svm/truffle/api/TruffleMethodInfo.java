/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.api;

import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.truffle.api.CompilerDirectives;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * A collection of all the flags that Truffle needs for partial evaluation. Most of this information
 * comes from annotations on methods. But since we do not want to store annotations in the image
 * heap, we pre-compute all information at image build time. This is also faster than accessing
 * annotations at image run time.
 */
public record TruffleMethodInfo(
                TruffleCompilerRuntime.LoopExplosionKind explosionKind,
                TruffleCompilerRuntime.InlineKind inlineKindPE,
                TruffleCompilerRuntime.InlineKind inlineKindNonPE,
                boolean isInlineable,
                boolean isTruffleBoundary,
                boolean isTruffleBoundaryTransferToInterpreterOnException,
                boolean isSpecializationMethod,
                boolean isBytecodeInterpreterSwitch,
                boolean isBytecodeInterpreterSwitchBoundary,
                boolean isInInterpreter,
                boolean isInInterpreterFastPath,
                boolean isTransferToInterpreterMethod,
                boolean isInliningCutoff) {

    @Platforms(Platform.HOSTED_ONLY.class)
    static TruffleMethodInfo create(TruffleCompilerRuntime runtime, ResolvedJavaMethod method) {
        return new TruffleMethodInfo(
                        runtime.getLoopExplosionKind(method),
                        runtime.getInlineKind(method, true),
                        runtime.getInlineKind(method, false),
                        runtime.isInlineable(method),
                        runtime.isTruffleBoundary(method),
                        computeTruffleBoundaryTransferToInterpreterOnException(method),
                        runtime.isSpecializationMethod(method),
                        runtime.isBytecodeInterpreterSwitch(method),
                        runtime.isBytecodeInterpreterSwitchBoundary(method),
                        runtime.isInInterpreter(method),
                        runtime.isInInterpreterFastPath(method),
                        runtime.isTransferToInterpreterMethod(method),
                        runtime.isInliningCutoff(method));
    }

    private static boolean computeTruffleBoundaryTransferToInterpreterOnException(ResolvedJavaMethod method) {
        CompilerDirectives.TruffleBoundary truffleBoundary = AnnotationAccess.getAnnotation(method, CompilerDirectives.TruffleBoundary.class);
        if (truffleBoundary != null) {
            return truffleBoundary.transferToInterpreterOnException();
        } else {
            return false;
        }
    }
}
