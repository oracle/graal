/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.classinitialization;

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.heap.ImageHeapArray;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapInstance;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisGraphDecoder;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.classinitialization.SimulateClassInitializerPolicy.SimulateClassInitializerInlineScope;
import com.oracle.svm.hosted.fieldfolding.IsStaticFinalFieldInitializedNode;
import com.oracle.svm.hosted.fieldfolding.MarkStaticFinalFieldInitializedNode;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.graphbuilderconf.LoopExplosionPlugin.LoopExplosionKind;
import jdk.graal.compiler.nodes.java.AccessMonitorNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.java.NewMultiArrayNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.nodes.virtual.VirtualArrayNode;
import jdk.graal.compiler.nodes.virtual.VirtualBoxingNode;
import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopyNode;
import jdk.graal.compiler.replacements.nodes.ObjectClone;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * The graph decoder that performs the partial evaluation of a single class initializer and all
 * methods invoked by that class initializer.
 * 
 * See {@link SimulateClassInitializerSupport} for an overview of class initializer simulation.
 */
public class SimulateClassInitializerGraphDecoder extends InlineBeforeAnalysisGraphDecoder {

    protected final SimulateClassInitializerSupport support;
    protected final SimulateClassInitializerClusterMember clusterMember;

    /**
     * Stored in a separate field because it is frequently accessed, so having a separate field
     * makes the code more readable.
     */
    protected final MetaAccessProvider metaAccess;

    protected final EconomicMap<VirtualObjectNode, ImageHeapConstant> allVirtualObjects = EconomicMap.create();
    protected final EconomicMap<AnalysisField, Object> currentStaticFields = EconomicMap.create();
    protected final EconomicSet<ImageHeapConstant> currentActiveObjects = EconomicSet.create();
    protected final EconomicMap<AnalysisField, Boolean> isStaticFinalFieldInitializedStates = EconomicMap.create();

    protected SimulateClassInitializerGraphDecoder(BigBang bb, SimulateClassInitializerPolicy policy, SimulateClassInitializerClusterMember clusterMember, StructuredGraph graph) {
        super(bb, policy, graph, clusterMember.cluster.providers, unused -> LoopExplosionKind.FULL_UNROLL);

        this.support = clusterMember.cluster.support;
        this.clusterMember = clusterMember;
        this.metaAccess = providers.getMetaAccess();
    }

    @Override
    public void decode(ResolvedJavaMethod classInitializer) {
        for (var f : classInitializer.getDeclaringClass().getStaticFields()) {
            var field = (AnalysisField) f;

            /*
             * The initial value (before any field store) of a static field in our own class is the
             * value coming from the constant pool attribute.
             */
            var initialValue = field.getConstantValue();
            if (initialValue == null) {
                initialValue = JavaConstant.defaultForKind(field.getStorageKind());
            }
            currentStaticFields.put(field, initialValue);
            isStaticFinalFieldInitializedStates.put(field, Boolean.FALSE);
        }
        super.decode(classInitializer);
    }

    @Override
    protected void maybeAbortInlining(MethodScope ms, LoopScope loopScope, Node node) {
        InlineBeforeAnalysisMethodScope methodScope = cast(ms);

        if (node instanceof ControlSplitNode || node instanceof AccessMonitorNode) {
            if (support.collectAllReasons) {
                if (methodScope.isInlinedMethod()) {
                    /*
                     * We want to collect more reasons in the class initializer itself, so we abort
                     * inlining of too complex methods.
                     */
                    abortInlining(methodScope);
                    return;
                } else if (loopScope.loopDepth == 0) {
                    /*
                     * We are in the class initializer itself, so we just continue decoding. Unless
                     * we are in an unrolled loop, in which case we need to abort because otherwise
                     * decoding can quickly explode the graph size.
                     */
                    return;
                }
            }

            /*
             * Any control flow split means that our resulting class initializer cannot be
             * simulated, so we can do an early abort of the analysis.
             */
            throw SimulateClassInitializerAbortException.doAbort(clusterMember, graph, node);
        }
    }

