/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.llvm;

import static org.graalvm.compiler.core.llvm.LLVMIRBuilder.isObject;
import static org.graalvm.compiler.core.llvm.LLVMIRBuilder.isVoidType;
import static org.graalvm.compiler.core.llvm.LLVMIRBuilder.typeOf;
import static org.graalvm.compiler.core.llvm.LLVMUtils.dumpTypes;
import static org.graalvm.compiler.core.llvm.LLVMUtils.dumpValues;
import static org.graalvm.compiler.core.llvm.LLVMUtils.getType;
import static org.graalvm.compiler.core.llvm.LLVMUtils.getVal;
import static org.graalvm.compiler.debug.GraalError.shouldNotReachHere;
import static org.graalvm.compiler.debug.GraalError.unimplemented;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.bytedeco.javacpp.LLVM.LLVMBasicBlockRef;
import org.bytedeco.javacpp.LLVM.LLVMTypeRef;
import org.bytedeco.javacpp.LLVM.LLVMValueRef;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.spi.CodeGenProviders;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.core.common.type.RawPointerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.llvm.LLVMUtils.DebugLevel;
import org.graalvm.compiler.core.llvm.LLVMUtils.LLVMConstant;
import org.graalvm.compiler.core.llvm.LLVMUtils.LLVMKind;
import org.graalvm.compiler.core.llvm.LLVMUtils.LLVMStackSlot;
import org.graalvm.compiler.core.llvm.LLVMUtils.LLVMValueWrapper;
import org.graalvm.compiler.core.llvm.LLVMUtils.LLVMVariable;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.SwitchStrategy;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.aarch64.AArch64ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterAttributes;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public abstract class LLVMGenerator implements LIRGeneratorTool {
    private final ArithmeticLLVMGenerator arithmetic;
    protected final LLVMIRBuilder builder;
    private final LIRKindTool lirKindTool;
    private final Providers providers;
    private final LLVMGenerationResult generationResult;
    private final boolean returnsEnum;
    private final int debugLevel;
    private LLVMValueRef indentCounter;
    private LLVMValueRef spacesVector;

    private Block currentBlock;
    private Map<AbstractBeginNode, LLVMBasicBlockRef> basicBlockMap = new HashMap<>();
    Map<Block, LLVMBasicBlockRef> splitBlockEndMap = new HashMap<>();

    public LLVMGenerator(Providers providers, LLVMGenerationResult generationResult, ResolvedJavaMethod method, LLVMIRBuilder builder, LIRKindTool lirKindTool, int debugLevel) {
        this.providers = providers;
        this.generationResult = generationResult;
        this.returnsEnum = method.getSignature().getReturnType(null).resolve(null).isEnum();

        /* this.explodeSelects = selectBlacklist.contains(builder.getFunctionName()); */

        this.builder = builder;
        this.arithmetic = new ArithmeticLLVMGenerator(builder);
        this.lirKindTool = lirKindTool;

        this.debugLevel = debugLevel;

        if (debugLevel >= DebugLevel.FUNCTION) {
            this.indentCounter = builder.getUniqueGlobal("__svm_indent_counter", builder.intType(), true);
            this.spacesVector = builder.getUniqueGlobal("__svm_spaces_vector", builder.vectorType(builder.rawPointerType(), 100), false);
            StringBuilder strBuilder = new StringBuilder();
            LLVMValueRef[] strings = new LLVMValueRef[100];
            for (int i = 0; i < 100; ++i) {
                strings[i] = builder.getUniqueGlobal("__svm_" + i + "_spaces", builder.arrayType(builder.byteType(), strBuilder.length() + 1), false);
                builder.setInitializer(strings[i], builder.constantString(strBuilder.toString()));
                strings[i] = builder.buildBitcast(strings[i], builder.rawPointerType());
                strBuilder.append(' ');
            }
            builder.setInitializer(spacesVector, builder.constantVector(strings));
        }
    }

    public LLVMIRBuilder getBuilder() {
        return builder;
    }

    void appendBasicBlock(Block block) {
        LLVMBasicBlockRef basicBlock = builder.appendBasicBlock(block.toString());
        basicBlockMap.put(block.getBeginNode(), basicBlock);
    }

    LLVMBasicBlockRef getBlock(Block block) {
        return getBlock(block.getBeginNode());
    }

    LLVMBasicBlockRef getBlock(AbstractBeginNode begin) {
        return basicBlockMap.get(begin);
    }

    public LLVMBasicBlockRef getBlockEnd(Block block) {
        return (splitBlockEndMap.containsKey(block)) ? splitBlockEndMap.get(block) : getBlock(block);
    }

    void beginBlock(Block block) {
        currentBlock = block;
        builder.positionAtEnd(getBlock(block));
    }

    LLVMTypeRef getLLVMType(Stamp stamp) {
        if (stamp instanceof RawPointerStamp) {
            return builder.rawPointerType();
        }
        return builder.getLLVMType(getTypeKind(stamp.javaType(getMetaAccess())));
    }

    protected JavaKind getTypeKind(@SuppressWarnings("unused") ResolvedJavaType type) {
        throw unimplemented();
    }

    public LLVMValueRef getFunction(ResolvedJavaMethod method) {
        LLVMTypeRef functionType = getLLVMFunctionType(method);
        return builder.getFunction(getFunctionName(method), functionType);
    }

    public abstract void allocateRegisterSlots();

    public String getFunctionName(ResolvedJavaMethod method) {
        return method.getName();
    }

    LLVMTypeRef getLLVMFunctionReturnType(ResolvedJavaMethod method) {
        ResolvedJavaType returnType = method.getSignature().getReturnType(null).resolve(null);
        return builder.getLLVMStackType(getTypeKind(returnType));
    }

    protected LLVMTypeRef[] getLLVMFunctionArgTypes(ResolvedJavaMethod method) {
        ResolvedJavaType receiver = method.hasReceiver() ? method.getDeclaringClass() : null;
        JavaType[] parameterTypes = method.getSignature().toParameterTypes(receiver);
        return Arrays.stream(parameterTypes).map(type -> builder.getLLVMStackType(getTypeKind(type.resolve(null)))).toArray(LLVMTypeRef[]::new);
    }

    public LLVMTypeRef getLLVMFunctionType(ResolvedJavaMethod method) {
        return builder.functionType(getLLVMFunctionReturnType(method), getLLVMFunctionArgTypes(method));
    }

    private long nextConstantId = 0L;

    private LLVMValueRef getLLVMPlaceholderForConstant(Constant constant) {
        String symbolName = generationResult.getSymbolNameForConstant(constant);
        if (symbolName == null) {
            symbolName = "constant_" + builder.getFunctionName() + "#" + nextConstantId++;
            generationResult.recordConstant(constant, symbolName);
        }
        return builder.getExternalObject(symbolName);
    }

    protected void emitPrintf(String base) {
        emitPrintf(base, new JavaKind[0], new LLVMValueRef[0]);
    }

    protected void emitPrintf(String base, JavaKind[] types, LLVMValueRef[] values) {
        LLVMValueRef printf = builder.getFunction("printf", builder.functionType(builder.intType(), true, builder.rawPointerType()));

        if (debugLevel >= DebugLevel.FUNCTION) {
            LLVMValueRef count = builder.buildLoad(indentCounter);
            LLVMValueRef vector = builder.buildLoad(spacesVector);
            LLVMValueRef spaces = builder.buildExtractElement(vector, count);
            builder.buildCall(printf, spaces);
        }

        StringBuilder introString = new StringBuilder(base);
        List<LLVMValueRef> printfArgs = new ArrayList<>();

        assert types.length == values.length;

        for (int i = 0; i < types.length; ++i) {
            switch (types[i]) {
                case Boolean:
                case Byte:
                    introString.append(" %hhd ");
                    break;
                case Short:
                    introString.append(" %hd ");
                    break;
                case Char:
                    introString.append(" %c ");
                    break;
                case Int:
                    introString.append(" %ld ");
                    break;
                case Float:
                case Double:
                    introString.append(" %f ");
                    break;
                case Long:
                    introString.append(" %lld ");
                    break;
                case Object:
                    introString.append(" %p ");
                    break;
                case Void:
                case Illegal:
                default:
                    throw shouldNotReachHere();
            }

            printfArgs.add(values[i]);
        }
        introString.append("\n");

        printfArgs.add(0, builder.buildGlobalStringPtr(introString.toString()));
        builder.buildCall(printf, printfArgs.toArray(new LLVMValueRef[0]));
    }

    @Override
    public ArithmeticLIRGeneratorTool getArithmetic() {
        return arithmetic;
    }

    @Override
    public CodeGenProviders getProviders() {
        return providers;
    }

    @Override
    public TargetDescription target() {
        return getCodeCache().getTarget();
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
    public AbstractBlockBase<?> getCurrentBlock() {
        return currentBlock;
    }

    @Override
    public LIRGenerationResult getResult() {
        throw unimplemented();
    }

    @Override
    public RegisterConfig getRegisterConfig() {
        return getCodeCache().getRegisterConfig();
    }

    @Override
    public boolean hasBlockEnd(AbstractBlockBase<?> block) {
        throw unimplemented();
    }

    @Override
    public MoveFactory getMoveFactory() {
        throw unimplemented();
    }

    @Override
    public MoveFactory getSpillMoveFactory() {
        return null;
    }

    @Override
    public BlockScope getBlockScope(AbstractBlockBase<?> block) {
        throw unimplemented();
    }

    @Override
    public boolean canInlineConstant(Constant constant) {
        return false;
    }

    @Override
    public boolean mayEmbedConstantLoad(Constant constant) {
        return false;
    }

    @Override
    public Value emitConstant(LIRKind kind, Constant constant) {
        LLVMValueRef value = emitLLVMConstant(((LLVMKind) kind.getPlatformKind()).get(), (JavaConstant) constant);
        return new LLVMConstant(value, constant);
    }

    @Override
    public Value emitJavaConstant(JavaConstant constant) {
        LLVMValueRef value = emitLLVMConstant(builder.getLLVMType(constant.getJavaKind()), constant);
        return new LLVMConstant(value, constant);
    }

    LLVMValueRef emitLLVMConstant(LLVMTypeRef type, JavaConstant constant) {
        LLVMValueRef value;
        if (LLVMIRBuilder.isIntegerType(type)) {
            switch (LLVMIRBuilder.integerTypeWidth(type)) {
                case 1:
                    value = builder.constantBoolean(constant.asBoolean());
                    break;
                case 8:
                    value = builder.constantByte((byte) constant.asInt());
                    break;
                case 16:
                    value = builder.constantShort((short) constant.asInt());
                    break;
                case 32:
                    value = builder.constantInt(constant.asInt());
                    break;
                case 64:
                    value = builder.constantLong(constant.asLong());
                    break;
                default:
                    throw shouldNotReachHere("invalid integer width " + LLVMIRBuilder.integerTypeWidth(type));
            }
        } else if (LLVMIRBuilder.isFloatType(type)) {
            value = builder.constantFloat(constant.asFloat());
        } else if (LLVMIRBuilder.isDoubleType(type)) {
            value = builder.constantDouble(constant.asDouble());
        } else if (LLVMIRBuilder.isObject(type)) {
            if (constant.isNull()) {
                value = builder.constantNull(builder.objectType());
            } else {
                value = builder.buildLoad(getLLVMPlaceholderForConstant(constant), builder.objectType());
            }
        } else {
            throw shouldNotReachHere(dumpTypes("unsupported constant type", type));
        }

        return value;
    }

    @Override
    public <K extends ValueKind<K>> K toRegisterKind(K kind) {
        /* Registers are handled by LLVM. */
        throw unimplemented();
    }

    @Override
    public AllocatableValue emitLoadConstant(ValueKind<?> kind, Constant constant) {
        LLVMValueRef value = builder.buildLoad(getLLVMPlaceholderForConstant(constant), ((LLVMKind) kind.getPlatformKind()).get());
        return new LLVMVariable(value);
    }

    @Override
    public void emitNullCheck(Value address, LIRFrameState state) {
        throw unimplemented();
    }

    @Override
    public Value emitAtomicReadAndWrite(Value address, ValueKind<?> valueKind, Value newValue) {
        LLVMValueRef atomicRMW = builder.buildAtomicXchg(getVal(address), getVal(newValue));
        return new LLVMVariable(atomicRMW);
    }

    @Override
    public Value emitAtomicReadAndAdd(Value address, ValueKind<?> valueKind, Value delta) {
        LLVMValueRef atomicRMW = builder.buildAtomicAdd(getVal(address), getVal(delta));
        return new LLVMVariable(atomicRMW);
    }

    @Override
    public Variable emitLogicCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue) {
        LLVMValueRef success = builder.buildLogicCmpxchg(getVal(address), getVal(expectedValue), getVal(newValue));
        LLVMValueRef result = builder.buildSelect(success, getVal(trueValue), getVal(falseValue));
        return new LLVMVariable(result);
    }

    @Override
    public Value emitValueCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue) {
        LLVMValueRef result = builder.buildValueCmpxchg(getVal(address), getVal(expectedValue), getVal(newValue));
        return new LLVMVariable(result);
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value failedSpeculation, LIRFrameState state) {
        throw unimplemented();
    }

    @Override
    public Variable emitForeignCall(ForeignCallLinkage linkage, LIRFrameState state, Value... arguments) {
        ResolvedJavaMethod targetMethod = findForeignCallTarget(linkage.getDescriptor());

        state.initDebugInfo(null, false);
        long patchpointId = LLVMIRBuilder.nextPatchpointId.getAndIncrement();
        generationResult.recordDirectCall(targetMethod, patchpointId, state.debugInfo());

        LLVMValueRef callee = getFunction(targetMethod);
        LLVMValueRef[] args = Arrays.stream(arguments).map(LLVMUtils::getVal).toArray(LLVMValueRef[]::new);
        LLVMValueRef[] callArguments = getCallArguments(args, getCallingConventionType(linkage.getOutgoingCallingConvention()), targetMethod);

        LLVMValueRef call = builder.buildCall(callee, patchpointId, callArguments);
        return (isVoidType(getLLVMFunctionReturnType(targetMethod))) ? null : new LLVMVariable(call);
    }

    protected abstract CallingConvention.Type getCallingConventionType(CallingConvention callingConvention);

    public abstract LLVMValueRef[] getCallArguments(LLVMValueRef[] args, CallingConvention.Type callType, ResolvedJavaMethod targetMethod);

    protected abstract ResolvedJavaMethod findForeignCallTarget(@SuppressWarnings("unused") ForeignCallDescriptor descriptor);

    @Override
    public RegisterAttributes attributes(Register register) {
        throw unimplemented();
    }

    @Override
    public Variable newVariable(ValueKind<?> kind) {
        return new LLVMVariable(kind);
    }

    @Override
    public Variable emitMove(Value input) {
        if (input instanceof LLVMVariable) {
            return (LLVMVariable) input;
        } else if (input instanceof LLVMValueWrapper) {
            return new LLVMVariable(getVal(input));
        }
        throw shouldNotReachHere("Unknown move input");
    }

    @Override
    public void emitMove(AllocatableValue dst, Value src) {
        LLVMValueRef source = getVal(src);
        LLVMTypeRef sourceType = typeOf(source);
        LLVMTypeRef destType = ((LLVMKind) dst.getPlatformKind()).get();

        /* Floating word cast */
        if (LLVMIRBuilder.isObject(destType) && LLVMIRBuilder.isIntegerType(sourceType) && LLVMIRBuilder.integerTypeWidth(sourceType) == JavaKind.Long.getBitCount()) {
            source = builder.buildIntToPtr(source, builder.rawPointerType());
            source = builder.buildRegisterObject(source);
        } else if (LLVMIRBuilder.isIntegerType(destType) && LLVMIRBuilder.integerTypeWidth(destType) == JavaKind.Long.getBitCount() && LLVMIRBuilder.isObject(sourceType)) {
            source = builder.buildPtrToInt(source, builder.longType());
        }
        ((LLVMVariable) dst).set(source);
    }

    @Override
    public void emitMoveConstant(AllocatableValue dst, Constant src) {
        throw unimplemented();
    }

    @Override
    public Variable emitAddress(AllocatableValue stackslot) {
        if (stackslot instanceof LLVMStackSlot) {
            return ((LLVMStackSlot) stackslot).address();
        }
        throw shouldNotReachHere("Unknown address type");
    }

    @Override
    public void emitMembar(int barriers) {
        builder.buildFence();
    }

    @Override
    public void emitUnwind(Value operand) {
        throw unimplemented();
    }

    @Override
    public void beforeRegisterAllocation() {
        throw unimplemented();
    }

    @Override
    public void emitIncomingValues(Value[] params) {
        throw unimplemented();
    }

    @Override
    public void emitReturn(JavaKind javaKind, Value input) {
        if (javaKind == JavaKind.Void) {
            if (debugLevel >= DebugLevel.FUNCTION) {
                emitPrintf("Return");
                deindent();
            }
            builder.buildRetVoid();
        } else {
            if (debugLevel >= DebugLevel.FUNCTION) {
                emitPrintf("Return", new JavaKind[]{javaKind}, new LLVMValueRef[]{getVal(input)});
                deindent();
            }
            LLVMValueRef retVal = getVal(input);
            if (javaKind == JavaKind.Int) {
                assert LLVMIRBuilder.isIntegerType(typeOf(retVal));
                switch (LLVMIRBuilder.integerTypeWidth(typeOf(retVal))) {
                    case 1:
                        retVal = builder.buildZExt(retVal, JavaKind.Int.getBitCount());
                        break;
                    case 8:
                    case 16:
                        retVal = builder.buildSExt(retVal, JavaKind.Int.getBitCount());
                        break;
                    case 32:
                        break;
                    default:
                        throw shouldNotReachHere(dumpValues("invalid return type for stack int", retVal));
                }
            } else if (returnsEnum && javaKind == JavaKind.Long) {
                /* Enum values are returned as long */
                retVal = builder.buildIntToPtr(retVal, builder.rawPointerType());
                retVal = builder.buildRegisterObject(retVal);
            }
            builder.buildRet(retVal);
        }
    }

    void indent() {
        LLVMValueRef counter = builder.buildLoad(indentCounter);
        LLVMValueRef newCounter = builder.buildAdd(counter, builder.constantInt(1));
        builder.buildStore(newCounter, indentCounter);
    }

    private void deindent() {
        LLVMValueRef counter = builder.buildLoad(indentCounter);
        LLVMValueRef newCounter = builder.buildSub(counter, builder.constantInt(1));
        builder.buildStore(newCounter, indentCounter);
    }

    @Override
    public AllocatableValue asAllocatable(Value value) {
        return (AllocatableValue) value;
    }

    @Override
    public Variable load(Value value) {
        LLVMValueRef load = builder.buildPtrToInt(getVal(value), builder.longType());
        return new LLVMVariable(load);
    }

    @Override
    public Value loadNonConst(Value value) {
        throw unimplemented();
    }

    @Override
    public boolean needOnlyOopMaps() {
        return false;
    }

    @Override
    public AllocatableValue resultOperandFor(JavaKind javaKind, ValueKind<?> valueKind) {
        return new LLVMVariable(builder.getLLVMType(javaKind));
    }

    @Override
    public <I extends LIRInstruction> I append(I op) {
        throw unimplemented();
    }

    @Override
    public void setSourcePosition(NodeSourcePosition position) {
        throw unimplemented();
    }

    @Override
    public void emitJump(LabelRef label) {
        builder.buildBranch(getBlock((Block) label.getTargetBlock()));
    }

    @Override
    public void emitCompareBranch(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination,
                    double trueDestinationProbability) {
        throw unimplemented();
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, LIRKind cmpKind, double overflowProbability) {
        throw unimplemented();
    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability) {
        throw unimplemented();
    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value leftVal, Value rightVal, Condition cond, boolean unorderedIsTrue, Value trueVal, Value falseVal) {
        LLVMValueRef condition = builder.buildCompare(cond, getVal(leftVal), getVal(rightVal), unorderedIsTrue);

        LLVMValueRef select;
        LLVMValueRef trueValue = getVal(trueVal);
        LLVMValueRef falseValue = getVal(falseVal);
        if (isObject(typeOf(trueValue))) {
            select = buildSelect(condition, trueValue, falseValue);
        } else {
            select = builder.buildSelect(condition, trueValue, falseValue);
        }
        return new LLVMVariable(select);
    }

    Variable emitIsNullMove(Value value, Value trueValue, Value falseValue) {
        LLVMValueRef isNull = builder.buildIsNull(getVal(value));
        LLVMValueRef select = builder.buildSelect(isNull, getVal(trueValue), getVal(falseValue));
        return new LLVMVariable(select);
    }

    @Override
    public Variable emitIntegerTestMove(Value left, Value right, Value trueValue, Value falseValue) {
        LLVMValueRef and = builder.buildAnd(getVal(left), getVal(right));
        LLVMValueRef isNull = builder.buildIsNull(and);
        LLVMValueRef select = builder.buildSelect(isNull, getVal(trueValue), getVal(falseValue));
        return new LLVMVariable(select);
    }

    /*
     * Select has to be manually created because of a bug in LLVM which makes it incompatible with
     * statepoint emission in rare cases.
     */
    private LLVMValueRef buildSelect(LLVMValueRef condition, LLVMValueRef trueVal, LLVMValueRef falseVal) {
        LLVMBasicBlockRef trueBlock = builder.appendBasicBlock(currentBlock.toString() + "_select_true");
        LLVMBasicBlockRef falseBlock = builder.appendBasicBlock(currentBlock.toString() + "_select_false");
        LLVMBasicBlockRef mergeBlock = builder.appendBasicBlock(currentBlock.toString() + "_select_end");
        splitBlockEndMap.put(currentBlock, mergeBlock);

        assert LLVMIRBuilder.compatibleTypes(typeOf(trueVal), typeOf(falseVal));

        builder.buildIf(condition, trueBlock, falseBlock);

        builder.positionAtEnd(trueBlock);
        builder.buildBranch(mergeBlock);

        builder.positionAtEnd(falseBlock);
        builder.buildBranch(mergeBlock);

        builder.positionAtEnd(mergeBlock);
        LLVMValueRef[] incomingValues = new LLVMValueRef[]{trueVal, falseVal};
        LLVMBasicBlockRef[] incomingBlocks = new LLVMBasicBlockRef[]{trueBlock, falseBlock};
        return builder.buildPhi(typeOf(trueVal), incomingValues, incomingBlocks);
    }

    @Override
    public void emitStrategySwitch(JavaConstant[] keyConstants, double[] keyProbabilities, LabelRef[] keyTargets, LabelRef defaultTarget, Variable value) {
        throw unimplemented();
    }

    @Override
    public void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget) {
        throw unimplemented();
    }

    @Override
    public Variable emitByteSwap(Value operand) {
        LLVMValueRef byteSwap = builder.buildBswap(getVal(operand));
        return new LLVMVariable(byteSwap);
    }

    @Override
    public Variable emitArrayEquals(JavaKind kind, Value array1, Value array2, Value length, int constantLength, boolean directPointers) {
        LLVMTypeRef elemType = builder.getLLVMType(kind);

        LLVMValueRef inArray1;
        LLVMValueRef inArray2;
        if (directPointers) {
            inArray1 = builder.buildIntToPtr(getVal(array1), builder.pointerType(elemType, false));
            inArray2 = builder.buildIntToPtr(getVal(array2), builder.pointerType(elemType, false));
        } else {
            int arrayBaseOffset = getProviders().getMetaAccess().getArrayBaseOffset(kind);

            inArray1 = builder.buildAddrSpaceCast(getVal(array1), builder.rawPointerType());
            inArray1 = builder.buildGEP(inArray1, builder.constantInt(arrayBaseOffset));
            inArray1 = builder.buildBitcast(inArray1, builder.pointerType(elemType, false));

            inArray2 = builder.buildAddrSpaceCast(getVal(array2), builder.rawPointerType());
            inArray2 = builder.buildGEP(inArray2, builder.constantInt(arrayBaseOffset));
            inArray2 = builder.buildBitcast(inArray2, builder.pointerType(elemType, false));
        }

        if (constantLength == 0) {
            return new LLVMVariable(builder.constantBoolean(true));
        }
        LLVMValueRef count = (constantLength > 0) ? builder.constantInt(constantLength) : getVal(length);

        LLVMBasicBlockRef startBlock = getBlockEnd(currentBlock);
        LLVMBasicBlockRef loopBlock = builder.appendBasicBlock(currentBlock.toString() + "array_equals_loop");
        LLVMBasicBlockRef endBlock = builder.appendBasicBlock(currentBlock.toString() + "array_equals_end");
        splitBlockEndMap.put(currentBlock, endBlock);

        /* Loop prologue */
        LLVMValueRef zeroLength = builder.buildIsNull(count);
        builder.buildIf(zeroLength, endBlock, loopBlock);

        /* Loop body (will be vectorized by LLVM) */
        builder.positionAtEnd(loopBlock);

        LLVMBasicBlockRef[] incomingBlocks = {startBlock};
        LLVMValueRef[] incomingEqualsValues = {builder.constantBoolean(true)};
        LLVMValueRef equals = builder.buildPhi(builder.booleanType(), incomingEqualsValues, incomingBlocks);
        LLVMValueRef[] incomingIValues = {builder.constantInt(0)};
        LLVMValueRef i = builder.buildPhi(builder.intType(), incomingIValues, incomingBlocks);

        LLVMValueRef elem1 = builder.buildLoad(builder.buildGEP(inArray1, i));
        LLVMValueRef elem2 = builder.buildLoad(builder.buildGEP(inArray2, i));
        LLVMValueRef compare = builder.buildCompare(Condition.EQ, elem1, elem2, false);

        LLVMValueRef newEquals = builder.buildAnd(compare, equals);
        LLVMValueRef newI = builder.buildAdd(i, builder.constantInt(1));

        LLVMBasicBlockRef[] newIncomingBlocks = new LLVMBasicBlockRef[]{loopBlock};
        LLVMValueRef[] newIncomingEqualsValues = new LLVMValueRef[]{newEquals};
        builder.addIncoming(equals, newIncomingEqualsValues, newIncomingBlocks);
        LLVMValueRef[] newIncomingIValues = new LLVMValueRef[]{newI};
        builder.addIncoming(i, newIncomingIValues, newIncomingBlocks);

        LLVMValueRef atEnd = builder.buildCompare(Condition.EQ, newI, count, false);
        LLVMValueRef exitLoop = builder.buildOr(atEnd, builder.buildNot(newEquals));
        builder.buildIf(exitLoop, endBlock, loopBlock);

        /* Loop exit */
        builder.positionAtEnd(endBlock);

        LLVMBasicBlockRef[] incomingArrayEqualsBlocks = new LLVMBasicBlockRef[]{startBlock, loopBlock};
        LLVMValueRef[] incomingArrayEqualsValues = new LLVMValueRef[]{builder.constantBoolean(true), newEquals};
        LLVMValueRef arrayEquals = builder.buildPhi(builder.booleanType(), incomingArrayEqualsValues, incomingArrayEqualsBlocks);

        return new LLVMVariable(builder.buildSelect(arrayEquals, builder.constantInt(1), builder.constantInt(0)));
    }

    @Override
    public Variable emitArrayCompareTo(JavaKind kind1, JavaKind kind2, Value array1, Value array2, Value length1, Value length2) {
        LLVMValueRef inArray1 = builder.buildAddrSpaceCast(getVal(array1), builder.rawPointerType());
        int arrayBaseOffset1 = getProviders().getMetaAccess().getArrayBaseOffset(kind1);
        inArray1 = builder.buildGEP(inArray1, builder.constantInt(arrayBaseOffset1));
        LLVMTypeRef elemType1 = builder.getLLVMType(kind1);
        inArray1 = builder.buildBitcast(inArray1, builder.pointerType(elemType1, false));

        LLVMValueRef inArray2 = builder.buildAddrSpaceCast(getVal(array2), builder.rawPointerType());
        int arrayBaseOffset2 = getProviders().getMetaAccess().getArrayBaseOffset(kind2);
        inArray2 = builder.buildGEP(inArray2, builder.constantInt(arrayBaseOffset2));
        LLVMTypeRef elemType2 = builder.getLLVMType(kind2);
        inArray2 = builder.buildBitcast(inArray2, builder.pointerType(elemType2, false));

        LLVMValueRef count1 = builder.buildDiv(getVal(length1), builder.constantInt(kind1.getByteCount()));
        LLVMValueRef count2 = builder.buildDiv(getVal(length2), builder.constantInt(kind2.getByteCount()));

        LLVMBasicBlockRef startBlock = getBlockEnd(currentBlock);
        LLVMBasicBlockRef loopBlock = builder.appendBasicBlock(currentBlock.toString() + "array_compareto_loop");
        LLVMBasicBlockRef endBlock = builder.appendBasicBlock(currentBlock.toString() + "array_compareto_end");
        splitBlockEndMap.put(currentBlock, endBlock);

        /* Loop prologue */
        LLVMValueRef countDiff = builder.buildSub(count1, count2);
        LLVMValueRef minCount = builder.buildSelect(builder.buildICmp(Condition.BE, count1, count2), count1, count2);

        LLVMValueRef zeroLength = builder.buildIsNull(minCount);
        builder.buildIf(zeroLength, endBlock, loopBlock);

        /* Loop body (will be vectorized by LLVM) */
        builder.positionAtEnd(loopBlock);

        LLVMBasicBlockRef[] incomingBlocks = {startBlock};
        LLVMValueRef[] incomingIValues = {builder.constantInt(0)};
        LLVMValueRef i = builder.buildPhi(builder.intType(), incomingIValues, incomingBlocks);

        LLVMValueRef elem1 = builder.buildLoad(builder.buildGEP(inArray1, i));
        LLVMValueRef elem2 = builder.buildLoad(builder.buildGEP(inArray2, i));

        assert kind1 == kind2;
        LLVMValueRef different = builder.buildCompare(Condition.NE, elem1, elem2, false);
        LLVMValueRef compare = builder.buildCompare(Condition.LT, elem1, elem2, false);

        LLVMValueRef newI = builder.buildAdd(i, builder.constantInt(1));

        LLVMValueRef[] newIncomingIValues = new LLVMValueRef[]{newI};
        LLVMBasicBlockRef[] newIncomingBlocks = new LLVMBasicBlockRef[]{loopBlock};
        builder.addIncoming(i, newIncomingIValues, newIncomingBlocks);

        LLVMValueRef atEnd = builder.buildCompare(Condition.EQ, newI, minCount, false);
        LLVMValueRef exitLoop = builder.buildOr(atEnd, different);
        builder.buildIf(exitLoop, endBlock, loopBlock);

        /* Loop end */
        builder.positionAtEnd(endBlock);

        LLVMBasicBlockRef[] incomingEndBlocks = new LLVMBasicBlockRef[]{startBlock, loopBlock};
        LLVMValueRef[] incomingDifferentValues = new LLVMValueRef[]{builder.constantBoolean(false), different};
        LLVMValueRef differentPhi = builder.buildPhi(builder.booleanType(), incomingDifferentValues, incomingEndBlocks);
        LLVMValueRef[] incomingCompareValues = new LLVMValueRef[]{builder.constantBoolean(false), compare};
        LLVMValueRef comparePhi = builder.buildPhi(builder.booleanType(), incomingCompareValues, incomingEndBlocks);

        LLVMValueRef valCmp = builder.buildSelect(comparePhi, builder.constantInt(-1), builder.constantInt(1));
        LLVMValueRef arrayCompare = builder.buildSelect(differentPhi, valCmp, countDiff);

        return new LLVMVariable(arrayCompare);
    }

    @Override
    public Variable emitArrayIndexOf(JavaKind arrayKind, JavaKind valueKind, boolean findTwoConsecutive, Value arrayPointer, Value arrayCount, Value fromIndexVal, Value... searchValues) {
        JavaKind comparisonKind;
        switch (valueKind) {
            case Byte:
                comparisonKind = findTwoConsecutive ? JavaKind.Short : JavaKind.Byte;
                break;
            case Short:
            case Char:
                comparisonKind = findTwoConsecutive ? JavaKind.Int : JavaKind.Short;
                break;
            default:
                throw shouldNotReachHere("invalid array index of kind " + valueKind.toString());
        }
        LLVMTypeRef comparisonType = builder.getLLVMType(comparisonKind);
        LLVMTypeRef elemType = builder.getLLVMType(valueKind);

        LLVMValueRef array = builder.buildAddrSpaceCast(getVal(arrayPointer), builder.rawPointerType());
        array = builder.buildGEP(array, builder.constantInt(getProviders().getMetaAccess().getArrayBaseOffset(arrayKind)));
        array = builder.buildBitcast(array, builder.pointerType(elemType, false));

        LLVMValueRef fromIndex = getVal(fromIndexVal);

        LLVMValueRef count = getVal(arrayCount);
        assert LLVMIRBuilder.isIntegerType(typeOf(count)) && LLVMIRBuilder.integerTypeWidth(typeOf(count)) == 32;
        if (findTwoConsecutive) {
            count = builder.buildSub(count, builder.constantInt(1));
        }

        assert searchValues.length <= 4;
        int valueWidth = LLVMIRBuilder.integerTypeWidth(comparisonType);
        LLVMValueRef[] values = Arrays.stream(searchValues).map(LLVMUtils::getVal).map(value -> {
            assert LLVMIRBuilder.isIntegerType(typeOf(value));
            return builder.buildIntegerConvert(value, valueWidth);
        }).toArray(LLVMValueRef[]::new);

        LLVMBasicBlockRef startBlock = getBlockEnd(currentBlock);
        LLVMBasicBlockRef loopBlock = builder.appendBasicBlock(getCurrentBlock().toString() + "_array_indexof_loop");
        LLVMBasicBlockRef endBlock = builder.appendBasicBlock(getCurrentBlock().toString() + "array_indexof_end");
        splitBlockEndMap.put(currentBlock, endBlock);

        /* Loop prologue */
        LLVMValueRef zeroLength = builder.buildIsNull(count);
        builder.buildIf(zeroLength, endBlock, loopBlock);

        /* Loop body (will be vectorized by LLVM) */
        builder.positionAtEnd(loopBlock);

        LLVMBasicBlockRef[] incomingBlocks = {startBlock};
        LLVMValueRef[] incomingIValues = {fromIndex};
        LLVMValueRef i = builder.buildPhi(builder.intType(), incomingIValues, incomingBlocks);
        LLVMValueRef[] incomingIndexValues = {builder.constantInt(-1)};
        LLVMValueRef index = builder.buildPhi(builder.intType(), incomingIndexValues, incomingBlocks);

        LLVMValueRef pointer = builder.buildGEP(array, i);
        if (findTwoConsecutive) {
            pointer = builder.buildBitcast(pointer, builder.pointerType(comparisonType, false));
        }
        LLVMValueRef elem = builder.buildLoad(pointer, comparisonType);
        LLVMValueRef equal = builder.constantBoolean(false);
        for (LLVMValueRef value : values) {
            LLVMValueRef thisCompare = builder.buildCompare(Condition.EQ, elem, value, false);
            equal = builder.buildOr(equal, thisCompare);
        }

        LLVMValueRef newI = builder.buildAdd(i, builder.constantInt(1));
        LLVMValueRef newIndex = builder.buildSelect(equal, i, index);

        LLVMBasicBlockRef[] newIncomingBlocks = new LLVMBasicBlockRef[]{loopBlock};
        LLVMValueRef[] newIncomingIValues = new LLVMValueRef[]{newI};
        builder.addIncoming(i, newIncomingIValues, newIncomingBlocks);
        LLVMValueRef[] newIncomingIndexValues = new LLVMValueRef[]{newIndex};
        builder.addIncoming(index, newIncomingIndexValues, newIncomingBlocks);

        LLVMValueRef atEnd = builder.buildCompare(Condition.EQ, newI, count, false);
        LLVMValueRef exitLoop = builder.buildOr(atEnd, equal);
        builder.buildIf(exitLoop, endBlock, loopBlock);

        /* Loop exit */
        builder.positionAtEnd(endBlock);

        LLVMBasicBlockRef[] incomingEndBlocks = new LLVMBasicBlockRef[]{startBlock, loopBlock};
        LLVMValueRef[] incomingReturnValues = new LLVMValueRef[]{builder.constantInt(-1), newIndex};
        LLVMValueRef indexPhi = builder.buildPhi(builder.intType(), incomingReturnValues, incomingEndBlocks);

        return new LLVMVariable(indexPhi);
    }

    @Override
    public void emitBlackhole(Value operand) {
        builder.buildStackmap(builder.constantLong(LLVMUtils.DEFAULT_PATCHPOINT_ID), getVal(operand));
    }

    @Override
    public LIRKind getLIRKind(Stamp stamp) {
        return stamp.getLIRKind(lirKindTool);
    }

    @Override
    public void emitPause() {
        // this will be implemented as part of issue #1126. For now, we just do nothing.
        // throw unimplemented();
    }

    @Override
    public void emitPrefetchAllocate(Value address) {
        builder.buildPrefetch(getVal(address));
    }

    @Override
    public Value emitCompress(Value pointer, CompressEncoding encoding, boolean nonNull) {
        throw unimplemented();
    }

    @Override
    public Value emitUncompress(Value pointer, CompressEncoding encoding, boolean nonNull) {
        throw unimplemented();
    }

    @Override
    public void emitSpeculationFence() {
        throw unimplemented();
    }

    @Override
    public LIRKind getValueKind(JavaKind javaKind) {
        return getLIRKind(StampFactory.forKind(javaKind));
    }

    @Override
    public LIRInstruction createBenchmarkCounter(String name, String group, Value increment) {
        throw unimplemented();
    }

    @Override
    public LIRInstruction createMultiBenchmarkCounter(String[] names, String[] groups, Value[] increments) {
        throw unimplemented();
    }

    @Override
    public StandardOp.SaveRegistersOp createZapRegisters(Register[] zappedRegisters, JavaConstant[] zapValues) {
        throw unimplemented();
    }

    @Override
    public StandardOp.SaveRegistersOp createZapRegisters() {
        throw unimplemented();
    }

    @Override
    public LIRInstruction createZapArgumentSpace(StackSlot[] zappedStack, JavaConstant[] zapValues) {
        throw unimplemented();
    }

    @Override
    public LIRInstruction zapArgumentSpace() {
        throw unimplemented();
    }

    @Override
    public VirtualStackSlot allocateStackSlots(int slots) {
        builder.positionAtStart();
        LLVMValueRef alloca = builder.buildPtrToInt(builder.buildArrayAlloca(slots), builder.longType());
        builder.positionAtEnd(getBlockEnd(currentBlock));

        return new LLVMStackSlot(alloca);
    }

    @Override
    public Value emitReadCallerStackPointer(Stamp wordStamp) {
        LLVMValueRef basePointer = builder.buildFrameAddress(builder.constantInt(0));
        LLVMValueRef callerSP = builder.buildAdd(builder.buildPtrToInt(basePointer, builder.longType()), builder.constantLong(16));
        return new LLVMVariable(callerSP);
    }

    @Override
    public Value emitReadReturnAddress(Stamp wordStamp, int returnAddressSize) {
        LLVMValueRef returnAddress = builder.buildReturnAddress(builder.constantInt(0));
        return new LLVMVariable(builder.buildPtrToInt(returnAddress, builder.longType()));
    }

    public LLVMGenerationResult getLLVMResult() {
        return generationResult;
    }

    int getDebugLevel() {
        return debugLevel;
    }

    public static class ArithmeticLLVMGenerator implements ArithmeticLIRGeneratorTool, AArch64ArithmeticLIRGeneratorTool {
        private final LLVMIRBuilder builder;

        ArithmeticLLVMGenerator(LLVMIRBuilder builder) {
            this.builder = builder;
        }

        @Override
        public Value emitNegate(Value input) {
            LLVMValueRef neg = builder.buildNeg(getVal(input));
            return new LLVMVariable(neg);
        }

        @Override
        public Value emitAdd(Value a, Value b, boolean setFlags) {
            LLVMValueRef add = builder.buildAdd(getVal(a), getVal(b));
            return new LLVMVariable(add);
        }

        @Override
        public Value emitSub(Value a, Value b, boolean setFlags) {
            LLVMValueRef sub = builder.buildSub(getVal(a), getVal(b));
            return new LLVMVariable(sub);
        }

        @Override
        public Value emitMul(Value a, Value b, boolean setFlags) {
            LLVMValueRef mul = builder.buildMul(getVal(a), getVal(b));
            return new LLVMVariable(mul);
        }

        @Override
        public Value emitMulHigh(Value a, Value b) {
            return emitMulHigh(a, b, true);
        }

        @Override
        public Value emitUMulHigh(Value a, Value b) {
            return emitMulHigh(a, b, false);
        }

        private LLVMVariable emitMulHigh(Value a, Value b, boolean signed) {
            LLVMValueRef valA = getVal(a);
            LLVMValueRef valB = getVal(b);
            assert LLVMIRBuilder.compatibleTypes(typeOf(valA), typeOf(valB)) : dumpValues("invalid mulhigh arguments", valA, valB);

            int baseBits = LLVMIRBuilder.integerTypeWidth(LLVMIRBuilder.typeOf(valA));
            int extendedBits = baseBits * 2;

            BiFunction<LLVMValueRef, Integer, LLVMValueRef> extend = (signed) ? builder::buildSExt : builder::buildZExt;
            valA = extend.apply(valA, extendedBits);
            valB = extend.apply(valB, extendedBits);
            LLVMValueRef mul = builder.buildMul(valA, valB);

            BiFunction<LLVMValueRef, LLVMValueRef, LLVMValueRef> shift = (signed) ? builder::buildShr : builder::buildUShr;
            LLVMValueRef shiftedMul = shift.apply(mul, builder.constantInteger(baseBits, baseBits));
            LLVMValueRef truncatedMul = builder.buildTrunc(shiftedMul, baseBits);

            return new LLVMVariable(truncatedMul);
        }

        @Override
        public Value emitDiv(Value a, Value b, LIRFrameState state) {
            LLVMValueRef div = builder.buildDiv(getVal(a), getVal(b));
            return new LLVMVariable(div);
        }

        @Override
        public Value emitRem(Value a, Value b, LIRFrameState state) {
            LLVMValueRef rem = builder.buildRem(getVal(a), getVal(b));
            return new LLVMVariable(rem);
        }

        @Override
        public Value emitUDiv(Value a, Value b, LIRFrameState state) {
            LLVMValueRef uDiv = builder.buildUDiv(getVal(a), getVal(b));
            return new LLVMVariable(uDiv);
        }

        @Override
        public Value emitURem(Value a, Value b, LIRFrameState state) {
            LLVMValueRef uRem = builder.buildURem(getVal(a), getVal(b));
            return new LLVMVariable(uRem);
        }

        @Override
        public Value emitNot(Value input) {
            LLVMValueRef not = builder.buildNot(getVal(input));
            return new LLVMVariable(not);
        }

        @Override
        public Value emitAnd(Value a, Value b) {
            LLVMValueRef and = builder.buildAnd(getVal(a), getVal(b));
            return new LLVMVariable(and);
        }

        @Override
        public Value emitOr(Value a, Value b) {
            LLVMValueRef or = builder.buildOr(getVal(a), getVal(b));
            return new LLVMVariable(or);
        }

        @Override
        public Value emitXor(Value a, Value b) {
            LLVMValueRef xor = builder.buildXor(getVal(a), getVal(b));
            return new LLVMVariable(xor);
        }

        @Override
        public Value emitShl(Value a, Value b) {
            LLVMValueRef shl = builder.buildShl(getVal(a), getVal(b));
            return new LLVMVariable(shl);
        }

        @Override
        public Value emitShr(Value a, Value b) {
            LLVMValueRef shr = builder.buildShr(getVal(a), getVal(b));
            return new LLVMVariable(shr);
        }

        @Override
        public Value emitUShr(Value a, Value b) {
            LLVMValueRef ushr = builder.buildUShr(getVal(a), getVal(b));
            return new LLVMVariable(ushr);
        }

        @Override
        public Value emitFloatConvert(FloatConvert op, Value inputVal) {
            LLVMTypeRef destType;
            switch (op) {
                case F2I:
                case D2I:
                    destType = builder.intType();
                    break;
                case F2L:
                case D2L:
                    destType = builder.longType();
                    break;
                case I2F:
                case L2F:
                case D2F:
                    destType = builder.floatType();
                    break;
                case I2D:
                case L2D:
                case F2D:
                    destType = builder.doubleType();
                    break;
                default:
                    throw shouldNotReachHere("invalid FloatConvert type");
            }

            LLVMValueRef convert;
            switch (op.getCategory()) {
                case FloatingPointToInteger:
                    convert = builder.buildFPToSI(getVal(inputVal), destType);
                    break;
                case IntegerToFloatingPoint:
                    convert = builder.buildSIToFP(getVal(inputVal), destType);
                    break;
                case FloatingPointToFloatingPoint:
                    convert = builder.buildFPCast(getVal(inputVal), destType);
                    break;
                default:
                    throw shouldNotReachHere("invalid FloatConvert type");
            }
            return new LLVMVariable(convert);
        }

        @Override
        public Value emitReinterpret(LIRKind to, Value inputVal) {
            LLVMTypeRef type = getType(to);
            LLVMValueRef cast = builder.buildBitcast(getVal(inputVal), type);
            return new LLVMVariable(cast);
        }

        @Override
        public Value emitNarrow(Value inputVal, int bits) {
            LLVMValueRef narrow = builder.buildTrunc(getVal(inputVal), bits);
            return new LLVMVariable(narrow);
        }

        @Override
        public Value emitSignExtend(Value inputVal, int fromBits, int toBits) {
            LLVMValueRef signExtend = builder.buildSExt(getVal(inputVal), toBits);
            return new LLVMVariable(signExtend);
        }

        @Override
        public Value emitZeroExtend(Value inputVal, int fromBits, int toBits) {
            LLVMValueRef zeroExtend = builder.buildZExt(getVal(inputVal), toBits);
            return new LLVMVariable(zeroExtend);
        }

        @Override
        public Value emitMathAbs(Value input) {
            LLVMValueRef abs = builder.buildAbs(getVal(input));
            return new LLVMVariable(abs);
        }

        @Override
        public Value emitMathSqrt(Value input) {
            LLVMValueRef sqrt = builder.buildSqrt(getVal(input));
            return new LLVMVariable(sqrt);
        }

        @Override
        public Value emitMathLog(Value input, boolean base10) {
            LLVMValueRef value = getVal(input);
            LLVMValueRef log = base10 ? builder.buildLog10(value) : builder.buildLog(value);
            return new LLVMVariable(log);
        }

        @Override
        public Value emitMathCos(Value input) {
            LLVMValueRef cos = builder.buildCos(getVal(input));
            return new LLVMVariable(cos);
        }

        @Override
        public Value emitMathSin(Value input) {
            LLVMValueRef sin = builder.buildSin(getVal(input));
            return new LLVMVariable(sin);
        }

        @Override
        public Value emitMathTan(Value input) {
            LLVMValueRef value = getVal(input);
            LLVMValueRef sin = builder.buildSin(value);
            LLVMValueRef cos = builder.buildCos(value);
            LLVMValueRef tan = builder.buildDiv(sin, cos);
            return new LLVMVariable(tan);
        }

        @Override
        public Value emitMathExp(Value input) {
            LLVMValueRef exp = builder.buildExp(getVal(input));
            return new LLVMVariable(exp);
        }

        @Override
        public Value emitMathPow(Value x, Value y) {
            LLVMValueRef pow = builder.buildPow(getVal(x), getVal(y));
            return new LLVMVariable(pow);
        }

        @Override
        public Value emitBitCount(Value operand) {
            LLVMValueRef op = getVal(operand);
            LLVMValueRef answer = builder.buildCtpop(op);
            answer = builder.buildIntegerConvert(answer, LLVMIRBuilder.integerTypeWidth(builder.intType()));
            return new LLVMVariable(answer);
        }

        @Override
        public Value emitBitScanForward(Value operand) {
            LLVMValueRef op = getVal(operand);
            LLVMValueRef trailingZeros = builder.buildCttz(op);

            int resultSize = LLVMIRBuilder.integerTypeWidth(typeOf(trailingZeros));
            int expectedSize = JavaKind.Int.getBitCount();
            if (resultSize < expectedSize) {
                trailingZeros = builder.buildZExt(trailingZeros, expectedSize);
            } else if (resultSize > expectedSize) {
                trailingZeros = builder.buildTrunc(trailingZeros, expectedSize);
            }

            return new LLVMVariable(trailingZeros);
        }

        @Override
        public Value emitBitScanReverse(Value operand) {
            LLVMValueRef op = getVal(operand);

            int opSize = LLVMIRBuilder.integerTypeWidth(typeOf(op));
            int expectedSize = JavaKind.Int.getBitCount();
            LLVMValueRef leadingZeros = builder.buildCtlz(op);
            if (opSize < expectedSize) {
                leadingZeros = builder.buildZExt(leadingZeros, expectedSize);
            } else if (opSize > expectedSize) {
                leadingZeros = builder.buildTrunc(leadingZeros, expectedSize);
            }

            LLVMValueRef result = builder.buildSub(builder.constantInt(opSize - 1), leadingZeros);
            return new LLVMVariable(result);
        }

        @Override
        public Variable emitLoad(LIRKind kind, Value address, LIRFrameState state) {
            LLVMValueRef load = builder.buildLoad(getVal(address), getType(kind));
            return new LLVMVariable(load);
        }

        @Override
        public void emitStore(ValueKind<?> kind, Value address, Value input, LIRFrameState state) {
            builder.buildStore(getVal(input), getVal(address));
        }

        @Override
        public Value emitCountLeadingZeros(Value value) {
            LLVMValueRef op = getVal(value);
            LLVMValueRef answer = builder.buildCtlz(op);
            answer = builder.buildIntegerConvert(answer, LLVMIRBuilder.integerTypeWidth(builder.intType()));
            return new LLVMVariable(answer);
        }

        @Override
        public Value emitCountTrailingZeros(Value value) {
            LLVMValueRef op = getVal(value);
            LLVMValueRef answer = builder.buildCttz(op);
            answer = builder.buildIntegerConvert(answer, LLVMIRBuilder.integerTypeWidth(builder.intType()));
            return new LLVMVariable(answer);
        }

        @Override
        @SuppressWarnings("unused")
        public Value emitRound(Value value, RoundingMode mode) {
            // This should be implemented, see #1168
            return null;
        }

        @SuppressWarnings("unused")
        public void emitCompareOp(AArch64Kind cmpKind, Variable left, Value right) {
            // This should be implemented, see #1168
        }
    }
}
