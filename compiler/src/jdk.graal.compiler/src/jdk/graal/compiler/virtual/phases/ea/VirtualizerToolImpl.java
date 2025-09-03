/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.virtual.phases.ea;

import static jdk.graal.compiler.core.common.GraalOptions.MaximumEscapeAnalysisArrayLength;

import java.util.List;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.calc.UnpackEndianHalfNode;
import jdk.graal.compiler.nodes.java.MonitorIdNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.CoreProvidersDelegate;
import jdk.graal.compiler.nodes.spi.NodeWithState;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.virtual.VirtualArrayNode;
import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.DefaultJavaLoweringProvider;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Forwards calls from {@link VirtualizerTool} to the actual {@link PartialEscapeBlockState}.
 */
class VirtualizerToolImpl extends CoreProvidersDelegate implements VirtualizerTool, CanonicalizerTool {

    private final PartialEscapeClosure<?> closure;
    private final Assumptions assumptions;
    private final OptionValues options;
    private final DebugContext debug;
    private ConstantNode illegalConstant;

    VirtualizerToolImpl(CoreProviders providers, PartialEscapeClosure<?> closure, Assumptions assumptions, OptionValues options, DebugContext debug) {
        super(providers);
        this.closure = closure;
        this.assumptions = assumptions;
        this.options = options;
        this.debug = debug;
    }

    private boolean deleted;
    private PartialEscapeBlockState<?> state;
    private ValueNode current;
    private FixedNode position;
    private GraphEffectList effects;

    @Override
    public OptionValues getOptions() {
        return options;
    }

    @Override
    public DebugContext getDebug() {
        return debug;
    }

    public void reset(PartialEscapeBlockState<?> newState, ValueNode newCurrent, FixedNode newPosition, GraphEffectList newEffects) {
        deleted = false;
        state = newState;
        current = newCurrent;
        position = newPosition;
        effects = newEffects;
    }

    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public ValueNode getAlias(ValueNode value) {
        return closure.getAliasAndResolve(state, value);
    }

    @Override
    public ValueNode getEntry(VirtualObjectNode virtualObject, int index) {
        return state.getObjectState(virtualObject).getEntry(index);
    }