    @Override
    protected void checkLoopExplosionIteration(MethodScope methodScope, LoopScope loopScope) {
        if (loopScope.loopIteration > support.maxLoopIterations) {
            /*
             * Most loops that are not unrollable bail out of loop unrolling earlier when a
             * ControlSplitNode is appended. But for example an empty endless loop triggers this
             * check.
             */
            throw SimulateClassInitializerAbortException.doAbort(clusterMember, graph, "Loop iteration count exceeding unrolling limit");
        }
    }

    @Override
    protected Node handleFloatingNodeBeforeAdd(MethodScope methodScope, LoopScope loopScope, Node n) {
        Node node = n;
        if (node instanceof AllocatedObjectNode allocatedObjectNode) {
            node = handleAllocatedObjectNode(allocatedObjectNode);
        }
        return super.handleFloatingNodeBeforeAdd(methodScope, loopScope, node);
    }

    @Override
    protected Node doCanonicalizeFixedNode(InlineBeforeAnalysisMethodScope methodScope, LoopScope loopScope, Node initialNode) {
        Node node = super.doCanonicalizeFixedNode(methodScope, loopScope, initialNode);

        var countersScope = (SimulateClassInitializerInlineScope) methodScope.policyScope;
        if (node instanceof StoreFieldNode storeFieldNode) {
            node = handleStoreFieldNode(storeFieldNode);
        } else if (node instanceof LoadFieldNode loadFieldNode) {
            node = handleLoadFieldNode(loadFieldNode);
        } else if (node instanceof StoreIndexedNode storeIndexedNode) {
            node = handleStoreIndexedNode(storeIndexedNode);
        } else if (node instanceof LoadIndexedNode loadIndexedNode) {
            node = handleLoadIndexedNode(loadIndexedNode);
        } else if (node instanceof ArrayCopyNode arrayCopyNode) {
            node = handleArrayCopyNode(arrayCopyNode);
        } else if (node instanceof EnsureClassInitializedNode ensureClassInitializedNode) {
            node = handleEnsureClassInitializedNode(ensureClassInitializedNode);
        } else if (node instanceof IsStaticFinalFieldInitializedNode isStaticFinalFieldInitializedNode) {
            node = handleIsStaticFinalFieldInitializedNode(isStaticFinalFieldInitializedNode);
        } else if (node instanceof MarkStaticFinalFieldInitializedNode markStaticFinalFieldInitializedNode) {
            node = handleMarkStaticFinalFieldInitializedNode(markStaticFinalFieldInitializedNode);
        } else if (node instanceof AccessMonitorNode accessMonitorNode) {
            node = handleAccessMonitorNode(accessMonitorNode);
        } else if (node instanceof CommitAllocationNode commitAllocationNode) {
            handleCommitAllocationNode(countersScope, commitAllocationNode);
        } else if (node instanceof NewInstanceNode newInstanceNode) {
            node = handleNewInstanceNode(countersScope, newInstanceNode);
        } else if (node instanceof NewArrayNode newArrayNode) {
            node = handleNewArrayNode(countersScope, newArrayNode);
        } else if (node instanceof NewMultiArrayNode newMultiArrayNode) {
            node = handleNewMultiArrayNode(countersScope, newMultiArrayNode);
        } else if (node instanceof BoxNode boxNode) {
            node = handleBoxNode(boxNode);
        } else if (node instanceof ObjectClone objectClone) {
            node = handleObjectClone(countersScope, objectClone);
        }

        if (node instanceof AbstractBeginNode && node.predecessor() instanceof ControlSplitNode) {
            /*
             * It is not possible to do a flow-sensitive analysis during partial evaluation, so
             * after every control flow split we need to re-set our information about fields and
             * objects. But this is only necessary in the "collect all reasons" mode, i.e., normally
             * we abort the analysis.
             */
            if (!support.collectAllReasons) {
                throw SimulateClassInitializerAbortException.doAbort(clusterMember, graph, node.predecessor());
            }
            currentActiveObjects.clear();
            currentStaticFields.clear();
            isStaticFinalFieldInitializedStates.clear();
        }

        return node;
    }

    private Node handleStoreFieldNode(StoreFieldNode node) {
        var field = (AnalysisField) node.field();
        if (field.isStatic()) {
            currentStaticFields.put(field, node.value());

        } else {
            var object = asActiveImageHeapInstance(node.object());
            var value = node.value().asJavaConstant();
            if (object != null && value != null) {
                object.setFieldValue(field, adaptForImageHeap(value, field.getStorageKind()));
                return null;
            }
        }
        return node;
    }

