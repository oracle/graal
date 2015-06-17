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
 * This package contains an experimental framework for building simple command-line oriented debuggers
 * that work with Truffle-implemented languages; it is used mainly for testing Truffle's built-in
 * {@link com.oracle.truffle.tools.debug.engine.DebugEngine}, which actually provides the debugging services.
 * <p>
 * Truffle debugging is made possible by the general purpose Instrumentation Framework built
 * into the Truffle platform.  Some online documentation for the Instrumentation Framework is available
 * online:
 * <quote>
 * <a href="https://wiki.openjdk.java.net/display/Graal/Instrumentation+API">https://wiki.openjdk.java.net/display/Graal/Instrumentation+API</a>
 * </quote>
 * <p>
 * Building one of these command line debuggers requires creating language-specific instances of:
 * <ol>
 * <li>{@link com.oracle.truffle.tools.debug.engine.DebugEngine},
 * noting that this instance also depends on related services provided by the language implementation,</li>
 * <li>{@link com.oracle.truffle.tools.debug.shell.REPLServer}, best accomplished by copying the implementation for
 * Truffle's demonstration language "Simple" (a.k.a. "SL").</li>
 * </ol>
 *
 * <strong>Disclaimer: </strong> although these command line debuggers are useful, they are
 * not intended, and will not be maintained as, fully functioning debuggers.  They should be
 * considered valuable tools for the maintainers of the {@link com.oracle.truffle.tools.debug.engine.DebugEngine},
 * as well as for Truffle language implementors for whom concurrent access to any kind debugging services can
 * be quite helpful.
 * <p>
 * <strong>Note:</strong> Both the functionality and API for this package are under active development.
 * <p>
 * @see com.oracle.truffle.api.instrument
 * @see com.oracle.truffle.tools.debug.engine
 */
package com.oracle.truffle.tools.debug.shell;

