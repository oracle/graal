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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointContext;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.core.c.function.CEntryPointSetup.LeaveDetachThreadEpilogue;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.ParkEvent;
import com.oracle.svm.core.thread.ParkEvent.ParkEventFactory;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.windows.headers.Process;
import com.oracle.svm.core.windows.headers.WinBase;
import com.oracle.svm.core.windows.headers.SynchAPI;

@Platforms(Platform.WINDOWS.class)
public final class WindowsJavaThreads extends JavaThreads {

    @Fold
    public static WindowsJavaThreads singleton() {
        return (WindowsJavaThreads) JavaThreads.singleton();
    }

    @Platforms(HOSTED_ONLY.class)
    WindowsJavaThreads() {
    }

    @Override
    protected void start0(Thread thread, long stackSize) {
        int threadStackSize = (int) stackSize;
        int initFlag = Process.CREATE_SUSPENDED();

        ThreadStartData startData = UnmanagedMemory.malloc(SizeOf.get(ThreadStartData.class));
        startData.setIsolate(CEntryPointContext.getCurrentIsolate());
        startData.setThreadHandle(ObjectHandles.getGlobal().create(thread));

        if (!thread.isDaemon()) {
            JavaThreads.singleton().signalNonDaemonThreadStart();
        }

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
    protected void setNativeName(String name) {
    }

    @Override
    protected void yield() {
        Process.SwitchToThread();
    }

    @RawStructure
    interface ThreadStartData extends PointerBase {

        @RawField
        ObjectHandle getThreadHandle();

        @RawField
        void setThreadHandle(ObjectHandle handle);

        @RawField
        WinBase.HANDLE getOSThreadHandle();

        @RawField
        void setOSThreadHandle(WinBase.HANDLE osHandle);

        @RawField
        Isolate getIsolate();

        @RawField
        void setIsolate(Isolate vm);
    }

    private static final CEntryPointLiteral<CFunctionPointer> osThreadStartRoutine = CEntryPointLiteral.create(WindowsJavaThreads.class, "osThreadStartRoutine", ThreadStartData.class);

    private static class OSThreadStartRoutinePrologue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString("Failed to attach a newly launched thread.");

        @SuppressWarnings("unused")
        static void enter(ThreadStartData data) {
            int code = CEntryPointActions.enterAttachThread(data.getIsolate());
            if (code != 0) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }

    @CEntryPoint
    @CEntryPointOptions(prologue = OSThreadStartRoutinePrologue.class, epilogue = LeaveDetachThreadEpilogue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static WordBase osThreadStartRoutine(ThreadStartData data) {
        ObjectHandle threadHandle = data.getThreadHandle();
        WinBase.HANDLE osThreadHandle = data.getOSThreadHandle();
        UnmanagedMemory.free(data);

        Thread thread = ObjectHandles.getGlobal().get(threadHandle);

        boolean status = singleton().assignJavaThread(thread, false);
        VMError.guarantee(status, "currentThread already initialized");

        /*
         * Destroy the handle only after setting currentThread, since the lock used by destroy
         * requires the current thread.
         */
        ObjectHandles.getGlobal().destroy(threadHandle);

        singleton().noteThreadStart(thread);

        try {
            thread.run();
        } catch (Throwable ex) {
            dispatchUncaughtException(thread, ex);
        } finally {
            exit(thread);
            singleton().noteThreadFinish(thread);
            WinBase.CloseHandle(osThreadHandle);
        }

        return WordFactory.nullPointer();
    }

    private void noteThreadStart(Thread thread) {
        totalThreads.incrementAndGet();
        int lThreads = liveThreads.incrementAndGet();
        peakThreads.set(Integer.max(peakThreads.get(), lThreads));
        if (thread.isDaemon()) {
            daemonThreads.incrementAndGet();
        } else {
            nonDaemonThreads.incrementAndGet();
        }
    }

    private void noteThreadFinish(Thread thread) {
        liveThreads.decrementAndGet();
        if (thread.isDaemon()) {
            daemonThreads.decrementAndGet();
        } else {
            nonDaemonThreads.decrementAndGet();
        }
    }
}

@Platforms(Platform.WINDOWS.class)
class WindowsParkEvent extends ParkEvent {

    /** opaque Event Object Handle from the operating system. */
    private final WinBase.HANDLE eventHandle;

    WindowsParkEvent() {
        /* Create an Event */
        eventHandle = SynchAPI.CreateEventA(WordFactory.nullPointer(), 0, 0, WordFactory.nullPointer());
        VMError.guarantee(eventHandle.rawValue() != 0, "CreateEventA failed");
    }

    @Override
    protected WaitResult condWait() {
        WaitResult result = WaitResult.UNPARKED;
        try {
            if (resetEventBeforeWait) {
                event = false;
            }
            /*
             * Wait while the ticket is not available. Note that the ticket might already be
             * available before we enter the loop the first time, in which case we do not want to
             * wait at all.
             */
            while (!event) {
                /* Before blocking, check if this thread has been interrupted. */
                if (Thread.interrupted()) {
                    result = WaitResult.INTERRUPTED;
                    SynchAPI.ResetEvent(eventHandle);
                    return result;
                }

                int status = SynchAPI.WaitForSingleObject(eventHandle, SynchAPI.INFINITE());

                /*
                 * If the status isn't WAIT_OBJECT_0, then something went wrong.
                 */
                if (status != SynchAPI.WAIT_OBJECT_0()) {
                    Log.log().newline().string("WindowsParkEvent.condWait failed, status returned:  ").hex(status);
                    Log.log().newline().string("GetLastError returned:  ").hex(WinBase.GetLastError()).newline();
                    result = WaitResult.INTERRUPTED;
                    break;
                }
            }

            if (event) {
                /* If the ticket is available, then someone unparked me. */
                event = false;
                result = WaitResult.UNPARKED;
            }
        } finally {
            SynchAPI.ResetEvent(eventHandle);
        }
        return result;
    }

    @Override
    protected WaitResult condTimedWait(long delayNanos) {
        int dwMilliseconds = (int) (delayNanos / 1000000);
        WaitResult result = WaitResult.UNPARKED;
        try {
            if (resetEventBeforeWait) {
                event = false;
            }
            /*
             * Wait while the ticket is not available. Note that the ticket might already be
             * available before we enter the loop the first time, in which case we do not want to
             * wait at all.
             */
            while (!event) {
                /* Before blocking, check if this thread has been interrupted. */
                if (Thread.interrupted()) {
                    result = WaitResult.INTERRUPTED;
                    SynchAPI.ResetEvent(eventHandle);
                    return result;
                }

                int status = SynchAPI.WaitForSingleObject(eventHandle, dwMilliseconds);

                /*
                 * If the status is WAIT_OBJECT_0, then we're done.
                 */
                if (status == SynchAPI.WAIT_OBJECT_0()) {
                    break;
                }

                if (status == SynchAPI.WAIT_TIMEOUT()) {
                    /* If I was awakened because I ran out of time, do not wait for the ticket. */
                    result = WaitResult.TIMED_OUT;
                    break;
                }

                /* If we got WAIT_ABANDONED or WAIT_FAILED, log it and say we were interrupted */
                Log.log().newline().string("WindowsParkEvent.condTimedWait failed, status returned:  ").hex(status);
                Log.log().newline().string("GetLastError returned:  ").hex(WinBase.GetLastError()).newline();
                result = WaitResult.INTERRUPTED;
                break;

            }

            if (event) {
                /* If the ticket is available, then someone unparked me. */
                event = false;
                result = WaitResult.UNPARKED;
            }
        } finally {
            SynchAPI.ResetEvent(eventHandle);
        }

        return result;
    }

    @Override
    protected void unpark() {
        /* Re-establish the ticket. */
        event = true;
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
