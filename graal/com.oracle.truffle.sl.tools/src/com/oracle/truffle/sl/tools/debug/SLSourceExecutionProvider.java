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
package com.oracle.truffle.sl.tools.debug;

import java.io.*;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.sl.*;
import com.oracle.truffle.sl.nodes.instrument.*;
import com.oracle.truffle.sl.runtime.*;
import com.oracle.truffle.tools.debug.engine.*;

/**
 * Specialization of the Truffle debugging engine for the Simple language. The engine implements
 * basic debugging operations during Truffle-based execution.
 */
public final class SLSourceExecutionProvider extends SourceExecutionProvider {

    private final SLContext slContext;

    public SLSourceExecutionProvider(SLContext context) {
        this.slContext = context;
        Probe.registerASTProber(new SLStandardASTProber());
    }

    @Override
    public void languageRun(Source source) {
        try {
            SLMain.run(source);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation of Simple is too, well, simple to support eval. Just for starters, the
     * parser can only produce a whole program.
     */
    @Override
    public Object languageEval(Source source, Node node, MaterializedFrame mFrame) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation of Simple is too, well, simple to support this. Just for starters, the
     * parser can only produce a whole program.
     */
    @Override
    public AdvancedInstrumentRootFactory languageAdvancedInstrumentRootFactory(String expr, AdvancedInstrumentResultListener resultListener) throws DebugException {
        throw new UnsupportedOperationException();
    }
}
