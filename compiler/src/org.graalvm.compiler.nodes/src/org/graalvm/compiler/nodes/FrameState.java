/*
 * Copyright (c) 2009, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import static jdk.vm.ci.code.BytecodeFrame.getPlaceholderBciName;
import static jdk.vm.ci.code.BytecodeFrame.isPlaceholderBci;
import static org.graalvm.compiler.nodeinfo.InputType.Association;
import static org.graalvm.compiler.nodeinfo.InputType.State;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.Bytecodes;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.graph.Position;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;
import org.graalvm.compiler.nodes.virtual.EscapeObjectState;
import org.graalvm.compiler.nodes.virtual.MaterializedObjectState;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * The {@code FrameState} class encapsulates the frame state (i.e. local variables, operand stack
 * and locked objects) at a particular point in the abstract interpretation.
 *
 * This can be used as debug or deoptimization information.
 */
@NodeInfo(nameTemplate = "@{p#code/s}:{p#bci}", cycles = CYCLES_0, size = SIZE_1)
public final class FrameState extends VirtualState implements IterableNodeType {
    public static final NodeClass<FrameState> TYPE = NodeClass.create(FrameState.class);

    /**
     * Marker value for the second slot of values that occupy two local variable or expression stack
     * slots. The marker value is used by the bytecode parser, but replaced with {@code null} in the
     * {@link #values} of the {@link FrameState}.
     */
    public static final ValueNode TWO_SLOT_MARKER = new TwoSlotMarker();

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    private static final class TwoSlotMarker extends ValueNode {
        public static final NodeClass<TwoSlotMarker> TYPE = NodeClass.create(TwoSlotMarker.class);

        protected TwoSlotMarker() {
            super(TYPE, StampFactory.forKind(JavaKind.Illegal));
        }
    }

    /**
     * Logical number of local variables represented in this frame state. See {@link #values()} for
     * details on storage allocated for the local variables.
     */
    private final char localsSize;

    /**
     * Number of entries in {@link #values()} allocated for expression stack values.
     */
    private final char stackSize;

    /**
     * Number of entries in {@link #values()} allocated for locked object values.
     */
    private final char locksSize;

    /**
     * @see BytecodeFrame#rethrowException
     */
    private final boolean rethrowException;

    private final boolean duringCall;

    /**
     * When a method {@code y()} has been inlined into a method {@code x()}, the frame states from
     * {@code y()} reference an <em>outer</em> frame state in the surrounding method {@code x()}.
     * <p>
     * Multiple inner frame states can refer to the same outer frame state, which leads to trees of
     * frame states in the IR.
     */
    @OptionalInput(value = InputType.State) FrameState outerFrameState;

    /**
     * @see #values()
     */
    @OptionalInput private NodeInputList<ValueNode> values;

    @Input(Association) NodeInputList<MonitorIdNode> monitorIds;

    @OptionalInput(State) NodeInputList<EscapeObjectState> virtualObjectMappings;

    /**
     * The bytecode index to which this frame state applies.
     */
    public final int bci;

    /**
     * The bytecode to which this frame state applies.
     */
    private final Bytecode code;

    /**
     * Flag to indicate whether this frame represents valid deoptimization state.
     */
    private boolean validForDeoptimization;

    /**
     * Narrows {@code value} to a {@code char} while ensuring the value does not change.
     */
    private static char ensureChar(int value) {
        char cvalue = (char) value;
        if (cvalue != value) {
            throw new IllegalArgumentException(value + " (0x" + Integer.toHexString(value) + ") is not a char");
        }
        return cvalue;
    }

