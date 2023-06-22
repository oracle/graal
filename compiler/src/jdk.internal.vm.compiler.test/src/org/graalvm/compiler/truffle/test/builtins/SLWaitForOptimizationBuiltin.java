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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.sl.runtime.SLFunction;
import com.oracle.truffle.sl.runtime.SLNull;

/**
 * Waits for the optimization of a function to complete if it was already triggered. If no
 * optimization was triggered then this builtin does nothing.
 */
@NodeInfo(shortName = "waitForOptimization")
public abstract class SLWaitForOptimizationBuiltin extends SLGraalRuntimeBuiltin {

    @Specialization
    public SLFunction waitForOptimization(SLFunction function, long timeout) {
        OptimizedCallTarget target = (OptimizedCallTarget) function.getCallTarget();
        GraalTruffleRuntime runtime = ((GraalTruffleRuntime) Truffle.getRuntime());
        try {
            runtime.waitForCompilation(target, timeout);
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
        return function;
    }

    @Specialization
    public SLFunction waitForCompilation(SLFunction function, @SuppressWarnings("unused") SLNull timeout) {
        return waitForOptimization(function, 640000);
    }

}
