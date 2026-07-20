/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.impl.Word;

import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.SectionName;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.code.InterpreterAccessStubData;
import com.oracle.svm.core.graal.code.PreparedSignature;
import com.oracle.svm.core.graal.code.SubstrateBackendWithAssembler;
import com.oracle.svm.core.graal.code.SubstrateRegisterConfigFactory;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.graal.nodes.VaListInitializationNode;
import com.oracle.svm.core.graal.nodes.VaListNextArgNode;
import com.oracle.svm.core.handles.ThreadLocalHandles;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.crema.CremaResolvedJavaMethod;
import com.oracle.svm.core.hub.crema.CremaSupport;
import com.oracle.svm.core.interpreter.InterpreterEnterStub;
import com.oracle.svm.core.interpreter.InterpreterSupport;
import com.oracle.svm.core.jni.JNIMethodSupport;
import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jni.access.JNINativeLinkage;
import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.monitor.MonitorInflationCause;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.espresso.shared.resolver.CallKind;
import com.oracle.svm.graal.meta.SubstrateInstalledCodeImpl;
import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.guest.staging.jdk.InternalVMMethod;
import com.oracle.svm.hosted.image.AbstractImage;
import com.oracle.svm.hosted.image.NativeImage;
import com.oracle.svm.hosted.image.RelocatableBuffer;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;
import com.oracle.svm.interpreter.metadata.InterpreterUniverse;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;
import com.oracle.svm.shared.AlwaysInline;
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.NumUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.ValueKindFactory;
import jdk.vm.ci.meta.JavaKind;

/**
 * Generates and owns the machine-code stubs used to enter Crema interpreter methods from compiled
 * code and from vtable dispatch.
 *
 * <p>
 * The runtime entry helpers form an ABI boundary: while they are deciding whether a valid compiled
 * entry point can be used, references may be held in locations that the GC does not describe and
 * installed code must not be deoptimized between the entry-point lookup and the branch to that
 * code. Those helpers therefore remain {@link Uninterruptible} until the slow path is known to be
 * required.
 *
 * <p>
 * When no compiled body is available, execution deliberately switches back to interruptible Java
 * code before calling {@link Interpreter#execute(InterpreterResolvedJavaMethod, Object[])}. Guest
 * Java exceptions are then propagated according to the normal interpreter entry-point contract, so
 * callers observe the same exception they would have seen from a compiled call.
 */
@InternalVMMethod
public abstract class InterpreterStubSection {
    @Platforms(Platform.HOSTED_ONLY.class) //
    public static final SectionName SVM_INTERP = new SectionName.ProgbitsSectionName("svm_interp");

    private static final CGlobalData<Pointer> BASE = CGlobalDataFactory.forSymbol(nameForVTableIndex(0));

    private static final String REASON_REFERENCES_ON_STACK = "stack frame might contain object references that are not known to the GC";

    private static final String REASON_DEOPT_INSTALLED_CODE = "InstalledCode can be deoptimized or freed at a safepoint.";

    private static final String SWITCH_TO_UNINTERRUPTIBLE = "switch to uninterruptible";

    /* '-3' to reduce padding due to alignment in .svm_interp section */
    static final int MAX_VTABLE_STUBS = 2 * 1024 - 3;

    protected final SubstrateTarget target;
    protected final RegisterConfig registerConfig;
    protected final ValueKindFactory<LIRKind> valueKindFactory;

    @Platforms(Platform.HOSTED_ONLY.class) //
    private ObjectFile.ProgbitsSectionImpl stubsBufferImpl;

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final Map<InterpreterResolvedJavaMethod, Integer> enterTrampolineOffsets = new HashMap<>();
    @Platforms(Platform.HOSTED_ONLY.class) //
    private int vTableStubBaseOffset = -1;

