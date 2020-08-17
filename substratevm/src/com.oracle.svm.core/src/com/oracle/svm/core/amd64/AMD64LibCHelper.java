/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.amd64;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.struct.AllowNarrowingCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;

@CLibrary(value = "libchelper", requireStatic = true)
public class AMD64LibCHelper {
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native void determineCPUFeatures(CPUFeatures features);

    @CStruct
    @CContext(AMD64LibCHelperDirectives.class)
    public interface CPUFeatures extends PointerBase {

        @AllowNarrowingCast
        @CField
        boolean fCX8();

        @AllowNarrowingCast
        @CField
        boolean fCMOV();

        @AllowNarrowingCast
        @CField
        boolean fFXSR();

        @AllowNarrowingCast
        @CField
        boolean fHT();

        @AllowNarrowingCast
        @CField
        boolean fMMX();

        @AllowNarrowingCast
        @CField
        boolean fAMD3DNOWPREFETCH();

        @AllowNarrowingCast
        @CField
        boolean fSSE();

        @AllowNarrowingCast
        @CField
        boolean fSSE2();

        @AllowNarrowingCast
        @CField
        boolean fSSE3();

        @AllowNarrowingCast
        @CField
        boolean fSSSE3();

        @AllowNarrowingCast
        @CField
        boolean fSSE4A();

        @AllowNarrowingCast
        @CField()
        boolean fSSE41();

        @AllowNarrowingCast
        @CField
        boolean fSSE42();

        @AllowNarrowingCast
        @CField
        boolean fPOPCNT();

        @AllowNarrowingCast
        @CField
        boolean fLZCNT();

        @AllowNarrowingCast
        @CField
        boolean fTSC();

        @AllowNarrowingCast
        @CField
        boolean fTSCINV();

        @AllowNarrowingCast
        @CField
        boolean fAVX();

        @AllowNarrowingCast
        @CField
        boolean fAVX2();

        @AllowNarrowingCast
        @CField
        boolean fAES();

        @AllowNarrowingCast
        @CField
        boolean fERMS();

        @AllowNarrowingCast
        @CField
        boolean fCLMUL();

        @AllowNarrowingCast
        @CField
        boolean fBMI1();

        @AllowNarrowingCast
        @CField
        boolean fBMI2();

        @AllowNarrowingCast
        @CField
        boolean fRTM();

        @AllowNarrowingCast
        @CField
        boolean fADX();

        @AllowNarrowingCast
        @CField
        boolean fAVX512F();

        @AllowNarrowingCast
        @CField
        boolean fAVX512DQ();

        @AllowNarrowingCast
        @CField
        boolean fAVX512PF();

        @AllowNarrowingCast
        @CField
        boolean fAVX512ER();

        @AllowNarrowingCast
        @CField
        boolean fAVX512CD();

        @AllowNarrowingCast
        @CField
        boolean fAVX512BW();

        @AllowNarrowingCast
        @CField
        boolean fSHA();

        @AllowNarrowingCast
        @CField
        boolean fFMA();
    }

}
