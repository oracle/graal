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
 group="Tools & Extras"
 )
 */

/**
 * <strong>Warning:</strong> The public classes in this package have been deprecated and will be
 * removed from the Truffle public API in a subsequent release.
 * <p>
 * This package contains <strong>REPL*</strong>: an experimental framework for building a
 * <em>language-agnostic</em> command-line oriented debugger that:
 * <ul>
 * <li>works with every Truffle-implemented language "out of the box", i.e. requiring minimal
 * additional support by language implementors;</li>
 * <li>works simultaneously, without special configuration, for all Truffle language implementations
 * available to it; and</li>
 * <li>demonstrates Truffle language-interopability by debugging seamlessly across Truffle
 * <em>cross-language</em> calls.</li>
 * </ul>
 * <p>
 * <h4>Goals for building <strong>REPL*</strong></h4>
 * <ol>
 * <li>Emulates a client/server architecture to demonstrate that language-agnostic debugging can be
 * implemented over wire protocols. Wire protocol communication between client and server is
 * <em>partially</em> emulated by passing messages expressed as textual key-value pairs. The
 * emulation is <em>partial</em> because both run interleaved on a single JVM thread, with some
 * sharing of resources.</li>
 * <li>Provide a working debugger that is always available during development of new Truffle
 * language implementations.</li>
 * <li>Provide a working debugger with extra support for Truffle language development, in particular
 * the ability to inspect the current structure of the Truffle AST around a halted location.</li>
 * </ol>
 * <h4>Command Set</h4> The Command Line Interface (CLI) for <strong>REPL*</strong> is based as much
 * as possible on the CLI for the <a href="http://www.gnu.org/software/gdb/documentation/">GDB
 * Debugger.</a>
 * <h4>REPL* Functionality</h4> Basic navigation:
 * <ul>
 * <li>StepIn (n times)</li>
 * <li>StepOut (n times)</li>
 * <li>StepOver (n times)</li>
 * </ul>
 * Execution:
 * <ul>
 * <li>Load a file source</li>
 * <li>Call a defined symbol</li>
 * </ul>
 * Stack:
 * <ul>
 * <li>List frames in current execution stack</li>
 * <li>Select a frame</li>
 * <li>Display selected frame contents</li>
 * <li>Move frame selection up/down</li>
 * </ul>
 * Evaluate:
 * <ul>
 * <li>Evaluate a Language string in halted context</li>
 * <li>Evaluate a Language string in selected frame</li>
 * </ul>
 * Breakpoints:
 * <ul>
 * <li>Set/create on a specified line</li>
 * <li>Set/create on any throw (before exception created)</li>
 * <li>Enable / Disable</li>
 * <li>One-shot (once only)</li>
 * <li>Get <em>Hit</em> count</li>
 * <li>Set <em>Ignore</em> count</li>
 * <li>Unset/dispose</li>
 * <li>Get all breakpoints</li>
 * <li>Find a breakpoint by UID</li>
 * <li>Set/clear the condition on a breakpoint</li>
 * </ul>
 * Others:
 * <ul>
 * <li>Display halted location in source</li>
 * <li>Nested execution
 * <li>Help</li>
 * <li>Info displays</li>
 * <li>Set/display options</li>
 * <li>Display Truffle AST structure</li>
 * </ul>
 *
 * @since 0.8 or earlier
 */
package com.oracle.truffle.tools.debug.shell;
