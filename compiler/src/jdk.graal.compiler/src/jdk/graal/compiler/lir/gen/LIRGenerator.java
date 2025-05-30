/*
 * Copyright (c) 2009, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.gen;

import static jdk.graal.compiler.core.common.GraalOptions.LoopHeaderAlignment;
import static jdk.vm.ci.code.ValueUtil.asAllocatableValue;
import static jdk.vm.ci.code.ValueUtil.asStackSlot;
import static jdk.vm.ci.code.ValueUtil.isAllocatableValue;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.vm.ci.code.ValueUtil.isLegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.core.common.spi.LIRKindTool;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.LabelRef;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.SwitchStrategy;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.hashing.IntHasher;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.CoreProvidersDelegate;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterAttributes;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * This class traverses the HIR instructions and generates LIR instructions from them.
 */
public abstract class LIRGenerator extends CoreProvidersDelegate implements LIRGeneratorTool {

    private final int loopHeaderAlignment;

    public static class Options {
        // @formatter:off
        @Option(help = "Print HIR along side LIR as the latter is generated", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintIRWithLIR = new OptionKey<>(false);
        @Option(help = "The trace level for the LIR generator", type = OptionType.Debug)
        public static final OptionKey<Integer> TraceLIRGeneratorLevel = new OptionKey<>(0);
        // @formatter:on
    }

    private final LIRKindTool lirKindTool;

    private BasicBlock<?> currentBlock;

    private LIRGenerationResult res;

    protected final ArithmeticLIRGenerator arithmeticLIRGen;
    protected final BarrierSetLIRGeneratorTool barrierSetLIRGen;

    private final MoveFactory moveFactory;

    private final boolean printIrWithLir;
    private final int traceLIRGeneratorLevel;

    @SuppressWarnings("this-escape")
    public LIRGenerator(LIRKindTool lirKindTool, ArithmeticLIRGenerator arithmeticLIRGen, BarrierSetLIRGeneratorTool barrierSetLIRGen, MoveFactory moveFactory, CoreProviders providers,
                    LIRGenerationResult res) {
        super(providers);
        this.lirKindTool = lirKindTool;
        this.arithmeticLIRGen = arithmeticLIRGen;
        this.barrierSetLIRGen = barrierSetLIRGen;
        this.res = res;
        OptionValues options = res.getLIR().getOptions();
        this.printIrWithLir = !TTY.isSuppressed() && Options.PrintIRWithLIR.getValue(options);
        this.traceLIRGeneratorLevel = TTY.isSuppressed() ? 0 : Options.TraceLIRGeneratorLevel.getValue(options);
        this.loopHeaderAlignment = LoopHeaderAlignment.getValue(options);

        this.moveFactory = moveFactory;

        arithmeticLIRGen.setLirGen(this);
    }

    @Override
    public ArithmeticLIRGeneratorTool getArithmetic() {
        return arithmeticLIRGen;
    }

    @Override
    public BarrierSetLIRGeneratorTool getBarrierSet() {
        return barrierSetLIRGen;
    }

    @Override
    public MoveFactory getMoveFactory() {
        return moveFactory;
    }

    private MoveFactory spillMoveFactory;

    @Override
    public MoveFactory getSpillMoveFactory() {
        if (spillMoveFactory == null) {
            boolean verify = false;
            assert (verify = true) == true;
            if (verify) {
                spillMoveFactory = new VerifyingMoveFactory(moveFactory);
            } else {
                spillMoveFactory = moveFactory;
            }
        }
        return spillMoveFactory;
    }

    @Override
    public LIRKind getValueKind(JavaKind javaKind) {
        return LIRKind.fromJavaKind(target().arch, javaKind);
    }

    @Override
    public TargetDescription target() {
        return getCodeCache().getTarget();
    }

    public LIRKindTool getLIRKindTool() {
        return lirKindTool;
    }

    /**
     * Hide {@link #nextVariable()} from other users.
     */
    public abstract static class VariableProvider {
        private int numVariables;

        public int numVariables() {
            return numVariables;
        }

        private int nextVariable() {
            return numVariables++;
        }
    }

    @Override
    public Variable newVariable(ValueKind<?> valueKind) {
        return new Variable(valueKind, ((VariableProvider) res.getLIR()).nextVariable());
    }

