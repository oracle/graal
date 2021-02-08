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

import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.nativeimage.c.function.CEntryPoint;

import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.graal.isolated.ClientHandle;
import com.oracle.svm.graal.isolated.ClientIsolateThread;
import com.oracle.svm.graal.isolated.IsolatedCompileClient;
import com.oracle.svm.graal.isolated.IsolatedCompileContext;
import com.oracle.svm.graal.isolated.IsolatedObjectProxy;

final class IsolatedTruffleCompilationTask extends IsolatedObjectProxy<TruffleCompilationTask> implements TruffleCompilationTask {

    IsolatedTruffleCompilationTask(ClientHandle<TruffleCompilationTask> handle) {
        super(handle);
    }

    @Override
    public boolean isCancelled() {
        return isCancelled0(IsolatedCompileContext.get().getClient(), handle);
    }

    @Override
    public boolean isLastTier() {
        return isLastTier0(IsolatedCompileContext.get().getClient(), handle);
    }

    @Override
    public boolean isFirstTier() {
        return isFirstTier0(IsolatedCompileContext.get().getClient(), handle);
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static boolean isCancelled0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<TruffleCompilationTask> taskHandle) {
        return IsolatedCompileClient.get().unhand(taskHandle).isCancelled();
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static boolean isLastTier0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<TruffleCompilationTask> taskHandle) {
        return IsolatedCompileClient.get().unhand(taskHandle).isLastTier();
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static boolean isFirstTier0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<TruffleCompilationTask> taskHandle) {
        return IsolatedCompileClient.get().unhand(taskHandle).isFirstTier();
    }
}
