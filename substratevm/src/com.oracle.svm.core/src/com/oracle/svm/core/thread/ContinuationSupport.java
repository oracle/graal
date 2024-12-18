/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.nodes.WriteStackPointerNode;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationAccess;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.snippets.KnownIntrinsics;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;

public class ContinuationSupport {
    static final class Options {
        @Option(type = OptionType.Expert, help = "Support for continuations which are used by virtual threads. " +
                        "If disabled, virtual threads can be started but each of them is backed by a platform thread.") //
        public static final HostedOptionKey<Boolean> VMContinuations = new HostedOptionKey<>(true);
    }

    @Fold
    public static boolean isSupported() {
        return ContinuationsFeature.isSupported();
    }

    /* See JDK native enum {@code freeze_result}. */
    public static final int FREEZE_OK = 0;
    public static final int FREEZE_PINNED_CS = 2; // critical section
    public static final int FREEZE_PINNED_NATIVE = 3;
    public static final int FREEZE_YIELDING = -2;

    private long ipOffset;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected ContinuationSupport() {
    }

    @Fold
    public static ContinuationSupport singleton() {
        return ImageSingletons.lookup(ContinuationSupport.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setIPOffset(long value) {
        assert ipOffset == 0;
        ipOffset = value;
    }

    public long getIPOffset() {
        assert ipOffset != 0;
        return ipOffset;
    }

    public Object prepareCopy(@SuppressWarnings("unused") StoredContinuation storedCont) {
        return null;
    }

    /**
     * This method reserves the extra stack space for the continuation. Be careful when modifying
     * the code or the arguments of this method because we need the guarantee the following
     * invariants:
     * <ul>
     * <li>The method must not contain any stack accesses as they would be relative to the
     * manipulated (and therefore incorrect) stack pointer.</li>
     * <li>The method must never return because the stack pointer would be incorrect
     * afterwards.</li>
     * <li>Only uninterruptible code may be executed once this method is called.</li>
     * </ul>
     */
    @NeverInline("Modifies the stack pointer manually, which breaks stack accesses.")
    @Uninterruptible(reason = "Manipulates the stack pointer.")
    public static void enter(StoredContinuation storedCont, Pointer topSP, Object preparedData) {
        WriteStackPointerNode.write(topSP);
        enter0(storedCont, topSP, preparedData);
    }

    @NeverInline("The caller modified the stack pointer manually, so we need a new stack frame.")
    @Uninterruptible(reason = "Copies stack frames containing references.")
    private static void enter0(StoredContinuation storedCont, Pointer topSP, Object preparedData) {
        // copyFrames() may do something interruptible before uninterruptibly copying frames.
        // Code must not rely on remaining uninterruptible until after frames were copied.
        CodePointer enterIP = singleton().copyFrames(storedCont, topSP, preparedData);
        KnownIntrinsics.farReturn(FREEZE_OK, topSP, enterIP, false);
    }

    @Uninterruptible(reason = "Copies stack frames containing references.")
    protected CodePointer copyFrames(StoredContinuation storedCont, Pointer topSP, @SuppressWarnings("unused") Object preparedData) {
        int totalSize = StoredContinuationAccess.getFramesSizeInBytes(storedCont);
        assert totalSize % ConfigurationValues.getTarget().wordSize == 0;

        Pointer frameData = StoredContinuationAccess.getFramesStart(storedCont);
        UnmanagedMemoryUtil.copyWordsForward(frameData, topSP, Word.unsigned(totalSize));
        return StoredContinuationAccess.getIP(storedCont);
    }

    @Uninterruptible(reason = "Copies stack frames containing references.")
    public CodePointer copyFrames(StoredContinuation fromCont, StoredContinuation toCont, Object preparedData) {
        return copyFrames(fromCont, StoredContinuationAccess.getFramesStart(toCont), preparedData);
    }
}
