/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrument;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * A listener of Truffle AST runtime execution events that can collect information and possibly
 * intervene on behalf of an external tool.
 */
public interface TruffleEventListener {

    /**
     * Receive notification that an AST node's execute method is about to be called.
     */
    void enter(Node node, VirtualFrame frame);

    /**
     * Receive notification that an AST Node's {@code void}-valued execute method has just returned.
     */
    void returnVoid(Node node, VirtualFrame frame);

    /**
     * Receive notification that an AST Node'sexecute method has just returned a value (boxed if
     * primitive).
     */
    void returnValue(Node node, VirtualFrame frame, Object result);

    /**
     * Receive notification that an AST Node's execute method has just thrown an exception.
     */
    void returnExceptional(Node node, VirtualFrame frame, Exception exception);

}
