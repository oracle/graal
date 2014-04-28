/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.gen;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.api.meta.Value.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.lir.LIR.*;
import static com.oracle.graal.lir.LIRValueUtil.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.compiler.common.spi.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.StandardOp.NoOp;
import com.oracle.graal.options.*;

/**
 * This class traverses the HIR instructions and generates LIR instructions from them.
 */
public abstract class LIRGenerator implements LIRGeneratorTool, PlatformKindTool {

    public static class Options {
        // @formatter:off
        @Option(help = "Print HIR along side LIR as the latter is generated")
        public static final OptionValue<Boolean> PrintIRWithLIR = new OptionValue<>(false);
        @Option(help = "The trace level for the LIR generator")
        public static final OptionValue<Integer> TraceLIRGeneratorLevel = new OptionValue<>(0);
        // @formatter:on
    }

    private final CodeGenProviders providers;
    private final CallingConvention cc;

    private AbstractBlock<?> currentBlock;

    /**
     * Handle for an operation that loads a constant into a variable. The operation starts in the
     * first block where the constant is used but will eventually be
     * {@linkplain LIRGenerator#insertConstantLoads() moved} to a block dominating all usages of the
     * constant.
     */
    public static class LoadConstant implements Comparable<LoadConstant> {
        /**
         * The index of {@link #op} within {@link #block}'s instruction list or -1 if {@code op} is
         * to be moved to a dominator block.
         */
        int index;

        /**
         * The operation that loads the constant.
         */
        private final LIRInstruction op;

        /**
         * The block that does or will contain {@link #op}. This is initially the block where the
         * first usage of the constant is seen during LIR generation.
         */
        AbstractBlock<?> block;

        /**
         * The variable into which the constant is loaded.
         */
        final Variable variable;

        public LoadConstant(Variable variable, AbstractBlock<?> block, int index, LIRInstruction op) {
            this.variable = variable;
            this.block = block;
            this.index = index;
            this.op = op;
        }

        /**
         * Sorts {@link LoadConstant} objects according to their enclosing blocks. This is used to
         * group loads per block in {@link LIRGenerator#insertConstantLoads()}.
         */
        public int compareTo(LoadConstant o) {
            if (block.getId() < o.block.getId()) {
                return -1;
            }
            if (block.getId() > o.block.getId()) {
                return 1;
            }
            return 0;
        }

        @Override
        public String toString() {
            return block + "#" + op;
        }

        /**
         * Removes the {@link #op} from its original location if it is still at that location.
         */
        public void unpin(LIR lir) {
            if (index >= 0) {
                // Replace the move with a filler op so that the operation
                // list does not need to be adjusted.
                List<LIRInstruction> instructions = lir.getLIRforBlock(block);
                instructions.set(index, new NoOp(null, -1));
                index = -1;
            }
        }

        public AbstractBlock<?> getBlock() {
            return block;
        }

        public void setBlock(AbstractBlock<?> block) {
            this.block = block;
        }

        public Variable getVariable() {
            return variable;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }
    }

    private Map<Constant, LoadConstant> constantLoads;

    private LIRGenerationResult res;

    /**
     * Checks whether the supplied constant can be used without loading it into a register for store
     * operations, i.e., on the right hand side of a memory access.
     *
     * @param c The constant to check.
     * @return True if the constant can be used directly, false if the constant needs to be in a
     *         register.
     */
    public abstract boolean canStoreConstant(Constant c, boolean isCompressed);

    public LIRGenerator(CodeGenProviders providers, CallingConvention cc, LIRGenerationResult res) {
        this.res = res;
        this.providers = providers;
        this.cc = cc;
    }

    /**
     * Returns true if the redundant move elimination optimization should be done after register
     * allocation.
     */
    public boolean canEliminateRedundantMoves() {
        return true;
    }

    @Override
    public TargetDescription target() {
        return getCodeCache().getTarget();
    }

    public CodeGenProviders getProviders() {
        return providers;
    }

    @Override
    public MetaAccessProvider getMetaAccess() {
        return providers.getMetaAccess();
    }

    @Override
    public CodeCacheProvider getCodeCache() {
        return providers.getCodeCache();
    }

    @Override
    public ForeignCallsProvider getForeignCalls() {
        return providers.getForeignCalls();
    }

