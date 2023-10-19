/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.truffle.phases;

import java.util.function.Predicate;

import jdk.compiler.graal.nodes.AbstractBeginNode;
import jdk.compiler.graal.nodes.FixedWithNextNode;
import jdk.compiler.graal.nodes.Invoke;
import jdk.compiler.graal.nodes.InvokeWithExceptionNode;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.phases.Phase;
import jdk.compiler.graal.truffle.nodes.SpeculativeExceptionAnchorNode;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Instruments the exception edge of TruffleBoundary method calls with a speculative transfer to
 * interpreter.
 */
public class DeoptimizeOnExceptionPhase extends Phase {
    private final Predicate<ResolvedJavaMethod> deoptimizeOnExceptionPredicate;

    public DeoptimizeOnExceptionPhase(Predicate<ResolvedJavaMethod> deoptimizeOnExceptionPredicate) {
        this.deoptimizeOnExceptionPredicate = deoptimizeOnExceptionPredicate;
    }

    @Override
    protected void run(StructuredGraph graph) {
        for (Invoke invoke : graph.getInvokes()) {
            if (invoke instanceof InvokeWithExceptionNode) {
                InvokeWithExceptionNode invokeWithException = (InvokeWithExceptionNode) invoke;
                ResolvedJavaMethod targetMethod = invokeWithException.callTarget().targetMethod();
                if (deoptimizeOnExceptionPredicate.test(targetMethod)) {
                    // Method has @TruffleBoundary(transferToInterpreterOnException=true)
                    // Note: Speculation is inserted during PE.
                    AbstractBeginNode exceptionEdge = invokeWithException.exceptionEdge();
                    FixedWithNextNode newNode = graph.add(new SpeculativeExceptionAnchorNode(DeoptimizationReason.TransferToInterpreter, DeoptimizationAction.InvalidateRecompile, targetMethod));
                    graph.addAfterFixed(exceptionEdge, newNode);
                }
            }
        }
    }
}
