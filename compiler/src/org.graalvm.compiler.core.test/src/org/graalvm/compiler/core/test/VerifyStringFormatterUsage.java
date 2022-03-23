/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.List;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.VerifyPhase;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Verifies that call sites do not eagerly evaluate their arguments by using string concatenation or
 * {@link String#format}, and additionally verifies that no argument is the result of a call to
 * {@link StringBuilder#toString} or {@link StringBuffer#toString}.
 * <p>
 * Useful for calls that may be eliminated, such as to loggers or guarantees (always-on assertions)
 * and to methods with string-formatting capabilities, with which a string built in the caller can
 * be ill-suited as format string and lead to an {@link IllegalFormatException}.
 */
public abstract class VerifyStringFormatterUsage extends VerifyPhase<CoreProviders> {

    @Override
    public boolean checkContract() {
        return false;
    }

    protected void verifyParameters(MethodCallTargetNode callTarget, StructuredGraph callerGraph, NodeInputList<? extends ValueNode> args, ResolvedJavaType stringType, int startArgIdx) {
        if (callTarget.targetMethod().isVarArgs() && args.get(args.count() - 1) instanceof NewArrayNode) {
            // unpack the arguments to the var args
            List<ValueNode> unpacked = new ArrayList<>(args.snapshot());
            NewArrayNode varArgParameter = (NewArrayNode) unpacked.remove(unpacked.size() - 1);
            int firstVarArg = unpacked.size();
            for (Node usage : varArgParameter.usages()) {
                if (usage instanceof StoreIndexedNode) {
                    StoreIndexedNode si = (StoreIndexedNode) usage;
                    unpacked.add(si.value());
                }
            }
            verifyParameters(callerGraph, callTarget, unpacked, stringType, startArgIdx, firstVarArg);
        } else {
            verifyParameters(callerGraph, callTarget, args, stringType, startArgIdx, -1);
        }
    }

    protected void verifyParameters(StructuredGraph callerGraph, MethodCallTargetNode debugCallTarget, List<? extends ValueNode> args, ResolvedJavaType stringType, int startArgIdx, int varArgsIndex) {
        ResolvedJavaMethod verifiedCallee = debugCallTarget.targetMethod();
        int argIdx = startArgIdx;
        int varArgsElementIndex = 0;
        for (int i = 0; i < args.size(); i++) {
            ValueNode arg = args.get(i);
            // Ignore if the argument value is used elsewhere too
            if (arg instanceof Invoke && hasSingleUsage(arg)) {
                boolean reportVarArgs = varArgsIndex >= 0 && argIdx >= varArgsIndex;
                Invoke invoke = (Invoke) arg;
                CallTargetNode callTarget = invoke.callTarget();
                if (callTarget instanceof MethodCallTargetNode) {
                    ResolvedJavaMethod m = callTarget.targetMethod();
                    if (m.getName().equals("toString")) {
                        int bci = invoke.bci();
                        int nonVarArgIdx = reportVarArgs ? argIdx - varArgsElementIndex : argIdx;
                        verifyStringConcat(callerGraph, verifiedCallee, bci, nonVarArgIdx, reportVarArgs ? varArgsElementIndex : -1, m);
                        verifyToStringCall(callerGraph, verifiedCallee, stringType, m, bci, nonVarArgIdx, reportVarArgs ? varArgsElementIndex : -1);
                    } else if (m.getName().equals("format")) {
                        int bci = invoke.bci();
                        int nonVarArgIdx = reportVarArgs ? argIdx - varArgsElementIndex : argIdx;
                        verifyFormatCall(callerGraph, verifiedCallee, stringType, m, bci, nonVarArgIdx, reportVarArgs ? varArgsElementIndex : -1);

                    } else if (m.getName().equals("linkToTargetMethod") &&
                                    (m.getDeclaringClass().getName().equals("Ljava/lang/invoke/Invokers$Holder;") || m.getDeclaringClass().getName().startsWith("Ljava/lang/invoke/LambdaForm$MH"))) {
                        // This is the shape of an indy'fied string concatenation (JDK-8085796)
                        int bci = invoke.bci();
                        StackTraceElement e = callerGraph.method().asStackTraceElement(bci);
                        throw new VerificationError(
                                        "In %s: parameter %d of call to %s appears to be an indy'fied String concatenation expression.", e, argIdx, verifiedCallee.format("%H.%n(%p)"));

                    }
                }
            }
            if (varArgsIndex >= 0 && i >= varArgsIndex) {
                varArgsElementIndex++;
            }
            argIdx++;
        }
    }

    private static boolean hasSingleUsage(Node arg) {
        boolean found = false;
        for (Node usage : arg.usages()) {
            if (!(usage instanceof FrameState)) {
                if (found) {
                    return false;
                }
                found = true;
            }
        }
        return found;
    }

    /**
     * Checks that a given call is not to {@link StringBuffer#toString()} or
     * {@link StringBuilder#toString()}.
     */
    protected static void verifyStringConcat(StructuredGraph callerGraph, ResolvedJavaMethod verifiedCallee, int bci, int argIdx, int varArgsElementIndex, ResolvedJavaMethod callee) {
        if (callee.getDeclaringClass().getName().equals("Ljava/lang/StringBuilder;") || callee.getDeclaringClass().getName().equals("Ljava/lang/StringBuffer;")) {
            StackTraceElement e = callerGraph.method().asStackTraceElement(bci);
            if (varArgsElementIndex >= 0) {
                throw new VerificationError(
                                "In %s: element %d of parameter %d of call to %s appears to be a String concatenation expression.%n", e, varArgsElementIndex, argIdx,
                                verifiedCallee.format("%H.%n(%p)"));
            } else {
                throw new VerificationError(
                                "In %s: parameter %d of call to %s appears to be a String concatenation expression.", e, argIdx, verifiedCallee.format("%H.%n(%p)"));
            }
        }
    }

    /**
     * Checks that a given call is not to {@link Object#toString()}.
     */
    protected static void verifyToStringCall(StructuredGraph callerGraph, ResolvedJavaMethod verifiedCallee, ResolvedJavaType stringType, ResolvedJavaMethod callee, int bci, int argIdx,
                    int varArgsElementIndex) {
        if (callee.getSignature().getParameterCount(false) == 0 && callee.getSignature().getReturnType(callee.getDeclaringClass()).equals(stringType)) {
            StackTraceElement e = callerGraph.method().asStackTraceElement(bci);
            if (varArgsElementIndex >= 0) {
                throw new VerificationError(
                                "In %s: element %d of parameter %d of call to %s is a call to toString() which is redundant (the callee will do it) and forces unnecessary eager evaluation.",
                                e, varArgsElementIndex, argIdx, verifiedCallee.format("%H.%n(%p)"));
            } else {
                throw new VerificationError("In %s: parameter %d of call to %s is a call to toString() which is redundant (the callee will do it) and forces unnecessary eager evaluation.", e, argIdx,
                                verifiedCallee.format("%H.%n(%p)"));
            }
        }
    }

    /**
     * Checks that a given call is not to {@link String#format(String, Object...)} or
     * {@link String#format(java.util.Locale, String, Object...)}.
     */
    protected static void verifyFormatCall(StructuredGraph callerGraph, ResolvedJavaMethod verifiedCallee, ResolvedJavaType stringType, ResolvedJavaMethod callee, int bci, int argIdx,
                    int varArgsElementIndex) {
        if (callee.getDeclaringClass().equals(stringType) && callee.getSignature().getReturnType(callee.getDeclaringClass()).equals(stringType)) {
            StackTraceElement e = callerGraph.method().asStackTraceElement(bci);
            if (varArgsElementIndex >= 0) {
                throw new VerificationError(
                                "In %s: element %d of parameter %d of call to %s is a call to String.format() which is redundant (%s does formatting) and forces unnecessary eager evaluation.",
                                e, varArgsElementIndex, argIdx, verifiedCallee.format("%H.%n(%p)"), verifiedCallee.format("%h.%n"));
            } else {
                throw new VerificationError("In %s: parameter %d of call to %s is a call to String.format() which is redundant (%s does formatting) and forces unnecessary eager evaluation.", e,
                                argIdx,
                                verifiedCallee.format("%H.%n(%p)"), verifiedCallee.format("%h.%n"));
            }
        }
    }
}
