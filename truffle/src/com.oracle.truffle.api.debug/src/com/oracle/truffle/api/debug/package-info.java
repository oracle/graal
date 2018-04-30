/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

/*
 @ApiInfo(
 group="Truffle"
 )
 */

/**
 * The debug package contains a debugger API that can be used to install breakpoints, step through
 * and control the execution of Truffle guest language applications. The debug package is designed
 * to be used with multiple languages and multiple threads executing at the same time.
 *
 * <h4>Getting started using the debugger</h4>
 *
 * First the debugger instance needs to be discovered using a Truffle guest language with
 * {@link com.oracle.truffle.api.debug.Debugger#find(com.oracle.truffle.api.TruffleLanguage.Env)}.
 * Next a debugger session needs to be started with
 * {@link com.oracle.truffle.api.debug.Debugger#startSession(SuspendedCallback)} providing a
 * {@link com.oracle.truffle.api.debug.SuspendedCallback callback} that will be invoked whenever the
 * execution is suspended. The debugger client can either
 * {@link com.oracle.truffle.api.debug.DebuggerSession#install(Breakpoint) install} a breakpoint or
 * {@link com.oracle.truffle.api.debug.DebuggerSession#suspendNextExecution() suspend} the current
 * or next execution. Whenever the execution is suspended and the
 * {@link com.oracle.truffle.api.debug.SuspendedCallback callback} is invoked the client can decide
 * step into, step out or step over the next statements. For a usage example please refer to
 * {@link com.oracle.truffle.api.debug.DebuggerSession} and
 * {@link com.oracle.truffle.api.debug.Breakpoint}.
 * <p>
 *
 * <h4>Enable Debugging for your Truffle guest language</h4>
 *
 * The platform's core support for {@link com.oracle.truffle.api.debug.Debugger debugging} is
 * language-agnostic. A {@link com.oracle.truffle.api.TruffleLanguage language implementation}
 * enables debugging by supplying extra information in every AST that configures debugger behavior
 * for code written in that particular language.
 * <p>
 * This extra information is expressed using so called tags. Tags can be applied to AST nodes by
 * implementing the {@link com.oracle.truffle.api.instrumentation.InstrumentableNode#hasTag(Class)}
 * method. The debugger requires the guest language to implement statement and call tags from the
 * set of standard Truffle tags. Please refer to
 * {@link com.oracle.truffle.api.instrumentation.StandardTags} on how to implement them.
 *
 * @since 0.8 or older
 */
package com.oracle.truffle.api.debug;