    private FrameState(FrameState outerFrameState,
                    Bytecode code,
                    int bci,
                    int localsSize,
                    int stackSize,
                    int locksSize,
                    boolean rethrowException,
                    boolean duringCall,
                    boolean validForDeoptimization,
                    List<MonitorIdNode> monitorIds,
                    List<EscapeObjectState> virtualObjectMappings) {
        super(TYPE);
        if (code != null) {
            /*
             * Make sure the bci is within range of the bytecodes. If the code size is 0 then allow
             * any value, otherwise the bci must be less than the code size. Any negative value is
             * also allowed to represent special bytecode states.
             */
            int codeSize = code.getCodeSize();
            if (codeSize != 0 && bci >= codeSize) {
                throw new GraalError("bci %d is out of range for %s %d bytes", bci, code.getMethod().format("%H.%n(%p)"), codeSize);
            }
        }
        assert stackSize >= 0;
        this.outerFrameState = outerFrameState;
        assert outerFrameState == null || outerFrameState.bci >= 0;
        this.code = code;
        this.bci = bci;
        this.localsSize = ensureChar(localsSize);
        this.locksSize = ensureChar(locksSize);
        this.stackSize = ensureChar(stackSize);

        if (monitorIds != null && monitorIds.size() > 0) {
            this.monitorIds = new NodeInputList<>(this, monitorIds);
        }

        if (virtualObjectMappings != null && virtualObjectMappings.size() > 0) {
            this.virtualObjectMappings = new NodeInputList<>(this, virtualObjectMappings);
        }

        this.rethrowException = rethrowException;
        this.duringCall = duringCall;
        this.validForDeoptimization = validForDeoptimization;
        assert !this.rethrowException || this.stackSize == 1 : "must have exception on top of the stack";
        assert this.locksSize() == this.monitorIdCount();
    }

    /**
     * @param values see {@link #values()}
     */
    public FrameState(FrameState outerFrameState,
                    Bytecode code,
                    int bci,
                    List<ValueNode> values,
                    int localsSize,
                    int stackSize,
                    int locksSize,
                    boolean rethrowException,
                    boolean duringCall,
                    boolean validForDeoptimization,
                    List<MonitorIdNode> monitorIds,
                    List<EscapeObjectState> virtualObjectMappings,
                    ValueFunction valueFunction) {
        this(outerFrameState, code, bci, localsSize, stackSize, locksSize, rethrowException, duringCall, validForDeoptimization, monitorIds, virtualObjectMappings);
        this.values = new NodeInputList<>(this, values.size());
        for (int i = 0; i < values.size(); ++i) {
            ValueNode value = values.get(i);
            if (valueFunction != null) {
                value = valueFunction.apply(i, value);
            }
            this.values.initialize(i, value);
        }
    }

    private void verifyAfterExceptionState() {
        if (this.bci == BytecodeFrame.AFTER_EXCEPTION_BCI) {
            assert this.outerFrameState == null;
            for (int i = 0; i < this.localsSize; i++) {
                assertTrue(this.localAt(i) == null, "locals should be null in AFTER_EXCEPTION_BCI state");
            }
        }
    }

    public FrameState(int bci) {
        this(null, null, bci, 0, 0, 0, false, false, true, null, null);
        assert bci == BytecodeFrame.BEFORE_BCI ||
                        bci == BytecodeFrame.AFTER_BCI ||
                        bci == BytecodeFrame.AFTER_EXCEPTION_BCI ||
                        bci == BytecodeFrame.UNKNOWN_BCI ||
                        bci == BytecodeFrame.INVALID_FRAMESTATE_BCI;
        this.values = new NodeInputList<>(this);
    }

    /**
     * Creates a placeholder frame state with a single element on the stack representing a return
     * value or thrown exception. This allows the parsing of an intrinsic to communicate the
     * returned or thrown value in a {@link StateSplit#stateAfter() stateAfter} to the inlining call
     * site.
     *
     * @param bci this must be {@link BytecodeFrame#AFTER_BCI}
     */
    public FrameState(int bci, ValueNode returnValueOrExceptionObject) {
        this(null, null, bci, 0, returnValueOrExceptionObject.getStackKind().getSlotCount(), 0, returnValueOrExceptionObject instanceof ExceptionObjectNode, false, true, null, null);
        assert (bci == BytecodeFrame.AFTER_BCI && !rethrowException()) || (bci == BytecodeFrame.AFTER_EXCEPTION_BCI && rethrowException());
        ValueNode[] stack = {returnValueOrExceptionObject};
        this.values = new NodeInputList<>(this, stack);
    }