    private Node handleLoadFieldNode(LoadFieldNode node) {
        var field = (AnalysisField) node.field();
        if (field.isStatic()) {
            var currentValue = currentStaticFields.get(field);
            if (currentValue instanceof ValueNode currentValueNode) {
                return currentValueNode;
            } else if (currentValue instanceof JavaConstant currentConstant) {
                return ConstantNode.forConstant(currentConstant, metaAccess);
            }
            assert currentValue == null : "Unexpected static field value: " + currentValue;

            ConstantNode canonicalized = support.tryCanonicalize(bb, node);
            if (canonicalized != null) {
                return canonicalized;
            }

        } else {
            var object = asActiveImageHeapInstance(node.object());
            if (object != null) {
                var currentValue = (JavaConstant) object.getFieldValue(field);
                return ConstantNode.forConstant(currentValue, metaAccess);
            }
        }
        var intrinsified = support.fieldValueInterceptionSupport.tryIntrinsifyFieldLoad(providers, node);
        if (intrinsified != null) {
            return intrinsified;
        }
        return node;
    }

    private Node handleStoreIndexedNode(StoreIndexedNode node) {
        var array = asActiveImageHeapArray(node.array());
        var value = node.value().asJavaConstant();
        int idx = asIntegerOrMinusOne(node.index());

        if (array != null && value != null && idx >= 0 && idx < array.getLength()) {
            var componentType = array.getType().getComponentType();
            if (node.elementKind().isPrimitive() || value.isNull() || componentType.isAssignableFrom(((ImageHeapConstant) value).getType())) {
                array.setElement(idx, adaptForImageHeap(value, componentType.getStorageKind()));
                return null;
            }
        }
        return node;
    }

    private Node handleLoadIndexedNode(LoadIndexedNode node) {
        var array = asActiveImageHeapArray(node.array());
        int idx = asIntegerOrMinusOne(node.index());

        if (array != null && idx >= 0 && idx < array.getLength()) {
            var currentValue = (JavaConstant) array.getElement(idx);
            return ConstantNode.forConstant(currentValue, metaAccess);
        }
        return node;
    }

    private Node handleArrayCopyNode(ArrayCopyNode node) {
        if (handleArrayCopy(asActiveImageHeapArray(node.getSource()), asIntegerOrMinusOne(node.getSourcePosition()),
                        asActiveImageHeapArray(node.getDestination()), asIntegerOrMinusOne(node.getDestinationPosition()), asIntegerOrMinusOne(node.getLength()))) {
            return null;
        }
        return node;
    }

    protected boolean handleArrayCopy(ImageHeapArray source, int sourcePos, ImageHeapArray dest, int destPos, int length) {
        if (source == null || sourcePos < 0 || sourcePos >= source.getLength() ||
                        dest == null || destPos < 0 || destPos >= dest.getLength() ||
                        length < 0 || sourcePos > source.getLength() - length || destPos > dest.getLength() - length) {
            return false;
        }

        var sourceComponentType = source.getType().getComponentType();
        var destComponentType = dest.getType().getComponentType();
        if (sourceComponentType.getJavaKind() != destComponentType.getJavaKind()) {
            return false;
        }
        if (destComponentType.getJavaKind() == JavaKind.Object && !destComponentType.isJavaLangObject() && !sourceComponentType.equals(destComponentType)) {
            for (int i = 0; i < length; i++) {
                var elementValue = (JavaConstant) source.getElement(sourcePos + i);
                if (elementValue.isNonNull()) {
                    var elementValueType = ((ImageHeapConstant) elementValue).getType();
                    if (!destComponentType.isAssignableFrom(elementValueType)) {
                        return false;
                    }
                }
            }
        }

        /* All checks passed, we can now copy array elements. */
        if (source == dest && sourcePos < destPos) {
            /* Must copy backwards to avoid losing elements. */
            for (int i = length - 1; i >= 0; i--) {
                dest.setElement(destPos + i, (JavaConstant) source.getElement(sourcePos + i));
            }
        } else {
            for (int i = 0; i < length; i++) {
                dest.setElement(destPos + i, (JavaConstant) source.getElement(sourcePos + i));
            }
        }
        return true;
    }

