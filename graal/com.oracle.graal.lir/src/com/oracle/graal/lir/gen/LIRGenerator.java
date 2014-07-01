/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.options.*;

/**
 * This class traverses the HIR instructions and generates LIR instructions from them.
 */
public abstract class LIRGenerator implements LIRGeneratorTool, LIRKindTool {

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

    private LIRGenerationResult res;

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

    @Override
    public Variable newVariable(LIRKind lirKind) {
        return new Variable(lirKind, res.getLIR().nextVariable());
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
    public AllocatableValue resultOperandFor(LIRKind kind) {
        return res.getFrameMap().registerConfig.getReturnRegister((Kind) kind.getPlatformKind()).asValue(kind);
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
            LIRKind stackKind = value.getLIRKind().changeType(value.getKind().getStackKind());
            if (isRegister(value)) {
                return asRegister(value).asValue(stackKind);
            } else if (isStackSlot(value)) {
                return StackSlot.get(stackKind, asStackSlot(value).getRawOffset(), asStackSlot(value).getRawAddFrameSize());
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
    public LIRKind getLIRKind(Stamp stamp) {
        return stamp.getLIRKind(this);
    }

    public LIRKind getIntegerKind(int bits) {
        if (bits <= 8) {
            return LIRKind.value(Kind.Byte);
        } else if (bits <= 16) {
            return LIRKind.value(Kind.Short);
        } else if (bits <= 32) {
            return LIRKind.value(Kind.Int);
        } else {
            assert bits <= 64;
            return LIRKind.value(Kind.Long);
        }
    }

    public LIRKind getFloatingKind(int bits) {
        switch (bits) {
            case 32:
                return LIRKind.value(Kind.Float);
            case 64:
                return LIRKind.value(Kind.Double);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    public LIRKind getObjectKind() {
        return LIRKind.reference(Kind.Object);
    }

    protected LIRKind getAddressKind(Value base, long displacement, Value index) {
        if (base.getLIRKind().isValue() && (index.equals(Value.ILLEGAL) || index.getLIRKind().isValue())) {
            return LIRKind.value(target().wordKind);
        } else if (base.getLIRKind().isReference(0) && displacement == 0L && index.equals(Value.ILLEGAL)) {
            return LIRKind.reference(target().wordKind);
        } else {
            return LIRKind.derivedReference(target().wordKind);
        }
    }

    public AbstractBlock<?> getCurrentBlock() {
        return currentBlock;
    }

    void setCurrentBlock(AbstractBlock<?> block) {
        currentBlock = block;
    }

    public LIRGenerationResult getResult() {
        return res;
    }
}
