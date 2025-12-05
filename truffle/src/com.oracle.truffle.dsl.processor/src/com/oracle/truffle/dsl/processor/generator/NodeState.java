/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.generator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.TruffleProcessorOptions;
import com.oracle.truffle.dsl.processor.generator.BitStateList.AOTPreparedState;
import com.oracle.truffle.dsl.processor.generator.BitStateList.EncodedEnumState;
import com.oracle.truffle.dsl.processor.generator.BitStateList.GuardActive;
import com.oracle.truffle.dsl.processor.generator.BitStateList.ImplicitCastState;
import com.oracle.truffle.dsl.processor.generator.BitStateList.InlinedNodeState;
import com.oracle.truffle.dsl.processor.generator.BitStateList.SpecializationActive;
import com.oracle.truffle.dsl.processor.generator.BitStateList.SpecializationCachesInitialized;
import com.oracle.truffle.dsl.processor.generator.BitStateList.SpecializationExcluded;
import com.oracle.truffle.dsl.processor.generator.BitStateList.State;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.model.CacheExpression;
import com.oracle.truffle.dsl.processor.model.GuardExpression;
import com.oracle.truffle.dsl.processor.model.InlineFieldData;
import com.oracle.truffle.dsl.processor.model.InlinedNodeData;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.Parameter;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.parser.SpecializationGroup.TypeGuard;

/**
 * Encapsulates all logic related to specialization bit-wise profiling state. It encapsulating all
 * logic so we can also use it in the parser to share the complex logic.
 */
public final class NodeState {

    // factory may be null
    private final FlatNodeGenFactory factory;
    /**
     * All state active for the given current node.
     */
    public final MultiStateBitSet activeState;
    /**
     * All state for all sharingNodes.
     */
    public final MultiStateBitSet allState;

    private final Map<SpecializationData, MultiStateBitSet> specializationStates = new HashMap<>();

    NodeState(FlatNodeGenFactory factory, MultiStateBitSet state, MultiStateBitSet allState) {
        this.factory = factory;
        this.activeState = state;
        this.allState = allState;
    }

    public static NodeState create(NodeData node, int maxBitWidth) {
        return create(null, maxBitWidth, List.of(node), node);
    }

    @SuppressWarnings("hiding")
    public static NodeState create(FlatNodeGenFactory factory, int maxBitWidth, Collection<NodeData> sharingNodes, NodeData node) {
        Objects.requireNonNull(sharingNodes);
        Objects.requireNonNull(node);

        BitStateList list = computeNodeState(factory, sharingNodes, node);
        MultiStateBitSet state = createMultiStateBitset("", maxBitWidth, node, list);
        MultiStateBitSet allState = new MultiStateBitSet(state.all, state.all);
        return new NodeState(factory, state, allState);
    }

    MultiStateBitSet lookupSpecializationState(SpecializationData specialization) {
        MultiStateBitSet specializationState = specializationStates.get(specialization);
        if (!specializationStates.containsKey(specialization)) {
            BitStateList list = computeSpecializationState(factory, specialization);
            if (list.getBitCount() > 0) {
                specializationState = createMultiStateBitset(ElementUtils.firstLetterLowerCase(specialization.getId()) + "_", FlatNodeGenFactory.DEFAULT_MAX_BIT_WIDTH, specialization.getNode(), list);
            }
            specializationStates.put(specialization, specializationState);
        }
        return specializationState;
    }

    private static MultiStateBitSet createMultiStateBitset(String namePrefix, int maxBitWidth, NodeData activeNode, BitStateList objects) {
        int maxBits = TruffleProcessorOptions.stateBitWidth(activeNode, maxBitWidth);
        return objects.splitBitSets(namePrefix, activeNode, maxBits);
    }

