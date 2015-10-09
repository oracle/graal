/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.alloc.trace;

import static com.oracle.graal.compiler.common.GraalOptions.DetailedAsserts;
import static com.oracle.graal.lir.LIRValueUtil.isVariable;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;
import static jdk.vm.ci.code.ValueUtil.isStackSlotValue;
import static jdk.vm.ci.code.ValueUtil.isVirtualStackSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.StackSlotValue;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.Value;

import com.oracle.graal.compiler.common.util.Util;
import com.oracle.graal.debug.TTY;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.Variable;

/**
 * Represents an interval in the {@linkplain TraceLinearScan linear scan register allocator}.
 */
final class TraceInterval extends IntervalHint {

    static final class AnyList {

        /**
         * List of intervals whose binding is currently {@link RegisterBinding#Any}.
         */
        public TraceInterval any;

        public AnyList(TraceInterval any) {
            this.any = any;
        }

        /**
         * Gets the any list.
         */
        public TraceInterval getAny() {
            return any;
        }

        /**
         * Sets the any list.
         */
        public void setAny(TraceInterval list) {
            any = list;
        }

        /**
         * Adds an interval to a list sorted by {@linkplain TraceInterval#from() current from}
         * positions.
         *
         * @param interval the interval to add
         */
        public void addToListSortedByFromPositions(TraceInterval interval) {
            TraceInterval list = getAny();
            TraceInterval prev = null;
            TraceInterval cur = list;
            while (cur.from() < interval.from()) {
                prev = cur;
                cur = cur.next;
            }
            TraceInterval result = list;
            if (prev == null) {
                // add to head of list
                result = interval;
            } else {
                // add before 'cur'
                prev.next = interval;
            }
            interval.next = cur;
            setAny(result);
        }

        /**
         * Adds an interval to a list sorted by {@linkplain TraceInterval#from() start} positions
         * and {@linkplain TraceInterval#firstUsage(RegisterPriority) first usage} positions.
         *
         * @param interval the interval to add
         */
        public void addToListSortedByStartAndUsePositions(TraceInterval interval) {
            TraceInterval list = getAny();
            TraceInterval prev = null;
            TraceInterval cur = list;
            while (cur.from() < interval.from() || (cur.from() == interval.from() && cur.firstUsage(RegisterPriority.None) < interval.firstUsage(RegisterPriority.None))) {
                prev = cur;
                cur = cur.next;
            }
            if (prev == null) {
                list = interval;
            } else {
                prev.next = interval;
            }
            interval.next = cur;
            setAny(list);
        }

        /**
         * Removes an interval from a list.
         *
         * @param i the interval to remove
         */
        public void removeAny(TraceInterval i) {
            TraceInterval list = getAny();
            TraceInterval prev = null;
            TraceInterval cur = list;
            while (cur != i) {
                assert cur != null && cur != TraceInterval.EndMarker : "interval has not been found in list: " + i;
                prev = cur;
                cur = cur.next;
            }
            if (prev == null) {
                setAny(cur.next);
            } else {
                prev.next = cur.next;
            }
        }
    }

    /**
     * Constants denoting the register usage priority for an interval. The constants are declared in
     * increasing order of priority are are used to optimize spilling when multiple overlapping
     * intervals compete for limited registers.
     */
    public enum RegisterPriority {
        /**
         * No special reason for an interval to be allocated a register.
         */
        None,

        /**
         * Priority level for intervals live at the end of a loop.
         */
        LiveAtLoopEnd,

        /**
         * Priority level for intervals that should be allocated to a register.
         */
        ShouldHaveRegister,

        /**
         * Priority level for intervals that must be allocated to a register.
         */
        MustHaveRegister;

        public static final RegisterPriority[] VALUES = values();

        /**
         * Determines if this priority is higher than or equal to a given priority.
         */
        public boolean greaterEqual(RegisterPriority other) {
            return ordinal() >= other.ordinal();
        }

        /**
         * Determines if this priority is lower than a given priority.
         */
        public boolean lessThan(RegisterPriority other) {
            return ordinal() < other.ordinal();
        }

        public CharSequence shortName() {
            return name().subSequence(0, 1);
        }
    }

    /**
     * Constants denoting whether an interval is bound to a specific register. This models platform
     * dependencies on register usage for certain instructions.
     */
    enum RegisterBinding {
        /**
         * Interval is bound to a specific register as required by the platform.
         */
        Fixed,

