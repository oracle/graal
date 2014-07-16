/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.verify;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

/**
 * Verifies that no argument to one of the {@link Debug#log(String)} methods is the result of
 * {@link StringBuilder#toString()} or {@link StringBuffer#toString()}. Instead, one of the
 * multi-parameter {@code log()} methods should be used. The goal is to minimize/prevent allocation
 * at logging call sites.
 */
public class VerifyDebugUsage extends VerifyPhase<PhaseContext> {

    @Override
    protected boolean verify(StructuredGraph graph, PhaseContext context) {
        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.class)) {
            ResolvedJavaMethod callee = t.targetMethod();
            ResolvedJavaType debugType = context.getMetaAccess().lookupJavaType(Debug.class);
            if (callee.getDeclaringClass().equals(debugType)) {
                if (callee.getName().equals("log")) {
                    int argIdx = 1;
                    for (ValueNode arg : t.arguments()) {
                        if (arg instanceof Invoke) {
                            Invoke invoke = (Invoke) arg;
                            CallTargetNode callTarget = invoke.callTarget();
                            if (callTarget instanceof MethodCallTargetNode) {
                                ResolvedJavaMethod m = ((MethodCallTargetNode) callTarget).targetMethod();
                                if (m.getName().equals("toString")) {
                                    String holder = m.getDeclaringClass().getName();
                                    if (holder.equals("Ljava/lang/StringBuilder;") || holder.equals("Ljava/lang/StringBuffer;")) {
                                        StackTraceElement e = graph.method().asStackTraceElement(invoke.bci());
                                        throw new VerificationError(String.format("%s: parameter %d of call to %s appears to be a String concatenation expression.%n"
                                                        + "    Use one of the multi-parameter Debug.log() methods or Debug.logv() instead.", e, argIdx, callee.format("%H.%n(%p)")));
                                    }
                                }
                            }
                        }
                        argIdx++;
                    }
                }
            }
        }
        return true;
    }
}
