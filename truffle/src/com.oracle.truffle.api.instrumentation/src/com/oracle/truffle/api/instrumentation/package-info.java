/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @see com.oracle.truffle.api.instrumentation.Instrumentable
 * @since 0.8 or older
 */
package com.oracle.truffle.api.instrumentation;
