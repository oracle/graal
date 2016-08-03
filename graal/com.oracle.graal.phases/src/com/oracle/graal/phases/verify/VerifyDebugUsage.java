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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugMethodMetrics;
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeInputList;
import com.oracle.graal.nodes.CallTargetNode;
import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.java.MethodCallTargetNode;
import com.oracle.graal.nodes.java.NewArrayNode;
import com.oracle.graal.nodes.java.StoreIndexedNode;
import com.oracle.graal.phases.VerifyPhase;
import com.oracle.graal.phases.tiers.PhaseContext;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 *
 * Verifies that callsites calling one of the methods in {@link Debug} use them correctly. Correct
 * usage of the methods in {@link Debug} requires callsites to not eagerly evaluate their arguments.
 * Additionally this phase verifies that no argument is the result of a call to
 * {@link StringBuilder#toString()} or {@link StringBuffer#toString()}. Ideally the parameters at
 * callsites of {@link Debug} are eliminated, and do not produce additional allocations, if
 * {@link Debug#isDumpEnabled(int)} (or {@link Debug#isLogEnabled(int)}, ...) is {@code false}.
 *
 * Methods in {@link Debug} checked by this phase are various different versions of
 * {@link Debug#log(String)} , {@link Debug#dump(int, Object, String)},
 * {@link Debug#logAndIndent(String)} and {@link Debug#verify(Object, String)}.
 */
public class VerifyDebugUsage extends VerifyPhase<PhaseContext> {

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    protected boolean verify(StructuredGraph graph, PhaseContext context) {
        ResolvedJavaType debugType = context.getMetaAccess().lookupJavaType(Debug.class);
        ResolvedJavaType nodeType = context.getMetaAccess().lookupJavaType(Node.class);
        ResolvedJavaType stringType = context.getMetaAccess().lookupJavaType(String.class);
        ResolvedJavaType debugMethodMetricsType = context.getMetaAccess().lookupJavaType(DebugMethodMetrics.class);
        ResolvedJavaType graalErrorType = context.getMetaAccess().lookupJavaType(GraalError.class);

        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            String calleeName = callee.getName();
            if (callee.getDeclaringClass().equals(debugType)) {
                if (calleeName.equals("log") || calleeName.equals("logAndIndent") || calleeName.equals("verify") || calleeName.equals("dump")) {
                    verifyParameters(t, graph, t.arguments(), stringType, calleeName.equals("dump") ? 2 : 1);
                }
            }
            if (callee.getDeclaringClass().isAssignableFrom(nodeType)) {
                if (calleeName.equals("assertTrue") || calleeName.equals("assertFalse")) {
                    verifyParameters(t, graph, t.arguments(), stringType, 1);
                }
            }
            if (callee.getDeclaringClass().equals(debugMethodMetricsType)) {
                if (calleeName.equals("addToMetric") || calleeName.equals("getCurrentMetricValue") || calleeName.equals("incrementMetric")) {
                    verifyParameters(t, graph, t.arguments(), stringType, 1);
                }
            }
            if (callee.getDeclaringClass().isAssignableFrom(graalErrorType) && !graph.method().getDeclaringClass().isAssignableFrom(graalErrorType)) {
                if (calleeName.equals("guarantee")) {
                    verifyParameters(t, graph, t.arguments(), stringType, 1);
                }
                if (calleeName.equals("<init>") && callee.getSignature().getParameterCount(false) == 2) {
                    verifyParameters(t, graph, t.arguments(), stringType, 1);
                }
            }
        }
        return true;
    }

    private static void verifyParameters(MethodCallTargetNode callTarget, StructuredGraph graph, NodeInputList<? extends Node> args, ResolvedJavaType stringType, int startArgIdx) {
        // TRANSIENT end VARARGS modifiers share the same encoding, for methods VARARGS is set if
        // they have var arg parameters
        if (Modifier.isTransient(callTarget.targetMethod().getModifiers()) && args.get(args.count() - 1) instanceof NewArrayNode) {
            // unpack the arguments to the var args
            List<Node> unpacked = new ArrayList<>(args.snapshot());
            NewArrayNode varArgParameter = (NewArrayNode) unpacked.remove(unpacked.size() - 1);
            for (Node usage : varArgParameter.usages()) {
                if (usage instanceof StoreIndexedNode) {
                    StoreIndexedNode si = (StoreIndexedNode) usage;
                    unpacked.add(si.value());
                }
            }
            verifyParameters(graph, unpacked, stringType, startArgIdx);
        } else {
            verifyParameters(graph, args.snapshot(), stringType, startArgIdx);
        }
    }

    private static void verifyParameters(StructuredGraph graph, List<? extends Node> args, ResolvedJavaType stringType, int startArgIdx) {
        int argIdx = startArgIdx;
        for (Node arg : args) {
            if (arg instanceof Invoke) {
                Invoke invoke = (Invoke) arg;
                CallTargetNode callTarget = invoke.callTarget();
                if (callTarget instanceof MethodCallTargetNode) {
                    ResolvedJavaMethod m = ((MethodCallTargetNode) callTarget).targetMethod();
                    if (m.getName().equals("toString")) {
                        int bci = invoke.bci();
                        verifyStringConcat(graph, bci, argIdx, m);
                        verifyToStringCall(graph, stringType, m, bci, argIdx);
                    }
                }
            }
            argIdx++;
        }
    }

    private static void verifyStringConcat(StructuredGraph graph, int bci, int argIdx, ResolvedJavaMethod callee) {
        if (callee.getDeclaringClass().getName().equals("Ljava/lang/StringBuilder;") || callee.getDeclaringClass().getName().equals("Ljava/lang/StringBuffer;")) {
            StackTraceElement e = graph.method().asStackTraceElement(bci);
            throw new VerificationError(
                            "%s: parameter %d of call to %s appears to be a String concatenation expression.%n" + "    Use one of the multi-parameter Debug.log() methods or Debug.logv() instead.", e,
                            argIdx, callee.format("%H.%n(%p)"));
        }
    }

    private static void verifyToStringCall(StructuredGraph graph, ResolvedJavaType stringType, ResolvedJavaMethod callee, int bci, int argIdx) {
        if (callee.getSignature().getParameterCount(false) == 0 && callee.getSignature().getReturnType(callee.getDeclaringClass()).equals(stringType)) {
            StackTraceElement e = graph.method().asStackTraceElement(bci);
            throw new VerificationError("%s: parameter %d of call to %s is a call to toString() which is redundant (the callee will do it) and forces unnecessary eager evaluation.", e, argIdx,
                            callee.format("%H.%n(%p)"));
        }
    }
}
