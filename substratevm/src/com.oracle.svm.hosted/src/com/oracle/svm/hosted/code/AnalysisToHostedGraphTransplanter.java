/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.graal.nodes.ComputedIndirectCallTargetNode;
import com.oracle.svm.core.graal.nodes.SubstrateFieldLocationIdentity;
import com.oracle.svm.core.graal.nodes.SubstrateNarrowOopStamp;
import com.oracle.svm.core.meta.MethodOffset;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.meta.SubstrateMethodOffsetConstant;
import com.oracle.svm.core.meta.SubstrateMethodPointerConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;

import jdk.graal.compiler.bytecode.ResolvedJavaMethodBytecode;
import jdk.graal.compiler.core.common.Fields;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectState;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class AnalysisToHostedGraphTransplanter {
    protected final HostedUniverse universe;
    protected final CompileQueue compileQueue;

    public AnalysisToHostedGraphTransplanter(HostedUniverse universe, CompileQueue compileQueue) {
        this.universe = universe;
        this.compileQueue = compileQueue;
    }

    public StructuredGraph transplantGraph(DebugContext debug, HostedMethod hMethod, CompileQueue.CompileReason reason) {
        AnalysisMethod aMethod = hMethod.getWrapped();
        StructuredGraph aGraph = aMethod.decodeAnalyzedGraph(debug, null);
        if (aGraph == null) {
            throw VMError.shouldNotReachHere("Method not parsed during static analysis: " + aMethod.format("%r %H.%n(%p)") + ". Reachable from: " + reason);
        }

        /*
         * The graph in the analysis universe is no longer necessary once it is transplanted into
         * the hosted universe.
         */
        aMethod.clearAnalyzedGraph();

        /*
         * The static analysis always needs NodeSourcePosition. But for AOT compilation, we only
         * need to preserve them when explicitly enabled, to reduce memory pressure.
         */
        OptionValues compileOptions = compileQueue.getCustomizedOptions(hMethod, debug);
        boolean trackNodeSourcePosition = GraalOptions.TrackNodeSourcePosition.getValue(compileOptions);
        assert aMethod.equals(aGraph.method());

        List<ResolvedJavaMethod> inlinedHMethods = null;
        if (aGraph.isRecordingInlinedMethods()) {
            inlinedHMethods = aGraph.getMethods().stream().map(m -> getHostedMethod(universe, m)).collect(Collectors.toList());
        }
        StructuredGraph graph = aGraph.copy(hMethod, inlinedHMethods, compileOptions, debug, trackNodeSourcePosition);

        transplantEscapeAnalysisState(graph);

        IdentityHashMap<Object, Object> replacements = new IdentityHashMap<>();
        for (Node node : graph.getNodes()) {
            NodeClass<?> nodeClass = node.getNodeClass();

            for (int i = 0; i < nodeClass.getData().getCount(); i++) {
                Object oldValue = nodeClass.getData().get(node, i);
                Object newValue = replaceAnalysisObjects(oldValue, node, replacements, universe);
                if (oldValue != newValue) {
                    nodeClass.getData().putObjectChecked(node, i, newValue);
                }
            }
            /*
             * The NodeSourcePosition is not part of the regular "data" fields, so we need to
             * process it manually.
             */
            if (trackNodeSourcePosition) {
                node.setNodeSourcePosition((NodeSourcePosition) replaceAnalysisObjects(node.getNodeSourcePosition(), node, replacements, universe));
            } else {
                node.clearNodeSourcePosition();
            }
        }

        return graph;
    }

    /**
     * The nodes produced by escape analysis need some manual patching: escape analysis requires
     * that {@link ResolvedJavaType#getInstanceFields} is stable and uses the index of a field in
     * that array also to index its own data structures. But {@link AnalysisType} and
     * {@link HostedType} cannot return fields in the same order: Fields that are not seen as
     * reachable by the static analysis are removed from the hosted type; and the layout of objects,
     * i.e., the field order, is only decided after static analysis. Therefore, we need to fix up
     * all the nodes that implicitly use the field index.
     */
    protected void transplantEscapeAnalysisState(StructuredGraph graph) {
        for (CommitAllocationNode node : graph.getNodes().filter(CommitAllocationNode.class)) {
            List<ValueNode> values = node.getValues();
            List<ValueNode> aValues = new ArrayList<>(values);
            values.clear();

            int aObjectStartIndex = 0;
            for (VirtualObjectNode virtualObject : node.getVirtualObjects()) {
                transplantVirtualObjectState(virtualObject, aValues, values, aObjectStartIndex);
                aObjectStartIndex += virtualObject.entryCount();
            }
            assert aValues.size() == aObjectStartIndex;
        }

        for (VirtualObjectState node : graph.getNodes().filter(VirtualObjectState.class)) {
            List<ValueNode> values = node.values();
            List<ValueNode> aValues = new ArrayList<>(values);
            values.clear();

            transplantVirtualObjectState(node.object(), aValues, values, 0);
        }

        for (VirtualInstanceNode node : graph.getNodes(VirtualInstanceNode.TYPE)) {
            AnalysisType aType = (AnalysisType) node.type();
            ResolvedJavaField[] aFields = node.getFields();
            assert Arrays.equals(aFields, aType.getInstanceFields(true));
            HostedField[] hFields = universe.lookup(aType).getInstanceFields(true);
            /*
             * We cannot directly write the final field `VirtualInstanceNode.fields`. So we rely on
             * the NodeClass mechanism, which is also used to transplant all other fields.
             */
            Fields nodeClassDataFields = node.getNodeClass().getData();
            for (int i = 0; i < nodeClassDataFields.getCount(); i++) {
                if (nodeClassDataFields.get(node, i) == aFields) {
                    nodeClassDataFields.putObjectChecked(node, i, hFields);
                }
            }
        }
    }

    private void transplantVirtualObjectState(VirtualObjectNode virtualObject, List<ValueNode> aValues, List<ValueNode> hValues, int aObjectStartIndex) {
        AnalysisType aType = (AnalysisType) virtualObject.type();
        if (aType.isArray()) {
            /* For arrays, there is no change between analysis and hosted elements. */
            for (int i = 0; i < virtualObject.entryCount(); i++) {
                hValues.add(aValues.get(aObjectStartIndex + i));
            }
        } else {
            /*
             * For instance fields, we need to add fields in the order of the hosted fields.
             * `AnalysisField.getPosition` gives us the index of the field in the analysis-level
             * list of field values.
             */
            assert virtualObject.entryCount() == aType.getInstanceFields(true).length;
            HostedField[] hFields = universe.lookup(aType).getInstanceFields(true);
            for (HostedField hField : hFields) {
                int aPosition = hField.wrapped.getPosition();
                assert hField.wrapped.equals(aType.getInstanceFields(true)[aPosition]);
                hValues.add(aValues.get(aObjectStartIndex + aPosition));
            }
        }
    }

    private static HostedMethod getHostedMethod(HostedUniverse universe, ResolvedJavaMethod method) {
        if (method instanceof AnalysisMethod) {
            AnalysisMethod aMethod = (AnalysisMethod) method;
            if (!aMethod.isOriginalMethod()) {
                /*
                 * Queries to the HostedUniverse must be made on the original method.
                 */
                AnalysisMethod aOrig = aMethod.getMultiMethod(MultiMethod.ORIGINAL_METHOD);
                assert aOrig != null;
                HostedMethod hOrig = universe.lookup(aOrig);
                HostedMethod hMethod = hOrig.getMultiMethod(aMethod.getMultiMethodKey());
                assert hMethod != null;
                return hMethod;
            }
        }
        return universe.lookup(method);
    }

    static Object replaceAnalysisObjects(Object obj, Node node, IdentityHashMap<Object, Object> replacements, HostedUniverse hUniverse) {
        if (obj == null) {
            return obj;
        }
        Object existingReplacement = replacements.get(obj);
        if (existingReplacement != null) {
            return existingReplacement;
        }

        Object newReplacement;

        if (obj instanceof Node) {
            throw VMError.shouldNotReachHere("Must not replace a Graal graph nodes, only data objects referenced from a node");

        } else if (obj instanceof AnalysisType) {
            newReplacement = hUniverse.lookup((AnalysisType) obj);
        } else if (obj instanceof AnalysisMethod) {
            newReplacement = getHostedMethod(hUniverse, (AnalysisMethod) obj);
        } else if (obj instanceof AnalysisField) {
            newReplacement = hUniverse.lookup((AnalysisField) obj);
        } else if (obj instanceof FieldLocationIdentity) {
            ResolvedJavaField inner = ((FieldLocationIdentity) obj).getField();
            assert inner instanceof AnalysisField;
            newReplacement = new SubstrateFieldLocationIdentity((ResolvedJavaField) replaceAnalysisObjects(inner, node, replacements, hUniverse), ((FieldLocationIdentity) obj).isImmutable());
        } else if (obj.getClass() == ObjectStamp.class) {
            ObjectStamp stamp = (ObjectStamp) obj;
            if (stamp.type() == null) {
                /* No actual type referenced, so we can keep the original object. */
                newReplacement = obj;
            } else {
                /*
                 * ObjectStamp references a type indirectly, so we need to provide a new stamp with
                 * a modified type.
                 */
                newReplacement = new ObjectStamp((ResolvedJavaType) replaceAnalysisObjects(stamp.type(), node, replacements, hUniverse), stamp.isExactType(), stamp.nonNull(), stamp.alwaysNull(),
                                stamp.isAlwaysArray());
            }
        } else if (obj.getClass() == SubstrateNarrowOopStamp.class) {
            SubstrateNarrowOopStamp stamp = (SubstrateNarrowOopStamp) obj;
            if (stamp.type() == null) {
                newReplacement = obj;
            } else {
                newReplacement = new SubstrateNarrowOopStamp((ResolvedJavaType) replaceAnalysisObjects(stamp.type(), node, replacements, hUniverse), stamp.isExactType(), stamp.nonNull(),
                                stamp.alwaysNull(),
                                stamp.isAlwaysArray(), stamp.getEncoding());
            }
        } else if (obj.getClass() == PiNode.PlaceholderStamp.class) {
            assert ((PiNode.PlaceholderStamp) obj).type() == null : "PlaceholderStamp never references a type";
            newReplacement = obj;
        } else if (obj instanceof AbstractObjectStamp) {
            throw VMError.shouldNotReachHere("missing replacement of a subclass of AbstractObjectStamp: " + obj.getClass().getTypeName());

        } else if (obj.getClass() == StampPair.class) {
            StampPair pair = (StampPair) obj;
            Stamp trustedStamp = (Stamp) replaceAnalysisObjects(pair.getTrustedStamp(), node, replacements, hUniverse);
            Stamp uncheckedStamp = (Stamp) replaceAnalysisObjects(pair.getUncheckedStamp(), node, replacements, hUniverse);
            if (trustedStamp != pair.getTrustedStamp() || uncheckedStamp != pair.getUncheckedStamp()) {
                newReplacement = StampPair.create(trustedStamp, uncheckedStamp);
            } else {
                newReplacement = pair;
            }

        } else if (obj.getClass() == ResolvedJavaMethodBytecode.class) {
            ResolvedJavaMethodBytecode bc = (ResolvedJavaMethodBytecode) obj;
            newReplacement = new ResolvedJavaMethodBytecode(getHostedMethod(hUniverse, bc.getMethod()), bc.getOrigin());

        } else if (obj instanceof Object[]) {
            Object[] originalArray = (Object[]) obj;
            Object[] copyArray = null;
            for (int i = 0; i < originalArray.length; i++) {
                Object original = originalArray[i];
                Object replaced = replaceAnalysisObjects(original, node, replacements, hUniverse);
                if (replaced != original) {
                    if (copyArray == null) {
                        copyArray = Arrays.copyOf(originalArray, originalArray.length);
                    }
                    copyArray[i] = replaced;
                }
            }
            newReplacement = copyArray != null ? copyArray : originalArray;

        } else if (obj.getClass() == NodeSourcePosition.class) {
            NodeSourcePosition nsp = (NodeSourcePosition) obj;

            NodeSourcePosition replacedCaller = (NodeSourcePosition) replaceAnalysisObjects(nsp.getCaller(), node, replacements, hUniverse);
            ResolvedJavaMethod replacedMethod = (ResolvedJavaMethod) replaceAnalysisObjects(nsp.getMethod(), node, replacements, hUniverse);
            newReplacement = new NodeSourcePosition(nsp.getSourceLanguage(), replacedCaller, replacedMethod, nsp.getBCI(), nsp.getMarker());

        } else if (obj.getClass() == BytecodePosition.class) {
            BytecodePosition nsp = (BytecodePosition) obj;

            BytecodePosition replacedCaller = (BytecodePosition) replaceAnalysisObjects(nsp.getCaller(), node, replacements, hUniverse);
            ResolvedJavaMethod replacedMethod = (ResolvedJavaMethod) replaceAnalysisObjects(nsp.getMethod(), node, replacements, hUniverse);
            newReplacement = new BytecodePosition(replacedCaller, replacedMethod, nsp.getBCI());

        } else if (obj.getClass() == SubstrateMethodPointerConstant.class) {
            SubstrateMethodPointerConstant methodPointerConstant = (SubstrateMethodPointerConstant) obj;

            MethodPointer methodPointer = methodPointerConstant.pointer();
            ResolvedJavaMethod method = methodPointer.getMethod();
            ResolvedJavaMethod replacedMethod = (ResolvedJavaMethod) replaceAnalysisObjects(method, node, replacements, hUniverse);
            newReplacement = new SubstrateMethodPointerConstant(new MethodPointer(replacedMethod));

        } else if (obj.getClass() == SubstrateMethodOffsetConstant.class) {
            SubstrateMethodOffsetConstant methodOffsetConstant = (SubstrateMethodOffsetConstant) obj;

            MethodOffset methodOffset = methodOffsetConstant.offset();
            ResolvedJavaMethod replacedMethod = (ResolvedJavaMethod) replaceAnalysisObjects(methodOffset.getMethod(), node, replacements, hUniverse);
            newReplacement = new SubstrateMethodOffsetConstant(new MethodOffset(replacedMethod));

        } else if (obj.getClass() == ComputedIndirectCallTargetNode.FieldLoad.class) {
            ComputedIndirectCallTargetNode.FieldLoad fieldLoad = (ComputedIndirectCallTargetNode.FieldLoad) obj;
            newReplacement = new ComputedIndirectCallTargetNode.FieldLoad(hUniverse.lookup(fieldLoad.getField()));
        } else if (obj.getClass() == ComputedIndirectCallTargetNode.FieldLoadIfZero.class) {
            ComputedIndirectCallTargetNode.FieldLoadIfZero fieldLoadIfZero = (ComputedIndirectCallTargetNode.FieldLoadIfZero) obj;
            newReplacement = new ComputedIndirectCallTargetNode.FieldLoadIfZero(fieldLoadIfZero.getObject(), hUniverse.lookup(fieldLoadIfZero.getField()));
        } else if (obj.getClass() == SnippetTemplate.SnippetInfo.class) {
            SnippetTemplate.SnippetInfo info = (SnippetTemplate.SnippetInfo) obj;
            newReplacement = info.copyWith((ResolvedJavaMethod) replaceAnalysisObjects(info.getMethod(), node, replacements, hUniverse));
        } else if (obj instanceof ImageHeapConstant) {
            newReplacement = obj;
        } else {
            /* Check that we do not have a class or package name that relates to the analysis. */
            assert !obj.getClass().getName().toLowerCase(Locale.ROOT).contains("analysis") : "Object " + obj + " of " + obj.getClass() + " in node " + node;
            assert !obj.getClass().getName().toLowerCase(Locale.ROOT).contains("pointsto") : "Object " + obj + " of " + obj.getClass() + " in node " + node;
            newReplacement = obj;
        }

        replacements.put(obj, newReplacement);
        return newReplacement;
    }
}
