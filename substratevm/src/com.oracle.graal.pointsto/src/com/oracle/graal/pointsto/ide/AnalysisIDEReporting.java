/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.ide;

import static jdk.graal.compiler.ide.IDEReport.getFilePath;
import static jdk.graal.compiler.ide.IDEReport.getInliningTrace;
import static jdk.graal.compiler.ide.IDEReport.getLineNumber;
import static jdk.graal.compiler.ide.IDEReport.runIfEnabled;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.PointsToAnalysisField;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.typestate.PrimitiveConstantTypeState;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.ide.IDEReport;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Utility class providing methods for reporting analysis results to an IDE.
 * <p>
 * The methods in this class are used to report various analysis results, such as unreachable code,
 * devirtualized invocations, and constant parameter and return types, to an IDE.
 *
 * @see IDEReport
 */
public class AnalysisIDEReporting {

    private AnalysisIDEReporting() {
    }

    /**
     * Reports an unreachable branch in the given {@link IfNode}.
     *
     * @param ifNode the IfNode containing the unreachable branch
     * @param unreachableBranch the unreachable branch node
     */
    public static void reportUnreachableBranch(IfNode ifNode, AbstractBeginNode unreachableBranch, boolean isNullCheck, boolean isTypeCheck) {
        var isExplicitCheck = !isImplicitCheck(ifNode);
        if (isExplicitCheck || (!isNullCheck && !isTypeCheck)) {
            runIfEnabled(ideReport -> {
                var srcPos = ifNode.getNodeSourcePosition();
                if (srcPos == null) {
                    return;
                }
                var srcFile = getFilePath(srcPos.getMethod());
                var range = rangeOfBranch(unreachableBranch, ifNode);
                if (range != null) {
                    var className = srcPos.getMethod().getDeclaringClass().toJavaName();
                    ideReport.saveUnreachableRangeReport(srcFile, className, range.startLine(), range.endLine(), "Branch is unreachable", getInliningTrace(srcPos));
                }
            });
        }
    }

    /**
     * Reports an unreachable node in the graph.
     *
     * @param unreachableNode the unreachable node
     */
    public static void reportUnreachableNode(Node unreachableNode) {
        if (unreachableNode == null) {
            return;
        }
        runIfEnabled(ideReport -> {
            var srcPos = unreachableNode.getNodeSourcePosition();
            if (srcPos == null) {
                return;
            }
            var srcFile = getFilePath(srcPos.getMethod());
            var range = rangeOfBranch(unreachableNode, unreachableNode.graph().getNode(0));
            if (range != null) {
                var className = srcPos.getMethod().getDeclaringClass().toJavaName();
                ideReport.saveUnreachableRangeReport(srcFile, className, range.startLine(), range.endLine(), "Code is unreachable", getInliningTrace(srcPos));
            }
        });
    }

    /**
     * Reports a devirtualized method invocation.
     *
     * @param method the method containing the invocation
     * @param invoke the invocation node
     * @param singleCallee the single callee method
     */
    public static void reportDevirtualizedInvoke(PointsToAnalysisMethod method, Invoke invoke, AnalysisMethod singleCallee) {
        runIfEnabled(ideReport -> {
            var targetMethod = invoke.getTargetMethod();
            if (!(targetMethod instanceof AnalysisMethod)) {
                targetMethod = method.getUniverse().lookup(targetMethod);
            }
            if (singleCallee.equals(targetMethod)) {
                /* Report would add no additional value in this case */
                return;
            }
            var srcFile = getFilePath(invoke.getContextMethod());
            var nodeSourcePosition = ((InvokeWithExceptionNode) invoke).getNodeSourcePosition();
            var className = nodeSourcePosition.getMethod().getDeclaringClass().toJavaName();
            int line = getLineNumber(nodeSourcePosition);
            ideReport.saveLineReport(srcFile, className, line, "Invocation of " + singleCallee.getName() + " has been devirtualized to " + singleCallee.getQualifiedName(),
                            getInliningTrace(nodeSourcePosition));
        });
    }