    @Override
    public RegisterConfig getRegisterConfig() {
        return res.getRegisterConfig();
    }

    public RegisterAttributes attributes(Register register) {
        return getRegisterConfig().getAttributesMap().get(register.number);
    }

    @Override
    public Variable emitMove(Value input) {
        assert !LIRValueUtil.isVariable(input) : "Creating a copy of a variable via this method is not supported (and potentially a bug): " + input;
        return emitMoveHelper(input.getValueKind(), input);
    }

    @Override
    public Variable emitMove(ValueKind<?> dst, Value src) {
        return emitMoveHelper(dst, src);
    }

    private Variable emitMoveHelper(ValueKind<?> dst, Value input) {
        Variable result = newVariable(dst);
        emitMove(result, input);
        return result;
    }

    @Override
    public void emitMove(AllocatableValue dst, Value src) {
        append(moveFactory.createMove(dst, src));
    }

    @Override
    public Variable emitReadRegister(Register register, ValueKind<?> kind) {
        return emitMove(register.asValue(kind));
    }

    @Override
    public void emitWriteRegister(Register dst, Value src, ValueKind<?> kind) {
        emitMove(dst.asValue(kind), src);
    }

    @Override
    public void emitMoveConstant(AllocatableValue dst, Constant src) {
        append(moveFactory.createLoad(dst, src));
    }

    @Override
    public boolean canInlineConstant(Constant constant) {
        return moveFactory.canInlineConstant(constant);
    }

    @Override
    public boolean mayEmbedConstantLoad(Constant constant) {
        return moveFactory.mayEmbedConstantLoad(constant);
    }

    @Override
    public Value emitConstant(LIRKind kind, Constant constant) {
        if (moveFactory.canInlineConstant(constant)) {
            return new ConstantValue(toRegisterKind(kind), constant);
        } else {
            return emitLoadConstant(toRegisterKind(kind), constant);
        }
    }

    @Override
    public Value emitJavaConstant(JavaConstant constant) {
        return emitConstant(getValueKind(constant.getJavaKind()), constant);
    }

    @Override
    public AllocatableValue emitLoadConstant(ValueKind<?> kind, Constant constant) {
        Variable result = newVariable(kind);
        emitMoveConstant(result, constant);
        return result;
    }

