/*
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.extended;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodes.spi.Simplifiable;
import org.graalvm.compiler.nodes.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ProfileData.ProfileSource;
import org.graalvm.compiler.nodes.ProfileData.SwitchProbabilityData;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.SwitchFoldable;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * The {@code IntegerSwitchNode} represents a switch on integer keys, with a sorted array of key
 * values. The actual implementation of the switch will be decided by the backend.
 */
@NodeInfo
public final class IntegerSwitchNode extends SwitchNode implements LIRLowerable, Simplifiable, SwitchFoldable {
    public static final NodeClass<IntegerSwitchNode> TYPE = NodeClass.create(IntegerSwitchNode.class);

    protected final int[] keys;
    /**
     * True if keys are contiguous ([n, n+1, n+2, n+3, ..., n+k]).
     *
     * When this is the case, we can lookup successor index in O(1).
     */
    protected final boolean areKeysContiguous;

    public IntegerSwitchNode(ValueNode value, AbstractBeginNode[] successors, int[] keys, int[] keySuccessors, SwitchProbabilityData profileData) {
        super(TYPE, value, successors, keySuccessors, profileData);
        assert keySuccessors.length == keys.length + 1;
        assert keySuccessors.length == profileData.getKeyProbabilities().length;
        this.keys = keys;
        areKeysContiguous = keys.length > 0 && keys[keys.length - 1] - keys[0] + 1 == keys.length;
        assert value.stamp(NodeView.DEFAULT) instanceof PrimitiveStamp && value.stamp(NodeView.DEFAULT).getStackKind().isNumericInteger();
        assert assertSorted();
        assert assertNoUntargettedSuccessor();
    }

    private boolean assertSorted() {
        for (int i = 1; i < keys.length; i++) {
            assert keys[i - 1] < keys[i];
        }
        return true;
    }

    private boolean assertNoUntargettedSuccessor() {
        boolean[] checker = new boolean[successors.size()];
        for (int successorIndex : keySuccessors) {
            checker[successorIndex] = true;
        }
        checker[defaultSuccessorIndex()] = true;
        for (boolean b : checker) {
            assert b;
        }
        return true;
    }

    public IntegerSwitchNode(ValueNode value, int successorCount, int[] keys, int[] keySuccessors, SwitchProbabilityData profileData) {
        this(value, new AbstractBeginNode[successorCount], keys, keySuccessors, profileData);
    }

    @Override
    public boolean isSorted() {
        return true;
    }

    /**
     * Gets the key at the specified index.
     *
     * @param i the index
     * @return the key at that index
     */
    @Override
    public JavaConstant keyAt(int i) {
        return JavaConstant.forInt(keys[i]);
    }

    /**
     * Gets the key at the specified index, as a java int.
     */
    @Override
    public int intKeyAt(int i) {
        return keys[i];
    }

    @Override
    public int keyCount() {
        return keys.length;
    }

    @Override
    public boolean equalKeys(SwitchNode switchNode) {
        if (!(switchNode instanceof IntegerSwitchNode)) {
            return false;
        }
        IntegerSwitchNode other = (IntegerSwitchNode) switchNode;
        return Arrays.equals(keys, other.keys);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.emitSwitch(this);
    }

    public AbstractBeginNode successorAtKey(int key) {
        return blockSuccessor(successorIndexAtKey(key));
    }

    public int successorIndexAtKey(int key) {
        if (areKeysContiguous && keys[0] <= key && key <= keys[keys.length - 1]) {
            return keySuccessorIndex(key - keys[0]);
        }
        int index = Arrays.binarySearch(keys, key);
        if (index >= 0) {
            return keySuccessorIndex(index);
        }
        return keySuccessorIndex(keyCount());
    }

    @Override
    public void simplify(SimplifierTool tool) {
        NodeView view = NodeView.from(tool);
        if (blockSuccessorCount() == 1) {
            tool.addToWorkList(defaultSuccessor());
            graph().removeSplitPropagate(this, defaultSuccessor());
        } else if (value() instanceof ConstantNode) {
            killOtherSuccessors(tool, successorIndexAtKey(value().asJavaConstant().asInt()));
        } else if (tryOptimizeEnumSwitch(tool)) {
            return;
        } else if (tryRemoveUnreachableKeys(tool, value().stamp(view))) {
            return;
        } else if (switchTransformationOptimization(tool)) {
            return;
        } else if (tryMergeCommonSuccessors()) {
            return;
        }
    }

    private void addSuccessorForDeletion(AbstractBeginNode defaultNode) {
        successors.add(defaultNode);
    }

