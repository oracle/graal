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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.core.c.function.CEntryPointSetup.LeaveDetachThreadEpilogue;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.ParkEvent;
import com.oracle.svm.core.thread.ParkEvent.ParkEventFactory;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.windows.headers.Process;
import com.oracle.svm.core.windows.headers.SynchAPI;
import com.oracle.svm.core.windows.headers.WinBase;

@Platforms(Platform.WINDOWS.class)
public final class WindowsJavaThreads extends JavaThreads {
    @Platforms(HOSTED_ONLY.class)
    WindowsJavaThreads() {
    }

    @Override
    protected void doStartThread(Thread thread, long stackSize) {
        int threadStackSize = (int) stackSize;
        int initFlag = Process.CREATE_SUSPENDED();

        WindowsThreadStartData startData = UnmanagedMemory.malloc(SizeOf.get(WindowsThreadStartData.class));
        prepareStartData(thread, startData);

        // If caller specified a stack size, don't commit it all at once.
        if (threadStackSize != 0) {
            initFlag |= Process.STACK_SIZE_PARAM_IS_A_RESERVATION();
        }

        CIntPointer osThreadID = StackValue.get(CIntPointer.class);
        WinBase.HANDLE osThreadHandle = Process._beginthreadex(WordFactory.nullPointer(), threadStackSize, WindowsJavaThreads.osThreadStartRoutine.getFunctionPointer(), startData, initFlag,
                        osThreadID);
        VMError.guarantee(osThreadHandle.rawValue() != 0, "Could not create thread");
        startData.setOSThreadHandle(osThreadHandle);

        // Start the thread running
        Process.ResumeThread(osThreadHandle);
    }

    /**
     * Windows doesn't support setting a native threads name unless process is attached to a
     * debugger.
     */
    @Override
    protected void setNativeName(Thread thread, String name) {
    }

    @Override
    protected void yield() {
        Process.SwitchToThread();
    }

    @RawStructure
    interface WindowsThreadStartData extends ThreadStartData {

        @RawField
        WinBase.HANDLE getOSThreadHandle();

        @RawField
        void setOSThreadHandle(WinBase.HANDLE osHandle);
    }

    private static final CEntryPointLiteral<CFunctionPointer> osThreadStartRoutine = CEntryPointLiteral.create(WindowsJavaThreads.class, "osThreadStartRoutine", WindowsThreadStartData.class);

    private static class OSThreadStartRoutinePrologue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString("Failed to attach a newly launched thread.");

        @SuppressWarnings("unused")
        static void enter(WindowsThreadStartData data) {
            int code = CEntryPointActions.enterAttachThread(data.getIsolate(), false);
            if (code != CEntryPointErrors.NO_ERROR) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }

    @CEntryPoint
    @CEntryPointOptions(prologue = OSThreadStartRoutinePrologue.class, epilogue = LeaveDetachThreadEpilogue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static WordBase osThreadStartRoutine(WindowsThreadStartData data) {
        ObjectHandle threadHandle = data.getThreadHandle();
        WinBase.HANDLE osThreadHandle = data.getOSThreadHandle();
        UnmanagedMemory.free(data);

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
    private final WinBase.HANDLE eventHandle;

    WindowsParkEvent() {
        /* Create an auto-reset event. */
        eventHandle = SynchAPI.CreateEventA(WordFactory.nullPointer(), 0, 0, WordFactory.nullPointer());
        VMError.guarantee(eventHandle.rawValue() != 0, "CreateEventA failed");
    }

    @Override
    protected void reset() {
        SynchAPI.ResetEvent(eventHandle);
    }

    @Override
    protected void condWait() {
        int status = SynchAPI.WaitForSingleObject(eventHandle, SynchAPI.INFINITE());
        if (status != SynchAPI.WAIT_OBJECT_0()) {
            Log.log().newline().string("WindowsParkEvent.condWait failed, status returned:  ").hex(status);
            Log.log().newline().string("GetLastError returned:  ").hex(WinBase.GetLastError()).newline();
            throw VMError.shouldNotReachHere("WaitForSingleObject failed");
        }
    }

    @Override
    protected void condTimedWait(long delayNanos) {
        final int maxTimeout = 0x10_000_000;
        long delayMillis = Math.max(0, TimeUtils.roundUpNanosToMillis(delayNanos));
        do { // at least once to consume potential unpark
            int timeout = (delayMillis < maxTimeout) ? (int) delayMillis : maxTimeout;
            int status = SynchAPI.WaitForSingleObject(eventHandle, timeout);
            if (status == SynchAPI.WAIT_OBJECT_0()) {
                break; // unparked
            } else if (status != SynchAPI.WAIT_TIMEOUT()) {
                Log.log().newline().string("WindowsParkEvent.condTimedWait failed, status returned:  ").hex(status);
                Log.log().newline().string("GetLastError returned:  ").hex(WinBase.GetLastError()).newline();
                throw VMError.shouldNotReachHere("WaitForSingleObject failed");
            }
            delayMillis -= timeout;
        } while (delayMillis > 0);
    }

    @Override
    protected void unpark() {
        SynchAPI.SetEvent(eventHandle);
    }
}

@Platforms(Platform.WINDOWS.class)
class WindowsParkEventFactory implements ParkEventFactory {
    @Override
    public ParkEvent create() {
        return new WindowsParkEvent();
    }
}

@AutomaticFeature
@Platforms(Platform.WINDOWS.class)
class WindowsThreadsFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(JavaThreads.class, new WindowsJavaThreads());
        ImageSingletons.add(ParkEventFactory.class, new WindowsParkEventFactory());
    }
}
