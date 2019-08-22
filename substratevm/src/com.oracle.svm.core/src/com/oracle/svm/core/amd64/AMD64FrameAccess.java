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
package com.oracle.svm.core.amd64;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform.AMD64;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;

@AutomaticFeature
@Platforms(AMD64.class)
class AMD64FrameAccessFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(FrameAccess.class, new AMD64FrameAccess());
    }
}

@Platforms(AMD64.class)
public final class AMD64FrameAccess extends FrameAccess {

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public CodePointer readReturnAddress(Pointer sourceSp) {
        /* Read the return address, which is stored just below the stack pointer. */
        return (CodePointer) sourceSp.readWord(-returnAddressSize());
    }

    @Override
    public void writeReturnAddress(Pointer sourceSp, CodePointer newReturnAddress) {
        sourceSp.writeWord(-returnAddressSize(), newReturnAddress);
    }

    @Fold
    @Override
    public int savedBasePointerSize() {
        if (SubstrateOptions.PreserveFramePointer.getValue()) {
            return wordSize();
        } else {
            return 0;
        }
    }

    @Override
    @Fold
    public int stackPointerAdjustmentOnCall() {
        // A call on AMD64 pushes %rip onto the stack and increments %rsp by wordSize().
        return wordSize();
    }
}
