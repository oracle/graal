/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.results;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeSourcePosition;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.FormalParamTypeFlow;
import com.oracle.graal.pointsto.flow.InstanceOfTypeFlow;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.MonitorEnterTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.infrastructure.Universe;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.results.StaticAnalysisResults.BytecodeEntry;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.typestate.TypeStateUtils;

import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaTypeProfile;

public class StaticAnalysisResultsBuilder extends AbstractAnalysisResultsBuilder {

    public StaticAnalysisResultsBuilder(PointsToAnalysis bb, Universe converter) {
        super(bb, converter);
    }

    private PointsToAnalysis getAnalysis() {
        return ((PointsToAnalysis) bb);
    }

    @Override
    public StaticAnalysisResults makeOrApplyResults(AnalysisMethod method) {
        PointsToAnalysis pta = getAnalysis();
        MethodTypeFlow methodFlow = PointsToAnalysis.assertPointsToAnalysisMethod(method).getTypeFlow();
        if (!methodFlow.flowsGraphCreated()) {
            return StaticAnalysisResults.NO_RESULTS;
        }
        MethodFlowsGraph originalFlows = methodFlow.getMethodFlowsGraph();

        ArrayList<JavaTypeProfile> paramProfiles = new ArrayList<>(originalFlows.getParameters().length);
        for (int i = 0; i < originalFlows.getParameters().length; i++) {
            FormalParamTypeFlow parameter = originalFlows.getParameter(i);
            if (parameter == null) {
                /*
                 * The paramater can be `null` at this point if it doesn't have any usages inside
                 * the method, i.e., its TypeFlowBuilder was never materialized. This is an
                 * optimization carried out by the TypeFlowGraphBuilder. This means that it will not
                 * have a corresponding type profile.
                 */
                continue;
            }
            if (methodFlow.isSaturated(pta, parameter)) {
                /* The parameter type flow is saturated, it's type state doesn't matter. */
                continue;
            }

            TypeState paramTypeState = methodFlow.foldTypeFlow(pta, parameter);
            JavaTypeProfile paramProfile = makeTypeProfile(paramTypeState);
            if (paramProfile != null) {
                ensureSize(paramProfiles, i);
                paramProfiles.set(i, paramProfile);
            }
        }
        JavaTypeProfile[] parameterTypeProfiles = null;
        if (paramProfiles.size() > 0) {
            parameterTypeProfiles = paramProfiles.toArray(new JavaTypeProfile[paramProfiles.size()]);
        }

        JavaTypeProfile resultTypeProfile = makeTypeProfile(methodFlow.foldTypeFlow(pta, originalFlows.getReturnFlow()));

        ArrayList<BytecodeEntry> entries = new ArrayList<>(method.getCodeSize());

        var instanceOfCursor = originalFlows.getInstanceOfFlows().getEntries();
        while (instanceOfCursor.advance()) {
            if (isValidBci(instanceOfCursor.getKey())) {
                int bci = (int) instanceOfCursor.getKey();
                InstanceOfTypeFlow originalInstanceOf = instanceOfCursor.getValue();

                if (methodFlow.isSaturated(pta, originalInstanceOf)) {
                    /*
                     * If the instance flow is saturated its exact type state doesn't matter. This
                     * instanceof cannot be optimized.
                     */
                    continue;
                }

                /* Fold the instanceof flows. */
                TypeState instanceOfTypeState = methodFlow.foldTypeFlow(pta, originalInstanceOf);
                originalInstanceOf.setState(pta, instanceOfTypeState);

                JavaTypeProfile typeProfile = makeTypeProfile(instanceOfTypeState);
                if (typeProfile != null) {
                    ensureSize(entries, bci);
                    assert entries.get(bci) == null : "In " + method.format("%h.%n(%p)") + " a profile with bci=" + bci + " already exists: " + entries.get(bci);
                    entries.set(bci, createBytecodeEntry(method, bci, typeProfile, null, null, typeProfile));
                }
            }
        }

        var invokesCursor = originalFlows.getInvokes().getEntries();
        while (invokesCursor.advance()) {
            if (isValidBci(invokesCursor.getKey())) {
                int bci = (int) invokesCursor.getKey();
                InvokeTypeFlow originalInvoke = invokesCursor.getValue();

                TypeState invokeTypeState = null;
                /* If the receiver flow is saturated its exact type state doesn't matter. */
                if (originalInvoke.getTargetMethod().hasReceiver() && !methodFlow.isSaturated(pta, originalInvoke.getReceiver())) {
                    invokeTypeState = methodFlow.foldTypeFlow(pta, originalInvoke.getReceiver());
                    originalInvoke.setState(pta, invokeTypeState);
                }

                TypeFlow<?> originalReturn = originalInvoke.getActualReturn();
                TypeState returnTypeState = null;
                /* If the return flow is saturated its exact type state doesn't matter. */
                if (originalReturn != null && !methodFlow.isSaturated(pta, originalReturn)) {
                    returnTypeState = methodFlow.foldTypeFlow(pta, originalReturn);
                    originalReturn.setState(pta, returnTypeState);
                }

                JavaTypeProfile typeProfile = makeTypeProfile(invokeTypeState);
                JavaMethodProfile methodProfile = makeMethodProfile(originalInvoke.getCallees());
                JavaTypeProfile invokeResultTypeProfile = originalReturn == null ? null : makeTypeProfile(returnTypeState);

                if (hasStaticProfiles(typeProfile, methodProfile, invokeResultTypeProfile) || hasRuntimeProfiles()) {
                    ensureSize(entries, bci);
                    assert entries.get(bci) == null : "In " + method.format("%h.%n(%p)") + " a profile with bci=" + bci + " already exists: " + entries.get(bci);
                    entries.set(bci, createBytecodeEntry(method, bci, typeProfile, methodProfile, invokeResultTypeProfile, typeProfile));
                }
            }
        }

        if (PointstoOptions.PrintSynchronizedAnalysis.getValue(pta.getOptions())) {
            originalFlows.getMiscFlows().stream()
                            .filter(flow -> flow instanceof MonitorEnterTypeFlow)
                            .map(flow -> (MonitorEnterTypeFlow) flow)
                            .filter(m -> m.getState().typesCount() > 20)
                            .sorted(Comparator.comparingInt(m2 -> m2.getState().typesCount()))
                            .forEach(monitorEnter -> {
                                TypeState monitorEntryState = monitorEnter.getState();
                                String typesString = TypeStateUtils.closeToAllInstantiated(pta, monitorEntryState) ? "close to all instantiated"
                                                : StreamSupport.stream(monitorEntryState.types(pta).spliterator(), false).map(AnalysisType::getName).collect(Collectors.joining(", "));
                                StringBuilder strb = new StringBuilder();
                                strb.append("Location: ");
                                String methodName = method.format("%h.%n(%p)");
                                int bci = monitorEnter.getLocation().getBCI();
                                if (isValidBci(bci)) {
                                    StackTraceElement traceElement = method.asStackTraceElement(bci);
                                    String sourceLocation = traceElement.getFileName() + ":" + traceElement.getLineNumber();
                                    strb.append("@(").append(methodName).append(":").append(bci).append(")");
                                    strb.append("=(").append(sourceLocation).append(")");
                                } else {
                                    strb.append("@(").append(methodName).append(")");
                                }
                                strb.append("\n");
                                strb.append("Synchronized types #: ").append(monitorEntryState.typesCount()).append("\n");
                                strb.append("Types: ").append(typesString).append("\n");
                                System.out.println(strb);
                            });
        }

        BytecodeEntry first = null;
        for (int i = entries.size() - 1; i >= 0; i--) {
            BytecodeEntry cur = entries.get(i);
            if (cur != null) {
                cur.next = first;
                first = cur;
            }
        }

        return createStaticAnalysisResults(method, parameterTypeProfiles, resultTypeProfile, first);
    }