    protected InterpreterStubSection() {
        this.target = SubstrateTarget.singleton();
        this.registerConfig = SubstrateRegisterConfigFactory.singleton().newRegisterFactory(SubstrateRegisterConfig.ConfigKind.NATIVE_TO_JAVA, null, this.target, true);
        this.valueKindFactory = javaKind -> LIRKind.fromJavaKind(this.target.arch, javaKind);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void createInterpreterEnterStubSection(AbstractImage image, Collection<InterpreterResolvedJavaMethod> methods, SubstrateBackendWithAssembler<?> backend) {
        ObjectFile objectFile = image.getObjectFile();
        byte[] stubsBlob = generateEnterStubs(backend, methods);

        RelocatableBuffer stubsBuffer = new RelocatableBuffer(stubsBlob.length, objectFile.getByteOrder());
        stubsBufferImpl = new BasicProgbitsSectionImpl(stubsBuffer.getBackingArray());
        ObjectFile.Section stubsSection = objectFile.newProgbitsSection(SVM_INTERP.getFormatDependentName(objectFile.getFormat()), objectFile.getPageSize(), false, true, stubsBufferImpl);

        stubsBuffer.getByteBuffer().put(stubsBlob, 0, stubsBlob.length);

        boolean internalSymbolsAreGlobal = SubstrateOptions.InternalSymbolsAreGlobal.getValue();
        objectFile.createDefinedSymbol("interp_enter_trampoline", stubsSection, 0, 0, true, internalSymbolsAreGlobal, internalSymbolsAreGlobal);

        for (InterpreterResolvedJavaMethod method : enterTrampolineOffsets.keySet()) {
            int offset = enterTrampolineOffsets.get(method);
            objectFile.createDefinedSymbol(nameForInterpMethod(method), stubsSection, offset, target.wordSize, true, internalSymbolsAreGlobal, internalSymbolsAreGlobal);
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
    public void createInterpreterVTableEnterStubSection(AbstractImage image, SubstrateBackendWithAssembler<?> backend) {
        ObjectFile objectFile = image.getObjectFile();
        byte[] stubsBlob = generateVTableEnterStubs(backend, MAX_VTABLE_STUBS);

        RelocatableBuffer stubsBuffer = new RelocatableBuffer(stubsBlob.length, objectFile.getByteOrder());
        stubsBufferImpl = new BasicProgbitsSectionImpl(stubsBuffer.getBackingArray());

        // TODO: if the section should be re-used, we need to respect the offsets into this section.
        // or just a new dedicated section?
        ObjectFile.Section stubsSection = objectFile.newProgbitsSection(SVM_INTERP.getFormatDependentName(objectFile.getFormat()), objectFile.getPageSize(), false, true, stubsBufferImpl);

        stubsBuffer.getByteBuffer().put(stubsBlob, 0, stubsBlob.length);

        boolean internalSymbolsAreGlobal = SubstrateOptions.InternalSymbolsAreGlobal.getValue();
        objectFile.createDefinedSymbol("crema_enter_trampoline", stubsSection, 0, 0, true, internalSymbolsAreGlobal, internalSymbolsAreGlobal);

        assert vTableStubBaseOffset != -1;
        for (int vTableIndex = 0; vTableIndex < MAX_VTABLE_STUBS; vTableIndex++) {
            int codeOffset = vTableStubBaseOffset + vTableIndex * getVTableStubSize();
            String symbolName = nameForVTableIndex(vTableIndex);
            objectFile.createDefinedSymbol(symbolName, stubsSection, codeOffset, target.wordSize, true, internalSymbolsAreGlobal, internalSymbolsAreGlobal);
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

    protected abstract byte[] generateEnterStubs(SubstrateBackendWithAssembler<?> backend, Collection<InterpreterResolvedJavaMethod> methods);

    protected abstract byte[] generateVTableEnterStubs(SubstrateBackendWithAssembler<?> backend, int maxVTableIndex);

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
    @InterpreterEnterStub(InterpreterEnterStub.Kind.EST_OFFSET)
    public static long enterMethodInterpreterStub(int interpreterMethodESTOffset, Pointer enterData) {
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
    @InterpreterEnterStub(InterpreterEnterStub.Kind.DIRECT)
    public static long enterDirectInterpreterStub(InterpreterResolvedJavaMethod interpreterMethod, Pointer enterData) {
        VMError.guarantee(interpreterMethod != null);

        return enterHelper(interpreterMethod, enterData);
    }

    @Deoptimizer.DeoptStub(stubType = Deoptimizer.StubType.InterpreterEnterStub)
    @NeverInline("needs ABI boundary")
    @Uninterruptible(reason = REASON_REFERENCES_ON_STACK)
    @InterpreterEnterStub(InterpreterEnterStub.Kind.VTABLE)
    public static long enterVTableInterpreterStub(int vTableIndex, Pointer enterData) {
        InterpreterAccessStubData accessHelper = ImageSingletons.lookup(InterpreterAccessStubData.class);

        /* assuming that this is a virtual method, i.e. has a 'this' argument */
        Object receiver = ((Pointer) Word.pointer(accessHelper.getGpArgumentAt(PreparedSignature.getDefaultArgumentType(), enterData, 0))).toObject();

        DynamicHub hub = DynamicHub.fromClass(receiver.getClass());
        InterpreterResolvedObjectType thisType = (InterpreterResolvedObjectType) hub.getInterpreterType();
        InterpreterResolvedJavaMethod interpreterMethod = thisType.getVtable()[vTableIndex];

        return enterHelper(interpreterMethod, enterData);
    }

    public static long enterDirectInterpreterStubArrayVirtual(JNIObjectHandle receiverOrClass, JNIObjectHandle methodId, Pointer array) throws InstantiationException {
        InterpreterResolvedJavaMethod method = JNIObjectHandles.getObject(methodId);
        Object[] args = prepareJNIArgsFromArray(receiverOrClass, method, array, false);
        return enterInterpreterFromNativeVirtual(method, args);
    }

    public static long enterDirectInterpreterStubArrayNonVirtual(JNIObjectHandle receiverOrClass, JNIObjectHandle methodId, Pointer array) throws InstantiationException {
        InterpreterResolvedJavaMethod method = JNIObjectHandles.getObject(methodId);
        Object[] args = prepareJNIArgsFromArray(receiverOrClass, method, array, false);
        return enterInterpreterFromNativeNonVirtual(method, args);
    }

    public static long enterDirectInterpreterStubArrayReadDoubleForFloatVirtual(JNIObjectHandle receiverOrClass, JNIObjectHandle methodId, Pointer array) throws InstantiationException {
        InterpreterResolvedJavaMethod method = JNIObjectHandles.getObject(methodId);
        Object[] args = prepareJNIArgsFromArray(receiverOrClass, method, array, true);
        return enterInterpreterFromNativeVirtual(method, args);
    }

    public static long enterDirectInterpreterStubArrayReadDoubleForFloatNonVirtual(JNIObjectHandle receiverOrClass, JNIObjectHandle methodId, Pointer array) throws InstantiationException {
        InterpreterResolvedJavaMethod method = JNIObjectHandles.getObject(methodId);
        Object[] args = prepareJNIArgsFromArray(receiverOrClass, method, array, true);
        return enterInterpreterFromNativeNonVirtual(method, args);
    }

    public static long enterDirectInterpreterStubVarargsVirtual(JNIObjectHandle receiverOrClass, JNIObjectHandle methodId, Pointer jniEnterData) throws InstantiationException {
        InterpreterResolvedJavaMethod method = JNIObjectHandles.getObject(methodId);
        Object[] args = prepareJNIArgsFromVarargs(receiverOrClass, method, jniEnterData, false);
        return enterInterpreterFromNativeVirtual(method, args);
    }

    public static long enterDirectInterpreterStubVarargsNonVirtual(JNIObjectHandle receiverOrClass, JNIObjectHandle methodId, Pointer jniEnterData) throws InstantiationException {
        InterpreterResolvedJavaMethod method = JNIObjectHandles.getObject(methodId);
        Object[] args = prepareJNIArgsFromVarargs(receiverOrClass, method, jniEnterData, true);
        return enterInterpreterFromNativeNonVirtual(method, args);
    }

    public static long enterDirectInterpreterStubVaListVirtual(JNIObjectHandle receiverOrClass, JNIObjectHandle methodId, Pointer vaList) throws InstantiationException {
        InterpreterResolvedJavaMethod method = JNIObjectHandles.getObject(methodId);
        Pointer vaListInitialized = VaListInitializationNode.vaListInitialization(vaList);
        Object[] args = prepareJNIArgsFromVaList(receiverOrClass, method, vaListInitialized);
        return enterInterpreterFromNativeVirtual(method, args);
    }

    public static long enterDirectInterpreterStubVaListNonVirtual(JNIObjectHandle receiverOrClass, JNIObjectHandle methodId, Pointer vaList) throws InstantiationException {
        InterpreterResolvedJavaMethod method = JNIObjectHandles.getObject(methodId);
        Pointer vaListInitialized = VaListInitializationNode.vaListInitialization(vaList);
        Object[] args = prepareJNIArgsFromVaList(receiverOrClass, method, vaListInitialized);
        return enterInterpreterFromNativeNonVirtual(method, args);
    }

    private static long enterInterpreterFromNativeVirtual(InterpreterResolvedJavaMethod method, Object[] args) {
        Object result = CremaSupport.singleton().execute(method, args, CallKind.getCallKind(method));
        return encodeJNIResult(method, args, result);
    }

    private static long enterInterpreterFromNativeNonVirtual(InterpreterResolvedJavaMethod method, Object[] args) {
        Object result = CremaSupport.singleton().execute(method, args, CallKind.DIRECT);
        return encodeJNIResult(method, args, result);
    }

    private static long encodeJNIResult(InterpreterResolvedJavaMethod method, Object[] args, Object result) {
        if (method.isConstructor()) {
            return JNIObjectHandles.createLocal(args[0]).rawValue();
        }
        return encodeJNIReturn(method.getPreparedSignature().getReturnKind(), result);
    }

    private static Object[] createJNIArgs(InterpreterResolvedJavaMethod method) {
        int[] argumentTypes = method.getPreparedSignature().getArgumentTypes();
        return new Object[argumentTypes.length];
    }

    private static Object[] prepareJNIArgsFromArray(JNIObjectHandle receiverOrClass, InterpreterResolvedJavaMethod method, Pointer array, boolean readDoubleForFloat) throws InstantiationException {
        int offset = 0;
        int[] argumentTypes = method.getPreparedSignature().getArgumentTypes();
        Object[] args = new Object[argumentTypes.length];
        int index = prepareJNIReceiver(receiverOrClass, method, args);
        for (int i = index; i < argumentTypes.length; i++) {
            args[i] = readArray(array, offset, PreparedSignature.getKind(argumentTypes[i]), readDoubleForFloat);
            offset += SubstrateTarget.getWordSize();
        }
        return args;
    }

    private static int prepareJNIReceiver(JNIObjectHandle receiverOrClass, InterpreterResolvedJavaMethod method, Object[] args) throws InstantiationException {
        if (!method.hasReceiver()) {
            return 0;
        }
        Object receiver = JNIObjectHandles.getObject(receiverOrClass);

        if (method.isConstructor()) {
            /*
             * The called function could either be NewObject or Call<Type>Method with a constructor
             * (without creating a new object).
             *
             * To distinguish them, we look at the second parameter, which is either `jobject obj` (the
             * receiver object) for `Call<Type>Method`, or `jclass clazz` (hub of the receiver object)
             * for `NewObject`.
             */
            if (receiver == InterpreterSupport.singleton().toClass(method.getDeclaringClass())) {
                receiver = CremaSupport.singleton().allocateInstance(method.getDeclaringClass());
            }
        }
        args[0] = receiver;
        return 1;
    }

    @AlwaysInline("Performance")
    private static Object readArray(Pointer array, int offset, JavaKind kind, boolean readDoubleForFloat) {
        return switch (kind) {
            case Boolean -> array.readByte(offset) != 0;
            case Byte -> array.readByte(offset);
            case Short -> array.readShort(offset);
            case Char -> array.readChar(offset);
            case Int -> array.readInt(offset);
            case Long -> array.readLong(offset);
            case Float -> readDoubleForFloat
                            ? (float) array.readDouble(offset)
                            : array.readFloat(offset);
            case Double -> array.readDouble(offset);
            case Object -> JNIObjectHandles.getObject(array.readWord(offset));
            default -> throw VMError.shouldNotReachHereAtRuntime();
        };
    }

    private static Object[] prepareJNIArgsFromVarargs(JNIObjectHandle receiverOrClass, InterpreterResolvedJavaMethod method, Pointer jniEnterData, boolean nonVirtual) throws InstantiationException {
        InterpreterAccessStubData accessHelper = ImageSingletons.lookup(InterpreterAccessStubData.class);
        int[] targetArgumentTypes = method.getPreparedSignature().getArgumentTypes();
        int[] sourceArgumentTypes = InterpreterSupport.singleton().prepareJNIUpcallVarargsSignature(method.getSignature(), method.getDeclaringClass(),
                        nonVirtual)
                        .getArgumentTypes();
        Object[] args = createJNIArgs(method);
        int targetArgumentIndex = prepareJNIReceiver(receiverOrClass, method, args);
        /*
         * Varargs JNI calls start with JNIEnv, the receiver/class handle, and the jmethodID. The
         * non-virtual variants have one additional class handle before the jmethodID that selects the
         * implementation class, so Java arguments start after 4 native arguments instead of 3.
         */
        int sourceArgumentIndex = (nonVirtual ? 4 : 3);
        for (; targetArgumentIndex < targetArgumentTypes.length; targetArgumentIndex++, sourceArgumentIndex++) {
            int sourceArgType = sourceArgumentTypes[sourceArgumentIndex];
            args[targetArgumentIndex] = readVarargs(jniEnterData, sourceArgType, PreparedSignature.getKind(targetArgumentTypes[targetArgumentIndex]), accessHelper);
        }
        return args;
    }

    @AlwaysInline("Performance")
    private static Object readVarargs(Pointer jniEnterData, int sourceArgType, JavaKind kind, InterpreterAccessStubData accessHelper) {
        int pos = PreparedSignature.isRegister(sourceArgType) ? PreparedSignature.getRegister(sourceArgType) : -1;
        return switch (kind) {
            case Boolean -> readJNIVarargsGpArgument(jniEnterData, sourceArgType, pos, accessHelper) != 0;
            case Byte -> (byte) readJNIVarargsGpArgument(jniEnterData, sourceArgType, pos, accessHelper);
            case Short -> (short) readJNIVarargsGpArgument(jniEnterData, sourceArgType, pos, accessHelper);
            case Char -> (char) readJNIVarargsGpArgument(jniEnterData, sourceArgType, pos, accessHelper);
            case Int -> (int) readJNIVarargsGpArgument(jniEnterData, sourceArgType, pos, accessHelper);
            case Long -> readJNIVarargsGpArgument(jniEnterData, sourceArgType, pos, accessHelper);
            case Object -> JNIObjectHandles.getObject(Word.pointer(readJNIVarargsGpArgument(jniEnterData, sourceArgType, pos, accessHelper)));
            case Float -> (float) Double.longBitsToDouble(readJNIVarargsFpArgument(jniEnterData, sourceArgType, pos, accessHelper));
            case Double -> Double.longBitsToDouble(readJNIVarargsFpArgument(jniEnterData, sourceArgType, pos, accessHelper));
            default -> throw VMError.shouldNotReachHereAtRuntime();
        };
    }

    private static Object[] prepareJNIArgsFromVaList(JNIObjectHandle receiverOrClass, InterpreterResolvedJavaMethod method, Pointer vaList) throws InstantiationException {
        int[] argumentTypes = method.getPreparedSignature().getArgumentTypes();
        Object[] args = createJNIArgs(method);
        int index = prepareJNIReceiver(receiverOrClass, method, args);
        for (int i = index; i < argumentTypes.length; i++) {
            args[i] = readVaList(vaList, PreparedSignature.getKind(argumentTypes[i]));
        }
        return args;
    }

    @AlwaysInline("Performance")
    private static Object readVaList(Pointer vaList, JavaKind kind) {
        return switch (kind) {
            case Boolean -> readVaListBoolean(vaList);
            case Byte -> readVaListByte(vaList);
            case Short -> readVaListShort(vaList);
            case Char -> readVaListChar(vaList);
            case Int -> readVaListInt(vaList);
            case Long -> readVaListLong(vaList);
            case Object -> JNIObjectHandles.getObject(Word.pointer(readVaListLong(vaList)));
            case Float -> readVaListFloat(vaList);
            case Double -> readVaListDouble(vaList);
            default -> throw VMError.shouldNotReachHereAtRuntime();
        };
    }

    @Uninterruptible(reason = SWITCH_TO_UNINTERRUPTIBLE)
    @NeverInline("")
    private static long readJNIVarargsGpArgument(Pointer jniEnterData, int cArgType, int pos, InterpreterAccessStubData accessHelper) {
        return accessHelper.getGpArgumentAt(cArgType, jniEnterData, pos);
    }

    @Uninterruptible(reason = SWITCH_TO_UNINTERRUPTIBLE)
    @NeverInline("")
    private static long readJNIVarargsFpArgument(Pointer jniEnterData, int cArgType, int pos, InterpreterAccessStubData accessHelper) {
        return accessHelper.getFpArgumentAt(cArgType, jniEnterData, pos);
    }

    @AlwaysInline("Performance")
    private static boolean readVaListBoolean(Pointer vaList) {
        return VaListNextArgNode.vaListNextBoolean(JavaKind.Boolean, vaList);
    }

    @AlwaysInline("Performance")
    private static byte readVaListByte(Pointer vaList) {
        return VaListNextArgNode.vaListNextByte(JavaKind.Byte, vaList);
    }

    @AlwaysInline("Performance")
    private static short readVaListShort(Pointer vaList) {
        return VaListNextArgNode.vaListNextShort(JavaKind.Short, vaList);
    }

    @AlwaysInline("Performance")
    private static char readVaListChar(Pointer vaList) {
        return VaListNextArgNode.vaListNextChar(JavaKind.Char, vaList);
    }

    @AlwaysInline("Performance")
    private static int readVaListInt(Pointer vaList) {
        return VaListNextArgNode.vaListNextInt(JavaKind.Int, vaList);
    }

    @AlwaysInline("Performance")
    private static long readVaListLong(Pointer vaList) {
        return VaListNextArgNode.vaListNextLong(JavaKind.Long, vaList);
    }

    @AlwaysInline("Performance")
    private static float readVaListFloat(Pointer vaList) {
        return VaListNextArgNode.vaListNextFloat(JavaKind.Float, vaList);
    }

    @AlwaysInline("Performance")
    private static double readVaListDouble(Pointer vaList) {
        return VaListNextArgNode.vaListNextDouble(JavaKind.Double, vaList);
    }

    private static long encodeJNIReturn(JavaKind returnKind, Object retVal) {
        /*
         * A return-type mismatch violates an interpreter invariant. Fail with a fatal VM error from
         * the explicit assertions instead of leaking a ClassCastException from this internal VM
         * method.
         */
        return switch (returnKind) {
            case Boolean -> {
                InterpreterUtil.assertion(retVal instanceof Boolean, "invalid return type");
                yield ((Boolean) retVal) ? 1 : 0;
            }
            case Byte -> {
                InterpreterUtil.assertion(retVal instanceof Byte, "invalid return type");
                yield ((Byte) retVal).longValue();
            }
            case Short -> {
                InterpreterUtil.assertion(retVal instanceof Short, "invalid return type");
                yield ((Short) retVal).longValue();
            }
            case Char -> {
                InterpreterUtil.assertion(retVal instanceof Character, "invalid return type");
                yield ((Character) retVal).charValue();
            }
            case Int -> {
                InterpreterUtil.assertion(retVal instanceof Integer, "invalid return type");
                yield ((Integer) retVal).longValue();
            }
            case Long -> {
                InterpreterUtil.assertion(retVal instanceof Long, "invalid return type");
                yield (Long) retVal;
            }
            case Float -> {
                InterpreterUtil.assertion(retVal instanceof Float, "invalid return type");
                yield Float.floatToRawIntBits((float) retVal);
            }
            case Double -> {
                InterpreterUtil.assertion(retVal instanceof Double, "invalid return type");
                yield Double.doubleToRawLongBits((double) retVal);
            }
            case Object -> JNIObjectHandles.createLocal(retVal).rawValue();
            case Void -> 0;
            default -> throw VMError.shouldNotReachHereAtRuntime();
        };
    }

    /**
     * The "enter stub" pretends to be like a compiled method, with the advantage that the caller
     * does not need to know where the call ends up. Therefore, it has to look like a compiled
     * entrypoint.
     * <p>
     * The low-level stubs calling this helper spill native ABI arguments to the stack frame, see
     * {@link com.oracle.svm.core.graal.amd64.AMD64InterpreterStubs.InterpreterEnterStubContext} and
     * {@link com.oracle.svm.core.graal.aarch64.AArch64InterpreterStubs.InterpreterEnterStubContext}.
     * Its layout is defined in
     * {@link com.oracle.svm.core.graal.amd64.AMD64InterpreterStubs.InterpreterDataAMD64} and
     * {@link com.oracle.svm.core.graal.aarch64.AArch64InterpreterStubs.InterpreterDataAArch64}.
     * <p>
     * The ABI arguments can contain references which the GC is not aware of, so until they are
     * moved to a known location, a safepoint must be avoided. ThreadLocalHandles are used for that.
     *
     * @param interpreterMethod method that should run in the interpreter.
     * @param enterData pointer to struct that contains ABI arguments.
     * @return the raw ABI result. The low-level caller stub copies the result to both the integer
     *         and floating-point return registers, and the compiled caller reads the register for
     *         its declared return kind.
     */
    @AlwaysInline("Performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static long enterHelper(InterpreterResolvedJavaMethod interpreterMethod, Pointer enterData) {
        InterpreterAccessStubData accessHelper = ImageSingletons.lookup(InterpreterAccessStubData.class);

        PreparedSignature compiledSignature = interpreterMethod.getPreparedSignature();
        VMError.guarantee(compiledSignature != null);
        int[] argumentTypes = compiledSignature.getArgumentTypes();

        ThreadLocalHandles<ThreadLocalInterpreterHandle> handles = tlsHandles();
        VMError.guarantee(handles.getHandleCount() == 0);
        int handleFrameId = handles.pushFrameUninterruptible(MAX_ARGUMENT_HANDLES - 1);

        int gpIdx = 0;
        int handleCount = 0;
        for (int i = 0; i < argumentTypes.length; i++) {
            int cArgType = argumentTypes[i];
            JavaKind argKind = PreparedSignature.getKind(cArgType);
            if (argKind == JavaKind.Object) {
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

            switch (argKind) {
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

        /*
         * enterData is a pointer into this stub's stack frame. The interpreter can block and cause
         * a virtual thread to migrate, so enterData must not be accessed after the interpreter
         * returns. Return the raw result directly instead.
         */
        return switch (compiledSignature.getReturnKind()) {
            case Boolean:
                InterpreterUtil.assertion(retVal instanceof Boolean, "invalid return type");
                yield ((Boolean) retVal) ? 1 : 0;
            case Byte:
                InterpreterUtil.assertion(retVal instanceof Byte, "invalid return type");
                yield ((Byte) retVal).longValue();
            case Short:
                InterpreterUtil.assertion(retVal instanceof Short, "invalid return type");
                yield ((Short) retVal).longValue();
            case Char:
                InterpreterUtil.assertion(retVal instanceof Character, "invalid return type");
                yield ((Character) retVal).charValue();
            case Int:
                InterpreterUtil.assertion(retVal instanceof Integer, "invalid return type");
                yield ((Integer) retVal).longValue();
            case Long:
                InterpreterUtil.assertion(retVal instanceof Long, "invalid return type");
                yield (Long) retVal;
            case Float:
                InterpreterUtil.assertion(retVal instanceof Float, "invalid return type");
                yield Float.floatToRawIntBits((float) retVal);
            case Double:
                InterpreterUtil.assertion(retVal instanceof Double, "invalid return type");
                yield Double.doubleToRawLongBits((double) retVal);
            case Object:
                /*
                 * A tracked non-null reference cannot be merged with the primitive results at the
                 * method-end infopoint. Dropping the tracking is safe because no safepoint is
                 * possible between this conversion and the ABI handoff. However, the untracked
                 * conversion maps the compiled null representation to zero, so return the heap
                 * base which represents null in compiled code.
                 */
                if (retVal == null) {
                    yield KnownIntrinsics.heapBase().rawValue();
                }
                yield Word.objectToUntrackedPointer(retVal).rawValue();
            case Void:
                yield 0;
            default:
                throw VMError.shouldNotReachHereAtRuntime();
        };
    }

    @Uninterruptible(reason = "Switch to interruptible code.", mayBeInlined = true, calleeMustBe = false)
    private static Object enterInterpreterStub0(InterpreterResolvedJavaMethod interpreterMethod, PreparedSignature compiledSignature, Pointer enterData, int handleCount, int handleFrameId) {
        TestingBackdoor.stressEnterStub();
        return enterInterpreterStubCore(interpreterMethod, compiledSignature, enterData, handleCount, handleFrameId);
    }

    private static Object enterInterpreterStubCore(InterpreterResolvedJavaMethod interpreterMethod, PreparedSignature compiledSignature, Pointer enterData, int handleCount, int handleFrameId) {
        InterpreterAccessStubData accessHelper = ImageSingletons.lookup(InterpreterAccessStubData.class);
        ThreadLocalHandles<ThreadLocalInterpreterHandle> handles = tlsHandles();
        int[] argumentTypes = compiledSignature.getArgumentTypes();
        int count = argumentTypes.length;

        int interpSlot = 0;
        int gpIdx = 0;
        int fpIdx = 0;

        Object[] args = new Object[count + (interpreterMethod.hasReceiver() ? 1 : 0)];

        for (int i = 0; i < count; i++) {
            long arg = 0;
            assert gpIdx + fpIdx == i;
            int cArgType = argumentTypes[i];
            JavaKind argKind = PreparedSignature.getKind(cArgType);
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

        if (interpreterMethod.isNative() && interpreterMethod instanceof CremaResolvedJavaMethod) {
            try {
                return Interpreter.JNIDowncallRoot.execute(interpreterMethod, args);
            } catch (Throwable t) {
                throw uncheckedThrow(t);
            }
        }

        return call(interpreterMethod, args, true);
    }

    @Uninterruptible(reason = "Raw object pointer.")
    private static Object popReferenceFromEnterData(InterpreterAccessStubData accessHelper, int cArgType, Pointer enterData, int gpIdx) {
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
    private static long popPrimitiveFromEnterData(InterpreterAccessStubData accessHelper, int cArgType, Pointer enterData, int gpIdx) {
        return accessHelper.getGpArgumentAt(cArgType, enterData, gpIdx);
    }

    /**
     * Leaves interpreter execution, invokes {@code entryPoint}, and returns the raw ABI result of
     * that call. For general-purpose returns this is the value from the integer return register;
     * for floating-point returns the leave-stub backend moves the raw bits from the floating-point
     * return register into the integer return register before returning to Java.
     */
    @Deoptimizer.DeoptStub(stubType = Deoptimizer.StubType.InterpreterLeaveStub)
    @NeverInline("needs ABI boundary")
    @Uninterruptible(reason = REASON_REFERENCES_ON_STACK)
    @SuppressWarnings("unused")
    public static long leaveInterpreterStub(CFunctionPointer entryPoint, Pointer leaveData, long stackSize, boolean returnInFpRegister) {
        /*
         * The backend overwrites this value and makes the stub return the raw result of invoking
         * entryPoint instead. Nevertheless, it relies on entryPoint.rawValue() being in the integer
         * return register, so this Java method must not return a different value.
         */
        return entryPoint.rawValue();
    }

    @Uninterruptible(reason = REASON_DEOPT_INSTALLED_CODE)
    public static Object leaveInterpreter(CFunctionPointer compiledEntryPoint, InterpreterResolvedJavaMethod seedMethod, Object[] args) {
        PreparedSignature compiledSignature = seedMethod.getPreparedSignature();
        return leaveInterpreter(compiledEntryPoint, args, compiledSignature);
    }

    /**
     * Enters Ristretto OSR-compiled code without marshaling Java method-entry arguments.
     *
     * The OSR graph must not read Java ABI parameters. It materializes live locals and monitors through
     * {@code RistrettoOSRSupport}'s thread-local transfer state instead. The original prepared signature is
     * still used for the return kind and for the outgoing stack area size required by the leave stub.
     */
    @Uninterruptible(reason = REASON_DEOPT_INSTALLED_CODE)
    public static Object leaveInterpreterOSR(CFunctionPointer compiledEntryPoint, InterpreterResolvedJavaMethod seedMethod) {
        PreparedSignature compiledSignature = seedMethod.getPreparedSignature();
        return leaveInterpreter(compiledEntryPoint, null, compiledSignature);
    }

    @Uninterruptible(reason = REASON_DEOPT_INSTALLED_CODE)
    private static Object leaveInterpreter(CFunctionPointer compiledEntryPoint, Object[] args, PreparedSignature compiledSignature) {
        VMError.guarantee(compiledSignature != null);
        InterpreterAccessStubData accessHelper = ImageSingletons.lookup(InterpreterAccessStubData.class);
        Pointer leaveData = StackValue.get(accessHelper.allocateStubDataSize());

        int stackSize = getStackSize(compiledSignature);
        assert stackSize > 0 : "Stack size should include deopt slot.";
        Pointer stackBuffer = allocateStackBuffer(accessHelper, leaveData, stackSize, true);
        try {
            // GR-55022: Stack overflow check should be done here
            return leaveInterpreter0(compiledEntryPoint, args, compiledSignature, accessHelper, leaveData, stackSize);
        } finally {
            freeStackBuffer(stackBuffer, stackSize);
        }
    }

    @Uninterruptible(reason = REASON_DEOPT_INSTALLED_CODE)
    private static int getStackSize(PreparedSignature compiledSignature) {
        InterpreterStubSection stubSection = ImageSingletons.lookup(InterpreterStubSection.class);
        return NumUtil.roundUp(compiledSignature.getStackSize(), stubSection.target.stackAlignment);
    }

    @Uninterruptible(reason = REASON_DEOPT_INSTALLED_CODE)
    private static Pointer allocateStackBuffer(InterpreterAccessStubData accessHelper, Pointer leaveData, int stackSize, boolean saveStackSizeInDeoptSlot) {
        Pointer stackBuffer = Word.nullPointer();
        if (stackSize > 0) {
            stackBuffer = NullableNativeMemory.malloc(Word.unsigned(stackSize), NmtCategory.Interpreter);
            VMError.guarantee(stackBuffer.isNonNull(), "Out-of-memory while allocating interpreter-internal data.");
            accessHelper.setSp(leaveData, stackSize, stackBuffer, saveStackSizeInDeoptSlot);
        }
        return stackBuffer;
    }

    @Uninterruptible(reason = REASON_DEOPT_INSTALLED_CODE)
    private static void freeStackBuffer(Pointer stackBuffer, int stackSize) {
        if (stackSize > 0) {
            VMError.guarantee(stackBuffer.isNonNull());
            NativeMemory.free(stackBuffer);
        }
    }

    @Uninterruptible(reason = "References are put on the stack which the GC is unaware of.")
    private static Object leaveInterpreter0(CFunctionPointer compiledEntryPoint, Object[] args, PreparedSignature compiledSignature, InterpreterAccessStubData accessHelper, Pointer leaveData,
                    int stackSize) {
        int[] argumentTypes = compiledSignature.getArgumentTypes();
        int gpIdx = 0;
        int fpIdx = 0;

        if (args != null) {
            int argCount = argumentTypes.length;
            for (int i = 0; i < argCount; i++) {
                Object arg = args[i];
                assert gpIdx + fpIdx == i;
                int cArgType = argumentTypes[i];
                JavaKind argKind = PreparedSignature.getKind(cArgType);
                switch (argKind) {
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
        }

        VMError.guarantee(compiledEntryPoint.isNonNull());
        JavaKind returnKind = compiledSignature.getReturnKind();
        boolean returnInFpRegister = returnKind == JavaKind.Float || returnKind == JavaKind.Double;
        /*
         * leaveData should no longer be accessed by accessHelper after the stub call. This is
         * because leaveData is a pointer to the stack which may become invalid when virtual threads
         * are used.
         */
        long rawReturnValue = leaveInterpreterStub(compiledEntryPoint, leaveData, stackSize, returnInFpRegister);

        // @formatter:off
        return switch (compiledSignature.getReturnKind()) {
            case Boolean -> (rawReturnValue & 0xff) != 0;
            case Byte    -> (byte) rawReturnValue;
            case Short   -> (short) rawReturnValue;
            case Char    -> (char) rawReturnValue;
            case Int     -> (int) rawReturnValue;
            case Long    -> rawReturnValue;
            case Float   -> Float.intBitsToFloat((int) rawReturnValue);
            case Double  -> Double.longBitsToDouble(rawReturnValue);
            case Object  -> ((Pointer) Word.pointer(rawReturnValue)).toObject();
            case Void    -> null;
            default      -> throw VMError.shouldNotReachHereAtRuntime();
        };
        // @formatter:on
    }

    @Deoptimizer.DeoptStub(stubType = Deoptimizer.StubType.InterpreterLeaveJNIStub)
    @NeverInline("needs ABI boundary")
    @Uninterruptible(reason = REASON_REFERENCES_ON_STACK)
    @SuppressWarnings("unused")
    public static long leaveInterpreterJNIStub(CFunctionPointer entryPoint, Pointer leaveData, long stackSize, boolean returnInFpRegister) {
        /*
         * The backend overwrites this value and makes the stub return the raw result of invoking
         * entryPoint instead. Nevertheless, it relies on entryPoint.rawValue() being in the integer
         * return register, so this Java method must not return a different value.
         */
        return entryPoint.rawValue();
    }

    public static Object leaveInterpreterJNI(InterpreterResolvedJavaMethod seedMethod, Object[] args) throws Throwable {
        VMError.guarantee(seedMethod instanceof CremaResolvedJavaMethod, "Unexpected native interpreter method");

        CremaResolvedJavaMethod target = (CremaResolvedJavaMethod) seedMethod;
        PreparedSignature jniSignature = target.getJNIDowncallPreparedSignature();

        JNINativeLinkage linkage = target.getJNINativeLinkage();
        CFunctionPointer nativeEntryPoint = (CFunctionPointer) JNIMethodSupport.nativeCallAddress(linkage);

        Object receiverOrClass = target.isStatic() ? seedMethod.getDeclaringClass().getJavaClass() : args[0];
        VMError.guarantee(receiverOrClass != null);
        Object lockTarget = null;
        if (target.isSynchronized()) {
            lockTarget = receiverOrClass;
            MonitorSupport.singleton().monitorEnter(lockTarget, MonitorInflationCause.MONITOR_ENTER);
        }

        Object result;
        try {
            int handleFrame = JNIMethodSupport.nativeCallPrologue();
            try {
                Pointer stackBuffer = Word.nullPointer();
                InterpreterAccessStubData accessHelper = ImageSingletons.lookup(InterpreterAccessStubData.class);
                Pointer leaveData = StackValue.get(accessHelper.allocateStubDataSize());
                int stackSize = getStackSize(jniSignature);
                try {
                    stackBuffer = allocateStackBuffer(accessHelper, leaveData, stackSize, false);
                    result = leaveInterpreterJNI(nativeEntryPoint, args, jniSignature, accessHelper, leaveData, receiverOrClass, target.hasReceiver(), JNIMethodSupport.environment());
                } finally {
                    freeStackBuffer(stackBuffer, stackSize);
                }
            } finally {
                JNIMethodSupport.nativeCallEpilogue(handleFrame);
            }
        } finally {
            if (lockTarget != null) {
                MonitorSupport.singleton().monitorExit(lockTarget, MonitorInflationCause.VM_INTERNAL);
            }
        }
        JNIMethodSupport.rethrowPendingException();
        return result;
    }

    @Uninterruptible(reason = REASON_DEOPT_INSTALLED_CODE)
    public static Object leaveInterpreterJNI(CFunctionPointer nativeEntryPoint, Object[] args, PreparedSignature jniSignature, InterpreterAccessStubData accessHelper, Pointer leaveData,
                    Object receiverOrClass, boolean hasReceiver, JNIEnvironment jniEnvironment) {
        int[] argumentTypes = jniSignature.getArgumentTypes();
        int gpPos = 0;
        accessHelper.setGpArgumentAtOutgoingJNI(argumentTypes[0], leaveData, gpPos++, jniEnvironment.rawValue());
        accessHelper.setGpArgumentAtOutgoingJNI(argumentTypes[1], leaveData, gpPos++, JNIMethodSupport.boxObjectInLocalHandle(receiverOrClass).rawValue());
        int fpPos = 0;
        int argCount = argumentTypes.length;
        int argsIndex = hasReceiver ? 1 : 0;
        for (int i = 2; i < argCount; i++) {
            if (Platform.includedIn(InternalPlatform.WINDOWS_BASE.class) && Platform.includedIn(Platform.AMD64.class)) {
                /*
                 * Windows AMD64 native calls consume GP and FP register positions strictly by
                 * argument index.
                 */
                gpPos = i;
                fpPos = i;
            }
            Object arg = args[argsIndex++];
            int cArgType = argumentTypes[i];
            JavaKind argKind = PreparedSignature.getKind(cArgType);
            switch (argKind) {
                case Boolean:
                    accessHelper.setGpArgumentAtOutgoingJNI(cArgType, leaveData, gpPos, (boolean) arg ? 1 : 0);
                    gpPos++;
                    break;
                case Byte:
                    accessHelper.setGpArgumentAtOutgoingJNI(cArgType, leaveData, gpPos, (byte) arg);
                    gpPos++;
                    break;
                case Short:
                    accessHelper.setGpArgumentAtOutgoingJNI(cArgType, leaveData, gpPos, (short) arg);
                    gpPos++;
                    break;
                case Char:
                    accessHelper.setGpArgumentAtOutgoingJNI(cArgType, leaveData, gpPos, (char) arg);
                    gpPos++;
                    break;
                case Int:
                    accessHelper.setGpArgumentAtOutgoingJNI(cArgType, leaveData, gpPos, (int) arg);
                    gpPos++;
                    break;
                case Long:
                    accessHelper.setGpArgumentAtOutgoingJNI(cArgType, leaveData, gpPos, (long) arg);
                    gpPos++;
                    break;
                case Object:
                    accessHelper.setGpArgumentAtOutgoingJNI(cArgType, leaveData, gpPos, JNIMethodSupport.boxObjectInLocalHandle(arg).rawValue());
                    gpPos++;
                    break;
                case Float:
                    accessHelper.setFpArgumentAtJNI(cArgType, leaveData, fpPos, Float.floatToRawIntBits((float) arg));
                    fpPos++;
                    break;
                case Double:
                    accessHelper.setFpArgumentAtJNI(cArgType, leaveData, fpPos, Double.doubleToRawLongBits((double) arg));
                    fpPos++;
                    break;

                default:
                    throw VMError.shouldNotReachHereAtRuntime();
            }
        }

        VMError.guarantee(nativeEntryPoint.isNonNull());
        JavaKind returnKind = jniSignature.getReturnKind();
        boolean returnInFpRegister = returnKind == JavaKind.Float || returnKind == JavaKind.Double;
        int stackSize = getStackSize(jniSignature);
        CFunctionPrologueNode.cFunctionPrologue(StatusSupport.STATUS_IN_NATIVE);
        /*
         * leaveData should no longer be accessed by accessHelper after the stub call. This is
         * because leaveData is a pointer to the stack which may become invalid when virtual threads
         * are used.
         */
        long rawReturnValue = leaveInterpreterJNIStub(nativeEntryPoint, leaveData, stackSize, returnInFpRegister);
        CFunctionEpilogueNode.cFunctionEpilogue(StatusSupport.STATUS_IN_NATIVE);

        return switch (returnKind) {
            case Boolean -> (rawReturnValue & 0xff) != 0;
            case Byte -> (byte) rawReturnValue;
            case Short -> (short) rawReturnValue;
            case Char -> (char) rawReturnValue;
            case Int -> (int) rawReturnValue;
            case Long -> rawReturnValue;
            case Float -> Float.intBitsToFloat((int) rawReturnValue);
            case Double -> Double.longBitsToDouble(rawReturnValue);
            case Object -> JNIMethodSupport.unboxHandle(Word.pointer(rawReturnValue));
            case Void -> null;
            default -> throw VMError.shouldNotReachHereAtRuntime();
        };
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

        private static boolean checkFastPath = false;

        public static void enableFastPathCheck() {
            VMError.guarantee(InterpreterOptions.InterpreterBackdoor.getValue());
            checkFastPath = true;
        }

        public static void disableFastPathCheck() {
            VMError.guarantee(InterpreterOptions.InterpreterBackdoor.getValue());
            checkFastPath = false;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public static void handleFastPathWasNotTaken() {
            if (InterpreterOptions.InterpreterBackdoor.getValue() && checkFastPath) {
                VMError.shouldNotReachHere("JIT method is available, but the fast path was not taken.");
            }
        }
    }

    /**
     * Try to query {@link InstalledCode} for the given {@link InterpreterResolvedJavaMethod}. In
     * order to guarantee that no safepoint happens during this operation and the execution of the
     * first instruction inside {@code installedCode} this method is marked as
     * {@code Uninterruptible}. Callers must ensure no safepoint happens between the return and the
     * call to the installed code.
     */
    @Uninterruptible(reason = REASON_DEOPT_INSTALLED_CODE, callerMustBe = true)
    private static CFunctionPointer getInstalledCodeEntryPoint(InterpreterResolvedJavaMethod interpreterMethod) {
        if (!SubstrateOptions.useRistretto()) {
            return Word.nullPointer();
        }
        RistrettoMethod rMethod = (com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod) interpreterMethod.getRistrettoMethod();
        if (rMethod != null) {
            SubstrateInstalledCodeImpl ic = rMethod.installedCode;
            // entryPoint != isValid (means it is not deoptimized yet)
            if (ic != null && ic.getEntryPoint() != 0) {
                return Word.pointer(ic.getEntryPoint());
            }
        }
        return Word.nullPointer();
    }

    @Uninterruptible(reason = REASON_DEOPT_INSTALLED_CODE)
    public static Object call(InterpreterResolvedJavaMethod interpreterMethod, Object[] args, boolean callerIsCompiled) {
        /*
         * Determine if a JIT compiled version is available and if so execute this one instead.
         */
        CFunctionPointer entryPoint = getInstalledCodeEntryPoint(interpreterMethod);
        if (entryPoint.isNonNull()) {
            if (callerIsCompiled) {
                TestingBackdoor.handleFastPathWasNotTaken();
            }
            return leaveInterpreter(entryPoint, interpreterMethod, args);
        }

        return callInterpreterInterruptibly(interpreterMethod, args);
    }

    @Uninterruptible(reason = "No JIT compiled code found, so it is safe to switch to interruptible code.", calleeMustBe = false)
    private static Object callInterpreterInterruptibly(InterpreterResolvedJavaMethod interpreterMethod, Object[] args) {
        return Interpreter.execute(interpreterMethod, args);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException uncheckedThrow(Throwable t) throws T {
        throw (T) t;
    }
}
