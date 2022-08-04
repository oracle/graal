/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.classinitialization;

import com.oracle.graal.pointsto.classinitialization.AbstractTypeInitializerGraph;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.graal.pointsto.standalone.StandaloneHost;
import com.oracle.graal.pointsto.standalone.phases.StandaloneClassInitializationPlugin;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.common.ClassInitializationNode;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ResolvedJavaField;
import org.graalvm.collections.Pair;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Specify the class initialization safety rules only available for standalone pointsto analysis
 * scenario. The class safety algorithm is mostly the same as SVM's class initialization
 * optimization, but differs at relaxing side effect checking as described in
 * {@code HostVM#checkClassInitializerSideEffect}. See {@link LooseSideEffectChecker} for details.
 * <p>
 * NOTE: the dependency between methods and type initializers is maintained by the
 * {@link StandaloneClassInitializationPlugin} that emits {@link ClassInitializationNode} for every
 * load, store, call, and instantiation in the bytecode. These dependencies are collected in
 * {@link StandaloneHost#getInitializedClasses}.
 */
public class StandaloneTypeInitializerGraph extends AbstractTypeInitializerGraph {
    public enum UnsafeKind {
        SIDE_EFFECT, // The method has side effect, e.g. accessing other class' static field
        NATIVE_METHOD, // The method is a native method
        UNBOUND_VIRTUAL_CALL, // The virtual method can't be statically bound
        INIT_UNSAFE_TYPE, // The method initializes another unsafe type
        IMPLICIT_EXCEPTION, // The <clinit> method throws exception at runtime
        UNSAFE_DEPENDENCY, // The type inherits an unsafe type
        NONE // The type/method is safe
    }

    private final Set<AnalysisMethod> knownSafeMethods;
    private final Map<AnalysisMethod, Pair<UnsafeKind, String>> unsafeMethodReasons;
    private final Map<AnalysisType, Pair<UnsafeKind, Optional<String>>> unsafeTypeReasons;
    private static final Pair<UnsafeKind, Optional<String>> EMPTY_REASON = Pair.create(UnsafeKind.NONE, Optional.empty());

    public StandaloneTypeInitializerGraph(AnalysisUniverse universe, Set<AnalysisMethod> knownSafeMethods) {
        super(universe);
        this.knownSafeMethods = knownSafeMethods;
        unsafeMethodReasons = new HashMap<>();
        unsafeTypeReasons = new HashMap<>();
    }

    @Override
    public void computeInitializerSafety() {
        super.computeInitializerSafety();
        // Relax the static field access checking for side effect and compute the type safety again
        updateTypeSafety();
    }

    private void updateTypeSafety() {
        AtomicBoolean safetyChanged = new AtomicBoolean();
        Map<AnalysisMethod, Optional<String>> updatedMethods = new HashMap<>();
        do {
            safetyChanged.set(false);
            // 1. Update the type safety
            types.entrySet().stream().filter(entry -> entry.getKey().isReachable()).forEach(entry -> {
                Pair<UnsafeKind, Optional<String>> reason = null;
                AnalysisType type = entry.getKey();
                Safety originalSafety = entry.getValue();
                if (originalSafety == Safety.SAFE) {
                    // For previously analyzed safe types, run its <clinit> to check if there's any
                    // implicit exception
                    reason = checkImplicitException(type);
                } else if (needExploreReason(type)) {
                    // Only explore the unsafe reason when necessary
                    reason = exploreUnsafeReason(type);
                }
                if (reason != null) {
                    unsafeTypeReasons.put(type, reason);
                    Safety newTypeSafety = reason.getLeft() == UnsafeKind.NONE ? Safety.SAFE : Safety.UNSAFE;
                    if (newTypeSafety != originalSafety) {
                        entry.setValue(newTypeSafety);
                        safetyChanged.set(true);
                    }
                }
            });

            /*
             * 2. Update the related method safety When a type turns to safe, the following methods
             * need to checked for possible safety updating: - The method was unsafe because it
             * initialized unsafe type.
             */
            if (safetyChanged.get()) {
                unsafeMethodReasons.entrySet().stream().filter(entry -> entry.getValue().getLeft() == UnsafeKind.INIT_UNSAFE_TYPE).forEach(
                                entry -> {
                                    AnalysisMethod originalUnsafeMethod = entry.getKey();
                                    // Check if the originalUnsafeMethod causes any any unsafe type
                                    // to initialize
                                    Optional<AnalysisType> ret = super.initUnsafeClass(originalUnsafeMethod);
                                    if (ret.isPresent()) {
                                        // Still can initialize unsafe type (by other reasons),
                                        // update the unsafe reason.
                                        String reasonDesc = initUnsafeTypeDesc(ret.get());
                                        if (!unsafeMethodReasons.get(originalUnsafeMethod).getRight().equals(reasonDesc)) {
                                            updatedMethods.put(originalUnsafeMethod, Optional.of(reasonDesc));
                                        }
                                    } else {
                                        // No unsafe type will be initialized, update the method to
                                        // safe
                                        updatedMethods.put(originalUnsafeMethod, Optional.empty());
                                        methodSafety.put(originalUnsafeMethod, Safety.SAFE);
                                    }
                                });
            }
            // 3. Update the methods that turned to be safe
            updatedMethods.forEach((updatedMethod, updatedReason) -> unsafeMethodReasons.compute(updatedMethod, (key, value) -> updatedReason.map(s -> Pair.create(value.getLeft(), s)).orElse(null)));
            updatedMethods.clear();
        } while (safetyChanged.get());
    }

    private boolean needExploreReason(AnalysisType type) {
        Pair<UnsafeKind, Optional<String>> originalReason = unsafeTypeReasons.get(type);
        boolean firstExplore = originalReason == null;
        boolean isEffected = !firstExplore &&
                        (originalReason.getLeft() == UnsafeKind.INIT_UNSAFE_TYPE || originalReason.getLeft() == UnsafeKind.UNSAFE_DEPENDENCY);
        return firstExplore || isEffected;
    }

    private static Pair<UnsafeKind, Optional<String>> checkImplicitException(AnalysisType type) {
        Pair<UnsafeKind, Optional<String>> reason = EMPTY_REASON;
        if (!type.isInitialized()) {
            try {
                Unsafe.getUnsafe().ensureClassInitialized(type.getJavaClass());
            } catch (ExceptionInInitializerError e) {
                reason = createInitFailReason(e.getException());
            } catch (OutOfMemoryError e) {
                // A better way is to prevent OOM, see
                // com.oracle.svm.hosted.classinitialization.EarlyClassInitializerAnalysis$AbortOnDisallowedNode
                reason = createInitFailReason(e);
            }
        }
        return reason;
    }

    private static Pair<UnsafeKind, Optional<String>> createInitFailReason(Throwable e) {
        try (StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw)) {
            e.printStackTrace(pw);
            return Pair.create(UnsafeKind.IMPLICIT_EXCEPTION, Optional.of("<clinit> has implicit exception:\n" + sw.toString()));
        } catch (IOException ioe) {
            AnalysisError.shouldNotReachHere(ioe);
            return null;
        }
    }

    /**
     * Find the unsafe reason for the given type if it is unsafe, or an empty Optional if it is
     * safe.
     *
     * @return the reason why the given type is unsafe, or empty if the type is safe.
     */
    private Pair<UnsafeKind, Optional<String>> exploreUnsafeReason(AnalysisType type) {
        StringBuilder sb = new StringBuilder();
        String typeName = type.toJavaName(true);
        sb.append("The type ").append(typeName).append(" is unsafe because ");
        // 1. Check if the type depends on any unsafe types
        for (AnalysisType dependency : getDependencies(type)) {
            if (isUnsafe(dependency)) {
                sb.append("it depends on unsafe type ").append(dependency.toJavaName(true));
                return Pair.create(UnsafeKind.UNSAFE_DEPENDENCY, Optional.of(sb.toString()));
            }
        }

        // 2. Check if the clinit calls any unsafe method on the call graph
        Deque<CallStackInfo> stack = new ArrayDeque<>(); // Trace the call stack from clinit to the
                                                         // unsafe method
        Set<AnalysisMethod> visited = new HashSet<>();

        AnalysisMethod classInitializer = type.getClassInitializer();
        if (classInitializer != null) {
            Pair<UnsafeKind, String> reason = getMethodUnsafeReason(classInitializer);
            if (reason != null && !canLooseSideEffectCheck(type, reason, classInitializer)) {
                sb.append(reason.getRight());
                return Pair.create(reason.getLeft(), Optional.of(sb.toString()));
            }

            stack.push(new CallStackInfo(classInitializer, null));
            while (!stack.isEmpty()) {
                CallStackInfo top = stack.peek();
                Iterator<? extends InvokeInfo> invokeIterator = top.method.getInvokes().iterator();
                if (!invokeIterator.hasNext()) {
                    stack.pop();
                } else {
                    int visitedNum = 0;
                    int iterations = 0;
                    while (invokeIterator.hasNext()) {
                        InvokeInfo invoke = invokeIterator.next();
                        iterations++;
                        AnalysisMethod currentMethod = getActualMethod(invoke);
                        if (visited.contains(currentMethod)) {
                            // If all invokes of current method are visited, it's time to pop the
                            // stack
                            if (++visitedNum == iterations && !invokeIterator.hasNext()) {
                                stack.pop();
                            }
                        } else {
                            visited.add(currentMethod);
                            reason = getMethodUnsafeReason(currentMethod);
                            if (reason != null && !canLooseSideEffectCheck(type, reason, currentMethod)) {
                                // Print the unsafe reason and the stack trace
                                sb.append("it calls the unsafe method: ").append(reason.getRight()).append("\n");
                                sb.append("The stack trace is:\n");
                                sb.append(currentMethod.getQualifiedName()).append("\n");
                                sb.append(((InvokeTypeFlow) invoke).getSource()).append("\n");
                                while (!stack.isEmpty()) {
                                    BytecodePosition bytecodePosition = stack.removeFirst().bytecodePosition;
                                    if (bytecodePosition != null) {
                                        sb.append(bytecodePosition).append("\n");
                                    }
                                }
                                visited.clear();
                                return Pair.create(reason.getLeft(), Optional.of(sb.toString()));
                            } else {
                                if (!isSafeMethod(currentMethod)) {
                                    stack.push(new CallStackInfo(currentMethod, ((InvokeTypeFlow) invoke).getSource()));
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        // No unsafe method is found on the entire call graph, the given type should be safe
        return EMPTY_REASON;
    }

    private boolean canLooseSideEffectCheck(AnalysisType type, Pair<UnsafeKind, String> reason, AnalysisMethod m) {
        if (reason != null && reason.getLeft() == UnsafeKind.SIDE_EFFECT) {
            return LooseSideEffectChecker.check((StandaloneHost) hostVM, m, type);
        }
        return false;
    }

    @Override
    protected Safety initialMethodSafety(AnalysisMethod m) {
        if (knownSafeMethods.contains(m)) {
            return Safety.SAFE;
        } else {
            return super.initialMethodSafety(m);
        }
    }

    @Override
    protected boolean cLInitHasSideEffect(AnalysisMethod method) {
        if (super.cLInitHasSideEffect(method)) {
            StringBuilder sb = new StringBuilder();
            sb.append(method.getQualifiedName()).append(" has side effect");
            ResolvedJavaField f = ((StandaloneHost) hostVM).lookupSideEffectField(method);
            if (f != null) {
                String fieldName = f.getDeclaringClass().toJavaName(true) + "." + f.getName();
                sb.append(" on field ").append(fieldName);
            }
            unsafeMethodReasons.put(method, Pair.create(UnsafeKind.SIDE_EFFECT, sb.append(".").toString()));
            return true;
        }
        return false;
    }

    @Override
    protected boolean isNativeInvoke(AnalysisMethod method) {
        if (super.isNativeInvoke(method) && !knownSafeMethods.contains(method)) {
            unsafeMethodReasons.put(method, Pair.create(UnsafeKind.NATIVE_METHOD, "native method " + method.getQualifiedName()));
            return true;
        }
        return false;
    }

    @Override
    protected Optional<AnalysisType> initUnsafeClass(AnalysisMethod m) {
        Optional<AnalysisType> ret = super.initUnsafeClass(m);
        ret.ifPresent(type -> unsafeMethodReasons.put(m, Pair.create(UnsafeKind.INIT_UNSAFE_TYPE, initUnsafeTypeDesc(type))));
        return ret;
    }

    private static String initUnsafeTypeDesc(AnalysisType type) {
        return "cause unsafe type " + type.toJavaName(true) + " initialized.";
    }

    @Override
    protected boolean canBeStaticallyBound(InvokeInfo invokeInfo) {
        if (super.canBeStaticallyBound(invokeInfo)) {
            return true;
        } else {
            Collection<AnalysisMethod> callees = invokeInfo.getOriginalCallees();
            unsafeMethodReasons.put(invokeInfo.getTargetMethod(), Pair.create(UnsafeKind.UNBOUND_VIRTUAL_CALL, "Virtual method can't statically bound. There are " + callees.size() + " callees: " +
                            callees.stream().limit(10).map(AnalysisMethod::getQualifiedName).reduce((s1, s2) -> s1 + ", " + s2).orElse("")));
            return false;
        }
    }

    public Pair<UnsafeKind, String> getMethodUnsafeReason(AnalysisMethod m) {
        return unsafeMethodReasons.get(m);
    }

    @Override
    protected void initTypeSafety(AnalysisType t) {
        // Set all types as safe initially
        types.put(t, Safety.SAFE);
    }

    @Override
    protected boolean updateMethodSafety(AnalysisMethod m) {
        if (knownSafeMethods.contains(m)) {
            return false;
        } else {
            return super.updateMethodSafety(m);
        }
    }

    private static AnalysisMethod getActualMethod(InvokeInfo invoke) {
        AnalysisMethod m = invoke.getTargetMethod();
        if (!invoke.isDirectInvoke() && invoke.canBeStaticallyBound()) {
            // Get the de-virtualized method
            m = invoke.getOriginalCallees().toArray(new AnalysisMethod[0])[0];
        }
        return m;
    }

    private static class CallStackInfo {
        public AnalysisMethod method;
        public BytecodePosition bytecodePosition;

        CallStackInfo(AnalysisMethod method, BytecodePosition bytecodePosition) {
            this.method = method;
            this.bytecodePosition = bytecodePosition;
        }
    }

    public Map<AnalysisType, Pair<UnsafeKind, Optional<String>>> getUnsafeTypeReasons() {
        return unsafeTypeReasons;
    }
}
