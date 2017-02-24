/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

// @formatter:off

/*
 @ApiInfo(
 group="Tutorial"
 )
 */
/**
 * <h1>Truffle Tutorial: Implementing an LLVM-based Language with Sulong</h1>
 *
 * <div id="contents">
 *
 * Sulong is an LLVM IR interpreter implemented in Truffle.
 * It supports execution of all LLVM-based languages, including C, C++, and Fortran,
 * in the Truffle execution environment and presents opportunities for <em>interoperation</em>
 * between these and other Truffle-implemented languages with low-overhead.
 * <p>
 * More information is available at the Sulong
 * <a href="https://github.com/graalvm/sulong/blob/master/README.md">README</a> and
 * <a href="https://github.com/graalvm/sulong/blob/master/docs/FAQ.md">FAQ</a>.
 *
 * <p>
 * Related information:
 * </ul>
 * <li>Sulong home: <a href="https://github.com/graalvm/sulong">https://github.com/graalvm/sulong</a></li>
 * <li>{@linkplain com.oracle.truffle.tutorial Other Truffle Tutorials}
 * </ul>
 *
 * </div>
 * <script src="../doc-files/tutorial.js"></script>
 *
 * @since 0.25
 */
package com.oracle.truffle.tutorial.sulong;