    /**
     * Reports any constant parameter and return types for the given method.
     *
     * @param m the method to report on
     */
    public static void reportConstantParamAndReturnTypes(AnalysisMethod m) {
        if (!(m instanceof PointsToAnalysisMethod method)) {
            /* We only support reports from points-to analysis at the moment. */
            return;
        }
        var lineNumberTable = method.getLineNumberTable();
        if (lineNumberTable == null) {
            return;
        }
        runIfEnabled(ideReport -> {
            var line = lineNumberTable.getLineNumbers()[0] - 1;
            var filename = getFilePath(method);
            var paramFlows = method.getTypeFlow().getMethodFlowsGraph().getParameters();
            ResolvedJavaMethod.Parameter[] params = null;
            try {
                params = method.getParameters();
            } catch (UnsupportedOperationException | NoClassDefFoundError ignored) {
                /* Ignore errors coming, e.g., from NonBytecodeMethods, and linking errors. */
            }
            boolean shiftParamsToAccountForReceiver;
            if (params == null || params.length == paramFlows.length) {
                shiftParamsToAccountForReceiver = false;
            } else if (params.length == paramFlows.length - 1) {
                shiftParamsToAccountForReceiver = true;
            } else {
                params = null;
                shiftParamsToAccountForReceiver = false;
            }
            for (var i = 0; i < paramFlows.length; i++) {
                var paramFlow = paramFlows[i];
                if (paramFlow != null) {
                    String paramDescr;
                    if (i == 0 && shiftParamsToAccountForReceiver) {
                        paramDescr = "Receiver";
                    } else {
                        var paramIdx = shiftParamsToAccountForReceiver ? i - 1 : i;
                        var paramName = params != null ? params[paramIdx].getName() : ("arg" + paramIdx);
                        paramDescr = "Parameter " + paramName;
                    }
                    reportParamOrReturnType(paramFlow, paramDescr, method, filename, method.getQualifiedName(), line, ideReport);
                }
            }
            reportParamOrReturnType(method.getTypeFlow().getReturn(), "Return value", method, filename, method.getQualifiedName(), line, ideReport);
        });
    }

    /**
     * Reports possible return types for the given invocation.
     *
     * @param invoke the invocation node
     * @param callees the possible callee methods
     */
    public static void reportPossibleReturnTypes(Invoke invoke, Collection<AnalysisMethod> callees) {
        runIfEnabled(ideReport -> {
            var possibleReturnValues = new HashSet<String>();
            var possibleReturnTypes = new HashSet<AnalysisType>();
            foreachCallee: for (var calleeRaw : callees) {
                var callee = (PointsToAnalysisMethod) calleeRaw;
                var returnFlow = callee.getTypeFlow().getReturn();
                if (returnFlow == null) {
                    continue foreachCallee;
                }
                var constValue = extractConstant(returnFlow);
                if (constValue != null) {
                    possibleReturnValues.add(constValue);
                } else if (!returnFlow.isPrimitiveFlow()) {
                    var exactType = returnFlow.getState().exactType();
                    if (exactType == null) {
                        continue foreachCallee;
                    }
                    possibleReturnTypes.add(exactType);
                }
            }
            var staticReturnType = invoke.getTargetMethod().getSignature().getReturnType(invoke.getContextType());
            possibleReturnTypes.removeIf(rt -> rt.toJavaName().equals(staticReturnType.toJavaName()));
            var filePath = getFilePath(invoke.getContextMethod());
            var srcPos = invoke.asNode().getNodeSourcePosition();
            var line = getLineNumber(srcPos);
            var mthName = invoke.getTargetMethod().getName();
            String msg;
            if (possibleReturnValues.size() == 1) {
                msg = mthName + " always returns " + possibleReturnValues.iterator().next();
            } else if (possibleReturnTypes.size() == 1) {
                msg = mthName + " always returns a value of type " + possibleReturnTypes.iterator().next().toJavaName();
            } else if (!possibleReturnValues.isEmpty() && possibleReturnTypes.isEmpty()) {
                msg = mthName + " always returns one of the following values: " + String.join(", ", possibleReturnValues);
            } else if (!possibleReturnTypes.isEmpty()) {
                msg = mthName + " always returns a value of one of the following types: " + new TreeSet<>(possibleReturnTypes).stream().map(AnalysisType::toJavaName).collect(Collectors.joining(", "));
            } else {
                return;
            }
            var className = srcPos.getMethod().getDeclaringClass().toJavaName();
            ideReport.saveLineReport(filePath, className, line, msg, getInliningTrace(srcPos));
        });
    }