    public FrameState(FrameState outerFrameState,
                    Bytecode code,
                    int bci,
                    ValueNode[] locals,
                    ValueNode[] stack,
                    int stackSize,
                    JavaKind[] pushedSlotKinds,
                    ValueNode[] pushedValues,
                    ValueNode[] locks,
                    List<MonitorIdNode> monitorIds,
                    boolean rethrowException,
                    boolean duringCall) {
        this(outerFrameState, code, bci, locals.length, stackSize + computeSize(pushedSlotKinds), locks.length, rethrowException, duringCall, true, monitorIds, null);
        createValues(locals, stack, stackSize, pushedSlotKinds, pushedValues, locks);
    }

    public boolean isValidForDeoptimization() {
        return validForDeoptimization;
    }

    public void invalidateForDeoptimization() {
        validForDeoptimization = false;
    }

    private static int computeSize(JavaKind[] slotKinds) {
        int result = 0;
        if (slotKinds != null) {
            for (JavaKind slotKind : slotKinds) {
                result += slotKind.getSlotCount();
            }
        }
        return result;
    }

    private void createValues(ValueNode[] locals, ValueNode[] stack, int initialStackSize, JavaKind[] pushedSlotKinds, ValueNode[] pushedValues, ValueNode[] locks) {
        assert this.values == null;
        int lastNonNullLocal = locals.length - 1;
        while (lastNonNullLocal >= 0) {
            ValueNode local = locals[lastNonNullLocal];
            if (local != null && local != TWO_SLOT_MARKER) {
                break;
            }
            --lastNonNullLocal;
        }

        this.values = new NodeInputList<>(this, locks.length + stackSize + lastNonNullLocal + 1);
        int index = 0;
        for (int i = 0; i < locks.length; ++i) {
            ValueNode value = locks[i];
            assert value != TWO_SLOT_MARKER;
            this.values.initialize(index++, value);
        }

        for (int i = 0; i < initialStackSize; ++i) {
            ValueNode value = stack[i];
            if (value == TWO_SLOT_MARKER) {
                value = null;
            }
            this.values.initialize(index++, value);
        }
        if (pushedValues != null) {
            assert pushedSlotKinds.length == pushedValues.length;
            for (int i = 0; i < pushedValues.length; i++) {
                this.values.initialize(index++, pushedValues[i]);
                if (pushedSlotKinds[i].needsTwoSlots()) {
                    this.values.initialize(index++, null);
                }
            }
        }
        for (int i = 0; i <= lastNonNullLocal; ++i) {
            ValueNode value = locals[i];
            if (value == TWO_SLOT_MARKER) {
                value = null;
            }
            this.values.initialize(index++, value);
        }
    }

    /**
     * Gets the list of values in this frame state. The returned list contains the locked objects,
     * the expression stack and the used locals in that order. The used locals are those up to and
     * including the last non-null local. That is, no storage is allocated for the trailing null
     * locals.
     *
     * <pre>
     *
     *   <-- locksSize --> <-- stackSize --> <----- localsSize ---->
     *  +-----------------+-----------------+---------+-------------+
     *  |      locks      |      stack      | locals  | null locals |
     *  +-----------------+-----------------+---------+-------------+
     *   <-------------- values.size() -------------->
     * </pre>
     */
    public NodeInputList<ValueNode> values() {
        return values;
    }

    /**
     * Wrapper for {@link Position#get(Node)} that is aware of the local variable storage truncation
     * in {@link #values()}. That is, if {@code p} denotes a valid local variable index for which
     * there is no allocated storage, {@code null} is returned.
     */
    public Node getInput(Position p) {
        int valuesIndex = p.getSubIndex();
        if (valuesIndex >= values.size() && valuesIndex < locksSize + stackSize + localsSize && "values".equals(p.getName())) {
            return null;
        }
        return p.get(this);
    }

    public NodeInputList<MonitorIdNode> monitorIds() {
        return monitorIds;
    }

    public FrameState outerFrameState() {
        return outerFrameState;
    }

    public void setOuterFrameState(FrameState x) {
        assert x == null || (!x.isDeleted() && x.bci >= 0) : "cannot set outer frame state of:\n" + toString(this) +
                        "\nto:\n" + toString(x) + "\nisDeleted=" + x.isDeleted();
        updateUsages(this.outerFrameState, x);
        this.outerFrameState = x;
    }

