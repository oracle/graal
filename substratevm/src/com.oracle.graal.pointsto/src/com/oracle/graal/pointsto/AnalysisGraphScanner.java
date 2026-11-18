/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto;

import java.util.Optional;
import java.util.function.Consumer;

import com.oracle.graal.pointsto.ObjectScanner.EmbeddedRootScan;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.results.StrengthenGraphs;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import jdk.graal.compiler.nodes.extended.FieldOffsetProvider;
import jdk.graal.compiler.nodes.extended.ForeignCall;
import jdk.graal.compiler.nodes.extended.RawLoadNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.java.AtomicReadAndAddNode;
import jdk.graal.compiler.nodes.java.AtomicReadAndWriteNode;
import jdk.graal.compiler.nodes.java.ClassIsAssignableFromNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.NewArrayWithExceptionNode;
import jdk.graal.compiler.nodes.java.NewInstanceWithExceptionNode;
import jdk.graal.compiler.nodes.java.NewMultiArrayNode;
import jdk.graal.compiler.nodes.java.NewMultiArrayWithExceptionNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.UnsafeCompareAndExchangeNode;
import jdk.graal.compiler.nodes.java.UnsafeCompareAndSwapNode;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Registers analysis elements used by a structured graph and delegates engine-specific
 * registrations to the owning analysis engine.
 */
public final class AnalysisGraphScanner {

    private AnalysisGraphScanner() {
    }

    /**
     * Registers elements common to analysis engines, then lets the engine register any additional
     * elements for each node. The callback runs after the common registrations for that node.
     */
    public static void registerUsedElements(AbstractAnalysisEngine bb, StructuredGraph graph, Consumer<Node> registerEngineSpecificElements) {
        AnalysisMethod method = (AnalysisMethod) graph.method();
        HostedProviders providers = bb.getProviders(method);
        for (Node n : graph.getNodes()) {
            if (n instanceof InstanceOfNode node) {
                AnalysisType type = (AnalysisType) node.type().getType();
                if (!ignoreInstanceOfType(bb, type)) {
                    type.registerAsReachable(AbstractAnalysisEngine.sourcePosition(node));
                }

            } else if (n instanceof NewInstanceWithExceptionNode node) {
                AnalysisType type = (AnalysisType) node.instanceClass();
                type.registerAsInstantiated(AbstractAnalysisEngine.sourcePosition(node));

            } else if (n instanceof VirtualObjectNode node) {
                AnalysisType type = (AnalysisType) node.type();
                type.registerAsInstantiated(AbstractAnalysisEngine.sourcePosition(node));

            } else if (n instanceof NewArrayNode node) {
                AnalysisType type = ((AnalysisType) node.elementType()).getArrayClass();
                type.registerAsInstantiated(AbstractAnalysisEngine.sourcePosition(node));

            } else if (n instanceof NewArrayWithExceptionNode node) {
                AnalysisType type = ((AnalysisType) node.elementType()).getArrayClass();
                type.registerAsInstantiated(AbstractAnalysisEngine.sourcePosition(node));

            } else if (n instanceof NewMultiArrayNode node) {
                AnalysisType type = (AnalysisType) node.type();
                for (int i = 0; i < node.dimensionCount(); i++) {
                    type.registerAsInstantiated(AbstractAnalysisEngine.sourcePosition(node));
                    type = type.getComponentType();
                }

            } else if (n instanceof NewMultiArrayWithExceptionNode node) {
                AnalysisType type = (AnalysisType) node.type();
                for (int i = 0; i < node.dimensionCount(); i++) {
                    type.registerAsInstantiated(AbstractAnalysisEngine.sourcePosition(node));
                    type = type.getComponentType();
                }

            } else if (n instanceof BoxNode node) {
                AnalysisType type = (AnalysisType) StampTool.typeOrNull(node, bb.getMetaAccess());
                type.registerAsInstantiated(AbstractAnalysisEngine.sourcePosition(node));

            } else if (n instanceof LoadFieldNode node) {
                AnalysisField field = (AnalysisField) node.field();
                field.registerAsRead(AbstractAnalysisEngine.sourcePosition(node));

            } else if (n instanceof StoreFieldNode node) {
                AnalysisField field = (AnalysisField) node.field();
                field.registerAsWritten(AbstractAnalysisEngine.sourcePosition(node));

            } else if (n instanceof ConstantNode cn) {
                /* GR-58472: We should try to delay marking constants as instantiated */
                JavaConstant root = cn.asJavaConstant();
                if (cn.hasUsages() && cn.isJavaConstant() && root.getJavaKind() == JavaKind.Object && root.isNonNull()) {
                    assert StampTool.isExactType(cn) : cn;
                    if (!ignoreConstant(bb, cn)) {
                        AnalysisType type = (AnalysisType) StampTool.typeOrNull(cn, bb.getMetaAccess());
                        BytecodePosition position = AbstractAnalysisEngine.sourcePosition(cn);
                        registerObjectConstantRoot(bb, root, type, position);
                    }
                }

            } else if (n instanceof FieldOffsetProvider node) {
                if (needsUnsafeRegistration(node)) {
                    ((AnalysisField) node.getField()).registerAsUnsafeAccessed(AbstractAnalysisEngine.sourcePosition(node.asNode()));
                }

            } else if (n instanceof FrameState node) {
                AnalysisMethod frameStateMethod = (AnalysisMethod) node.getMethod();
                if (frameStateMethod != null) {
                    /*
                     * All types referenced in (possibly inlined) frame states must be reachable,
                     * because these classes will be reachable from stack walking metadata. This
                     * metadata is only constructed after AOT compilation, so the image heap
                     * scanning during static analysis does not see these classes.
                     */
                    frameStateMethod.getDeclaringClass().registerAsReachable(AbstractAnalysisEngine.syntheticSourcePosition(node, method));
                }

            } else if (n instanceof ForeignCall node) {
                registerForeignCall(bb, providers.getForeignCalls(), node.getDescriptor(), graph.method());
            } else if (n instanceof UnaryMathIntrinsicNode node) {
                registerForeignCall(bb, providers.getForeignCalls(), providers.getForeignCalls().getDescriptor(node.getOperation().foreignCallSignature), graph.method());
            } else if (n instanceof BinaryMathIntrinsicNode node) {
                registerForeignCall(bb, providers.getForeignCalls(), providers.getForeignCalls().getDescriptor(node.getOperation().foreignCallSignature), graph.method());
            }

            registerEngineSpecificElements.accept(n);
        }
    }

