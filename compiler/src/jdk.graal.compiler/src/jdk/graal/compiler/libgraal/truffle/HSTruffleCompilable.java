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
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.HotSpotGraalServices;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;

import java.util.Map;
import java.util.function.Supplier;

final class HSTruffleCompilable extends HSIndirectHandle implements TruffleCompilable {

    /**
     * Handle to {@code speculationLog} field of the {@code OptimizedCallTarget}.
     */
    private Long cachedFailedSpeculationsAddress;
    private volatile String cachedName;
    private volatile String cachedString;

    HSTruffleCompilable(Object hsHandle) {
        super(hsHandle);
    }

    @Override
    public SpeculationLog getCompilationSpeculationLog() {
        Long res = cachedFailedSpeculationsAddress;
        if (res == null) {
            res = TruffleFromLibGraalStartPoints.getFailedSpeculationsAddress(hsHandle);
            cachedFailedSpeculationsAddress = res;
        }
        return HotSpotGraalServices.newHotSpotSpeculationLog(cachedFailedSpeculationsAddress);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> getCompilerOptions() {
        return TruffleFromLibGraalStartPoints.getCompilerOptions(hsHandle);
    }

    @Override
    public long engineId() {
        return TruffleFromLibGraalStartPoints.engineId(hsHandle);
    }

    @Override
    public boolean prepareForCompilation(boolean rootCompilation, int compilationTier, boolean lastTier) {
        return TruffleFromLibGraalStartPoints.prepareForCompilation(hsHandle, rootCompilation, compilationTier, lastTier);
    }

    @Override
    public boolean isTrivial() {
        return TruffleFromLibGraalStartPoints.isTrivial(hsHandle);
    }

    @Override
    public JavaConstant asJavaConstant() {
        long constantHandle;
        constantHandle = TruffleFromLibGraalStartPoints.asJavaConstant(hsHandle);
        return HotSpotJVMCIRuntime.runtime().unhand(JavaConstant.class, constantHandle);
    }

    @Override
    public void onCompilationFailed(Supplier<String> serializedException, boolean suppressed, boolean bailout, boolean permanentBailout, boolean graphTooBig) {
        Object serializedExceptionHsHandle = TruffleFromLibGraalStartPoints.createStringSupplier(serializedException);
        TruffleFromLibGraalStartPoints.onCompilationFailed(hsHandle, serializedExceptionHsHandle, suppressed, bailout, permanentBailout, graphTooBig);
    }

    @Override
    public boolean onInvalidate(Object source, CharSequence reason, boolean wasActive) {
        throw GraalError.shouldNotReachHere("Should not be reachable."); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public String getName() {
        String res = cachedName;
        if (res == null) {
            res = TruffleFromLibGraalStartPoints.getCompilableName(hsHandle);
            cachedName = res;
        }
        return res;
    }

    @Override
    public String toString() {
        String res = cachedString;
        if (res == null) {
            res = TruffleFromLibGraalStartPoints.compilableToString(hsHandle);
            cachedString = res;
        }
        return res;
    }

    @Override
    public int getNonTrivialNodeCount() {
        return TruffleFromLibGraalStartPoints.getNonTrivialNodeCount(hsHandle);
    }

    @Override
    public int countDirectCallNodes() {
        return TruffleFromLibGraalStartPoints.countDirectCallNodes(hsHandle);
    }

    @Override
    public int getCallCount() {
        return TruffleFromLibGraalStartPoints.getCompilableCallCount(hsHandle);
    }

    @Override
    public boolean cancelCompilation(CharSequence reason) {
        return TruffleFromLibGraalStartPoints.cancelCompilation(hsHandle, reason);
    }

    @Override
    public boolean isSameOrSplit(TruffleCompilable ast) {
        return TruffleFromLibGraalStartPoints.isSameOrSplit(hsHandle, ast == null ? null : ((HSTruffleCompilable) ast).hsHandle);
    }

    @Override
    public int getKnownCallSiteCount() {
        return TruffleFromLibGraalStartPoints.getKnownCallSiteCount(hsHandle);
    }
}