    @Override
    public AllocatableValue asAllocatable(Value value) {
        if (isAllocatableValue(value)) {
            return asAllocatableValue(value);
        } else if (LIRValueUtil.isConstantValue(value)) {
            return emitLoadConstant(value.getValueKind(), LIRValueUtil.asConstant(value));
        } else {
            return emitMove(value);
        }
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
     * @param javaKind the kind of value being returned
     * @param valueKind the backend type of the value being returned
     * @return the operand representing the ABI defined location used return a value of kind
     *         {@code kind}
     */
    public AllocatableValue resultOperandFor(JavaKind javaKind, ValueKind<?> valueKind) {
        Register reg = getRegisterConfig().getReturnRegister(javaKind);
        assert target().arch.canStoreValue(reg.getRegisterCategory(), valueKind.getPlatformKind()) : reg.getRegisterCategory() + " " + valueKind.getPlatformKind();
        return reg.asValue(valueKind);
    }

    NodeSourcePosition currentPosition;

    public void setSourcePosition(NodeSourcePosition position) {
        currentPosition = position;
    }

    private static boolean verify(final LIRInstruction op) {
        op.visitEachInput(LIRGenerator::allowed);
        op.visitEachAlive(LIRGenerator::allowed);
        op.visitEachState(LIRGenerator::allowed);
        op.visitEachTemp(LIRGenerator::allowed);
        op.visitEachOutput(LIRGenerator::allowed);

        op.verify();
        return true;
    }

    // @formatter:off
    private static void allowed(Object op, Value val, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
        Value value = LIRValueUtil.stripCast(val);
        if ((LIRValueUtil.isVariable(value) && flags.contains(LIRInstruction.OperandFlag.REG)) ||
            (isRegister(value) && flags.contains(LIRInstruction.OperandFlag.REG)) ||
            (LIRValueUtil.isStackSlotValue(value) && flags.contains(LIRInstruction.OperandFlag.STACK)) ||
            (LIRValueUtil.isConstantValue(value) && flags.contains(LIRInstruction.OperandFlag.CONST) && mode != LIRInstruction.OperandMode.DEF) ||
            (isIllegal(value) && flags.contains(LIRInstruction.OperandFlag.ILLEGAL))) {
            return;
        }
        throw new GraalError("Invalid LIR%n  Instruction: %s%n  Mode: %s%n  Flags: %s%n  Unexpected value: %s %s",
                        op, mode, flags, value.getClass().getSimpleName(), value);
    }
    // @formatter:on

    @Override
    public <I extends LIRInstruction> I append(I op) {
        LIR lir = res.getLIR();
        if (printIrWithLir) {
            TTY.println(op.toStringWithIdPrefix());
            TTY.println();
        }
        assert verify(op);
        ArrayList<LIRInstruction> lirForBlock = lir.getLIRforBlock(getCurrentBlock());
        op.setPosition(currentPosition);

        assert !lirForBlock.contains(op) : "added " + op + " twice";
        lirForBlock.add(op);
        return op;
    }

    public boolean hasBlockEnd(BasicBlock<?> block) {
        ArrayList<LIRInstruction> ops = getResult().getLIR().getLIRforBlock(block);
        if (ops.size() == 0) {
            return false;
        }
        return ops.get(ops.size() - 1) instanceof StandardOp.BlockEndOp;
    }

    private final class BlockScopeImpl extends BlockScope {

        private BlockScopeImpl(BasicBlock<?> block) {
            currentBlock = block;
        }

        private void doBlockStart() {
            if (printIrWithLir) {
                TTY.print(currentBlock.toString());
            }

            // set up the list of LIR instructions
            assert res.getLIR().getLIRforBlock(currentBlock) == null : "LIR list already computed for this block";
            res.getLIR().setLIRforBlock(currentBlock, new ArrayList<>());

            append(new StandardOp.LabelOp(new Label(currentBlock.getId()), currentBlock.isAligned() ? loopHeaderAlignment : 0));

            if (traceLIRGeneratorLevel >= 1) {
                TTY.println("BEGIN Generating LIR for block B" + currentBlock.getId());
            }
        }

        private void doBlockEnd() {
            if (traceLIRGeneratorLevel >= 1) {
                TTY.println("END Generating LIR for block B" + currentBlock.getId());
            }

            if (printIrWithLir) {
                TTY.println();
            }
            currentBlock = null;
        }

        @Override
        public BasicBlock<?> getCurrentBlock() {
            return currentBlock;
        }

        @Override
        public void close() {
            doBlockEnd();
        }

    }

    public final BlockScope getBlockScope(BasicBlock<?> block) {
        BlockScopeImpl blockScope = new BlockScopeImpl(block);
        blockScope.doBlockStart();
        return blockScope;
    }

    private final class MatchScope implements DebugCloseable {

        private MatchScope(BasicBlock<?> block) {
            currentBlock = block;
        }

        @Override
        public void close() {
            currentBlock = null;
        }

    }

    public final DebugCloseable getMatchScope(BasicBlock<?> block) {
        MatchScope matchScope = new MatchScope(block);
        return matchScope;
    }

    public void emitIncomingValues(Value[] params) {
        ((StandardOp.LabelOp) res.getLIR().getLIRforBlock(getCurrentBlock()).get(0)).setIncomingValues(params);
    }

    @Override
    public abstract void emitJump(LabelRef label);

    public abstract void emitCompareBranch(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination,
                    double trueDestinationProbability);

    public abstract void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, LIRKind cmpKind, double overflowProbability);

    public abstract void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability);

    public abstract void emitOpMaskTestBranch(Value left, boolean negateLeft, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability);

    public abstract void emitOpMaskOrTestBranch(Value left, Value right, boolean allZeros, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability);

    @Override
    public abstract Variable emitConditionalMove(PlatformKind cmpKind, Value leftVal, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue);

    @Override
    public abstract Variable emitIntegerTestMove(Value leftVal, Value right, Value trueValue, Value falseValue);

    public abstract Variable emitOpMaskTestMove(Value leftVal, boolean negateLeft, Value right, Value trueValue, Value falseValue);

    public abstract Variable emitOpMaskOrTestMove(Value leftVal, Value right, boolean allZeros, Value trueValue, Value falseValue);

    /** Loads the target address for indirect {@linkplain #emitForeignCall foreign calls}. */
    protected Value emitIndirectForeignCallAddress(@SuppressWarnings("unused") ForeignCallLinkage linkage) {
        return null;
    }

