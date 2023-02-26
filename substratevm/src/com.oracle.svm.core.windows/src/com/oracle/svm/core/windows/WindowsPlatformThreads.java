/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointSetup.LeaveDetachThreadEpilogue;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.ParkEvent;
import com.oracle.svm.core.thread.ParkEvent.ParkEventFactory;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.windows.headers.Process;
import com.oracle.svm.core.windows.headers.SynchAPI;
import com.oracle.svm.core.windows.headers.WinBase;

@AutomaticallyRegisteredImageSingleton(PlatformThreads.class)
@Platforms(Platform.WINDOWS.class)
public final class WindowsPlatformThreads extends PlatformThreads {
    @Platforms(HOSTED_ONLY.class)
    WindowsPlatformThreads() {
    }

    @Override
    protected boolean doStartThread(Thread thread, long stackSize) {
        int threadStackSize = (int) stackSize;
        int initFlag = Process.CREATE_SUSPENDED();

        WindowsThreadStartData startData = prepareStart(thread, SizeOf.get(WindowsThreadStartData.class));

        // If caller specified a stack size, don't commit it all at once.
        if (threadStackSize != 0) {
            initFlag |= Process.STACK_SIZE_PARAM_IS_A_RESERVATION();
        }

        CIntPointer osThreadID = UnsafeStackValue.get(CIntPointer.class);
        WinBase.HANDLE osThreadHandle = Process._beginthreadex(WordFactory.nullPointer(), threadStackSize,
                        WindowsPlatformThreads.osThreadStartRoutine.getFunctionPointer(), startData, initFlag, osThreadID);
        if (osThreadHandle.isNull()) {
            undoPrepareStartOnError(thread, startData);
            return false;
        }
        startData.setOSThreadHandle(osThreadHandle);

        // Start the thread running
        Process.ResumeThread(osThreadHandle);
        return true;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public OSThreadHandle startThreadUnmanaged(CFunctionPointer threadRoutine, PointerBase userData, int stackSize) {
        int initFlag = 0;

        // If caller specified a stack size, don't commit it all at once.
        if (stackSize != 0) {
            initFlag |= Process.STACK_SIZE_PARAM_IS_A_RESERVATION();
        }

        WinBase.HANDLE osThreadHandle = Process.NoTransitions._beginthreadex(WordFactory.nullPointer(), stackSize,
                        threadRoutine, userData, initFlag, WordFactory.nullPointer());
        return (PlatformThreads.OSThreadHandle) osThreadHandle;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean joinThreadUnmanaged(OSThreadHandle threadHandle, WordPointer threadExitStatus) {
        if (SynchAPI.NoTransitions.WaitForSingleObject((WinBase.HANDLE) threadHandle, SynchAPI.INFINITE()) != SynchAPI.WAIT_OBJECT_0()) {
            return false;
        }
        if (Process.NoTransitions.GetExitCodeThread((WinBase.HANDLE) threadHandle, (CIntPointer) threadExitStatus) == 0) {
            return false;
        }
        return true;
    }

    @Override
    @SuppressWarnings("unused")
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

    @RawStructure
    interface WindowsThreadStartData extends ThreadStartData {

        @RawField
        WinBase.HANDLE getOSThreadHandle();

        @RawField
        void setOSThreadHandle(WinBase.HANDLE osHandle);
    }

    private static final CEntryPointLiteral<CFunctionPointer> osThreadStartRoutine = CEntryPointLiteral.create(WindowsPlatformThreads.class, "osThreadStartRoutine", WindowsThreadStartData.class);

    private static class OSThreadStartRoutinePrologue implements CEntryPointOptions.Prologue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString("Failed to attach a newly launched thread.");

        @SuppressWarnings("unused")
        @Uninterruptible(reason = "prologue")
        static void enter(WindowsThreadStartData data) {
            int code = CEntryPointActions.enterAttachThread(data.getIsolate(), true, false);
            if (code != CEntryPointErrors.NO_ERROR) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = OSThreadStartRoutinePrologue.class, epilogue = LeaveDetachThreadEpilogue.class)
    static WordBase osThreadStartRoutine(WindowsThreadStartData data) {
        ObjectHandle threadHandle = data.getThreadHandle();
        WinBase.HANDLE osThreadHandle = data.getOSThreadHandle();
        freeStartData(data);

        try {
            threadStartRoutine(threadHandle);
        } finally {
            /*
             * Note that there is another handle to the thread stored in VMThreads.OSThreadHandleTL.
             * This is necessary to ensure that the operating system does not release the thread
             * resources too early.
             */
            WinBase.CloseHandle(osThreadHandle);
        }
        return WordFactory.nullPointer();
    }
}

@Platforms(Platform.WINDOWS.class)
class WindowsParkEvent extends ParkEvent {

    /**
     * An opaque handle for an event object from the operating system. Event objects have explicit
     * set and reset operations. They can be waited on until they become set or a timeout occurs,
     * spurious wakeups cannot occur.
     */
    private WinBase.HANDLE eventHandle;

    WindowsParkEvent() {
        /* Create an auto-reset event. */
        eventHandle = SynchAPI.CreateEventA(WordFactory.nullPointer(), 0, 0, WordFactory.nullPointer());
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
    protected void condWait() {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            int status = SynchAPI.WaitForSingleObject(eventHandle, SynchAPI.INFINITE());
            if (status != SynchAPI.WAIT_OBJECT_0()) {
                Log.log().newline().string("WindowsParkEvent.condWait failed, status returned:  ").hex(status);
                Log.log().newline().string("GetLastError returned:  ").hex(WinBase.GetLastError()).newline();
                throw VMError.shouldNotReachHere("WaitForSingleObject failed");
            }
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @Override
    protected void condTimedWait(long durationNanos) {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            final int maxTimeout = 0x10_000_000;
            long durationMillis = Math.max(0, TimeUtils.roundUpNanosToMillis(durationNanos));
            do { // at least once to consume potential unpark
                int timeout = (durationMillis < maxTimeout) ? (int) durationMillis : maxTimeout;
                int status = SynchAPI.WaitForSingleObject(eventHandle, timeout);
                if (status == SynchAPI.WAIT_OBJECT_0()) {
                    break; // unparked
                } else if (status != SynchAPI.WAIT_TIMEOUT()) {
                    Log.log().newline().string("WindowsParkEvent.condTimedWait failed, status returned:  ").hex(status);
                    Log.log().newline().string("GetLastError returned:  ").hex(WinBase.GetLastError()).newline();
                    throw VMError.shouldNotReachHere("WaitForSingleObject failed");
                }
                durationMillis -= timeout;
            } while (durationMillis > 0);
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @Override
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

@AutomaticallyRegisteredImageSingleton(ParkEventFactory.class)
@Platforms(Platform.WINDOWS.class)
class WindowsParkEventFactory implements ParkEventFactory {
    @Override
    public ParkEvent acquire() {
        return new WindowsParkEvent();
    }
}