        /**
         * Interval has no specific register requirements.
         */
        Any,

        /**
         * Interval is bound to a stack slot.
         */
        Stack;

        public static final RegisterBinding[] VALUES = values();
    }

    /**
     * Constants denoting the linear-scan states an interval may be in with respect to the
     * {@linkplain TraceInterval#from() start} {@code position} of the interval being processed.
     */
    enum State {
        /**
         * An interval that starts after {@code position}.
         */
        Unhandled,

        /**
         * An interval that {@linkplain TraceInterval#covers covers} {@code position} and has an
         * assigned register.
         */
        Active,

        /**
         * An interval that starts before and ends after {@code position} but does not
         * {@linkplain TraceInterval#covers cover} it due to a lifetime hole.
         */
        Inactive,

        /**
         * An interval that ends before {@code position} or is spilled to memory.
         */
        Handled;
    }

    /**
     * Constants used in optimization of spilling of an interval.
     */
    public enum SpillState {
        /**
         * Starting state of calculation: no definition found yet.
         */
        NoDefinitionFound,

        /**
         * One definition has already been found. Two consecutive definitions are treated as one
         * (e.g. a consecutive move and add because of two-operand LIR form). The position of this
         * definition is given by {@link TraceInterval#spillDefinitionPos()}.
         */
        NoSpillStore,

        /**
         * One spill move has already been inserted.
         */
        OneSpillStore,

        /**
         * The interval is spilled multiple times or is spilled in a loop. Place the store somewhere
         * on the dominator path between the definition and the usages.
         */
        SpillInDominator,

        /**
         * The interval should be stored immediately after its definition to prevent multiple
         * redundant stores.
         */
        StoreAtDefinition,

        /**
         * The interval starts in memory (e.g. method parameter), so a store is never necessary.
         */
        StartInMemory,

        /**
         * The interval has more than one definition (e.g. resulting from phi moves), so stores to
         * memory are not optimized.
         */
        NoOptimization
    }

    /**
     * The {@linkplain RegisterValue register} or {@linkplain Variable variable} for this interval
     * prior to register allocation.
     */
    public final AllocatableValue operand;

    /**
     * The operand number for this interval's {@linkplain #operand operand}.
     */
    public final int operandNumber;

    /**
     * The {@linkplain RegisterValue register} or {@linkplain StackSlot spill slot} assigned to this
     * interval. In case of a spilled interval which is re-materialized this is
     * {@link Value#ILLEGAL}.
     */
    private AllocatableValue location;

    /**
     * The stack slot to which all splits of this interval are spilled if necessary.
     */
    private StackSlotValue spillSlot;

    /**
     * The kind of this interval.
     */
    private LIRKind kind;

    /**
     * The start of the range, inclusive.
     */
    public int intFrom;

    /**
     * The end of the range, exclusive.
     */
    public int intTo;

    /**
     * List of (use-positions, register-priorities) pairs, sorted by use-positions.
     */
    private UsePosList usePosList;

    /**
     * Link to next interval in a sorted list of intervals that ends with {@link #EndMarker}.
     */
    TraceInterval next;

    /**
     * The linear-scan state of this interval.
     */
    State state;

    /**
     * The interval from which this one is derived. If this is a {@linkplain #isSplitParent() split
     * parent}, it points to itself.
     */
    private TraceInterval splitParent;

    /**
     * List of all intervals that are split off from this interval. This is only used if this is a
     * {@linkplain #isSplitParent() split parent}.
     */
    private List<TraceInterval> splitChildren = Collections.emptyList();

    /**
     * Current split child that has been active or inactive last (always stored in split parents).
     */
    private TraceInterval currentSplitChild;

    /**
     * Specifies if move is inserted between currentSplitChild and this interval when interval gets
     * active the first time.
     */
    private boolean insertMoveWhenActivated;

    /**
     * For spill move optimization.
     */
    private SpillState spillState;

    /**
     * Position where this interval is defined (if defined only once).
     */
    private int spillDefinitionPos;

    /**
     * This interval should be assigned the same location as the hint interval.
     */
    private IntervalHint locationHint;

    /**
     * The value with which a spilled child interval can be re-materialized. Currently this must be
     * a Constant.
     */
    private JavaConstant materializedValue;

    /**
     * The number of times {@link #addMaterializationValue(JavaConstant)} is called.
     */
    private int numMaterializationValuesAdded;