    /**
     * Creates a new {@linkplain Variable variable}.
     *
     * @param platformKind The kind of the new variable.
     * @return a new variable
     */
    @Override
    public Variable newVariable(PlatformKind platformKind) {
        return new Variable(platformKind, res.getLIR().nextVariable());
    }

    @Override
    public RegisterAttributes attributes(Register register) {
        return res.getFrameMap().registerConfig.getAttributesMap()[register.number];
    }

    @Override
    public abstract Variable emitMove(Value input);

    public AllocatableValue asAllocatable(Value value) {
        if (isAllocatableValue(value)) {
            return asAllocatableValue(value);
        } else {
            return emitMove(value);
        }
    }

    public Variable load(Value value) {
        if (!isVariable(value)) {
            return emitMove(value);
        }
        return (Variable) value;
    }

    public Value loadNonConst(Value value) {
        if (isConstant(value) && !canInlineConstant((Constant) value)) {
            return emitMove(value);
        }
        return value;
    }

    /**
     * Determines if only oop maps are required for the code generated from the LIR.
     */
    public boolean needOnlyOopMaps() {
        return false;
    }

    /**
     * Gets the ABI specific operand used to return a value of a given kind from a method.
     *
     * @param kind the kind of value being returned
     * @return the operand representing the ABI defined location used return a value of kind
     *         {@code kind}
     */
    public AllocatableValue resultOperandFor(Kind kind) {
        if (kind == Kind.Void) {
            return ILLEGAL;
        }
        return res.getFrameMap().registerConfig.getReturnRegister(kind).asValue(kind);
    }

    public void append(LIRInstruction op) {
        if (Options.PrintIRWithLIR.getValue() && !TTY.isSuppressed()) {
            TTY.println(op.toStringWithIdPrefix());
            TTY.println();
        }
        assert LIRVerifier.verify(op);
        res.getLIR().getLIRforBlock(currentBlock).add(op);
    }

    public boolean hasBlockEnd(AbstractBlock<?> block) {
        List<LIRInstruction> ops = getResult().getLIR().getLIRforBlock(block);
        if (ops.size() == 0) {
            return false;
        }
        return ops.get(ops.size() - 1) instanceof BlockEndOp;
    }

    public final void doBlockStart(AbstractBlock<?> block) {
        if (Options.PrintIRWithLIR.getValue()) {
            TTY.print(block.toString());
        }

        currentBlock = block;

        // set up the list of LIR instructions
        assert res.getLIR().getLIRforBlock(block) == null : "LIR list already computed for this block";
        res.getLIR().setLIRforBlock(block, new ArrayList<LIRInstruction>());

        append(new LabelOp(new Label(block.getId()), block.isAligned()));

        if (Options.TraceLIRGeneratorLevel.getValue() >= 1) {
            TTY.println("BEGIN Generating LIR for block B" + block.getId());
        }
    }

    public final void doBlockEnd(AbstractBlock<?> block) {

        if (Options.TraceLIRGeneratorLevel.getValue() >= 1) {
            TTY.println("END Generating LIR for block B" + block.getId());
        }

        currentBlock = null;

        if (Options.PrintIRWithLIR.getValue()) {
            TTY.println();
        }
    }

    public void emitIncomingValues(Value[] params) {
        ((LabelOp) res.getLIR().getLIRforBlock(currentBlock).get(0)).setIncomingValues(params);
    }

    public abstract void emitJump(LabelRef label);

    public abstract void emitCompareBranch(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination,
                    double trueDestinationProbability);

