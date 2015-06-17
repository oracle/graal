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

/**
 * This package contains the shared (language-agnostic) support for implementing debuggers
 * that work with Truffle-implemented languages.
 * <p>
 * This implementation is made possible by the general purpose Instrumentation Framework built
 * into the Truffle platform.  Some online documentation for the Instrumentation Framework is available
 * online:
 * <quote>
 * <a href="https://wiki.openjdk.java.net/display/Graal/Instrumentation+API">https://wiki.openjdk.java.net/display/Graal/Instrumentation+API</a>
 * </quote>
 * <p>
 * Debugging services for a Truffle-implemented language are provided by creating an instance
 * of {@link com.oracle.truffle.tools.debug.engine.DebugEngine} specialized for a specific language.  The DebugEngine can:
 * <ul>
 * <li>Load and run sources in the language</li>
 * <li>Set breakpoints possibly with conditions and other attributes, on source lines</li>
 * <li>Navigate by Continue, StepIn, StepOver, or StepOut</li>
 * <li>Examine the execution stack</li>
 * <li>Examine the contents of a stack frame</li>
 * <li>Evaluate a code fragment in the context of a stack frame</li>
 * </ul>
 * <p>
 * Specialization of the DebugEngine for a Truffle-implemented language takes several forms:
 * <ol>
 * <li>A specification from the language implementor that adds Instrumentation "tags" to the nodes
 * that a debugger should know about, for example Statements, Calls, and Throws</li>
 * <li>Methods to run programs/scripts generally, and more specifically run text fragments in the context of
 * a particular frame/Node in a halted Truffle execution</li>
 * <li>Utility methods, such as providing textual displays of Objects that represent values in the language</li>
 * </ol>
 * <p>
 * <strong>Note:</strong> Both the functionality and API for this package are under active development.
 * <p>
 * @see com.oracle.truffle.api.instrument
 */
package com.oracle.truffle.tools.debug.engine;

