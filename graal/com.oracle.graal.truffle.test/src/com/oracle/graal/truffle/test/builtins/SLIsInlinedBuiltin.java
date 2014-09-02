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
    public Object isInlined(SLFunction parent, SLFunction inlinedFunction) {
        boolean allFalse = true;
        boolean allTrue = true;
        for (OptimizedCallTarget parentTarget : findDuplicateCallTargets((OptimizedCallTarget) parent.getCallTarget())) {
            Set<DirectCallNode> callNodes = findCallsTo(parentTarget.getRootNode(), (OptimizedCallTarget) inlinedFunction.getCallTarget());
            for (DirectCallNode directCall : callNodes) {
                if (directCall.isInlined()) {
                    allFalse = false;
                } else {
                    allTrue = false;
                }
            }
        }
        if (allFalse && allTrue) {
            throw new AssertionError(String.format("No calls found from %s to %s .", parent, inlinedFunction));
        } else if (!allFalse && !allTrue) {
            throw new AssertionError(String.format("Some calls from %s to %s are inlined and some are not.", parent, inlinedFunction));
        }
        if (allTrue) {
            return true;
        } else {
            return false;
        }
    }
}
