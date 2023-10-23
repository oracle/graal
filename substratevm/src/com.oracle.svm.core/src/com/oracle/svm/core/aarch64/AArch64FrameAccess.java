/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.aarch64;

import jdk.graal.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateControlFlowIntegrity;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.graal.nodes.aarch64.AArch64XPACNode;

@AutomaticallyRegisteredImageSingleton(FrameAccess.class)
@Platforms(Platform.AARCH64.class)
public class AArch64FrameAccess extends FrameAccess {
    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public CodePointer readReturnAddress(Pointer sourceSp) {
        /* Read the return address, which is stored immediately below the stack pointer. */
        CodePointer returnAddress = sourceSp.readWord(-returnAddressSize());
        /* Remove the pointer authentication code (PAC), if present. */
        if (SubstrateControlFlowIntegrity.enabled()) {
            return AArch64XPACNode.stripAddress(returnAddress);
        }
        return returnAddress;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void writeReturnAddress(Pointer sourceSp, CodePointer newReturnAddress) {
        sourceSp.writeWord(-returnAddressSize(), newReturnAddress);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public Pointer getReturnAddressLocation(Pointer sourceSp) {
        return sourceSp.subtract(returnAddressSize());
    }

    @Fold
    @Override
    public int savedBasePointerSize() {
        // The base pointer is always saved with stp instruction on method entry
        return wordSize();
    }

    @Override
    @Fold
    public int stackPointerAdjustmentOnCall() {
        // A call on AArch64 does not touch the SP.
        return 0;
    }
}
