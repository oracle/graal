/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes.instrument;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Marker interface for all Truffle <strong>instrumentation nodes</strong>: nodes that are do not
 * appear in Truffle ASTs as part of a language's execution semantics.
 * <p>
 * In documentation related to instrumentation nodes, these are distinguished by referring to all
 * other nodes (i.e. ones that <em>do</em> implement language semantics) as <em>AST nodes</em>.
 */
public interface InstrumentationProbeEvents {

    /**
     * Notifies a probe that receiver that an AST node's execute method has just been entered.
     * Callers should assure that a matching call to {@link #leave(Node, VirtualFrame, Object)}
     * always follows.
     * 
     * @param astNode The AST node on which the execute method is being called
     * @param frame The frame being passed to the execute method
     */
    void enter(Node astNode, VirtualFrame frame);

    /**
     * Notifies a probe that an AST Node's void-valued execute method is about to exit.
     * <p>
     * Callers should assure (via {@code try/finally}) that a matching call to this method always
     * follows a call to {@link #enter(Node, VirtualFrame)}.
     * 
     * @param astNode The AST node on which the execute method is being called
     * @param frame The frame that was passed to the execute method
     */
    void leave(Node astNode, VirtualFrame frame);

    /**
     * Notifies a probe that an AST Node's boolean-valued execute method is about to exit.
     * <p>
     * Callers should assure (via {@code try/finally}) that a matching call to this method always
     * follows a call to {@link #enter(Node, VirtualFrame)}.
     * 
     * @param astNode The AST node on which the execute method is being called
     * @param frame The frame that was passed to the execute method
     * @param result The result of the call to the execute method.
     */
    void leave(Node astNode, VirtualFrame frame, boolean result);

    /**
     * Notifies a probe that an AST Node's byte-valued execute method is about to exit.
     * <p>
     * Callers should assure (via {@code try/finally}) that a matching call to this method always
     * follows a call to {@link #enter(Node, VirtualFrame)}.
     * 
     * @param astNode The AST node on which the execute method is being called
     * @param frame The frame that was passed to the execute method
     * @param result The result of the call to the execute method.
     */
    void leave(Node astNode, VirtualFrame frame, byte result);

    /**
     * Notifies a probe that an AST Node's short-valued execute method is about to exit.
     * <p>
     * Callers should assure (via {@code try/finally}) that a matching call to this method always
     * follows a call to {@link #enter(Node, VirtualFrame)}.
     * 
     * @param astNode The AST node on which the execute method is being called
     * @param frame The frame that was passed to the execute method
     * @param result The result of the call to the execute method.
     */
    void leave(Node astNode, VirtualFrame frame, short result);

    /**
     * Notifies a probe that an AST Node's integer-valued execute method is about to exit.
     * <p>
     * Callers should assure (via {@code try/finally}) that a matching call to this method always
     * follows a call to {@link #enter(Node, VirtualFrame)}.
     * 
     * @param astNode The AST node on which the execute method is being called
     * @param frame The frame that was passed to the execute method
     * @param result The result of the call to the execute method.
     */
    void leave(Node astNode, VirtualFrame frame, int result);

    /**
     * Notifies a probe that an AST Node's long-valued execute method is about to exit.
     * <p>
     * Callers should assure (via {@code try/finally}) that a matching call to this method always
     * follows a call to {@link #enter(Node, VirtualFrame)}.
     * 
     * @param astNode The AST node on which the execute method is being called
     * @param frame The frame that was passed to the execute method
     * @param result The result of the call to the execute method.
     */
    void leave(Node astNode, VirtualFrame frame, long result);

    /**
     * Notifies a probe that an AST Node's float-valued execute method is about to exit.
     * <p>
     * Callers should assure (via {@code try/finally}) that a matching call to this method always
     * follows a call to {@link #enter(Node, VirtualFrame)}.
     * 
     * @param astNode The AST node on which the execute method is being called
     * @param frame The frame that was passed to the execute method
     * @param result The result of the call to the execute method.
     */
    void leave(Node astNode, VirtualFrame frame, float result);

    /**
     * Notifies a probe that an AST Node's double-valued execute method is about to exit.
     * <p>
     * Callers should assure (via {@code try/finally}) that a matching call to this method always
     * follows a call to {@link #enter(Node, VirtualFrame)}.
     * 
     * @param astNode The AST node on which the execute method is being called
     * @param frame The frame that was passed to the execute method
     * @param result The result of the call to the execute method.
     */
    void leave(Node astNode, VirtualFrame frame, double result);

    /**
     * Notifies a probe that an AST Node's char-valued execute method is about to exit.
     * <p>
     * Callers should assure (via {@code try/finally}) that a matching call to this method always
     * follows a call to {@link #enter(Node, VirtualFrame)}.
     * 
     * @param astNode The AST node on which the execute method is being called
     * @param frame The frame that was passed to the execute method
     * @param result The result of the call to the execute method.
     */
    void leave(Node astNode, VirtualFrame frame, char result);

    /**
     * Notifies a probe that an AST Node's object-valued execute method is about to exit.
     * <p>
     * Callers should assure (via {@code try/finally}) that a matching call to this method always
     * follows a call to {@link #enter(Node, VirtualFrame)}.
     * 
     * @param astNode The AST node on which the execute method is being called
     * @param frame The frame that was passed to the execute method
     * @param result The result of the call to the execute method.
     */
    void leave(Node astNode, VirtualFrame frame, Object result);

    /**
     * Notifies a probe that an AST Node's execute method is about to leave under exceptional
     * conditions, returning no value.
     * <p>
     * Callers should assure (via {@code try/finally}) that a matching call to this method always
     * follows a call to {@link #enter(Node, VirtualFrame)}.
     * 
     * @param astNode The AST node on which the execute method is being called
     * @param frame The frame that was passed to the execute method
     * @param e the exception associated with the unusual return
     */
    void leaveExceptional(Node astNode, VirtualFrame frame, Exception e);

    /**
     * Notifies a probe that an AST node is about to be replaced with another.
     * 
     * @param oldAstNode the AST node currently in the tree
     * @param newAstNode the AST replacement node
     * @param reason explanation for the replacement
     */
    void replace(Node oldAstNode, Node newAstNode, String reason);

}
