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
package jdk.graal.compiler.libgraal.truffle;

import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleSourceLanguagePosition;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.JavaConstant;
import org.graalvm.jniutils.HSObject;
import org.graalvm.jniutils.JNI.JByteArray;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.AddInlinedTarget;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.AddTargetToDequeue;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetDebugProperties;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetPosition;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.HasNextTier;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsCancelled;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsLastTier;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.SetCallCounts;
import static org.graalvm.jniutils.JNIMethodScope.env;

final class HSTruffleCompilationTask extends HSObject implements TruffleCompilationTask {

    private final TruffleFromLibGraalCalls calls;

    HSTruffleCompilationTask(JNIMethodScope scope, JObject handle, HSTruffleCompilerRuntime runtime) {
        super(scope, handle);
        this.calls = runtime.calls;
    }

    @TruffleFromLibGraal(IsCancelled)
    @Override
    public boolean isCancelled() {
        return HSTruffleCompilationTaskGen.callIsCancelled(calls, env(), getHandle());
    }

    @TruffleFromLibGraal(IsLastTier)
    @Override
    public boolean isLastTier() {
        return HSTruffleCompilationTaskGen.callIsLastTier(calls, env(), getHandle());
    }

    @TruffleFromLibGraal(HasNextTier)
    @Override
    public boolean hasNextTier() {
        return HSTruffleCompilationTaskGen.callHasNextTier(calls, env(), getHandle());
    }

    @TruffleFromLibGraal(GetPosition)
    @Override
    public TruffleSourceLanguagePosition getPosition(JavaConstant node) {
        long nodeHandle = HotSpotJVMCIRuntime.runtime().translate(node);
        JObject res = HSTruffleCompilationTaskGen.callGetPosition(calls, env(), getHandle(), nodeHandle);
        if (res.isNull()) {
            return null;
        }
        return new HSTruffleSourceLanguagePosition(JNIMethodScope.scope(), res, calls);
    }

    @TruffleFromLibGraal(AddTargetToDequeue)
    @Override
    public void addTargetToDequeue(TruffleCompilable target) {
        JObject hsCompilable = ((HSTruffleCompilable) target).getHandle();
        HSTruffleCompilationTaskGen.callAddTargetToDequeue(calls, env(), getHandle(), hsCompilable);
    }

    @TruffleFromLibGraal(SetCallCounts)
    @Override
    public void setCallCounts(int total, int inlined) {
        HSTruffleCompilationTaskGen.callSetCallCounts(calls, env(), getHandle(), total, inlined);
    }

    @TruffleFromLibGraal(AddInlinedTarget)
    @Override
    public void addInlinedTarget(TruffleCompilable target) {
        JObject hsCompilable = ((HSTruffleCompilable) target).getHandle();
        HSTruffleCompilationTaskGen.callAddInlinedTarget(calls, env(), getHandle(), hsCompilable);
    }

    @TruffleFromLibGraal(GetDebugProperties)
    @Override
    public Map<String, Object> getDebugProperties(JavaConstant node) {
        long nodeHandle = HotSpotJVMCIRuntime.runtime().translate(node);
        JNIEnv env = JNIMethodScope.env();
        byte[] bytes = JNIUtil.createArray(env, (JByteArray) HSTruffleCompilationTaskGen.callGetDebugProperties(calls, env, getHandle(), nodeHandle));
        BinaryInput in = BinaryInput.create(bytes);
        Map<String, Object> result = new LinkedHashMap<>();
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            String key = in.readUTF();
            Object value = in.readTypedValue();
            result.put(key, value);
        }
        return result;
    }
}