    private Node handleEnsureClassInitializedNode(EnsureClassInitializedNode node) {
        var classInitType = (AnalysisType) node.constantTypeOrNull(providers.getConstantReflection());
        if (classInitType != null) {
            if (support.trySimulateClassInitializer(graph.getDebug(), classInitType, clusterMember)) {
                /* Class is already simulated initialized, no need for a run-time check. */
                return null;
            }
            var classInitTypeMember = clusterMember.cluster.clusterMembers.get(classInitType);
            if (classInitTypeMember != null && !classInitTypeMember.status.published) {
                /*
                 * The class is part of the same cycle as our class. We optimistically remove the
                 * initialization check, which is correct if the whole cycle can be simulated. If
                 * the cycle cannot be simulated, then this graph with the optimistic assumption
                 * will be discarded.
                 */
                clusterMember.dependencies.add(classInitTypeMember);
                return null;
            }
        }
        return node;
    }

    private Node handleIsStaticFinalFieldInitializedNode(IsStaticFinalFieldInitializedNode node) {
        var field = (AnalysisField) node.getField();
        var isStaticFinalFieldInitialized = isStaticFinalFieldInitializedStates.get(field);
        if (isStaticFinalFieldInitialized != null) {
            return ConstantNode.forBoolean(isStaticFinalFieldInitialized);
        }
        if (support.trySimulateClassInitializer(graph.getDebug(), field.getDeclaringClass(), clusterMember)) {
            return ConstantNode.forBoolean(true);
        }
        return node;
    }

    private Node handleMarkStaticFinalFieldInitializedNode(MarkStaticFinalFieldInitializedNode node) {
        var field = (AnalysisField) node.getField();
        isStaticFinalFieldInitializedStates.put(field, Boolean.TRUE);
        return node;
    }

    private Node handleAccessMonitorNode(AccessMonitorNode node) {
        var object = asActiveImageHeapConstant(node.object());
        if (object != null) {
            /*
             * Objects allocated within the class initializer are similar to escape analyzed
             * objects, so we can eliminate such synchronization.
             * 
             * Note that we cannot eliminate all synchronization in general: an object that was
             * present before class initialization started could be permanently locked by another
             * thread, in which case the class initializer must never complete. We cannot detect
             * such cases during simulation.
             */
            return null;
        }
        return node;
    }

    private Node handleAllocatedObjectNode(AllocatedObjectNode node) {
        var imageHeapConstant = allVirtualObjects.get(node.getVirtualObject());
        if (imageHeapConstant != null) {
            return ConstantNode.forConstant(imageHeapConstant, metaAccess);
        }
        return node;
    }

    private ValueNode handleNewInstanceNode(SimulateClassInitializerInlineScope countersScope, NewInstanceNode node) {
        var type = (AnalysisType) node.instanceClass();
        if (accumulateNewInstanceSize(countersScope, type, node)) {
            var instance = new ImageHeapInstance(type);
            for (var field : type.getInstanceFields(true)) {
                var aField = (AnalysisField) field;
                instance.setFieldValue(aField, JavaConstant.defaultForKind(aField.getStorageKind()));
            }
            currentActiveObjects.add(instance);
            return ConstantNode.forConstant(instance, metaAccess);
        }
        return node;
    }

    private ValueNode handleNewArrayNode(SimulateClassInitializerInlineScope countersScope, NewArrayNode node) {
        var arrayType = (AnalysisType) node.elementType().getArrayClass();
        int length = asIntegerOrMinusOne(node.length());
        if (accumulateNewArraySize(countersScope, arrayType, length, node)) {
            var array = createNewArray(arrayType, length);
            return ConstantNode.forConstant(array, metaAccess);
        }
        return node;
    }

    protected ImageHeapArray createNewArray(AnalysisType arrayType, int length) {
        var array = ImageHeapArray.create(arrayType, length);
        var defaultValue = JavaConstant.defaultForKind(arrayType.getComponentType().getStorageKind());
        for (int i = 0; i < length; i++) {
            array.setElement(i, defaultValue);
        }
        currentActiveObjects.add(array);
        return array;
    }

