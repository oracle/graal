/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;

public abstract class FrameAccess {

    @Fold
    public static FrameAccess singleton() {
        return ImageSingletons.lookup(FrameAccess.class);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract CodePointer readReturnAddress(Pointer sourceSp);

    public abstract void writeReturnAddress(Pointer sourceSp, CodePointer newReturnAddress);

    @Fold
    public static int returnAddressSize() {
        Architecture arch = ConfigurationValues.getTarget().arch;
        if (arch instanceof AArch64) {
            return 8;
        } else {
            return arch.getReturnAddressSize();
        }
    }

    /**
     * Gets the amount by which the stack pointer is adjusted by a call instruction.
     */
    @Fold
    public abstract int stackPointerAdjustmentOnCall();

    /**
     * Returns the size in bytes of the saved base pointer in the stack frame. The saved base
     * pointer must be located immediately after the return address (if this is not the case in a
     * new architecture, bigger modifications to code like the Deoptimizer is required).
     */
    public abstract int savedBasePointerSize();

    @Fold
    public static int wordSize() {
        return ConfigurationValues.getTarget().arch.getWordSize();
    }

    public static int uncompressedReferenceSize() {
        return wordSize();
    }

    public static JavaKind getWordKind() {
        return ConfigurationValues.getTarget().wordJavaKind;
    }

    public static Stamp getWordStamp() {
        return StampFactory.forKind(ConfigurationValues.getTarget().wordJavaKind);
    }
}
