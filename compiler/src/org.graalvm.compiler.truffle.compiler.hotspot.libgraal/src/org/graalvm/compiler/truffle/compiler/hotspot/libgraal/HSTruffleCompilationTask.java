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
package org.graalvm.compiler.truffle.compiler.hotspot.libgraal;

import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.AddInlinedTarget;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.AddTargetToDequeue;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetDescription;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetLanguage;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetLineNumber;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetNodeClassName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetNodeId;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetOffsetEnd;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetOffsetStart;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetPosition;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetURI;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.HasNextTier;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsCancelled;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsLastTier;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.SetCallCounts;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilationTaskGen.callAddInlinedTarget;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilationTaskGen.callAddTargetToDequeue;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilationTaskGen.callGetDebugProperties;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilationTaskGen.callGetDescription;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilationTaskGen.callGetLanguage;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilationTaskGen.callGetLineNumber;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilationTaskGen.callGetNodeClassName;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilationTaskGen.callGetNodeId;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilationTaskGen.callGetOffsetEnd;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilationTaskGen.callGetOffsetStart;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilationTaskGen.callGetPosition;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilationTaskGen.callGetURI;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilationTaskGen.callHasNextTier;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilationTaskGen.callIsCancelled;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilationTaskGen.callIsLastTier;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilationTaskGen.callSetCallCounts;
import static org.graalvm.jniutils.JNIMethodScope.env;
import static org.graalvm.jniutils.JNIUtil.createString;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleSourceLanguagePosition;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id;
import org.graalvm.jniutils.HSObject;
import org.graalvm.jniutils.JNI;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNI.JString;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.libgraal.LibGraal;
import org.graalvm.nativebridge.BinaryInput;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Proxy for a {@code Supplier<Boolean>} object in the HotSpot heap.
 */
final class HSTruffleCompilationTask extends HSObject implements TruffleCompilationTask {

    HSTruffleCompilationTask(JNIMethodScope scope, JObject handle) {
        super(scope, handle);
    }

    @TruffleFromLibGraal(IsCancelled)
    @Override
    public boolean isCancelled() {
        return callIsCancelled(env(), getHandle());
    }

    @TruffleFromLibGraal(HasNextTier)
    @Override
    public boolean hasNextTier() {
        return callHasNextTier(env(), getHandle());
    }

    @TruffleFromLibGraal(IsLastTier)
    @Override
    public boolean isLastTier() {
        return callIsLastTier(env(), getHandle());
    }

    @TruffleFromLibGraal(GetPosition)
    @Override
    public TruffleSourceLanguagePosition getPosition(JavaConstant node) {
        long nodeHandle = LibGraal.translate(node);
        JObject res = callGetPosition(env(), getHandle(), nodeHandle);
        if (res.isNull()) {
            return null;
        }
        return new HSTruffleSourceLanguagePosition(JNIMethodScope.scope(), res);
    }

    @TruffleFromLibGraal(AddTargetToDequeue)
    @Override
    public void addTargetToDequeue(CompilableTruffleAST target) {
        JObject hsCompilable = ((HSCompilableTruffleAST) target).getHandle();
        callAddTargetToDequeue(env(), getHandle(), hsCompilable);
    }

    @TruffleFromLibGraal(SetCallCounts)
    @Override
    public void setCallCounts(int total, int inlined) {
        callSetCallCounts(env(), getHandle(), total, inlined);
    }

    @TruffleFromLibGraal(AddInlinedTarget)
    @Override
    public void addInlinedTarget(CompilableTruffleAST target) {
        JObject hsCompilable = ((HSCompilableTruffleAST) target).getHandle();
        callAddInlinedTarget(env(), getHandle(), hsCompilable);
    }

    @TruffleFromLibGraal(Id.GetDebugProperties)
    @Override
    public Map<String, Object> getDebugProperties(JavaConstant node) {
        long nodeHandle = LibGraal.translate(node);
        JNIEnv env = env();
        JNI.JByteArray res = callGetDebugProperties(env, getHandle(), nodeHandle);
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

        HSTruffleSourceLanguagePosition(JNIMethodScope scope, JObject handle) {
            super(scope, handle);
        }

        @TruffleFromLibGraal(GetOffsetStart)
        @Override
        public int getOffsetStart() {
            return callGetOffsetStart(JNIMethodScope.env(), getHandle());
        }

        @TruffleFromLibGraal(GetOffsetEnd)
        @Override
        public int getOffsetEnd() {
            return callGetOffsetEnd(JNIMethodScope.env(), getHandle());
        }

        @TruffleFromLibGraal(GetLineNumber)
        @Override
        public int getLineNumber() {
            return callGetLineNumber(JNIMethodScope.env(), getHandle());
        }

        @TruffleFromLibGraal(GetLanguage)
        @Override
        public String getLanguage() {
            JString res = callGetLanguage(JNIMethodScope.env(), getHandle());
            return createString(JNIMethodScope.env(), res);
        }

        @TruffleFromLibGraal(GetDescription)
        @Override
        public String getDescription() {
            JString res = callGetDescription(JNIMethodScope.env(), getHandle());
            return createString(JNIMethodScope.env(), res);
        }

        @TruffleFromLibGraal(GetURI)
        @Override
        public URI getURI() {
            JString res = callGetURI(JNIMethodScope.env(), getHandle());
            String stringifiedURI = createString(JNIMethodScope.env(), res);
            return stringifiedURI == null ? null : URI.create(stringifiedURI);
        }

        @TruffleFromLibGraal(GetNodeClassName)
        @Override
        public String getNodeClassName() {
            JString res = callGetNodeClassName(JNIMethodScope.env(), getHandle());
            return createString(JNIMethodScope.env(), res);
        }

        @TruffleFromLibGraal(GetNodeId)
        @Override
        public int getNodeId() {
            return callGetNodeId(JNIMethodScope.env(), getHandle());
        }
    }
}