    void assignLocation(AllocatableValue newLocation) {
        if (isRegister(newLocation)) {
            assert this.location == null : "cannot re-assign location for " + this;
            if (newLocation.getLIRKind().equals(LIRKind.Illegal) && !kind.equals(LIRKind.Illegal)) {
                this.location = asRegister(newLocation).asValue(kind);
                return;
            }
        } else if (isIllegal(newLocation)) {
            assert canMaterialize();
        } else {
            assert this.location == null || isRegister(this.location) || (isVirtualStackSlot(this.location) && isStackSlot(newLocation)) : "cannot re-assign location for " + this;
            assert isStackSlotValue(newLocation);
            assert !newLocation.getLIRKind().equals(LIRKind.Illegal);
            assert newLocation.getLIRKind().equals(this.kind);
        }
        this.location = newLocation;
    }

    /**
     * Gets the {@linkplain RegisterValue register} or {@linkplain StackSlot spill slot} assigned to
     * this interval.
     */
    @Override
    public AllocatableValue location() {
        return location;
    }

    public LIRKind kind() {
        assert !isRegister(operand) : "cannot access type for fixed interval";
        return kind;
    }

    public void setKind(LIRKind kind) {
        assert isRegister(operand) || this.kind().equals(LIRKind.Illegal) || this.kind().equals(kind) : "overwriting existing type";
        this.kind = kind;
    }

    public boolean isEmpty() {
        return intFrom == Integer.MAX_VALUE && intTo == Integer.MAX_VALUE;
    }

    public void setFrom(int pos) {
        intFrom = pos;
    }

    @Override
    public int from() {
        return intFrom;
    }

    int to() {
        return intTo;
    }

    int numUsePositions() {
        return usePosList.size();
    }

    public void setLocationHint(IntervalHint interval) {
        locationHint = interval;
    }

    public boolean isSplitParent() {
        return splitParent == this;
    }

    boolean isSplitChild() {
        return splitParent != this;
    }

    /**
     * Gets the split parent for this interval.
     */
    public TraceInterval splitParent() {
        assert splitParent.isSplitParent() : "not a split parent: " + this;
        return splitParent;
    }

    /**
     * Gets the canonical spill slot for this interval.
     */
    public StackSlotValue spillSlot() {
        return splitParent().spillSlot;
    }

    public void setSpillSlot(StackSlotValue slot) {
        assert splitParent().spillSlot == null || (isVirtualStackSlot(splitParent().spillSlot) && isStackSlot(slot)) : "connot overwrite existing spill slot";
        splitParent().spillSlot = slot;
    }

    TraceInterval currentSplitChild() {
        return splitParent().currentSplitChild;
    }

    void makeCurrentSplitChild() {
        splitParent().currentSplitChild = this;
    }

    boolean insertMoveWhenActivated() {
        return insertMoveWhenActivated;
    }

    void setInsertMoveWhenActivated(boolean b) {
        insertMoveWhenActivated = b;
    }

    // for spill optimization
    public SpillState spillState() {
        return splitParent().spillState;
    }

    public int spillDefinitionPos() {
        return splitParent().spillDefinitionPos;
    }

    public void setSpillState(SpillState state) {
        assert state.ordinal() >= spillState().ordinal() : "state cannot decrease";
        splitParent().spillState = state;
    }

    public void setSpillDefinitionPos(int pos) {
        assert spillState() == SpillState.SpillInDominator || spillState() == SpillState.NoDefinitionFound || spillDefinitionPos() == -1 : "cannot set the position twice";
        splitParent().spillDefinitionPos = pos;
    }

    // returns true if this interval has a shadow copy on the stack that is always correct
    public boolean alwaysInMemory() {
        return (splitParent().spillState == SpillState.SpillInDominator || splitParent().spillState == SpillState.StoreAtDefinition || splitParent().spillState == SpillState.StartInMemory) &&
                        !canMaterialize();
    }

    void removeFirstUsePos() {
        usePosList.removeLowestUsePos();
    }

    // test intersection
    boolean intersects(TraceInterval i) {
        return intersectsAt(i) != -1;
    }

    int intersectsAt(TraceInterval i) {
        TraceInterval i1;
        TraceInterval i2;
        if (i.from() < this.from()) {
            i1 = i;
            i2 = this;
        } else {
            i1 = this;
            i2 = i;
        }
        assert i1.from() <= i2.from();

        if (i1.to() <= i2.from()) {
            return -1;
        }
        return i2.from();
    }

