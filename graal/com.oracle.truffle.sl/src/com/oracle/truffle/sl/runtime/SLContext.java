/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.sl.runtime;

import java.io.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.sl.builtins.*;
import com.oracle.truffle.sl.nodes.*;

public final class SLContext {
    private final SourceManager sourceManager;
    private final PrintStream output;
    private final SLFunctionRegistry functionRegistry;

    public SLContext(SourceManager sourceManager, PrintStream output) {
        this.sourceManager = sourceManager;
        this.output = output;
        this.functionRegistry = new SLFunctionRegistry();

        installBuiltins();
    }

    public SourceManager getSourceManager() {
        return sourceManager;
    }

    public PrintStream getPrintOutput() {
        return output;
    }

    public SLFunctionRegistry getFunctionRegistry() {
        return functionRegistry;
    }

    private void installBuiltins() {
        installBuiltin(SLPrintBuiltinFactory.getInstance(), "print");
        installBuiltin(SLTimeBuiltinFactory.getInstance(), "time");
        installBuiltin(SLDefineFunctionBuiltinFactory.getInstance(), "defineFunction");
    }

    private void installBuiltin(NodeFactory<? extends SLBuiltinNode> factory, String name) {
        getFunctionRegistry().register(name, SLRootNode.createBuiltin(this, factory, name));
    }
}
