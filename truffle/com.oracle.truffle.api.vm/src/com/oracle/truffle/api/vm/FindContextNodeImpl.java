/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.impl.FindContextNode;

final class FindContextNodeImpl<L> extends FindContextNode {
    private static final Object UNINITIALIZED = new Object();
    private static final Object MULTIPLE = new Object();
    private final TruffleLanguage<L> language;
    @Child private PolyglotEngine.FindContextForEngineNode<L> fromEngine;
    @CompilerDirectives.CompilationFinal private Object singleEngine = UNINITIALIZED;
    @CompilerDirectives.CompilationFinal private L singleContext;

    private FindContextNodeImpl(TruffleLanguage<L> language) {
        super(PolyglotEngine.FIND_ENGINE_NODE);
        this.fromEngine = new PolyglotEngine.FindContextForEngineNode<>(language);
        this.language = language;
    }

    static <C> FindContextNodeImpl<C> create(TruffleLanguage<C> language) {
        return new FindContextNodeImpl<>(language);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    protected <C> C findContextForEngine(Object engine, TruffleLanguage<C> forLanguage) {
        if (this.language != forLanguage) {
            throw new ClassCastException();
        }
        return (C) findContext(engine);
    }

    private L findContext(Object rawEngine) {
        if (singleEngine != MULTIPLE) {
            if (singleEngine == rawEngine) {
                return singleContext;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (singleEngine == UNINITIALIZED) {
                    singleEngine = rawEngine;
                    singleContext = fromEngine.executeFindContext(rawEngine);
                    return singleContext;
                } else {
                    singleEngine = MULTIPLE;
                    singleContext = null;
                }
            }
        }
        return fromEngine.executeFindContext(rawEngine);
    }
}
