/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.FrameInfoEncoder.ValueRetentionPolicy;
import com.oracle.svm.core.interpreter.InterpreterSupport;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.graal.compiler.nodes.FrameState;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Selects frame-value retention policies for hosted compilation and runtime metadata encoding.
 * {@link NativeImageCodeCache} separately decides whether to encode value information for a frame
 * chain; this class determines which individual values must survive compilation.
 */
public final class FrameInfoRetention {
    private FrameInfoRetention() {
    }

    /**
     * Selects the policy used to prune graph frame states before LIR generation. The checks must
     * remain consistent with {@link #getValueRetentionPolicy(HostedMethod, boolean, BytecodeFrame)},
     * which verifies retention using the frame representation available during metadata encoding.
     *
     * @param method the compilation root
     * @param topFrame the innermost frame state in the inlining chain
     * @return the handler policy if pruning is permitted for the entire chain, otherwise
     *         {@link ValueRetentionPolicy#ALL}
     */
    public static ValueRetentionPolicy getValueRetentionPolicy(HostedMethod method, FrameState topFrame) {
        if (!canPruneFrameStateValues(method)) {
            return ValueRetentionPolicy.ALL;
        }
        for (FrameState frame = topFrame; frame != null; frame = frame.outerFrameState()) {
            if (SubstrateCompilationDirectives.singleton().isFrameInformationRequired(frame.getMethod())) {
                return ValueRetentionPolicy.ALL;
            }
        }
        return InterpreterSupport.singleton().getBytecodeHandlerValueRetentionPolicy();
    }

    /**
     * Selects the policy used to verify retained values during metadata encoding. This repeats the
     * checks in {@link #getValueRetentionPolicy(HostedMethod, FrameState)} over the emitted bytecode
     * frame chain instead of graph frame states, and retains all values for deoptimization entries.
     *
     * @param method the compilation root
     * @param isDeoptEntry whether the infopoint is a deoptimization entry point
     * @param topFrame the innermost bytecode frame in the inlining chain
     * @return the handler policy if pruning is permitted for the entire chain, otherwise
     *         {@link ValueRetentionPolicy#ALL}
     */
    public static ValueRetentionPolicy getValueRetentionPolicy(HostedMethod method, boolean isDeoptEntry, BytecodeFrame topFrame) {
        if (isDeoptEntry || !canPruneFrameStateValues(method)) {
            return ValueRetentionPolicy.ALL;
        }
        for (BytecodeFrame frame = topFrame; frame != null; frame = frame.caller()) {
            if (SubstrateCompilationDirectives.singleton().isFrameInformationRequired(frame.getMethod())) {
                return ValueRetentionPolicy.ALL;
            }
        }
        return InterpreterSupport.singleton().getBytecodeHandlerValueRetentionPolicy();
    }

    /**
     * Returns whether the compilation root permits pruning. Individual frame chains may still
     * require all values when frame information was explicitly requested.
     */
    public static boolean canPruneFrameStateValues(HostedMethod method) {
        if (!isInterpreterBytecodeHandlerStub(method)) {
            return false;
        }
        assert !method.canDeoptimize() && !method.isDeoptTarget() : method;
        return !SubstrateOptions.useDebugInfoGeneration() && !SubstrateOptions.getSourceLevelDebug();
    }

    private static boolean isInterpreterBytecodeHandlerStub(ResolvedJavaMethod method) {
        return InterpreterSupport.isEnabled() && InterpreterSupport.singleton().isInterpreterBytecodeHandlerStub(method);
    }
}
