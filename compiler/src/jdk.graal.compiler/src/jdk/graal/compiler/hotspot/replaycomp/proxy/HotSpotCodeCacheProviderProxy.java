/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp.proxy;

import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.equalsInvokable;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.equalsMethod;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.hashCodeInvokable;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.hashCodeMethod;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.toStringInvokable;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.toStringMethod;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.unproxifyInvokable;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase.unproxifyMethod;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

//JaCoCo Exclude

public final class HotSpotCodeCacheProviderProxy extends HotSpotCodeCacheProvider implements CompilationProxy {
    private final InvocationHandler handler;

    HotSpotCodeCacheProviderProxy(InvocationHandler handler) {
        super(HotSpotJVMCIRuntime.runtime(), null, null);
        this.handler = handler;
    }

    private static SymbolicMethod method(String name, Class<?>... params) {
        return new SymbolicMethod(HotSpotCodeCacheProvider.class, name, params);
    }

    private Object handle(SymbolicMethod method, InvokableMethod invokable, Object... args) {
        return CompilationProxy.handle(handler, this, method, invokable, args);
    }

    public static final SymbolicMethod installCodeMethod = method("installCode", ResolvedJavaMethod.class, CompiledCode.class, InstalledCode.class, SpeculationLog.class, boolean.class, boolean.class);
    private static final InvokableMethod installCodeInvokable = (receiver, args) -> ((HotSpotCodeCacheProvider) receiver).installCode(
                    (ResolvedJavaMethod) args[0], (CompiledCode) args[1], (InstalledCode) args[2], (SpeculationLog) args[3], (boolean) args[4], (boolean) args[5]);

    @Override
    public InstalledCode installCode(ResolvedJavaMethod method, CompiledCode compiledCode, InstalledCode installedCode, SpeculationLog log, boolean isDefault, boolean profileDeopt) {
        return (InstalledCode) handle(installCodeMethod, installCodeInvokable, method, compiledCode, installedCode, log, isDefault, profileDeopt);
    }

    private static final SymbolicMethod invalidateInstalledCodeMethod = method("invalidateInstalledCode", InstalledCode.class);
    private static final InvokableMethod invalidateInstalledCodeInvokable = (receiver, args) -> {
        ((HotSpotCodeCacheProvider) receiver).invalidateInstalledCode((InstalledCode) args[0]);
        return null;
    };

    @Override
    public void invalidateInstalledCode(InstalledCode installedCode) {
        handle(invalidateInstalledCodeMethod, invalidateInstalledCodeInvokable, installedCode);
    }

    private static final SymbolicMethod getRegisterConfigMethod = method("getRegisterConfig");
    private static final InvokableMethod getRegisterConfigInvokable = (receiver, args) -> ((HotSpotCodeCacheProvider) receiver).getRegisterConfig();

    @Override
    public RegisterConfig getRegisterConfig() {
        return (RegisterConfig) handle(getRegisterConfigMethod, getRegisterConfigInvokable);
    }

    private static final SymbolicMethod getMinimumOutgoingSizeMethod = method("getMinimumOutgoingSize");
    private static final InvokableMethod getMinimumOutgoingSizeInvokable = (receiver, args) -> ((HotSpotCodeCacheProvider) receiver).getMinimumOutgoingSize();

    @Override
    public int getMinimumOutgoingSize() {
        return (int) handle(getMinimumOutgoingSizeMethod, getMinimumOutgoingSizeInvokable);
    }

    private static final SymbolicMethod getTargetMethod = method("getTarget");
    private static final InvokableMethod getTargetInvokable = (receiver, args) -> ((HotSpotCodeCacheProvider) receiver).getTarget();

    @Override
    public TargetDescription getTarget() {
        return (TargetDescription) handle(getTargetMethod, getTargetInvokable);
    }

    private static final SymbolicMethod createSpeculationLogMethod = method("createSpeculationLog");
    private static final InvokableMethod createSpeculationLogInvokable = (receiver, args) -> ((HotSpotCodeCacheProvider) receiver).createSpeculationLog();

    @Override
    public SpeculationLog createSpeculationLog() {
        return (SpeculationLog) handle(createSpeculationLogMethod, createSpeculationLogInvokable);
    }

    private static final SymbolicMethod getMaxCallTargetOffsetMethod = method("getMaxCallTargetOffset", long.class);
    private static final InvokableMethod getMaxCallTargetOffsetInvokable = (receiver, args) -> ((HotSpotCodeCacheProvider) receiver).getMaxCallTargetOffset((long) args[0]);

    @Override
    public long getMaxCallTargetOffset(long address) {
        return (long) handle(getMaxCallTargetOffsetMethod, getMaxCallTargetOffsetInvokable, address);
    }

    private static final SymbolicMethod shouldDebugNonSafepointsMethod = method("shouldDebugNonSafepoints");
    private static final InvokableMethod shouldDebugNonSafepointsInvokable = (receiver, args) -> ((HotSpotCodeCacheProvider) receiver).shouldDebugNonSafepoints();

    @Override
    public boolean shouldDebugNonSafepoints() {
        return (boolean) handle(shouldDebugNonSafepointsMethod, shouldDebugNonSafepointsInvokable);
    }

    private static final SymbolicMethod disassembleMethod = method("disassemble", InstalledCode.class);
    private static final InvokableMethod disassembleInvokable = (receiver, args) -> ((HotSpotCodeCacheProvider) receiver).disassemble((InstalledCode) args[0]);

    @Override
    public String disassemble(InstalledCode code) {
        return (String) handle(disassembleMethod, disassembleInvokable, code);
    }

    public static final SymbolicMethod interpreterFrameSizeMethod = method("interpreterFrameSize", BytecodeFrame.class);
    private static final InvokableMethod interpreterFrameSizeInvokable = (receiver, args) -> ((HotSpotCodeCacheProvider) receiver).interpreterFrameSize((BytecodeFrame) args[0]);

    @Override
    public int interpreterFrameSize(BytecodeFrame pos) {
        return (int) handle(interpreterFrameSizeMethod, interpreterFrameSizeInvokable, pos);
    }

    private static final SymbolicMethod resetCompilationStatisticsMethod = method("resetCompilationStatistics");
    private static final InvokableMethod resetCompilationStatisticsInvokable = (receiver, args) -> {
        ((HotSpotCodeCacheProvider) receiver).resetCompilationStatistics();
        return null;
    };

    @Override
    public void resetCompilationStatistics() {
        handle(resetCompilationStatisticsMethod, resetCompilationStatisticsInvokable);
    }

    @Override
    public Object unproxify() {
        return handle(unproxifyMethod, unproxifyInvokable);
    }

    @Override
    public int hashCode() {
        return (int) handle(hashCodeMethod, hashCodeInvokable);
    }

    @Override
    public boolean equals(Object obj) {
        return (boolean) handle(equalsMethod, equalsInvokable, obj);
    }

    @Override
    public String toString() {
        return (String) handle(toStringMethod, toStringInvokable);
    }
}
