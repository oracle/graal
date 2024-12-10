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
import com.oracle.truffle.compiler.TruffleSourceLanguagePosition;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.JavaConstant;

import java.util.Map;

final class HSTruffleCompilationTask extends HSIndirectHandle implements TruffleCompilationTask {

    HSTruffleCompilationTask(Object hsHandle) {
        super(hsHandle);
    }

    @Override
    public boolean isCancelled() {
        return TruffleFromLibGraalStartPoints.isCancelled(hsHandle);
    }

    @Override
    public boolean isLastTier() {
        return TruffleFromLibGraalStartPoints.isLastTier(hsHandle);
    }

    @Override
    public boolean hasNextTier() {
        return TruffleFromLibGraalStartPoints.hasNextTier(hsHandle);
    }

    @Override
    public TruffleSourceLanguagePosition getPosition(JavaConstant node) {
        long nodeHandle = HotSpotJVMCIRuntime.runtime().translate(node);
        Object positionHsHandle = TruffleFromLibGraalStartPoints.getPosition(hsHandle, nodeHandle);
        if (positionHsHandle == null) {
            return null;
        } else {
            return new HSTruffleSourceLanguagePosition(positionHsHandle);
        }
    }

    @Override
    public void addTargetToDequeue(TruffleCompilable target) {
        TruffleFromLibGraalStartPoints.addTargetToDequeue(hsHandle, ((HSTruffleCompilable) target).hsHandle);
    }

    @Override
    public void setCallCounts(int total, int inlined) {
        TruffleFromLibGraalStartPoints.setCallCounts(hsHandle, total, inlined);
    }

    @Override
    public void addInlinedTarget(TruffleCompilable target) {
        TruffleFromLibGraalStartPoints.addInlinedTarget(hsHandle, ((HSTruffleCompilable) target).hsHandle);
    }

    @Override
    public Map<String, Object> getDebugProperties(JavaConstant node) {
        long nodeHandle = HotSpotJVMCIRuntime.runtime().translate(node);
        return TruffleFromLibGraalStartPoints.getDebugProperties(hsHandle, nodeHandle);
    }
}
