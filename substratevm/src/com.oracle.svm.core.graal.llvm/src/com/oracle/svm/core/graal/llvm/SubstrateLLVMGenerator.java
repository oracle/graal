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

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static com.oracle.svm.core.util.VMError.unimplemented;
import static org.graalvm.compiler.core.llvm.LLVMUtils.getVal;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.llvm.LLVMGenerationResult;
import org.graalvm.compiler.core.llvm.LLVMGenerator;
import org.graalvm.compiler.core.llvm.LLVMIRBuilder;
import org.graalvm.compiler.core.llvm.LLVMUtils.LLVMKindTool;
import org.graalvm.compiler.core.llvm.LLVMUtils.LLVMVariable;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.util.GuardedAnnotationAccess;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointBuiltins;
import com.oracle.svm.core.c.function.CEntryPointNativeFunctions;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.graal.code.SubstrateCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.code.SubstrateLIRGenerator;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.graal.snippets.CEntryPointSnippets;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMContextRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMTypeRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMValueRef;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public class SubstrateLLVMGenerator extends LLVMGenerator implements SubstrateLIRGenerator {
    /*
     * Special registers (thread pointer and heap base) are implemented in the LLVM backend by
     * passing them as arguments to functions. As these registers can be modified in entry point
     * methods, these hold the values of these registers in stack slots, which get passed to callees
     * that can potentially modify them and hold the updated version of the "register" upon return.
     */
    private LLVMValueRef[] registerStackSlots = new LLVMValueRef[LLVMFeature.SPECIAL_REGISTER_COUNT];

    private final boolean isEntryPoint;
    private List<String> aliases;
    private final boolean canModifySpecialRegisters;
    private final boolean returnsCEnum;

    SubstrateLLVMGenerator(Providers providers, LLVMGenerationResult generationResult, ResolvedJavaMethod method, LLVMContextRef context, int debugLevel) {
        super(providers, generationResult, method, new LLVMIRBuilder(SubstrateUtil.uniqueShortName(method), context, SubstrateOptions.SpawnIsolates.getValue()),
                        new LLVMKindTool(context), debugLevel, LLVMFeature.useExplicitSelects());
        this.isEntryPoint = isEntryPoint(method);
        this.canModifySpecialRegisters = canModifySpecialRegisters(method);

        aliases = new ArrayList<>();
        if (isEntryPoint) {
            aliases.add(SubstrateUtil.mangleName(builder.getFunctionName()));

            Object entryPointData = ((HostedMethod) method).getWrapped().getEntryPointData();
            if (entryPointData instanceof CEntryPointData) {
                CEntryPointData cEntryPointData = (CEntryPointData) entryPointData;
                if (cEntryPointData.getPublishAs() != CEntryPointOptions.Publish.NotPublished) {
                    String entryPointSymbolName = cEntryPointData.getSymbolName();
                    assert !entryPointSymbolName.isEmpty();
                    aliases.add(entryPointSymbolName);
                }
            }
        }

        ResolvedJavaType returnType = method.getSignature().getReturnType(null).resolve(null);
        this.returnsCEnum = returnType.isEnum() && GuardedAnnotationAccess.isAnnotationPresent(returnType, CEnum.class);
    }

    boolean isEntryPoint() {
        return isEntryPoint;
    }

    List<String> getAliases() {
        return aliases;
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

    @Override
    public SubstrateRegisterConfig getRegisterConfig() {
        return (SubstrateRegisterConfig) super.getRegisterConfig();
    }

    @Override
    public void allocateRegisterSlots() {
        if (!isEntryPoint) {
            /* Non-entry point methods get the stack slots of their caller as argument. */
            return;
        }

        for (int i = 0; i < registerStackSlots.length; ++i) {
            registerStackSlots[i] = builder.buildArrayAlloca(1);
        }
    }

    @Override
    public void emitVerificationMarker(Object marker) {
        /*
         * No-op, for now we do not have any verification of the LLVM IR that requires the markers.
         */
    }

    @Override
    public void emitInstructionSynchronizationBarrier() {
        /*
         * No-op - for now this is not needed for LLVM backend.
         */
    }

    @Override
    protected LLVMValueRef convertEnumReturnValue(LLVMValueRef longValue) {
        if (returnsCEnum) {
            return builder.buildTrunc(longValue, JavaKind.Int.getBitCount());
        }
        return super.convertEnumReturnValue(longValue);
    }

    @Override
    public void emitFarReturn(AllocatableValue result, Value sp, Value setjmpBuffer, boolean fromMethodWithCalleeSavedRegisters) {
        /* Exception unwinding is handled by libunwind */
        throw unimplemented();
    }

    @Override
    public void emitDeadEnd() {
        builder.buildUnreachable();
    }

    @Override
    protected ResolvedJavaMethod findForeignCallTarget(ForeignCallDescriptor descriptor) {
        return ((SnippetRuntime.SubstrateForeignCallDescriptor) descriptor).findMethod(getMetaAccess());
    }

    @Override
    public Value emitConstant(LIRKind kind, Constant constant) {
        if (SubstrateOptions.SpawnIsolates.getValue() && kind.isReference(0) && !kind.isCompressedReference(0)) {
            LIRKind compressedKind = lirKindTool.getNarrowOopKind();
            Value compressedConstant = super.emitConstant(compressedKind, constant);
            return emitUncompress(compressedConstant, ReferenceAccess.singleton().getCompressEncoding(), false);
        }
        return super.emitConstant(kind, constant);
    }

    @Override
    public AllocatableValue emitLoadConstant(ValueKind<?> kind, Constant constant) {
        AllocatableValue rawConstant = super.emitLoadConstant(kind, constant);
        if (SubstrateOptions.SpawnIsolates.getValue() && ((LIRKind) kind).isReference(0) && !((LIRKind) kind).isCompressedReference(0)) {
            return (AllocatableValue) emitUncompress(rawConstant, ReferenceAccess.singleton().getCompressEncoding(), false);
        }
        return rawConstant;
    }

    @Override
    public Value emitCompress(Value pointer, CompressEncoding encoding, boolean nonNull) {
        LLVMValueRef heapBase = getSpecialRegister(LLVMFeature.HEAP_BASE_INDEX);
        return new LLVMVariable(builder.buildCompress(getVal(pointer), heapBase, nonNull, encoding.getShift()));
    }

    @Override
    public Value emitUncompress(Value pointer, CompressEncoding encoding, boolean nonNull) {
        LLVMValueRef heapBase = getSpecialRegister(LLVMFeature.HEAP_BASE_INDEX);
        return new LLVMVariable(builder.buildUncompress(getVal(pointer), heapBase, nonNull, encoding.getShift()));
    }

    @Override
    protected boolean isConstantCompressed(Constant constant) {
        return constant instanceof SubstrateObjectConstant && ((SubstrateObjectConstant) constant).isCompressed();
    }

    @Override
    public String getFunctionName(ResolvedJavaMethod method) {
        return SubstrateUtil.uniqueShortName(method);
    }

    @Override
    protected JavaKind getTypeKind(ResolvedJavaType type, boolean forMainFunction) {
        if (forMainFunction && isEntryPoint && type.isEnum() && GuardedAnnotationAccess.isAnnotationPresent(type, CEnum.class)) {
            return JavaKind.Int;
        }
        return ((HostedType) type).getStorageKind();
    }

    @Override
    protected LLVMTypeRef[] getLLVMFunctionArgTypes(ResolvedJavaMethod method, boolean forMainFunction) {
        LLVMTypeRef[] parameterTypes = super.getLLVMFunctionArgTypes(method, forMainFunction);
        LLVMTypeRef[] newParameterTypes = parameterTypes;
        if (!isEntryPoint(method) && registerStackSlots.length > 0) {
            newParameterTypes = new LLVMTypeRef[registerStackSlots.length + parameterTypes.length];
            for (int i = 0; i < registerStackSlots.length; ++i) {
                newParameterTypes[i] = canModifySpecialRegisters(method) ? builder.rawPointerType() : builder.longType();
            }
            System.arraycopy(parameterTypes, 0, newParameterTypes, LLVMFeature.SPECIAL_REGISTER_COUNT, parameterTypes.length);
        }
        return newParameterTypes;
    }

    @Override
    public Variable emitReadRegister(Register register, ValueKind<?> kind) {
        LLVMValueRef value;
        if (register.equals(getRegisterConfig().getThreadRegister())) {
            value = getSpecialRegister(LLVMFeature.THREAD_POINTER_INDEX);
        } else if (register.equals(getRegisterConfig().getHeapBaseRegister())) {
            value = getSpecialRegister(LLVMFeature.HEAP_BASE_INDEX);
        } else if (register.equals(getRegisterConfig().getFrameRegister())) {
            value = builder.buildReadRegister(builder.register(getRegisterConfig().getFrameRegister().name));
        } else {
            throw shouldNotReachHere();
        }
        return new LLVMVariable(value);
    }

    @Override
    public void emitWriteRegister(Register dst, Value src, ValueKind<?> kind) {
        if (dst.equals(getRegisterConfig().getThreadRegister())) {
            assert isEntryPoint || canModifySpecialRegisters;
            builder.buildStore(getVal(src), getSpecialRegisterPointer(LLVMFeature.THREAD_POINTER_INDEX));
            return;
        } else if (dst.equals(getRegisterConfig().getHeapBaseRegister())) {
            assert isEntryPoint || canModifySpecialRegisters;
            builder.buildStore(getVal(src), getSpecialRegisterPointer(LLVMFeature.HEAP_BASE_INDEX));
            return;
        }
        throw shouldNotReachHere();
    }

    private LLVMValueRef getSpecialRegisterPointer(int index) {
        if (isEntryPoint) {
            return registerStackSlots[index];
        } else if (canModifySpecialRegisters) {
            return builder.getParam(index);
        } else {
            throw shouldNotReachHere();
        }
    }

    LLVMValueRef getSpecialRegister(int index) {
        LLVMValueRef specialRegister;
        if (isEntryPoint || canModifySpecialRegisters) {
            LLVMValueRef specialRegisterPointer = getSpecialRegisterPointer(index);
            specialRegister = builder.buildLoad(specialRegisterPointer, builder.longType());
        } else {
            specialRegister = builder.getParam(index);
        }
        return specialRegister;
    }

    private LLVMValueRef getSpecialRegisterArgument(int index, ResolvedJavaMethod targetMethod) {
        LLVMValueRef specialRegisterArg;
        if (targetMethod != null && canModifySpecialRegisters(targetMethod)) {
            if (isEntryPoint || canModifySpecialRegisters) {
                specialRegisterArg = builder.buildBitcast(getSpecialRegisterPointer(index), builder.rawPointerType());
            } else {
                /*
                 * This means that an entry point method is called directly from Java code. We only
                 * accept this in the case of a method that doesn't do anything Java-related, and
                 * therefore doesn't need the actual value of its special registers.
                 */
                assert GuardedAnnotationAccess.isAnnotationPresent(targetMethod, Uninterruptible.class);
                specialRegisterArg = builder.constantNull(builder.rawPointerType());
            }
        } else if (isEntryPoint || canModifySpecialRegisters) {
            specialRegisterArg = builder.buildLoad(getSpecialRegisterPointer(index), builder.longType());
        } else {
            specialRegisterArg = getSpecialRegister(index);
        }

        return specialRegisterArg;
    }

    @Override
    public LLVMValueRef[] getCallArguments(LLVMValueRef[] args, CallingConvention.Type callType, ResolvedJavaMethod targetMethod) {
        LLVMValueRef[] newArgs = args;

        if (!((SubstrateCallingConventionType) callType).nativeABI && registerStackSlots.length > 0) {
            newArgs = new LLVMValueRef[registerStackSlots.length + args.length];
            for (int i = 0; i < registerStackSlots.length; ++i) {
                newArgs[i] = getSpecialRegisterArgument(i, targetMethod);
            }
            System.arraycopy(args, 0, newArgs, LLVMFeature.SPECIAL_REGISTER_COUNT, args.length);
        }
        return newArgs;
    }

    @Override
    public LLVMTypeRef[] getUnknownCallArgumentTypes(LLVMTypeRef[] types, CallingConvention.Type callType) {
        LLVMTypeRef[] newTypes = types;

        if (!((SubstrateCallingConventionType) callType).nativeABI && registerStackSlots.length > 0) {
            newTypes = new LLVMTypeRef[registerStackSlots.length + types.length];
            for (int i = 0; i < registerStackSlots.length; ++i) {
                newTypes[i] = builder.longType();
            }
            System.arraycopy(types, 0, newTypes, LLVMFeature.SPECIAL_REGISTER_COUNT, types.length);
        }
        return newTypes;
    }

    @Override
    protected CallingConvention.Type getCallingConventionType(CallingConvention callingConvention) {
        return ((SubstrateCallingConvention) callingConvention).getType();
    }

    @Override
    public LLVMValueRef getRetrieveExceptionFunction() {
        return getFunction(LLVMFeature.retrieveExceptionMethod);
    }
}
