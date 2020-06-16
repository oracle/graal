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
package com.oracle.svm.core.graal.llvm;

import static com.oracle.svm.core.graal.llvm.util.LLVMIRBuilder.typeOf;
import static com.oracle.svm.core.graal.llvm.util.LLVMUtils.dumpTypes;
import static com.oracle.svm.core.graal.llvm.util.LLVMUtils.dumpValues;
import static com.oracle.svm.core.graal.llvm.util.LLVMUtils.getType;
import static com.oracle.svm.core.graal.llvm.util.LLVMUtils.getVal;
import static org.graalvm.compiler.debug.GraalError.shouldNotReachHere;
import static org.graalvm.compiler.debug.GraalError.unimplemented;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.spi.CodeGenProviders;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.core.common.type.RawPointerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.type.NarrowOopStamp;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.util.GuardedAnnotationAccess;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointBuiltins;
import com.oracle.svm.core.c.function.CEntryPointNativeFunctions;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.graal.code.SubstrateCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.code.SubstrateDataBuilder;
import com.oracle.svm.core.graal.code.SubstrateLIRGenerator;
import com.oracle.svm.core.graal.llvm.LLVMFeature.LLVMVersionChecker;
import com.oracle.svm.core.graal.llvm.replacements.LLVMIntrinsicGenerator;
import com.oracle.svm.core.graal.llvm.runtime.LLVMExceptionUnwind;
import com.oracle.svm.core.graal.llvm.util.LLVMIRBuilder;
import com.oracle.svm.core.graal.llvm.util.LLVMIRBuilder.Attribute;
import com.oracle.svm.core.graal.llvm.util.LLVMIRBuilder.GCStrategy;
import com.oracle.svm.core.graal.llvm.util.LLVMIRBuilder.InlineAssemblyConstraint;
import com.oracle.svm.core.graal.llvm.util.LLVMIRBuilder.InlineAssemblyConstraint.Location;
import com.oracle.svm.core.graal.llvm.util.LLVMIRBuilder.InlineAssemblyConstraint.Type;
import com.oracle.svm.core.graal.llvm.util.LLVMIRBuilder.LinkageType;
import com.oracle.svm.core.graal.llvm.util.LLVMOptions;
import com.oracle.svm.core.graal.llvm.util.LLVMStackMapInfo;
import com.oracle.svm.core.graal.llvm.util.LLVMTargetSpecific;
import com.oracle.svm.core.graal.llvm.util.LLVMUtils;
import com.oracle.svm.core.graal.llvm.util.LLVMUtils.LLVMConstant;
import com.oracle.svm.core.graal.llvm.util.LLVMUtils.LLVMKind;
import com.oracle.svm.core.graal.llvm.util.LLVMUtils.LLVMPendingSpecialRegisterRead;
import com.oracle.svm.core.graal.llvm.util.LLVMUtils.LLVMStackSlot;
import com.oracle.svm.core.graal.llvm.util.LLVMUtils.LLVMValueWrapper;
import com.oracle.svm.core.graal.llvm.util.LLVMUtils.LLVMVariable;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.graal.snippets.CEntryPointSnippets;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMTypeRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMValueRef;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.code.site.InfopointReason;
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

/*
 * Contains the tools needed to emit instructions from Graal nodes into LLVM bitcode,
 * via the LLVMIRBuilder class.
 */
public class LLVMGenerator implements LIRGeneratorTool, SubstrateLIRGenerator {
    private static final SubstrateDataBuilder dataBuilder = new SubstrateDataBuilder();
    private final Providers providers;
    private final CompilationResult compilationResult;

    private final LLVMIRBuilder builder;
    private final ArithmeticLLVMGenerator arithmetic;
    private final LIRKindTool lirKindTool;
    private final DebugInfoPrinter debugInfoPrinter;

    private final String functionName;
    private final boolean isEntryPoint;
    private final boolean canModifySpecialRegisters;
    private final boolean returnsEnum;
    private final boolean returnsCEnum;

    private Block currentBlock;
    private final Map<AbstractBeginNode, LLVMBasicBlockRef> basicBlockMap = new HashMap<>();
    private final Map<Block, LLVMBasicBlockRef> splitBlockEndMap = new HashMap<>();
    private final Map<Block, LLVMValueRef[]> specialRegValues = new HashMap<>();
    private final Map<Block, LLVMValueRef[]> initialSpecialRegValues = new HashMap<>();
    private final Map<Block, LLVMValueRef[]> handlerSpecialRegValues = new HashMap<>();

    private final LLVMValueRef[] stackSlots = new LLVMValueRef[SpecialRegister.count()];
    private final Map<Constant, String> constants = new HashMap<>();

    LLVMGenerator(Providers providers, CompilationResult result, ResolvedJavaMethod method, int debugLevel) {
        this.providers = providers;
        this.compilationResult = result;
        this.builder = new LLVMIRBuilder(method.format("%H.%n"));
        this.arithmetic = new ArithmeticLLVMGenerator(builder);
        this.lirKindTool = new LLVMUtils.LLVMKindTool(builder);
        this.debugInfoPrinter = new DebugInfoPrinter(this, debugLevel);

        this.functionName = SubstrateUtil.uniqueShortName(method);
        this.isEntryPoint = isEntryPoint(method);
        this.canModifySpecialRegisters = canModifySpecialRegisters(method);

        ResolvedJavaType returnType = method.getSignature().getReturnType(null).resolve(null);
        this.returnsEnum = returnType.isEnum();
        this.returnsCEnum = isCEnumType(returnType);

        addMainFunction(method);
    }

    @Override
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
    public TargetDescription target() {
        return getCodeCache().getTarget();
    }

    @Override
    public SubstrateRegisterConfig getRegisterConfig() {
        return (SubstrateRegisterConfig) getCodeCache().getRegisterConfig();
    }

    @Override
    public ForeignCallsProvider getForeignCalls() {
        return providers.getForeignCalls();
    }

    CompilationResult getCompilationResult() {
        return compilationResult;
    }

    public LLVMIRBuilder getBuilder() {
        return builder;
    }

    @Override
    public ArithmeticLIRGeneratorTool getArithmetic() {
        return arithmetic;
    }

    DebugInfoPrinter getDebugInfoPrinter() {
        return debugInfoPrinter;
    }

    /* Function */

    String getFunctionName() {
        return functionName;
    }

    boolean isEntryPoint() {
        return isEntryPoint;
    }

    private void addMainFunction(ResolvedJavaMethod method) {
        builder.setMainFunction(functionName, getLLVMFunctionType(method, true));
        builder.setFunctionLinkage(LinkageType.External);
        builder.setFunctionAttribute(Attribute.NoInline);
        builder.setFunctionAttribute(Attribute.NoRedZone);
        builder.setFunctionAttribute(Attribute.NoRealignStack);
        builder.setGarbageCollector(GCStrategy.CompressedPointers);
        builder.setPersonalityFunction(getFunction(LLVMExceptionUnwind.getPersonalityStub(getMetaAccess())));

        if (isEntryPoint) {
            builder.addAlias(SubstrateUtil.mangleName(functionName));

            Object entryPointData = ((HostedMethod) method).getWrapped().getEntryPointData();
            if (entryPointData instanceof CEntryPointData) {
                CEntryPointData cEntryPointData = (CEntryPointData) entryPointData;
                if (cEntryPointData.getPublishAs() != CEntryPointOptions.Publish.NotPublished) {
                    String entryPointSymbolName = cEntryPointData.getSymbolName();
                    assert !entryPointSymbolName.isEmpty();
                    builder.addAlias(entryPointSymbolName);
                }
            }
        }
    }

