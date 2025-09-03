/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.interpreter;

import static com.oracle.svm.interpreter.EspressoFrame.setLocalDouble;
import static com.oracle.svm.interpreter.EspressoFrame.setLocalFloat;
import static com.oracle.svm.interpreter.EspressoFrame.setLocalInt;
import static com.oracle.svm.interpreter.EspressoFrame.setLocalLong;
import static com.oracle.svm.interpreter.EspressoFrame.setLocalObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;

import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.SectionName;
import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.code.InterpreterAccessStubData;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.image.AbstractImage;
import com.oracle.svm.hosted.image.NativeImage;
import com.oracle.svm.hosted.image.RelocatableBuffer;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;
import com.oracle.svm.interpreter.metadata.InterpreterUnresolvedSignature;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.ValueKindFactory;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@InternalVMMethod
public abstract class InterpreterStubSection {
    @Platforms(Platform.HOSTED_ONLY.class) //
    public static final SectionName SVM_INTERP = new SectionName.ProgbitsSectionName("svm_interp");

    private static final CGlobalData<Pointer> BASE = CGlobalDataFactory.forSymbol(nameForVTableIndex(0));

    /* '-3' to reduce padding due to alignment in .svm_interp section */
    static final int MAX_VTABLE_STUBS = 2 * 1024 - 3;

    protected RegisterConfig registerConfig;
    protected SubstrateTargetDescription target;
    protected ValueKindFactory<LIRKind> valueKindFactory;

    @Platforms(Platform.HOSTED_ONLY.class) //
    private ObjectFile.ProgbitsSectionImpl stubsBufferImpl;

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final Map<InterpreterResolvedJavaMethod, Integer> enterTrampolineOffsets = new HashMap<>();
    @Platforms(Platform.HOSTED_ONLY.class) //
    private int vTableStubBaseOffset = -1;

    @Platforms(Platform.HOSTED_ONLY.class)
    public void createInterpreterEnterStubSection(AbstractImage image, Collection<InterpreterResolvedJavaMethod> methods) {
        ObjectFile objectFile = image.getObjectFile();
        byte[] stubsBlob = generateEnterStubs(methods);

        RelocatableBuffer stubsBuffer = new RelocatableBuffer(stubsBlob.length, objectFile.getByteOrder());
        stubsBufferImpl = new BasicProgbitsSectionImpl(stubsBuffer.getBackingArray());
        ObjectFile.Section stubsSection = objectFile.newProgbitsSection(SVM_INTERP.getFormatDependentName(objectFile.getFormat()), objectFile.getPageSize(), false, true, stubsBufferImpl);

        stubsBuffer.getByteBuffer().put(stubsBlob, 0, stubsBlob.length);

        boolean internalSymbolsAreGlobal = SubstrateOptions.InternalSymbolsAreGlobal.getValue();
        objectFile.createDefinedSymbol("interp_enter_trampoline", stubsSection, 0, 0, true, internalSymbolsAreGlobal);

        for (InterpreterResolvedJavaMethod method : enterTrampolineOffsets.keySet()) {
            int offset = enterTrampolineOffsets.get(method);
            objectFile.createDefinedSymbol(nameForInterpMethod(method), stubsSection, offset, ConfigurationValues.getTarget().wordSize, true, internalSymbolsAreGlobal);
        }
    }

    public static String nameForInterpMethod(InterpreterResolvedJavaMethod method) {
        return "interp_enter_" + NativeImage.localSymbolNameForMethod(method);
    }

    public static String nameForVTableIndex(int vTableIndex) {
        return "crema_enter_" + String.format("%04d", vTableIndex);
    }

