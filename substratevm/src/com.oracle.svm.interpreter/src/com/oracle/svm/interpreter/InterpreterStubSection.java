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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.SectionName;
import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.code.InterpreterAccessStubData;
import com.oracle.svm.core.graal.code.PreparedArgumentType;
import com.oracle.svm.core.graal.code.PreparedSignature;
import com.oracle.svm.core.handles.ThreadLocalHandles;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.image.AbstractImage;
import com.oracle.svm.hosted.image.NativeImage;
import com.oracle.svm.hosted.image.RelocatableBuffer;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;
import com.oracle.svm.interpreter.metadata.InterpreterUniverse;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.ValueKindFactory;
import jdk.vm.ci.meta.JavaKind;

@InternalVMMethod
public abstract class InterpreterStubSection {
    @Platforms(Platform.HOSTED_ONLY.class) //
    public static final SectionName SVM_INTERP = new SectionName.ProgbitsSectionName("svm_interp");

    private static final CGlobalData<Pointer> BASE = CGlobalDataFactory.forSymbol(nameForVTableIndex(0));

    private static final String REASON_REFERENCES_ON_STACK = "stack frame might contain object references that are not known to the GC";

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
    public void markEnterStubPatch(HostedMethod enterStub) {
        markEnterStubPatch(stubsBufferImpl, enterStub);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected abstract void markEnterStubPatch(ObjectFile.ProgbitsSectionImpl pltBuffer, HostedMethod enterStub);

    interface ThreadLocalInterpreterHandle extends ObjectHandle, PointerBase {
    }

    @SuppressWarnings("rawtypes") //
    public static final FastThreadLocalObject<ThreadLocalHandles> TL_HANDLES = FastThreadLocalFactory.createObject(ThreadLocalHandles.class, "Interpreter handles for enter stub");

    /*
     * Maximum number of parameters that can be passed according to 4.3.3 in the JVM spec. Could be
     * optimized, see GR-71907.
     */
    public static final int MAX_ARGUMENT_HANDLES = 255;

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static ThreadLocalHandles<ThreadLocalInterpreterHandle> tlsHandles() {
        return TL_HANDLES.get();
    }

    @Deoptimizer.DeoptStub(stubType = Deoptimizer.StubType.InterpreterEnterStub)
    @NeverInline("needs ABI boundary")
    @Uninterruptible(reason = REASON_REFERENCES_ON_STACK)
    public static Pointer enterMethodInterpreterStub(int interpreterMethodESTOffset, Pointer enterData) {
        DebuggerSupport debuggerSupport = ImageSingletons.lookup(DebuggerSupport.class);

        InterpreterUniverse interpreterUniverse = debuggerSupport.getUniverseOrNull();
        VMError.guarantee(interpreterUniverse != null);

        InterpreterResolvedJavaMethod interpreterMethod = (InterpreterResolvedJavaMethod) interpreterUniverse.getMethodForESTOffset(interpreterMethodESTOffset);
        VMError.guarantee(interpreterMethod != null);

        return enterHelper(interpreterMethod, enterData);
    }

    @Deoptimizer.DeoptStub(stubType = Deoptimizer.StubType.InterpreterEnterStub)
    @NeverInline("needs ABI boundary")
    @Uninterruptible(reason = REASON_REFERENCES_ON_STACK)
    public static Pointer enterVTableInterpreterStub(int vTableIndex, Pointer enterData) {
        InterpreterAccessStubData accessHelper = ImageSingletons.lookup(InterpreterAccessStubData.class);

        /* assuming that this is a virtual method, i.e. has a 'this' argument */
        Object receiver = ((Pointer) Word.pointer(accessHelper.getGpArgumentAt(null, enterData, 0))).toObject();

        DynamicHub hub = DynamicHub.fromClass(receiver.getClass());
        InterpreterResolvedObjectType thisType = (InterpreterResolvedObjectType) hub.getInterpreterType();
        InterpreterResolvedJavaMethod interpreterMethod = thisType.getVtable()[vTableIndex];

        return enterHelper(interpreterMethod, enterData);
    }

    /**
     * The "enter stub" pretends to be like a compiled method, with the advantage that the caller
     * does not need to know where the call ends up. Therefore, it has to look like a compiled
     * entrypoint.
     *
     * The low-level stubs calling this helper spill native ABI arguments to the stack frame, see
     * {@link com.oracle.svm.core.graal.amd64.AMD64InterpreterStubs.InterpreterEnterStubContext} and
     * {@link com.oracle.svm.core.graal.aarch64.AArch64InterpreterStubs.InterpreterEnterStubContext}.
     * Its layout is defined in
     * {@link com.oracle.svm.core.graal.amd64.AMD64InterpreterStubs.InterpreterDataAMD64} and
     * {@link com.oracle.svm.core.graal.aarch64.AArch64InterpreterStubs.InterpreterDataAArch64}.
     *
     * The ABI arguments can contain references which the GC is not aware of, so until they are
     * moved to a known location, a safepoint must be avoided. ThreadLocalHandles are used for that.
     *
     * @param interpreterMethod method that should run in the interpreter.
     * @param enterData pointer to struct that contains ABI arguments.
     * @return pointer to enterData, used by the low-level caller stub.
     */
    @AlwaysInline("Performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Pointer enterHelper(InterpreterResolvedJavaMethod interpreterMethod, Pointer enterData) {
        InterpreterAccessStubData accessHelper = ImageSingletons.lookup(InterpreterAccessStubData.class);

        PreparedSignature compiledSignature = interpreterMethod.getPreparedSignature();
        VMError.guarantee(compiledSignature != null);

        ThreadLocalHandles<ThreadLocalInterpreterHandle> handles = tlsHandles();
        VMError.guarantee(handles.getHandleCount() == 0);
        int handleFrameId = handles.pushFrameUninterruptible(MAX_ARGUMENT_HANDLES - 1);

        int gpIdx = 0;
        int handleCount = 0;
        for (int i = 0; i < compiledSignature.getCount(); i++) {
            PreparedArgumentType cArgType = compiledSignature.getPreparedArgumentTypes()[i];
            if (cArgType.getKind() == JavaKind.Object) {
                /*
                 * The GC is not aware of references in enterData, therefore they are replaced with
                 * object handles before allowing safepoints again.
                 */
                long rawAddr = accessHelper.getGpArgumentAt(cArgType, enterData, gpIdx);
                Object obj = ((Pointer) Word.pointer(rawAddr)).toObject();
                if (obj == null) {
                    accessHelper.setGpArgumentAtIncoming(cArgType, enterData, gpIdx, 0L);
                } else {
                    ThreadLocalInterpreterHandle threadLocalInterpreterHandle = handles.tryCreateNonNullUninterruptible(obj);
                    VMError.guarantee(threadLocalInterpreterHandle.isNonNull());
                    accessHelper.setGpArgumentAtIncoming(cArgType, enterData, gpIdx, threadLocalInterpreterHandle.rawValue());
                    handleCount++;
                }
            }

            switch (cArgType.getKind()) {
                case Float:
                case Double:
                    break;
                case Void:
                case Illegal:
                    throw VMError.shouldNotReachHereAtRuntime();
                default:
                    gpIdx++;
                    break;
            }
        }

        Object retVal = enterInterpreterStub0(interpreterMethod, compiledSignature, enterData, handleCount, handleFrameId);

        switch (compiledSignature.getReturnKind()) {
            case Boolean:
                InterpreterUtil.assertion(retVal instanceof Boolean, "invalid return type");
                accessHelper.setGpReturn(enterData, ((Boolean) retVal) ? 1 : 0);
                break;
            case Byte:
                InterpreterUtil.assertion(retVal instanceof Byte, "invalid return type");
                accessHelper.setGpReturn(enterData, ((Byte) retVal).longValue());
                break;
            case Short:
                InterpreterUtil.assertion(retVal instanceof Short, "invalid return type");
                accessHelper.setGpReturn(enterData, ((Short) retVal).longValue());
                break;
            case Char:
                InterpreterUtil.assertion(retVal instanceof Character, "invalid return type");
                accessHelper.setGpReturn(enterData, ((Character) retVal).charValue());
                break;
            case Int:
                InterpreterUtil.assertion(retVal instanceof Integer, "invalid return type");
                accessHelper.setGpReturn(enterData, ((Integer) retVal).longValue());
                break;
            case Long:
                InterpreterUtil.assertion(retVal instanceof Long, "invalid return type");
                accessHelper.setGpReturn(enterData, (Long) retVal);
                break;
            case Float:
                InterpreterUtil.assertion(retVal instanceof Float, "invalid return type");
                accessHelper.setFpReturn(enterData, Float.floatToRawIntBits((float) retVal));
                break;
            case Double:
                InterpreterUtil.assertion(retVal instanceof Double, "invalid return type");
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

    @Uninterruptible(reason = "allow allocation now ", calleeMustBe = false)
    private static Object enterInterpreterStub0(InterpreterResolvedJavaMethod interpreterMethod, PreparedSignature compiledSignature, Pointer enterData, int handleCount, int handleFrameId) {
        TestingBackdoor.stressEnterStub();
        return enterInterpreterStubCore(interpreterMethod, compiledSignature, enterData, handleCount, handleFrameId);
    }

    private static Object enterInterpreterStubCore(InterpreterResolvedJavaMethod interpreterMethod, PreparedSignature compiledSignature, Pointer enterData, int handleCount, int handleFrameId) {
        InterpreterAccessStubData accessHelper = ImageSingletons.lookup(InterpreterAccessStubData.class);
        PreparedArgumentType[] cArgsType = compiledSignature.getPreparedArgumentTypes();
        ThreadLocalHandles<ThreadLocalInterpreterHandle> handles = tlsHandles();
        int count = cArgsType.length;

        int interpSlot = 0;
        int gpIdx = 0;
        int fpIdx = 0;

        Object[] args = new Object[count + (interpreterMethod.hasReceiver() ? 1 : 0)];

        for (int i = 0; i < count; i++) {
            long arg = 0;
            PreparedArgumentType cArgType = cArgsType[gpIdx + fpIdx];
            JavaKind argKind = cArgType.getKind();
            switch (argKind) {
                case Float:
                case Double:
                    arg = accessHelper.getFpArgumentAt(cArgType, enterData, fpIdx);
                    fpIdx++;
                    break;
                case Object:
                    args[interpSlot] = popReferenceFromEnterData(accessHelper, cArgType, enterData, gpIdx);
                    gpIdx++;
                    break;
                case Void:
                case Illegal:
                    throw VMError.shouldNotReachHereAtRuntime();
                default:
                    arg = popPrimitiveFromEnterData(accessHelper, cArgType, enterData, gpIdx);
                    gpIdx++;
                    break;
            }

            switch (argKind) {
                // @formatter:off
                case Boolean: args[interpSlot] = (arg & 0xff) != 0; break;
                case Byte:    args[interpSlot] = (byte) arg; break;
                case Short:   args[interpSlot] = (short) arg; break;
                case Char:    args[interpSlot] = (char) arg; break;
                case Int:     args[interpSlot] = (int) arg; break;
                case Long:    args[interpSlot] = arg; break;
                case Float:   args[interpSlot] = Float.intBitsToFloat((int) arg); break;
                case Double:  args[interpSlot] = Double.longBitsToDouble(arg); break;
                case Object: /* already handled */ break;
                // @formatter:on
                default:
                    throw VMError.shouldNotReachHereAtRuntime();
            }
            interpSlot++;
        }

        VMError.guarantee(handles.getHandleCount() == handleCount);
        VMError.guarantee(handleFrameId == handles.popFrame());

        RistrettoMethod rMethod = (com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod) interpreterMethod.getRistrettoMethod();
        if (rMethod != null && rMethod.installedCode != null && rMethod.installedCode.isValid()) {
            /*
             * A JIT compiled version is available, execute this one instead. This could be more
             * optimized, see GR-71160.
             */

            CFunctionPointer entryPoint = Word.pointer(rMethod.installedCode.getEntryPoint());
            return leaveInterpreter(entryPoint, interpreterMethod, args);
        } else {
            return Interpreter.execute(interpreterMethod, args);
        }
    }

    @Uninterruptible(reason = "Raw object pointer.")
    private static Object popReferenceFromEnterData(InterpreterAccessStubData accessHelper, PreparedArgumentType cArgType, Pointer enterData, int gpIdx) {
        long arg = accessHelper.getGpArgumentAt(cArgType, enterData, gpIdx);

        /* reference in `enterData` has been replaced with a handle */
        ThreadLocalInterpreterHandle handle = Word.pointer(arg);

        if (handle.rawValue() == 0L) {
            return null;
        } else {
            return tlsHandles().getObject(handle);
        }
    }

    @Uninterruptible(reason = "Wrapping of getter, no raw object pointer involved in this case.")
    private static long popPrimitiveFromEnterData(InterpreterAccessStubData accessHelper, PreparedArgumentType cArgType, Pointer enterData, int gpIdx) {
        return accessHelper.getGpArgumentAt(cArgType, enterData, gpIdx);
    }

    /* reserve two slots for: 1. base address of outgoing stack args, and 2. variable stack size. */
    @Deoptimizer.DeoptStub(stubType = Deoptimizer.StubType.InterpreterLeaveStub)
    @NeverInline("needs ABI boundary")
    @Uninterruptible(reason = REASON_REFERENCES_ON_STACK)
    @SuppressWarnings("unused")
    public static Pointer leaveInterpreterStub(CFunctionPointer entryPoint, Pointer leaveData, long stackSize) {
        return (Pointer) entryPoint;
    }

    public static Object leaveInterpreter(CFunctionPointer compiledEntryPoint, InterpreterResolvedJavaMethod seedMethod, Object[] args) {
        PreparedSignature compiledSignature = seedMethod.getPreparedSignature();
        VMError.guarantee(compiledSignature != null);
        InterpreterStubSection stubSection = ImageSingletons.lookup(InterpreterStubSection.class);

        InterpreterAccessStubData accessHelper = ImageSingletons.lookup(InterpreterAccessStubData.class);
        Pointer leaveData = StackValue.get(accessHelper.allocateStubDataSize());

        int stackSize = NumUtil.roundUp(compiledSignature.getStackSize(), stubSection.target.stackAlignment);

        Pointer stackBuffer = Word.nullPointer();
        if (stackSize > 0) {
            stackBuffer = NativeMemory.malloc(Word.unsigned(stackSize), NmtCategory.Interpreter);
            accessHelper.setSp(leaveData, stackSize, stackBuffer);
        }

        try {
            // GR-55022: Stack overflow check should be done here
            return leaveInterpreter0(compiledEntryPoint, args, compiledSignature, accessHelper, leaveData, stackSize);
        } catch (Throwable e) {
            // native code threw exception, wrap it
            throw SemanticJavaException.raise(e);
        } finally {
            if (stackSize > 0) {
                VMError.guarantee(stackBuffer.isNonNull());
                NativeMemory.free(stackBuffer);
            }
        }
    }

    @Uninterruptible(reason = "References are put on the stack which the GC is unaware of.")
    private static Object leaveInterpreter0(CFunctionPointer compiledEntryPoint, Object[] args, PreparedSignature compiledSignature, InterpreterAccessStubData accessHelper, Pointer leaveData,
                    int stackSize) {
        int gpIdx = 0;
        int fpIdx = 0;

        int argCount = compiledSignature.getCount();
        for (int i = 0; i < argCount; i++) {
            Object arg = args[i];
            PreparedArgumentType cArgType = compiledSignature.getPreparedArgumentTypes()[gpIdx + fpIdx];
            switch (cArgType.getKind()) {
                case Boolean:
                    accessHelper.setGpArgumentAtOutgoing(cArgType, leaveData, gpIdx, (boolean) arg ? 1 : 0);
                    gpIdx++;
                    break;
                case Byte:
                    accessHelper.setGpArgumentAtOutgoing(cArgType, leaveData, gpIdx, (byte) arg);
                    gpIdx++;
                    break;
                case Short:
                    accessHelper.setGpArgumentAtOutgoing(cArgType, leaveData, gpIdx, (short) arg);
                    gpIdx++;
                    break;
                case Char:
                    accessHelper.setGpArgumentAtOutgoing(cArgType, leaveData, gpIdx, (char) arg);
                    gpIdx++;
                    break;
                case Int:
                    accessHelper.setGpArgumentAtOutgoing(cArgType, leaveData, gpIdx, (int) arg);
                    gpIdx++;
                    break;
                case Long:
                    accessHelper.setGpArgumentAtOutgoing(cArgType, leaveData, gpIdx, (long) arg);
                    gpIdx++;
                    break;
                case Object:
                    accessHelper.setGpArgumentAtOutgoing(cArgType, leaveData, gpIdx, Word.objectToTrackedPointer(arg).rawValue());
                    gpIdx++;
                    break;

                case Float:
                    accessHelper.setFpArgumentAt(cArgType, leaveData, fpIdx, Float.floatToRawIntBits((float) arg));
                    fpIdx++;
                    break;
                case Double:
                    accessHelper.setFpArgumentAt(cArgType, leaveData, fpIdx, Double.doubleToRawLongBits((double) arg));
                    fpIdx++;
                    break;

                default:
                    throw VMError.shouldNotReachHereAtRuntime();
            }
        }

        VMError.guarantee(compiledEntryPoint.isNonNull());
        leaveInterpreterStub(compiledEntryPoint, leaveData, stackSize);

        // @formatter:off
        return switch (compiledSignature.getReturnKind()) {
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

    public static class TestingBackdoor {
        private static boolean stressEnterStub = false;

        public static void enableStressEnterStub() {
            VMError.guarantee(InterpreterOptions.InterpreterBackdoor.getValue());
            stressEnterStub = true;
        }

        public static void disableStressEnterStub() {
            VMError.guarantee(InterpreterOptions.InterpreterBackdoor.getValue());
            stressEnterStub = false;
        }

        public static void stressEnterStub() {
            if (InterpreterOptions.InterpreterBackdoor.getValue() && stressEnterStub) {
                Heap.getHeap().getGC().collectCompletely(GCCause.UnitTest);
            }
        }
    }
}
