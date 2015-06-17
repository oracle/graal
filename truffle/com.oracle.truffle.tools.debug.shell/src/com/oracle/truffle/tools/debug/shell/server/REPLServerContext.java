/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.debug.shell.server;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.vm.TruffleVM.Language;
import com.oracle.truffle.tools.debug.engine.*;
import com.oracle.truffle.tools.debug.shell.*;

public abstract class REPLServerContext {

    private final int level;
    private final Node astNode;
    private final MaterializedFrame mFrame;

    protected REPLServerContext(int level, Node astNode, MaterializedFrame mFrame) {
        this.level = level;
        this.astNode = astNode;
        this.mFrame = mFrame;
    }

    /**
     * The nesting depth of this context in the current session.
     */
    public int getLevel() {
        return level;
    }

    /**
     * The AST node where execution is halted in this context.
     */
    public Node getNode() {
        return astNode;
    }

    /**
     * The frame where execution is halted in this context.
     */
    public MaterializedFrame getFrame() {
        return mFrame;
    }

    public abstract Language getLanguage();

    public abstract DebugEngine getDebugEngine();

    /**
     * Dispatches a REPL request to the appropriate handler.
     */
    public abstract REPLMessage[] receive(REPLMessage request);

}
