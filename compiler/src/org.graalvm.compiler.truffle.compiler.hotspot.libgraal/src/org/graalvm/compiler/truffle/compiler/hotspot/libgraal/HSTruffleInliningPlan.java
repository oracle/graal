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

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.FindDecision;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetDescription;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetLanguage;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetLineNumber;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetNodeRewritingAssumption;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetOffsetEnd;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetOffsetStart;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetPosition;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetTargetName;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetURI;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.IsTargetStable;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.ShouldInline;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callFindDecision;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callGetDescription;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callGetLanguage;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callGetLineNumber;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callGetNodeRewritingAssumption;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callGetOffsetEnd;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callGetOffsetStart;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callGetPosition;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callGetTargetName;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callGetURI;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callIsTargetStable;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleInliningPlanGen.callShouldInline;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.createString;

import java.net.URI;

import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.common.TruffleSourceLanguagePosition;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JNIEnv;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JObject;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JString;
import org.graalvm.libgraal.LibGraal;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Proxy for a {@link TruffleInliningPlan} object in the HotSpot heap.
 */
class HSTruffleInliningPlan extends HSObject implements TruffleInliningPlan {

    final HotSpotToSVMScope scope;

    HSTruffleInliningPlan(HotSpotToSVMScope scope, JObject handle) {
        super(scope, handle);
        this.scope = scope;
    }

    @SVMToHotSpot(FindDecision)
    @Override
    public Decision findDecision(JavaConstant callNode) {
        long callNodeHandle = LibGraal.translate(runtime(), callNode);
        JNIEnv env = scope.getEnv();
        JObject res = callFindDecision(env, getHandle(), callNodeHandle);
        if (res.isNull()) {
            return null;
        }
        return new HSDecision(scope, res);
    }

    @SVMToHotSpot(GetPosition)
    @Override
    public TruffleSourceLanguagePosition getPosition(JavaConstant node) {
        long nodeHandle = LibGraal.translate(runtime(), node);
        JNIEnv env = scope.getEnv();
        JObject res = callGetPosition(env, getHandle(), nodeHandle);
        if (res.isNull()) {
            return null;
        }
        return new HSTruffleSourceLanguagePosition(scope, res);
    }

    /**
     * Proxy for a {@code TruffleInliningPlan.Decision} object in the HotSpot heap.
     */
    private static final class HSDecision extends HSTruffleInliningPlan implements Decision {

        HSDecision(HotSpotToSVMScope scope, JObject handle) {
            super(scope, handle);
        }

        @SVMToHotSpot(ShouldInline)
        @Override
        public boolean shouldInline() {
            return callShouldInline(scope.getEnv(), getHandle());
        }

        @SVMToHotSpot(IsTargetStable)
        @Override
        public boolean isTargetStable() {
            return callIsTargetStable(scope.getEnv(), getHandle());
        }

        @SVMToHotSpot(GetTargetName)
        @Override
        public String getTargetName() {
            JNIEnv env = scope.getEnv();
            JString res = callGetTargetName(env, getHandle());
            return createString(env, res);
        }

        @SVMToHotSpot(GetNodeRewritingAssumption)
        @Override
        public JavaConstant getNodeRewritingAssumption() {
            long javaConstantHandle = callGetNodeRewritingAssumption(scope.getEnv(), getHandle());
            return LibGraal.unhand(runtime(), JavaConstant.class, javaConstantHandle);
        }
    }

    /**
     * Proxy for a {@link TruffleSourceLanguagePosition} object in the HotSpot heap.
     */
    private static final class HSTruffleSourceLanguagePosition extends HSObject implements TruffleSourceLanguagePosition {

        HSTruffleSourceLanguagePosition(HotSpotToSVMScope scope, JObject handle) {
            super(scope, handle);
        }

        @SVMToHotSpot(GetOffsetStart)
        @Override
        public int getOffsetStart() {
            return callGetOffsetStart(HotSpotToSVMScope.env(), getHandle());
        }

        @SVMToHotSpot(GetOffsetEnd)
        @Override
        public int getOffsetEnd() {
            return callGetOffsetEnd(HotSpotToSVMScope.env(), getHandle());
        }

        @SVMToHotSpot(GetLineNumber)
        @Override
        public int getLineNumber() {
            return callGetLineNumber(HotSpotToSVMScope.env(), getHandle());
        }

        @SVMToHotSpot(GetLanguage)
        @Override
        public String getLanguage() {
            JString res = callGetLanguage(HotSpotToSVMScope.env(), getHandle());
            return createString(HotSpotToSVMScope.env(), res);
        }

        @SVMToHotSpot(GetDescription)
        @Override
        public String getDescription() {
            JString res = callGetDescription(HotSpotToSVMScope.env(), getHandle());
            return createString(HotSpotToSVMScope.env(), res);
        }

        @SVMToHotSpot(GetURI)
        @Override
        public URI getURI() {
            JString res = callGetURI(HotSpotToSVMScope.env(), getHandle());
            String stringifiedURI = createString(HotSpotToSVMScope.env(), res);
            return stringifiedURI == null ? null : URI.create(stringifiedURI);
        }
    }
}
