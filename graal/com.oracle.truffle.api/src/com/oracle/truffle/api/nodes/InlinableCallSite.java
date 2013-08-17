/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.*;

/**
 * Denotes a call node that can inline the tree of its associated call target.
 * 
 * @see InlinedCallSite
 */
public interface InlinableCallSite {

    /**
     * Returns the number of calls since the last reset of the call count.
     * 
     * @return the current call count.
     */
    int getCallCount();

    /**
     * Resets the call count to 0.
     */
    void resetCallCount();

    /**
     * Returns the tree that would be inlined by a call to {@link #inline(FrameFactory)}.
     * 
     * @return the node tree to be inlined.
     */
    Node getInlineTree();

    /**
     * Returns the call target associated with this call site.
     * 
     * @return the inlinable {@link CallTarget}.
     */
    CallTarget getCallTarget();

    /**
     * Instructs the call node to inline the associated call target.
     * 
     * @param factory Frame factory for creating new virtual frames for inlined calls.
     * @return {@code true} if call target was inlined; {@code false} otherwise.
     */
    boolean inline(FrameFactory factory);
}
