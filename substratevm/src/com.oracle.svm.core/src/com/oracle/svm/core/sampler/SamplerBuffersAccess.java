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

import static com.oracle.svm.core.jfr.JfrStackTraceRepository.JfrStackTraceTableEntryStatus.FAILED;
import static com.oracle.svm.core.jfr.JfrStackTraceRepository.JfrStackTraceTableEntryStatus.SERIALIZED;
import static com.oracle.svm.core.jfr.JfrStackTraceRepository.JfrStackTraceTableEntryStatus.SHOULD_SERIALIZE;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.JfrStackTraceRepository;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.events.ExecutionSampleEvent;
import com.oracle.svm.core.util.VMError;

/**
 * Used to access the pool of {@link SamplerBuffer}s.
 */
public final class SamplerBuffersAccess {
    private static final CodeInfoQueryResult CODE_INFO_QUERY_RESULT = new CodeInfoQueryResult();

    private SamplerBuffersAccess() {
    }

    @Uninterruptible(reason = "All code that accesses a sampler buffer must be uninterruptible.")
    public static void processSamplerBuffers() {
        if (!HasJfrSupport.get()) {
            /*
             * This method will become reachable on WINDOWS during the building of JFR tests via
             * com.oracle.svm.core.jfr.JfrChunkWriter.closeFile, and it will fail during
             * InvocationPlugin call if we do not have this check.
             * 
             * Note that although we are building the JFR tests for Windows as well, they will not
             * be executed because of guard in com.oracle.svm.test.jfr.JfrTest.checkForJFR.
             *
             * Once we have support for Windows, this check will become obsolete.
             */
            return;
        }

        SubstrateSigprofHandler.singleton().setSignalHandlerGloballyDisabled(true);
        while (true) {
            /* Pop top buffer from stack of full buffers. */
            SamplerBuffer buffer = SubstrateSigprofHandler.singleton().fullBuffers().popBuffer();
            if (buffer.isNull()) {
                /* No buffers to process. */
                SubstrateSigprofHandler.singleton().setSignalHandlerGloballyDisabled(false);
                return;
            }

            /* Process the buffer. */
            processSamplerBuffer(buffer);
            if (buffer.getFreeable()) {
                SamplerBufferAccess.free(buffer);
            } else {
                SubstrateSigprofHandler.singleton().availableBuffers().pushBuffer(buffer);
            }
        }
    }

    @Uninterruptible(reason = "All code that accesses a sampler buffer must be uninterruptible.")
    public static void processSamplerBuffer(SamplerBuffer buffer) {
        Pointer end = buffer.getPos();
        Pointer current = SamplerBufferAccess.getDataStart(buffer);
        while (current.belowThan(end)) {
            /* Sample hash. */
            int sampleHash = current.readInt(0);
            current = current.add(Integer.BYTES);

            /* Is truncated. */
            boolean isTruncated = current.readInt(0) == 1;
            current = current.add(Integer.BYTES);

            /* Sample size. */
            int sampleSize = current.readInt(0);
            current = current.add(Integer.BYTES);

            /* Tick. */
            long sampleTick = current.readLong(0);
            current = current.add(Long.BYTES);

            /* Thread state. */
            long threadState = current.readLong(0);
            current = current.add(Long.BYTES);

            CIntPointer status = StackValue.get(CIntPointer.class);
            JfrStackTraceRepository stackTraceRepo = SubstrateJVM.getStackTraceRepo();
            stackTraceRepo.acquireLock();
            try {
                long stackTraceId = stackTraceRepo.getStackTraceId(current, current.add(sampleSize), sampleHash, status, true);
                boolean serialized = JfrStackTraceRepository.JfrStackTraceTableEntryStatus.get(status, SERIALIZED);
                boolean failed = JfrStackTraceRepository.JfrStackTraceTableEntryStatus.get(status, FAILED);
                if (serialized || failed) {
                    /*
                     * Sample/Stack is already there or there is not enough memory to operate, skip
                     * the rest of the data.
                     */
                    current = current.add(sampleSize);
                    long endMarker = current.readLong(0);
                    if (endMarker == SamplerSampleWriter.SAMPLE_EVENT_DATA_END && serialized) {
                        ExecutionSampleEvent.writeExecutionSample(sampleTick, buffer.getOwner(), stackTraceId, threadState);
                    }
                    current = current.add(SamplerSampleWriter.END_MARKER_SIZE);
                } else {
                    assert JfrStackTraceRepository.JfrStackTraceTableEntryStatus.get(status, SHOULD_SERIALIZE);
                    /* Sample is not there. Start walking a stacktrace. */
                    stackTraceRepo.serializeStackTraceHeader(stackTraceId, isTruncated, sampleSize / SamplerSampleWriter.IP_SIZE);
                    while (current.belowThan(end)) {
                        long ip = current.readLong(0);
                        /* Check if we hit any of the end markers. */
                        if (ip == SamplerSampleWriter.JFR_STACK_TRACE_END || ip == SamplerSampleWriter.SAMPLE_EVENT_DATA_END) {
                            if (ip == SamplerSampleWriter.SAMPLE_EVENT_DATA_END) {
                                ExecutionSampleEvent.writeExecutionSample(sampleTick, buffer.getOwner(), stackTraceId, threadState);
                            }
                            current = current.add(SamplerSampleWriter.END_MARKER_SIZE);
                            break;
                        } else {
                            visitFrame(ip);
                            current = current.add(SamplerSampleWriter.IP_SIZE);
                        }
                    }
                }
            } finally {
                stackTraceRepo.releaseLock();
            }
        }
        SamplerBufferAccess.reinitialize(buffer);
    }

    @Uninterruptible(reason = "The handle should only be accessed from uninterruptible code to prevent that the GC frees the CodeInfo.", callerMustBe = true)
    private static void visitFrame(long address) {
        CodePointer ip = WordFactory.pointer(address);
        UntetheredCodeInfo untetheredInfo = CodeInfoTable.lookupCodeInfo(ip);
        if (untetheredInfo.isNull()) {
            /* Unknown frame. May happen for various reasons. */
            VMError.shouldNotReachHere("Stack walk must walk only frames of known code.");
        }

        Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
        CodeInfo tetheredCodeInfo = CodeInfoAccess.convert(untetheredInfo, tether);
        try {
            /* Now the value of code info can be passed to interruptible code safely. */
            visitFrame0(tetheredCodeInfo, ip);
        } finally {
            CodeInfoAccess.releaseTether(untetheredInfo, tether);
        }
    }

    @Uninterruptible(reason = "The handle should only be accessed from uninterruptible code to prevent that the GC frees the CodeInfo.", callerMustBe = true)
    private static void visitFrame0(CodeInfo codeInfo, CodePointer ip) {
        CodeInfoQueryResult queryResult = CodeInfoTable.lookupCodeInfoQueryResultUninterruptible(codeInfo, ip, CODE_INFO_QUERY_RESULT);
        VMError.guarantee(queryResult != null);
        FrameInfoQueryResult frameInfoQueryResult = queryResult.getFrameInfo();
        if (frameInfoQueryResult != null) {
            SubstrateJVM.getStackTraceRepo().serializeStackTraceElement(frameInfoQueryResult);
        } else {
            /* We don't have information about native code. */
            SubstrateJVM.getStackTraceRepo().serializeUnknownStackTraceElement();
        }
    }
}