    public static NodeSourcePosition toSourcePosition(FrameState fs) {
        if (fs == null) {
            return null;
        }
        return new NodeSourcePosition(toSourcePosition(fs.outerFrameState()), fs.code.getMethod(), fs.bci);
    }

    /**
     * @see BytecodeFrame#rethrowException
     */
    public boolean rethrowException() {
        return rethrowException;
    }

    public boolean duringCall() {
        return duringCall;
    }

    public Bytecode getCode() {
        return code;
    }

    public ResolvedJavaMethod getMethod() {
        return code == null ? null : code.getMethod();
    }

    /**
     * Determines if this frame state can be converted to a {@link BytecodeFrame}.
     *
     * Since a {@link BytecodeFrame} encodes {@link #getMethod()} and {@link #bci}, it does not
     * preserve {@link #getCode()}. {@link #bci} is only guaranteed to be valid in terms of
     * {@code getCode().getCode()} which may be different from {@code getMethod().getCode()} if the
     * latter has been subject to instrumentation.
     */
    public boolean canProduceBytecodeFrame() {
        return code != null && Arrays.equals(code.getCode(), code.getMethod().getCode());
    }

    public void addVirtualObjectMapping(EscapeObjectState virtualObject) {
        if (virtualObjectMappings == null) {
            virtualObjectMappings = new NodeInputList<>(this);
        }
        virtualObjectMappings.add(virtualObject);
    }

    public int virtualObjectMappingCount() {
        if (virtualObjectMappings == null) {
            return 0;
        }
        return virtualObjectMappings.size();
    }

    public EscapeObjectState virtualObjectMappingAt(int i) {
        return virtualObjectMappings.get(i);
    }

    public NodeInputList<EscapeObjectState> virtualObjectMappings() {
        return virtualObjectMappings;
    }

    /**
     * Gets a copy of this frame state.
     */
    public FrameState duplicate() {
        return graph().add(new FrameState(outerFrameState(), code, bci, values, localsSize, stackSize, locksSize, rethrowException, duringCall, validForDeoptimization, monitorIds,
                        virtualObjectMappings, null));
    }

    /**
     * Function for computing a value in this frame state.
     */
    public interface ValueFunction {
        /**
         * Computes the value that should be assigned to position {@code index} in
         * {@link FrameState#values()} given the current value {@code currentValue} at that
         * position.
         */
        ValueNode apply(int index, ValueNode currentValue);
    }

    /**
     * Gets a copy of this frame state with each value in the copy computed by applying
     * {@code valueFunc} to the {@link #values()} in this frame state.
     */
    public FrameState duplicate(ValueFunction valueFunc) {
        return new FrameState(outerFrameState(), code, bci, values, localsSize, stackSize, locksSize, rethrowException, duringCall, validForDeoptimization, monitorIds, virtualObjectMappings,
                        valueFunc);
    }

    /**
     * Duplicates a FrameState, along with a deep copy of all connected VirtualState (outer
     * FrameStates, VirtualObjectStates, ...).
     */
    @Override
    public FrameState duplicateWithVirtualState() {
        FrameState newOuterFrameState = outerFrameState();
        if (newOuterFrameState != null) {
            newOuterFrameState = newOuterFrameState.duplicateWithVirtualState();
        }
        ArrayList<EscapeObjectState> newVirtualMappings = null;
        if (virtualObjectMappings != null) {
            newVirtualMappings = new ArrayList<>(virtualObjectMappings.size());
            for (EscapeObjectState state : virtualObjectMappings) {
                newVirtualMappings.add(state.duplicateWithVirtualState());
            }
        }
        return graph().add(new FrameState(newOuterFrameState, code, bci, values, localsSize, stackSize, locksSize, rethrowException, duringCall, validForDeoptimization, monitorIds, newVirtualMappings,
                        null));
    }

    /**
     * Creates a copy of this frame state with one stack element of type {@code popKind} popped from
     * the stack.
     */
    public FrameState duplicateModifiedDuringCall(int newBci, JavaKind popKind) {
        return duplicateModified(graph(), newBci, rethrowException, true, popKind, null, null, null);
    }