    @Override
    public Node getNextSwitchFoldableBranch() {
        return defaultSuccessor();
    }

    @Override
    public boolean isInSwitch(ValueNode switchValue) {
        return value == switchValue;
    }

    @Override
    public void cutOffCascadeNode() {
        AbstractBeginNode toKill = defaultSuccessor();
        clearSuccessors();
        addSuccessorForDeletion(toKill);
    }

    @Override
    public void cutOffLowestCascadeNode() {
        clearSuccessors();
    }

    @Override
    public AbstractBeginNode getDefault() {
        return defaultSuccessor();
    }

    @Override
    public ValueNode switchValue() {
        return value();
    }

    @Override
    public boolean isNonInitializedProfile() {
        return !ProfileSource.isTrusted(profileData.getProfileSource());
    }

    @Override
    public ProfileSource profileSource() {
        return profileData.getProfileSource();
    }

    private static final class MergeCoalesceBuilder {

        private final List<KeyData> newKeyData = new ArrayList<>();
        private final ArrayList<AbstractBeginNode> newSuccessors = new ArrayList<>();
        private final EconomicMap<AbstractMergeNode, MergeMarker> mergeKeys = EconomicMap.create();
        private final IntegerSwitchNode switchNode;

        private int newDefaultSuccessor = -1;
        private double newDefaultProbability;

        private boolean canRewire;

        MergeCoalesceBuilder(IntegerSwitchNode switchNode) {
            this.switchNode = switchNode;
        }

        private static final class MergeMarker implements Iterable<Integer> {
            private final ArrayList<Integer> indexes = new ArrayList<>();
            private final EconomicSet<EndNode> ends = EconomicSet.create();
            private boolean hasDefault = false;

            private static final int DEFAULT_KEY = -1;

            void update(int index, EndNode end) {
                if (index == DEFAULT_KEY) {
                    hasDefault = true;
                }
                indexes.add(index);
                ends.add(end);
            }

            int visitedEnds() {
                return ends.size();
            }

            boolean hasDefault() {
                return hasDefault;
            }

            Iterable<EndNode> ends() {
                return ends;
            }

            @Override
            public Iterator<Integer> iterator() {
                return indexes.iterator();
            }
        }

        boolean canRewire() {
            return canRewire;
        }

        void tryMergeBranch(AbstractBeginNode begin, int i) {
            tryMergeCommon(begin, false, i);
        }

        void tryMergeDefault(AbstractBeginNode begin) {
            tryMergeCommon(begin, true, MergeMarker.DEFAULT_KEY);
        }

        private void tryMergeCommon(AbstractBeginNode begin, boolean isDefault, int i) {
            if (begin instanceof BeginNode && begin.hasNoUsages()) {
                FixedNode next = begin.next();
                if (next instanceof EndNode) {
                    EndNode endNode = (EndNode) next;
                    AbstractMergeNode merge = endNode.merge();
                    if ((merge instanceof MergeNode) &&
                                    merge.phis().isEmpty()) {
                        // Multiple keys wire to the same trivial merge.
                        if (mergeKeys.containsKey(merge)) {
                            mergeKeys.get(merge).update(i, endNode);
                            canRewire = true;
                        } else {
                            MergeMarker indexes = new MergeMarker();
                            indexes.update(i, endNode);
                            mergeKeys.put(merge, indexes);
                        }
                        return;
                    }
                }
            }
            if (!isDefault) {
                assert i >= 0 && i < switchNode.keyCount();
                addKeyData(addNewSuccessor(begin, newSuccessors), i);
            }
        }