    private ValueNode handleNewMultiArrayNode(SimulateClassInitializerInlineScope countersScope, NewMultiArrayNode node) {
        int[] dimensions = new int[node.dimensionCount()];

        /*
         * Check first that all array dimensions are valid and that the sum of all resulting array
         * sizes is not too large.
         */
        long totalLength = 1;
        var curArrayType = (AnalysisType) node.type();
        for (int i = 0; i < dimensions.length; i++) {
            int length = asIntegerOrMinusOne(node.dimension(i));
            totalLength = totalLength * length;
            if (!accumulateNewArraySize(countersScope, curArrayType, totalLength, node)) {
                return node;
            }
            dimensions[i] = length;
            curArrayType = curArrayType.getComponentType();
        }

        var array = createNewMultiArray((AnalysisType) node.type(), 0, dimensions);
        return ConstantNode.forConstant(array, metaAccess);
    }

    private ImageHeapArray createNewMultiArray(AnalysisType curArrayType, int curDimension, int[] dimensions) {
        int curLength = dimensions[curDimension];
        int nextDimension = curDimension + 1;
        if (nextDimension == dimensions.length) {
            return createNewArray(curArrayType, curLength);
        }
        var nextArrayType = curArrayType.getComponentType();

        var array = ImageHeapArray.create(curArrayType, dimensions[curDimension]);
        for (int i = 0; i < curLength; i++) {
            array.setElement(i, createNewMultiArray(nextArrayType, nextDimension, dimensions));
        }
        currentActiveObjects.add(array);
        return array;
    }

    private ValueNode handleBoxNode(BoxNode node) {
        var value = node.getValue().asJavaConstant();
        if (value == null || node.hasIdentity()) {
            return node;
        }

        /*
         * No need to produce a ImageHeapConstant, because we know all the boxing classes are always
         * initialized at image build time.
         */
        var boxedValue = switch (node.getBoxingKind()) {
            case Byte -> Byte.valueOf((byte) value.asInt());
            case Boolean -> Boolean.valueOf(value.asInt() != 0);
            case Short -> Short.valueOf((short) value.asInt());
            case Char -> Character.valueOf((char) value.asInt());
            case Int -> Integer.valueOf(value.asInt());
            case Long -> Long.valueOf(value.asLong());
            case Float -> Float.valueOf(value.asFloat());
            case Double -> Double.valueOf(value.asDouble());
            default -> throw VMError.shouldNotReachHere("Unexpected kind", node.getBoxingKind());
        };
        return ConstantNode.forConstant(providers.getSnippetReflection().forObject(boxedValue), metaAccess);
    }

    private ValueNode handleObjectClone(SimulateClassInitializerInlineScope countersScope, ObjectClone node) {
        var originalImageHeapConstant = asActiveImageHeapConstant(node.getObject());
        if (originalImageHeapConstant != null) {
            var type = originalImageHeapConstant.getType();
            if ((originalImageHeapConstant instanceof ImageHeapArray originalArray && accumulateNewArraySize(countersScope, type, originalArray.getLength(), node.asNode())) ||
                            (type.isCloneableWithAllocation() && accumulateNewInstanceSize(countersScope, type, node.asNode()))) {
                var cloned = originalImageHeapConstant.forObjectClone();
                currentActiveObjects.add(cloned);
                return ConstantNode.forConstant(cloned, metaAccess);
            }
        }

        var original = node.getObject().asJavaConstant();
        if (original != null && ((ConstantNode) node.getObject()).getStableDimension() > 0) {
            /*
             * Cloning of an array with stable elements produces a new image heap array with all
             * elements copied from the stable array. But the new array is not stable anymore.
             */
            var arrayType = (AnalysisType) metaAccess.lookupJavaType(original);
            Integer length = providers.getConstantReflection().readArrayLength(original);
            if (length != null && accumulateNewArraySize(countersScope, arrayType, length, node.asNode())) {
                var array = ImageHeapArray.create(arrayType, length);
                for (int i = 0; i < length; i++) {
                    array.setElement(i, adaptForImageHeap(providers.getConstantReflection().readArrayElement(original, i), arrayType.getComponentType().getStorageKind()));
                }
                currentActiveObjects.add(array);
                return ConstantNode.forConstant(array, metaAccess);
            }
        }
        return node.asNode();
    }