    public FrameState duplicateModifiedBeforeCall(int newBci,
                    JavaKind popKind,
                    JavaKind[] pushedSlotKinds,
                    ValueNode[] pushedValues,
                    List<EscapeObjectState> pushedVirtualObjectMappings) {
        return duplicateModified(graph(), newBci, rethrowException, false, popKind, pushedSlotKinds, pushedValues, pushedVirtualObjectMappings);
    }

    /**
     * Creates a copy of this frame state with the top of stack replaced with {@code pushedValue}
     * which must be of type {@code popKind}.
     */
    public FrameState duplicateModified(JavaKind popKind,
                    JavaKind pushedSlotKind,
                    ValueNode pushedValue,
                    List<EscapeObjectState> pushedVirtualObjectMappings) {
        assert pushedValue != null && pushedValue.getStackKind() == popKind;
        return duplicateModified(graph(), bci, rethrowException, duringCall, popKind, new JavaKind[]{pushedSlotKind}, new ValueNode[]{pushedValue}, pushedVirtualObjectMappings);
    }

    public FrameState duplicateModified(StructuredGraph graph,
                    int newBci,
                    boolean newRethrowException,
                    boolean newDuringCall,
                    JavaKind popKind,
                    JavaKind[] pushedSlotKinds,
                    ValueNode[] pushedValues,
                    List<EscapeObjectState> pushedVirtualObjectMappings) {
        return duplicateModified(graph, newBci, newRethrowException, newDuringCall, popKind, pushedSlotKinds, pushedValues, pushedVirtualObjectMappings, true);
    }

    /**
     * Creates a copy of this frame state with one stack element of type {@code popKind} popped from
     * the stack and the values in {@code pushedValues} pushed on the stack. The
     * {@code pushedValues} will be formatted correctly in slot encoding: a long or double will be
     * followed by a null slot. The bci will be changed to {@code newBci}.
     */
    public FrameState duplicateModified(StructuredGraph graph,
                    int newBci,
                    boolean newRethrowException,
                    boolean newDuringCall,
                    JavaKind popKind,
                    JavaKind[] pushedSlotKinds,
                    ValueNode[] pushedValues,
                    List<EscapeObjectState> pushedVirtualObjectMappings,
                    boolean checkStackDepth) {

        // Compute size of stack to copy (accounting for popping)
        // and final stack size after pushing
        int copyStackSize;
        int newStackSize = 0;
        if (pushedValues != null) {
            assert pushedSlotKinds.length == pushedValues.length;
            for (int i = 0; i < pushedValues.length; i++) {
                newStackSize += pushedSlotKinds[i].getSlotCount();
            }
        }
        if (newRethrowException && !rethrowException && popKind == JavaKind.Void) {
            assert popKind == JavaKind.Void;
            copyStackSize = 0;
        } else {
            if (popKind != JavaKind.Void) {
                if (stackAt(stackSize() - 1) == null) {
                    copyStackSize = stackSize - 2;
                } else {
                    copyStackSize = stackSize - 1;
                }
                ValueNode lastSlot = stackAt(copyStackSize);
                assert lastSlot.getStackKind() == popKind.getStackKind();
            } else {
                copyStackSize = stackSize;
            }
            newStackSize += copyStackSize;
        }

        int copyLocalsSize = values.size() - locksSize - stackSize;
        int newValuesSize = locksSize + newStackSize + copyLocalsSize;
        ArrayList<ValueNode> newValues = new ArrayList<>(newValuesSize);

        // Copy the locks
        if (locksSize != 0) {
            newValues.addAll(values.subList(0, locksSize));
        }

        // Copy the stack
        if (copyStackSize > 0) {
            newValues.addAll(values.subList(locksSize, locksSize + copyStackSize));
        }

        // Push new values to the stack
        List<EscapeObjectState> copiedVirtualObjectMappings = null;
        if (pushedValues != null) {
            for (int i = 0; i < pushedValues.length; i++) {
                ValueNode pushedValue = pushedValues[i];
                if (pushedValue instanceof VirtualObjectNode) {
                    copiedVirtualObjectMappings = ensureHasVirtualObjectMapping((VirtualObjectNode) pushedValue, pushedVirtualObjectMappings, copiedVirtualObjectMappings);
                }
                newValues.add(pushedValue);
                if (pushedSlotKinds[i].needsTwoSlots()) {
                    newValues.add(null);
                }
            }
        }
        if (copyLocalsSize > 0) {
            // Copy locals
            newValues.addAll(values.subList(locksSize + stackSize, values.size()));
        }

        // Check invariants
        assert newValues.size() == newValuesSize : newValues.size() + " != " + newValuesSize;
        assert !checkStackDepth || checkStackDepth(bci, stackSize, duringCall, rethrowException, newBci, newStackSize, newDuringCall, newRethrowException);

        return graph.add(new FrameState(outerFrameState(),
                        code,
                        newBci,
                        newValues,
                        localsSize,
                        newStackSize,
                        locksSize,
                        newRethrowException,
                        newDuringCall,
                        validForDeoptimization,
                        monitorIds,
                        copiedVirtualObjectMappings != null ? copiedVirtualObjectMappings : virtualObjectMappings,
                        null));
    }

