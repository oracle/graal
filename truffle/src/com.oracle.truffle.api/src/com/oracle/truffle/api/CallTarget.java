/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Represents the target of a call. Call targets are created automatically from a {@link RootNode}
 * by calling {@link RootNode#getCallTarget()}.
 * <p>
 * A call target allows the runtime to employ a compilation heuristic to trigger partial evaluation
 * of the underlying {@link RootNode}, typically in the background. Additionally, calling a call
 * target builds a guest language level {@link TruffleStackTrace stack trace} which can be inspected
 * using {@link TruffleRuntime#iterateFrames(com.oracle.truffle.api.frame.FrameInstanceVisitor)} or
 * {@link TruffleStackTrace#getStackTrace(Throwable)}.
 * <p>
 * Do not subclass {@link CallTarget} directly, as this interface is likely to become sealed in the
 * future.
 *
 * @see RootNode
 * @see DirectCallNode
 * @see IndirectCallNode
 * @since 0.8 or earlier
 */
public interface CallTarget {

    /**
     * Calls the encapsulated root node with the given arguments and returns the result.
     * <p>
     * By calling this method, the call location is looked up using
     * {@link EncapsulatingNodeReference}. Use {@link #call(Node, Object...)} if the call location
     * is already known.
     * <p>
     * Calling this method in partially evaluated code will allow it to get inlined if the receiver
     * (this) is a {@link CompilerDirectives#isPartialEvaluationConstant(Object) pe-constant}. Call
     * site {@link RootNode#isCloningAllowed() cloning} is only supported if a
     * {@link DirectCallNode} is used instead.
     *
     * @param arguments The arguments passed to the call, as an object array.
     * @return The result of the call.
     * @see #call(Node, Object...)
     * @since 0.8 or earlier
     */
    Object call(Object... arguments);

    /**
     * Calls the encapsulated root node with an explicit call location and arguments, and returns
     * the result.
     * <p>
     * This method should be preferred over {@link #call(Object...)} if the current location is
     * known, as it avoids looking up the current location from a thread-local.
     * <p>
     *
     * @param location A {@link Node} that identifies the location of this call. The location may be
     *            <code>null</code> if no location is available.
     * @param arguments The arguments passed to the call, as an object array.
     * @return The result of the call.
     * @see #call(Object...)
     * @since 24.1
     */
    default Object call(Node location, Object... arguments) {
        throw CompilerDirectives.shouldNotReachHere("callDirect not supported for this runtime");
    }

}
