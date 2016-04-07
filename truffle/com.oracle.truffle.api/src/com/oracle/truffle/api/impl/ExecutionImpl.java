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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.source.Source;
import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Objects;

final class ExecutionImpl extends Accessor.ExecSupport {
    //
    // execution
    //

    private static final ThreadLocal<Object> CURRENT_VM = new ThreadLocal<>();
    private static Reference<Object> previousVM = new WeakReference<>(null);
    private static Assumption oneVM = Truffle.getRuntime().createAssumption();

    @Override
    @CompilerDirectives.TruffleBoundary
    public Closeable executionStart(Object vm, int currentDepth, Object[] debuggerHolder, Source s) {
        CompilerAsserts.neverPartOfCompilation("do not call Accessor.executionStart from compiled code");
        Objects.requireNonNull(vm);
        final Object prev = CURRENT_VM.get();
        Accessor.DebugSupport debug = Accessor.debugAccess();
        final Closeable debugClose = debug == null ? null : debug.executionStart(vm, prev == null ? 0 : -1, debuggerHolder, s);
        if (!(vm == previousVM.get())) {
            previousVM = new WeakReference<>(vm);
            oneVM.invalidate();
            oneVM = Truffle.getRuntime().createAssumption();

        }
        CURRENT_VM.set(vm);
        class ContextCloseable implements Closeable {

            @CompilerDirectives.TruffleBoundary
            @Override
            public void close() throws IOException {
                CURRENT_VM.set(prev);
                if (debugClose != null) {
                    debugClose.close();
                }
            }
        }
        return new ContextCloseable();
    }

    static Assumption oneVMAssumption() {
        return oneVM;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <C> C findContext(Class<? extends TruffleLanguage> type) {
        TruffleLanguage.Env env = Accessor.engineAccess().findEnv(CURRENT_VM.get(), type);
        return (C) Accessor.languageAccess().findContext(env);
    }

    @Override
    public Object findVM() {
        return CURRENT_VM.get();
    }

}
