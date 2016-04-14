/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import com.oracle.truffle.api.TruffleLanguage;
import java.util.Objects;

final class ExecutionImpl extends Accessor.ExecSupport {
    //
    // execution
    //

    private static final ContextStoreProfile CURRENT_VM = new ContextStoreProfile(null);

    static ContextStoreProfile sharedProfile() {
        return CURRENT_VM;
    }

    @Override
    public ContextStore createStore(Object vm) {
        return new ContextStore(vm, 4);
    }

    @Override
    public ContextStore executionStarted(ContextStore context) {
        Object vm = context.vm;
        Objects.requireNonNull(vm);
        final ContextStore prev = CURRENT_VM.get();
        CURRENT_VM.enter(context);
        return prev;
    }

    @Override
    public void executionEnded(ContextStore prev) {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <C> C findContext(Object currentVM, Class<? extends TruffleLanguage> type) {
        TruffleLanguage.Env env = Accessor.engineAccess().findEnv(currentVM, type);
        return (C) Accessor.languageAccess().findContext(env);
    }

    @Override
    public Object findVM() {
        return currentVM();
    }

    private static Object currentVM() {
        ContextStore current = CURRENT_VM.get();
        return current == null ? null : current.vm;
    }

}
