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
import java.util.List;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.generator.BitStateList.EncodedEnumState;
import com.oracle.truffle.dsl.processor.generator.BitStateList.GuardActive;
import com.oracle.truffle.dsl.processor.generator.BitStateList.ImplicitCastState;
import com.oracle.truffle.dsl.processor.generator.BitStateList.SpecializationActive;
import com.oracle.truffle.dsl.processor.generator.BitStateList.SpecializationExcluded;
import com.oracle.truffle.dsl.processor.generator.BitStateList.State;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.FrameState;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.LocalVariable;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.ExecutableTypeData;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.parser.SpecializationGroup.TypeGuard;

public final class MultiStateBitSet extends MultiBitSet {

    /*
     * All bitsets in used by other nodes in the same generated class. E.g. nodes in exports are all
     * generated into the same class.
     */
    final List<BitSet> all;

    MultiStateBitSet(List<BitSet> all, List<BitSet> active) {
        super(active);
        this.all = all;
    }

    public void clearLoaded(FrameState frameState) {
        for (BitSet state : all) {
            state.clearLoaded(frameState);
        }
    }

    <T> BitSet findSet(StateQuery query) {
        for (BitSet state : all) {
            if (state.contains(query)) {
                return state;
            }
        }
        return null;
    }

    <T> BitSet findSet(Class<? extends State<T>> clazz, T param) {
        return findSet(StateQuery.create(clazz, param));
    }

    int getAllCapacity() {
        int length = 0;
        for (BitSet a : all) {
            length += a.getBitCount();
        }
        return length;
    }

    List<CodeVariableElement> createCachedFields() {
        List<CodeVariableElement> variables = new ArrayList<>();
        for (BitSet bitSet : all) {
            CodeVariableElement v = bitSet.createField();
            if (v != null) {
                variables.add(v);
            }
        }
        return variables;
    }

    void addParametersTo(FrameState frameState, CodeExecutableElement targetMethod) {
        for (BitSet set : getSets()) {
            LocalVariable local = frameState.get(set.getName());
            if (local != null) {
                targetMethod.addParameter(local.createParameter());
            }
        }
    }

    void removeParametersFrom(CodeExecutableElement targetMethod) {
        for (VariableElement var : targetMethod.getParameters().toArray(new VariableElement[0])) {
            for (BitSet set : getSets()) {
                if (var.getSimpleName().toString().equals(set.getName())) {
                    targetMethod.getParameters().remove(var);
                }
            }
        }
    }

    void addReferencesTo(FrameState frameState, CodeTreeBuilder builder) {
        for (BitSet set : getSets()) {
            LocalVariable local = frameState.get(set.getName());
            if (local != null) {
                builder.tree(local.createReference());
            }
        }
    }

    void addReferencesTo(FrameState frameState, CodeTreeBuilder builder, StateQuery... relevantQuery) {
        for (BitSet set : getSets()) {
            LocalVariable local = frameState.get(set.getName());
            if (local != null) {
                if (set.contains(relevantQuery)) {
                    builder.tree(local.createReference());
                }
            }
        }
    }

    CodeTree createLoad(FrameState frameState, StateQuery... relevantQuery) {
        return createLoadImpl(getSets(), frameState, false, relevantQuery);
    }

    CodeTree createForceLoad(FrameState frameState, StateQuery... relevantQuery) {
        return createLoadImpl(getSets(), frameState, true, relevantQuery);
    }

    private static CodeTree createLoadImpl(List<? extends BitSet> sets, FrameState frameState, boolean force, StateQuery... relevantQuery) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        for (BitSet bitSet : sets) {
            if (bitSet.contains(relevantQuery)) {
                builder.tree(bitSet.createLoad(frameState, force));
            }
        }
        return builder.build();
    }

    CodeTree createLoadAll(FrameState frameState, StateQuery relevantQuery) {
        return createLoadImpl(all, frameState, false, relevantQuery);
    }

    CodeTree createLoadFastPath(FrameState frameState, ExecutableTypeData executable, List<SpecializationData> specializations) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        for (BitSet bitSet : getSets()) {
            if (isRelevantForFastPath(frameState, bitSet, executable, specializations)) {
                builder.tree(bitSet.createLoad(frameState));
            }
        }
        return builder.build();
    }

    CodeTree createLoadSlowPath(FrameState frameState, List<SpecializationData> specializations, boolean force) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();

        for (BitSet bitSet : getSets()) {
            if (isRelevantForSlowPath(bitSet, specializations)) {
                builder.tree(bitSet.createLoad(frameState, force));
            }
        }
        return builder.build();
    }

    static boolean isRelevantForFastPath(FrameState frameState, BitSet bitSet, ExecutableTypeData executable, Collection<SpecializationData> usedSpecializations) {
        if (!frameState.isSkipStateChecks() && bitSet.getStates().contains(StateQuery.create(SpecializationActive.class, usedSpecializations))) {
            return true;
        }
        if (bitSet.getStates().contains(FlatNodeGenFactory.AOT_PREPARED)) {
            return true;
        }
        for (SpecializationData specialization : usedSpecializations) {
            if (bitSet.getStates().contains(StateQuery.create(EncodedEnumState.class, specialization.getCaches()))) {
                return true;
            }
        }
        for (SpecializationData specialization : usedSpecializations) {
            for (TypeGuard implicitTypeGuard : specialization.getImplicitTypeGuards()) {
                if (specialization.isImplicitTypeGuardUsed(implicitTypeGuard, executable)) {
                    if (bitSet.getStates().contains(StateQuery.create(ImplicitCastState.class, implicitTypeGuard))) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @SuppressWarnings("unused")
    static boolean isRelevantForSlowPath(BitSet bitSet, Collection<SpecializationData> usedSpecializations) {
        if (bitSet.getStates().contains(StateQuery.create(SpecializationActive.class, usedSpecializations))) {
            return true;
        }
        if (bitSet.getStates().contains(StateQuery.create(SpecializationExcluded.class, usedSpecializations))) {
            return true;
        }
        for (GuardActive state : bitSet.getStates().queryStates(GuardActive.class)) {
            if (usedSpecializations.contains(state.getDependentSpecialization())) {
                return true;
            }
        }
        for (SpecializationData specialization : usedSpecializations) {
            if (bitSet.contains(StateQuery.create(EncodedEnumState.class, specialization.getCaches()))) {
                return true;
            }
        }
        for (ImplicitCastState state : bitSet.getStates().queryStates(ImplicitCastState.class)) {
            if (FlatNodeGenFactory.isImplicitCastUsed(state.node.getPolymorphicExecutable(), usedSpecializations, state.key)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unused")
    static boolean isRelevantForQuickening(BitSet bitSet, Collection<SpecializationData> usedSpecializations) {
        if (bitSet.getStates().contains(StateQuery.create(SpecializationActive.class, usedSpecializations))) {
            return true;
        }
        for (ImplicitCastState state : bitSet.getStates().queryStates(ImplicitCastState.class)) {
            TypeGuard guard = state.key;
            int signatureIndex = guard.getSignatureIndex();
            for (SpecializationData specialization : usedSpecializations) {
                TypeMirror specializationType = specialization.getSignatureParameters().get(signatureIndex).getType();
                if (!state.node.getTypeSystem().lookupByTargetType(specializationType).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

}
