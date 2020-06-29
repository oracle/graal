/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test.builtins;

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.sl.runtime.SLFunction;
import com.oracle.truffle.sl.runtime.SLNull;

/**
 * Calls a given function until the Graal runtime decides to optimize the function. Use
 * <code>waitForOptimization(function)</code> to wait until the runtime system has completed the
 * possibly parallel optimization.
 *
 * @see SLWaitForOptimizationBuiltin
 */
@NodeInfo(shortName = "callUntilOptimized")
public abstract class SLCallUntilOptimizedBuiltin extends SLGraalRuntimeBuiltin {

    private static final int MAX_CALLS = 10000;
    private static final Object[] EMPTY_ARGS = new Object[0];

    @Child private IndirectCallNode indirectCall = Truffle.getRuntime().createIndirectCallNode();

    @Specialization
    public SLFunction callUntilCompiled(SLFunction function, @SuppressWarnings("unused") SLNull checkTarget) {
        return callUntilCompiled(function, false);
    }

    @Specialization
    public SLFunction callUntilCompiled(SLFunction function, boolean checkTarget) {
        OptimizedCallTarget target = ((OptimizedCallTarget) function.getCallTarget());
        for (int i = 0; i < MAX_CALLS; i++) {
            if (isCompiling(target)) {
                break;
            } else {
                indirectCall.call(target, EMPTY_ARGS);
            }
        }

        // call one more in compiled
        indirectCall.call(target, EMPTY_ARGS);

        if (checkTarget) {
            checkTarget(target);
        }

        return function;
    }

    @TruffleBoundary
    private void checkTarget(OptimizedCallTarget target) throws SLAssertionError {
        if (!target.isValid()) {
            throw new SLAssertionError("Function " + target + " invalidated.", this);
        }
    }

    @TruffleBoundary
    private static boolean isCompiling(OptimizedCallTarget target) {
        return target.isSubmittedForCompilation() || target.isValid();
    }
}
