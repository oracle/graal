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
package com.oracle.truffle.tools.debug.engine;

import java.util.*;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;

/**
 * Base for language-specific support required by the {@link DebugEngine} and any other tools that
 * run sources.
 */
public abstract class SourceExecutionProvider {

    interface ExecutionListener {

        /**
         * Notifies that execution is about to start and requests initial execution mode.
         */
        void executionStarted(Source source, boolean stepInto);

        /**
         * Notification that the current execution has just ended.
         */
        void executionEnded();
    }

    private final List<ExecutionListener> listeners = new ArrayList<>();

    final void addExecutionListener(ExecutionListener listener) {
        assert listener != null;
        listeners.add(listener);
    }

    /**
     * Runs a script. If "StepInto" is specified, halts at the first location tagged as a
     * {@linkplain StandardSyntaxTag#STATEMENT STATEMENT}.
     */
    final void run(Source source, boolean stepInto) throws DebugException {
        for (ExecutionListener listener : listeners) {
            listener.executionStarted(source, stepInto);
        }
        try {
            languageRun(source);
        } finally {
            for (ExecutionListener listener : listeners) {
                listener.executionEnded();
            }
        }
    }

    /**
     * Evaluates string of language code in a halted execution context, at top level if
     * <code>mFrame==null</code>.
     */
    final Object eval(Source source, Node node, MaterializedFrame mFrame) {
        for (ExecutionListener listener : listeners) {
            listener.executionStarted(source, false);
        }
        try {
            return languageEval(source, node, mFrame);
        } finally {
            for (ExecutionListener listener : listeners) {
                listener.executionEnded();
            }
        }
    }

    /**
     * Creates a language-specific factory to produce instances of {@link AdvancedInstrumentRoot}
     * that, when executed, computes the result of a textual expression in the language; used to
     * create an
     * {@linkplain Instrument#create(AdvancedInstrumentResultListener, AdvancedInstrumentRootFactory, Class, String)
     * Advanced Instrument}.
     *
     * @param expr a guest language expression
     * @param resultListener optional listener for the result of each evaluation.
     * @return a new factory
     * @throws DebugException if the factory cannot be created, for example if the expression is
     *             badly formed.
     */
    final AdvancedInstrumentRootFactory createAdvancedInstrumentRootFactory(String expr, AdvancedInstrumentResultListener resultListener) throws DebugException {
        return languageAdvancedInstrumentRootFactory(expr, resultListener);
    }

    /**
     * Runs source code.
     *
     * @param source code
     * @throws DebugException if unable to run successfully
     */
    public abstract void languageRun(Source source) throws DebugException;

    /**
     * Runs source code in a halted execution context, or at top level.
     *
     * @param source the code to run
     * @param node node where execution halted, {@code null} if no execution context
     * @param mFrame frame where execution halted, {@code null} if no execution context
     * @return result of running the code in the context, or at top level if no execution context.
     */
    public abstract Object languageEval(Source source, Node node, MaterializedFrame mFrame);

    /**
     * Creates a {@linkplain AdvancedInstrumentRootFactory factory} that produces AST fragments from
     * a textual expression, suitable for execution in context by the Instrumentation Framework.
     *
     * @param expr
     * @param resultListener
     * @return a factory that returns AST fragments that compute the expression
     * @throws DebugException if the expression cannot be processed
     *
     * @see Instrument#create(AdvancedInstrumentResultListener, AdvancedInstrumentRootFactory,
     *      Class, String)
     */
    public abstract AdvancedInstrumentRootFactory languageAdvancedInstrumentRootFactory(String expr, AdvancedInstrumentResultListener resultListener) throws DebugException;
}
