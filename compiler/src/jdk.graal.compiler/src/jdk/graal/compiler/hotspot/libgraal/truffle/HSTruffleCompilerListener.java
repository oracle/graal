/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.libgraal.truffle;

import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleCompilerListener;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id;

import java.lang.invoke.MethodHandle;
import java.util.function.Supplier;

import static jdk.graal.compiler.hotspot.libgraal.truffle.BuildTime.getHostMethodHandleOrFail;

final class HSTruffleCompilerListener extends HSIndirectHandle implements TruffleCompilerListener {

    private static final Handles HANDLES = new Handles();

    HSTruffleCompilerListener(Object hsHandle) {
        super(hsHandle);
    }

    @Override
    public void onSuccess(TruffleCompilable compilable, TruffleCompilationTask task, GraphInfo graphInfo, CompilationResultInfo compilationResultInfo, int tier) {
        Object hsCompilable = ((HSTruffleCompilable) compilable).hsHandle;
        Object hsTask = ((HSTruffleCompilationTask) task).hsHandle;
        try {
            HANDLES.onSuccess.invoke(hsHandle, hsCompilable, hsTask, graphInfo, compilationResultInfo, tier);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public void onTruffleTierFinished(TruffleCompilable compilable, TruffleCompilationTask task, GraphInfo graph) {
        Object hsCompilable = ((HSTruffleCompilable) compilable).hsHandle;
        Object hsTask = ((HSTruffleCompilationTask) task).hsHandle;
        try {
            HANDLES.onTruffleTierFinished.invoke(hsHandle, hsCompilable, hsTask, graph);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public void onGraalTierFinished(TruffleCompilable compilable, GraphInfo graph) {
        Object hsCompilable = ((HSTruffleCompilable) compilable).hsHandle;
        try {
            HANDLES.onGraalTierFinished.invoke(hsHandle, hsCompilable, graph);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public void onFailure(TruffleCompilable compilable, String reason, boolean bailout, boolean permanentBailout, int tier, Supplier<String> lazyStackTrace) {
        Object hsCompilable = ((HSTruffleCompilable) compilable).hsHandle;
        try {
            HANDLES.onFailure.invoke(hsHandle, hsCompilable, reason, bailout, permanentBailout, tier, lazyStackTrace);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public void onCompilationRetry(TruffleCompilable compilable, TruffleCompilationTask task) {
        Object hsCompilable = ((HSTruffleCompilable) compilable).hsHandle;
        Object hsTask = ((HSTruffleCompilationTask) task).hsHandle;
        try {
            HANDLES.onCompilationRetry.invoke(hsHandle, hsCompilable, hsTask);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    private static final class Handles {
        final MethodHandle onSuccess = getHostMethodHandleOrFail(Id.OnSuccess);
        final MethodHandle onTruffleTierFinished = getHostMethodHandleOrFail(Id.OnTruffleTierFinished);
        final MethodHandle onGraalTierFinished = getHostMethodHandleOrFail(Id.OnGraalTierFinished);
        final MethodHandle onFailure = getHostMethodHandleOrFail(Id.OnFailure);
        final MethodHandle onCompilationRetry = getHostMethodHandleOrFail(Id.OnCompilationRetry);
    }
}
