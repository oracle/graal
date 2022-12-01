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

package com.oracle.svm.core.sampler;

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.stack.ParameterizedStackFrameVisitor;

final class SamplerStackWalkVisitor extends ParameterizedStackFrameVisitor<Object> {
    @Override
    @Uninterruptible(reason = "The method executes during signal handling.", callerMustBe = true)
    protected boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptimizedFrame, Object data) {
        SamplerSampleWriterData writerData = SamplerThreadLocal.getWriterData();
        boolean shouldSkipFrame = shouldSkipFrame(writerData);
        boolean shouldContinueWalk = shouldContinueWalk(writerData);
        if (!shouldSkipFrame && shouldContinueWalk) {
            writerData.setHashCode(computeHash(writerData.getHashCode(), ip.rawValue()));
            shouldContinueWalk = SamplerSampleWriter.putLong(writerData, ip.rawValue());
        }
        return shouldContinueWalk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean shouldContinueWalk(SamplerSampleWriterData data) {
        if (data.getNumFrames() >= data.getMaxDepth()) {
            /* The stack size exceeds given depth. Stop walk! */
            data.setTruncated(true);
            return false;
        } else {
            return true;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean shouldSkipFrame(SamplerSampleWriterData data) {
        data.setNumFrames(data.getNumFrames() + 1);
        return data.getNumFrames() <= data.getSkipCount();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int computeHash(int oldHash, long ip) {
        int hash = (int) (ip ^ (ip >>> 32));
        return 31 * oldHash + hash;
    }

    @Override
    @Uninterruptible(reason = "The method executes during signal handling.", callerMustBe = true)
    protected boolean unknownFrame(Pointer sp, CodePointer ip, DeoptimizedFrame deoptimizedFrame, Object data) {
        SamplerThreadLocal.increaseUnparseableStacks();
        return false;
    }
}
