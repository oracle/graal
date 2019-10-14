/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.frame;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.nodes.Node;

/**
 * Represents a current frame instance on the stack. Please note that any frame instance must not be
 * used after the {@link TruffleRuntime#iterateFrames(FrameInstanceVisitor) iterateFrames()} method
 * returned.
 *
 * @see TruffleRuntime#iterateFrames(FrameInstanceVisitor) To iterate the current frame instances on
 *      the stack.
 * @since 0.8 or earlier
 */
public interface FrameInstance {
    /**
     * Access mode for {@link FrameInstance#getFrame(FrameAccess)}.
     *
     * @see FrameInstance#getFrame(FrameAccess)
     * @since 0.8 or earlier
     */
    enum FrameAccess {

        /**
         * This mode allows to read the frame and provides read only access to its local variables.
         * The returned frame must not be stored/persisted. Writing local variables in this mode
         * will result in an {@link AssertionError} only if assertions (-ea) are enabled.
         *
         * @since 0.8 or earlier
         */
        READ_ONLY,

        /**
         * This mode allows to read the frame and provides read and write access to its local
         * variables. The returned frame must not be stored/persisted.
         *
         * @since 0.8 or earlier
         **/
        READ_WRITE,
        /**
         * This mode allows to read a materialized version of the frame and provides read and write
         * access to its local variables. In addition to {@link #READ_WRITE} this mode allows to
         * store/persist the returned frame.
         *
         * @since 0.8 or earlier
         **/
        MATERIALIZE
    }

    /**
     * Accesses the underlying frame using a specified {@link FrameAccess access mode}.
     *
     * @see FrameAccess
     * @since 0.23
     */
    Frame getFrame(FrameAccess access);

    /** @since 0.8 or earlier */
    boolean isVirtualFrame();

    /**
     * Returns a node representing the callsite of the next new target on the stack.
     *
     * This picture indicates how {@link FrameInstance} groups the stack.
     *
     * <pre>
     *                      ===============
     *  {@link TruffleRuntime#getCurrentFrame() Current}:         ,>|  CallTarget   | FrameInstance
     *                   |  ===============
     *  {@link TruffleRuntime#getCallerFrame() Caller}:          '-|  CallNode     | FrameInstance
     *                   ,>|  CallTarget   |
     *                   |  ===============
     *                   '-|  CallNode     | FrameInstance
     *                     |  CallTarget   |
     *                      ===============
     *                           ...
     *                      ===============
     *                     |  CallNode     | FrameInstance
     * Initial call:       |  CallTarget   |
     *                      ===============
     *
     * </pre>
     *
     * @return a node representing the callsite of the next new target on the stack. Null in case
     *         there is no upper target or if the target was not invoked using a
     *         {@link TruffleRuntime#createDirectCallNode(CallTarget) direct} or
     *         {@link TruffleRuntime#createIndirectCallNode() indirect} call node.
     *
     * @since 0.8 or earlier
     **/
    Node getCallNode();

    /**
     * The {@link CallTarget} being invoked in this frame.
     * <p>
     * See {@link #getCallNode()} for the relation between call node and CallTarget.
     *
     * @since 0.8 or earlier
     **/
    CallTarget getCallTarget();

}
