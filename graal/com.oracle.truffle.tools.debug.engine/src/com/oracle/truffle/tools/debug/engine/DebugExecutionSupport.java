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

import com.oracle.truffle.api.debug.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;

/**
 * Access to language-specific support for debugging.
 */
final class DebugExecutionSupport {

    interface DebugExecutionListener {

        /**
         * Notifies that execution is about to start and requests initial execution mode.
         */
        void executionStarted(Source source, boolean stepInto);

        /**
         * Notification that the current execution has just ended.
         */
        void executionEnded();
    }

    private final String languageName;
    private final DebugSupportProvider provider;
    private final List<DebugExecutionListener> listeners = new ArrayList<>();

    DebugExecutionSupport(String languageName, DebugSupportProvider provider) {
        this.languageName = languageName;
        this.provider = provider;
    }

    void addExecutionListener(DebugExecutionListener listener) {
        assert listener != null;
        listeners.add(listener);
    }

    String getLanguageName() {
        return languageName;
    }

    Visualizer getVisualizer() {
        return provider.getVisualizer();
    }

    /**
     * Runs a script. If "StepInto" is specified, halts at the first location tagged as a
     * {@linkplain StandardSyntaxTag#STATEMENT STATEMENT}.
     */
    void run(Source source, boolean stepInto) throws DebugException {
        for (DebugExecutionListener listener : listeners) {
            listener.executionStarted(source, stepInto);
        }
        try {
            provider.run(source);
        } catch (DebugSupportException ex) {
            throw new DebugException(ex);
        } finally {
            for (DebugExecutionListener listener : listeners) {
                listener.executionEnded();
            }
        }
    }

    /**
     * Evaluates string of language code in a halted execution context, at top level if
     * <code>mFrame==null</code>.
     *
     * @throws DebugException
     */
    Object evalInContext(Source source, Node node, MaterializedFrame mFrame) throws DebugException {
        for (DebugExecutionListener listener : listeners) {
            listener.executionStarted(source, false);
        }
        try {
            return provider.evalInContext(source, node, mFrame);
        } catch (DebugSupportException ex) {
            throw new DebugException(ex);
        } finally {
            for (DebugExecutionListener listener : listeners) {
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
    AdvancedInstrumentRootFactory createAdvancedInstrumentRootFactory(String expr, AdvancedInstrumentResultListener resultListener) throws DebugException {
        try {
            return provider.createAdvancedInstrumentRootFactory(expr, resultListener);
        } catch (DebugSupportException ex) {
            throw new DebugException(ex);
        }
    }

}
