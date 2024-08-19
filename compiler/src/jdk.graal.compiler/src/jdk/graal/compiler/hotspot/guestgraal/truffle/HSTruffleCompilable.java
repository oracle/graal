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
import static jdk.graal.compiler.hotspot.guestgraal.truffle.BuildTime.getHostMethodHandleOrFail;

final class HSTruffleCompilable extends HSIndirectHandle implements TruffleCompilable {

    private static final Handles HANDLES = new Handles();

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
                res = (long) HANDLES.getFailedSpeculationsAddress.invoke(hsHandle);
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
            return (Map<String, String>) HANDLES.getCompilerOptions.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public long engineId() {
        try {
            return (long) HANDLES.engineId.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public void prepareForCompilation() {
        try {
            HANDLES.prepareForCompilation.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public boolean isTrivial() {
        try {
            return (boolean) HANDLES.isTrivial.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public JavaConstant asJavaConstant() {
        long constantHandle;
        try {
            constantHandle = (long) HANDLES.asJavaConstant.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
        return HotSpotJVMCIRuntime.runtime().unhand(JavaConstant.class, constantHandle);
    }

    @Override
    public void onCompilationFailed(Supplier<String> serializedException, boolean suppressed, boolean bailout, boolean permanentBailout, boolean graphTooBig) {
        try {
            Object serializedExceptionHsHandle = HANDLES.createStringSupplier.invoke(serializedException);
            HANDLES.onCompilationFailed.invoke(hsHandle, serializedExceptionHsHandle, suppressed, bailout, permanentBailout, graphTooBig);
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
                res = (String) HANDLES.getCompilableName.invoke(hsHandle);
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
                res = (String) HANDLES.compilableToString.invoke(hsHandle);
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
            return (int) HANDLES.getNonTrivialNodeCount.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public int countDirectCallNodes() {
        try {
            return (int) HANDLES.countDirectCallNodes.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public int getCallCount() {
        try {
            return (int) HANDLES.getCompilableCallCount.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public boolean cancelCompilation(CharSequence reason) {
        try {
            return (boolean) HANDLES.cancelCompilation.invoke(hsHandle, reason);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public boolean isSameOrSplit(TruffleCompilable ast) {
        try {
            return (boolean) HANDLES.isSameOrSplit.invoke(hsHandle, ast == null ? null : ((HSTruffleCompilable) ast).hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public int getKnownCallSiteCount() {
        try {
            return (int) HANDLES.getKnownCallSiteCount.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    private static final class Handles {
        final MethodHandle getFailedSpeculationsAddress = getHostMethodHandleOrFail(GetFailedSpeculationsAddress);
        final MethodHandle getCompilerOptions = getHostMethodHandleOrFail(GetCompilerOptions);
        final MethodHandle engineId = getHostMethodHandleOrFail(EngineId);
        final MethodHandle prepareForCompilation = getHostMethodHandleOrFail(PrepareForCompilation);
        final MethodHandle isTrivial = getHostMethodHandleOrFail(IsTrivial);
        final MethodHandle asJavaConstant = getHostMethodHandleOrFail(AsJavaConstant);
        final MethodHandle getCompilableName = getHostMethodHandleOrFail(GetCompilableName);
        final MethodHandle createStringSupplier = getHostMethodHandleOrFail(CreateStringSupplier);
        final MethodHandle onCompilationFailed = getHostMethodHandleOrFail(OnCompilationFailed);;
        final MethodHandle getNonTrivialNodeCount = getHostMethodHandleOrFail(GetNonTrivialNodeCount);
        final MethodHandle countDirectCallNodes = getHostMethodHandleOrFail(CountDirectCallNodes);
        final MethodHandle getCompilableCallCount = getHostMethodHandleOrFail(GetCompilableCallCount);
        final MethodHandle compilableToString = getHostMethodHandleOrFail(CompilableToString);
        final MethodHandle cancelCompilation = getHostMethodHandleOrFail(CancelCompilation);
        final MethodHandle isSameOrSplit = getHostMethodHandleOrFail(IsSameOrSplit);
        final MethodHandle getKnownCallSiteCount = getHostMethodHandleOrFail(GetKnownCallSiteCount);
    }
}