    public static Pointer getCremaStubForVTableIndex(int vTableIndex) {
        VMError.guarantee(vTableIndex >= 0 && vTableIndex < MAX_VTABLE_STUBS);
        return BASE.get().add(vTableIndex * ImageSingletons.lookup(InterpreterStubSection.class).getVTableStubSize());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void createInterpreterVTableEnterStubSection(AbstractImage image) {
        ObjectFile objectFile = image.getObjectFile();
        byte[] stubsBlob = generateVTableEnterStubs(MAX_VTABLE_STUBS);

        RelocatableBuffer stubsBuffer = new RelocatableBuffer(stubsBlob.length, objectFile.getByteOrder());
        stubsBufferImpl = new BasicProgbitsSectionImpl(stubsBuffer.getBackingArray());

        // TODO: if the section should be re-used, we need to respect the offsets into this section.
        // or just a new dedicated section?
        ObjectFile.Section stubsSection = objectFile.newProgbitsSection(SVM_INTERP.getFormatDependentName(objectFile.getFormat()), objectFile.getPageSize(), false, true, stubsBufferImpl);

        stubsBuffer.getByteBuffer().put(stubsBlob, 0, stubsBlob.length);

        boolean internalSymbolsAreGlobal = SubstrateOptions.InternalSymbolsAreGlobal.getValue();
        objectFile.createDefinedSymbol("crema_enter_trampoline", stubsSection, 0, 0, true, internalSymbolsAreGlobal);

        assert vTableStubBaseOffset != -1;
        for (int vTableIndex = 0; vTableIndex < MAX_VTABLE_STUBS; vTableIndex++) {
            int codeOffset = vTableStubBaseOffset + vTableIndex * getVTableStubSize();
            String symbolName = nameForVTableIndex(vTableIndex);
            objectFile.createDefinedSymbol(symbolName, stubsSection, codeOffset, ConfigurationValues.getTarget().wordSize, true, internalSymbolsAreGlobal);
        }
    }

    protected void recordVTableStubBaseOffset(int codeOffset) {
        assert vTableStubBaseOffset == -1;
        vTableStubBaseOffset = codeOffset;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected void recordEnterTrampoline(InterpreterResolvedJavaMethod m, int position) {
        enterTrampolineOffsets.put(m, position);
    }

    protected abstract byte[] generateEnterStubs(Collection<InterpreterResolvedJavaMethod> methods);

    protected abstract byte[] generateVTableEnterStubs(int maxVTableIndex);

    public abstract int getVTableStubSize();

    @Platforms(Platform.HOSTED_ONLY.class)
    public void markEnterStubPatch(ResolvedJavaMethod enterStub) {
        markEnterStubPatch(stubsBufferImpl, enterStub);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected abstract void markEnterStubPatch(ObjectFile.ProgbitsSectionImpl pltBuffer, ResolvedJavaMethod enterStub);

    @Deoptimizer.DeoptStub(stubType = Deoptimizer.StubType.InterpreterEnterStub)
    @NeverInline("needs ABI boundary")
    public static Pointer enterMethodInterpreterStub(int interpreterMethodESTOffset, Pointer enterData) {
        DebuggerSupport interpreterSupport = ImageSingletons.lookup(DebuggerSupport.class);

        InterpreterResolvedJavaMethod interpreterMethod = (InterpreterResolvedJavaMethod) interpreterSupport.getUniverse().getMethodForESTOffset(interpreterMethodESTOffset);
        VMError.guarantee(interpreterMethod != null);

        return enterHelper(interpreterMethod, enterData);
    }

    @Deoptimizer.DeoptStub(stubType = Deoptimizer.StubType.InterpreterEnterStub)
    @NeverInline("needs ABI boundary")
    public static Pointer enterVTableInterpreterStub(int vTableIndex, Pointer enterData) {
        InterpreterAccessStubData accessHelper = ImageSingletons.lookup(InterpreterAccessStubData.class);

        /* assuming that this is a virtual method, i.e. has a 'this' argument */
        Object receiver = ((Pointer) Word.pointer(accessHelper.getGpArgumentAt(null, enterData, 0))).toObject();

        DynamicHub hub = DynamicHub.fromClass(receiver.getClass());
        InterpreterResolvedObjectType thisType = (InterpreterResolvedObjectType) hub.getInterpreterType();
        InterpreterResolvedJavaMethod interpreterMethod = thisType.getVtable()[vTableIndex];

        return enterHelper(interpreterMethod, enterData);
    }

    @AlwaysInline("helper")
    private static Pointer enterHelper(InterpreterResolvedJavaMethod interpreterMethod, Pointer enterData) {
        InterpreterAccessStubData accessHelper = ImageSingletons.lookup(InterpreterAccessStubData.class);
        InterpreterStubSection stubSection = ImageSingletons.lookup(InterpreterStubSection.class);

        InterpreterUnresolvedSignature signature = interpreterMethod.getSignature();
        VMError.guarantee(signature != null);

        int count = signature.getParameterCount(false);

        ResolvedJavaType accessingClass = interpreterMethod.getDeclaringClass();

        JavaType thisType = interpreterMethod.hasReceiver() ? accessingClass : null;
        JavaType returnType = signature.getReturnType(accessingClass);

        var kind = SubstrateCallingConventionKind.Java.toType(true);
        CallingConvention callingConvention = stubSection.registerConfig.getCallingConvention(kind, returnType, signature.toParameterTypes(thisType), stubSection.valueKindFactory);

        InterpreterFrame frame = EspressoFrame.allocate(interpreterMethod.getMaxLocals(), interpreterMethod.getMaxStackSize(), new Object[0]);

        int interpSlot = 0;
        int gpIdx = 0;
        int fpIdx = 0;
        if (interpreterMethod.hasReceiver()) {
            Object receiver = ((Pointer) Word.pointer(accessHelper.getGpArgumentAt(callingConvention.getArgument(gpIdx), enterData, gpIdx))).toObject();
            setLocalObject(frame, 0, receiver);
            gpIdx++;
            interpSlot++;
        }

        for (int i = 0; i < count; i++) {
            JavaKind argKind = signature.getParameterKind(i);
            long arg;
            AllocatableValue ccArg = callingConvention.getArgument(gpIdx + fpIdx);
            switch (argKind) {
                case Float:
                case Double:
                    arg = accessHelper.getFpArgumentAt(ccArg, enterData, fpIdx);
                    fpIdx++;
                    break;
                default:
                    arg = accessHelper.getGpArgumentAt(ccArg, enterData, gpIdx);
                    gpIdx++;
                    break;
            }

            switch (argKind) {
                // @formatter:off
                case Boolean: setLocalInt(frame, interpSlot,  (arg & 0xff) != 0 ? 1 : 0); break;
                case Byte:    setLocalInt(frame, interpSlot, (byte) arg); break;
                case Short:   setLocalInt(frame, interpSlot, (short) arg); break;
                case Char:    setLocalInt(frame, interpSlot, (char) arg); break;
                case Int:     setLocalInt(frame, interpSlot, (int) arg); break;
                case Long:    setLocalLong(frame, interpSlot, arg); interpSlot++; break;
                case Float:   setLocalFloat(frame, interpSlot, Float.intBitsToFloat((int) arg)); break;
                case Double:  setLocalDouble(frame, interpSlot, Double.longBitsToDouble(arg)); interpSlot++; break;
                case Object:  setLocalObject(frame, interpSlot, ((Pointer) Word.pointer(arg)).toObject()); break;
                // @formatter:on
                default:
                    throw VMError.shouldNotReachHereAtRuntime();
            }
            interpSlot++;
        }

        Object retVal = Interpreter.execute(interpreterMethod, frame);

        switch (returnType.getJavaKind()) {
            case Boolean:
                assert retVal instanceof Boolean;
                accessHelper.setGpReturn(enterData, ((Boolean) retVal) ? 1 : 0);
                break;
            case Byte:
                assert retVal instanceof Byte;
                accessHelper.setGpReturn(enterData, ((Byte) retVal).longValue());
                break;
            case Short:
                assert retVal instanceof Short;
                accessHelper.setGpReturn(enterData, ((Short) retVal).longValue());
                break;
            case Char:
                assert retVal instanceof Character;
                accessHelper.setGpReturn(enterData, ((Character) retVal).charValue());
                break;
            case Int:
                assert retVal instanceof Integer;
                accessHelper.setGpReturn(enterData, ((Integer) retVal).longValue());
                break;
            case Long:
                assert retVal instanceof Long;
                accessHelper.setGpReturn(enterData, (Long) retVal);
                break;
            case Float:
                assert retVal instanceof Float;
                accessHelper.setFpReturn(enterData, Float.floatToRawIntBits((float) retVal));
                break;
            case Double:
                assert retVal instanceof Double;
                accessHelper.setFpReturn(enterData, Double.doubleToRawLongBits((double) retVal));
                break;
            case Object:
                accessHelper.setGpReturn(enterData, Word.objectToTrackedPointer(retVal).rawValue());
                break;
            case Void:
                break;
            default:
                throw VMError.shouldNotReachHereAtRuntime();
        }
        return enterData;
    }

    /*
     * reserve four slots for: 1. base address of outgoing stack args, 2. variable stack size, 3.
     * gcReferenceMap, 4. stack padding to match alignment
     */
    @Deoptimizer.DeoptStub(stubType = Deoptimizer.StubType.InterpreterLeaveStub)
    @NeverInline("needs ABI boundary")
    @SuppressWarnings("unused")
    public static Pointer leaveInterpreterStub(CFunctionPointer entryPoint, Pointer leaveData, long stackSize, long gcReferenceMap) {
        return (Pointer) entryPoint;
    }

    public static Object leaveInterpreter(CFunctionPointer compiledEntryPoint, InterpreterResolvedJavaMethod seedMethod, ResolvedJavaType accessingClass, Object[] args) {
        InterpreterUnresolvedSignature targetSignature = seedMethod.getSignature();
        InterpreterStubSection stubSection = ImageSingletons.lookup(InterpreterStubSection.class);

        JavaType thisType = seedMethod.hasReceiver() ? seedMethod.getDeclaringClass() : null;
        SubstrateCallingConventionType kind = SubstrateCallingConventionKind.Java.toType(true);
        JavaType returnType = targetSignature.getReturnType(accessingClass);

        CallingConvention callingConvention = stubSection.registerConfig.getCallingConvention(kind, returnType, targetSignature.toParameterTypes(thisType), stubSection.valueKindFactory);

        InterpreterAccessStubData accessHelper = ImageSingletons.lookup(InterpreterAccessStubData.class);
        Pointer leaveData = StackValue.get(1, accessHelper.allocateStubDataSize());

        /* GR-54726: Reference map is currently limited to 64 arguments */
        long gcReferenceMap = 0;
        int gpIdx = 0;
        int fpIdx = 0;
        if (seedMethod.hasReceiver()) {
            gcReferenceMap |= accessHelper.setGpArgumentAt(callingConvention.getArgument(gpIdx), leaveData, gpIdx, Word.objectToTrackedPointer(args[0]).rawValue());
            gpIdx++;
        }

        int stackSize = NumUtil.roundUp(callingConvention.getStackSize(), stubSection.target.stackAlignment);

        Pointer stackBuffer = Word.nullPointer();
        if (stackSize > 0) {
            stackBuffer = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(Word.unsigned(stackSize));
            accessHelper.setSp(leaveData, stackSize, stackBuffer);
        }

        int argCount = targetSignature.getParameterCount(false);
        for (int i = 0; i < argCount; i++) {
            Object arg = args[i + (seedMethod.hasReceiver() ? 1 : 0)];

            AllocatableValue ccArg = callingConvention.getArgument(gpIdx + fpIdx);
            JavaType type = targetSignature.getParameterType(i, accessingClass);
            switch (type.getJavaKind()) {
                case Boolean:
                    accessHelper.setGpArgumentAt(ccArg, leaveData, gpIdx, (boolean) arg ? 1 : 0);
                    gpIdx++;
                    break;
                case Byte:
                    accessHelper.setGpArgumentAt(ccArg, leaveData, gpIdx, (byte) arg);
                    gpIdx++;
                    break;
                case Short:
                    accessHelper.setGpArgumentAt(ccArg, leaveData, gpIdx, (short) arg);
                    gpIdx++;
                    break;
                case Char:
                    accessHelper.setGpArgumentAt(ccArg, leaveData, gpIdx, (char) arg);
                    gpIdx++;
                    break;
                case Int:
                    accessHelper.setGpArgumentAt(ccArg, leaveData, gpIdx, (int) arg);
                    gpIdx++;
                    break;
                case Long:
                    accessHelper.setGpArgumentAt(ccArg, leaveData, gpIdx, (long) arg);
                    gpIdx++;
                    break;
                case Object:
                    gcReferenceMap |= accessHelper.setGpArgumentAt(ccArg, leaveData, gpIdx, Word.objectToTrackedPointer(arg).rawValue());
                    gpIdx++;
                    break;

                case Float:
                    accessHelper.setFpArgumentAt(ccArg, leaveData, fpIdx, Float.floatToRawIntBits((float) arg));
                    fpIdx++;
                    break;
                case Double:
                    accessHelper.setFpArgumentAt(ccArg, leaveData, fpIdx, Double.doubleToRawLongBits((double) arg));
                    fpIdx++;
                    break;

                default:
                    throw VMError.shouldNotReachHereAtRuntime();
            }
        }

        VMError.guarantee(compiledEntryPoint.isNonNull());

        try {
            // GR-55022: Stack overflow check should be done here
            leaveInterpreterStub(compiledEntryPoint, leaveData, stackSize, gcReferenceMap);
        } catch (Throwable e) {
            // native code threw exception, wrap it
            throw SemanticJavaException.raise(e);
        } finally {
            if (stackSize > 0) {
                VMError.guarantee(stackBuffer.isNonNull());
                ImageSingletons.lookup(UnmanagedMemorySupport.class).free(stackBuffer);
            }
        }

        // @formatter:off
        return switch (returnType.getJavaKind()) {
            case Boolean -> (accessHelper.getGpReturn(leaveData) & 0xff) != 0;
            case Byte    -> (byte) accessHelper.getGpReturn(leaveData);
            case Short   -> (short) accessHelper.getGpReturn(leaveData);
            case Char    -> (char) accessHelper.getGpReturn(leaveData);
            case Int     -> (int) accessHelper.getGpReturn(leaveData);
            case Long    -> accessHelper.getGpReturn(leaveData);
            case Float   -> Float.intBitsToFloat((int) accessHelper.getFpReturn(leaveData));
            case Double  -> Double.longBitsToDouble(accessHelper.getFpReturn(leaveData));
            case Object  -> ((Pointer) Word.pointer(accessHelper.getGpReturn(leaveData))).toObject();
            case Void    -> null;
            default      -> throw VMError.shouldNotReachHereAtRuntime();
        };
        // @formatter:on
    }
}
