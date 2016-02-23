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

/*
 @ApiInfo(
 group="Stable"
 )
 */

/**
 * Control over {@link com.oracle.truffle.api.debug.Debugger debugging} of your {@link com.oracle.truffle.api.vm.PolyglotEngine}. Each {@link com.oracle.truffle.api.vm.PolyglotEngine}
 * is inherently capable to run in debugging mode - there is just one thing
 * to do - the {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder creator of the virtual machine}
 * needs to turn debugging on when constructing its polyglot execution engine:
 * <pre>
 * vm = {@link com.oracle.truffle.api.vm.PolyglotEngine#buildNew()}.
 *     {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#onEvent(com.oracle.truffle.api.vm.EventConsumer) onEvent}(<b>new</b> {@link com.oracle.truffle.api.vm.EventConsumer EventConsumer}
 *     {@code <}{@link com.oracle.truffle.api.debug.ExecutionEvent}{@code >}() {
 *         <b>public void</b> handle({@link com.oracle.truffle.api.debug.ExecutionEvent} ev) {
 *             <em>// configure the virtual machine as {@link com.oracle.truffle.api.vm.PolyglotEngine#eval(com.oracle.truffle.api.source.Source) new execution} is starting</em>
 *         }
 *     }).
 *     {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#onEvent(com.oracle.truffle.api.vm.EventConsumer) onEvent}(<b>new</b> {@link com.oracle.truffle.api.vm.EventConsumer EventConsumer}{@code <}
 *     {@link com.oracle.truffle.api.debug.SuspendedEvent}{@code >}() {
 *         <b>public void</b> handle({@link com.oracle.truffle.api.debug.SuspendedEvent} ev) {
 *             <em>// execution is suspended on a breakpoint or on a step - decide what next</em>
 *         }
 *     }).{@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#build() build()};
 * </pre>
 * The debugging is controlled by events emitted by the Truffle virtual machine
 * at important moments. The {@link com.oracle.truffle.api.debug.ExecutionEvent}
 * is sent when a call to {@link com.oracle.truffle.api.vm.PolyglotEngine#eval(com.oracle.truffle.api.source.Source)}
 * is made and allows one to configure {@link com.oracle.truffle.api.debug.Breakpoint breakpoints} and/or decide whether the
 * program should {@link com.oracle.truffle.api.debug.ExecutionEvent#prepareStepInto() step-into} or
 * {@link com.oracle.truffle.api.debug.ExecutionEvent#prepareContinue() just run}. Once the execution is suspended a
 * {@link com.oracle.truffle.api.debug.SuspendedEvent} is generated which
 * allows one to inspect the stack and choose the further execution mode
 * ({@link com.oracle.truffle.api.debug.SuspendedEvent#prepareStepInto(int) step-into}, {@link com.oracle.truffle.api.debug.SuspendedEvent#prepareStepOver(int) step-over},
 *  {@link com.oracle.truffle.api.debug.SuspendedEvent#prepareStepOut() step-out}, {@link com.oracle.truffle.api.debug.SuspendedEvent#prepareContinue() continue}).
 * <p>
 * The events methods are only available when the event is being delivered and
 * shouldn't be used anytime later. Both events however provide access to
 * {@link com.oracle.truffle.api.debug.Debugger} which can be kept and used
 * during whole existence of the {@link com.oracle.truffle.api.vm.PolyglotEngine}.
 * {@link com.oracle.truffle.api.debug.Debugger} is the central class that
 * keeps information about {@link com.oracle.truffle.api.debug.Debugger#getBreakpoints() registered breakpoints}
 * and allows one create new {@link com.oracle.truffle.api.debug.Breakpoint ones}.
 *
 * <h4>Turning on Stepping Mode</h4>
 *
 * In case you want your execution to pause on first statement, register for
 * {@link com.oracle.truffle.api.debug.ExecutionEvent} and once delivered
 * call {@link com.oracle.truffle.api.debug.ExecutionEvent#prepareStepInto()}.
 *
 * <h4>Register a {@link com.oracle.truffle.api.debug.Breakpoint}</h4>
 *
 * Wait for execution to be started - which generates an
 * {@link com.oracle.truffle.api.debug.ExecutionEvent}. Use its
 * {@link com.oracle.truffle.api.debug.Debugger ev.getDebugger}()
 * methods to submit breakpoints.
 */
package com.oracle.truffle.api.debug;

