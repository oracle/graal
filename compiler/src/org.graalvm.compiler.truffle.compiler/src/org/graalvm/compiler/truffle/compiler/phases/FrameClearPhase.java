/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.truffle.compiler.phases;

import static org.graalvm.compiler.truffle.common.TruffleCompilerRuntime.getRuntime;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.virtual.EscapeObjectState;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.compiler.substitutions.KnownTruffleTypes;
import org.graalvm.compiler.virtual.nodes.MaterializedObjectState;
import org.graalvm.compiler.virtual.nodes.VirtualObjectState;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class FrameClearPhase extends BasePhase<CoreProviders> {
    private final ResolvedJavaType frameType;
    private final int tagArrayIndex;
    private final int objectArrayIndex;
    private final int primitiveArrayIndex;

    private final int illegalTag;
    private final int objectTag;

    private ValueNode nullConstant;
    private ValueNode zeroConstant;
    private IntegerStamp illegalStamp;

    public FrameClearPhase(KnownTruffleTypes knownTruffleTypes) {
        this(knownTruffleTypes.classFrameClass, knownTruffleTypes.fieldTags, knownTruffleTypes.fieldLocals, knownTruffleTypes.fieldPrimitiveLocals);
    }

    private FrameClearPhase(ResolvedJavaType frameType,
                    ResolvedJavaField tagArray, ResolvedJavaField objectArray, ResolvedJavaField primitiveArray) {
        this.frameType = frameType;
        this.tagArrayIndex = findFieldIndex(frameType, tagArray);
        this.objectArrayIndex = findFieldIndex(frameType, objectArray);
        this.primitiveArrayIndex = findFieldIndex(frameType, primitiveArray);
        assert tagArrayIndex >= 0 && objectArrayIndex >= 0 && primitiveArrayIndex >= 0;

        TruffleCompilerRuntime runtime = getRuntime();
        this.illegalTag = runtime.getFrameSlotKindTagForJavaKind(JavaKind.Illegal);
        this.objectTag = runtime.getFrameSlotKindTagForJavaKind(JavaKind.Object);

        illegalStamp = StampFactory.forInteger(JavaKind.Byte.getBitCount(), illegalTag, illegalTag);
    }

    private static int findFieldIndex(ResolvedJavaType type, ResolvedJavaField field) {
        ResolvedJavaField[] fields = type.getInstanceFields(true);
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].equals(field)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        nullConstant = ConstantNode.defaultForKind(JavaKind.Object, graph);
        zeroConstant = ConstantNode.defaultForKind(JavaKind.Long, graph);

        for (FrameState fs : graph.getNodes(FrameState.TYPE)) {
            EconomicMap<VirtualObjectNode, EscapeObjectState> objectStates = getObjectStateMappings(fs);
            for (EscapeObjectState objectState : objectStates.getValues()) {
                if ((objectState instanceof VirtualObjectState) && objectState.object().type().equals(frameType)) {
                    VirtualObjectState vObjState = (VirtualObjectState) objectState;
                    ValueNode tagArrayValue = vObjState.values().get(tagArrayIndex);
                    if ((tagArrayValue instanceof VirtualArrayNode) &&
                                    (vObjState.values().get(objectArrayIndex) instanceof VirtualArrayNode) &&
                                    (vObjState.values().get(primitiveArrayIndex) instanceof VirtualArrayNode)) {
                        EscapeObjectState tagArrayVirtual = objectStates.get((VirtualArrayNode) tagArrayValue);
                        EscapeObjectState objectArrayVirtual = objectStates.get((VirtualArrayNode) vObjState.values().get(objectArrayIndex));
                        EscapeObjectState primitiveArrayVirtual = objectStates.get((VirtualArrayNode) vObjState.values().get(primitiveArrayIndex));

                        assert tagArrayVirtual instanceof VirtualObjectState && objectArrayVirtual instanceof VirtualObjectState && primitiveArrayVirtual instanceof VirtualObjectState;

                        int length = ((VirtualArrayNode) tagArrayValue).entryCount();
                        for (int i = 0; i < length; i++) {
                            maybeClearFrameSlot((VirtualObjectState) tagArrayVirtual, (VirtualObjectState) objectArrayVirtual, (VirtualObjectState) primitiveArrayVirtual, i, fs);
                        }
                    }
                }
            }
        }
    }

    private void maybeClearFrameSlot(VirtualObjectState tagArrayVirtual, VirtualObjectState objectArrayVirtual, VirtualObjectState primitiveArrayVirtual, int i, FrameState fs) {
        ValueNode tagNode = tagArrayVirtual.values().get(i);
        if (tagNode.isJavaConstant()) {
            int tag = tagNode.asJavaConstant().asInt();
            if (tag == illegalTag) {
                objectArrayVirtual.values().set(i, nullConstant);
                primitiveArrayVirtual.values().set(i, zeroConstant);
            } else {
                VirtualObjectState toClear = (tag == objectTag) ? primitiveArrayVirtual : objectArrayVirtual;
                ValueNode toSet = (tag == objectTag) ? zeroConstant : nullConstant;
                toClear.values().set(i, toSet);
            }
        } else {
            Stamp tagStamp = tagNode.stamp(NodeView.DEFAULT);
            if (!tagStamp.join(illegalStamp).isEmpty()) {
                throw new PermanentBailoutException("Inconsistent use of Frame.clear() detected.\n" + fs.getNodeSourcePosition());
            }
        }
    }

    private EconomicMap<VirtualObjectNode, EscapeObjectState> getObjectStateMappings(FrameState fs) {
        EconomicMap<VirtualObjectNode, EscapeObjectState> objectStates = EconomicMap.create(Equivalence.IDENTITY);
        FrameState current = fs;
        do {
            if (current.virtualObjectMappingCount() > 0) {
                for (EscapeObjectState state : current.virtualObjectMappings()) {
                    if (!objectStates.containsKey(state.object())) {
                        if (!(state instanceof MaterializedObjectState) || ((MaterializedObjectState) state).materializedValue() != state.object()) {
                            objectStates.put(state.object(), state);
                        }
                    }
                }
            }
            current = current.outerFrameState();
        } while (current != null);
        return objectStates;
    }
}
