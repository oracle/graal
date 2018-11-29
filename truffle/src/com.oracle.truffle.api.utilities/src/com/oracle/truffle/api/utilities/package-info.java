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
 * Various utility classes that aim to make the development of TruffleLanguage guest languages
 * easier.
 *
 * <p>
 * We try to capture common language patterns in this package. Here is an overview of patterns that
 * we have captured so far:
 * <ul>
 * <li>{@link com.oracle.truffle.api.utilities.AssumedValue} speculates on global values to remain
 * unchanged.</li>
 * <li>{@link com.oracle.truffle.api.utilities.CyclicAssumption} speculates on assumptions that
 * might invalidate more often but are assumed to stabilize over time.</li>
 * <li>{@link com.oracle.truffle.api.utilities.NeverValidAssumption} utility for assumptions that
 * are always invalidated</li>
 * <li>{@link com.oracle.truffle.api.utilities.UnionAssumption} utility for the union of two
 * assumptions</li>
 * </ul>
 * </p>
 *
 * @since 0.8 or older
 */
package com.oracle.truffle.api.utilities;
