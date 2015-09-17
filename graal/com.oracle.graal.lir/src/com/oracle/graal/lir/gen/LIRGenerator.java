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
package com.oracle.graal.lir.gen;

import static com.oracle.graal.lir.LIRValueUtil.asConstant;
import static com.oracle.graal.lir.LIRValueUtil.asJavaConstant;
import static com.oracle.graal.lir.LIRValueUtil.isConstantValue;
import static com.oracle.graal.lir.LIRValueUtil.isJavaConstant;
import static com.oracle.graal.lir.LIRValueUtil.isVariable;
import static jdk.internal.jvmci.code.ValueUtil.asAllocatableValue;
import static jdk.internal.jvmci.code.ValueUtil.isAllocatableValue;
import static jdk.internal.jvmci.code.ValueUtil.isLegal;

import java.util.ArrayList;
import java.util.List;

import jdk.internal.jvmci.code.CallingConvention;
import jdk.internal.jvmci.code.CodeCacheProvider;
import jdk.internal.jvmci.code.Register;
import jdk.internal.jvmci.code.RegisterAttributes;
import jdk.internal.jvmci.code.TargetDescription;
import jdk.internal.jvmci.common.JVMCIError;
import jdk.internal.jvmci.meta.AllocatableValue;
import jdk.internal.jvmci.meta.Constant;
import jdk.internal.jvmci.meta.JavaConstant;
import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.LIRKind;
import jdk.internal.jvmci.meta.MetaAccessProvider;
import jdk.internal.jvmci.meta.PlatformKind;
import jdk.internal.jvmci.meta.Value;
import jdk.internal.jvmci.options.Option;
import jdk.internal.jvmci.options.OptionType;
import jdk.internal.jvmci.options.OptionValue;

import com.oracle.graal.asm.Label;
import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.compiler.common.spi.CodeGenProviders;
import com.oracle.graal.compiler.common.spi.ForeignCallLinkage;
import com.oracle.graal.compiler.common.spi.ForeignCallsProvider;
import com.oracle.graal.compiler.common.spi.LIRKindTool;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.debug.TTY;
import com.oracle.graal.lir.ConstantValue;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LIRVerifier;
import com.oracle.graal.lir.LabelRef;
import com.oracle.graal.lir.StandardOp;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.SwitchStrategy;
import com.oracle.graal.lir.Variable;

/**
 * This class traverses the HIR instructions and generates LIR instructions from them.
 */
public abstract class LIRGenerator implements LIRGeneratorTool {

    public static class Options {
        // @formatter:off
        @Option(help = "Print HIR along side LIR as the latter is generated", type = OptionType.Debug)
        public static final OptionValue<Boolean> PrintIRWithLIR = new OptionValue<>(false);
        @Option(help = "The trace level for the LIR generator", type = OptionType.Debug)
        public static final OptionValue<Integer> TraceLIRGeneratorLevel = new OptionValue<>(0);
        // @formatter:on
    }

    private final LIRKindTool lirKindTool;

    private final CodeGenProviders providers;
    private final CallingConvention cc;

    private AbstractBlockBase<?> currentBlock;

    private LIRGenerationResult res;

