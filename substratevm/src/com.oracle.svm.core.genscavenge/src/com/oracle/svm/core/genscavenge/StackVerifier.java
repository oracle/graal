/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.thread.VMThreads;

/** Walk the stack of threads, verifying the Objects pointed to from the frames. */
final class StackVerifier {
    private static final VerifyFrameReferencesVisitor verifyFrameReferencesVisitor = new VerifyFrameReferencesVisitor();

    private final StackFrameVerifierVisitor stackFrameVisitor = new StackFrameVerifierVisitor();

    StackVerifier() {
    }

    public boolean verifyInAllThreads(Pointer currentSp, String message) {
        Log trace = getTraceLog();
        trace.string("[StackVerifier.verifyInAllThreads:").string(message).newline();
        // Flush thread-local allocation data.
        ThreadLocalAllocation.disableAndFlushForAllThreads();
        trace.string("Current thread ").hex(CurrentIsolate.getCurrentThread()).string(": [").newline();
        if (!JavaStackWalker.walkCurrentThread(currentSp, stackFrameVisitor)) {
            return false;
        }
        trace.string("]").newline();
        if (SubstrateOptions.MultiThreaded.getValue()) {
            for (IsolateThread vmThread = VMThreads.firstThread(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
                if (vmThread == CurrentIsolate.getCurrentThread()) {
                    continue;
                }
                trace.string("Thread ").hex(vmThread).string(": [").newline();
                if (!JavaStackWalker.walkThread(vmThread, stackFrameVisitor)) {
                    return false;
                }
                trace.string("]").newline();
            }
        }
        trace.string("]").newline();
        return true;
    }

    private static boolean verifyFrame(Pointer frameSP, CodePointer frameIP, CodeInfo codeInfo, DeoptimizedFrame deoptimizedFrame) {
        Log trace = getTraceLog();
        trace.string("[StackVerifier.verifyFrame:");
        trace.string("  frameSP: ").hex(frameSP);
        trace.string("  frameIP: ").hex(frameIP);
        trace.string("  pc: ").hex(frameIP);
        trace.newline();

        if (!CodeInfoTable.visitObjectReferences(frameSP, frameIP, codeInfo, deoptimizedFrame, verifyFrameReferencesVisitor)) {
            return false;
        }

        trace.string("  returns true]").newline();
        return true;
    }

    private static class StackFrameVerifierVisitor extends StackFrameVisitor {
        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while verifying the stack.")
        public boolean visitFrame(Pointer currentSP, CodePointer currentIP, CodeInfo codeInfo, DeoptimizedFrame deoptimizedFrame) {
            Log trace = getTraceLog();
            long totalFrameSize = CodeInfoAccess.lookupTotalFrameSize(codeInfo, CodeInfoAccess.relativeIP(codeInfo, currentIP));
            trace.string("  currentIP: ").hex(currentIP);
            trace.string("  currentSP: ").hex(currentSP);
            trace.string("  frameSize: ").signed(totalFrameSize).newline();

            if (!verifyFrame(currentSP, currentIP, codeInfo, deoptimizedFrame)) {
                Log witness = Log.log();
                witness.string("  frame fails to verify");
                witness.string("  returns false]").newline();
                return false;
            }
            return true;
        }
    }

    private static class VerifyFrameReferencesVisitor implements ObjectReferenceVisitor {
        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed) {
            Pointer objAddr = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);

            Log trace = StackVerifier.getTraceLog();
            trace.string("  objAddr: ").hex(objAddr);
            trace.newline();
            if (!objAddr.isNull() && !HeapImpl.getHeapImpl().getHeapVerifier().verifyObjectAt(objAddr)) {
                Log witness = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog();
                witness.string("[StackVerifier.verifyFrame:");
                witness.string("  objAddr: ").hex(objAddr);
                witness.string("  fails to verify");
                witness.string("]").newline();
                return false;
            }
            return true;
        }
    }

    private static Log getTraceLog() {
        return (HeapOptions.TraceStackVerification.getValue() ? Log.log() : Log.noopLog());
    }
}