    /**
     * A {@link VirtualObjectNode} in a frame state requires a corresponding
     * {@link EscapeObjectState} entry in {@link FrameState#virtualObjectMappings}. So when a
     * {@link VirtualObjectNode} is pushed as part of a frame state modification, the
     * {@link EscapeObjectState} must either be already there, or it must be passed in explicitly
     * from another frame state where the pushed value is coming from.
     */
    private List<EscapeObjectState> ensureHasVirtualObjectMapping(VirtualObjectNode pushedValue, List<EscapeObjectState> pushedVirtualObjectMappings,
                    List<EscapeObjectState> copiedVirtualObjectMappings) {
        if (virtualObjectMappings != null) {
            for (EscapeObjectState existingEscapeObjectState : virtualObjectMappings) {
                if (existingEscapeObjectState.object() == pushedValue) {
                    /* Found a matching EscapeObjectState, nothing needs to be added. */
                    return copiedVirtualObjectMappings;
                }
            }
        }

        if (pushedVirtualObjectMappings == null) {
            throw GraalError.shouldNotReachHere("Pushing a virtual object, but no virtual object mapping provided: " + pushedValue);
        }
        for (EscapeObjectState pushedEscapeObjectState : pushedVirtualObjectMappings) {
            if (pushedEscapeObjectState.object() == pushedValue) {
                /*
                 * A VirtualObjectState could have transitive dependencies on other object states
                 * that are would also need to be added. For now, we do not have a case where a
                 * FrameState with a VirtualObjectState is duplicated, therefore this case is not
                 * implemented yet.
                 */
                GraalError.guarantee(pushedEscapeObjectState instanceof MaterializedObjectState, "A VirtualObjectState could have transitive dependencies");
                /*
                 * Found a new EscapeObjectState that needs to be added to the
                 * virtualObjectMappings.
                 */
                List<EscapeObjectState> result = copiedVirtualObjectMappings;
                if (result == null) {
                    result = new ArrayList<>();
                    if (virtualObjectMappings != null) {
                        result.addAll(virtualObjectMappings);
                    }
                }
                result.add(pushedEscapeObjectState);
                return result;
            }
        }
        throw GraalError.shouldNotReachHere("Did not find a virtual object mapping: " + pushedValue);
    }

    /**
     * Perform a few sanity checks on the transformation of the stack state. The current expectation
     * is that a stateAfter is being transformed into a stateDuring, so the stack depth may change.
     */
    private boolean checkStackDepth(int oldBci, int oldStackSize, boolean oldDuringCall, boolean oldRethrowException, int newBci, int newStackSize, boolean newDuringCall,
                    boolean newRethrowException) {
        if (BytecodeFrame.isPlaceholderBci(oldBci)) {
            return true;
        }
        /*
         * It would be nice to have a complete check of the shape of the FrameState based on a
         * dataflow of the bytecodes but for now just check for obvious expression stack depth
         * mistakes.
         */
        byte[] codes = code.getCode();
        if (codes == null) {
            /* Graph was constructed manually. */
            return true;
        }
        byte newCode = codes[newBci];
        if (oldBci == newBci) {
            assert oldStackSize == newStackSize || oldDuringCall != newDuringCall || oldRethrowException != newRethrowException : "bci is unchanged, stack depth shouldn't change";
        } else {
            byte oldCode = codes[oldBci];
            assert Bytecodes.lengthOf(newCode) + newBci == oldBci || Bytecodes.lengthOf(oldCode) + oldBci == newBci : "expecting roll back or forward";
        }
        return true;
    }