    /**
     * This method filters constants, i.e., these constants are not seen as reachable on its own.
     * This avoids making things reachable just because of that constant usage. For all cases where
     * this method returns true, {@link StrengthenGraphs} must have a corresponding re-write of the
     * constant in case nothing else in the application made that constant reachable.
     *
     * {@link Class#isAssignableFrom} is often used with a constant receiver class. In that case, we
     * do not want to make the receiver class reachable, because as long as the receiver class is
     * not reachable for any other "real" reason we know that isAssignableFrom will always return
     * false. So in {@link StrengthenGraphs} we can then constant-fold the
     * {@link ClassIsAssignableFromNode} to false. We only apply this optimization for
     * {@link ClassIsAssignableFromNode} if it's a closed type world, for open world we cannot fold
     * the type check since the type may be used later.
     *
     * Similarly, a class should not be marked as reachable only so that we can add the class name
     * to the error message of a {@link ClassCastException}. In {@link StrengthenGraphs} we can
     * re-write the Class constant to a String constant, i.e., only embed the class name and not the
     * full java.lang.Class object in the image. We can apply this optimization optimistically for
     * both closed and open type world.
     *
     * {@link FrameState} are only used for debugging. We do not want to have larger images just so
     * that users can see a constant value in the debugger.
     */
    private static boolean ignoreConstant(AbstractAnalysisEngine bb, ConstantNode node) {
        for (var u : node.usages()) {
            if (u instanceof ClassIsAssignableFromNode usage) {
                if (!bb.getHostVM().isClosedTypeWorld() || usage.getOtherClass() == node || usage.getThisClass() != node) {
                    return false;
                }
            } else if (u instanceof BytecodeExceptionNode usage) {
                /* The checked type is the second argument for a CLASS_CAST. */
                if (usage.getExceptionKind() != BytecodeExceptionKind.CLASS_CAST || usage.getArguments().size() != 2 || usage.getArguments().get(0) == node || usage.getArguments().get(1) != node) {
                    return false;
                }
            } else if (u instanceof FrameState) {
                /* FrameState usages are only for debugging and not necessary for correctness. */
            } else {
                return false;
            }
        }
        /* Success, the ConstantNode does not need to be seen as reachable. */
        return true;
    }