    /**
     * Sentinel interval to denote the end of an interval list.
     */
    static final TraceInterval EndMarker = new TraceInterval(Value.ILLEGAL, -1);

    TraceInterval(AllocatableValue operand, int operandNumber) {
        assert operand != null;
        this.operand = operand;
        this.operandNumber = operandNumber;
        if (isRegister(operand)) {
            location = operand;
        } else {
            assert isIllegal(operand) || isVariable(operand);
        }
        this.kind = LIRKind.Illegal;
        this.intFrom = Integer.MAX_VALUE;
        this.intTo = Integer.MAX_VALUE;
        this.usePosList = new UsePosList(4);
        this.next = EndMarker;
        this.spillState = SpillState.NoDefinitionFound;
        this.spillDefinitionPos = -1;
        splitParent = this;
        currentSplitChild = this;
    }

    /**
     * Sets the value which is used for re-materialization.
     */
    public void addMaterializationValue(JavaConstant value) {
        if (numMaterializationValuesAdded == 0) {
            materializedValue = value;
        } else {
            // Interval is defined on multiple places -> no materialization is possible.
            materializedValue = null;
        }
        numMaterializationValuesAdded++;
    }

    /**
     * Returns true if this interval can be re-materialized when spilled. This means that no
     * spill-moves are needed. Instead of restore-moves the {@link #materializedValue} is restored.
     */
    public boolean canMaterialize() {
        return getMaterializedValue() != null;
    }

    /**
     * Returns a value which can be moved to a register instead of a restore-move from stack.
     */
    public JavaConstant getMaterializedValue() {
        return splitParent().materializedValue;
    }

    // consistency check of split-children
    boolean checkSplitChildren() {
        if (!splitChildren.isEmpty()) {
            assert isSplitParent() : "only split parents can have children";

            for (int i = 0; i < splitChildren.size(); i++) {
                TraceInterval i1 = splitChildren.get(i);

                assert i1.splitParent() == this : "not a split child of this interval";
                assert i1.kind().equals(kind()) : "must be equal for all split children";
                assert (i1.spillSlot() == null && spillSlot == null) || i1.spillSlot().equals(spillSlot()) : "must be equal for all split children";

                for (int j = i + 1; j < splitChildren.size(); j++) {
                    TraceInterval i2 = splitChildren.get(j);

                    assert !i1.operand.equals(i2.operand) : "same register number";

                    if (i1.from() < i2.from()) {
                        assert i1.to() <= i2.from() && i1.to() < i2.to() : "intervals overlapping";
                    } else {
                        assert i2.from() < i1.from() : "intervals start at same opId";
                        assert i2.to() <= i1.from() && i2.to() < i1.to() : "intervals overlapping";
                    }
                }
            }
        }

        return true;
    }

    public IntervalHint locationHint(boolean searchSplitChild) {
        if (!searchSplitChild) {
            return locationHint;
        }

        if (locationHint != null) {
            assert !(locationHint instanceof TraceInterval) || ((TraceInterval) locationHint).isSplitParent() : "ony split parents are valid hint registers";

            if (locationHint.location() != null && isRegister(locationHint.location())) {
                return locationHint;
            } else if (locationHint instanceof TraceInterval) {
                TraceInterval hint = (TraceInterval) locationHint;
                if (!hint.splitChildren.isEmpty()) {
                    // search the first split child that has a register assigned
                    int len = hint.splitChildren.size();
                    for (int i = 0; i < len; i++) {
                        TraceInterval interval = hint.splitChildren.get(i);
                        if (interval.location != null && isRegister(interval.location)) {
                            return interval;
                        }
                    }
                }
            }
        }

        // no hint interval found that has a register assigned
        return null;
    }

    TraceInterval getSplitChildAtOpId(int opId, LIRInstruction.OperandMode mode, TraceLinearScan allocator) {
        assert isSplitParent() : "can only be called for split parents";
        assert opId >= 0 : "invalid opId (method cannot be called for spill moves)";

        if (splitChildren.isEmpty()) {
            assert this.covers(opId, mode) : this + " does not cover " + opId;
            return this;
        } else {
            TraceInterval result = null;
            int len = splitChildren.size();

            // in outputMode, the end of the interval (opId == cur.to()) is not valid
            int toOffset = (mode == LIRInstruction.OperandMode.DEF ? 0 : 1);

            int i;
            for (i = 0; i < len; i++) {
                TraceInterval cur = splitChildren.get(i);
                if (cur.from() <= opId && opId < cur.to() + toOffset) {
                    if (i > 0) {
                        // exchange current split child to start of list (faster access for next
                        // call)
                        Util.atPutGrow(splitChildren, i, splitChildren.get(0), null);
                        Util.atPutGrow(splitChildren, 0, cur, null);
                    }

                    // interval found
                    result = cur;
                    break;
                }
            }

            assert checkSplitChild(result, opId, allocator, toOffset, mode);
            return result;
        }
    }