    /**
     * Emits the single call operation at the heart of generating LIR for a
     * {@linkplain #emitForeignCall foreign call}.
     */
    protected abstract void emitForeignCallOp(ForeignCallLinkage linkage, Value targetAddress, Value result, Value[] arguments, Value[] temps, LIRFrameState info);

    @Override
    public Variable emitForeignCall(ForeignCallLinkage linkage, LIRFrameState frameState, Value... args) {
        LIRFrameState state = null;
        if (linkage.needsDebugInfo()) {
            if (frameState != null) {
                state = frameState;
            } else {
                assert needOnlyOopMaps();
                state = new LIRFrameState(null, null, null, false);
            }
        }

        Value targetAddress = emitIndirectForeignCallAddress(linkage);

        // move the arguments into the correct location
        CallingConvention linkageCc = linkage.getOutgoingCallingConvention();
        res.getFrameMapBuilder().callsMethod(linkageCc);
        assert linkageCc.getArgumentCount() == args.length : "argument count mismatch";
        Value[] argLocations = new Value[args.length];
        for (int i = 0; i < args.length; i++) {
            Value arg = args[i];
            AllocatableValue loc = linkageCc.getArgument(i);
            emitMove(loc, arg);
            argLocations[i] = loc;
        }

        res.setForeignCall(true);
        emitForeignCallOp(linkage, targetAddress, linkageCc.getReturn(), argLocations, linkage.getTemporaries(), state);

        if (isLegal(linkageCc.getReturn())) {
            return emitMove(linkageCc.getReturn());
        } else {
            return null;
        }
    }

    /**
     * If we decide that the switch strategy is better in terms of throughput, do it, this means we
     * consider an indirect jump to be JUMP_TABLE_THRESHOLD times as expensive as a compare and
     * branch.
     */
    private static final double JUMP_TABLE_THRESHOLD = 3;

    /**
     * Try to come up with a preferable way to execute a TableSwitch.
     *
     * <ol>
     * <li>If a series of compare and branches is deemed better throughput-wise, then it should be a
     * win both in terms of time and space. As a result, it would be chosen. Note that this is not
     * absolutely true, though, as a typical direct jump table entry is 4 bytes while a compare and
     * branch is often 8 bytes on A64 and 10 bytes on x64.</li>
     * <li>Else if the table is dense enough or it is not too large, then we generate a direct jump
     * table.</li>
     * <li>Else we try to find a subset of keys that contains the majority of the jump targets by
     * frequency. If there is such a subset that is dense enough or not too large, we generate a
     * jump table for that subset, the remaining targets including the default one are reached with
     * a series of compare and branches in a slowpath stub.</li>
     * <li>Else we try to generate a hashed jump table, if the hashed jump table is dense enough
     * then we choose it. Note that a hashed jump table is larger than a direct one so we do not
     * need to consider its size now.</li>
     * <li>Else we fall back to a series of compare and branches.</li>
     * </ol>
     */
    public void emitStrategySwitch(JavaConstant[] keyConstants, double[] keyProbabilities, LabelRef[] keyTargets, LabelRef defaultTarget, AllocatableValue value) {
        SwitchStrategy strategy = SwitchStrategy.getBestStrategy(keyProbabilities, keyConstants, keyTargets);

        if (strategy.getAverageEffort() < JUMP_TABLE_THRESHOLD) {
            emitStrategySwitch(strategy, value, keyTargets, defaultTarget);
            return;
        }

        // If the density of the jump table would be larger than this value then we will emit one
        double minDensity = 1 / Math.sqrt(strategy.getAverageEffort());

        Subrange subrangeForDirectJump = findSubrangeForDirectJumpTable(keyConstants, keyProbabilities, minDensity);
        if (subrangeForDirectJump != null) {
            int loIdx = subrangeForDirectJump.loIdx;

            // Micro-optimization, try to extend minValue to 0 so that we may avoid the subtraction
            int minValue = keyConstants[loIdx].asInt();
            if (minValue > 0) {
                int extendedLoIdx = 0;
                for (; extendedLoIdx <= loIdx; extendedLoIdx++) {
                    if (keyConstants[extendedLoIdx].asInt() >= 0) {
                        break;
                    }
                }
                if (directJumpForSubrange(keyConstants, minDensity, 0, extendedLoIdx, subrangeForDirectJump.hiIdx)) {
                    loIdx = extendedLoIdx;
                    minValue = 0;
                }
            }

            emitDirectJumpTableHelper(keyConstants, keyProbabilities, keyTargets, defaultTarget, value, minValue, loIdx, subrangeForDirectJump.hiIdx);
            return;
        }

        // Try a hashed jump table
        Optional<IntHasher> hasher = hasherFor(keyConstants);
        if (hasher.isEmpty()) {
            emitStrategySwitch(strategy, value, keyTargets, defaultTarget);
            return;
        }

        IntHasher h = hasher.get();
        double hashDensity = (double) keyConstants.length / Integer.toUnsignedLong(h.cardinality);
        // No need to check for size since if this table is too big for a guaranteed direct jump
        // then it is surely too big for a guaranteed hashed jump
        if (hashDensity >= minDensity) {
            emitHashedJumpTableHelper(keyConstants, keyTargets, defaultTarget, value, h);
        } else {
            emitStrategySwitch(strategy, value, keyTargets, defaultTarget);
        }
    }