    /**
     * Unsafe access nodes whose offset is a {@link FieldOffsetProvider} are modeled directly as
     * field access type flows and therefore do not need unsafe registration.
     *
     * We do not want that a field is registered as unsafe accessed just so that we have the field
     * offset during debugging, so we also ignore {@link FrameState}. {@link StrengthenGraphs}
     * removes the node from the {@link FrameState} if it is not registered for unsafe access for
     * any other reason.
     */
    private static boolean needsUnsafeRegistration(FieldOffsetProvider node) {
        for (var usage : node.asNode().usages()) {
            if (usage instanceof RawLoadNode || usage instanceof RawStoreNode ||
                            usage instanceof UnsafeCompareAndSwapNode || usage instanceof UnsafeCompareAndExchangeNode ||
                            usage instanceof AtomicReadAndWriteNode || usage instanceof AtomicReadAndAddNode) {
                /* Unsafe usages are modeled as field type flows. */
            } else if (usage instanceof FrameState) {
                /* FrameState usages are only for debugging and not necessary for correctness. */
            } else {
                return true;
            }
        }
        /* Success, the field does not need to be registered for unsafe access. */
        return false;
    }

    /**
     * In closed type world, just using a type in an instanceof type check doesn't mark the type as
     * reachable. Assuming the type is not otherwise made reachable, this allows the graph
     * strengthening to eliminate the type check completely by replacing a stamp with an unreachable
     * type with an empty stamp (see StrengthenSimplifier#strengthenStamp).
     * <p>
     * However, in open world we cannot make assumptions about types that may become reachable
     * later. Therefore, we must mark the instanceof checked type as reachable. Moreover, stamp
     * strengthening based on reachability status of types must be disabled.
     */
    private static boolean ignoreInstanceOfType(AbstractAnalysisEngine bb, AnalysisType type) {
        if (bb.getHostVM().ignoreInstanceOfTypeDisallowed()) {
            return false;
        }
        if (!bb.getHostVM().isClosedTypeWorld()) {
            return false;
        }
        if (type == null) {
            return false;
        }
        if (type.isArray()) {
            /*
             * There is no real overhead when making array types reachable (which automatically also
             * makes them instantiated), and it avoids manual reflection configuration because
             * casting to an array type then automatically marks the array type as instantiated.
             */
            return false;
        }
        return true;
    }

    /** Registers an object constant with the default precise semantics used by core analysis. */
    private static void registerObjectConstantRoot(AbstractAnalysisEngine bb, JavaConstant constantValue, AnalysisType stampedType, BytecodePosition position) {
        stampedType.registerAsInstantiated(new EmbeddedRootScan(position, constantValue));
        bb.getUniverse().registerEmbeddedRoot(constantValue, position);
    }

    private static void registerForeignCall(AbstractAnalysisEngine bb, ForeignCallsProvider foreignCallsProvider, ForeignCallDescriptor foreignCallDescriptor, ResolvedJavaMethod from) {
        Optional<AnalysisMethod> targetMethod = bb.getHostVM().handleForeignCall(foreignCallDescriptor, foreignCallsProvider);
        targetMethod.ifPresent(analysisMethod -> bb.addRootMethod(analysisMethod, true, from));
    }
}
