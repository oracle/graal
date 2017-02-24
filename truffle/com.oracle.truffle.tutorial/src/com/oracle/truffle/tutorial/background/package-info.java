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
 * <h1>Truffle Tutorial: Background</h1>
 * <div id="contents">
 *
 * Truffle is an Open Source library for building programming language implementations as interpreters
 * for self-modifying Abstract Syntax Trees.
 * Together with the Open Source <a href="https://github.com/graalvm/graal-core/">Graal compiler</a>, Truffle
 * represents a significant step forward in programming language implementation technology in the
 * current era of dynamic languages.
 * <p>
 * A growing  body of shared implementation code and services
 * reduces language implementation effort significantly, but leads to extremely competitive runtime
 * performance that matches or exceeds the competition.  The value of the platform is further
 * increased by support for low-overhead language interoperation, as well as a general instrumentation
 * framework that supports multi-language debugging and other external developer tools.
 * <p>
 * Open Source Truffle implementations of both R and Ruby demonstrate excellent performance, and
 * Sulong supports Truffle implementations of all LLVM-based languages.
 * <p>
 * Truffle is part of the Graal Project, developed and maintained by
 * <a href="http://labs.oracle.com/">Oracle Labs</a>
 * and the
 * <a href="http://www.jku.at/isse/content">Institute for System Software</a> of the
 * Johannes Kepler University Linz.
 * <p>
 * For additional information, please visit:
 * <ul>
 * <li>The Graal project home:  <a href="https://github.com/graalvm">https://github.com/graalvm</a></li>
 * <li>Truffle home:  <a href="https://github.com/graalvm/truffle">https://github.com/graalvm/truffle</a></li>
 * <li>Sulong home:  <a href="https://github.com/graalvm/sulong">https://github.com/graalvm/sulong</a></li>
 * <li><a href="http://www.oracle.com/technetwork/oracle-labs/program-languages/overview/index-2301583.html">Graal VM</a>
 * download on the Oracle Technology Network</li>
 * <li><a href="https://github.com/graalvm/truffle/blob/master/docs/Publications.md#truffle-presentations">Truffle Presentations and Publications</a></li>
 * <li><a href="https://github.com/graalvm/sulong/blob/master/docs/PUBLICATIONS.md">Sulong Presentations and Publications</a></li>
 * <li><a href="https://github.com/graalvm/graal-core/blob/master/docs/Publications.md#graal-papers">Graal Publications</a></li>
 * <li><a href="https://github.com/graalvm/simplelanguage">SimpleLanguage</a>: the tutorial Truffle language implementation.</li>
 * <li><a href="https://github.com/graalvm/fastr/blob/master/README.md">FastR</a>: an Open Source Truffle implementation of the R language</li>
 * <li><a href="https://github.com/graalvm/truffleruby/blob/master/README.md">TruffleRuby</a>: an Open Source Truffle implementation of the Ruby language</li>
 * <li>Mailing list for developers <a href="mailto:graal-dev@openjdk.java.net">graal-dev@openjdk.java.net</a>
 * <li>{@linkplain com.oracle.truffle.tutorial Other Truffle Tutorials}
 * </ul>

 *
 * </div>
<script src="../doc-files/tutorial.js"></script>
 *
 * @since 0.25
 */
package com.oracle.truffle.tutorial.background;