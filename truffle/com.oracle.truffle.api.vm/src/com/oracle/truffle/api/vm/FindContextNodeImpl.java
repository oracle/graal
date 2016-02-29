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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.impl.FindContextNode;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

final class FindContextNodeImpl<L> extends FindContextNode {
    private static Reference<Object> previousVM = new WeakReference<>(null);
    private static Assumption oneGlobalVM = Truffle.getRuntime().createAssumption();

    static void usingVM(PolyglotEngine polyglotEngine) {
        if (polyglotEngine != previousVM.get()) {
            oneGlobalVM.invalidate();
        }
    }

    private final TruffleLanguage<L> language;
    @CompilerDirectives.CompilationFinal private L context;
    @CompilerDirectives.CompilationFinal private Assumption oneVM;

    public FindContextNodeImpl(TruffleLanguage<L> language) {
        this.language = language;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <C> C executeFindContext(TruffleLanguage<C> language) {
        if (this.language != language) {
            throw new ClassCastException();
        }
        return (C) findContext();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private L findContext() {
        if (context != null && oneVM.isValid()) {
            return context;
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        oneVM = oneGlobalVM;
        TruffleLanguage.Env env = PolyglotEngine.findEnv(language);
        return context = (L) PolyglotEngine.findContext(env);
    }
}
