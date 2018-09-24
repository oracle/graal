/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