    LLVMValueRef getFunction(ResolvedJavaMethod method) {
        LLVMTypeRef functionType = getLLVMFunctionType(method, false);
        return builder.getFunction(getFunctionName(method), functionType);
    }

    byte[] getBitcode() {
        assert builder.verifyBitcode();
        byte[] bitcode = builder.getBitcode();
        builder.close();
        return bitcode;
    }

    private static String getFunctionName(ResolvedJavaMethod method) {
        return SubstrateUtil.uniqueShortName(method);
    }

    private static boolean isEntryPoint(ResolvedJavaMethod method) {
        return ((HostedMethod) method).isEntryPoint();
    }

    private boolean canModifySpecialRegisters(ResolvedJavaMethod method) {
        CEntryPointOptions entryPointOptions = GuardedAnnotationAccess.getAnnotation(method, CEntryPointOptions.class);
        return (entryPointOptions != null) && entryPointOptions.prologue() == CEntryPointOptions.NoPrologue.class ||
                        method.getDeclaringClass().equals(getMetaAccess().lookupJavaType(CEntryPointSnippets.class)) ||
                        method.getDeclaringClass().equals(getMetaAccess().lookupJavaType(CEntryPointNativeFunctions.class)) ||
                        method.getDeclaringClass().equals(getMetaAccess().lookupJavaType(CEntryPointBuiltins.class));
    }

    /* Basic blocks */

    void appendBasicBlock(Block block) {
        LLVMBasicBlockRef basicBlock = builder.appendBasicBlock(block.toString());
        basicBlockMap.put(block.getBeginNode(), basicBlock);
    }

    void beginBlock(Block block) {
        currentBlock = block;
        builder.positionAtEnd(getBlock(block));
    }

    void resumeBlock(Block block) {
        currentBlock = block;
        builder.positionAtEnd(getBlockEnd(block));
    }

    void editBlock(Block block) {
        currentBlock = block;
        builder.positionBeforeTerminator(getBlockEnd(block));
    }

    @Override
    public AbstractBlockBase<?> getCurrentBlock() {
        return currentBlock;
    }

    LLVMBasicBlockRef getBlock(Block block) {
        return getBlock(block.getBeginNode());
    }

    LLVMBasicBlockRef getBlock(AbstractBeginNode begin) {
        return basicBlockMap.get(begin);
    }

    LLVMBasicBlockRef getBlockEnd(Block block) {
        return (splitBlockEndMap.containsKey(block)) ? splitBlockEndMap.get(block) : getBlock(block);
    }

    /* Types */

    @Override
    public LIRKind getLIRKind(Stamp stamp) {
        return stamp.getLIRKind(lirKindTool);
    }

    @Override
    public LIRKind getValueKind(JavaKind javaKind) {
        return getLIRKind(StampFactory.forKind(javaKind));
    }

    LLVMTypeRef getLLVMType(Stamp stamp) {
        if (stamp instanceof RawPointerStamp) {
            return builder.rawPointerType();
        }
        return getLLVMType(getTypeKind(stamp.javaType(getMetaAccess()), false), stamp instanceof NarrowOopStamp);
    }

    LLVMTypeRef getLLVMStackType(JavaKind kind) {
        return getLLVMType(kind.getStackKind(), false);
    }

    JavaKind getTypeKind(ResolvedJavaType type, boolean forMainFunction) {
        if (forMainFunction && isEntryPoint && isCEnumType(type)) {
            return JavaKind.Int;
        }
        return ((HostedType) type).getStorageKind();
    }

    private LLVMTypeRef getLLVMType(JavaKind kind, boolean compressedObjects) {
        switch (kind) {
            case Boolean:
                return builder.booleanType();
            case Byte:
                return builder.byteType();
            case Short:
                return builder.shortType();
            case Char:
                return builder.charType();
            case Int:
                return builder.intType();
            case Float:
                return builder.floatType();
            case Long:
                return builder.longType();
            case Double:
                return builder.doubleType();
            case Object:
                return builder.objectType(compressedObjects);
            case Void:
                return builder.voidType();
            case Illegal:
            default:
                throw shouldNotReachHere("Illegal type");
        }
    }

    private static JavaKind getJavaKind(LLVMTypeRef type) {
        if (LLVMIRBuilder.isBooleanType(type)) {
            return JavaKind.Boolean;
        } else if (LLVMIRBuilder.isByteType(type)) {
            return JavaKind.Byte;
        } else if (LLVMIRBuilder.isShortType(type)) {
            return JavaKind.Short;
        } else if (LLVMIRBuilder.isCharType(type)) {
            return JavaKind.Char;
        } else if (LLVMIRBuilder.isIntType(type)) {
            return JavaKind.Int;
        } else if (LLVMIRBuilder.isLongType(type)) {
            return JavaKind.Long;
        } else if (LLVMIRBuilder.isFloatType(type)) {
            return JavaKind.Float;
        } else if (LLVMIRBuilder.isDoubleType(type)) {
            return JavaKind.Double;
        } else if (LLVMIRBuilder.isObjectType(type)) {
            return JavaKind.Object;
        } else if (LLVMIRBuilder.isVoidType(type)) {
            return JavaKind.Void;
        } else {
            throw shouldNotReachHere("Unknown LLVM type");
        }
    }

    private LLVMTypeRef getLLVMFunctionType(ResolvedJavaMethod method, boolean forMainFunction) {
        return builder.functionType(getLLVMFunctionReturnType(method, forMainFunction), getLLVMFunctionArgTypes(method, forMainFunction));
    }

    LLVMTypeRef getLLVMFunctionPointerType(ResolvedJavaMethod method) {
        return builder.functionPointerType(getLLVMFunctionReturnType(method, false), getLLVMFunctionArgTypes(method, false));
    }

    LLVMTypeRef getLLVMFunctionReturnType(ResolvedJavaMethod method, boolean forMainFunction) {
        ResolvedJavaType returnType = method.getSignature().getReturnType(null).resolve(null);
        LLVMTypeRef llvmReturnType = getLLVMStackType(getTypeKind(returnType, forMainFunction));

        if (LLVMOptions.ReturnSpecialRegs.getValue() && !(forMainFunction && isEntryPoint)) {
            boolean voidReturnType = LLVMIRBuilder.isVoidType(llvmReturnType);
            LLVMTypeRef[] returnTypes = new LLVMTypeRef[SpecialRegister.count() + (voidReturnType ? 0 : 1)];
            for (SpecialRegister reg : SpecialRegister.registers()) {
                returnTypes[reg.index] = builder.wordType();
            }
            if (!voidReturnType) {
                returnTypes[SpecialRegister.count()] = llvmReturnType;
            }
            llvmReturnType = builder.structType(returnTypes);
        }

        return llvmReturnType;
    }

    boolean isVoidReturnType(LLVMTypeRef returnType) {
        if (LLVMOptions.ReturnSpecialRegs.getValue()) {
            return LLVMIRBuilder.countElementTypes(returnType) == SpecialRegister.count();
        }
        return LLVMIRBuilder.isVoidType(returnType);
    }

    private LLVMTypeRef[] getLLVMFunctionArgTypes(ResolvedJavaMethod method, boolean forMainFunction) {
        ResolvedJavaType receiver = method.hasReceiver() ? method.getDeclaringClass() : null;
        JavaType[] javaParameterTypes = method.getSignature().toParameterTypes(receiver);
        LLVMTypeRef[] parameterTypes = Arrays.stream(javaParameterTypes).map(type -> getLLVMStackType(getTypeKind(type.resolve(null), forMainFunction))).toArray(LLVMTypeRef[]::new);
        LLVMTypeRef[] newParameterTypes = parameterTypes;
        if (!isEntryPoint(method) && SpecialRegister.count() > 0) {
            newParameterTypes = new LLVMTypeRef[SpecialRegister.count() + parameterTypes.length];
            for (SpecialRegister reg : SpecialRegister.registers()) {
                newParameterTypes[reg.index] = !LLVMOptions.ReturnSpecialRegs.getValue() && canModifySpecialRegisters(method) ? builder.pointerType(builder.wordType()) : builder.wordType();
            }
            System.arraycopy(parameterTypes, 0, newParameterTypes, SpecialRegister.count(), parameterTypes.length);
        }
        return newParameterTypes;
    }

