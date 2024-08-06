/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.ParameterizedStackFrameVisitor;
import com.oracle.svm.core.util.VMError;

class SamplingStackVisitor extends ParameterizedStackFrameVisitor {

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate within the safepoint sampler.")
    protected boolean visitRegularFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, Object data) {
        SamplingStackVisitor.StackTrace stackTrace = (SamplingStackVisitor.StackTrace) data;
        if (stackTrace.num < stackTrace.buffer.length) {
            stackTrace.buffer[stackTrace.num++] = ip.rawValue();
            return true;
        } else {
            stackTrace.overflow = true;
            return false;
        }
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate within the safepoint sampler.")
    protected boolean visitDeoptimizedFrame(Pointer originalSP, CodePointer deoptStubIP, DeoptimizedFrame deoptimizedFrame, Object data) {
        throw VMError.shouldNotReachHere("Sampling is not supported if JIT compilation is enabled.");
    }

    @Override
    protected boolean unknownFrame(Pointer sp, CodePointer ip, Object data) {
        throw JavaStackWalker.fatalErrorUnknownFrameEncountered(sp, ip);
    }

    static class StackTrace {
        final long[] buffer;
        int num;
        boolean overflow;

        StackTrace(long stackSizeInBytes) {
            this.buffer = new long[((int) stackSizeInBytes) / 4];
            this.num = 0;
            this.overflow = false;
        }

        public void reset() {
            this.num = 0;
            this.overflow = false;
        }

        @Override
        public String toString() {
            return "StackTrace<buffer length = " + buffer.length + ", num = " + num + ">";
        }
    }
}