    /**
     * A subrange of the keyConstants array.
     */
    private record Subrange(int loIdx, int hiIdx) {
    }

    /**
     * The threshold at which a subrange is considered a common path and we will decide if a jump
     * table for that subrange is acceptable.
     */
    private static final double MAJORITY_THRESHOLD = 0.99;

    /**
     * The general approach is to check the most frequent targets to see if we can emit a direct
     * jump table with them, the default target will now check the other remaining targets. In the
     * best case, our subrange will cover the whole range and there is no remaining target.
     */
    private static Subrange findSubrangeForDirectJumpTable(JavaConstant[] keyConstants, double[] keyProbabilities, double minDensity) {
        if (directJumpForSubrange(keyConstants, minDensity, keyConstants[0].asInt(), 0, keyConstants.length - 1)) {
            // If all cases can be fit into a jump table then ignore the probability
            return new Subrange(0, keyConstants.length - 1);
        }

        // The probability of all key targets starting from loIdx
        double loRangeProbability = 0;
        // Since loIdx starts at 0, this is the combined probability of all key targets
        for (int i = 0; i < keyConstants.length; i++) {
            loRangeProbability += keyProbabilities[i];
        }

        // This loop nest simply traverses all the subranges
        for (int loIdx = 0; loIdx < keyConstants.length; loIdx++) {
            double subrangeProbability = loRangeProbability;
            if (subrangeProbability < MAJORITY_THRESHOLD) {
                break;
            }

            // Walk backward so we can have the largest subrange possible
            for (int hiIdx = keyConstants.length - 1; hiIdx >= loIdx; hiIdx--) {
                if (subrangeProbability < MAJORITY_THRESHOLD) {
                    break;
                }

                if (directJumpForSubrange(keyConstants, minDensity, keyConstants[loIdx].asInt(), loIdx, hiIdx)) {
                    return new Subrange(loIdx, hiIdx);
                }

                subrangeProbability -= keyProbabilities[hiIdx];
            }

            loRangeProbability -= keyProbabilities[loIdx];
        }

        // No preferable subrange found
        return null;
    }

