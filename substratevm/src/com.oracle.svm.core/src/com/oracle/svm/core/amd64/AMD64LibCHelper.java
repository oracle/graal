/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.struct.AllowNarrowingCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.util.BasedOnJDKFile;

/*
 * To be kept in sync with:
 *  - substratevm/src/com.oracle.svm.native.libchelper/include/amd64hotspotcpuinfo.h
 *  - substratevm/src/com.oracle.svm.native.libchelper/src/cpuid.c
 */
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-23+14/src/hotspot/cpu/x86/vm_version_x86.hpp#L40-L313")
@CLibrary(value = "libchelper", requireStatic = true)
public class AMD64LibCHelper {
    @Platforms(Platform.AMD64.class)
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native void determineCPUFeatures(CPUFeatures features);

    @Platforms(Platform.AMD64.class)
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int checkCPUFeatures(CCharPointer buildtimeCPUFeatureMask);

    @Platforms(Platform.AMD64.class)
    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int checkCPUFeaturesOrExit(CCharPointer buildtimeCPUFeatureMask, CCharPointer errorMessage);

    // Checkstyle: stop
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
        boolean fAMD_3DNOW_PREFETCH();

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
        boolean fSSE4_1();

        @AllowNarrowingCast
        @CField
        boolean fSSE4_2();

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
        boolean fTSCINV_BIT();

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
        boolean fAVX512VL();

        @AllowNarrowingCast
        @CField
        boolean fSHA();

        @AllowNarrowingCast
        @CField
        boolean fFMA();

        @AllowNarrowingCast
        @CField
        boolean fVZEROUPPER();

        @AllowNarrowingCast
        @CField
        boolean fAVX512_VPOPCNTDQ();

        @AllowNarrowingCast
        @CField
        boolean fAVX512_VPCLMULQDQ();

        @AllowNarrowingCast
        @CField
        boolean fAVX512_VAES();

        @AllowNarrowingCast
        @CField
        boolean fAVX512_VNNI();

        @AllowNarrowingCast
        @CField
        boolean fFLUSH();

        @AllowNarrowingCast
        @CField
        boolean fFLUSHOPT();

        @AllowNarrowingCast
        @CField
        boolean fCLWB();

        @AllowNarrowingCast
        @CField
        boolean fAVX512_VBMI2();

        @AllowNarrowingCast
        @CField
        boolean fAVX512_VBMI();

        @AllowNarrowingCast
        @CField
        boolean fHV();

        @AllowNarrowingCast
        @CField
        boolean fSERIALIZE();

        @AllowNarrowingCast
        @CField
        boolean fRDTSCP();

        @AllowNarrowingCast
        @CField
        boolean fRDPID();

        @AllowNarrowingCast
        @CField
        boolean fFSRM();

        @AllowNarrowingCast
        @CField
        boolean fGFNI();

        @AllowNarrowingCast
        @CField
        boolean fAVX512_BITALG();

        @AllowNarrowingCast
        @CField
        boolean fPKU();

        @AllowNarrowingCast
        @CField
        boolean fOSPKE();

        @AllowNarrowingCast
        @CField
        boolean fCET_IBT();

        @AllowNarrowingCast
        @CField
        boolean fCET_SS();

        @AllowNarrowingCast
        @CField
        boolean fF16C();

        @AllowNarrowingCast
        @CField
        boolean fAVX512_IFMA();

        @AllowNarrowingCast
        @CField
        boolean fAVX_IFMA();
    }
    // Checkstyle: resume
}
