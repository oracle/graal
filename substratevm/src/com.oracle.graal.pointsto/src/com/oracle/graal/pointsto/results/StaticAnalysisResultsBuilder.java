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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.FormalParamTypeFlow;
import com.oracle.graal.pointsto.flow.InstanceOfTypeFlow;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.infrastructure.Universe;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.results.StaticAnalysisResults.BytecodeEntry;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaMethodProfile.ProfiledMethod;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.JavaTypeProfile.ProfiledType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.TriState;

public class StaticAnalysisResultsBuilder {

    private final BigBang bb;

    /**
     * The universe used to convert analysis metadata to hosted metadata, or {@code null} if no
     * conversion should be performed.
     */
    protected final Universe converter;

    /* Caches for JavaTypeProfile with 0, 1, or more types. */
    private final JavaTypeProfile[] types0;
    private final JavaTypeProfile[] types1Null;
    private final JavaTypeProfile[] types1NonNull;
    private final Map<JavaTypeProfile, JavaTypeProfile> types;

    /* Caches for JavaMethodProfile with 0, 1, or more types. */
    private final JavaMethodProfile[] methods0;
    private final JavaMethodProfile[] methods1;
    private final Map<JavaMethodProfile, JavaMethodProfile> methods;

    public StaticAnalysisResultsBuilder(BigBang bb, Universe converter) {
        this.bb = bb;
        this.converter = converter;

        types0 = new JavaTypeProfile[2];
        types1Null = new JavaTypeProfile[bb.getUniverse().getNextTypeId()];
        types1NonNull = new JavaTypeProfile[bb.getUniverse().getNextTypeId()];
        types = new HashMap<>();

        methods0 = new JavaMethodProfile[1];
        methods1 = new JavaMethodProfile[bb.getUniverse().getNextMethodId()];
        methods = new HashMap<>();
    }