    /**
     * Creates a new function type based on the given one with the given argument types prepended to
     * the original ones.
     */
    private LLVMTypeRef prependArgumentTypes(LLVMTypeRef functionType, int prefixTypes, LLVMTypeRef... typesToAdd) {
        LLVMTypeRef returnType = LLVMIRBuilder.getReturnType(functionType);
        boolean varargs = LLVMIRBuilder.isFunctionVarArg(functionType);
        LLVMTypeRef[] oldTypes = LLVMIRBuilder.getParamTypes(functionType);

        LLVMTypeRef[] newTypes = new LLVMTypeRef[oldTypes.length + typesToAdd.length];
        System.arraycopy(oldTypes, 0, newTypes, 0, prefixTypes);
        System.arraycopy(typesToAdd, 0, newTypes, prefixTypes, typesToAdd.length);
        System.arraycopy(oldTypes, prefixTypes, newTypes, prefixTypes + typesToAdd.length, oldTypes.length - prefixTypes);

        return builder.functionType(returnType, varargs, newTypes);
    }

    private static boolean isCEnumType(ResolvedJavaType type) {
        return type.isEnum() && GuardedAnnotationAccess.isAnnotationPresent(type, CEnum.class);
    }

    /* Constants */

    @Override
    public Value emitConstant(LIRKind kind, Constant constant) {
        boolean uncompressedObject = isUncompressedObjectKind(kind);
        LLVMTypeRef actualType = uncompressedObject ? builder.objectType(true) : ((LLVMKind) kind.getPlatformKind()).get();
        LLVMValueRef value = emitLLVMConstant(actualType, (JavaConstant) constant);
        Value val = new LLVMConstant(value, constant);
        return uncompressedObject ? emitUncompress(val, ReferenceAccess.singleton().getCompressEncoding(), false) : val;
    }

    @Override
    public Value emitJavaConstant(JavaConstant constant) {
        assert constant.getJavaKind() != JavaKind.Object;
        LLVMValueRef value = emitLLVMConstant(getLLVMType(constant.getJavaKind(), false), constant);
        return new LLVMConstant(value, constant);
    }

    LLVMValueRef emitLLVMConstant(LLVMTypeRef type, JavaConstant constant) {
        switch (getJavaKind(type)) {
            case Boolean:
                return builder.constantBoolean(constant.asBoolean());
            case Byte:
                return builder.constantByte((byte) constant.asInt());
            case Short:
                return builder.constantShort((short) constant.asInt());
            case Char:
                return builder.constantChar((char) constant.asInt());
            case Int:
                return builder.constantInt(constant.asInt());
            case Long:
                return builder.constantLong(constant.asLong());
            case Float:
                return builder.constantFloat(constant.asFloat());
            case Double:
                return builder.constantDouble(constant.asDouble());
            case Object:
                if (constant.isNull()) {
                    return builder.constantNull(builder.objectType(LLVMIRBuilder.isCompressedPointerType(type)));
                } else {
                    return builder.buildLoad(getLLVMPlaceholderForConstant(constant), builder.objectType(LLVMIRBuilder.isCompressedPointerType(type)));
                }
            default:
                throw shouldNotReachHere(dumpTypes("unsupported constant type", type));
        }
    }

    @Override
    public AllocatableValue emitLoadConstant(ValueKind<?> kind, Constant constant) {
        LLVMValueRef value = builder.buildLoad(getLLVMPlaceholderForConstant(constant), ((LLVMKind) kind.getPlatformKind()).get());
        AllocatableValue rawConstant = new LLVMVariable(value);
        if (SubstrateOptions.SpawnIsolates.getValue() && ((LIRKind) kind).isReference(0) && !((LIRKind) kind).isCompressedReference(0)) {
            return (AllocatableValue) emitUncompress(rawConstant, ReferenceAccess.singleton().getCompressEncoding(), false);
        }
        return rawConstant;
    }

    private long nextConstantId = 0L;

    private LLVMValueRef getLLVMPlaceholderForConstant(Constant constant) {
        String symbolName = constants.get(constant);
        boolean uncompressedObject = isUncompressedObjectConstant(constant);
        if (symbolName == null) {
            symbolName = "constant_" + functionName + "#" + nextConstantId++;
            constants.put(constant, symbolName);

            Constant storedConstant = uncompressedObject ? ((SubstrateObjectConstant) constant).compress() : constant;
            DataSectionReference reference = compilationResult.getDataSection().insertData(dataBuilder.createDataItem(storedConstant));
            compilationResult.recordDataPatchWithNote(0, reference, symbolName);
        }
        return builder.getExternalObject(symbolName, isUncompressedObjectConstant(constant));
    }

    private static boolean isUncompressedObjectConstant(Constant constant) {
        return SubstrateOptions.SpawnIsolates.getValue() && constant instanceof SubstrateObjectConstant && !((SubstrateObjectConstant) constant).isCompressed();
    }

    private static boolean isUncompressedObjectKind(LIRKind kind) {
        return SubstrateOptions.SpawnIsolates.getValue() && kind.isReference(0) && !kind.isCompressedReference(0);
    }

    @Override
    public boolean canInlineConstant(Constant constant) {
        /* Forces constants to be emitted as LLVM constants */
        return false;
    }

    @Override
    public boolean mayEmbedConstantLoad(Constant constant) {
        /* Forces constants to be emitted as LLVM constants */
        return false;
    }

    @Override
    public <K extends ValueKind<K>> K toRegisterKind(K kind) {
        /* Registers are handled by LLVM. */
        throw unimplemented("only needed when emitting LIR constants");
    }

    @Override
    public void emitMoveConstant(AllocatableValue dst, Constant src) {
        throw unimplemented("the LLVM backend doesn't need to move constants");
    }

    /* Values */

    @Override
    public Variable newVariable(ValueKind<?> kind) {
        return new LLVMVariable(kind);
    }