    private void emitDirectJumpTableHelper(JavaConstant[] keyConstants, double[] keyProbs, LabelRef[] keyTargets, LabelRef defaultTarget, AllocatableValue value, int minValue, int loIdx, int hiIdx) {
        int maxValue = keyConstants[hiIdx].asInt();
        // This cannot overflow because we have ensured that above
        int subrangeValueRange = maxValue - minValue + 1;
        GraalError.guarantee(Integer.compareUnsigned(subrangeValueRange, Integer.MAX_VALUE) <= 0, "too large jump table: %s - %d - %d", Arrays.toString(keyConstants), minValue, maxValue);
        LabelRef[] targets = new LabelRef[subrangeValueRange];
        Arrays.fill(targets, defaultTarget);
        for (int i = loIdx; i <= hiIdx; i++) {
            targets[keyConstants[i].asInt() - minValue] = keyTargets[i];
        }

        // Use a switch strategy to process the remaining targets
        int remainingKeyCount = keyConstants.length - (hiIdx - loIdx + 1);
        SwitchStrategy remainingStrategy = null;
        LabelRef[] remainingKeyTargets = null;
        if (remainingKeyCount > 0) {
            JavaConstant[] remainingKeyConstants = new JavaConstant[remainingKeyCount];
            double[] remainingKeyProbabilities = new double[remainingKeyCount];
            remainingKeyTargets = new LabelRef[remainingKeyCount];
            int i = 0;
            for (int j = 0; j < loIdx; j++) {
                remainingKeyConstants[i] = keyConstants[j];
                remainingKeyProbabilities[i] = keyProbs[j];
                remainingKeyTargets[i] = keyTargets[j];
                i++;
            }
            for (int j = hiIdx + 1; j < keyConstants.length; j++) {
                remainingKeyConstants[i] = keyConstants[j];
                remainingKeyProbabilities[i] = keyProbs[j];
                remainingKeyTargets[i] = keyTargets[j];
                i++;
            }
            remainingStrategy = SwitchStrategy.getBestStrategy(remainingKeyProbabilities, remainingKeyConstants, remainingKeyTargets);
        }

        emitRangeTableSwitch(minValue, defaultTarget, targets, remainingStrategy, remainingKeyTargets, value);
    }

    private void emitHashedJumpTableHelper(JavaConstant[] keyConstants, LabelRef[] keyTargets, LabelRef defaultTarget, AllocatableValue value, IntHasher h) {
        LabelRef[] targets = new LabelRef[h.cardinality];
        JavaConstant[] keys = new JavaConstant[h.cardinality];
        for (int i = 0; i < h.cardinality; i++) {
            keys[i] = JavaConstant.INT_0;
            targets[i] = defaultTarget;
        }
        for (int i = 0; i < keyConstants.length; i++) {
            int idx = h.hash(keyConstants[i].asInt());
            keys[idx] = keyConstants[i];
            targets[idx] = keyTargets[i];
        }
        emitHashTableSwitch(h, keys, defaultTarget, targets, value);
    }

    /**
     * If the size of the jump table is smaller than this value then we emit one regardless the
     * table density.
     */
    private static final int MAX_DIRECT_SIZE = 128;

    /**
     * Each label is 4 bytes in the direct jump table, so if the table is too sparse we may waste a
     * lot of space. As a result, we emit a table if it is not too large, OR if it is dense enough.
     */
    private static boolean directJumpForSubrange(JavaConstant[] keyConstants, double minDensity, int minValue, int loIdx, int hiIdx) {
        // Subtraction of 2 sorted signed values will not overflow the unsigned range
        // Must not add 1 here because we can overflow -1 -> 0
        int valueRangeMinus1 = keyConstants[hiIdx].asInt() - minValue;
        if (Integer.compareUnsigned(valueRangeMinus1, Integer.MAX_VALUE) >= 0) {
            // Too large range, reject to avoid weird edge cases
            return false;
        }
        double density = (double) (hiIdx - loIdx + 1) / (Integer.toUnsignedLong(valueRangeMinus1) + 1);
        return Integer.compareUnsigned(valueRangeMinus1, MAX_DIRECT_SIZE) < 0 || density >= minDensity;
    }

    public abstract void emitStrategySwitch(SwitchStrategy strategy, AllocatableValue key, LabelRef[] keyTargets, LabelRef defaultTarget);