        void prepareMerge() {
            canRewire = false;

            MapCursor<AbstractMergeNode, MergeMarker> cursor = mergeKeys.getEntries();
            newDefaultProbability = switchNode.defaultProbability();

            while (cursor.advance()) {
                AbstractMergeNode merge = cursor.getKey();
                MergeMarker marker = cursor.getValue();
                if (marker.visitedEnds() > 1) {
                    /* Ensure that we coalesce more than a single branch. */
                    canRewire = true;
                    boolean partialCoalesce = merge.forwardEndCount() != marker.visitedEnds();

                    /* Rewire anchoring links, etc... to the new node */
                    AbstractBeginNode begin;
                    if (partialCoalesce) {
                        /*
                         * If one or more branches are not from the switch, or one of them can not
                         * be coalesced (/ex: begin used as a guard), we can still save a few
                         * begin/ends.
                         */
                        for (EndNode end : marker.ends()) {
                            /* Detach visited ends from the merge */
                            merge.removeEnd(end);
                        }
                        /* Attach a new end that will serve as the new target branch */
                        EndNode newEnd = switchNode.graph().add(new EndNode());
                        merge.addForwardEnd(newEnd);
                        begin = BeginNode.begin(newEnd);
                    } else {
                        FixedNode next = merge.next();
                        merge.setNext(null);
                        begin = BeginNode.begin(next);
                        merge.replaceAtUsages(begin, InputType.Anchor);
                        merge.replaceAtUsages(begin, InputType.Guard);
                        assert merge.hasNoUsages();
                    }

                    int successorIndex = addNewSuccessor(begin, newSuccessors);
                    if (marker.hasDefault()) {
                        assert newDefaultSuccessor == -1 : "Multiple default keys ?";
                        /*
                         * Rewire all keys pointing to same target as default to the default
                         * successor, and delete said kys from the switch.
                         */
                        newDefaultSuccessor = successorIndex;
                        for (int index : marker) {
                            if (index != MergeMarker.DEFAULT_KEY) {
                                newDefaultProbability += switchNode.keyProbability(index);
                            }
                        }
                    } else {
                        for (int index : marker) {
                            /* Rewire to the same successor */
                            AbstractBeginNode successorBegin = switchNode.keySuccessor(index);
                            assert successorBegin.next() instanceof EndNode;
                            addKeyData(successorIndex, index);
                        }
                    }
                } else {
                    /*
                     * Merge has an end that is not part of the switch, or it could not merge one of
                     * its branches.
                     */
                    for (int i : marker) {
                        if (i != MergeMarker.DEFAULT_KEY) {
                            addKeyData(addNewSuccessor(switchNode.keySuccessor(i), newSuccessors), i);
                        }
                    }
                }
            }
            if (newDefaultSuccessor == -1) {
                newDefaultSuccessor = addNewSuccessor(switchNode.defaultSuccessor(), newSuccessors);
            }
        }

        private boolean addKeyData(int successorIndex, int index) {
            return newKeyData.add(new KeyData(switchNode.intKeyAt(index), switchNode.keyProbability(index), successorIndex));
        }

        public void commit() {
            switchNode.doReplace(switchNode.value(), newKeyData, newSuccessors, newDefaultSuccessor, newDefaultProbability);
        }

    }

    /**
     * Coalesces branches of the switch that trivially merges together.
     */
    public boolean tryMergeCommonSuccessors() {
        /*-
         *              |  SWITCH  |
         *              |__________|
         *               /    |    \
         *              /     |     \
         *           | B |  | B |  | B |
         *             \      |      /
         *              \     |     /
         *              |   MERGE   |
         *                    |
         *                  | F |
         *
         *
         *                    |
         *                    v
         *
         *              |  SWITCH  |
         *              |__________|
         *                    |
         *                    |
         *                  | F |
         *
         */

        MergeCoalesceBuilder builder = new MergeCoalesceBuilder(this);
        for (int i = 0; i < keyCount(); i++) {
            builder.tryMergeBranch(keySuccessor(i), i);
        }
        // Try to coalesce also the default branch.
        builder.tryMergeDefault(defaultSuccessor());
        if (!builder.canRewire()) {
            // Nothing to do
            return false;
        }
        builder.prepareMerge();
        if (!builder.canRewire()) {
            // Detected merges could not be coalesced.
            return false;
        }
        builder.commit();
        return true;
    }

    static final class KeyData {
        final int key;
        final double keyProbability;
        final int keySuccessor;

        KeyData(int key, double keyProbability, int keySuccessor) {
            this.key = key;
            this.keyProbability = keyProbability;
            this.keySuccessor = keySuccessor;
        }
    }

