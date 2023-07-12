/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.libgraal.truffle;

import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilationTaskGen.callAddInlinedTarget;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilationTaskGen.callAddTargetToDequeue;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilationTaskGen.callGetDebugProperties;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilationTaskGen.callGetDescription;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilationTaskGen.callGetLanguage;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilationTaskGen.callGetLineNumber;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilationTaskGen.callGetNodeClassName;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilationTaskGen.callGetNodeId;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilationTaskGen.callGetOffsetEnd;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilationTaskGen.callGetOffsetStart;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilationTaskGen.callGetPosition;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilationTaskGen.callGetURI;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilationTaskGen.callHasNextTier;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilationTaskGen.callIsCancelled;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilationTaskGen.callIsLastTier;
import static com.oracle.svm.graal.hotspot.libgraal.truffle.HSTruffleCompilationTaskGen.callSetCallCounts;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.AddInlinedTarget;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.AddTargetToDequeue;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetDescription;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetLanguage;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetLineNumber;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetNodeClassName;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetNodeId;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetOffsetEnd;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetOffsetStart;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetPosition;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetURI;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.HasNextTier;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsCancelled;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsLastTier;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.SetCallCounts;
import static org.graalvm.jniutils.JNIMethodScope.env;
import static org.graalvm.jniutils.JNIUtil.createString;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.jniutils.HSObject;
import org.graalvm.jniutils.JNI;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNI.JString;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.nativebridge.BinaryInput;

import com.oracle.svm.graal.hotspot.libgraal.LibGraal;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleSourceLanguagePosition;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Proxy for a {@code Supplier<Boolean>} object in the HotSpot heap.
 */
final class HSTruffleCompilationTask extends HSObject implements TruffleCompilationTask {

    private final TruffleFromLibGraalCalls calls;

    HSTruffleCompilationTask(JNIMethodScope scope, JObject handle, HSTruffleCompilerRuntime runtime) {
        super(scope, handle);
        this.calls = runtime.calls;
    }

    @TruffleFromLibGraal(IsCancelled)
    @Override
    public boolean isCancelled() {
        return callIsCancelled(calls, env(), getHandle());
    }

    @TruffleFromLibGraal(HasNextTier)
    @Override
    public boolean hasNextTier() {
        return callHasNextTier(calls, env(), getHandle());
    }

    @TruffleFromLibGraal(IsLastTier)
    @Override
    public boolean isLastTier() {
        return callIsLastTier(calls, env(), getHandle());
    }

    @TruffleFromLibGraal(GetPosition)
    @Override
    public TruffleSourceLanguagePosition getPosition(JavaConstant node) {
        long nodeHandle = LibGraal.translate(node);
        JObject res = callGetPosition(calls, env(), getHandle(), nodeHandle);
        if (res.isNull()) {
            return null;
        }
        return new HSTruffleSourceLanguagePosition(JNIMethodScope.scope(), res, calls);
    }

    @TruffleFromLibGraal(AddTargetToDequeue)
    @Override
    public void addTargetToDequeue(TruffleCompilable target) {
        JObject hsCompilable = ((HSTruffleCompilable) target).getHandle();
        callAddTargetToDequeue(calls, env(), getHandle(), hsCompilable);
    }

    @TruffleFromLibGraal(SetCallCounts)
    @Override
    public void setCallCounts(int total, int inlined) {
        callSetCallCounts(calls, env(), getHandle(), total, inlined);
    }

    @TruffleFromLibGraal(AddInlinedTarget)
    @Override
    public void addInlinedTarget(TruffleCompilable target) {
        JObject hsCompilable = ((HSTruffleCompilable) target).getHandle();
        callAddInlinedTarget(calls, env(), getHandle(), hsCompilable);
    }

    @TruffleFromLibGraal(Id.GetDebugProperties)
    @Override
    public Map<String, Object> getDebugProperties(JavaConstant node) {
        long nodeHandle = LibGraal.translate(node);
        JNIEnv env = env();
        JNI.JByteArray res = callGetDebugProperties(calls, env, getHandle(), nodeHandle);
        byte[] realArray = JNIUtil.createArray(env, res);
        return readDebugMap(BinaryInput.create(realArray));
    }

    private static Map<String, Object> readDebugMap(BinaryInput in) {
        int size = in.readInt();
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            String key = in.readUTF();
            Object value = in.readTypedValue();
            map.put(key, value);
        }
        return map;
    }

    /**
     * Proxy for a {@link TruffleSourceLanguagePosition} object in the HotSpot heap.
     */
    private static final class HSTruffleSourceLanguagePosition extends HSObject implements TruffleSourceLanguagePosition {

        private final TruffleFromLibGraalCalls calls;

        HSTruffleSourceLanguagePosition(JNIMethodScope scope, JObject handle, TruffleFromLibGraalCalls calls) {
            super(scope, handle);
            this.calls = calls;
        }

        @TruffleFromLibGraal(GetOffsetStart)
        @Override
        public int getOffsetStart() {
            return callGetOffsetStart(calls, JNIMethodScope.env(), getHandle());
        }

        @TruffleFromLibGraal(GetOffsetEnd)
        @Override
        public int getOffsetEnd() {
            return callGetOffsetEnd(calls, JNIMethodScope.env(), getHandle());
        }

        @TruffleFromLibGraal(GetLineNumber)
        @Override
        public int getLineNumber() {
            return callGetLineNumber(calls, JNIMethodScope.env(), getHandle());
        }

        @TruffleFromLibGraal(GetLanguage)
        @Override
        public String getLanguage() {
            JString res = callGetLanguage(calls, JNIMethodScope.env(), getHandle());
            return createString(JNIMethodScope.env(), res);
        }

        @TruffleFromLibGraal(GetDescription)
        @Override
        public String getDescription() {
            JString res = callGetDescription(calls, JNIMethodScope.env(), getHandle());
            return createString(JNIMethodScope.env(), res);
        }

        @TruffleFromLibGraal(GetURI)
        @Override
        public URI getURI() {
            JString res = callGetURI(calls, JNIMethodScope.env(), getHandle());
            String stringifiedURI = createString(JNIMethodScope.env(), res);
            return stringifiedURI == null ? null : URI.create(stringifiedURI);
        }

        @TruffleFromLibGraal(GetNodeClassName)
        @Override
        public String getNodeClassName() {
            JString res = callGetNodeClassName(calls, JNIMethodScope.env(), getHandle());
            return createString(JNIMethodScope.env(), res);
        }

        @TruffleFromLibGraal(GetNodeId)
        @Override
        public int getNodeId() {
            return callGetNodeId(calls, JNIMethodScope.env(), getHandle());
        }
    }
}
