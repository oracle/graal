/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.frame;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.nodes.Node;

/**
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
         * @since 0.8 or earlier
         * @deprecated without replacement. This mode always returns <code>null</code>.
         **/
        @Deprecated
        NONE,

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
     *
     * @since 0.8 or earlier
     * @deprecated use {@link #getFrame(FrameAccess)} instead. It is equivalent to
     *             <code>FrameInstance.getFrame(access, true)</code>.
     */
    @Deprecated
    default Frame getFrame(FrameAccess access, @SuppressWarnings("unused") boolean slowPath) {
        return getFrame(access);
    }

    /**
     * Accesses the underlying frame using a specified {@link FrameAccess access mode}.
     *
     * @see FrameAccess
     * @since 0.23
     */
    default Frame getFrame(FrameAccess access) {
        return getFrame(access, true);
    }

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
     * @since 0.8 or earlier
     **/
    CallTarget getCallTarget();
}
