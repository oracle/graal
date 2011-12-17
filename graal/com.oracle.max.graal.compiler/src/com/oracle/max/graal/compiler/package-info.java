/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * The top-level package in Graal containing options, metrics, timers and the main compiler class
 * {@link com.oracle.max.graal.compiler.GraalCompiler}.
 *
 * <H2>{@code GraalCompiler} Overview</H2>
 *
 * Graal is intended to be used with multiple JVM's so makes no use of or reference to classes for a specific JVM, for
 * example Maxine.
 *
 * The compiler is represented by the class {@code GraalCompiler}. {@code GraalCompiler} binds a specific target
 * architecture and JVM interface to produce a usable compiler object.
 * {@code RiMethod} is Graal's representation of a Java method and {@code RiXirGenerator} represents the interface through
 * which the compiler requests the XIR for a given bytecode from the runtime system.
 *
 * <H3>The Graal Compilation Process</H3>
 *
 * {@link com.oracle.max.graal.compiler.GraalCompiler#compileMethod} creates a {@link GraalCompilation} instance and then returns the result of calling its
 * {@link com.oracle.max.graal.compiler.GraalCompilation#compile} method.
 * <p>
 * While there is only one {@code GraalCompiler} instance, there may be several compilations proceeding concurrently, each of
 * which is represented by a unique {@code GraalCompilation} instance. The static method {@link com.oracle.max.graal.compiler.GraalCompilation#current}} returns the
 * {@code GraalCompilation} instance associated with the current thread, and is managed using a {@link java.lang.ThreadLocal} variable.
 * Each {@code GraalCompilation} instance has an associated {@link com.sun.cri.ci.CiStatistics} object that accumulates information about the compilation process.
 * </p>
 * <H3>Supported backends</H3>
 *
 * <ul>
 * <li>AMD64/x64 with SSE2</li>
 * </ul>
 */
package com.oracle.max.graal.compiler;
