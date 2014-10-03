/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle.test.builtins;

import java.util.*;

import com.oracle.graal.truffle.*;
import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * Returns <code>true</code> if a function got inlined for all calls from a given {@link SLFunction}
 * . If no direct calls to the given {@link SLFunction} could be resolved or the call got inlined
 * for some callsites and for some not then an {@link AssertionError} is thrown.
 */
@NodeInfo(shortName = "isInlined")
public abstract class SLIsInlinedBuiltin extends SLGraalRuntimeBuiltin {

    @Specialization
    @SlowPath
    public Object isInlined(SLFunction rootFunction, SLFunction parentFunction, SLFunction inlinedFunction) {
        InliningTrace trace = new InliningTrace();

        for (OptimizedCallTarget target : findDuplicateCallTargets((OptimizedCallTarget) rootFunction.getCallTarget())) {
            if (target.isValid()) {
                searchInlined(trace, target, new ArrayList<>(), parentFunction, inlinedFunction);
            }
        }

        if (trace.allFalse && trace.allTrue) {
            throw new AssertionError(String.format("No optimized calls found from %s to %s .", parentFunction, inlinedFunction));
        } else if (!trace.allFalse && !trace.allTrue) {
            throw new AssertionError(String.format("Some optimized calls from %s to %s are inlined and some are not.", parentFunction, inlinedFunction));
        }
        if (trace.allTrue) {
            return true;
        } else {
            return false;
        }
    }

    private void searchInlined(InliningTrace trace, OptimizedCallTarget rootTarget, List<OptimizedDirectCallNode> stack, SLFunction parent, SLFunction inlinedFunction) {
        OptimizedCallTarget root;
        if (stack.isEmpty()) {
            root = rootTarget;
        } else {
            root = stack.get(stack.size() - 1).getCurrentCallTarget();
        }

        for (OptimizedDirectCallNode callNode : root.getCallNodes()) {
            stack.add(callNode);

            boolean inlined = rootTarget.isInlined(stack);
            if (callNode.getRootNode().getCallTarget() == parent.getCallTarget() && callNode.getCallTarget() == inlinedFunction.getCallTarget()) {
                if (inlined) {
                    trace.allFalse = false;
                } else {
                    trace.allTrue = false;
                }
            }

            if (inlined) {
                searchInlined(trace, rootTarget, stack, parent, inlinedFunction);
            }

            stack.remove(stack.size() - 1);
        }
    }

    private static final class InliningTrace {
        boolean allFalse = true;
        boolean allTrue = true;
    }
}
