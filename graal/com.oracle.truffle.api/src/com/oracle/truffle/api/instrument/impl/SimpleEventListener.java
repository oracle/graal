/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrument.impl;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;

/**
 * An abstract listener for AST {@linkplain TruffleEventListener execution events} that ignores
 * return values and supports handling all events by overriding only two methods:
 * <ul>
 * <li>{@link #enter(Node, VirtualFrame)}, and</li>
 * <li>{@link #returnAny(Node, VirtualFrame)}.</li>
 * </ul>
 */
public abstract class SimpleEventListener implements TruffleEventListener {

    public void enter(Node node, VirtualFrame frame) {
    }

    /**
     * Receive notification that one of an AST Node's execute methods has just returned by any
     * means: with or without a return value (ignored) or via exception (ignored).
     *
     * @param node
     * @param frame
     */
    public void returnAny(Node node, VirtualFrame frame) {
    }

    public final void returnVoid(Node node, VirtualFrame frame) {
        returnAny(node, frame);
    }

    public final void returnValue(Node node, VirtualFrame frame, Object result) {
        returnAny(node, frame);
    }

    public final void returnExceptional(Node node, VirtualFrame frame, Exception e) {
        returnAny(node, frame);
    }

}
