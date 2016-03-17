/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
     * Repeatedly invoked by a {@link LoopNode loop node} implementation until the method returns
     * <code>false</code> or throws an exception.
     *
     * @param frame the current execution frame passed through the interpreter
     * @return <code>true</code> if the method should be executed again to complete the loop and
     *         <code>false</code> if it must not.
     * @since 0.8 or earlier
     */
    boolean executeRepeating(VirtualFrame frame);

}
