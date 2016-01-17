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
package com.oracle.truffle.api.instrumentation;

import com.oracle.truffle.api.TruffleLanguage;

/**
 * <p>
 * {@link TruffleLanguage} implementations can decide to implement this additional interface to
 * register {@link EventBinding bindings} specifically for this language. This can be useful to
 * implement core language features using the instrumentation API. Instrumentations created by an
 * {@link InstrumentationLanguage} have elevated rights in the system. Exceptions thrown by
 * {@link EventBinding bindings} that were created using the {@link Instrumenter} passed in
 * {@link #installInstrumentations(Object, Instrumenter)} are directly passed on to the guest
 * language AST.
 * </p>
 *
 * Bindings created by the guest language are also automatically disposed together with the
 * language.
 */
public interface InstrumentationLanguage<C> {

    /**
     * Invoked for each allocated context on guest language startup. Bindings attached to the
     * instrumenter apply only for the given context and guest language.
     *
     * @param context the context of the language that
     * @param instrumenter
     *
     * @see Instrumenter
     */
    void installInstrumentations(C context, Instrumenter instrumenter);

}