    @Override
    public boolean setVirtualEntry(VirtualObjectNode virtual, int index, ValueNode value, JavaKind theAccessKind, long offset) {
        ObjectState obj = state.getObjectState(virtual);
        assert obj.isVirtual() : "not virtual: " + obj;
        JavaKind entryKind = virtual.entryKind(this.getMetaAccessExtensionProvider(), index);
        JavaKind accessKind = theAccessKind != null ? theAccessKind : entryKind;
        ValueNode newValue = closure.getAliasAndResolve(state, value);
        getDebug().log(DebugContext.DETAILED_LEVEL, "Setting entry %d in virtual object %s %s results in %s", index, virtual.getObjectId(), virtual, state.getObjectState(virtual.getObjectId()));
        ValueNode oldValue = getEntry(virtual, index);
        boolean oldIsIllegal = oldValue.isIllegalConstant();
        boolean canVirtualize = entryKind == accessKind || (entryKind == accessKind.getStackKind() && virtual instanceof VirtualInstanceNode);
        if (canVirtualize && virtual.isVirtualByteArrayAccess(getMetaAccessExtensionProvider(), accessKind)) {
            GraalError.guarantee(entryKind == JavaKind.Byte && accessKind == JavaKind.Byte, "expected byte kinds by canVirtualize condition");
            /*
             * Special case: An explicit byte write into a virtual byte array. We cannot virtualize
             * this if it would overwrite part of a larger value. That is the case if the old value
             * is either larger than a byte (indicating the first byte of the value) or illegal
             * (indicating one of the following bytes of a preceding value).
             */
            if (theAccessKind == JavaKind.Byte &&  // explicitly specified by the caller
                            (oldIsIllegal || ((VirtualArrayNode) virtual).byteArrayEntryByteCount(index, this) > 1)) {
                canVirtualize = false;
            }
        }
        if (!canVirtualize) {
            assert entryKind != JavaKind.Long || newValue != null;
            if (entryKind == JavaKind.Long && oldValue.getStackKind() == newValue.getStackKind() && oldValue.getStackKind().isPrimitive()) {
                /*
                 * Special case: If the entryKind is long, allow arbitrary kinds as long as a value
                 * of the same kind is already there. This can only happen if some other node
                 * initialized the entry with a value of a different kind. One example where this
                 * happens is the Truffle NewFrameNode.
                 */
                getDebug().log(DebugContext.DETAILED_LEVEL, "virtualizing %s with primitive of kind %s in long entry ", current, oldValue.getStackKind());
                canVirtualize = true;
            } else if (entryKind == JavaKind.Int && (accessKind == JavaKind.Long || accessKind == JavaKind.Double) && offset % 8 == 0) {
                /*
                 * Special case: Allow storing a single long or double value into two consecutive
                 * int slots.
                 */
                int nextIndex = virtual.entryIndexForOffset(getMetaAccess(), offset + 4, JavaKind.Int);
                if (nextIndex != -1) {
                    canVirtualize = true;
                    assert nextIndex == index + 1 : "expected to be sequential";
                    getDebug().log(DebugContext.DETAILED_LEVEL, "virtualizing %s for double word stored in two ints", current);
                }
            } else if (canVirtualizeLargeByteArrayUnsafeWrite(virtual, accessKind, offset)) {
                /*
                 * Special case: Allow storing any primitive inside a byte array, as long as there
                 * is enough room left, and all accesses and subsequent writes are on the exact
                 * position of the first write, and of the same kind.
                 *
                 * Any other access results in materialization.
                 */
                int accessLastIndex = virtual.entryIndexForOffset(getMetaAccess(), offset + accessKind.getByteCount() - 1, JavaKind.Byte);
                if (accessLastIndex != -1 && !oldIsIllegal && canStoreOverOldValue((VirtualArrayNode) virtual, oldValue, accessKind, index)) {
                    canVirtualize = true;
                    getDebug().log(DebugContext.DETAILED_LEVEL, "virtualizing %s for %s word stored in byte array", current, accessKind);
                }
            }
        }

        if (canVirtualize) {
            getDebug().log(DebugContext.DETAILED_LEVEL, "virtualizing %s for entryKind %s and access kind %s", current, entryKind, accessKind);
            if (theAccessKind != null && entryKind != theAccessKind &&
                            (theAccessKind != JavaKind.Int && theAccessKind.getStackKind() == JavaKind.Int)) {
                ValueNode entry = getEntry(virtual, index);
                // We can't just set the given value as the new entry but need to simulate a store
                // operation
                newValue = DefaultJavaLoweringProvider.simulatePrimitiveStore(accessKind, entry, newValue);
                ensureAdded(newValue);
            }
            state.setEntry(virtual.getObjectId(), index, newValue);
            if (entryKind == JavaKind.Int) {
                if (accessKind.needsTwoSlots()) {
                    // Storing double word value two int slots
                    assert virtual.entryKind(getMetaAccessExtensionProvider(), index + 1) == JavaKind.Int : Assertions.errorMessage(virtual, index, value, theAccessKind, offset);
                    state.setEntry(virtual.getObjectId(), index + 1, getIllegalConstant());
                } else if (oldValue.getStackKind() == JavaKind.Double || oldValue.getStackKind() == JavaKind.Long) {
                    // Splitting double word constant by storing over it with an int
                    getDebug().log(DebugContext.DETAILED_LEVEL, "virtualizing %s producing second half of double word value %s", current, oldValue);
                    ValueNode secondHalf = UnpackEndianHalfNode.create(oldValue, false, NodeView.DEFAULT);
                    addNode(secondHalf);
                    state.setEntry(virtual.getObjectId(), index + 1, secondHalf);
                }
            } else if (canVirtualizeLargeByteArrayUnsafeWrite(virtual, accessKind, offset)) {
                for (int i = index + 1; i < index + accessKind.getByteCount(); i++) {
                    state.setEntry(virtual.getObjectId(), i, getIllegalConstant());
                }
            }
            if (oldIsIllegal) {
                if (entryKind == JavaKind.Int) {
                    // Storing into second half of double, so replace previous value
                    ValueNode previous = getEntry(virtual, index - 1);
                    getDebug().log(DebugContext.DETAILED_LEVEL, "virtualizing %s producing first half of double word value %s", current, previous);
                    ValueNode firstHalf = UnpackEndianHalfNode.create(previous, true, NodeView.DEFAULT);
                    addNode(firstHalf);
                    state.setEntry(virtual.getObjectId(), index - 1, firstHalf);
                }
            }
            return true;
        }
        /*
         * Should only occur if there are mismatches between the entry and access kind, or we are
         * clobbering a byte in a larger value.
         */
        assert entryKind != accessKind || entryKind == JavaKind.Byte : "setVirtualEntry failed on entry kind " + entryKind + ", access kind " + accessKind;
        return false;
    }