    private boolean checkSplitChild(TraceInterval result, int opId, TraceLinearScan allocator, int toOffset, LIRInstruction.OperandMode mode) {
        if (result == null) {
            // this is an error
            StringBuilder msg = new StringBuilder(this.toString()).append(" has no child at ").append(opId);
            if (!splitChildren.isEmpty()) {
                TraceInterval firstChild = splitChildren.get(0);
                TraceInterval lastChild = splitChildren.get(splitChildren.size() - 1);
                msg.append(" (first = ").append(firstChild).append(", last = ").append(lastChild).append(")");
            }
            throw new JVMCIError("Linear Scan Error: %s", msg);
        }

        if (!splitChildren.isEmpty()) {
            for (TraceInterval interval : splitChildren) {
                if (interval != result && interval.from() <= opId && opId < interval.to() + toOffset) {
                    TTY.println(String.format("two valid result intervals found for opId %d: %d and %d", opId, result.operandNumber, interval.operandNumber));
                    TTY.println(result.logString(allocator));
                    TTY.println(interval.logString(allocator));
                    throw new BailoutException("two valid result intervals found");
                }
            }
        }
        assert result.covers(opId, mode) : "opId not covered by interval";
        return true;
    }

    // returns the interval that covers the given opId or null if there is none
    TraceInterval getIntervalCoveringOpId(int opId) {
        assert opId >= 0 : "invalid opId";
        assert opId < to() : "can only look into the past";

        if (opId >= from()) {
            return this;
        }

        TraceInterval parent = splitParent();
        TraceInterval result = null;

        assert !parent.splitChildren.isEmpty() : "no split children available";
        int len = parent.splitChildren.size();

        for (int i = len - 1; i >= 0; i--) {
            TraceInterval cur = parent.splitChildren.get(i);
            if (cur.from() <= opId && opId < cur.to()) {
                assert result == null : "covered by multiple split children " + result + " and " + cur;
                result = cur;
            }
        }

        return result;
    }

    // returns the last split child that ends before the given opId
    TraceInterval getSplitChildBeforeOpId(int opId) {
        assert opId >= 0 : "invalid opId";

        TraceInterval parent = splitParent();
        TraceInterval result = null;

        assert !parent.splitChildren.isEmpty() : "no split children available";
        int len = parent.splitChildren.size();

        for (int i = len - 1; i >= 0; i--) {
            TraceInterval cur = parent.splitChildren.get(i);
            if (cur.to() <= opId && (result == null || result.to() < cur.to())) {
                result = cur;
            }
        }

        assert result != null : "no split child found";
        return result;
    }

    // checks if opId is covered by any split child
    boolean splitChildCovers(int opId, LIRInstruction.OperandMode mode) {
        assert isSplitParent() : "can only be called for split parents";
        assert opId >= 0 : "invalid opId (method can not be called for spill moves)";

        if (splitChildren.isEmpty()) {
            // simple case if interval was not split
            return covers(opId, mode);

        } else {
            // extended case: check all split children
            int len = splitChildren.size();
            for (int i = 0; i < len; i++) {
                TraceInterval cur = splitChildren.get(i);
                if (cur.covers(opId, mode)) {
                    return true;
                }
            }
            return false;
        }
    }

    private RegisterPriority adaptPriority(RegisterPriority priority) {
        /*
         * In case of re-materialized values we require that use-operands are registers, because we
         * don't have the value in a stack location. (Note that ShouldHaveRegister means that the
         * operand can also be a StackSlot).
         */
        if (priority == RegisterPriority.ShouldHaveRegister && canMaterialize()) {
            return RegisterPriority.MustHaveRegister;
        }
        return priority;
    }