    private void handleCommitAllocationNode(SimulateClassInitializerInlineScope countersScope, CommitAllocationNode node) {
        boolean progress;
        do {
            /*
             * To handle multi-dimensional arrays, we process the virtual objects multiple times as
             * long as we make any progress: In the first iteration, the "inner" arrays are
             * processed and put into the allVirtualObjects map. In the second iteration, these
             * virtual objects can be put into the outer array.
             */
            progress = false;

            int pos = 0;
            for (int i = 0; i < node.getVirtualObjects().size(); i++) {
                VirtualObjectNode virtualObject = node.getVirtualObjects().get(i);
                int entryCount = virtualObject.entryCount();
                List<ValueNode> entries = node.getValues().subList(pos, pos + entryCount);
                pos += entryCount;

                if (!node.getLocks(i).isEmpty() || node.getEnsureVirtual().get(i)) {
                    /*
                     * Ignore unnecessary corner cases: we do not expect to see objects that are
                     * locked because constructors are not inlined when escape analysis runs, i.e.,
                     * objects are always materialized before the constructor when they are also
                     * unlocked. Similarly, we do not see virtual objects that were already passed
                     * into a EnsureVirtualizedNode.
                     */
                } else if (allVirtualObjects.containsKey(virtualObject)) {
                    /* Already processed in previous round. */
                } else if (virtualObject instanceof VirtualBoxingNode) {
                    VMError.shouldNotReachHere("For testing: check if this is reachable");
                    /*
                     * Could be handled the same way as a BoxNode, but does not occur in practice
                     * because escape analysis does not seem to materialize boxed objects as part of
                     * a CommitAllocationNode.
                     */
                } else if (virtualObject instanceof VirtualInstanceNode virtualInstance) {
                    progress |= handleVirtualInstance(countersScope, virtualInstance, entries, node);
                } else if (virtualObject instanceof VirtualArrayNode virtualArray) {
                    progress |= handleVirtualArray(countersScope, virtualArray, entries, node);
                } else {
                    throw VMError.shouldNotReachHere(virtualObject.toString());
                }
            }
        } while (progress);
    }

    private boolean handleVirtualInstance(SimulateClassInitializerInlineScope countersScope, VirtualInstanceNode virtualInstance, List<ValueNode> entries, Node reason) {
        var type = (AnalysisType) virtualInstance.type();
        if (!accumulateNewInstanceSize(countersScope, type, reason)) {
            return false;
        }
        var instance = new ImageHeapInstance(type);
        for (int j = 0; j < virtualInstance.entryCount(); j++) {
            var entry = lookupConstantEntry(j, entries);
            if (entry == null) {
                /*
                 * That happens only in corner cases since constructors are not inlined when escape
                 * analysis runs.
                 */
                return false;
            }
            var field = (AnalysisField) virtualInstance.field(j);
            instance.setFieldValue(field, adaptForImageHeap(entry, field.getStorageKind()));
        }
        allVirtualObjects.put(virtualInstance, instance);
        currentActiveObjects.add(instance);
        return true;
    }

    private boolean handleVirtualArray(SimulateClassInitializerInlineScope countersScope, VirtualArrayNode virtualArray, List<ValueNode> entries, Node reason) {
        var arrayType = (AnalysisType) virtualArray.type();
        int length = virtualArray.entryCount();
        if (!accumulateNewArraySize(countersScope, arrayType, length, reason)) {
            return false;
        }
        var array = ImageHeapArray.create(arrayType, length);
        for (int j = 0; j < length; j++) {
            var entry = lookupConstantEntry(j, entries);
            if (entry == null) {
                /*
                 * Handling this would require emitting multiple StoreIndexed node, which is not
                 * possible as part of a canonicalization during partial evaluation.
                 */
                return false;
            }
            array.setElement(j, adaptForImageHeap(entry, arrayType.getComponentType().getStorageKind()));
        }
        allVirtualObjects.put(virtualArray, array);
        currentActiveObjects.add(array);
        return true;
    }