    private boolean canStoreOverOldValue(VirtualArrayNode virtual, ValueNode oldValue, JavaKind accessKind, int index) {
        if (!oldValue.getStackKind().isPrimitive()) {
            return false;
        }
        if (isEntryDefaults(virtual, accessKind.getByteCount(), index)) {
            return true;
        }
        return accessKind.getByteCount() == virtual.byteArrayEntryByteCount(index, this);
    }

    private boolean canVirtualizeLargeByteArrayUnsafeWrite(VirtualObjectNode virtual, JavaKind accessKind, long offset) {
        return canVirtualizeLargeByteArrayUnsafeAccess() && virtual.isVirtualByteArrayAccess(this.getMetaAccessExtensionProvider(), accessKind) &&
                        /*
                         * Require aligned writes. Some architectures do not support recovering
                         * writes to unaligned offsets. Since most use cases for this optimization
                         * will write to reasonable offsets, disabling the optimization for
                         * unreasonable ones is not that big an issue.
                         */
                        ((offset % accessKind.getByteCount()) == 0);
    }

    int getVirtualByteCount(ValueNode[] entries, int startIndex) {
        int pos = startIndex + 1;
        while (pos < entries.length && entries[pos].getStackKind() == JavaKind.Illegal) {
            pos++;
        }
        return pos - startIndex;
    }

    boolean isEntryDefaults(ObjectState object, int byteCount, int index) {
        for (int i = index; i < index + byteCount; i++) {
            if (!object.getEntry(i).isDefaultConstant()) {
                return false;
            }
        }
        return true;
    }

    boolean isEntryDefaults(VirtualObjectNode virtual, int byteCount, int index) {
        return isEntryDefaults(state.getObjectState(virtual), byteCount, index);
    }

    public ValueNode getIllegalConstant() {
        if (illegalConstant == null) {
            /* Try not to spawn a second illegal constant in the graph. */
            illegalConstant = ConstantNode.forConstant(JavaConstant.forIllegal(), getMetaAccess(), closure.cfg.graph);
        }
        return illegalConstant;
    }

    @Override
    public void setEnsureVirtualized(VirtualObjectNode virtualObject, boolean ensureVirtualized) {
        int id = virtualObject.getObjectId();
        state.setEnsureVirtualized(id, ensureVirtualized);
    }

    @Override
    public void replaceWithVirtual(VirtualObjectNode virtual) {
        closure.addVirtualAlias(virtual, current);
        effects.deleteNode(current);
        deleted = true;
    }

    @Override
    public void replaceWithValue(ValueNode replacement) {
        effects.replaceAtUsages(current, closure.getScalarAlias(replacement), position);
        closure.addScalarAlias(current, replacement);
        deleted = true;
    }

    @Override
    public void delete() {
        effects.deleteNode(current);
        deleted = true;
    }

    @Override
    public void replaceFirstInput(Node oldInput, Node replacement) {
        effects.replaceFirstInput(current, oldInput, replacement);
    }

    @Override
    public void addNode(ValueNode node) {
        if (node instanceof FloatingNode) {
            effects.addFloatingNode(node, "VirtualizerTool.addNode");
        } else {
            effects.addFixedNodeBefore((FixedWithNextNode) node, position);
        }
    }

    @Override
    public void ensureAdded(ValueNode node) {
        if (node.isAlive()) {
            // nothing to do
            return;
        }
        effects.ensureAdded(node, position);
        if (node instanceof NodeWithState withState) {
            if (!node.isAlive()) {
                current.graph().addWithoutUnique(node);
            }
            closure.processNodeWithState(withState, state, effects);
        }
    }

