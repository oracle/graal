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
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.HotSpotGraalServices;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;

import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.function.Supplier;

import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.AsJavaConstant;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.CancelCompilation;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.CompilableToString;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.CountDirectCallNodes;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.CreateStringSupplier;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.EngineId;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetCompilableCallCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetCompilableName;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetCompilerOptions;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetFailedSpeculationsAddress;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetKnownCallSiteCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetNonTrivialNodeCount;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsSameOrSplit;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsTrivial;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.OnCompilationFailed;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.PrepareForCompilation;
import static jdk.graal.compiler.hotspot.guestgraal.truffle.BuildTime.getOrFail;

final class HSTruffleCompilable extends HSIndirectHandle implements TruffleCompilable {

    private static MethodHandle getFailedSpeculationsAddress;
    private static MethodHandle getCompilerOptions;
    private static MethodHandle engineId;
    private static MethodHandle prepareForCompilation;
    private static MethodHandle isTrivial;
    private static MethodHandle asJavaConstant;
    private static MethodHandle getCompilableName;
    private static MethodHandle createStringSupplier;
    private static MethodHandle onCompilationFailed;
    private static MethodHandle getNonTrivialNodeCount;
    private static MethodHandle countDirectCallNodes;
    private static MethodHandle getCompilableCallCount;
    private static MethodHandle compilableToString;
    private static MethodHandle cancelCompilation;
    private static MethodHandle isSameOrSplit;
    private static MethodHandle getKnownCallSiteCount;

    static void initialize(Map<String, MethodHandle> upCallHandles) {
        getFailedSpeculationsAddress = getOrFail(upCallHandles, GetFailedSpeculationsAddress);
        getCompilerOptions = getOrFail(upCallHandles, GetCompilerOptions);
        engineId = getOrFail(upCallHandles, EngineId);
        prepareForCompilation = getOrFail(upCallHandles, PrepareForCompilation);
        isTrivial = getOrFail(upCallHandles, IsTrivial);
        asJavaConstant = getOrFail(upCallHandles, AsJavaConstant);
        getCompilableName = getOrFail(upCallHandles, GetCompilableName);
        createStringSupplier = getOrFail(upCallHandles, CreateStringSupplier);
        onCompilationFailed = getOrFail(upCallHandles, OnCompilationFailed);
        getNonTrivialNodeCount = getOrFail(upCallHandles, GetNonTrivialNodeCount);
        countDirectCallNodes = getOrFail(upCallHandles, CountDirectCallNodes);
        getCompilableCallCount = getOrFail(upCallHandles, GetCompilableCallCount);
        compilableToString = getOrFail(upCallHandles, CompilableToString);
        cancelCompilation = getOrFail(upCallHandles, CancelCompilation);
        isSameOrSplit = getOrFail(upCallHandles, IsSameOrSplit);
        getKnownCallSiteCount = getOrFail(upCallHandles, GetKnownCallSiteCount);
    }

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
            try {
                res = (long) getFailedSpeculationsAddress.invoke(hsHandle);
                cachedFailedSpeculationsAddress = res;
            } catch (Throwable t) {
                throw handleException(t);
            }
        }
        return HotSpotGraalServices.newHotSpotSpeculationLog(cachedFailedSpeculationsAddress);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> getCompilerOptions() {
        try {
            return (Map<String, String>) getCompilerOptions.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public long engineId() {
        try {
            return (long) engineId.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public void prepareForCompilation() {
        try {
            prepareForCompilation.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public boolean isTrivial() {
        try {
            return (boolean) isTrivial.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public JavaConstant asJavaConstant() {
        long constantHandle;
        try {
            constantHandle = (long) asJavaConstant.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
        return HotSpotJVMCIRuntime.runtime().unhand(JavaConstant.class, constantHandle);
    }

    @Override
    public void onCompilationFailed(Supplier<String> serializedException, boolean suppressed, boolean bailout, boolean permanentBailout, boolean graphTooBig) {
        try {
            Object serializedExceptionHsHandle = createStringSupplier.invoke(serializedException);
            onCompilationFailed.invoke(hsHandle, serializedExceptionHsHandle, suppressed, bailout, permanentBailout, graphTooBig);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public boolean onInvalidate(Object source, CharSequence reason, boolean wasActive) {
        throw GraalError.shouldNotReachHere("Should not be reachable."); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public String getName() {
        String res = cachedName;
        if (res == null) {
            try {
                res = (String) getCompilableName.invoke(hsHandle);
                cachedName = res;
            } catch (Throwable t) {
                throw handleException(t);
            }
        }
        return res;
    }

    @Override
    public String toString() {
        String res = cachedString;
        if (res == null) {
            try {
                res = (String) compilableToString.invoke(hsHandle);
                cachedString = res;
            } catch (Throwable t) {
                throw handleException(t);
            }
        }
        return res;
    }

    @Override
    public int getNonTrivialNodeCount() {
        try {
            return (int) getNonTrivialNodeCount.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public int countDirectCallNodes() {
        try {
            return (int) countDirectCallNodes.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public int getCallCount() {
        try {
            return (int) getCompilableCallCount.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public boolean cancelCompilation(CharSequence reason) {
        try {
            return (boolean) cancelCompilation.invoke(hsHandle, reason);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public boolean isSameOrSplit(TruffleCompilable ast) {
        try {
            return (boolean) isSameOrSplit.invoke(hsHandle, ast == null ? null : ((HSTruffleCompilable) ast).hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public int getKnownCallSiteCount() {
        try {
            return (int) getKnownCallSiteCount.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }
}