    private static void reportParamOrReturnType(TypeFlow<?> paramOrReturnFlow, String flowPositionDescr, PointsToAnalysisMethod mth, String filename, String className, int line, IDEReport ideReport) {
        if (paramOrReturnFlow == null) {
            return;
        }
        var constValue = extractConstant(paramOrReturnFlow);
        if (constValue != null) {
            ideReport.saveLineReport(filename, className, line, flowPositionDescr + " of method " + mth.getName() + " is constant: " + constValue, null);
        } else if (!paramOrReturnFlow.isPrimitiveFlow()) {
            var staticType = resolveToAnalysisType(paramOrReturnFlow.getDeclaredType(), mth.getUniverse());
            var exactType = paramOrReturnFlow.getState().exactType();
            if (exactType != null && !exactType.equals(staticType)) {
                ideReport.saveLineReport(filename, className, line, flowPositionDescr + " of method " + mth.getName() + " has actual type " + exactType.toJavaName(), null);
            }
        }
    }

    /**
     * Reports a constant field.
     *
     * @param field the field to report on
     */
    public static void maybeReportConstantField(AnalysisField field) {
        runIfEnabled(ideReport -> {
            var fieldSinkFlow = ((PointsToAnalysisField) field).getSinkFlow();
            if (field.getDeclaringClass().getSourceFileName() == null) {
                return;
            }
            var filePath = getFilePath(field.getDeclaringClass());
            var constant = extractConstant(fieldSinkFlow);
            if (constant != null) {
                ideReport.saveFieldReport(filePath, field.getDeclaringClass().toJavaName(), field.getName(), "Field " + field.getName() + " has constant value " + constant);
            } else if (field.getStorageKind().isObject()) {
                var exactType = fieldSinkFlow.getState().exactType();
                if (exactType != null && !exactType.equals(field.getType())) {
                    ideReport.saveFieldReport(filePath, field.getDeclaringClass().toJavaName(), field.getName(),
                                    "Values of field " + field.getName() + " are always of type " + exactType.toJavaName());
                }
            }
        });
    }

    private static String extractConstant(TypeFlow<?> flow) {
        if (flow.isSaturated()) {
            return null;
        }
        var typeState = flow.getState();
        if (typeState instanceof PrimitiveConstantTypeState cstTypeState) {
            var value = cstTypeState.getValue();
            if (flow.getDeclaredType().getJavaKind().equals(JavaKind.Boolean)) {
                return value == 0 ? "false" : "true";
            }
            return Long.toString(value);
        }
        var constant = typeState.asConstant();
        if (constant == null) {
            return null;
        }
        var type = typeState.exactType();
        if (type == null) {
            type = flow.getDeclaredType();
        }
        if (type != null && type.toJavaName().equals("java.lang.String")) {
            return "\"" + constant.toValueString() + "\"";
        }
        return constant.toValueString();
    }

    private static Range rangeOfBranch(Node branchBegin, Node decisionNode) {
        var rangeLines = linesOf(nodesReachableFrom(branchBegin, null));
        rangeLines.removeAll(linesOf(nodesReachableFrom(decisionNode, branchBegin)));
        if (rangeLines.isEmpty()) {
            return null;
        }
        var start = rangeLines.stream().mapToInt(x -> x).min().getAsInt();
        var end = start;
        while (rangeLines.contains(end)) {
            end++;
        }
        return new Range(start, end - 1);
    }

    private static Set<Integer> linesOf(Set<Node> allNodes) {
        return allNodes.stream()
                        .filter(n -> n.getNodeSourcePosition() != null)
                        .map(n -> getLineNumber(n.getNodeSourcePosition()))
                        .filter(l -> l > 0)
                        .collect(Collectors.toSet());
    }

    private static Set<Node> nodesReachableFrom(Node start, Node skippedNode) {
        // BFS
        var reachableNodes = new HashSet<Node>();
        var worklist = new LinkedList<Node>();
        worklist.addLast(start);
        while (!worklist.isEmpty()) {
            var curr = worklist.removeFirst();
            if (curr != skippedNode && reachableNodes.add(curr)) {
                for (var succ : curr.successors()) {
                    worklist.addLast(succ);
                }
            }
        }
        return reachableNodes;
    }

    private record Range(int startLine, int endLine) {
    }

    private static AnalysisType resolveToAnalysisType(JavaType type, AnalysisUniverse universe) {
        return type instanceof AnalysisType analysisType ? analysisType : universe.lookup(type);
    }

    private static boolean isImplicitCheck(IfNode ifNode) {
        var truePos = getLineNumber(ifNode.trueSuccessor().getNodeSourcePosition());
        var falsePos = getLineNumber(ifNode.falseSuccessor().getNodeSourcePosition());
        return truePos == falsePos;
    }

}