    public LIRGenerator(LIRKindTool lirKindTool, CodeGenProviders providers, CallingConvention cc, LIRGenerationResult res) {
        this.lirKindTool = lirKindTool;
        this.res = res;
        this.providers = providers;
        this.cc = cc;
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

    protected LIRKindTool getLIRKindTool() {
        return lirKindTool;
    }

    @Override
    public Variable newVariable(LIRKind lirKind) {
        return new Variable(lirKind, res.getLIR().nextVariable());
    }

    @Override
    public RegisterAttributes attributes(Register register) {
        return res.getFrameMapBuilder().getRegisterConfig().getAttributesMap()[register.number];
    }

    @Override
    public Variable emitMove(Value input) {
        assert !(input instanceof Variable) : "Creating a copy of a variable via this method is not supported (and potentially a bug): " + input;
        Variable result = newVariable(input.getLIRKind());
        emitMove(result, input);
        return result;
    }

    @Override
    public Value emitConstant(LIRKind kind, Constant constant) {
        if (constant instanceof JavaConstant && canInlineConstant((JavaConstant) constant)) {
            return new ConstantValue(toRegisterKind(kind), constant);
        } else {
            return emitLoadConstant(kind, constant);
        }
    }

    @Override
    public Value emitJavaConstant(JavaConstant constant) {
        return emitConstant(target().getLIRKind(constant.getJavaKind()), constant);
    }

    @Override
    public AllocatableValue emitLoadConstant(LIRKind kind, Constant constant) {
        Variable result = newVariable(kind);
        emitMoveConstant(result, constant);
        return result;
    }

    public AllocatableValue asAllocatable(Value value) {
        if (isAllocatableValue(value)) {
            return asAllocatableValue(value);
        } else if (isConstantValue(value)) {
            return emitLoadConstant(value.getLIRKind(), asConstant(value));
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

    /**
     * Checks whether the supplied constant can be used without loading it into a register for most
     * operations, i.e., for commonly used arithmetic, logical, and comparison operations.
     *
     * @param c The constant to check.
     * @return True if the constant can be used directly, false if the constant needs to be in a
     *         register.
     */
    protected abstract boolean canInlineConstant(JavaConstant c);

    public Value loadNonConst(Value value) {
        if (isJavaConstant(value) && !canInlineConstant(asJavaConstant(value))) {
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
     * @param javaKind the kind of value being returned
     * @param lirKind the backend type of the value being returned
     * @return the operand representing the ABI defined location used return a value of kind
     *         {@code kind}
     */
    public AllocatableValue resultOperandFor(JavaKind javaKind, LIRKind lirKind) {
        Register reg = res.getFrameMapBuilder().getRegisterConfig().getReturnRegister(javaKind);
        assert target().arch.canStoreValue(reg.getRegisterCategory(), lirKind.getPlatformKind()) : reg.getRegisterCategory() + " " + lirKind.getPlatformKind();
        return reg.asValue(lirKind);
    }

    public <I extends LIRInstruction> I append(I op) {
        if (Options.PrintIRWithLIR.getValue() && !TTY.isSuppressed()) {
            TTY.println(op.toStringWithIdPrefix());
            TTY.println();
        }
        assert LIRVerifier.verify(op);
        res.getLIR().getLIRforBlock(getCurrentBlock()).add(op);
        return op;
    }

    public boolean hasBlockEnd(AbstractBlockBase<?> block) {
        List<LIRInstruction> ops = getResult().getLIR().getLIRforBlock(block);
        if (ops.size() == 0) {
            return false;
        }
        return ops.get(ops.size() - 1) instanceof BlockEndOp;
    }

    private final class BlockScopeImpl extends BlockScope {

        private BlockScopeImpl(AbstractBlockBase<?> block) {
            currentBlock = block;
        }

        private void doBlockStart() {
            if (Options.PrintIRWithLIR.getValue()) {
                TTY.print(currentBlock.toString());
            }

            // set up the list of LIR instructions
            assert res.getLIR().getLIRforBlock(currentBlock) == null : "LIR list already computed for this block";
            res.getLIR().setLIRforBlock(currentBlock, new ArrayList<LIRInstruction>());

            append(new LabelOp(new Label(currentBlock.getId()), currentBlock.isAligned()));

            if (Options.TraceLIRGeneratorLevel.getValue() >= 1) {
                TTY.println("BEGIN Generating LIR for block B" + currentBlock.getId());
            }
        }

        private void doBlockEnd() {
            if (Options.TraceLIRGeneratorLevel.getValue() >= 1) {
                TTY.println("END Generating LIR for block B" + currentBlock.getId());
            }

            if (Options.PrintIRWithLIR.getValue()) {
                TTY.println();
            }
            currentBlock = null;
        }

        @Override
        public AbstractBlockBase<?> getCurrentBlock() {
            return currentBlock;
        }

        @Override
        public void close() {
            doBlockEnd();
        }

    }

    public final BlockScope getBlockScope(AbstractBlockBase<?> block) {
        BlockScopeImpl blockScope = new BlockScopeImpl(block);
        blockScope.doBlockStart();
        return blockScope;
    }

    public void emitIncomingValues(Value[] params) {
        ((LabelOp) res.getLIR().getLIRforBlock(getCurrentBlock()).get(0)).setIncomingValues(params);
    }

    public abstract void emitJump(LabelRef label);

    public abstract void emitCompareBranch(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination,
                    double trueDestinationProbability);

    public abstract void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, LIRKind cmpKind, double overflowProbability);

    public abstract void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability);

    public abstract Variable emitConditionalMove(PlatformKind cmpKind, Value leftVal, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue);

    public abstract Variable emitIntegerTestMove(Value leftVal, Value right, Value trueValue, Value falseValue);

    /**
     * Emits the single call operation at the heart of generating LIR for a
     * {@linkplain #emitForeignCall(ForeignCallLinkage, LIRFrameState, Value...) foreign call}.
     */
    protected abstract void emitForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info);

    @Override
    public Variable emitForeignCall(ForeignCallLinkage linkage, LIRFrameState frameState, Value... args) {
        LIRFrameState state = null;
        if (linkage.needsDebugInfo()) {
            if (frameState != null) {
                state = frameState;
            } else {
                assert needOnlyOopMaps();
                state = new LIRFrameState(null, null, null);
            }
        }

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
        emitForeignCallOp(linkage, linkageCc.getReturn(), argLocations, linkage.getTemporaries(), state);

        if (isLegal(linkageCc.getReturn())) {
            return emitMove(linkageCc.getReturn());
        } else {
            return null;
        }
    }

    public void emitStrategySwitch(JavaConstant[] keyConstants, double[] keyProbabilities, LabelRef[] keyTargets, LabelRef defaultTarget, Variable value) {
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
    protected JavaConstant zapValueForKind(PlatformKind kind) {
        long dead = 0xDEADDEADDEADDEADL;
        switch ((JavaKind) kind) {
            case Boolean:
                return JavaConstant.FALSE;
            case Byte:
                return JavaConstant.forByte((byte) dead);
            case Char:
                return JavaConstant.forChar((char) dead);
            case Short:
                return JavaConstant.forShort((short) dead);
            case Int:
                return JavaConstant.forInt((int) dead);
            case Double:
                return JavaConstant.forDouble(Double.longBitsToDouble(dead));
            case Float:
                return JavaConstant.forFloat(Float.intBitsToFloat((int) dead));
            case Long:
                return JavaConstant.forLong(dead);
            case Object:
                return JavaConstant.NULL_POINTER;
            default:
                throw new IllegalArgumentException(kind.toString());
        }
    }

    public LIRKind getLIRKind(Stamp stamp) {
        return stamp.getLIRKind(lirKindTool);
    }

    protected LIRKind getAddressKind(Value base, long displacement, Value index) {
        if (base.getLIRKind().isValue() && (index.equals(Value.ILLEGAL) || index.getLIRKind().isValue())) {
            return LIRKind.value(target().arch.getWordKind());
        } else if (base.getLIRKind().isReference(0) && displacement == 0L && index.equals(Value.ILLEGAL)) {
            return LIRKind.reference(target().arch.getWordKind());
        } else {
            return LIRKind.unknownReference(target().arch.getWordKind());
        }
    }

    public LIRKind toRegisterKind(LIRKind kind) {
        JavaKind stackKind = ((JavaKind) kind.getPlatformKind()).getStackKind();
        if (stackKind != kind.getPlatformKind()) {
            return kind.changeType(stackKind);
        } else {
            return kind;
        }
    }

    public AbstractBlockBase<?> getCurrentBlock() {
        return currentBlock;
    }

    public LIRGenerationResult getResult() {
        return res;
    }

    public void emitBlackhole(Value operand) {
        append(new StandardOp.BlackholeOp(operand));
    }

    public LIRInstruction createBenchmarkCounter(String name, String group, Value increment) {
        throw JVMCIError.unimplemented();
    }

    public LIRInstruction createMultiBenchmarkCounter(String[] names, String[] groups, Value[] increments) {
        throw JVMCIError.unimplemented();
    }

    // automatic derived reference handling

    protected boolean isNumericInteger(PlatformKind kind) {
        return ((JavaKind) kind).isNumericInteger();
    }

    protected abstract Variable emitAdd(LIRKind resultKind, Value a, Value b, boolean setFlags);

    public final Variable emitAdd(Value aVal, Value bVal, boolean setFlags) {
        LIRKind resultKind;
        Value a = aVal;
        Value b = bVal;

        if (isNumericInteger(a.getPlatformKind())) {
            LIRKind aKind = a.getLIRKind();
            LIRKind bKind = b.getLIRKind();
            assert a.getPlatformKind() == b.getPlatformKind();

            if (aKind.isUnknownReference()) {
                resultKind = aKind;
            } else if (bKind.isUnknownReference()) {
                resultKind = bKind;
            } else if (aKind.isValue() && bKind.isValue()) {
                resultKind = aKind;
            } else if (aKind.isValue()) {
                if (bKind.isDerivedReference()) {
                    resultKind = bKind;
                } else {
                    AllocatableValue allocatable = asAllocatable(b);
                    resultKind = bKind.makeDerivedReference(allocatable);
                    b = allocatable;
                }
            } else if (bKind.isValue()) {
                if (aKind.isDerivedReference()) {
                    resultKind = aKind;
                } else {
                    AllocatableValue allocatable = asAllocatable(a);
                    resultKind = aKind.makeDerivedReference(allocatable);
                    a = allocatable;
                }
            } else {
                resultKind = aKind.makeUnknownReference();
            }
        } else {
            resultKind = LIRKind.combine(a, b);
        }

        return emitAdd(resultKind, a, b, setFlags);
    }

    protected abstract Variable emitSub(LIRKind resultKind, Value a, Value b, boolean setFlags);

    public final Variable emitSub(Value aVal, Value bVal, boolean setFlags) {
        LIRKind resultKind;
        Value a = aVal;
        Value b = bVal;

        if (isNumericInteger(a.getPlatformKind())) {
            LIRKind aKind = a.getLIRKind();
            LIRKind bKind = b.getLIRKind();
            assert a.getPlatformKind() == b.getPlatformKind();

            if (aKind.isUnknownReference()) {
                resultKind = aKind;
            } else if (bKind.isUnknownReference()) {
                resultKind = bKind;
            }

            if (aKind.isValue() && bKind.isValue()) {
                resultKind = aKind;
            } else if (bKind.isValue()) {
                if (aKind.isDerivedReference()) {
                    resultKind = aKind;
                } else {
                    AllocatableValue allocatable = asAllocatable(a);
                    resultKind = aKind.makeDerivedReference(allocatable);
                    a = allocatable;
                }
            } else if (aKind.isDerivedReference() && bKind.isDerivedReference() && aKind.getDerivedReferenceBase().equals(bKind.getDerivedReferenceBase())) {
                resultKind = LIRKind.value(a.getPlatformKind());
            } else {
                resultKind = aKind.makeUnknownReference();
            }
        } else {
            resultKind = LIRKind.combine(a, b);
        }

        return emitSub(resultKind, a, b, setFlags);
    }
}
