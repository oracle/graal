/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.CallNodeHashCode;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetCallCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.GetCurrentCallTarget;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id.IsInliningForced;

import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCallNodeGen.callCallNodeHashCode;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCallNodeGen.callGetCallCount;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCallNodeGen.callGetCurrentCallTarget;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCallNodeGen.callIsInliningForced;
import static org.graalvm.libgraal.jni.HotSpotToSVMScope.env;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCallNode;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot;
import org.graalvm.libgraal.jni.HSObject;
import org.graalvm.libgraal.jni.HotSpotToSVMScope;
import org.graalvm.libgraal.jni.JNI.JObject;
import org.graalvm.libgraal.jni.JNIUtil;

final class HSTruffleCallNode extends HSObject implements TruffleCallNode {

    HSTruffleCallNode(HotSpotToSVMScope<HotSpotToSVM.Id> scope, JObject handle) {
        super(scope, handle);
    }

    @SVMToHotSpot(GetCurrentCallTarget)
    @Override
    public CompilableTruffleAST getCurrentCallTarget() {
        HotSpotToSVMScope<HotSpotToSVM.Id> scope = HotSpotToSVMScope.scope().narrow(HotSpotToSVM.Id.class);
        JObject hsCompilable = callGetCurrentCallTarget(scope.getEnv(), getHandle());
        if (hsCompilable.isNull()) {
            return null;
        } else {
            return new HSCompilableTruffleAST(scope, hsCompilable);
        }
    }

    @SVMToHotSpot(GetCallCount)
    @Override
    public int getCallCount() {
        return callGetCallCount(env(), getHandle());
    }

    @SVMToHotSpot(IsInliningForced)
    @Override
    public boolean isInliningForced() {
        return callIsInliningForced(env(), getHandle());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != HSTruffleCallNode.class) {
            return false;
        }
        return JNIUtil.IsSameObject(env(), getHandle(), ((HSTruffleCallNode) obj).getHandle());
    }

    @SVMToHotSpot(CallNodeHashCode)
    @Override
    public int hashCode() {
        return callCallNodeHashCode(env(), getHandle());
    }
}
