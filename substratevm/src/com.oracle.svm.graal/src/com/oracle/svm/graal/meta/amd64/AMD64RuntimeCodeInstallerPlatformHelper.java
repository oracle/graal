/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.meta.amd64;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.Map.Entry;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.graal.meta.RuntimeCodeInstaller.RuntimeCodeInstallerPlatformHelper;

@AutomaticFeature
@Platforms(Platform.AMD64.class)
class AMD64RuntimeCodeInstallerPlatformHelperFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(RuntimeCodeInstallerPlatformHelper.class, new AMD64RuntimeCodeInstallerPlatformHelper());
    }
}

public class AMD64RuntimeCodeInstallerPlatformHelper implements RuntimeCodeInstallerPlatformHelper {

    /**
     * The size for trampoline jumps: jmp [rip+offset]
     * <p>
     * Trampoline jumps are added immediately after the method code, where each jump needs 6 bytes.
     * The jump instructions reference the 8-byte destination addresses, which are allocated after
     * the jumps.
     */
    @Override
    public int getTrampolineCallSize() {
        return 6;
    }

    /**
     * Checking if the pc displacement is within a signed 32 bit range.
     */
    @Override
    public boolean targetWithinPCDisplacement(long pcDisplacement) {
        return pcDisplacement == (int) pcDisplacement;
    }

    /*
     * These trampolines could be improved by using "call [rip+offset]" instructions. But it's not
     * trivial because these instructions need one byte more than the original PC relative calls.
     */
    @Override
    public int insertTrampolineCalls(byte[] compiledBytes, int initialPos, Map<Long, Integer> directTargets) {
        /*
         * Insert trampoline jumps. Note that this is only a fail-safe, because usually the code
         * should be within a 32-bit address range.
         */
        int currentPos = NumUtil.roundUp(initialPos, 8);
        ByteOrder byteOrder = ConfigurationValues.getTarget().arch.getByteOrder();
        assert byteOrder == ByteOrder.LITTLE_ENDIAN : "Code below assumes little-endian byte order";
        ByteBuffer codeBuffer = ByteBuffer.wrap(compiledBytes).order(byteOrder);
        for (Entry<Long, Integer> entry : directTargets.entrySet()) {
            long targetAddress = entry.getKey();
            int trampolineOffset = entry.getValue();
            // Write the "jmp [rip+offset]" instruction
            codeBuffer.put(trampolineOffset + 0, (byte) 0xff);
            codeBuffer.put(trampolineOffset + 1, (byte) 0x25);
            codeBuffer.putInt(trampolineOffset + 2, currentPos - (trampolineOffset + getTrampolineCallSize()));
            // Write the target address
            codeBuffer.putLong(currentPos, targetAddress);
            currentPos += 8;
        }
        return currentPos;
    }

    @Override
    public void performCodeSynchronization(CodeInfo codeInfo) {

    }
}
