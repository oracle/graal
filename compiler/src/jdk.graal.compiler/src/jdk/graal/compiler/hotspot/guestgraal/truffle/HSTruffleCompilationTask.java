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
package jdk.graal.compiler.hotspot.guestgraal.truffle;

import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleSourceLanguagePosition;
import jdk.vm.ci.meta.JavaConstant;

import java.lang.invoke.MethodHandle;
import java.util.Map;

import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.AddInlinedTarget;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.AddTargetToDequeue;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetDebugProperties;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetPosition;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.HasNextTier;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsCancelled;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsLastTier;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.SetCallCounts;
import static jdk.graal.compiler.hotspot.guestgraal.truffle.BuildTime.getHostMethodHandleOrFail;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;

final class HSTruffleCompilationTask extends HSIndirectHandle implements TruffleCompilationTask {

    private static final Handles HANDLES = new Handles();

    HSTruffleCompilationTask(Object hsHandle) {
        super(hsHandle);
    }

    @Override
    public boolean isCancelled() {
        try {
            return (boolean) HANDLES.isCancelled.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public boolean isLastTier() {
        try {
            return (boolean) HANDLES.isLastTier.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public boolean hasNextTier() {
        try {
            return (boolean) HANDLES.hasNextTier.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public TruffleSourceLanguagePosition getPosition(JavaConstant node) {
        long nodeHandle = runtime().translate(node);
        Object positionHsHandle;
        try {
            positionHsHandle = HANDLES.getPosition.invoke(hsHandle, nodeHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
        if (positionHsHandle == null) {
            return null;
        } else {
            return new HSTruffleSourceLanguagePosition(positionHsHandle);
        }
    }

    @Override
    public void addTargetToDequeue(TruffleCompilable target) {
        try {
            HANDLES.addTargetToDequeue.invoke(hsHandle, ((HSTruffleCompilable) target).hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public void setCallCounts(int total, int inlined) {
        try {
            HANDLES.setCallCounts.invoke(hsHandle, total, inlined);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public void addInlinedTarget(TruffleCompilable target) {
        try {
            HANDLES.addInlinedTarget.invoke(hsHandle, ((HSTruffleCompilable) target).hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDebugProperties(JavaConstant node) {
        try {
            long nodeHandle = runtime().translate(node);
            return (Map<String, Object>) HANDLES.getDebugProperties.invoke(hsHandle, nodeHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    private static final class Handles {
        final MethodHandle isCancelled = getHostMethodHandleOrFail(IsCancelled);
        final MethodHandle hasNextTier = getHostMethodHandleOrFail(HasNextTier);
        final MethodHandle isLastTier = getHostMethodHandleOrFail(IsLastTier);
        final MethodHandle getPosition = getHostMethodHandleOrFail(GetPosition);
        final MethodHandle addTargetToDequeue = getHostMethodHandleOrFail(AddTargetToDequeue);
        final MethodHandle setCallCounts = getHostMethodHandleOrFail(SetCallCounts);
        final MethodHandle addInlinedTarget = getHostMethodHandleOrFail(AddInlinedTarget);
        final MethodHandle getDebugProperties = getHostMethodHandleOrFail(GetDebugProperties);
    }
}
