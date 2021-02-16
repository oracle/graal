/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * A node that is repeatedly invoked as part of a Truffle loop control structure. Repeating nodes
 * must extend {@link Node} or a subclass of {@link Node}.
 *
 * Repeating nodes are intended to be implemented by guest language implementations. For a full
 * usage example please see {@link LoopNode}.
 *
 * @see LoopNode
 * @see TruffleRuntime#createLoopNode(RepeatingNode)
 * @since 0.8 or earlier
 */
public interface RepeatingNode extends NodeInterface {
    /**
     * A value indicating that the loop should be repeated.
     *
     * @since 19.3
     */
    Object CONTINUE_LOOP_STATUS = new Object() {
        @Override
        public String toString() {
            return "CONTINUE_LOOP_STATUS";
        }
    };

    /**
     * A value indicating that the loop should not be repeated. Any other value different than
     * {@code CONTINUE_LOOP_STATUS} can also be used to indicate that the loop should not be
     * repeated.
     *
     * @since 19.3
     */
    Object BREAK_LOOP_STATUS = new Object() {
        @Override
        public String toString() {
            return "BREAK_LOOP_STATUS";
        }
    };

    /**
     * Repeatedly invoked by a {@link LoopNode loop node} implementation until the method returns
     * <code>false</code> or throws an exception.
     *
     * @param frame the current execution frame passed through the interpreter
     * @return <code>true</code> if the method should be executed again to complete the loop and
     *         <code>false</code> if it must not.
     * @since 0.8 or earlier
     */
    boolean executeRepeating(VirtualFrame frame);

    /**
     * Repeatedly invoked by a {@link LoopNode loop node} implementation, but allows returning a
     * language-specific loop exit status. Only languages that need to return custom loop statuses
     * should override this method.
     *
     * @param frame the current execution frame passed through the interpreter
     * @return a value <code>v</code> satisfying {@link RepeatingNode#shouldContinue
     *         shouldContinue(v)}<code> == true</code> if the method should be executed again to
     *         complete the loop and any other value if it must not.
     * @since 19.3
     */
    default Object executeRepeatingWithValue(VirtualFrame frame) {
        if (executeRepeating(frame)) {
            return CONTINUE_LOOP_STATUS;
        } else {
            return BREAK_LOOP_STATUS;
        }
    }

    /**
     * Returns a placeholder loop status used internally before the first iteration.
     *
     * @return a value satisfying
     *         <code>{@link RepeatingNode#shouldContinue shouldContinue}({@link RepeatingNode#initialLoopStatus initialLoopStatus}(v)) == true</code>
     * @since 20.3
     */
    default Object initialLoopStatus() {
        return CONTINUE_LOOP_STATUS;
    }

    /**
     * Predicate called on values returned by
     * {@link RepeatingNode#executeRepeatingWithValue(VirtualFrame) executeRepeatingWithValue()}.
     *
     * @param returnValue a value returned by
     *            {@link RepeatingNode#executeRepeatingWithValue(VirtualFrame)
     *            executeRepeatingWithValue()}
     * @return true if the loop should continue executing or false otherwise
     * @since 20.3
     */
    default boolean shouldContinue(Object returnValue) {
        return returnValue == initialLoopStatus();
    }
}
