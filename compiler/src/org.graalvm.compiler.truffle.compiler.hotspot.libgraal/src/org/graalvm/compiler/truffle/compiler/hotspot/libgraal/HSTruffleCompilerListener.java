/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Closeable;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.CreateCompilationResultInfo;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.CreateGraphInfo;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.OnFailure;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.OnGraalTierFinished;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.OnSuccess;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.OnTruffleTierFinished;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerListenerGen.callCreateCompilationResultInfo;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerListenerGen.callCreateGraphInfo;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerListenerGen.callOnFailure;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerListenerGen.callOnGraalTierFinished;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerListenerGen.callOnSuccess;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerListenerGen.callOnTruffleTierFinished;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.createHSString;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JNIEnv;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JObject;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JString;
import org.graalvm.word.WordFactory;

/**
 * Proxy for a {@link TruffleCompilerListener} object in the HotSpot heap.
 */
final class HSTruffleCompilerListener extends HSObject implements TruffleCompilerListener {

    HSTruffleCompilerListener(HotSpotToSVMScope scope, JObject handle) {
        super(scope, handle);
    }

    @SVMToHotSpot(OnSuccess)
    @Override
    public void onSuccess(CompilableTruffleAST compilable, TruffleInliningPlan inliningPlan, GraphInfo graphInfo, CompilationResultInfo compilationResultInfo) {
        JObject hsCompilable = ((HSCompilableTruffleAST) compilable).getHandle();
        JObject hsInliningPlan = ((HSTruffleInliningPlan) inliningPlan).getHandle();
        JNIEnv env = HotSpotToSVMScope.env();
        try (SVMObjectHandleScope hsGraphInfoScope = createHSGraphInfo(graphInfo)) {
            try (SVMObjectHandleScope hsCompilationResultInfoScope = createHSCompilationResultInfo(compilationResultInfo)) {
                callOnSuccess(env, getHandle(), hsCompilable, hsInliningPlan, hsGraphInfoScope.jobject, hsCompilationResultInfoScope.jobject);
            }
        }
    }

    @SVMToHotSpot(OnTruffleTierFinished)
    @Override
    public void onTruffleTierFinished(CompilableTruffleAST compilable, TruffleInliningPlan inliningPlan, GraphInfo graph) {
        JObject hsCompilable = ((HSCompilableTruffleAST) compilable).getHandle();
        JObject hsInliningPlan = ((HSTruffleInliningPlan) inliningPlan).getHandle();
        JNIEnv env = HotSpotToSVMScope.env();
        try (SVMObjectHandleScope hsGraphInfoScope = createHSGraphInfo(graph)) {
            callOnTruffleTierFinished(env, getHandle(), hsCompilable, hsInliningPlan, hsGraphInfoScope.jobject);
        }

    }

    @SVMToHotSpot(OnGraalTierFinished)
    @Override
    public void onGraalTierFinished(CompilableTruffleAST compilable, GraphInfo graph) {
        JObject hsCompilable = ((HSCompilableTruffleAST) compilable).getHandle();
        JNIEnv env = HotSpotToSVMScope.env();
        try (SVMObjectHandleScope hsGraphInfoScope = createHSGraphInfo(graph)) {
            callOnGraalTierFinished(env, getHandle(), hsCompilable, hsGraphInfoScope.jobject);
        }
    }

    @SVMToHotSpot(OnFailure)
    @Override
    public void onFailure(CompilableTruffleAST compilable, String reason, boolean bailout, boolean permanentBailout) {
        JObject hsCompilable = ((HSCompilableTruffleAST) compilable).getHandle();
        JNIEnv env = HotSpotToSVMScope.env();
        JString hsReason = createHSString(env, reason);
        callOnFailure(env, getHandle(), hsCompilable, hsReason, bailout, permanentBailout);
    }

    @SVMToHotSpot(CreateGraphInfo)
    private static SVMObjectHandleScope createHSGraphInfo(GraphInfo graphInfo) {
        if (graphInfo == null) {
            return SVMObjectHandleScope.create(WordFactory.nullPointer(), 0);
        }
        long handle = SVMObjectHandles.create(graphInfo);
        boolean success = false;
        try {
            JNIEnv env = HotSpotToSVMScope.env();
            JObject instance = callCreateGraphInfo(env, handle);
            success = true;
            return SVMObjectHandleScope.create(instance, handle);
        } finally {
            if (!success) {
                SVMObjectHandles.remove(handle);
            }
        }
    }

    @SVMToHotSpot(CreateCompilationResultInfo)
    private static SVMObjectHandleScope createHSCompilationResultInfo(CompilationResultInfo compilationResultInfo) {
        if (compilationResultInfo == null) {
            return SVMObjectHandleScope.create(WordFactory.nullPointer(), 0);
        }
        long handle = SVMObjectHandles.create(compilationResultInfo);
        boolean success = false;
        try {
            JNIEnv env = HotSpotToSVMScope.env();
            JObject instance = callCreateCompilationResultInfo(env, handle);
            success = true;
            return SVMObjectHandleScope.create(instance, handle);
        } finally {
            if (!success) {
                SVMObjectHandles.remove(handle);
            }
        }
    }

    private static final class SVMObjectHandleScope implements Closeable {

        final JObject jobject;
        private final long handle;

        private SVMObjectHandleScope(JObject jobject, long handle) {
            this.jobject = jobject;
            this.handle = handle;
        }

        @Override
        public void close() {
            if (jobject.isNonNull()) {
                SVMObjectHandles.remove(handle);
            }
        }

        static SVMObjectHandleScope create(JObject jobject, long handle) {
            return new SVMObjectHandleScope(jobject, handle);
        }
    }
}
