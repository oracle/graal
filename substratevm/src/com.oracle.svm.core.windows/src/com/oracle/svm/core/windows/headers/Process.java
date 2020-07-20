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
package com.oracle.svm.core.windows.headers;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.windows.headers.WinBase.HANDLE;
import com.oracle.svm.core.windows.headers.WinBase.LPHANDLE;

//Checkstyle: stop

/**
 * Definitions for Windows process.h
 */
@CContext(WindowsDirectives.class)
public class Process {

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native HANDLE GetCurrentProcess();

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native HANDLE OpenProcess(int dwDesiredAccess, int bInheritHandle, int dwProcessId);

    @CConstant
    public static native int PROCESS_TERMINATE();

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int TerminateProcess(HANDLE hProcess, int uExitCode);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int GetCurrentProcessId();

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int GetProcessId(HANDLE hProcess);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int OpenProcessToken(HANDLE processHandle, int desiredAccess, LPHANDLE tokenHandle);

    @CConstant
    public static native int TOKEN_QUERY();

    @CFunction
    public static native HANDLE _beginthreadex(PointerBase security, int stacksize, CFunctionPointer start_address,
                    PointerBase arglist, int initflag, CIntPointer thrdaddr);

    @CConstant
    public static native int CREATE_SUSPENDED();

    @CConstant
    public static native int STACK_SIZE_PARAM_IS_A_RESERVATION();

    @CFunction
    public static native int ResumeThread(HANDLE hThread);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int GetExitCodeThread(HANDLE hThread, CIntPointer lpExitCode);

    @CFunction
    public static native int SwitchToThread();

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int GetCurrentThreadId();

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native HANDLE GetCurrentThread();

    @CConstant
    public static native int SYNCHRONIZE();

    @CStruct
    public interface PCRITICAL_SECTION extends PointerBase {
    }

    @CStruct
    public interface CRITICAL_SECTION extends PointerBase {
    }

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native void InitializeCriticalSection(PCRITICAL_SECTION mutex);

    @CFunction(transition = Transition.TO_NATIVE)
    public static native void EnterCriticalSection(PCRITICAL_SECTION mutex);

    @CFunction(value = "EnterCriticalSection", transition = Transition.NO_TRANSITION)
    public static native void EnterCriticalSectionNoTrans(PCRITICAL_SECTION mutex);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native void LeaveCriticalSection(PCRITICAL_SECTION mutex);

    @CFunction(value = "LeaveCriticalSection", transition = Transition.NO_TRANSITION)
    public static native void LeaveCriticalSectionNoTrans(PCRITICAL_SECTION mutex);

    @CStruct
    public interface PCONDITION_VARIABLE extends PointerBase {
    }

    @CStruct
    public interface CONDITION_VARIABLE extends PointerBase {
    }

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native void InitializeConditionVariable(PCONDITION_VARIABLE cond);

    @CFunction
    public static native int SleepConditionVariableCS(PCONDITION_VARIABLE cond, PCRITICAL_SECTION mutex, int dwMilliseconds);

    @CFunction(value = "SleepConditionVariableCS", transition = Transition.NO_TRANSITION)
    public static native int SleepConditionVariableCSNoTrans(PCONDITION_VARIABLE cond, PCRITICAL_SECTION mutex, int dwMilliseconds);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native void WakeConditionVariable(PCONDITION_VARIABLE cond);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native void WakeAllConditionVariable(PCONDITION_VARIABLE cond);
}
