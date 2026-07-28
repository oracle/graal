/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.windows;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.Parker;
import com.oracle.svm.core.thread.Parker.ParkerFactory;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.shared.util.TimeUtils;
import com.oracle.svm.core.windows.headers.Process;
import com.oracle.svm.core.windows.headers.SynchAPI;
import com.oracle.svm.core.windows.headers.WinBase;
import com.oracle.svm.guest.staging.core.thread.OSThreadHandle;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.core.common.NumUtil;

@AutomaticallyRegisteredImageSingleton(PlatformThreads.class)
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public final class WindowsPlatformThreads extends PlatformThreads {
    @Platforms(HOSTED_ONLY.class)
    WindowsPlatformThreads() {
    }

    /** This method must not throw any exceptions. */
    @Override
    protected IsolateThread doStartThread(Thread thread, long javaStackSize) {
        assert StackOverflowCheck.singleton().isYellowZoneAvailable();

        int nativeStackSize = NumUtil.safeToUInt(javaStackSize);
        int initFlag = 0;
        // If the caller requested a Java stack size, don't commit it all at once.
        if (nativeStackSize != 0) {
            initFlag |= Process.STACK_SIZE_PARAM_IS_A_RESERVATION();
        }
        return doStartThread0(thread, nativeStackSize, initFlag);
    }

    /**
     * Starts a thread to the point so that it is executing. This method must not throw any
     * exceptions.
     */
    private IsolateThread doStartThread0(Thread thread, int nativeStackSize, int initFlag) {
        assert StackOverflowCheck.singleton().isYellowZoneAvailable();

        IsolateThread isolateThread = prepareThreadStart(thread);
        if (isolateThread.isNull()) {
            return Word.nullPointer();
        }
        WinBase.HANDLE osThreadHandle = Process._beginthreadex(Word.nullPointer(), nativeStackSize, threadStartRoutine.getFunctionPointer(), isolateThread, initFlag, Word.nullPointer());
        if (osThreadHandle.isNull()) {
            undoPrepareStartOnError(thread, isolateThread);
            return Word.nullPointer();
        }
        WinBase.CloseHandle(osThreadHandle);
        return isolateThread;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public OSThreadHandle startThreadUnmanaged(CFunctionPointer threadRoutine, PointerBase userData, long stackSize, boolean isJavaStackSize) {
        // _beginthreadex takes an unsigned int stack size; reject values that cannot be preserved.
        if (stackSize > 0xFFFF_FFFFL) {
            return Word.nullPointer();
        }
        int initFlag = 0;

        // If the caller requested a stack size, don't commit it all at once.
        if (stackSize != 0) {
            initFlag |= Process.STACK_SIZE_PARAM_IS_A_RESERVATION();
        }

        WinBase.HANDLE osThreadHandle = Process.NoTransitions._beginthreadex(Word.nullPointer(), (int) stackSize,
                        threadRoutine, userData, initFlag, Word.nullPointer());
        return (OSThreadHandle) osThreadHandle;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean joinThreadUnmanaged(OSThreadHandle threadHandle, WordPointer threadExitStatus) {
        if (SynchAPI.NoTransitions.WaitForSingleObject((WinBase.HANDLE) threadHandle, SynchAPI.INFINITE()) != SynchAPI.WAIT_OBJECT_0()) {
            return false;
        }
        if (threadExitStatus.isNull()) {
            return true;
        }

        // Since only an int is written, first clear word
        threadExitStatus.write(Word.zero());
        return Process.NoTransitions.GetExitCodeThread((WinBase.HANDLE) threadHandle, (CIntPointer) threadExitStatus) != 0;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean supportsUnmanagedThreadLocal() {
        return true;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public ThreadLocalKey createUnmanagedThreadLocal() {
        int result = Process.NoTransitions.TlsAlloc();
        VMError.guarantee(result != Process.TLS_OUT_OF_INDEXES(), "TlsAlloc failed.");
        return Word.unsigned(result);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void deleteUnmanagedThreadLocal(ThreadLocalKey key) {
        int dwTlsIndex = (int) key.rawValue();
        int result = Process.NoTransitions.TlsFree(dwTlsIndex);
        VMError.guarantee(result != 0, "TlsFree failed.");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @SuppressWarnings("unchecked")
    public <T extends WordBase> T getUnmanagedThreadLocalValue(ThreadLocalKey key) {
        int dwTlsIndex = (int) key.rawValue();
        VoidPointer result = Process.NoTransitions.TlsGetValue(dwTlsIndex);
        assert result.isNonNull() || WinBase.GetLastError() == WinBase.ERROR_SUCCESS();
        return (T) result;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void setUnmanagedThreadLocalValue(ThreadLocalKey key, WordBase value) {
        int dwTlsIndex = (int) key.rawValue();
        int result = Process.NoTransitions.TlsSetValue(dwTlsIndex, (VoidPointer) value);
        VMError.guarantee(result != 0, "TlsSetValue failed.");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void closeOSThreadHandle(OSThreadHandle threadHandle) {
        WinBase.CloseHandle((WinBase.HANDLE) threadHandle);
    }

    /**
     * Windows doesn't support setting a native threads name unless process is attached to a
     * debugger.
     */
    @Override
    protected void setNativeName(Thread thread, String name) {
    }

    @Override
    protected void yieldCurrent() {
        Process.SwitchToThread();
    }
}

class WindowsParker extends Parker {
    private static final long MAX_DWORD = (1L << 32) - 1;

    /**
     * An opaque handle for an event object from the operating system. Event objects have explicit
     * set and reset operations. They can be waited on until they become set or a timeout occurs,
     * spurious wakeups cannot occur.
     */
    private WinBase.HANDLE eventHandle;

    WindowsParker() {
        eventHandle = SynchAPI.CreateEventA(Word.nullPointer(), 1, 0, Word.nullPointer());
        VMError.guarantee(eventHandle.rawValue() != 0, "CreateEventA failed");
    }

    @Override
    protected void reset() {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            int status = SynchAPI.ResetEvent(eventHandle);
            VMError.guarantee(status != 0, "ResetEvent failed");
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @Override
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-23+26/src/hotspot/os/windows/os_windows.cpp#L5672-L5714")
    protected void park(boolean isAbsolute, long time) {
        assert time >= 0 && !(isAbsolute && time == 0) : "must not be called otherwise";

        long millis;
        if (time == 0) {
            millis = SynchAPI.INFINITE() & 0xFFFFFFFFL;
        } else if (isAbsolute) {
            millis = time - TimeUtils.currentTimeMillis();
            if (millis <= 0) {
                /* Already elapsed. */
                return;
            }
        } else {
            /* Coarsen from nanos to millis. */
            millis = TimeUtils.divideNanosToMillis(time);
            if (millis == 0) {
                /* Wait for the minimal time. */
                millis = 1;
            }
        }

        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            int status = SynchAPI.WaitForSingleObject(eventHandle, 0);
            if (status == SynchAPI.WAIT_OBJECT_0()) {
                /* There was already a notification pending. */
                SynchAPI.ResetEvent(eventHandle);
            } else {
                status = SynchAPI.WaitForSingleObject(eventHandle, toDword(millis));
                SynchAPI.ResetEvent(eventHandle);
            }
            assert status == SynchAPI.WAIT_OBJECT_0() || status == SynchAPI.WAIT_TIMEOUT();
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    /* DWORD is an unsigned 32-bit value. */
    private static int toDword(long value) {
        assert value >= 0;
        if (value > MAX_DWORD) {
            return (int) MAX_DWORD;
        }
        return (int) value;
    }

    @Override
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-23+26/src/hotspot/os/windows/os_windows.cpp#L5716-L5719")
    protected void unpark() {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            int status = SynchAPI.SetEvent(eventHandle);
            VMError.guarantee(status != 0, "SetEvent failed");
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void release() {
        WinBase.CloseHandle(eventHandle);
        eventHandle = WinBase.INVALID_HANDLE_VALUE();
    }
}

@AutomaticallyRegisteredImageSingleton(ParkerFactory.class)
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
class WindowsParkerFactory implements ParkerFactory {
    @Override
    public Parker acquire() {
        return new WindowsParker();
    }
}