    public abstract void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, double overflowProbability);

    public abstract void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability);

    public abstract Variable emitConditionalMove(PlatformKind cmpKind, Value leftVal, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue);

    public abstract Variable emitIntegerTestMove(Value leftVal, Value right, Value trueValue, Value falseValue);

    protected abstract void emitForeignCall(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info);

    public static AllocatableValue toStackKind(AllocatableValue value) {
        if (value.getKind().getStackKind() != value.getKind()) {
            // We only have stack-kinds in the LIR, so convert the operand kind for values from the
            // calling convention.
            if (isRegister(value)) {
                return asRegister(value).asValue(value.getKind().getStackKind());
            } else if (isStackSlot(value)) {
                return StackSlot.get(value.getKind().getStackKind(), asStackSlot(value).getRawOffset(), asStackSlot(value).getRawAddFrameSize());
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        }
        return value;
    }

    @Override
    public Variable emitForeignCall(ForeignCallLinkage linkage, LIRFrameState frameState, Value... args) {
        LIRFrameState state = null;
        if (linkage.canDeoptimize()) {
            if (frameState != null) {
                state = frameState;
            } else {
                assert needOnlyOopMaps();
                state = new LIRFrameState(null, null, null);
            }
        }

        // move the arguments into the correct location
        CallingConvention linkageCc = linkage.getOutgoingCallingConvention();
        res.getFrameMap().callsMethod(linkageCc);
        assert linkageCc.getArgumentCount() == args.length : "argument count mismatch";
        Value[] argLocations = new Value[args.length];
        for (int i = 0; i < args.length; i++) {
            Value arg = args[i];
            AllocatableValue loc = linkageCc.getArgument(i);
            emitMove(loc, arg);
            argLocations[i] = loc;
        }
        res.setForeignCall(true);
        emitForeignCall(linkage, linkageCc.getReturn(), argLocations, linkage.getTemporaries(), state);

        if (isLegal(linkageCc.getReturn())) {
            return emitMove(linkageCc.getReturn());
        } else {
            return null;
        }
    }

    public void emitStrategySwitch(Constant[] keyConstants, double[] keyProbabilities, LabelRef[] keyTargets, LabelRef defaultTarget, Variable value) {
        int keyCount = keyConstants.length;
        SwitchStrategy strategy = SwitchStrategy.getBestStrategy(keyProbabilities, keyConstants, keyTargets);
        long valueRange = keyConstants[keyCount - 1].asLong() - keyConstants[0].asLong() + 1;
        double tableSwitchDensity = keyCount / (double) valueRange;
        /*
         * This heuristic tries to find a compromise between the effort for the best switch strategy
         * and the density of a tableswitch. If the effort for the strategy is at least 4, then a
         * tableswitch is preferred if better than a certain value that starts at 0.5 and lowers
         * gradually with additional effort.
         */
        if (strategy.getAverageEffort() < 4 || tableSwitchDensity < (1 / Math.sqrt(strategy.getAverageEffort()))) {
            emitStrategySwitch(strategy, value, keyTargets, defaultTarget);
        } else {
            int minValue = keyConstants[0].asInt();
            assert valueRange < Integer.MAX_VALUE;
            LabelRef[] targets = new LabelRef[(int) valueRange];
            for (int i = 0; i < valueRange; i++) {
                targets[i] = defaultTarget;
            }
            for (int i = 0; i < keyCount; i++) {
                targets[keyConstants[i].asInt() - minValue] = keyTargets[i];
            }
            emitTableSwitch(minValue, defaultTarget, targets, value);
        }
    }

    public abstract void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget);

    protected abstract void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key);

    public CallingConvention getCallingConvention() {
        return cc;
    }

    @Override
    public void beforeRegisterAllocation() {
        insertConstantLoads();
    }

    /**
     * Moves deferred {@linkplain LoadConstant loads} of constants into blocks dominating all usages
     * of the constant. Any operations inserted into a block are guaranteed to be immediately prior
     * to the first control flow instruction near the end of the block.
     */
    private void insertConstantLoads() {
        if (constantLoads != null) {
            // Remove loads where all usages are in the same block.
            for (Iterator<Map.Entry<Constant, LoadConstant>> iter = constantLoads.entrySet().iterator(); iter.hasNext();) {
                LoadConstant lc = iter.next().getValue();

                // Move loads of constant outside of loops
                if (OptScheduleOutOfLoops.getValue()) {
                    AbstractBlock<?> outOfLoopDominator = lc.block;
                    while (outOfLoopDominator.getLoop() != null) {
                        outOfLoopDominator = outOfLoopDominator.getDominator();
                    }
                    if (outOfLoopDominator != lc.block) {
                        lc.unpin(res.getLIR());
                        lc.block = outOfLoopDominator;
                    }
                }

                if (lc.index != -1) {
                    assert res.getLIR().getLIRforBlock(lc.block).get(lc.index) == lc.op;
                    iter.remove();
                }
            }
            if (constantLoads.isEmpty()) {
                return;
            }

            // Sorting groups the loads per block.
            LoadConstant[] groupedByBlock = constantLoads.values().toArray(new LoadConstant[constantLoads.size()]);
            Arrays.sort(groupedByBlock);

            int groupBegin = 0;
            while (true) {
                int groupEnd = groupBegin + 1;
                AbstractBlock<?> block = groupedByBlock[groupBegin].block;
                while (groupEnd < groupedByBlock.length && groupedByBlock[groupEnd].block == block) {
                    groupEnd++;
                }
                int groupSize = groupEnd - groupBegin;

                List<LIRInstruction> ops = res.getLIR().getLIRforBlock(block);
                int lastIndex = ops.size() - 1;
                assert ops.get(lastIndex) instanceof BlockEndOp;
                int insertionIndex = lastIndex;
                for (int i = Math.max(0, lastIndex - MAX_EXCEPTION_EDGE_OP_DISTANCE_FROM_END); i < lastIndex; i++) {
                    if (getExceptionEdge(ops.get(i)) != null) {
                        insertionIndex = i;
                        break;
                    }
                }

                if (groupSize == 1) {
                    ops.add(insertionIndex, groupedByBlock[groupBegin].op);
                } else {
                    assert groupSize > 1;
                    List<LIRInstruction> moves = new ArrayList<>(groupSize);
                    for (int i = groupBegin; i < groupEnd; i++) {
                        moves.add(groupedByBlock[i].op);
                    }
                    ops.addAll(insertionIndex, moves);
                }

                if (groupEnd == groupedByBlock.length) {
                    break;
                }
                groupBegin = groupEnd;
            }
            constantLoads = null;
        }
    }

    /**
     * Gets a garbage value for a given kind.
     */
    protected Constant zapValueForKind(PlatformKind kind) {
        long dead = 0xDEADDEADDEADDEADL;
        switch ((Kind) kind) {
            case Boolean:
                return Constant.FALSE;
            case Byte:
                return Constant.forByte((byte) dead);
            case Char:
                return Constant.forChar((char) dead);
            case Short:
                return Constant.forShort((short) dead);
            case Int:
                return Constant.forInt((int) dead);
            case Double:
                return Constant.forDouble(Double.longBitsToDouble(dead));
            case Float:
                return Constant.forFloat(Float.intBitsToFloat((int) dead));
            case Long:
                return Constant.forLong(dead);
            case Object:
                return Constant.NULL_OBJECT;
            default:
                throw new IllegalArgumentException(kind.toString());
        }
    }

    /**
     * Default implementation: Return the Java stack kind for each stamp.
     */
    public PlatformKind getPlatformKind(Stamp stamp) {
        return stamp.getPlatformKind(this);
    }

    public PlatformKind getIntegerKind(int bits) {
        if (bits <= 8) {
            return Kind.Byte;
        } else if (bits <= 16) {
            return Kind.Short;
        } else if (bits <= 32) {
            return Kind.Int;
        } else {
            assert bits <= 64;
            return Kind.Long;
        }
    }

    public PlatformKind getFloatingKind(int bits) {
        switch (bits) {
            case 32:
                return Kind.Float;
            case 64:
                return Kind.Double;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    public PlatformKind getObjectKind() {
        return Kind.Object;
    }

    public abstract void emitBitCount(Variable result, Value operand);

    public abstract void emitBitScanForward(Variable result, Value operand);

    public abstract void emitBitScanReverse(Variable result, Value operand);

    public abstract void emitByteSwap(Variable result, Value operand);

    public abstract void emitArrayEquals(Kind kind, Variable result, Value array1, Value array2, Value length);

    public AbstractBlock<?> getCurrentBlock() {
        return currentBlock;
    }

    void setCurrentBlock(AbstractBlock<?> block) {
        currentBlock = block;
    }

    public LIRGenerationResult getResult() {
        return res;
    }

    public Map<Constant, LoadConstant> getConstantLoads() {
        return constantLoads;
    }

    public void setConstantLoads(Map<Constant, LoadConstant> constantLoads) {
        this.constantLoads = constantLoads;
    }
}