    private static BitStateList computeNodeState(FlatNodeGenFactory factory, Collection<NodeData> sharingNodes, NodeData node) {
        List<State<?>> stateObjects = new ArrayList<>();
        boolean aotStateAdded = false;

        Set<String> handledCaches = new HashSet<>();
        for (NodeData stateNode : sharingNodes) {
            Set<TypeGuard> implicitCasts = new LinkedHashSet<>();
            boolean needSpecialize = stateNode.needsSpecialize();

            List<SpecializationData> specializations = stateNode.getReachableSpecializations();
            for (SpecializationData specialization : specializations) {
                if (!aotStateAdded && FlatNodeGenFactory.needsAOTReset(node, sharingNodes)) {
                    stateObjects.add(new AOTPreparedState(node));
                    aotStateAdded = true;
                }

                if (needSpecialize) {
                    stateObjects.add(new SpecializationActive(specialization));
                }

                if (FlatNodeGenFactory.hasExcludeBit(specialization)) {
                    stateObjects.add(new SpecializationExcluded(specialization));
                }

                for (GuardExpression guard : specialization.getGuards()) {
                    if (FlatNodeGenFactory.guardNeedsNodeStateBit(specialization, guard)) {
                        stateObjects.add(new GuardActive(specialization, guard));
                    }
                }

                boolean useSpecializationClass = FlatNodeGenFactory.useSpecializationClass(specialization);
                for (CacheExpression cache : specialization.getCaches()) {
                    if (useSpecializationClass && FlatNodeGenFactory.canCacheBeStoredInSpecializationClass(specialization, cache)) {
                        continue;
                    }
                    if (!cache.isEncodedEnum()) {
                        continue;
                    }
                    String sharedGroup = cache.getSharedGroup();
                    if (sharedGroup == null || !handledCaches.contains(sharedGroup)) {
                        stateObjects.add(new BitStateList.EncodedEnumState(node, cache));
                        if (sharedGroup != null) {
                            handledCaches.add(sharedGroup);
                        }
                    }
                }

                int index = 0;
                for (Parameter p : specialization.getSignatureParameters()) {
                    TypeMirror targetType = p.getType();
                    Collection<TypeMirror> sourceTypes = stateNode.getTypeSystem().lookupSourceTypes(targetType);
                    if (sourceTypes.size() > 1) {
                        implicitCasts.add(new TypeGuard(stateNode.getTypeSystem(), targetType, index));
                    }
                    index++;
                }
            }
            for (TypeGuard cast : implicitCasts) {
                if (FlatNodeGenFactory.isImplicitCastUsed(stateNode.getPolymorphicExecutable(), specializations, cast)) {
                    stateObjects.add(new ImplicitCastState(stateNode, cast));
                }
            }
        }

        for (NodeData stateNode : sharingNodes) {
            for (SpecializationData specialization : stateNode.getReachableSpecializations()) {
                boolean useSpecializationClass = FlatNodeGenFactory.useSpecializationClass(specialization);
                BitStateList specializationState = computeSpecializationState(factory, specialization);
                for (CacheExpression cache : specialization.getCaches()) {
                    InlinedNodeData inline = cache.getInlinedNode();
                    if (inline == null) {
                        continue;
                    }

                    String cacheGroup = cache.getSharedGroup();
                    if (cacheGroup != null) {
                        if (handledCaches.contains(cacheGroup)) {
                            continue;
                        }
                        handledCaches.add(cacheGroup);
                    }

                    if (useSpecializationClass && FlatNodeGenFactory.canCacheBeStoredInSpecializationClass(specialization, cache)) {
                        // state is handled in computeSpecializationState
                        for (InlineFieldData fieldData : cache.getInlinedNode().getFields()) {
                            if (fieldData.isState()) {
                                if (!specializationState.contains(InlinedNodeState.class, fieldData)) {
                                    throw new AssertionError("Detected unhandled state");
                                }
                            }
                        }
                        continue;
                    }

                    SpecializationData excludeSpecialization = null;
                    if (cache.isUsedInGuard()) {
                        /*
                         * Inlined caches that are bound in guards must not be in the same state
                         * bitset as the dependent specialization bits. At the end of slow-path
                         * specialization we set the state bits of the specialization. If an inlined
                         * node in the guard changes the state bits we would override when we set
                         * the specialization bits. Alternatively we could re-read the state bit-set
                         * before we specialize in such case after the bound guards were executed,
                         * but that is very hard to get right in a thread-safe manner.
                         */
                        excludeSpecialization = specialization;
                    }

                    for (InlineFieldData fieldData : cache.getInlinedNode().getFields()) {
                        if (fieldData.isState()) {
                            stateObjects.add(new InlinedNodeState(stateNode, cache, fieldData, excludeSpecialization));
                        }
                    }
                }
            }
        }

        return new BitStateList(factory, stateObjects);
    }

    private static BitStateList computeSpecializationState(FlatNodeGenFactory factory, SpecializationData specialization) {
        List<State<?>> stateObjects = new ArrayList<>();
        if (FlatNodeGenFactory.useSpecializationClass(specialization)) {

            for (GuardExpression guard : specialization.getGuards()) {
                if (FlatNodeGenFactory.guardNeedsSpecializationStateBit(specialization, guard)) {
                    stateObjects.add(new GuardActive(specialization, guard));
                }
            }

            if (FlatNodeGenFactory.specializationNeedsInitializedBit(specialization)) {
                stateObjects.add(new SpecializationCachesInitialized(specialization));
            }

            for (CacheExpression cache : specialization.getCaches()) {
                if (cache.getInlinedNode() != null && FlatNodeGenFactory.canCacheBeStoredInSpecializationClass(specialization, cache)) {
                    for (InlineFieldData field : cache.getInlinedNode().getFields()) {
                        if (field.isState()) {
                            stateObjects.add(new InlinedNodeState(specialization.getNode(), cache, field, null));
                        }
                    }
                } else if (cache.isEncodedEnum()) {
                    stateObjects.add(new EncodedEnumState(specialization.getNode(), cache));
                }
            }
        }
        return new BitStateList(factory, stateObjects);
    }

}