    private JavaConstant lookupConstantEntry(int index, List<ValueNode> entries) {
        var entry = entries.get(index);
        if (entry instanceof VirtualObjectNode virtualObjectNode) {
            return allVirtualObjects.get(virtualObjectNode);
        } else {
            return entry.asJavaConstant();
        }
    }

    protected boolean accumulateNewInstanceSize(SimulateClassInitializerInlineScope countersScope, AnalysisType type, Node reason) {
        assert type.isInstanceClass() : type;
        /*
         * We do not know yet how large the object really will be, because we do not know yet which
         * fields are reachable. So we estimate by just summing up field sizes.
         */
        var objectLayout = ImageSingletons.lookup(ObjectLayout.class);
        long allocationSize = objectLayout.getFirstFieldOffset();
        for (var field : type.getInstanceFields(true)) {
            allocationSize += objectLayout.sizeInBytes(((AnalysisField) field).getStorageKind());
        }
        return accumulatedNewObjectSize(countersScope, allocationSize, reason);
    }

    protected boolean accumulateNewArraySize(SimulateClassInitializerInlineScope countersScope, AnalysisType arrayType, long length, Node reason) {
        if (length < 0) {
            return false;
        }
        var objectLayout = ImageSingletons.lookup(ObjectLayout.class);
        long allocationSize = objectLayout.getArraySize(arrayType.getComponentType().getStorageKind(), (int) length, true);
        return accumulatedNewObjectSize(countersScope, allocationSize, reason);
    }

    private boolean accumulatedNewObjectSize(SimulateClassInitializerInlineScope countersScope, long allocationSize, Node reason) {
        if (countersScope.accumulativeCounters.allocatedBytes + allocationSize > support.maxAllocatedBytes) {
            if (debug.isLogEnabled(DebugContext.BASIC_LEVEL)) {
                debug.log("object size %s too large since already %s allocated: %s %s", allocationSize, countersScope.accumulativeCounters.allocatedBytes, reason, reason.getNodeSourcePosition());
            }
            if (support.collectAllReasons) {
                return false;
            } else {
                throw SimulateClassInitializerAbortException.doAbort(clusterMember, graph, reason);
            }
        }
        countersScope.accumulativeCounters.allocatedBytes += allocationSize;
        countersScope.allocatedBytes += allocationSize;
        return true;
    }

    protected ImageHeapConstant asActiveImageHeapConstant(ValueNode node) {
        var constant = node.asJavaConstant();
        if (constant instanceof ImageHeapConstant imageHeapConstant && currentActiveObjects.contains(imageHeapConstant)) {
            return imageHeapConstant;
        }
        return null;
    }

    protected ImageHeapInstance asActiveImageHeapInstance(ValueNode node) {
        var constant = node.asJavaConstant();
        if (constant instanceof ImageHeapInstance imageHeapInstance && currentActiveObjects.contains(imageHeapInstance)) {
            return imageHeapInstance;
        }
        return null;
    }

    protected ImageHeapArray asActiveImageHeapArray(ValueNode node) {
        var constant = node.asJavaConstant();
        if (constant instanceof ImageHeapArray imageHeapArray && currentActiveObjects.contains(imageHeapArray)) {
            return imageHeapArray;
        }
        return null;
    }

    protected static int asIntegerOrMinusOne(ValueNode node) {
        var constant = node.asJavaConstant();
        if (constant != null) {
            return constant.asInt();
        }
        return -1;
    }

    /**
     * Make sure that constants added into the image heap have the correct kind. Constants for
     * sub-integer types are often just integer constants in the Graal IR, i.e., we cannot rely on
     * the JavaKind of the constant to match the type of the field or array.
     */
    private static JavaConstant adaptForImageHeap(JavaConstant value, JavaKind storageKind) {
        if (value.getJavaKind() != storageKind) {
            assert value instanceof PrimitiveConstant && value.getJavaKind().getStackKind() == storageKind.getStackKind() : "only sub-int values can have a mismatch of the JavaKind: " +
                            value.getJavaKind() + ", " + storageKind;
            return JavaConstant.forPrimitive(storageKind, value.asLong());
        } else {
            assert !storageKind.isObject() || value.isNull() || value instanceof ImageHeapConstant : "Expected ImageHeapConstant, found: " + value;
            return value;
        }
    }
}
