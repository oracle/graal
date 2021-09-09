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
import org.graalvm.word.PointerBase;

//Checkstyle: stop

/**
 * Definitions for Windows syncapi.h header file
 */
@CContext(WindowsDirectives.class)
@Platforms(Platform.WINDOWS.class)
public class SynchAPI {

    @CFunction
    public static native WinBase.HANDLE CreateEventA(PointerBase lpEventAttributes, int bManualReset, int bInitialState, PointerBase lpName);

    @CFunction
    public static native int ResetEvent(WinBase.HANDLE hEvent);

    @CFunction
    public static native int SetEvent(WinBase.HANDLE hEvent);

    @CFunction
    public static native int WaitForSingleObject(WinBase.HANDLE hEvent, int dwMilliseconds);

    /** Infinite timeout for WaitForSingleObject */
    @CConstant
    public static native int INFINITE();

    /*
     * Result codes for WaitForSingleObject
     */

    @CConstant
    public static native int WAIT_OBJECT_0();

    @CConstant
    public static native int WAIT_TIMEOUT();

    @CConstant
    public static native int WAIT_ABANDONED();

    @CConstant
    public static native int WAIT_FAILED();

    public static class NoTransitions {
        @CFunction(transition = Transition.NO_TRANSITION)
        public static native void Sleep(int dwMilliseconds);

        @CFunction(transition = Transition.NO_TRANSITION)
        public static native int WaitForSingleObject(WinBase.HANDLE hEvent, int dwMilliseconds);
    }
}