    // Note: use positions are sorted descending . first use has highest index
    int firstUsage(RegisterPriority minRegisterPriority) {
        assert isVariable(operand) : "cannot access use positions for fixed intervals";

        for (int i = usePosList.size() - 1; i >= 0; --i) {
            RegisterPriority registerPriority = adaptPriority(usePosList.registerPriority(i));
            if (registerPriority.greaterEqual(minRegisterPriority)) {
                return usePosList.usePos(i);
            }
        }
        return Integer.MAX_VALUE;
    }

    int nextUsage(RegisterPriority minRegisterPriority, int from) {
        assert isVariable(operand) : "cannot access use positions for fixed intervals";

        for (int i = usePosList.size() - 1; i >= 0; --i) {
            int usePos = usePosList.usePos(i);
            if (usePos >= from && adaptPriority(usePosList.registerPriority(i)).greaterEqual(minRegisterPriority)) {
                return usePos;
            }
        }
        return Integer.MAX_VALUE;
    }

    int nextUsageExact(RegisterPriority exactRegisterPriority, int from) {
        assert isVariable(operand) : "cannot access use positions for fixed intervals";

        for (int i = usePosList.size() - 1; i >= 0; --i) {
            int usePos = usePosList.usePos(i);
            if (usePos >= from && adaptPriority(usePosList.registerPriority(i)) == exactRegisterPriority) {
                return usePos;
            }
        }
        return Integer.MAX_VALUE;
    }

    int previousUsage(RegisterPriority minRegisterPriority, int from) {
        assert isVariable(operand) : "cannot access use positions for fixed intervals";

        int prev = -1;
        for (int i = usePosList.size() - 1; i >= 0; --i) {
            int usePos = usePosList.usePos(i);
            if (usePos > from) {
                return prev;
            }
            if (adaptPriority(usePosList.registerPriority(i)).greaterEqual(minRegisterPriority)) {
                prev = usePos;
            }
        }
        return prev;
    }

    public void addUsePos(int pos, RegisterPriority registerPriority) {
        assert isEmpty() || covers(pos, LIRInstruction.OperandMode.USE) : String.format("use position %d not covered by live range of interval %s", pos, this);

        // do not add use positions for precolored intervals because they are never used
        if (registerPriority != RegisterPriority.None && isVariable(operand)) {
            if (DetailedAsserts.getValue()) {
                for (int i = 0; i < usePosList.size(); i++) {
                    assert pos <= usePosList.usePos(i) : "already added a use-position with lower position";
                    if (i > 0) {
                        assert usePosList.usePos(i) < usePosList.usePos(i - 1) : "not sorted descending";
                    }
                }
            }

            // Note: addUse is called in descending order, so list gets sorted
            // automatically by just appending new use positions
            int len = usePosList.size();
            if (len == 0 || usePosList.usePos(len - 1) > pos) {
                usePosList.add(pos, registerPriority);
            } else if (usePosList.registerPriority(len - 1).lessThan(registerPriority)) {
                assert usePosList.usePos(len - 1) == pos : "list not sorted correctly";
                usePosList.setRegisterPriority(len - 1, registerPriority);
            }
        }
    }

    public void addRange(int from, int to) {
        assert from < to : "invalid range";

        if (from < intFrom) {
            intFrom = from;
        }
        if (intTo == Integer.MAX_VALUE || intTo < to) {
            intTo = to;
        }
    }

    TraceInterval newSplitChild(TraceLinearScan allocator) {
        // allocate new interval
        TraceInterval parent = splitParent();
        TraceInterval result = allocator.createDerivedInterval(parent);
        result.setKind(kind());

        result.splitParent = parent;
        result.setLocationHint(parent);

        // insert new interval in children-list of parent
        if (parent.splitChildren.isEmpty()) {
            assert isSplitParent() : "list must be initialized at first split";

            // Create new non-shared list
            parent.splitChildren = new ArrayList<>(4);
            parent.splitChildren.add(this);
        }
        parent.splitChildren.add(result);

        return result;
    }

