/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.isolated;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.compiler.TruffleCompilationIdentifier;
import org.graalvm.nativeimage.c.function.CEntryPoint;

import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.graal.isolated.ClientHandle;
import com.oracle.svm.graal.isolated.ClientIsolateThread;
import com.oracle.svm.graal.isolated.CompilerHandle;
import com.oracle.svm.graal.isolated.IsolatedCompileClient;
import com.oracle.svm.graal.isolated.IsolatedCompileContext;
import com.oracle.svm.graal.isolated.IsolatedObjectProxy;

final class IsolatedCompilationIdentifier extends IsolatedObjectProxy<TruffleCompilationIdentifier> implements TruffleCompilationIdentifier {
    private static final Verbosity[] VERBOSITIES = Verbosity.values();

    private final IsolatedCompilableTruffleAST compilable;
    private final String[] descriptions = new String[VERBOSITIES.length];

    IsolatedCompilationIdentifier(ClientHandle<TruffleCompilationIdentifier> handle, IsolatedCompilableTruffleAST compilable) {
        super(handle);
        this.compilable = compilable;
    }

    @Override
    public CompilableTruffleAST getCompilable() {
        return compilable;
    }

    @Override
    public void close() {
        close0(IsolatedCompileContext.get().getClient(), handle);
    }

    @Override
    public String toString() {
        return toString(Verbosity.DETAILED);
    }

    @Override
    public String toString(Verbosity verbosity) {
        int ordinal = verbosity.ordinal();
        if (descriptions[ordinal] == null) {
            CompilerHandle<String> h = toString0(IsolatedCompileContext.get().getClient(), handle, ordinal);
            descriptions[ordinal] = IsolatedCompileContext.get().unhand(h);
        }
        return descriptions[ordinal];
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(publishAs = CEntryPointOptions.Publish.NotPublished)
    private static void close0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<TruffleCompilationIdentifier> compilationHandle) {
        IsolatedCompileClient.get().unhand(compilationHandle).close();
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(publishAs = CEntryPointOptions.Publish.NotPublished)
    private static CompilerHandle<String> toString0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<TruffleCompilationIdentifier> idHandle, int verbosityOrdinal) {
        TruffleCompilationIdentifier id = IsolatedCompileClient.get().unhand(idHandle);
        String description = id.toString(VERBOSITIES[verbosityOrdinal]);
        return IsolatedCompileClient.get().createStringInCompiler(description);
    }
}