    public StaticAnalysisResults makeResults(AnalysisMethod method) {

        MethodTypeFlow methodFlow = method.getTypeFlow();
        MethodFlowsGraph originalFlows = methodFlow.getOriginalMethodFlows();

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
            if (methodFlow.isSaturated(bb, parameter)) {
                /* The parameter type flow is saturated, it's type state doesn't matter. */
                continue;
            }

            TypeState paramTypeState = methodFlow.foldTypeFlow(bb, parameter);
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

        JavaTypeProfile resultTypeProfile = makeTypeProfile(methodFlow.foldTypeFlow(bb, originalFlows.getResult()));

        ArrayList<BytecodeEntry> entries = new ArrayList<>(method.getCodeSize());

        for (Map.Entry<Object, InstanceOfTypeFlow> entry : originalFlows.getInstanceOfFlows()) {
            if (BytecodeLocation.isValidBci(entry.getKey())) {
                int bci = (int) entry.getKey();
                InstanceOfTypeFlow originalInstanceOf = entry.getValue();

                if (methodFlow.isSaturated(bb, originalInstanceOf)) {
                    /*
                     * If the instance flow is saturated its exact type state doesn't matter. This
                     * instanceof cannot be optimized.
                     */
                    continue;
                }

                /* Fold the instanceof flows. */
                TypeState instanceOfTypeState = methodFlow.foldTypeFlow(bb, originalInstanceOf);
                originalInstanceOf.setState(bb, instanceOfTypeState);

                JavaTypeProfile typeProfile = makeTypeProfile(instanceOfTypeState);
                if (typeProfile != null) {
                    ensureSize(entries, bci);
                    assert entries.get(bci) == null : "In " + method.format("%h.%n(%p)") + " a profile with bci=" + bci + " already exists: " + entries.get(bci);
                    entries.set(bci, createBytecodeEntry(method, bci, typeProfile, null, null));
                }
            }
        }

        for (Entry<Object, InvokeTypeFlow> entry : originalFlows.getInvokes()) {
            if (BytecodeLocation.isValidBci(entry.getKey())) {
                int bci = (int) entry.getKey();
                InvokeTypeFlow originalInvoke = entry.getValue();

                TypeState invokeTypeState = null;
                /* If the receiver flow is saturated its exact type state doesn't matter. */
                if (originalInvoke.getTargetMethod().hasReceiver() && !methodFlow.isSaturated(bb, originalInvoke.getReceiver())) {
                    invokeTypeState = methodFlow.foldTypeFlow(bb, originalInvoke.getReceiver());
                    originalInvoke.setState(bb, invokeTypeState);
                }

                TypeFlow<?> originalReturn = originalInvoke.getActualReturn();
                TypeState returnTypeState = null;
                /* If the return flow is saturated its exact type state doesn't matter. */
                if (originalReturn != null && !methodFlow.isSaturated(bb, originalReturn)) {
                    returnTypeState = methodFlow.foldTypeFlow(bb, originalReturn);
                    originalReturn.setState(bb, returnTypeState);
                }

                JavaTypeProfile typeProfile = makeTypeProfile(invokeTypeState);
                JavaMethodProfile methodProfile = makeMethodProfile(originalInvoke.getCallees());
                JavaTypeProfile invokeResultTypeProfile = originalReturn == null ? null : makeTypeProfile(returnTypeState);

                if (hasStaticProfiles(typeProfile, methodProfile, invokeResultTypeProfile) || hasRuntimeProfiles()) {
                    ensureSize(entries, bci);
                    assert entries.get(bci) == null : "In " + method.format("%h.%n(%p)") + " a profile with bci=" + bci + " already exists: " + entries.get(bci);
                    entries.set(bci, createBytecodeEntry(method, bci, typeProfile, methodProfile, invokeResultTypeProfile));
                }
            }
        }

        if (PointstoOptions.PrintSynchronizedAnalysis.getValue(bb.getOptions())) {
            originalFlows.getMonitorEntries().stream()
                            .filter(m -> m.getState().typesCount() > 20)
                            .sorted(Comparator.comparingInt(m2 -> m2.getState().typesCount()))
                            .forEach(monitorEnter -> {
                                TypeState monitorEntryState = monitorEnter.getState();
                                String typesString = monitorEntryState.closeToAllInstantiated(bb) ? "close to all instantiated"
                                                : StreamSupport.stream(monitorEntryState.types().spliterator(), false).map(AnalysisType::getName).collect(Collectors.joining(", "));
                                StringBuilder strb = new StringBuilder();
                                strb.append("Location: ");
                                String methodName = method.format("%h.%n(%p)");
                                int bci = monitorEnter.getLocation().getBci();
                                if (bci != BytecodeLocation.UNKNOWN_BCI) {
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

    protected BytecodeEntry createBytecodeEntry(@SuppressWarnings("unused") AnalysisMethod method, int bci, JavaTypeProfile typeProfile, JavaMethodProfile methodProfile,
                    JavaTypeProfile invokeResultTypeProfile) {
        return new BytecodeEntry(bci, typeProfile, methodProfile, invokeResultTypeProfile);
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

    public JavaTypeProfile makeTypeProfile(AnalysisField field) {
        TypeState fieldState = field.getTypeState();
        //@formatter:off
//        if (field.isStatic()) {
//            fieldState = field.getStaticFieldFlow().getState();
//        } else {
//          fieldState = TypeState.forEmpty();
//          for (FieldTypeFlow fieldFlow : this.getFieldFlows()) {
//              fieldState = TypeState.forUnion(bb, fieldState, fieldFlow.getState());
//        }
      //@formatter:on

        return makeTypeProfile(fieldState);
    }

    public BigBang getBigBang() {
        return bb;
    }

    private JavaTypeProfile makeTypeProfile(TypeState typeState) {
        if (typeState == null || typeState.isUnknown() ||
                        PointstoOptions.AnalysisSizeCutoff.getValue(bb.getOptions()) != -1 &&
                                        typeState.typesCount() > PointstoOptions.AnalysisSizeCutoff.getValue(bb.getOptions())) {
            return null;
        }

        if (typeState.isEmpty()) {
            synchronized (types0) {
                return cachedTypeProfile(types0, 0, typeState);
            }
        } else if (typeState.isNull()) {
            synchronized (types0) {
                return cachedTypeProfile(types0, 1, typeState);
            }
        } else if (typeState.exactType() != null) {
            if (typeState.canBeNull()) {
                synchronized (types1Null) {
                    return cachedTypeProfile(types1Null, typeState.exactType().getId(), typeState);
                }
            } else {
                synchronized (types1NonNull) {
                    return cachedTypeProfile(types1NonNull, typeState.exactType().getId(), typeState);
                }
            }
        }
        synchronized (types) {
            JavaTypeProfile created = createTypeProfile(typeState);
            types.putIfAbsent(created, created);
            return created;
        }
    }

    private JavaTypeProfile cachedTypeProfile(JavaTypeProfile[] cache, int cacheIdx, TypeState typeState) {
        JavaTypeProfile result = cache[cacheIdx];
        if (result == null) {
            result = createTypeProfile(typeState);
            cache[cacheIdx] = result;
        }
        return result;
    }

    private JavaTypeProfile createTypeProfile(TypeState typeState) {
        double probability = 1d / typeState.typesCount();
        ProfiledType[] pitems = typeState.typesStream()
                        .map(analysisType -> converter == null ? analysisType : converter.lookup(analysisType))
                        .sorted()
                        .map(type -> new ProfiledType(type, probability))
                        .toArray(ProfiledType[]::new);

        return new JavaTypeProfile(TriState.get(typeState.canBeNull()), 0, pitems);
    }

    private JavaMethodProfile makeMethodProfile(Collection<AnalysisMethod> callees) {
        if (PointstoOptions.AnalysisSizeCutoff.getValue(bb.getOptions()) != -1 && callees.size() > PointstoOptions.AnalysisSizeCutoff.getValue(bb.getOptions())) {
            return null;
        }
        if (callees.isEmpty()) {
            synchronized (methods0) {
                return cachedMethodProfile(methods0, 0, callees);
            }
        } else if (callees.size() == 1) {
            synchronized (methods1) {
                return cachedMethodProfile(methods1, callees.iterator().next().getId(), callees);
            }
        }
        synchronized (methods) {
            JavaMethodProfile created = createMethodProfile(callees);
            methods.putIfAbsent(created, created);
            return created;
        }
    }

    private JavaMethodProfile cachedMethodProfile(JavaMethodProfile[] cache, int cacheIdx, Collection<AnalysisMethod> callees) {
        JavaMethodProfile result = cache[cacheIdx];
        if (result == null) {
            result = createMethodProfile(callees);
            cache[cacheIdx] = result;
        }
        return result;
    }

    private JavaMethodProfile createMethodProfile(Collection<AnalysisMethod> callees) {
        ProfiledMethod[] pitems = new ProfiledMethod[callees.size()];
        double probability = 1d / pitems.length;

        int idx = 0;
        for (AnalysisMethod aMethod : callees) {
            ResolvedJavaMethod convertedMethod = converter == null ? aMethod : converter.lookup(aMethod);
            pitems[idx++] = new ProfiledMethod(convertedMethod, probability);
        }
        return new JavaMethodProfile(0, pitems);
    }
}
