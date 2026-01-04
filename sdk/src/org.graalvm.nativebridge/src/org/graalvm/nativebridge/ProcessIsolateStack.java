/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.nativebridge;

/**
 * Provides utility methods for merging stack traces for process isolate.
 */
final class ProcessIsolateStack {

    private static final String THREAD_CHANNEL_CLASS = ProcessIsolateThreadSupport.ThreadChannel.class.getName();
    private static final String DISPATCH_RUNNABLE = ProcessIsolateThreadSupport.DispatchRunnable.class.getName();
    private static final String FOREIGN_EXCEPTION_CLASS = ForeignException.class.getName();
    private static final String SEND_RECEIVE_METHOD = "sendAndReceive";
    private static final String CREATE_METHOD = "create";
    private static final String DISPATCH_METHOD = "dispatch";
    private static final String RUN_METHOD = "run";

    private ProcessIsolateStack() {
    }

    /**
     * Merges {@code host} and {@code isolate} stack traces respecting process isolate transition
     * boundaries.
     */
    static StackTraceElement[] mergeStackTraces(StackTraceElement[] host, StackTraceElement[] isolate, boolean originatedInHost) {
        int hostStackEndIndex;
        int isolateStackEndIndex;
        if (originatedInHost) {
            MirrorThreadInfo mirrorThreadInfo = getMirrorThreadEntryMethodInfo(host);
            hostStackEndIndex = mirrorThreadInfo.runnableIndex;
            if (hostStackEndIndex == host.length && mirrorThreadInfo.dispatchLoopIndex != -1) {
                // Already merged
                return host;
            }
            isolateStackEndIndex = getMirrorThreadEntryMethodInfo(isolate).runnableIndex;
        } else {
            MirrorThreadInfo mirrorThreadInfo = getMirrorThreadEntryMethodInfo(isolate);
            isolateStackEndIndex = mirrorThreadInfo.runnableIndex;
            if (isolateStackEndIndex == isolate.length && mirrorThreadInfo.dispatchLoopIndex != -1) {
                // Already merged
                return isolate;
            }
            hostStackEndIndex = getMirrorThreadEntryMethodInfo(host).runnableIndex;
        }
        StackTraceElement[] merged = mergeStackTraces(host, isolate, getTransitionIndex(host), hostStackEndIndex, getTransitionIndex(isolate), isolateStackEndIndex, originatedInHost);
        return merged;
    }

    private static StackTraceElement[] mergeStackTraces(
                    StackTraceElement[] hostStackTrace,
                    StackTraceElement[] isolateStackTrace,
                    int hostStackStartIndex,
                    int hostStackEndIndex,
                    int isolateStackStartIndex,
                    int isolateStackEndIndex,
                    boolean originatedInHotSpot) {
        int targetIndex = 0;
        StackTraceElement[] merged = new StackTraceElement[hostStackEndIndex - hostStackStartIndex + isolateStackEndIndex - isolateStackStartIndex];
        boolean startingHostFrame = true;
        boolean startingIsolateFrame = true;
        boolean useHostStack = originatedInHotSpot;
        int hostStackIndex = hostStackStartIndex;
        int isolateStackIndex = isolateStackStartIndex;
        while (hostStackIndex < hostStackEndIndex || isolateStackIndex < isolateStackEndIndex) {
            if (useHostStack) {
                while (hostStackIndex < hostStackEndIndex && (startingHostFrame || !isBoundary(hostStackTrace[hostStackIndex]))) {
                    startingHostFrame = false;
                    merged[targetIndex++] = hostStackTrace[hostStackIndex++];
                }
                startingHostFrame = true;
            } else {
                useHostStack = true;
            }
            while (isolateStackIndex < isolateStackEndIndex && (startingIsolateFrame || !isBoundary(isolateStackTrace[isolateStackIndex]))) {
                startingIsolateFrame = false;
                merged[targetIndex++] = isolateStackTrace[isolateStackIndex++];
            }
            startingIsolateFrame = true;
        }
        return merged;
    }

    private static boolean isBoundary(StackTraceElement frame) {
        return THREAD_CHANNEL_CLASS.equals(frame.getClassName()) && SEND_RECEIVE_METHOD.equals(frame.getMethodName());
    }

    private static int getTransitionIndex(StackTraceElement[] stack) {
        return FOREIGN_EXCEPTION_CLASS.equals(stack[0].getClassName()) && CREATE_METHOD.equals(stack[0].getMethodName()) ? 1 : 0;
    }

    private static MirrorThreadInfo getMirrorThreadEntryMethodInfo(StackTraceElement[] stack) {
        int dispatchIndex = -1;
        int index = 0;
        for (; index < stack.length; index++) {
            StackTraceElement frame = stack[index];
            if (THREAD_CHANNEL_CLASS.equals(frame.getClassName()) && DISPATCH_METHOD.equals(frame.getMethodName())) {
                dispatchIndex = index;
            }
            if (DISPATCH_RUNNABLE.equals(frame.getClassName()) && RUN_METHOD.equals(frame.getMethodName())) {
                break;
            }
        }
        return new MirrorThreadInfo(index, dispatchIndex);
    }

    /**
     * Holds the positions of key method frames in a mirror thread stack trace.
     * 
     * @param runnableIndex the index of the mirror thread's {@code run()} method; this frame and
     *            all frames below it are excluded during stack trace merging
     * @param dispatchLoopIndex the index of the dispatch loop method; this is the first frame from
     *            the mirror thread included in a merged stack trace. Its presence may also indicate
     *            that the stack trace has already been merged.
     */
    private record MirrorThreadInfo(int runnableIndex, int dispatchLoopIndex) {
    }
}