    @Override
    public AllocatableValue asAllocatable(Value value) {
        return (AllocatableValue) value;
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
        if (LLVMIRBuilder.isObjectType(destType) && LLVMIRBuilder.isWordType(sourceType)) {
            source = builder.buildIntToPtr(source, destType);
        } else if (LLVMIRBuilder.isWordType(destType) && LLVMIRBuilder.isObjectType(sourceType)) {
            source = builder.buildPtrToInt(source);
        }
        ((LLVMVariable) dst).set(source);
    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value leftVal, Value rightVal, Condition cond, boolean unorderedIsTrue, Value trueVal, Value falseVal) {
        LLVMValueRef condition = builder.buildCompare(cond, getVal(leftVal), getVal(rightVal), unorderedIsTrue);

        LLVMValueRef select;
        LLVMValueRef trueValue = getVal(trueVal);
        LLVMValueRef falseValue = getVal(falseVal);
        if (LLVMVersionChecker.useExplicitSelects() && LLVMIRBuilder.isObjectType(typeOf(trueValue))) {
            select = buildExplicitSelect(condition, trueValue, falseValue);
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
     * Select has to be manually created sometimes because of a bug in LLVM 8 and below which makes
     * it incompatible with statepoint emission in rare cases.
     */
    private LLVMValueRef buildExplicitSelect(LLVMValueRef condition, LLVMValueRef trueVal, LLVMValueRef falseVal) {
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
    public Variable emitByteSwap(Value operand) {
        LLVMValueRef byteSwap = builder.buildBswap(getVal(operand));
        return new LLVMVariable(byteSwap);
    }

    /* Memory */

    @Override
    public void emitMembar(int barriers) {
        builder.buildFence();
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
        LLVMValueRef success = buildCmpxchg(getVal(address), getVal(expectedValue), getVal(newValue), false);
        LLVMValueRef result = builder.buildSelect(success, getVal(trueValue), getVal(falseValue));
        return new LLVMVariable(result);
    }

    @Override
    public Value emitValueCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue) {
        LLVMValueRef result = buildCmpxchg(getVal(address), getVal(expectedValue), getVal(newValue), true);
        return new LLVMVariable(result);
    }

    private LLVMValueRef buildCmpxchg(LLVMValueRef address, LLVMValueRef expectedValue, LLVMValueRef newValue, boolean returnValue) {
        LLVMTypeRef expectedType = LLVMIRBuilder.typeOf(expectedValue);
        LLVMTypeRef newType = LLVMIRBuilder.typeOf(newValue);
        assert LLVMIRBuilder.compatibleTypes(expectedType, newType) : dumpValues("invalid cmpxchg arguments", expectedValue, newValue);

        LLVMValueRef castedAddress = builder.buildBitcast(address, builder.pointerType(expectedType, LLVMIRBuilder.isObjectType(typeOf(address)), false));
        return builder.buildCmpxchg(castedAddress, expectedValue, newValue, returnValue);
    }

    @Override
    public Variable emitReadRegister(Register register, ValueKind<?> kind) {
        LLVMValueRef value;
        if (register.equals(getRegisterConfig().getThreadRegister())) {
            if (isEntryPoint || canModifySpecialRegisters) {
                return new LLVMPendingSpecialRegisterRead(this, SpecialRegister.ThreadPointer);
            }
            value = getSpecialRegister(SpecialRegister.ThreadPointer);
        } else if (register.equals(getRegisterConfig().getHeapBaseRegister())) {
            if (isEntryPoint || canModifySpecialRegisters) {
                return new LLVMPendingSpecialRegisterRead(this, SpecialRegister.HeapBase);
            }
            value = getSpecialRegister(SpecialRegister.HeapBase);
        } else if (register.equals(getRegisterConfig().getFrameRegister())) {
            value = builder.buildReadRegister(builder.register(getRegisterConfig().getFrameRegister().name));
        } else {
            throw VMError.shouldNotReachHere();
        }
        return new LLVMVariable(value);
    }

    @Override
    public void emitWriteRegister(Register dst, Value src, ValueKind<?> kind) {
        if (dst.equals(getRegisterConfig().getThreadRegister())) {
            VMError.guarantee(isEntryPoint || canModifySpecialRegisters, "Can only write to registers in a method where it is expected.");
            if (LLVMOptions.ReturnSpecialRegs.getValue()) {
                setSpecialRegisterValue(SpecialRegister.ThreadPointer, getVal(src));
            } else {
                builder.buildStore(getVal(src), getSpecialRegisterPointer(SpecialRegister.ThreadPointer));
            }
            return;
        } else if (dst.equals(getRegisterConfig().getHeapBaseRegister())) {
            VMError.guarantee(isEntryPoint || canModifySpecialRegisters, "Can only write to registers in a method where it is expected.");
            if (LLVMOptions.ReturnSpecialRegs.getValue()) {
                setSpecialRegisterValue(SpecialRegister.HeapBase, getVal(src));
            } else {
                builder.buildStore(getVal(src), getSpecialRegisterPointer(SpecialRegister.HeapBase));
            }
            return;
        }
        throw VMError.shouldNotReachHere();
    }

    @Override
    public Variable load(Value value) {
        LLVMValueRef load = builder.buildPtrToInt(getVal(value));
        return new LLVMVariable(load);
    }

    @Override
    public void emitPrefetchAllocate(Value address) {
        builder.buildPrefetch(getVal(address));
    }

    @Override
    public Value emitCompress(Value pointer, CompressEncoding encoding, boolean nonNull) {
        LLVMValueRef heapBase = getSpecialRegister(SpecialRegister.HeapBase);
        return new LLVMVariable(builder.buildCompress(getVal(pointer), heapBase, nonNull, encoding.getShift()));
    }

    @Override
    public Value emitUncompress(Value pointer, CompressEncoding encoding, boolean nonNull) {
        LLVMValueRef heapBase = getSpecialRegister(SpecialRegister.HeapBase);
        return new LLVMVariable(builder.buildUncompress(getVal(pointer), heapBase, nonNull, encoding.getShift()));
    }

    @Override
    public VirtualStackSlot allocateStackSlots(int slots) {
        builder.positionAtStart();
        LLVMValueRef alloca = builder.buildArrayAlloca(builder.wordType(), slots);
        builder.positionAtEnd(getBlockEnd(currentBlock));

        return new LLVMStackSlot(alloca);
    }

    @Override
    public Variable emitAddress(AllocatableValue stackslot) {
        if (stackslot instanceof LLVMStackSlot) {
            return new LLVMVariable(builder.buildPtrToInt(getVal(stackslot)));
        }
        throw shouldNotReachHere("Unknown address type");
    }

    void allocateRegisterSlots() {
        if (!isEntryPoint) {
            /* Non-entry point methods get the stack slots of their caller as argument. */
            return;
        }

        for (SpecialRegister reg : SpecialRegister.registers()) {
            stackSlots[reg.index] = builder.buildAlloca(builder.wordType());
        }
    }

    @Override
    public Value emitReadCallerStackPointer(Stamp wordStamp) {
        LLVMValueRef basePointer = builder.buildFrameAddress(builder.constantInt(0));
        LLVMValueRef callerSP = builder.buildAdd(builder.buildPtrToInt(basePointer), builder.constantLong(16));
        return new LLVMVariable(callerSP);
    }

    @Override
    public Value emitReadReturnAddress(Stamp wordStamp, int returnAddressSize) {
        LLVMValueRef returnAddress = builder.buildReturnAddress(builder.constantInt(0));
        return new LLVMVariable(builder.buildPtrToInt(returnAddress));
    }

    /* Control flow */

    static final AtomicLong nextPatchpointId = new AtomicLong(0);

    LLVMValueRef buildStatepointCall(LLVMValueRef callee, boolean nativeABI, long statepointId, LLVMValueRef... args) {
        LLVMValueRef result;
        result = builder.buildCall(callee, args);
        builder.setCallSiteAttribute(result, Attribute.StatepointID, Long.toString(statepointId));

        if (!nativeABI && LLVMOptions.ReturnSpecialRegs.getValue()) {
            for (SpecialRegister reg : SpecialRegister.registers()) {
                setSpecialRegisterValue(reg, builder.buildExtractValue(result, reg.index));
            }
            int numReturnValues = LLVMIRBuilder.countElementTypes(typeOf(result));
            return numReturnValues > SpecialRegister.count() ? builder.buildExtractValue(result, SpecialRegister.count()) : result;
        }
        return result;
    }

    LLVMValueRef buildStatepointInvoke(LLVMValueRef callee, boolean nativeABI, LLVMBasicBlockRef successor, LLVMBasicBlockRef handler, long statepointId, LLVMValueRef... args) {
        LLVMBasicBlockRef successorBlock;
        LLVMBasicBlockRef handlerBlock;
        if (!nativeABI && LLVMOptions.ReturnSpecialRegs.getValue()) {
            successorBlock = builder.appendBasicBlock(currentBlock.toString() + "_invoke_successor");
            handlerBlock = builder.appendBasicBlock(currentBlock.toString() + "_invoke_handler");
            splitBlockEndMap.put(currentBlock, successorBlock);
        } else {
            successorBlock = successor;
            handlerBlock = handler;
        }

        LLVMValueRef result = builder.buildInvoke(callee, successorBlock, handlerBlock, args);
        builder.setCallSiteAttribute(result, Attribute.StatepointID, Long.toString(statepointId));

        if (!nativeABI && LLVMOptions.ReturnSpecialRegs.getValue()) {
            builder.positionAtEnd(handlerBlock);
            builder.buildLandingPad();
            for (SpecialRegister reg : SpecialRegister.registers()) {
                setHandlerSpecialRegisterValue(reg, getSpecialRegisterValue(reg));
            }
            builder.buildBranch(handler);

            builder.positionAtEnd(successorBlock);
            int numReturnValues = LLVMIRBuilder.countElementTypes(typeOf(result));
            for (SpecialRegister reg : SpecialRegister.registers()) {
                assert reg.index < numReturnValues;
                setSpecialRegisterValue(reg, builder.buildExtractValue(result, reg.index));
            }
            result = numReturnValues > SpecialRegister.count() ? builder.buildExtractValue(result, SpecialRegister.count()) : result;
            builder.buildBranch(successor);
        }

        return result;
    }

    @Override
    public Variable emitForeignCall(ForeignCallLinkage linkage, LIRFrameState state, Value... arguments) {
        return emitForeignCall(linkage, state, null, null, arguments);
    }

    public Variable emitForeignCall(ForeignCallLinkage linkage, LIRFrameState state, LLVMBasicBlockRef successor, LLVMBasicBlockRef handler, Value... arguments) {
        ResolvedJavaMethod targetMethod = ((SnippetRuntime.SubstrateForeignCallDescriptor) linkage.getDescriptor()).findMethod(getMetaAccess());

        state.initDebugInfo(null, false);
        long patchpointId = nextPatchpointId.getAndIncrement();
        compilationResult.recordCall(NumUtil.safeToInt(patchpointId), 0, targetMethod, state.debugInfo(), true);

        LLVMValueRef callee = getFunction(targetMethod);
        LLVMValueRef[] args = Arrays.stream(arguments).map(LLVMUtils::getVal).toArray(LLVMValueRef[]::new);
        CallingConvention.Type callType = ((SubstrateCallingConvention) linkage.getOutgoingCallingConvention()).getType();
        LLVMValueRef[] callArguments = getCallArguments(args, callType, targetMethod);

        LLVMValueRef call;
        boolean nativeABI = ((SubstrateCallingConventionType) callType).nativeABI;
        if (successor == null && handler == null) {
            call = buildStatepointCall(callee, nativeABI, patchpointId, callArguments);
        } else {
            assert successor != null && handler != null;
            call = buildStatepointInvoke(callee, nativeABI, successor, handler, patchpointId, callArguments);
        }

        return (isVoidReturnType(getLLVMFunctionReturnType(targetMethod, false))) ? null : new LLVMVariable(call);
    }

    LLVMValueRef[] getCallArguments(LLVMValueRef[] args, CallingConvention.Type callType, ResolvedJavaMethod targetMethod) {
        LLVMValueRef[] newArgs = args;

        if (!((SubstrateCallingConventionType) callType).nativeABI && SpecialRegister.hasRegisters()) {
            newArgs = new LLVMValueRef[SpecialRegister.count() + args.length];
            for (SpecialRegister reg : SpecialRegister.registers()) {
                newArgs[reg.index] = getSpecialRegisterArgument(reg, targetMethod);
            }
            System.arraycopy(args, 0, newArgs, SpecialRegister.count(), args.length);
        }
        return newArgs;
    }

    LLVMTypeRef[] getUnknownCallArgumentTypes(LLVMTypeRef[] types, CallingConvention.Type callType) {
        LLVMTypeRef[] newTypes = types;

        if (!((SubstrateCallingConventionType) callType).nativeABI && SpecialRegister.count() > 0) {
            newTypes = new LLVMTypeRef[SpecialRegister.count() + types.length];
            for (SpecialRegister reg : SpecialRegister.registers()) {
                newTypes[reg.index] = builder.wordType();
            }
            System.arraycopy(types, 0, newTypes, SpecialRegister.count(), types.length);
        }
        return newTypes;
    }

    public static final String JNI_WRAPPER_BASE_NAME = "__llvm_jni_wrapper_";

    /*
     * Calling a native function from Java code requires filling the JavaFrameAnchor with the return
     * address of the call. This wrapper allows this by creating an intermediary call frame from
     * which the return address can be accessed. The parameters to this wrapper are the anchor, the
     * native callee, and the arguments to the callee.
     */
    LLVMValueRef createJNIWrapper(LLVMValueRef callee, boolean nativeABI, int numArgs, int anchorIPOffset) {
        LLVMTypeRef calleeType = LLVMIRBuilder.getElementType(LLVMIRBuilder.typeOf(callee));
        String wrapperName = JNI_WRAPPER_BASE_NAME + LLVMIRBuilder.intrinsicType(calleeType) + (nativeABI ? "_native" : "");

        LLVMValueRef transitionWrapper = builder.getNamedFunction(wrapperName);
        if (transitionWrapper == null) {
            try (LLVMIRBuilder tempBuilder = new LLVMIRBuilder(builder)) {
                LLVMTypeRef wrapperType = prependArgumentTypes(calleeType, nativeABI ? 0 : SpecialRegister.count(), tempBuilder.rawPointerType(), LLVMIRBuilder.typeOf(callee));
                transitionWrapper = tempBuilder.addFunction(wrapperName, wrapperType);
                LLVMIRBuilder.setLinkage(transitionWrapper, LinkageType.LinkOnce);
                tempBuilder.setGarbageCollector(transitionWrapper, GCStrategy.CompressedPointers);
                tempBuilder.setFunctionAttribute(transitionWrapper, Attribute.NoInline);

                LLVMBasicBlockRef block = tempBuilder.appendBasicBlock(transitionWrapper, "main");
                tempBuilder.positionAtEnd(block);

                LLVMValueRef anchor = LLVMIRBuilder.getParam(transitionWrapper, 0 + (nativeABI ? 0 : SpecialRegister.count()));
                LLVMValueRef lastIPAddr = tempBuilder.buildGEP(anchor, tempBuilder.constantInt(anchorIPOffset));
                LLVMValueRef callIP = tempBuilder.buildReturnAddress(tempBuilder.constantInt(0));
                LLVMValueRef castedLastIPAddr = tempBuilder.buildBitcast(lastIPAddr, tempBuilder.pointerType(tempBuilder.rawPointerType()));
                tempBuilder.buildStore(callIP, castedLastIPAddr);

                LLVMValueRef[] args = new LLVMValueRef[numArgs];
                for (int i = 0; i < numArgs; ++i) {
                    if (!nativeABI && i < SpecialRegister.count()) {
                        args[i] = LLVMIRBuilder.getParam(transitionWrapper, i);
                    } else {
                        args[i] = LLVMIRBuilder.getParam(transitionWrapper, i + 2);
                    }
                }
                LLVMValueRef target = LLVMIRBuilder.getParam(transitionWrapper, 1 + (nativeABI ? 0 : SpecialRegister.count()));
                LLVMValueRef ret = tempBuilder.buildCall(target, args);
                tempBuilder.setCallSiteAttribute(ret, Attribute.GCLeafFunction);

                if (LLVMIRBuilder.isVoidType(LLVMIRBuilder.getReturnType(calleeType))) {
                    tempBuilder.buildRetVoid();
                } else {
                    tempBuilder.buildRet(ret);
                }
            }
        }
        return transitionWrapper;
    }

    void createJNITrampoline(RegisterValue threadArg, int threadIsolateOffset, RegisterValue methodIdArg, int methodObjEntryPointOffset) {
        builder.setFunctionAttribute(Attribute.Naked);

        LLVMBasicBlockRef block = builder.appendBasicBlock("main");
        builder.positionAtEnd(block);

        long startPatchpointId = LLVMGenerator.nextPatchpointId.getAndIncrement();
        builder.buildStackmap(builder.constantLong(startPatchpointId));
        compilationResult.recordInfopoint(NumUtil.safeToInt(startPatchpointId), null, InfopointReason.METHOD_START);

        LLVMValueRef jumpAddressAddress;
        if (SubstrateOptions.SpawnIsolates.getValue()) {
            LLVMValueRef thread = buildInlineGetRegister(threadArg.getRegister().name);
            LLVMValueRef heapBaseAddress = builder.buildGEP(builder.buildIntToPtr(thread, builder.rawPointerType()), builder.constantInt(threadIsolateOffset));
            LLVMValueRef heapBase = builder.buildLoad(heapBaseAddress, builder.rawPointerType());
            LLVMValueRef methodId = buildInlineGetRegister(methodIdArg.getRegister().name);
            LLVMValueRef methodBase = builder.buildGEP(builder.buildIntToPtr(heapBase, builder.rawPointerType()), builder.buildPtrToInt(methodId));
            jumpAddressAddress = builder.buildGEP(methodBase, builder.constantInt(methodObjEntryPointOffset));
        } else {
            LLVMValueRef methodBase = buildInlineGetRegister(methodIdArg.getRegister().name);
            jumpAddressAddress = builder.buildGEP(builder.buildIntToPtr(methodBase, builder.rawPointerType()), builder.constantInt(methodObjEntryPointOffset));
        }
        LLVMValueRef jumpAddress = builder.buildLoad(jumpAddressAddress, builder.rawPointerType());
        buildInlineJump(jumpAddress);
        builder.buildUnreachable();
    }

    @Override
    public void emitReturn(JavaKind javaKind, Value input) {
        if (javaKind == JavaKind.Void) {
            debugInfoPrinter.printRetVoid();
            if (LLVMOptions.ReturnSpecialRegs.getValue() && !isEntryPoint) {
                LLVMTypeRef[] retTypes = new LLVMTypeRef[SpecialRegister.count()];
                LLVMValueRef[] retValues = new LLVMValueRef[SpecialRegister.count()];
                for (SpecialRegister reg : SpecialRegister.registers()) {
                    retTypes[reg.index] = builder.wordType();
                    retValues[reg.index] = getSpecialRegisterValue(reg);
                }
                LLVMValueRef retStruct = builder.constantNull(builder.structType(retTypes));
                for (int i = 0; i < retValues.length; ++i) {
                    retStruct = builder.buildInsertValue(retStruct, i, retValues[i]);
                }
                builder.buildRet(retStruct);
            } else {
                builder.buildRetVoid();
            }
        } else {
            debugInfoPrinter.printRet(javaKind, input);
            LLVMValueRef retVal = getVal(input);
            if (javaKind == JavaKind.Int) {
                assert LLVMIRBuilder.isIntegerType(typeOf(retVal));
                retVal = arithmetic.emitIntegerConvert(retVal, builder.intType());
            } else if (returnsEnum && javaKind == FrameAccess.getWordKind()) {
                /*
                 * An enum value is represented by a long in the function body, but is returned as
                 * an object (CEnum values are returned as an int)
                 */
                LLVMValueRef result;
                if (returnsCEnum) {
                    result = builder.buildTrunc(retVal, JavaKind.Int.getBitCount());
                } else {
                    result = builder.buildIntToPtr(retVal, builder.objectType(false));
                }
                retVal = result;
            }

            if (LLVMOptions.ReturnSpecialRegs.getValue() && !isEntryPoint) {
                LLVMTypeRef[] retTypes = new LLVMTypeRef[SpecialRegister.count() + 1];
                LLVMValueRef[] retValues = new LLVMValueRef[SpecialRegister.count() + 1];
                for (SpecialRegister reg : SpecialRegister.registers()) {
                    retTypes[reg.index] = builder.wordType();
                    retValues[reg.index] = getSpecialRegisterValue(reg);
                }
                retTypes[SpecialRegister.count()] = LLVMIRBuilder.typeOf(retVal);
                retValues[SpecialRegister.count()] = retVal;

                LLVMValueRef retStruct = builder.constantNull(builder.structType(retTypes));
                for (int i = 0; i < retValues.length; ++i) {
                    retStruct = builder.buildInsertValue(retStruct, i, retValues[i]);
                }
                retVal = retStruct;
            }
            builder.buildRet(retVal);
        }
    }

    @Override
    public void emitJump(LabelRef label) {
        builder.buildBranch(getBlock((Block) label.getTargetBlock()));
    }

    @Override
    public void emitDeadEnd() {
        builder.buildUnreachable();
    }

    @Override
    public void emitBlackhole(Value operand) {
        builder.buildStackmap(builder.constantLong(LLVMStackMapInfo.DEFAULT_PATCHPOINT_ID), getVal(operand));
    }

    @Override
    public void emitPause() {
        // this will be implemented as part of issue #1126. For now, we just do nothing.
        // throw unimplemented();
    }

    /* Inline assembly */

    private void buildInlineJump(LLVMValueRef address) {
        LLVMTypeRef inlineAsmType = builder.functionType(builder.voidType(), builder.rawPointerType());
        String asmSnippet = LLVMTargetSpecific.get().getJumpInlineAsm();
        InlineAssemblyConstraint inputConstraint = new InlineAssemblyConstraint(Type.Input, Location.register());

        LLVMValueRef jump = builder.buildInlineAsm(inlineAsmType, asmSnippet, true, false, inputConstraint);
        LLVMValueRef call = builder.buildCall(jump, address);
        builder.setCallSiteAttribute(call, Attribute.GCLeafFunction);
    }

    private LLVMValueRef buildInlineGetRegister(String registerName) {
        LLVMTypeRef inlineAsmType = builder.functionType(builder.rawPointerType());
        String asmSnippet = LLVMTargetSpecific.get().getRegisterInlineAsm(registerName);
        InlineAssemblyConstraint outputConstraint = new InlineAssemblyConstraint(Type.Output, Location.namedRegister(LLVMTargetSpecific.get().getLLVMRegisterName(registerName)));

        LLVMValueRef getRegister = builder.buildInlineAsm(inlineAsmType, asmSnippet, false, false, outputConstraint);
        LLVMValueRef call = builder.buildCall(getRegister);
        builder.setCallSiteAttribute(call, Attribute.GCLeafFunction);
        return call;
    }

    /* Special registers */

    private void setSpecialRegisterValue(SpecialRegister reg, LLVMValueRef value) {
        assert getInitialSpecialRegisterValue(reg, currentBlock) != null;
        specialRegValues.computeIfAbsent(currentBlock, (b) -> new LLVMValueRef[SpecialRegister.count()])[reg.index] = value;
    }

    void setInitialSpecialRegisterValue(SpecialRegister reg, LLVMValueRef value) {
        initialSpecialRegValues.computeIfAbsent(currentBlock, (b) -> new LLVMValueRef[SpecialRegister.count()])[reg.index] = value;
        setSpecialRegisterValue(reg, value);
    }

    private void setHandlerSpecialRegisterValue(SpecialRegister reg, LLVMValueRef value) {
        handlerSpecialRegValues.computeIfAbsent(currentBlock, (b) -> new LLVMValueRef[SpecialRegister.count()])[reg.index] = value;
    }

    public LLVMValueRef getSpecialRegister(SpecialRegister register) {
        if (LLVMOptions.ReturnSpecialRegs.getValue()) {
            return getSpecialRegisterValue(register);
        }

        LLVMValueRef specialRegister;
        if (isEntryPoint || canModifySpecialRegisters) {
            LLVMValueRef specialRegisterPointer = getSpecialRegisterPointer(register);
            specialRegister = builder.buildLoad(specialRegisterPointer, builder.wordType());
        } else {
            specialRegister = builder.getFunctionParam(register.index);
        }
        return specialRegister;
    }

    private LLVMValueRef getSpecialRegisterPointer(SpecialRegister register) {
        assert !LLVMOptions.ReturnSpecialRegs.getValue();
        if (isEntryPoint) {
            return stackSlots[register.index];
        } else if (canModifySpecialRegisters) {
            return builder.getFunctionParam(register.index);
        } else {
            throw VMError.shouldNotReachHere();
        }
    }

    private LLVMValueRef getSpecialRegisterArgument(SpecialRegister register, ResolvedJavaMethod targetMethod) {
        if (LLVMOptions.ReturnSpecialRegs.getValue()) {
            return getSpecialRegisterValue(register);
        }

        LLVMValueRef specialRegisterArg;
        if (targetMethod != null && canModifySpecialRegisters(targetMethod)) {
            if (isEntryPoint || canModifySpecialRegisters) {
                specialRegisterArg = getSpecialRegisterPointer(register);
            } else {
                /*
                 * This means that an entry point method is called directly from Java code. We only
                 * accept this in the case of a method that doesn't do anything Java-related, and
                 * therefore doesn't need the actual value of its special registers.
                 */
                assert GuardedAnnotationAccess.isAnnotationPresent(targetMethod, Uninterruptible.class);
                specialRegisterArg = builder.constantNull(builder.pointerType(builder.wordType()));
            }
        } else if (isEntryPoint || canModifySpecialRegisters) {
            specialRegisterArg = builder.buildLoad(getSpecialRegisterPointer(register), builder.wordType());
        } else {
            specialRegisterArg = getSpecialRegister(register);
        }

        return specialRegisterArg;
    }

    LLVMValueRef getSpecialRegisterValue(SpecialRegister reg) {
        return getSpecialRegisterValue(reg, currentBlock);
    }

    LLVMValueRef getSpecialRegisterValue(SpecialRegister reg, Block block) {
        return specialRegValues.get(block)[reg.index];
    }

    LLVMValueRef getInitialSpecialRegisterValue(SpecialRegister reg, Block block) {
        if (!initialSpecialRegValues.containsKey(block)) {
            return null;
        }
        return initialSpecialRegValues.get(block)[reg.index];
    }

    LLVMValueRef getHandlerSpecialRegisterValue(SpecialRegister reg, Block block) {
        return handlerSpecialRegValues.get(block)[reg.index];
    }

    /*
     * Special registers (thread pointer and heap base) are implemented in the LLVM backend by
     * passing them as arguments to functions. As these registers can be modified in entry point
     * methods, these hold the values of these registers in stack slots, which get passed to callees
     * that can potentially modify them and hold the updated version of the "register" upon return.
     */
    public enum SpecialRegister {
        ThreadPointer(SubstrateOptions.MultiThreaded.getValue()),
        HeapBase(SubstrateOptions.SpawnIsolates.getValue());

        private static final int presentCount;
        private static final SpecialRegister[] presentRegisters;
        static {
            int index = 0;
            for (SpecialRegister reg : values()) {
                if (reg.isPresent) {
                    reg.index = index;
                    index++;
                }
            }
            presentCount = index;

            presentRegisters = new SpecialRegister[presentCount];
            for (SpecialRegister reg : values()) {
                if (reg.isPresent) {
                    presentRegisters[reg.index] = reg;
                }
            }
        }

        private boolean isPresent;
        private int index;

        SpecialRegister(boolean isPresent) {
            this.isPresent = isPresent;
        }

        int getIndex() {
            return index;
        }

        static boolean hasRegisters() {
            return presentCount > 0;
        }

        static int count() {
            return presentCount;
        }

        static SpecialRegister[] registers() {
            return presentRegisters.clone();
        }
    }

    /* Unimplemented */

    @Override
    public LIRGenerationResult getResult() {
        throw unimplemented("the LLVM backend doesn't produce an LIRGenerationResult");
    }

    @Override
    public MoveFactory getMoveFactory() {
        throw unimplemented("the LLVM backend doesn't use LIR moves");
    }

    @Override
    public MoveFactory getSpillMoveFactory() {
        throw unimplemented("the LLVM backend doesn't use LIR moves");
    }

    @Override
    public void emitNullCheck(Value address, LIRFrameState state) {
        throw unimplemented("the LLVM backend doesn't support deoptimization");
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value failedSpeculation, LIRFrameState state) {
        throw unimplemented("the LLVM backend doesn't support deoptimization");
    }

    @Override
    public void emitFarReturn(AllocatableValue result, Value sp, Value ip, boolean fromMethodWithCalleeSavedRegisters) {
        throw unimplemented("the LLVM backend delegates exception handling to libunwind");
    }

    @Override
    public void emitUnwind(Value operand) {
        throw shouldNotReachHere("handled by lowering");
    }

    @Override
    public void emitVerificationMarker(Object marker) {
        /*
         * No-op, for now we do not have any verification of the LLVM IR that requires the markers.
         */
    }

    @Override
    public void emitInstructionSynchronizationBarrier() {
        throw unimplemented("the LLVM backend doesn't support instruction synchronization");
    }

    @Override
    public <I extends LIRInstruction> I append(I op) {
        throw unimplemented("the LLVM backend doesn't support LIR instructions");
    }

    @Override
    public void emitSpeculationFence() {
        throw unimplemented("the LLVM backend doesn't support speculative execution attack mitigation");
    }

    @Override
    public LIRInstruction createBenchmarkCounter(String name, String group, Value increment) {
        throw unimplemented("the LLVM backend doesn't support diagnostic operations");
    }

    @Override
    public LIRInstruction createMultiBenchmarkCounter(String[] names, String[] groups, Value[] increments) {
        throw unimplemented("the LLVM backend doesn't support diagnostic operations");
    }

    @Override
    public StandardOp.ZapRegistersOp createZapRegisters(Register[] zappedRegisters, JavaConstant[] zapValues) {
        throw unimplemented("the LLVM backend doesn't support diagnostic operations");
    }

    @Override
    public StandardOp.ZapRegistersOp createZapRegisters(Register[] zappedRegisters) {
        throw unimplemented("the LLVM backend doesn't support diagnostic operations");
    }

    @Override
    public StandardOp.ZapRegistersOp createZapRegisters() {
        throw unimplemented("the LLVM backend doesn't support diagnostic operations");
    }

    @Override
    public LIRInstruction createZapArgumentSpace(StackSlot[] zappedStack, JavaConstant[] zapValues) {
        throw unimplemented("the LLVM backend doesn't support diagnostic operations");
    }

    @Override
    public LIRInstruction zapArgumentSpace() {
        throw unimplemented("the LLVM backend doesn't support diagnostic operations");
    }

    /* Arithmetic */

    public static class ArithmeticLLVMGenerator implements ArithmeticLIRGeneratorTool, LLVMIntrinsicGenerator {
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
            LLVMValueRef shiftedMul = shift.apply(mul, builder.constantInteger(baseBits, extendedBits));
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
            LLVMValueRef valA = getVal(a);
            LLVMValueRef shl = builder.buildShl(valA, emitIntegerConvert(getVal(b), typeOf(valA)));
            return new LLVMVariable(shl);
        }

        @Override
        public Value emitShr(Value a, Value b) {
            LLVMValueRef valA = getVal(a);
            LLVMValueRef shr = builder.buildShr(valA, emitIntegerConvert(getVal(b), typeOf(valA)));
            return new LLVMVariable(shr);
        }

        @Override
        public Value emitUShr(Value a, Value b) {
            LLVMValueRef valA = getVal(a);
            LLVMValueRef ushr = builder.buildUShr(valA, emitIntegerConvert(getVal(b), typeOf(valA)));
            return new LLVMVariable(ushr);
        }

        private LLVMValueRef emitIntegerConvert(LLVMValueRef value, LLVMTypeRef type) {
            int fromBits = LLVMIRBuilder.integerTypeWidth(typeOf(value));
            int toBits = LLVMIRBuilder.integerTypeWidth(type);
            if (fromBits < toBits) {
                return (fromBits == 1) ? builder.buildZExt(value, toBits) : builder.buildSExt(value, toBits);
            }
            if (fromBits > toBits) {
                return builder.buildTrunc(value, toBits);
            }
            return value;
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
        public Value emitMathCeil(Value input) {
            LLVMValueRef ceil = builder.buildCeil(getVal(input));
            return new LLVMVariable(ceil);
        }

        @Override
        public Value emitMathFloor(Value input) {
            LLVMValueRef floor = builder.buildFloor(getVal(input));
            return new LLVMVariable(floor);
        }

        @Override
        public Value emitCountLeadingZeros(Value input) {
            LLVMValueRef ctlz = builder.buildCtlz(getVal(input));
            ctlz = emitIntegerConvert(ctlz, builder.intType());
            return new LLVMVariable(ctlz);
        }

        @Override
        public Value emitCountTrailingZeros(Value input) {
            LLVMValueRef cttz = builder.buildCttz(getVal(input));
            cttz = emitIntegerConvert(cttz, builder.intType());
            return new LLVMVariable(cttz);
        }

        @Override
        public Value emitBitCount(Value operand) {
            LLVMValueRef op = getVal(operand);
            LLVMValueRef answer = builder.buildCtpop(op);
            answer = emitIntegerConvert(answer, builder.intType());
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
        public Value emitFusedMultiplyAdd(Value a, Value b, Value c) {
            LLVMValueRef fma = builder.buildFma(getVal(a), getVal(b), getVal(c));
            return new LLVMVariable(fma);
        }

        @Override
        public Value emitMathMin(Value a, Value b) {
            LLVMValueRef min = builder.buildMin(getVal(a), getVal(b));
            return new LLVMVariable(min);
        }

        @Override
        public Value emitMathMax(Value a, Value b) {
            LLVMValueRef max = builder.buildMax(getVal(a), getVal(b));
            return new LLVMVariable(max);
        }

        @Override
        public Value emitMathCopySign(Value a, Value b) {
            LLVMValueRef copySign = builder.buildCopysign(getVal(a), getVal(b));
            return new LLVMVariable(copySign);
        }

        @Override
        public Variable emitLoad(LIRKind kind, Value address, LIRFrameState state) {
            LLVMValueRef load = builder.buildLoad(getVal(address), getType(kind));
            return new LLVMVariable(load);
        }

        @Override
        public void emitStore(ValueKind<?> kind, Value addr, Value input, LIRFrameState state) {
            LLVMValueRef address = getVal(addr);
            LLVMValueRef value = getVal(input);
            LLVMTypeRef addressType = LLVMIRBuilder.typeOf(address);
            LLVMTypeRef valueType = LLVMIRBuilder.typeOf(value);
            LLVMValueRef castedValue = value;
            if (LLVMIRBuilder.isObjectType(valueType) && !LLVMIRBuilder.isObjectType(addressType)) {
                valueType = builder.rawPointerType();
                castedValue = builder.buildAddrSpaceCast(value, builder.rawPointerType());
            }
            LLVMValueRef castedAddress = builder.buildBitcast(address, builder.pointerType(valueType, LLVMIRBuilder.isObjectType(addressType), false));
            builder.buildStore(castedValue, castedAddress);
        }

        @Override
        public Variable emitVolatileLoad(LIRKind kind, Value address, LIRFrameState state) {
            throw GraalError.shouldNotReachHere();
        }

        @Override
        public void emitVolatileStore(ValueKind<?> kind, Value address, Value input, LIRFrameState state) {
            throw GraalError.shouldNotReachHere();
        }
    }

    static class DebugInfoPrinter {
        private LLVMGenerator gen;
        private LLVMIRBuilder builder;
        private int debugLevel;

        private LLVMValueRef indentCounter;
        private LLVMValueRef spacesVector;

        DebugInfoPrinter(LLVMGenerator gen, int debugLevel) {
            this.gen = gen;
            this.builder = gen.getBuilder();
            this.debugLevel = debugLevel;

            if (debugLevel >= DebugLevel.Function.level) {
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

        void printFunction(StructuredGraph graph, NodeLLVMBuilder nodeBuilder) {
            if (debugLevel >= DebugLevel.Function.level) {
                indent();
                List<JavaKind> printfTypes = new ArrayList<>();
                List<LLVMValueRef> printfArgs = new ArrayList<>();

                for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                    printfTypes.add(param.getStackKind());
                    printfArgs.add(getVal(nodeBuilder.operand(param)));
                }

                String functionName = gen.getFunctionName();
                emitPrintf("In " + functionName, printfTypes.toArray(new JavaKind[0]), printfArgs.toArray(new LLVMValueRef[0]));
            }
        }

        void printBlock(Block block) {
            if (debugLevel >= DebugLevel.Block.level) {
                emitPrintf("In block " + block.toString());
            }
        }

        void printNode(ValueNode valueNode) {
            if (debugLevel >= DebugLevel.Node.level) {
                emitPrintf(valueNode.toString());
            }
        }

        void printIndirectCall(ResolvedJavaMethod targetMethod, LLVMValueRef callee) {
            if (debugLevel >= DebugLevel.Node.level) {
                emitPrintf("Indirect call to " + ((targetMethod != null) ? targetMethod.getName() : "[unknown]"), new JavaKind[]{JavaKind.Object}, new LLVMValueRef[]{callee});
            }
        }

        void printBreakpoint() {
            if (debugLevel >= DebugLevel.Function.level) {
                emitPrintf("breakpoint");
            }
        }

        void printRetVoid() {
            if (debugLevel >= DebugLevel.Function.level) {
                emitPrintf("Return");
                deindent();
            }
        }

        void printRet(JavaKind kind, Value input) {
            if (debugLevel >= DebugLevel.Function.level) {
                emitPrintf("Return", new JavaKind[]{kind}, new LLVMValueRef[]{getVal(input)});
                deindent();
            }
        }

        void setValueName(LLVMValueWrapper value, ValueNode node) {
            if (debugLevel >= DebugLevel.Node.level && node.getStackKind() != JavaKind.Void) {
                builder.setValueName(value.get(), node.toString());
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

        private void emitPrintf(String base) {
            emitPrintf(base, new JavaKind[0], new LLVMValueRef[0]);
        }

        private void emitPrintf(String base, JavaKind[] types, LLVMValueRef[] values) {
            LLVMValueRef printf = builder.getFunction("printf", builder.functionType(builder.intType(), true, builder.rawPointerType()));

            if (debugLevel >= DebugLevel.Function.level) {
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

        public enum DebugLevel {
            Function(1),
            Block(2),
            Node(3);

            private int level;

            DebugLevel(int level) {
                this.level = level;
            }
        }
    }
}