    /**
     * Gets the size of the local variables.
     */
    public int localsSize() {
        return localsSize;
    }

    /**
     * Gets the size (height) of the stack.
     */
    public int stackSize() {
        return stackSize;
    }

    /**
     * Gets the number of locked monitors in this frame state.
     */
    public int locksSize() {
        return locksSize;
    }

    /**
     * Gets the number of locked monitors in this frame state and all {@linkplain #outerFrameState()
     * outer} frame states.
     */
    public int nestedLockDepth() {
        int depth = locksSize();
        for (FrameState outer = outerFrameState(); outer != null; outer = outer.outerFrameState()) {
            depth += outer.locksSize();
        }
        return depth;
    }

    /**
     * Gets the value in the local variables at the specified index.
     *
     * @param i the index into the locals
     * @return the instruction that produced the value for the specified local
     */
    public ValueNode localAt(int i) {
        assert i >= 0 && i < localsSize : "local variable index out of range: " + i;
        int valueIndex = localToValuesIndex(i);
        if (valueIndex < values.size()) {
            return values.get(valueIndex);
        }
        return null;
    }

    /**
     * Converts local index {@code i} to the index of the local in {@link #values}.
     */
    private int localToValuesIndex(int i) {
        return i + locksSize + stackSize;
    }

    /**
     * Get the value on the stack at the specified stack index.
     *
     * @param i the index into the stack, with {@code 0} being the bottom of the stack
     * @return the instruction at the specified position in the stack
     */
    public ValueNode stackAt(int i) {
        assert i >= 0 && i < stackSize;
        return values.get(locksSize + i);
    }

    /**
     * Get the monitor owner at the specified index.
     *
     * @param i the index into the list of locked monitors.
     * @return the lock owner at the given index.
     */
    public ValueNode lockAt(int i) {
        assert i >= 0 && i < locksSize;
        return values.get(i);
    }

    /**
     * Get the MonitorIdNode that corresponds to the locked object at the specified index.
     */
    public MonitorIdNode monitorIdAt(int i) {
        assert monitorIds != null && i >= 0 && i < locksSize();
        return monitorIds.get(i);
    }

    public int monitorIdCount() {
        if (monitorIds == null) {
            return 0;
        } else {
            return monitorIds.size();
        }
    }

    public NodeIterable<FrameState> innerFrameStates() {
        return usages().filter(FrameState.class);
    }