    /**
     * Splits this interval at a specified position and returns the remainder as a new <i>child</i>
     * interval of this interval's {@linkplain #splitParent() parent} interval.
     * <p>
     * When an interval is split, a bi-directional link is established between the original
     * <i>parent</i> interval and the <i>children</i> intervals that are split off this interval.
     * When a split child is split again, the new created interval is a direct child of the original
     * parent. That is, there is no tree of split children stored, just a flat list. All split
     * children are spilled to the same {@linkplain #spillSlot spill slot}.
     *
     * @param splitPos the position at which to split this interval
     * @param allocator the register allocator context
     * @return the child interval split off from this interval
     */
    TraceInterval split(int splitPos, TraceLinearScan allocator) {
        assert isVariable(operand) : "cannot split fixed intervals";

        // allocate new interval
        TraceInterval result = newSplitChild(allocator);

        // split the ranges
        result.intTo = intTo;
        result.intFrom = splitPos;
        intTo = splitPos;

        // split list of use positions
        result.usePosList = usePosList.splitAt(splitPos);

        if (DetailedAsserts.getValue()) {
            for (int i = 0; i < usePosList.size(); i++) {
                assert usePosList.usePos(i) < splitPos;
            }
            for (int i = 0; i < result.usePosList.size(); i++) {
                assert result.usePosList.usePos(i) >= splitPos;
            }
        }
        return result;
    }

    // returns true if the opId is inside the interval
    boolean covers(int opId, LIRInstruction.OperandMode mode) {
        if (mode == LIRInstruction.OperandMode.DEF) {
            return from() <= opId && opId < to();
        }
        return from() <= opId && opId <= to();
    }

    @Override
    public String toString() {
        String from = "?";
        String to = "?";
        if (!isEmpty()) {
            from = String.valueOf(from());
            to = String.valueOf(to());
        }
        String locationString = this.location == null ? "" : "@" + this.location;
        return operandNumber + ":" + operand + (isRegister(operand) ? "" : locationString) + "[" + from + "," + to + "]";
    }

    /**
     * Gets the use position information for this interval.
     */
    public UsePosList usePosList() {
        return usePosList;
    }

    /**
     * Gets a single line string for logging the details of this interval to a log stream.
     *
     * @param allocator the register allocator context
     */
    @Override
    public String logString(TraceLinearScan allocator) {
        StringBuilder buf = new StringBuilder(100);
        buf.append("any ").append(operandNumber).append(':').append(operand).append(' ');
        if (!isRegister(operand)) {
            if (location != null) {
                buf.append("location{").append(location).append("} ");
            }
        }

        buf.append("hints{").append(splitParent.operandNumber);
        IntervalHint hint = locationHint(false);
        if (hint != null) {
            buf.append(", ").append(hint.location());
        }
        buf.append("} ranges{");

        // print range
        buf.append("[" + from() + ", " + to() + "]");
        buf.append("} uses{");

        // print use positions
        int prev = -1;
        for (int i = usePosList.size() - 1; i >= 0; --i) {
            assert prev < usePosList.usePos(i) : "use positions not sorted";
            if (i != usePosList.size() - 1) {
                buf.append(", ");
            }
            buf.append(usePosList.usePos(i)).append(':').append(usePosList.registerPriority(i).shortName());
            prev = usePosList.usePos(i);
        }
        buf.append("} spill-state{").append(spillState()).append("}");
        if (canMaterialize()) {
            buf.append(" (remat:").append(getMaterializedValue().toString()).append(")");
        }
        return buf.toString();
    }

    List<TraceInterval> getSplitChildren() {
        return Collections.unmodifiableList(splitChildren);
    }

    boolean isFixedInterval() {
        return isRegister(operand);
    }

    private static boolean isDefinitionPosition(int usePos) {
        return (usePos & 1) == 1;
    }

    int currentFrom(int currentPosition) {
        assert isFixedInterval();
        for (int i = 0; i < usePosList.size(); i++) {
            int usePos = usePosList.usePos(i);
            if (usePos <= currentPosition && isDefinitionPosition(usePos)) {
                return usePos;
            }

        }
        return Integer.MAX_VALUE;
    }

    int currentIntersectsAt(int currentPosition, TraceInterval current) {
        assert isFixedInterval();
        assert !current.isFixedInterval();
        int from = Integer.MAX_VALUE;
        int to = Integer.MIN_VALUE;

        for (int i = 0; i < usePosList.size(); i++) {
            int usePos = usePosList.usePos(i);
            if (isDefinitionPosition(usePos)) {
                if (usePos <= currentPosition) {
                    from = usePos;
                    break;
                }
                to = Integer.MIN_VALUE;
            } else {
                if (to < usePos) {
                    to = usePos;
                }
            }
        }
        if (from < current.from()) {
            if (to <= current.from()) {
                return -1;
            }
            return current.from();
        } else {
            if (current.to() <= from) {
                return -1;
            }
            return from;
        }
    }
}
