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
 * <h1>Truffle Tutorial: The Graal Compiler</h1>
 * <div id="contents">
 *
 * This 3-hour tutorial presents Graal, the high-performance dynamic compiler for Java written in Java
 * that enables very high performance for Truffle-implemented languages.
 * The tutorial was presented February 8, 2015 at the International Symposium on Code Generation and Optimization
 * (<a href="http://cgo.org/cgo2015/">CGO 2015</a>) and covers the following topics:
 * <ul>
 * <li>Key distinguishing features of Graal</li>
 * <li>Introduction to the Graal IR: basic properties, instructions, and optimization phases</li>
 * <li>Speculative optimizations: first-class support for optimistic optimizations and deoptimization</li>
 * <li>Graal API: separation of the compiler from the VM</li>
 * <li>Snippets: expressing high-level semantics in low-level Java code</li>
 * <li>Compiler intrinsics: use all your hardware instructions with Graal</li>
 * <li>Using Graal for static analysis</li>
 * <li>Custom compilations with Graal: integration of the compiler with an application or library</li>
 * <li>Graal as a compiler for dynamic programming languages in the Truffle framework</li>
 * </ul>
 * <p>
 * Video recording: <a href="https://youtu.be/Af9T9kFk1lM">Part 1</a>,
 * <a href="https://youtu.be/WyU7KctlhzE">Part 2</a>
 * <br>
 * <a href="http://lafo.ssw.uni-linz.ac.at/papers/2015_CGO_Graal.pdf">Download Slides</a>
 *
 *
 * <p>
 * Related information:
 * <ul>
 * <li>The Graal project home:  <a href="https://github.com/graalvm">https://github.com/graalvm</a></li>
 * <li><a href="https://github.com/graalvm/graal-core/blob/master/docs/Publications.md#graal-papers">Graal Publications</a></li>
 * <li>{@linkplain com.oracle.truffle.tutorial Other Truffle Tutorials}
 * </ul>

 *
 * </div>
 * <script src="../doc-files/tutorial.js"></script>
 *
 * @since 0.25
 */
package com.oracle.truffle.tutorial.graal;