    private static String toString(FrameState frameState) {
        StringBuilder sb = new StringBuilder();
        String nl = CodeUtil.NEW_LINE;
        FrameState fs = frameState;
        while (fs != null) {
            Bytecode.appendLocation(sb, fs.getCode(), fs.bci);
            if (BytecodeFrame.isPlaceholderBci(fs.bci)) {
                sb.append("//").append(getPlaceholderBciName(fs.bci));
            }
            sb.append(nl);
            sb.append("locals: [");
            for (int i = 0; i < fs.localsSize(); i++) {
                sb.append(i == 0 ? "" : ", ").append(fs.localAt(i) == null ? "_" : fs.localAt(i).toString(Verbosity.Id));
            }
            sb.append("]").append(nl).append("stack: [");
            for (int i = 0; i < fs.stackSize(); i++) {
                sb.append(i == 0 ? "" : ", ").append(fs.stackAt(i) == null ? "_" : fs.stackAt(i).toString(Verbosity.Id));
            }
            sb.append("]").append(nl).append("locks: [");
            for (int i = 0; i < fs.locksSize(); i++) {
                sb.append(i == 0 ? "" : ", ").append(fs.lockAt(i) == null ? "_" : fs.lockAt(i).toString(Verbosity.Id));
            }
            sb.append(']').append(nl);
            fs = fs.outerFrameState();
        }
        return sb.toString();
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Debugger) {
            return toString(this);
        } else if (verbosity == Verbosity.Name) {
            String res = super.toString(Verbosity.Name) + "@" + bci;
            if (BytecodeFrame.isPlaceholderBci(bci)) {
                res += "[" + getPlaceholderBciName(bci) + "]";
            }
            return res;
        } else {
            return super.toString(verbosity);
        }
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Map<Object, Object> properties = super.getDebugProperties(map);
        if (code != null) {
            // properties.put("method", MetaUtil.format("%H.%n(%p):%r", method));
            StackTraceElement ste = code.asStackTraceElement(bci);
            if (ste.getFileName() != null && ste.getLineNumber() >= 0) {
                properties.put("sourceFile", ste.getFileName());
                properties.put("sourceLine", ste.getLineNumber());
            }
        }
        if (isPlaceholderBci(bci)) {
            properties.put("bci", getPlaceholderBciName(bci));
        }
        return properties;
    }

    @Override
    public boolean verify() {
        if (virtualObjectMappingCount() > 0) {
            for (EscapeObjectState state : virtualObjectMappings()) {
                assertTrue(state != null, "must be non-null");
            }
        }

        int allocatedLocals = values.size() - locksSize - stackSize;
        if (allocatedLocals > 0) {
            ValueNode lastAllocatedLocal = values.get(values.size() - 1);
            if (lastAllocatedLocal == null) {
                throw new AssertionError("last entry in values for a local must not be null");
            }
        }

        /*
         * The outermost FrameState should have a method that matches StructuredGraph.method except
         * when it's a substitution or it's null.
         */
        assertTrue(outerFrameState != null || graph() == null || graph().method() == null || code == null || Objects.equals(graph().method(), code.getMethod()) ||
                        graph().isSubstitution(), "wrong outerFrameState %s != %s", code == null ? "null" : code.getMethod(), graph().method());
        if (monitorIds() != null && monitorIds().size() > 0) {
            int depth = outerLockDepth();
            for (MonitorIdNode monitor : monitorIds()) {
                assertTrue(monitor.getLockDepth() == depth++, "wrong depth");
            }
        }
        assertTrue(locksSize() == monitorIdCount(), "mismatch in number of locks");
        for (ValueNode value : values) {
            assertTrue(value == null || !value.isDeleted(), "frame state must not contain deleted nodes: %s", value);
            assertTrue(value == null || value instanceof VirtualObjectNode || (value.getStackKind() != JavaKind.Void), "unexpected value: %s", value);
        }
        verifyAfterExceptionState();
        return super.verify();
    }

    private int outerLockDepth() {
        int depth = 0;
        FrameState outer = outerFrameState;
        while (outer != null) {
            depth += outer.monitorIdCount();
            outer = outer.outerFrameState;
        }
        return depth;
    }

    @Override
    public void applyToVirtual(VirtualClosure closure) {
        closure.apply(this);
        if (virtualObjectMappings != null) {
            for (EscapeObjectState state : virtualObjectMappings) {
                state.applyToVirtual(closure);
            }
        }
        if (outerFrameState() != null) {
            outerFrameState().applyToVirtual(closure);
        }
    }

    @Override
    public void applyToNonVirtual(NodePositionClosure<? super Node> closure) {
        Iterator<Position> iter = inputPositions().iterator();
        while (iter.hasNext()) {
            Position pos = iter.next();
            if (pos.get(this) != null) {
                if (pos.getInputType() == InputType.Value || pos.getInputType() == Association) {
                    closure.apply(this, pos);
                }
            }
        }
        if (virtualObjectMappings != null) {
            for (EscapeObjectState state : virtualObjectMappings) {
                state.applyToNonVirtual(closure);
            }
        }
        if (outerFrameState() != null) {
            outerFrameState().applyToNonVirtual(closure);
        }
    }

    @Override
    public boolean isPartOfThisState(VirtualState state) {
        if (state == this) {
            return true;
        }
        if (outerFrameState() != null && outerFrameState().isPartOfThisState(state)) {
            return true;
        }
        if (virtualObjectMappings != null) {
            for (EscapeObjectState objectState : virtualObjectMappings) {
                if (objectState.isPartOfThisState(state)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isExceptionHandlingBCI() {
        return bci == BytecodeFrame.AFTER_EXCEPTION_BCI || bci == BytecodeFrame.UNWIND_BCI;
    }

}
