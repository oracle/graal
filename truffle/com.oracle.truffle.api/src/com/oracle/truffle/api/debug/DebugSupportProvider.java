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
package com.oracle.truffle.api.debug;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;

/**
 * Access to language-specific information and execution services to enable debugging.
 */
public interface DebugSupportProvider extends ToolSupportProvider {

    /**
     * Runs source code.
     *
     * @param source code
     * @throws DebugSupportException if unable to run successfully
     */
    void run(Source source) throws DebugSupportException;

    /**
     * Runs source code in a halted execution context, or at top level.
     *
     * @param source the code to run
     * @param node node where execution halted, {@code null} if no execution context
     * @param mFrame frame where execution halted, {@code null} if no execution context
     * @return result of running the code in the context, or at top level if no execution context.
     * @throws DebugSupportException if the evaluation cannot be performed
     */
    Object evalInContext(Source source, Node node, MaterializedFrame mFrame) throws DebugSupportException;

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
     * @throws DebugSupportException if the factory cannot be created, for example if the expression
     *             is badly formed.
     */
    AdvancedInstrumentRootFactory createAdvancedInstrumentRootFactory(String expr, AdvancedInstrumentResultListener resultListener) throws DebugSupportException;
}