    /**
     * Remove unreachable keys from the switch based on the stamp of the value, i.e., based on the
     * known range of the switch value.
     */
    public boolean tryRemoveUnreachableKeys(SimplifierTool tool, Stamp valueStamp) {
        if (!(valueStamp instanceof IntegerStamp)) {
            return false;
        }
        IntegerStamp integerStamp = (IntegerStamp) valueStamp;
        if (integerStamp.isUnrestricted()) {
            return false;
        }

        List<KeyData> newKeyDatas = new ArrayList<>(keys.length);
        ArrayList<AbstractBeginNode> newSuccessors = new ArrayList<>(blockSuccessorCount());
        for (int i = 0; i < keys.length; i++) {
            if (integerStamp.contains(keys[i]) && keySuccessor(i) != defaultSuccessor()) {
                newKeyDatas.add(new KeyData(keys[i], getKeyProbabilities()[i], addNewSuccessor(keySuccessor(i), newSuccessors)));
            }
        }

        if (newKeyDatas.size() == keys.length) {
            /* All keys are reachable. */
            return false;

        } else if (newKeyDatas.size() == 0) {
            if (tool != null) {
                tool.addToWorkList(defaultSuccessor());
            }
            graph().removeSplitPropagate(this, defaultSuccessor());
            return true;

        } else {
            int newDefaultSuccessor = addNewSuccessor(defaultSuccessor(), newSuccessors);
            double newDefaultProbability = getKeyProbabilities()[getKeyProbabilities().length - 1];
            doReplace(value(), newKeyDatas, newSuccessors, newDefaultSuccessor, newDefaultProbability);
            return true;
        }
    }

    /**
     * For switch statements on enum values, the Java compiler has to generate complicated code:
     * because {@link Enum#ordinal()} can change when recompiling an enum, it cannot be used
     * directly as the value that is switched on. An intermediate int[] array, which is initialized
     * once at run time based on the actual {@link Enum#ordinal()} values, is used.
     * <p>
     * The {@link ConstantFieldProvider} of Graal already detects the int[] arrays and marks them as
     * {@link ConstantNode#isDefaultStable() stable}, i.e., the array elements are constant. The
     * code in this method detects array loads from such a stable array and re-wires the switch to
     * use the keys from the array elements, so that the array load is unnecessary.
     */
    private boolean tryOptimizeEnumSwitch(SimplifierTool tool) {
        if (!(value() instanceof LoadIndexedNode)) {
            /* Not the switch pattern we are looking for. */
            return false;
        }
        LoadIndexedNode loadIndexed = (LoadIndexedNode) value();
        if (loadIndexed.hasMoreThanOneUsage()) {
            /*
             * The array load is necessary for other reasons too, so there is no benefit optimizing
             * the switch.
             */
            return false;
        }
        assert loadIndexed.usages().first() == this;

        ValueNode newValue = loadIndexed.index();
        JavaConstant arrayConstant = loadIndexed.array().asJavaConstant();
        if (arrayConstant == null || ((ConstantNode) loadIndexed.array()).getStableDimension() != 1 || !((ConstantNode) loadIndexed.array()).isDefaultStable()) {
            /*
             * The array is a constant that we can optimize. We require the array elements to be
             * constant too, since we put them as literal constants into the switch keys.
             */
            return false;
        }

        Integer optionalArrayLength = tool.getConstantReflection().readArrayLength(arrayConstant);
        if (optionalArrayLength == null) {
            /* Loading a constant value can be denied by the VM. */
            return false;
        }
        int arrayLength = optionalArrayLength;

        Map<Integer, List<Integer>> reverseArrayMapping = new HashMap<>();
        for (int i = 0; i < arrayLength; i++) {
            JavaConstant elementConstant = tool.getConstantReflection().readArrayElement(arrayConstant, i);
            if (elementConstant == null || elementConstant.getJavaKind() != JavaKind.Int) {
                /* Loading a constant value can be denied by the VM. */
                return false;
            }
            int element = elementConstant.asInt();

            /*
             * The value loaded from the array is the old switch key, the index into the array is
             * the new switch key. We build a mapping from the old switch key to new keys.
             */
            reverseArrayMapping.computeIfAbsent(element, e -> new ArrayList<>()).add(i);
        }

        /* Build high-level representation of new switch keys. */
        List<KeyData> newKeyDatas = new ArrayList<>(arrayLength);
        ArrayList<AbstractBeginNode> newSuccessors = new ArrayList<>(blockSuccessorCount());
        for (int i = 0; i < keys.length; i++) {
            List<Integer> newKeys = reverseArrayMapping.get(keys[i]);
            if (newKeys == null || newKeys.size() == 0) {
                /* The switch case is unreachable, we can ignore it. */
                continue;
            }

            /*
             * We do not have detailed profiling information about the individual new keys, so we
             * have to assume they split the probability of the old key.
             */
            double newKeyProbability = getKeyProbabilities()[i] / newKeys.size();
            int newKeySuccessor = addNewSuccessor(keySuccessor(i), newSuccessors);

            for (int newKey : newKeys) {
                newKeyDatas.add(new KeyData(newKey, newKeyProbability, newKeySuccessor));
            }
        }

        int newDefaultSuccessor = addNewSuccessor(defaultSuccessor(), newSuccessors);
        double newDefaultProbability = getKeyProbabilities()[getKeyProbabilities().length - 1];

        if (loadIndexed.getBoundsCheck() == null) {
            /*
             * We remove the array load, but we still need to preserve exception semantics by
             * keeping the bounds check. Fortunately the array length is a constant.
             */
            LogicNode boundsCheck = graph().unique(new IntegerBelowNode(newValue, ConstantNode.forInt(arrayLength, graph())));
            graph().addBeforeFixed(this, graph().add(new FixedGuardNode(boundsCheck, DeoptimizationReason.BoundsCheckException, DeoptimizationAction.InvalidateReprofile)));
        }

        /*
         * Build the low-level representation of the new switch keys and replace ourself with a new
         * node.
         */
        doReplace(newValue, newKeyDatas, newSuccessors, newDefaultSuccessor, newDefaultProbability);

        /* The array load is now unnecessary. */
        assert loadIndexed.hasNoUsages();
        GraphUtil.removeFixedWithUnusedInputs(loadIndexed);

        return true;
    }

