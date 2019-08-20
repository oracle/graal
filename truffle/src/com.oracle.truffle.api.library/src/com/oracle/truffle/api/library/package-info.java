/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
/**
 * Truffle Libraries allow language implementations to use polymorphic dispatch for receiver types
 * with support for implementation specific caching/profiling and customization of the dispatch.
 * Libraries enable modularity and encapsulation for representation types in language
 * implementations.
 * <p>
 * Before reading the javadoc make sure you have read the
 * <a href= "https://github.com/oracle/graal/blob/master/truffle/docs/TruffleLibraries.md">tutorial
 * </a>.
 * <p>
 * Start learning Truffle Libraries by reading the following articles:
 * <ul>
 * <li>{@link com.oracle.truffle.api.library.GenerateLibrary Libraries} specify the set of available
 * messages i.e. the protocol.
 * <li>{@link com.oracle.truffle.api.library.ExportLibrary Exports} implement a library for a
 * receiver type.
 * <li>Automatic dispatching using
 * {@linkplain com.oracle.truffle.api.library.CachedLibrary @CachedLibrary} allows to call library
 * messages with concrete receiver and parameters from nodes using the
 * {@link com.oracle.truffle.api.dsl.Specialization specialization} DSL.
 * <li>Manual dispatching using the {@linkplain com.oracle.truffle.api.library.LibraryFactory
 * LibraryFactory} allows to perform slow-path calls and custom inline cache implementations.
 * </ul>
 * <p>
 * Advanced Features:
 * <ul>
 * <li>The {@link com.oracle.truffle.api.library.ReflectionLibrary} allows to reflectively export
 * and call messages without binary dependency to a library. It also allows to implement library
 * agnostic proxies.
 * <li>The {@link com.oracle.truffle.api.library.DynamicDispatchLibrary} allows to implement
 * receivers that dynamically dispatch to exported message implementations.
 * </ul>
 *
 * @see com.oracle.truffle.api.library.GenerateLibrary To specify libraries
 * @see com.oracle.truffle.api.library.ExportLibrary To export libraries
 * @see com.oracle.truffle.api.library.CachedLibrary To dispatch libraries from nodes
 * @see com.oracle.truffle.api.library.LibraryFactory To manually dispatch messages
 * @since 19.0
 */
package com.oracle.truffle.api.library;