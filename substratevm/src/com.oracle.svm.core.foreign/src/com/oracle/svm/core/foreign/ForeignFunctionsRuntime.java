/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.foreign;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;

import java.lang.constant.DirectMethodHandleDesc;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySegment.Scope;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.MissingForeignRegistrationError;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.ForeignSupport;
import com.oracle.svm.core.FunctionPointerHolder;
import com.oracle.svm.core.MissingRegistrationUtils;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.InvokeJavaFunctionPointer;
import com.oracle.svm.core.foreign.AbiUtils.TrampolineTemplate;
import com.oracle.svm.core.foreign.phases.SubstrateOptimizeSharedArenaAccessPhase.OptimizeSharedArenaConfig;
import com.oracle.svm.core.graal.code.SubstrateBackendWithAssembler;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.headers.WindowsAPIs;
import com.oracle.svm.core.image.DisallowedImageHeapObjects.DisallowedObjectReporter;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.word.Word;
import jdk.internal.foreign.CABI;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.abi.CapturableState;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class ForeignFunctionsRuntime implements ForeignSupport, OptimizeSharedArenaConfig {
    @Fold
    public static ForeignFunctionsRuntime singleton() {
        return ImageSingletons.lookup(ForeignFunctionsRuntime.class);
    }

    private final AbiUtils.TrampolineTemplate trampolineTemplate;

    private final EconomicMap<NativeEntryPointInfo, FunctionPointerHolder> downcallStubs = ImageHeapMap.create("downcallStubs");
    private final EconomicMap<Pair<DirectMethodHandleDesc, JavaEntryPointInfo>, FunctionPointerHolder> directUpcallStubs = ImageHeapMap.create("directUpcallStubs");
    private final EconomicMap<JavaEntryPointInfo, FunctionPointerHolder> upcallStubs = ImageHeapMap.create("upcallStubs");
    private final EconomicSet<ResolvedJavaType> neverAccessesSharedArenaTypes = EconomicSet.create();
    private final EconomicSet<ResolvedJavaMethod> neverAccessesSharedArenaMethods = EconomicSet.create();

    private final Map<Long, TrampolineSet> trampolines = new HashMap<>();
    private TrampolineSet currentTrampolineSet;

    // for testing: callback if direct upcall lookup succeeded
    private BiConsumer<Long, DirectMethodHandleDesc> usingSpecializedUpcallListener;

    @Platforms(Platform.HOSTED_ONLY.class)
    public ForeignFunctionsRuntime(AbiUtils abiUtils) {
        this.trampolineTemplate = new TrampolineTemplate(new byte[abiUtils.trampolineSize()]);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void generateTrampolineTemplate(SubstrateBackendWithAssembler<?> backend) {
        AbiUtils.singleton().generateTrampolineTemplate(backend, this.trampolineTemplate);
    }

    public static boolean areFunctionCallsSupported() {
        return switch (CABI.current()) {
            case CABI.SYS_V, CABI.WIN_64, CABI.MAC_OS_AARCH_64, CABI.LINUX_AARCH_64 -> true;
            default -> false;
        };
    }

    public static RuntimeException functionCallsUnsupported() {
        assert SubstrateOptions.isForeignAPIEnabled();
        throw VMError.unsupportedFeature("Calling foreign functions is currently not supported on platform: " +
                        (OS.getCurrent().className + "-" + SubstrateUtil.getArchitectureName()).toLowerCase(Locale.ROOT));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean downcallStubExists(NativeEntryPointInfo nep) {
        return downcallStubs.containsKey(nep);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public int getDowncallStubsCount() {
        return downcallStubs.size();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean upcallStubExists(JavaEntryPointInfo jep) {
        return upcallStubs.containsKey(jep);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public int getUpcallStubsCount() {
        return upcallStubs.size();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean directUpcallStubExists(DirectMethodHandleDesc desc, JavaEntryPointInfo jep) {
        return directUpcallStubs.containsKey(Pair.create(desc, jep));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public int getDirectUpcallStubsCount() {
        return directUpcallStubs.size();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean addDowncallStubPointer(NativeEntryPointInfo nep, CFunctionPointer ptr) {
        return downcallStubs.putIfAbsent(nep, new FunctionPointerHolder(ptr)) == null;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean addUpcallStubPointer(JavaEntryPointInfo jep, CFunctionPointer ptr) {
        return upcallStubs.putIfAbsent(jep, new FunctionPointerHolder(ptr)) == null;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean addDirectUpcallStubPointer(DirectMethodHandleDesc desc, JavaEntryPointInfo jep, CFunctionPointer ptr) {
        var key = Pair.create(desc, jep);
        return directUpcallStubs.putIfAbsent(key, new FunctionPointerHolder(ptr)) == null;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerSafeArenaAccessorClass(ResolvedJavaType type) {
        neverAccessesSharedArenaTypes.add(type);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerSafeArenaAccessorMethod(ResolvedJavaMethod method) {
        neverAccessesSharedArenaMethods.add(method);
    }

    /**
     * We'd rather report the function descriptor than the native method type, but we don't have it
     * available here. One could intercept this exception in
     * {@link jdk.internal.foreign.abi.DowncallLinker#getBoundMethodHandle} and add information
     * about the descriptor there.
     */
    CFunctionPointer getDowncallStubPointer(NativeEntryPointInfo nep) {
        FunctionPointerHolder holder = downcallStubs.get(nep);
        if (holder == null) {
            throw MissingForeignRegistrationUtils.reportDowncall(nep);
        }
        return holder.functionPointer;
    }

    CFunctionPointer getUpcallStubPointer(JavaEntryPointInfo jep) {
        FunctionPointerHolder holder = upcallStubs.get(jep);
        if (holder == null) {
            throw MissingForeignRegistrationUtils.reportUpcall(jep);
        }
        return holder.functionPointer;
    }

    Pointer registerForUpcall(MethodHandle methodHandle, JavaEntryPointInfo jep) {
        if (!areFunctionCallsSupported()) {
            throw functionCallsUnsupported();
        }
        /*
         * Look up the upcall stub pointer first to avoid unnecessary allocation and synchronization
         * if it doesn't exist.
         */
        CFunctionPointer upcallStubPointer = getUpcallStubPointer(jep);
        synchronized (trampolines) {
            if (currentTrampolineSet == null || !currentTrampolineSet.hasFreeTrampolines()) {
                currentTrampolineSet = new TrampolineSet(trampolineTemplate);
                trampolines.put(currentTrampolineSet.base().rawValue(), currentTrampolineSet);
            }
            return currentTrampolineSet.assignTrampoline(methodHandle, upcallStubPointer);
        }
    }

    /**
     * Updates the stub address in the upcall trampoline with the address of a direct upcall stub.
     * The trampoline is identified by the given native address and the direct upcall stub is
     * identified by the method handle descriptor.
     *
     * @param trampolineAddress The address of the upcall trampoline.
     * @param desc A direct method handle descriptor used to lookup the direct upcall stub.
     */
    void patchForDirectUpcall(long trampolineAddress, DirectMethodHandleDesc desc, FunctionDescriptor functionDescriptor, LinkerOptions options) {
        JavaEntryPointInfo jep = AbiUtils.singleton().makeJavaEntryPoint(functionDescriptor, options);
        FunctionPointerHolder functionPointerHolder = directUpcallStubs.get(Pair.create(desc, jep));
        if (functionPointerHolder == null) {
            return;
        }

        Pointer trampolinePointer = Word.pointer(trampolineAddress);
        Pointer trampolineSetBase = TrampolineSet.getAllocationBase(trampolinePointer);
        TrampolineSet trampolineSet = trampolines.get(trampolineSetBase.rawValue());
        if (trampolineSet == null) {
            return;
        }
        /*
         * Synchronizing on 'trampolineSet' is not necessary at this point since we are still in the
         * call context of 'Linker.upcallStub' and the allocated trampoline is owned by the
         * allocating thread until it returns from the call. Also, the trampoline cannot be free'd
         * between allocation and patching because the associated arena is still on the stack.
         */
        trampolineSet.patchTrampolineForDirectUpcall(trampolinePointer, functionPointerHolder.functionPointer);
        /*
         * If we reach this point, everything went fine and the trampoline was patched with the
         * specialized upcall stub's address. For testing, now report that the lookup and patching
         * succeeded.
         */
        if (usingSpecializedUpcallListener != null) {
            usingSpecializedUpcallListener.accept(trampolineAddress, desc);
        }
    }

    public void setUsingSpecializedUpcallListener(BiConsumer<Long, DirectMethodHandleDesc> listener) {
        usingSpecializedUpcallListener = listener;
    }

    void freeTrampoline(long addr) {
        synchronized (trampolines) {
            long base = TrampolineSet.getAllocationBase(Word.pointer(addr)).rawValue();
            TrampolineSet trampolineSet = trampolines.get(base);
            if (trampolineSet.tryFree()) {
                trampolines.remove(base);
            }
        }
    }

    public static class MissingForeignRegistrationUtils extends MissingRegistrationUtils {
        public static MissingForeignRegistrationError reportDowncall(NativeEntryPointInfo nep) {
            MissingForeignRegistrationError mfre = new MissingForeignRegistrationError(foreignRegistrationMessage("downcall", nep.methodType()));
            report(mfre);
            return mfre;
        }

        public static MissingForeignRegistrationError reportUpcall(JavaEntryPointInfo jep) {
            MissingForeignRegistrationError mfre = new MissingForeignRegistrationError(foreignRegistrationMessage("upcall", jep.cMethodType()));
            report(mfre);
            return mfre;
        }

        private static String foreignRegistrationMessage(String failedAction, MethodType methodType) {
            return registrationMessage("perform " + failedAction + " with leaf type", methodType.toString(), "", "", "foreign", "foreign");
        }

        private static void report(MissingForeignRegistrationError exception) {
            StackTraceElement responsibleClass = getResponsibleClass(exception, foreignEntryPoints);
            MissingRegistrationUtils.report(exception, responsibleClass);
        }

        private static final Map<String, Set<String>> foreignEntryPoints = Map.of(
                        "jdk.internal.foreign.abi.AbstractLinker", Set.of(
                                        "downcallHandle",
                                        "upcallStub"));
    }

    /**
     * Arguments follow the same structure as described in {@link NativeEntryPointInfo}, with an
     * additional {@link Target_jdk_internal_foreign_abi_NativeEntryPoint} (NEP) as the last
     * argument, i.e.
     *
     * <pre>
     * {@code
     *      [return buffer address] <call address> [capture state address] <actual arg 1> <actual arg 2> ... <NEP>
     * }
     * </pre>
     *
     * where <actual arg i>s are the arguments which end up being passed to the C native function
     */
    @Override
    public Object linkToNative(Object... args) throws Throwable {
        Target_jdk_internal_foreign_abi_NativeEntryPoint nep = (Target_jdk_internal_foreign_abi_NativeEntryPoint) args[args.length - 1];
        StubPointer pointer = Word.pointer(nep.downcallStubAddress);
        /* The nep argument will be dropped in the invoked function */
        return pointer.invoke(args);
    }

    @Override
    public void onMemorySegmentReachable(Object memorySegmentObj, DisallowedObjectReporter reporter) {
        VMError.guarantee(memorySegmentObj instanceof MemorySegment);

        MemorySegment memorySegment = (MemorySegment) memorySegmentObj;
        if (memorySegment.isNative() && !MemorySegment.NULL.equals(memorySegment)) {
            throw reporter.raise("Detected a native MemorySegment in the image heap. " +
                            "A native MemorySegment has a pointer to unmanaged C memory, and C memory from the image generator is not available at image runtime.", memorySegment,
                            "Try avoiding to initialize the class that called 'MemorySegment.ofAddress'.");
        }
    }

    @Override
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+21/src/java.base/share/classes/java/lang/foreign/MemorySegment.java#L2708")
    public void onScopeReachable(Object scopeObj, DisallowedObjectReporter reporter) {
        VMError.guarantee(scopeObj instanceof Scope);

        /*
         * We never allow memory sessions with state 'OPEN' to be included in the image heap because
         * native memory may be associated with them which will be attempted to be free'd if the
         * session is closed. Non-closable or closed sessions are allowed.
         * 
         * Note: This assumes that there is only one implementor of interface Scope which is
         * MemorySessionImpl. If JDK's class hierarchy changes, we need to adapt this as well.
         */
        if (scopeObj instanceof MemorySessionImpl memorySessionImpl && memorySessionImpl.isAlive() && memorySessionImpl.isCloseable()) {
            throw reporter.raise("Detected an open but closable MemorySegment.Scope in the image heap. " +
                            "A MemorySegment.Scope may have associated unmanaged C memory that will be attempted to be free'd if the scope is closed. " +
                            "However, C memory from the image generator is no longer available at image runtime.", memorySessionImpl,
                            "Try avoiding to initialize the class that called 'Arena.ofConfined/ofShared'.");
        }
    }

    /**
     * Workaround for CapturableState.maskFromName(String) being interruptible.
     */
    @Fold
    static int getMask(String state) {
        return CapturableState.maskFromName(state);
    }

    @Fold
    static boolean isWindows() {
        return OS.WINDOWS.isCurrent();
    }

    /**
     * Note that the states must be captured in the same order as in the JDK: GET_LAST_ERROR,
     * WSA_GET_LAST_ERROR, ERRNO.
     *
     * Violation of the assertions should have already been caught in
     * {@link AbiUtils#checkLibrarySupport()}, which is called when registering the feature.
     */
    @Uninterruptible(reason = "Interruptions might change call state.")
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+22/src/hotspot/share/prims/downcallLinker.cpp")
    public static void captureCallState(int statesToCapture, CIntPointer captureBuffer) {
        assert statesToCapture != 0;
        assert captureBuffer.isNonNull();

        int i = 0;
        if (isWindows()) {
            assert WindowsAPIs.isSupported() : "Windows APIs should be supported on Windows OS";

            if ((statesToCapture & getMask("GetLastError")) != 0) {
                captureBuffer.write(i, WindowsAPIs.getLastError());
            }
            ++i;
            if ((statesToCapture & getMask("WSAGetLastError")) != 0) {
                captureBuffer.write(i, WindowsAPIs.wsaGetLastError());
            }
            ++i;
        }

        assert LibC.isSupported() : "LibC should always be supported";
        if ((statesToCapture & getMask("errno")) != 0) {
            captureBuffer.write(i, LibC.errno());
        }
        ++i;
    }

    @Platforms(Platform.HOSTED_ONLY.class)//
    public static final SnippetRuntime.SubstrateForeignCallDescriptor CAPTURE_CALL_STATE = SnippetRuntime.findForeignCall(ForeignFunctionsRuntime.class,
                    "captureCallState", HAS_SIDE_EFFECT, LocationIdentity.any());

    @Override
    public boolean isSafeCallee(ResolvedJavaMethod method) {
        if (neverAccessesSharedArenaMethods.contains(method)) {
            return true;
        }
        if (neverAccessesSharedArenaTypes.contains(method.getDeclaringClass())) {
            return true;
        }
        return false;
    }
}

interface StubPointer extends CFunctionPointer {
    @InvokeJavaFunctionPointer
    Object invoke(Object... args);
}