    private static int addNewSuccessor(AbstractBeginNode newSuccessor, ArrayList<AbstractBeginNode> newSuccessors) {
        int index = newSuccessors.indexOf(newSuccessor);
        if (index == -1) {
            index = newSuccessors.size();
            newSuccessors.add(newSuccessor);
        }
        return index;
    }

    private void doReplace(ValueNode newValue, List<KeyData> newKeyDatas, ArrayList<AbstractBeginNode> newSuccessors, int newDefaultSuccessor, double newDefaultProbability) {
        /* Sort the new keys (invariant of the IntegerSwitchNode). */
        newKeyDatas.sort(Comparator.comparingInt(k -> k.key));

        /* Create the final data arrays. */
        int newKeyCount = newKeyDatas.size();
        int[] newKeys = new int[newKeyCount];
        double[] newKeyProbabilities = new double[newKeyCount + 1];
        int[] newKeySuccessors = new int[newKeyCount + 1];

        for (int i = 0; i < newKeyCount; i++) {
            KeyData keyData = newKeyDatas.get(i);
            newKeys[i] = keyData.key;
            newKeyProbabilities[i] = keyData.keyProbability;
            newKeySuccessors[i] = keyData.keySuccessor;
        }

        newKeySuccessors[newKeyCount] = newDefaultSuccessor;
        newKeyProbabilities[newKeyCount] = newDefaultProbability;

        /* Normalize new probabilities so that they sum up to 1. */
        double totalProbability = 0;
        for (double probability : newKeyProbabilities) {
            totalProbability += probability;
        }
        if (totalProbability > 0) {
            for (int i = 0; i < newKeyProbabilities.length; i++) {
                newKeyProbabilities[i] /= totalProbability;
            }
        } else {
            for (int i = 0; i < newKeyProbabilities.length; i++) {
                newKeyProbabilities[i] = 1.0 / newKeyProbabilities.length;
            }
        }

        /*
         * Surviving successors have to be cleaned before adding the new node to the graph. Keep the
         * dead ones attached to the old node for later cleanup.
         */
        for (int i = 0; i < successors.size(); i++) {
            if (newSuccessors.contains(successors.get(i))) {
                successors.set(i, null);
            }
        }

        /*
         * Create the new switch node. This is done before removing dead successors as `killCFG`
         * could edit some of the inputs (e.g., if `newValue` is a loop-phi of the loop that dies
         * while removing successors).
         */
        AbstractBeginNode[] successorsArray = newSuccessors.toArray(new AbstractBeginNode[newSuccessors.size()]);
        SwitchNode newSwitch = graph().add(new IntegerSwitchNode(newValue, successorsArray, newKeys, newKeySuccessors, profileData.copy(newKeyProbabilities)));

        /* Replace ourselves with the new switch */
        ((FixedWithNextNode) predecessor()).setNext(newSwitch);

        // Remove the old switch and the dead successors.
        GraphUtil.killCFG(this);
    }

    @Override
    public Stamp getValueStampForSuccessor(AbstractBeginNode beginNode) {
        Stamp result = null;
        if (beginNode != this.defaultSuccessor()) {
            for (int i = 0; i < keyCount(); i++) {
                if (keySuccessor(i) == beginNode) {
                    if (result == null) {
                        result = StampFactory.forConstant(keyAt(i));
                    } else {
                        result = result.meet(StampFactory.forConstant(keyAt(i)));
                    }
                }
            }
        }
        return result;
    }
}
