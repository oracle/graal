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
 * <h1>Truffle Tutorial: Implementing a New Language with Truffle</h1>
 *
 * <div id="contents">
 *
 * For an excellent, in-depth presentation on how to implement your language with Truffle,
 * please have a look at a 
 * <a href="https://youtu.be/FJY96_6Y3a4">three hour walkthrough</a> presented at a recent
 * Conference on Programming Language Design and Implementation
 * (<a href="http://conf.researchr.org/home/pldi-2016">PLDI 2016</a>).
 * <p>
 * <iframe width="854" height="480" src="https://www.youtube.com/embed/FJY96_6Y3a4" frameborder="0" allowfullscreen></iframe>
 * <br>
 * <a href="https://lafo.ssw.uni-linz.ac.at/pub/papers/2016_PLDI_Truffle.pdf">Download Slides</a>
 * <p>
 * Related information:
 * </ul>
 * <li>{@link com.oracle.truffle.api.TruffleLanguage}: base class for Truffle language implementations.</li>
 * <li>{@link com.oracle.truffle.api.vm.PolyglotEngine}: execution environment for Truffle-implemented languages.</li>
 * <li><a href="https://github.com/graalvm/simplelanguage">SimpleLanguage</a>: the tutorial Truffle language implementation.</li>
 * <li>{@linkplain com.oracle.truffle.tutorial Other Truffle Tutorials}
 * </ul>
 *
 * </div>
 * <script src="../doc-files/tutorial.js"></script>
 *
 * @since 0.25
 */
package com.oracle.truffle.tutorial.newlanguage;
