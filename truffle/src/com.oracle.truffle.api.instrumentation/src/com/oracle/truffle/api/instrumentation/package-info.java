/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * The instrumentation API provides a way to introspect and inject behavior into interpreters
 * written using the Truffle framework.
 *
 * To adopt instrumentation support for a guest language implementation you need to subclass syntax
 * nodes of your language with {@link com.oracle.truffle.api.instrumentation.InstrumentableNode}.
 * For details please refer to {@link com.oracle.truffle.api.instrumentation.InstrumentableNode}.
 *
 * To use the instrumentation framework implementors must implement the
 * {@link com.oracle.truffle.api.instrumentation.TruffleInstrument} interface. Please refer to
 * {@link com.oracle.truffle.api.instrumentation.TruffleInstrument} for further details.
 *
 * Guest languages that want to use the capabilities of the instrumentation framework can access
 * {@link com.oracle.truffle.api.instrumentation.Instrumenter} for their
 * {@link com.oracle.truffle.api.TruffleLanguage} by calling
 * {@link com.oracle.truffle.api.TruffleLanguage.Env#lookup(Class)}.
 * {@link com.oracle.truffle.api.instrumentation.SourceSectionFilter} created using guest languages
 * may be used to implement guest language features that require meta-programming capabilities.
 *
 * @see com.oracle.truffle.api.instrumentation.TruffleInstrument
 * @see com.oracle.truffle.api.instrumentation.InstrumentableNode
 * @since 0.8 or older
 */
package com.oracle.truffle.api.instrumentation;
