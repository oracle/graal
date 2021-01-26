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

import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.AddInlinedTarget;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.AddTargetToDequeue;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.FindCallNode;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetDescription;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetLanguage;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetLineNumber;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetNodeClassName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetNodeId;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetOffsetEnd;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetOffsetStart;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetPosition;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetURI;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.SetCallCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.SetInlinedCallCount;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callAddInlinedTarget;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callAddTargetToDequeue;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callFindCallNode;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callGetDescription;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callGetLanguage;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callGetLineNumber;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callGetNodeClassName;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callGetNodeId;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callGetOffsetEnd;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callGetOffsetStart;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callGetPosition;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callGetURI;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callSetCallCount;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callSetInlinedCallCount;
import static org.graalvm.libgraal.jni.JNIUtil.createString;

import java.net.URI;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCallNode;
import org.graalvm.compiler.truffle.common.TruffleMetaAccessProvider;
import org.graalvm.compiler.truffle.common.TruffleSourceLanguagePosition;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal;
import org.graalvm.libgraal.LibGraal;
import org.graalvm.libgraal.jni.HSObject;
import org.graalvm.libgraal.jni.JNI.JNIEnv;
import org.graalvm.libgraal.jni.JNI.JObject;
import org.graalvm.libgraal.jni.JNI.JString;
import org.graalvm.libgraal.jni.JNILibGraalScope;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Proxy for a {@link TruffleMetaAccessProvider} object in the HotSpot heap.
 */
class HSTruffleInliningPlan extends HSObject implements TruffleMetaAccessProvider {

    final JNILibGraalScope<TruffleToLibGraal.Id> scope;

    HSTruffleInliningPlan(JNILibGraalScope<TruffleToLibGraal.Id> scope, JObject handle) {
        super(scope, handle);
        this.scope = scope;
    }

    @TruffleFromLibGraal(FindCallNode)
    @Override
    public TruffleCallNode findCallNode(JavaConstant callNode) {
        long nodeHandle = LibGraal.translate(callNode);
        JNIEnv env = scope.getEnv();
        JObject res = callFindCallNode(env, getHandle(), nodeHandle);
        if (res.isNull()) {
            return null;
        }
        return new HSTruffleCallNode(scope, res);
    }

    @TruffleFromLibGraal(GetPosition)
    @Override
    public TruffleSourceLanguagePosition getPosition(JavaConstant node) {
        long nodeHandle = LibGraal.translate(node);
        JNIEnv env = scope.getEnv();
        JObject res = callGetPosition(env, getHandle(), nodeHandle);
        if (res.isNull()) {
            return null;
        }
        return new HSTruffleSourceLanguagePosition(scope, res);
    }

    @TruffleFromLibGraal(AddTargetToDequeue)
    @Override
    public void addTargetToDequeue(CompilableTruffleAST target) {
        JObject hsCompilable = ((HSCompilableTruffleAST) target).getHandle();
        JNIEnv env = scope.getEnv();
        callAddTargetToDequeue(env, getHandle(), hsCompilable);
    }

    @TruffleFromLibGraal(SetInlinedCallCount)
    @Override
    public void setInlinedCallCount(int count) {
        callSetInlinedCallCount(scope.getEnv(), getHandle(), count);
    }

    @TruffleFromLibGraal(SetCallCount)
    @Override
    public void setCallCount(int count) {
        callSetCallCount(scope.getEnv(), getHandle(), count);
    }

    @TruffleFromLibGraal(AddInlinedTarget)
    @Override
    public void addInlinedTarget(CompilableTruffleAST target) {
        JObject hsCompilable = ((HSCompilableTruffleAST) target).getHandle();
        callAddInlinedTarget(scope.getEnv(), getHandle(), hsCompilable);
    }

    /**
     * Proxy for a {@link TruffleSourceLanguagePosition} object in the HotSpot heap.
     */
    private static final class HSTruffleSourceLanguagePosition extends HSObject implements TruffleSourceLanguagePosition {

        HSTruffleSourceLanguagePosition(JNILibGraalScope<TruffleToLibGraal.Id> scope, JObject handle) {
            super(scope, handle);
        }

        @TruffleFromLibGraal(GetOffsetStart)
        @Override
        public int getOffsetStart() {
            return callGetOffsetStart(JNILibGraalScope.env(), getHandle());
        }

        @TruffleFromLibGraal(GetOffsetEnd)
        @Override
        public int getOffsetEnd() {
            return callGetOffsetEnd(JNILibGraalScope.env(), getHandle());
        }

        @TruffleFromLibGraal(GetLineNumber)
        @Override
        public int getLineNumber() {
            return callGetLineNumber(JNILibGraalScope.env(), getHandle());
        }

        @TruffleFromLibGraal(GetLanguage)
        @Override
        public String getLanguage() {
            JString res = callGetLanguage(JNILibGraalScope.env(), getHandle());
            return createString(JNILibGraalScope.env(), res);
        }

        @TruffleFromLibGraal(GetDescription)
        @Override
        public String getDescription() {
            JString res = callGetDescription(JNILibGraalScope.env(), getHandle());
            return createString(JNILibGraalScope.env(), res);
        }

        @TruffleFromLibGraal(GetURI)
        @Override
        public URI getURI() {
            JString res = callGetURI(JNILibGraalScope.env(), getHandle());
            String stringifiedURI = createString(JNILibGraalScope.env(), res);
            return stringifiedURI == null ? null : URI.create(stringifiedURI);
        }

        @TruffleFromLibGraal(GetNodeClassName)
        @Override
        public String getNodeClassName() {
            JString res = callGetNodeClassName(JNILibGraalScope.env(), getHandle());
            return createString(JNILibGraalScope.env(), res);
        }

        @TruffleFromLibGraal(GetNodeId)
        @Override
        public int getNodeId() {
            return callGetNodeId(JNILibGraalScope.env(), getHandle());
        }
    }
}