    @Override
    public void createVirtualObject(VirtualObjectNode virtualObject, ValueNode[] entryState, List<MonitorIdNode> locks, NodeSourcePosition sourcePosition, boolean ensureVirtualized) {
        VirtualUtil.trace(options, debug, "{{%s}} ", current);
        if (!virtualObject.isAlive()) {
            effects.addFloatingNode(virtualObject, "newVirtualObject");
        }
        for (int i = 0; i < entryState.length; i++) {
            ValueNode entry = entryState[i];
            entryState[i] = entry instanceof VirtualObjectNode ? entry : closure.getAliasAndResolve(state, entry);
        }
        int id = virtualObject.getObjectId();
        if (id == -1) {
            id = closure.virtualObjects.size();
            closure.virtualObjects.add(virtualObject);
            virtualObject.setObjectId(id);
        }
        state.addObject(id, new ObjectState(entryState, locks, ensureVirtualized));
        closure.addVirtualAlias(virtualObject, virtualObject);
        PartialEscapeClosure.COUNTER_ALLOCATION_REMOVED.increment(debug);
        effects.addVirtualizationDelta(1);
        effects.addLog(closure.cfg.graph.getOptimizationLog(), optimizationLog -> optimizationLog.getPartialEscapeLog().allocationRemoved(virtualObject));
        if (sourcePosition != null) {
            assert virtualObject.getNodeSourcePosition() == null || virtualObject.getNodeSourcePosition() == sourcePosition : "unexpected source pos!";
            virtualObject.setNodeSourcePosition(sourcePosition);
        }
    }

    @Override
    public int getMaximumEntryCount() {
        return MaximumEscapeAnalysisArrayLength.getValue(current.getOptions());
    }

    @Override
    public void replaceWith(ValueNode node) {
        if (node instanceof VirtualObjectNode) {
            replaceWithVirtual((VirtualObjectNode) node);
        } else {
            replaceWithValue(node);
        }
    }

    @Override
    public boolean ensureMaterialized(VirtualObjectNode virtualObject) {
        return closure.ensureMaterialized(state, virtualObject.getObjectId(), position, effects, PartialEscapeClosure.COUNTER_MATERIALIZATIONS_UNHANDLED);
    }

    @Override
    public boolean canVirtualizeLock(VirtualObjectNode virtualObject, MonitorIdNode monitorId) {
        if (getPlatformConfigurationProvider().requiresStrictLockOrder()) {
            int id = virtualObject.getObjectId();
            return !state.isNonImmediateRecursiveLock(id, monitorId);
        }
        return true;
    }

    @Override
    public void addLock(VirtualObjectNode virtualObject, MonitorIdNode monitorId) {
        int id = virtualObject.getObjectId();
        state.addLock(id, monitorId);
    }

    @Override
    public MonitorIdNode removeLock(VirtualObjectNode virtualObject) {
        int id = virtualObject.getObjectId();
        return state.removeLock(id);
    }

    @Override
    public boolean canVirtualizeLargeByteArrayUnsafeAccess() {
        if (getPlatformConfigurationProvider() != null) {
            return getPlatformConfigurationProvider().canVirtualizeLargeByteArrayAccess();
        }
        return false;
    }

    @Override
    public boolean canonicalizeReads() {
        return false;
    }

    @Override
    public boolean allUsagesAvailable() {
        return true;
    }

    @Override
    public Assumptions getAssumptions() {
        return assumptions;
    }

    @Override
    public Integer smallestCompareWidth() {
        if (getLowerer() != null) {
            return getLowerer().smallestCompareWidth();
        } else {
            return null;
        }
    }

    @Override
    public VirtualizerTool createSnapshot() {
        VirtualizerToolImpl snapshot = new VirtualizerToolImpl(getProviders(), closure, assumptions, options, debug);
        snapshot.current = this.current;
        snapshot.position = this.position;
        snapshot.effects = new GraphEffectList(this.debug);
        snapshot.state = new PartialEscapeBlockState.Final(this.getOptions(), this.getDebug());
        for (int i = 0; i < this.state.getStateCount(); i++) {
            if (this.state.hasObjectState(i)) {
                snapshot.state.addObject(i, this.state.getObjectState(i).cloneState());
            }
        }
        return snapshot;
    }

    @Override
    public boolean divisionOverflowIsJVMSCompliant() {
        if (getLowerer() != null) {
            return getLowerer().divisionOverflowIsJVMSCompliant();
        }
        // prevent accidental floating of divs if we don't know the target arch
        return false;
    }
}
