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
package com.oracle.svm.core.foreign;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;
import java.lang.invoke.MethodHandle;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.FunctionPointerHolder;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.headers.WindowsAPIs;
import com.oracle.svm.core.heap.OutOfMemoryUtil;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.util.VMError;

import jdk.internal.foreign.abi.CapturableState;

public class ForeignFunctionsRuntime {
    @Fold
    public static ForeignFunctionsRuntime singleton() {
        return ImageSingletons.lookup(ForeignFunctionsRuntime.class);
    }

    private final AbiUtils.TrampolineTemplate trampolineTemplate;
    private final EconomicMap<NativeEntryPointInfo, FunctionPointerHolder> downcallStubs = EconomicMap.create();
    private final EconomicMap<JavaEntryPointInfo, FunctionPointerHolder> upcallStubs = EconomicMap.create();

    public static final class PointerHolder {
        public final Pointer pointer;

        public PointerHolder(Pointer pointer) {
            this.pointer = pointer;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PointerHolder that = (PointerHolder) o;
            return pointer.equal(that.pointer);
        }

        @Override
        public int hashCode() {
            return Long.hashCode(pointer.rawValue());
        }
    }

    /* TODO: Fix inevitable concurrency issues */
    private final Map<PointerHolder, TrampolineSet> trampolines = new ConcurrentHashMap<>();
    private TrampolineSet currentTrampolineSet;

    public ForeignFunctionsRuntime(AbiUtils.TrampolineTemplate trampolineTemplate) {
        this.trampolineTemplate = trampolineTemplate;
    }

    private class TrampolineSet {
        private static UnsignedWord allocationSize() {
            return VirtualMemoryProvider.get().getGranularity();
        }

        private static int maxTrampolineCount() {
            long result = allocationSize().rawValue() / AbiUtils.singleton().trampolineSize();
            assert result < Integer.MAX_VALUE;
            return (int) result;
        }

        public static Pointer getAllocationBase(Pointer ptr) {
            long offset = ptr.rawValue() % allocationSize().rawValue();
            assert offset <= allocationSize().rawValue();
            assert offset <= Integer.MAX_VALUE;
            assert offset % AbiUtils.singleton().trampolineSize() == 0;
            return ptr.subtract((int) offset);
        }

        private final static int FREED = -1;

        private final Set<PinnedObject> pins;
        /*
         * Invariant: {@code freed <= assigned <= trampolineCount}
         */
        private int assigned; // Contains FREED after being freed
        private int freed;
        private final int trampolineCount;
        private final Pointer trampolines;

        private final PointerBase[] methodHandles;
        private final CFunctionPointer[] stubs;

        private PinnedObject pin(Object object) {
            PinnedObject pinned = PinnedObject.create(object);
            this.pins.add(pinned);
            return pinned;
        }

        public TrampolineSet() {
            assert allocationSize().rawValue() % AbiUtils.singleton().trampolineSize() == 0;
            this.pins = new HashSet<>();
            this.assigned = 0;
            this.freed = 0;

            this.trampolineCount = maxTrampolineCount();
            assert this.trampolineCount <= maxTrampolineCount();

            this.methodHandles = new PointerBase[trampolineCount];
            this.stubs = new CFunctionPointer[trampolineCount];
            this.trampolines = generateTrampolines(pin(this.methodHandles), pin(this.stubs));
        }

        public Pointer base() {
            return trampolines;
        }

        public boolean hasFreeTrampolines() {
            assert (0 <= assigned && assigned <= trampolineCount) || assigned == FREED;
            return assigned != FREED && assigned != trampolineCount;
        }

        public Pointer assignTrampoline(MethodHandle methodHandle, JavaEntryPointInfo jep) {
            assert hasFreeTrampolines();

            PinnedObject pinned = pin(methodHandle);
            int id = assigned++;
            CFunctionPointer stub = getUpcallStubPointer(jep);

            methodHandles[id] = pinned.addressOfObject();
            stubs[id] = stub;

            return trampolines.add(id * AbiUtils.singleton().trampolineSize());
        }

        private Pointer generateTrampolines(PinnedObject mhsArray, PinnedObject stubsArray) {
            VirtualMemoryProvider memoryProvider = VirtualMemoryProvider.get();
            UnsignedWord pageSize = allocationSize();
            /* We request a specific alignment to guarantee correctness of getAllocationBase */
            Pointer page = memoryProvider.reserve(pageSize, pageSize, true);
            page = memoryProvider.commit(page, pageSize, VirtualMemoryProvider.Access.WRITE | VirtualMemoryProvider.Access.FUTURE_EXECUTE);
            Pointer it = page;
            Pointer end = page.add(pageSize);

            if (page.isNull()) {
                OutOfMemoryError outOfMemoryError = new OutOfMemoryError("Could not allocate memory for trampolines.");
                throw OutOfMemoryUtil.reportOutOfMemoryError(outOfMemoryError);
            }

            for (int i = 0; i < this.trampolineCount; ++i) {
                VMError.guarantee(getAllocationBase(it).equal(page));
                it = trampolineTemplate.write(it, CurrentIsolate.getIsolate(), mhsArray.addressOfArrayElement(i), stubsArray.addressOfArrayElement(i));
            }
            assert it.belowOrEqual(end);

            memoryProvider.protect(page, pageSize, VirtualMemoryProvider.Access.EXECUTE);

            return page;
        }

        private boolean tryFree() {
            ++this.freed;
            assert this.freed <= this.trampolineCount;
            if (this.freed < this.trampolineCount) {
                return false;
            }
            for (PinnedObject pinned : this.pins) {
                pinned.close();
            }
            VirtualMemoryProvider.get().free(this.trampolines, allocationSize());
            this.assigned = FREED;
            return true;
        }
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
    public CFunctionPointer getDowncallStubPointer(NativeEntryPointInfo nep) {
        FunctionPointerHolder pointer = downcallStubs.get(nep);
        if (pointer == null) {
            throw new UnregisteredForeignStubException(nep);
        } else {
            return pointer.functionPointer;
        }
    }

    public CFunctionPointer getUpcallStubPointer(JavaEntryPointInfo jep) {
        FunctionPointerHolder pointer = upcallStubs.get(jep);
        if (pointer == null) {
            throw new UnregisteredForeignStubException(jep);
        } else {
            return pointer.functionPointer;
        }
    }

    public Pointer registerForUpcall(MethodHandle methodHandle, JavaEntryPointInfo jep) {
        if (this.currentTrampolineSet == null || !this.currentTrampolineSet.hasFreeTrampolines()) {
            this.currentTrampolineSet = new TrampolineSet();
            this.trampolines.put(new PointerHolder(this.currentTrampolineSet.base()), this.currentTrampolineSet);
        }
        return this.currentTrampolineSet.assignTrampoline(methodHandle, jep);
    }

    public void freeTrampoline(long addr) {
        Pointer ptr = TrampolineSet.getAllocationBase(WordFactory.pointer(addr));
        PointerHolder key = new PointerHolder(ptr);
        TrampolineSet trampolineSet = trampolines.get(key);
        VMError.guarantee(trampolineSet != null);
        if (trampolineSet.tryFree()) {
            trampolines.remove(key);
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
    public static int getMask(CapturableState state) {
        return state.mask();
    }

    @Fold
    public static boolean isWindows() {
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
