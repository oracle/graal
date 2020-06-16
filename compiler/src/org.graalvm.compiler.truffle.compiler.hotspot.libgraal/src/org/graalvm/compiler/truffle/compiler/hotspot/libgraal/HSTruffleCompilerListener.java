/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.hotspot.libgraal;

import org.graalvm.libgraal.jni.HSObject;
import java.io.Closeable;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnFailure;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnGraalTierFinished;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnSuccess;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnTruffleTierFinished;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerListenerGen.callOnFailure;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerListenerGen.callOnGraalTierFinished;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerListenerGen.callOnSuccess;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerListenerGen.callOnTruffleTierFinished;
import static org.graalvm.libgraal.jni.JNIUtil.createHSString;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.libgraal.jni.JNILibGraalScope;
import org.graalvm.libgraal.jni.JNI.JNIEnv;
import org.graalvm.libgraal.jni.JNI.JObject;
import org.graalvm.libgraal.jni.JNI.JString;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal;

/**
 * Proxy for a {@link TruffleCompilerListener} object in the HotSpot heap.
 */
final class HSTruffleCompilerListener extends HSObject implements TruffleCompilerListener {

    HSTruffleCompilerListener(JNILibGraalScope<TruffleToLibGraal.Id> scope, JObject handle) {
        super(scope, handle);
    }

    @TruffleFromLibGraal(OnSuccess)
    @Override
    public void onSuccess(CompilableTruffleAST compilable, TruffleInliningPlan inliningPlan, GraphInfo graphInfo, CompilationResultInfo compilationResultInfo) {
        JObject hsCompilable = ((HSCompilableTruffleAST) compilable).getHandle();
        JObject hsInliningPlan = ((HSTruffleInliningPlan) inliningPlan).getHandle();
        JNIEnv env = JNILibGraalScope.env();
        try (LibGraalObjectHandleScope graphInfoScope = LibGraalObjectHandleScope.forObject(graphInfo);
                        LibGraalObjectHandleScope compilationResultInfoScope = LibGraalObjectHandleScope.forObject(compilationResultInfo)) {
            callOnSuccess(env, getHandle(), hsCompilable, hsInliningPlan, graphInfoScope.getHandle(), compilationResultInfoScope.getHandle());
        }
    }

    @TruffleFromLibGraal(OnTruffleTierFinished)
    @Override
    public void onTruffleTierFinished(CompilableTruffleAST compilable, TruffleInliningPlan inliningPlan, GraphInfo graph) {
        JObject hsCompilable = ((HSCompilableTruffleAST) compilable).getHandle();
        JObject hsInliningPlan = ((HSTruffleInliningPlan) inliningPlan).getHandle();
        JNIEnv env = JNILibGraalScope.env();
        try (LibGraalObjectHandleScope graphInfoScope = LibGraalObjectHandleScope.forObject(graph)) {
            callOnTruffleTierFinished(env, getHandle(), hsCompilable, hsInliningPlan, graphInfoScope.getHandle());
        }

    }

    @TruffleFromLibGraal(OnGraalTierFinished)
    @Override
    public void onGraalTierFinished(CompilableTruffleAST compilable, GraphInfo graph) {
        JObject hsCompilable = ((HSCompilableTruffleAST) compilable).getHandle();
        JNIEnv env = JNILibGraalScope.env();
        try (LibGraalObjectHandleScope graphInfoScope = LibGraalObjectHandleScope.forObject(graph)) {
            callOnGraalTierFinished(env, getHandle(), hsCompilable, graphInfoScope.getHandle());
        }
    }

    @TruffleFromLibGraal(OnFailure)
    @Override
    public void onFailure(CompilableTruffleAST compilable, String serializedException, boolean bailout, boolean permanentBailout) {
        JObject hsCompilable = ((HSCompilableTruffleAST) compilable).getHandle();
        JNIEnv env = JNILibGraalScope.env();
        JString hsReason = createHSString(env, serializedException);
        callOnFailure(env, getHandle(), hsCompilable, hsReason, bailout, permanentBailout);
    }

    private static final class LibGraalObjectHandleScope implements Closeable {

        private long handle;

        private LibGraalObjectHandleScope(long handle) {
            this.handle = handle;
        }

        @Override
        public void close() {
            LibGraalObjectHandles.remove(handle);
            handle = 0;
        }

        long getHandle() {
            if (handle == 0) {
                throw new IllegalStateException("Reading handle from a closed scope.");
            }
            return handle;
        }

        static LibGraalObjectHandleScope forObject(Object object) {
            return new LibGraalObjectHandleScope(LibGraalObjectHandles.create(object));
        }
    }
}