    /**
     * Emit a direct jump table with {@code targets.length} consecutive keys starting at {@code
     * lowKey}. If {@code remainingStrategy == null}, all other values of {@code key} will result in
     * the control flow being transferred to {@code defaultTarget}. Otherwise, {@code
     * remainingStrategy} will decide the jump destination among {@code remainingTargets} and {@code
     * defaultTarget} when the value of {@code key} is not in the jump table.
     */
    protected abstract void emitRangeTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, SwitchStrategy remainingStrategy, LabelRef[] remainingTargets, AllocatableValue key);

    protected abstract void emitHashTableSwitch(JavaConstant[] keys, LabelRef defaultTarget, LabelRef[] targets, AllocatableValue value, Value hash);

    private static Optional<IntHasher> hasherFor(JavaConstant[] keyConstants) {
        int[] keys = new int[keyConstants.length];
        for (int i = 0; i < keyConstants.length; i++) {
            keys[i] = keyConstants[i].asInt();
        }
        return IntHasher.forKeys(keys);
    }

    private void emitHashTableSwitch(IntHasher hasher, JavaConstant[] keys, LabelRef defaultTarget, LabelRef[] targets, AllocatableValue value) {
        Value hash = value;
        if (hasher.factor > 1) {
            Value factor = emitJavaConstant(JavaConstant.forShort(hasher.factor));
            hash = arithmeticLIRGen.emitMul(hash, factor, false);
        }
        if (hasher.shift > 0) {
            Value shift = emitJavaConstant(JavaConstant.forByte(hasher.shift));
            hash = arithmeticLIRGen.emitShr(hash, shift);
        }
        Value cardinalityAnd = emitJavaConstant(JavaConstant.forInt(hasher.cardinality - 1));
        hash = arithmeticLIRGen.emitAnd(hash, cardinalityAnd);
        emitHashTableSwitch(keys, defaultTarget, targets, value, hash);
    }

    /**
     * Called just before register allocation is performed on the LIR owned by this generator.
     * Overriding implementations of this method must call the overridden method.
     */
    public void beforeRegisterAllocation() {
    }

    /**
     * Gets a garbage value for a given kind.
     */
    protected abstract JavaConstant zapValueForKind(PlatformKind kind);

    @Override
    public LIRKind getLIRKind(Stamp stamp) {
        return stamp.getLIRKind(lirKindTool);
    }

    @Override
    public BasicBlock<?> getCurrentBlock() {
        return currentBlock;
    }

    @Override
    public LIRGenerationResult getResult() {
        return res;
    }

    @Override
    public void emitBlackhole(Value operand) {
        append(new StandardOp.BlackholeOp(operand));
    }

    @Override
    public LIRInstruction createBenchmarkCounter(String name, String group, Value increment) {
        throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public LIRInstruction createMultiBenchmarkCounter(String[] names, String[] groups, Value[] increments) {
        throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public abstract LIRInstruction createZapRegisters(Register[] zappedRegisters, JavaConstant[] zapValues);

    @Override
    public LIRInstruction createZapRegisters() {
        Register[] zappedRegisters = getResult().getFrameMap().getRegisterConfig().getAllocatableRegisters().toArray(Register[]::new);
        return createZapRegisters(zappedRegisters);
    }

    @Override
    public LIRInstruction createZapRegisters(Register[] zappedRegisters) {
        JavaConstant[] zapValues = new JavaConstant[zappedRegisters.length];
        for (int i = 0; i < zappedRegisters.length; i++) {
            PlatformKind kind = target().arch.getLargestStorableKind(zappedRegisters[i].getRegisterCategory());
            zapValues[i] = zapValueForKind(kind);
        }
        return createZapRegisters(zappedRegisters, zapValues);
    }

    @Override
    public abstract LIRInstruction createZapArgumentSpace(StackSlot[] zappedStack, JavaConstant[] zapValues);

    @Override
    public LIRInstruction zapArgumentSpace() {
        List<StackSlot> slots = null;
        CallingConvention cc = res.getCallingConvention();
        for (AllocatableValue arg : cc.getArguments()) {
            if (isStackSlot(arg)) {
                if (slots == null) {
                    slots = new ArrayList<>();
                }
                slots.add((StackSlot) arg);
            } else {
                assert !LIRValueUtil.isVirtualStackSlot(arg);
            }
        }
        if (slots != null && isStackSlot(cc.getReturn())) {
            // Some calling conventions pass their return value through the stack so make sure not
            // to kill the return value.
            slots.remove(asStackSlot(cc.getReturn()));
        }
        if (slots == null || slots.size() == 0) {
            return null;
        }
        StackSlot[] zappedStack = slots.toArray(new StackSlot[slots.size()]);
        JavaConstant[] zapValues = new JavaConstant[zappedStack.length];
        for (int i = 0; i < zappedStack.length; i++) {
            PlatformKind kind = zappedStack[i].getPlatformKind();
            zapValues[i] = zapValueForKind(kind);
        }
        return createZapArgumentSpace(zappedStack, zapValues);
    }

    /**
     * Returns the offset of the array length word in an array object's header.
     */
    public abstract int getArrayLengthOffset();

    /**
     * Returns the offset of the first array element.
     */
    public int getArrayBaseOffset(JavaKind elementKind) {
        return getMetaAccess().getArrayBaseOffset(elementKind);
    }
}
