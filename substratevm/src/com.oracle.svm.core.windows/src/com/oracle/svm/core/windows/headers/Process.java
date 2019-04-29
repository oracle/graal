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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;

//Checkstyle: stop

/**
 * Definitions for Windows process.h
 */
@CContext(WindowsDirectives.class)
@Platforms(Platform.WINDOWS.class)
public class Process {

    /** Execute path with arguments argv. */
    @CFunction
    public static native int _execv(CCharPointer path, CCharPointerPointer argv);

    /**
     * Thread Creation
     */
    @CFunction
    public static native WinBase.HANDLE _beginthreadex(PointerBase security, int stacksize, CFunctionPointer start_address,
                    PointerBase arglist, int initflag, CIntPointer thrdaddr);

    @CConstant
    public static native int CREATE_SUSPENDED();

    @CConstant
    public static native int STACK_SIZE_PARAM_IS_A_RESERVATION();

    @CFunction
    public static native int ResumeThread(WinBase.HANDLE osThreadHandle);

    @CFunction
    public static native int SwitchToThread();

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int GetCurrentThreadId();

    /**
     * Windows Thread local storage functions
     */

    /** Allocate a slot in the thread local storage area */
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native int TlsAlloc();

    /** Destroy tlsIndex */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int TlsFree(int tlsIndex);

    /** Return current value of the thread-specific data slot identified by tlsIndex. */
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native <T extends WordBase> T TlsGetValue(int tlsIndex);

    /**
     * Store POINTER in the thread-specific data slot identified by tlsIndex.
     */
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native int TlsSetValue(int tlsIndex, WordBase value);

    /**
     * Windows Critical Section (for supporting Mutexes) functions and structure declarations
     */

    @CStruct
    public interface PCRITICAL_SECTION extends PointerBase {
    }

    @CStruct
    public interface CRITICAL_SECTION extends PointerBase {
    }

    /** Initialize a Critical Section */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native void InitializeCriticalSection(PCRITICAL_SECTION mutex);

    /** Enter a Critical Section */
    @CFunction(transition = Transition.TO_NATIVE)
    public static native void EnterCriticalSection(PCRITICAL_SECTION mutex);

    @CFunction(value = "EnterCriticalSection", transition = Transition.NO_TRANSITION)
    public static native void EnterCriticalSectionNoTrans(PCRITICAL_SECTION mutex);

    /** Exit a Critical Section */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native void LeaveCriticalSection(PCRITICAL_SECTION mutex);

    @CFunction(value = "LeaveCriticalSection", transition = Transition.NO_TRANSITION)
    public static native void LeaveCriticalSectionNoTrans(PCRITICAL_SECTION mutex);

    /**
     * Windows Condition Variable functions and structure declarations
     */

    @CStruct
    public interface PCONDITION_VARIABLE extends PointerBase {
    }

    @CStruct
    public interface CONDITION_VARIABLE extends PointerBase {
    }

    /** Initialize a condition variable */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native void InitializeConditionVariable(PCONDITION_VARIABLE cond);

    /** Wait on condition variable */
    @CFunction
    public static native int SleepConditionVariableCS(PCONDITION_VARIABLE cond, PCRITICAL_SECTION mutex, int dwMilliseconds);

    @CFunction(value = "SleepConditionVariableCS", transition = Transition.NO_TRANSITION)
    public static native int SleepConditionVariableCSNoTrans(PCONDITION_VARIABLE cond, PCRITICAL_SECTION mutex, int dwMilliseconds);

    /** Wake a single thread waiting on the condition variable */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native void WakeConditionVariable(PCONDITION_VARIABLE cond);

    /** Wake all threads waiting on the condition variable */
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native void WakeAllConditionVariable(PCONDITION_VARIABLE cond);
}
