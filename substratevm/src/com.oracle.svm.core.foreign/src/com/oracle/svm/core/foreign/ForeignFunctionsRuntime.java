/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.FunctionPointerHolder;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.headers.WindowsAPIs;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.internal.foreign.abi.CapturableState;

public class ForeignFunctionsRuntime {
    @Fold
    public static ForeignFunctionsRuntime singleton() {
        return ImageSingletons.lookup(ForeignFunctionsRuntime.class);
    }

    private final AbiUtils.TrampolineTemplate trampolineTemplate = AbiUtils.singleton().generateTrampolineTemplate();
    private final EconomicMap<NativeEntryPointInfo, FunctionPointerHolder> downcallStubs = EconomicMap.create();
    private final EconomicMap<JavaEntryPointInfo, FunctionPointerHolder> upcallStubs = EconomicMap.create();

    private final Map<Long, TrampolineSet> trampolines = new HashMap<>();
    private TrampolineSet currentTrampolineSet;

    @Platforms(Platform.HOSTED_ONLY.class)
    public ForeignFunctionsRuntime() {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void addDowncallStubPointer(NativeEntryPointInfo nep, CFunctionPointer ptr) {
        VMError.guarantee(!downcallStubs.containsKey(nep), "Seems like multiple stubs were generated for %s", nep);
        VMError.guarantee(downcallStubs.put(nep, new FunctionPointerHolder(ptr)) == null);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void addUpcallStubPointer(JavaEntryPointInfo jep, CFunctionPointer ptr) {
        VMError.guarantee(!upcallStubs.containsKey(jep), "Seems like multiple stubs were generated for " + jep);
        VMError.guarantee(upcallStubs.put(jep, new FunctionPointerHolder(ptr)) == null);
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
            throw new UnregisteredForeignStubException(nep);
        }
        return holder.functionPointer;
    }

    CFunctionPointer getUpcallStubPointer(JavaEntryPointInfo jep) {
        FunctionPointerHolder holder = upcallStubs.get(jep);
        if (holder == null) {
            throw new UnregisteredForeignStubException(jep);
        }
        return holder.functionPointer;
    }

    Pointer registerForUpcall(MethodHandle methodHandle, JavaEntryPointInfo jep) {
        synchronized (trampolines) {
            if (currentTrampolineSet == null || !currentTrampolineSet.hasFreeTrampolines()) {
                currentTrampolineSet = new TrampolineSet(trampolineTemplate);
                trampolines.put(currentTrampolineSet.base().rawValue(), currentTrampolineSet);
            }
            return currentTrampolineSet.assignTrampoline(methodHandle, getUpcallStubPointer(jep));
        }
    }

    void freeTrampoline(long addr) {
        synchronized (trampolines) {
            long base = TrampolineSet.getAllocationBase(WordFactory.pointer(addr)).rawValue();
            TrampolineSet trampolineSet = trampolines.get(base);
            if (trampolineSet.tryFree()) {
                trampolines.remove(base);
            }
        }
    }

    @SuppressWarnings("serial")
    public static class UnregisteredForeignStubException extends RuntimeException {

        UnregisteredForeignStubException(NativeEntryPointInfo nep) {
            super(generateMessage(nep));
        }

        UnregisteredForeignStubException(JavaEntryPointInfo jep) {
            super(generateMessage(jep));
        }

        private static String generateMessage(NativeEntryPointInfo nep) {
            return "Cannot perform downcall with leaf type " + nep.methodType() + " as it was not registered at compilation time.";
        }

        private static String generateMessage(JavaEntryPointInfo jep) {
            return "Cannot perform upcall with leaf type " + jep.cMethodType() + " as it was not registered at compilation time.";
        }
    }

    /**
     * Workaround for CapturableState.mask() being interruptible.
     */
    @Fold
    static int getMask(CapturableState state) {
        return state.mask();
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
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+12/src/hotspot/share/prims/downcallLinker.cpp")
    public static void captureCallState(int statesToCapture, CIntPointer captureBuffer) {
        assert statesToCapture != 0;
        assert captureBuffer.isNonNull();

        int i = 0;
        if (isWindows()) {
            assert WindowsAPIs.isSupported() : "Windows APIs should be supported on Windows OS";

            if ((statesToCapture & getMask(CapturableState.GET_LAST_ERROR)) != 0) {
                captureBuffer.write(i, WindowsAPIs.getLastError());
            }
            ++i;
            if ((statesToCapture & getMask(CapturableState.WSA_GET_LAST_ERROR)) != 0) {
                captureBuffer.write(i, WindowsAPIs.wsaGetLastError());
            }
            ++i;
        }

        assert LibC.isSupported() : "LibC should always be supported";
        if ((statesToCapture & getMask(CapturableState.ERRNO)) != 0) {
            captureBuffer.write(i, LibC.errno());
        }
        ++i;
    }

    @Platforms(Platform.HOSTED_ONLY.class)//
    public static final SnippetRuntime.SubstrateForeignCallDescriptor CAPTURE_CALL_STATE = SnippetRuntime.findForeignCall(ForeignFunctionsRuntime.class,
                    "captureCallState", HAS_SIDE_EFFECT, LocationIdentity.any());
}