    /**
     * This method returns a unique key for the given node, used to store and query invoke and
     * instance-of type flows.
     * 
     * Unless the node comes from a substitution, the unique key is the BCI of the node. Every
     * newinstance/newarray/newmultiarray/instanceof/checkcast node coming from a substitution
     * method cannot have a BCI. If one substitution has multiple nodes of the same type, then the
     * BCI would not be unique. In the later case the key is a unique object.
     */
    public static Object uniqueKey(Node node) {
        NodeSourcePosition position = node.getNodeSourcePosition();
        /* If 'position' has a 'caller' then it is inlined, so the BCI is probably not unique. */
        if (position != null && position.getCaller() == null) {
            if (position.getBCI() >= 0) {
                return position.getBCI();
            }
        }
        return new Object();
    }

    /** Check if the key, provided by {@link #uniqueKey(Node)} above is an actual BCI. */
    public static boolean isValidBci(Object key) {
        if (key instanceof Integer) {
            int bci = (int) key;
            return bci >= 0;
        }
        return false;
    }

    protected BytecodeEntry createBytecodeEntry(@SuppressWarnings("unused") AnalysisMethod method, int bci, JavaTypeProfile typeProfile, JavaMethodProfile methodProfile,
                    JavaTypeProfile invokeResultTypeProfile, JavaTypeProfile staticTypeProfile) {
        return new BytecodeEntry(bci, typeProfile, methodProfile, invokeResultTypeProfile, staticTypeProfile);
    }

    protected StaticAnalysisResults createStaticAnalysisResults(AnalysisMethod method, JavaTypeProfile[] parameterTypeProfiles, JavaTypeProfile resultTypeProfile, BytecodeEntry first) {
        if (parameterTypeProfiles == null && resultTypeProfile == null && first == null) {
            return StaticAnalysisResults.NO_RESULTS;
        } else {
            return new StaticAnalysisResults(method.getCodeSize(), parameterTypeProfiles, resultTypeProfile, first);
        }
    }

    protected boolean hasRuntimeProfiles() {
        return false;
    }

    private static boolean hasStaticProfiles(JavaTypeProfile typeProfile, JavaMethodProfile methodProfile, JavaTypeProfile invokeResultTypeProfile) {
        return typeProfile != null || methodProfile != null || invokeResultTypeProfile != null;
    }

    private static void ensureSize(ArrayList<?> list, int index) {
        list.ensureCapacity(index);
        while (list.size() <= index) {
            list.add(null);
        }
    }

    @Override
    public JavaTypeProfile makeTypeProfile(AnalysisField field) {
        return makeTypeProfile(field.getTypeState());
    }
}
