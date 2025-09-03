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

import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.TriState;

//JaCoCo Exclude

public sealed class ProfilingInfoProxy extends CompilationProxyBase implements ProfilingInfo permits HotSpotProfilingInfoProxy {
    ProfilingInfoProxy(InvocationHandler handler) {
        super(handler);
    }

    private static SymbolicMethod method(String name, Class<?>... params) {
        return new SymbolicMethod(ProfilingInfo.class, name, params);
    }

    private static final SymbolicMethod getCodeSizeMethod = method("getCodeSize");
    private static final InvokableMethod getCodeSizeInvokable = (receiver, args) -> ((ProfilingInfo) receiver).getCodeSize();

    @Override
    public int getCodeSize() {
        return (int) handle(getCodeSizeMethod, getCodeSizeInvokable);
    }

    private static final SymbolicMethod getBranchTakenProbabilityMethod = method("getBranchTakenProbability", int.class);
    private static final InvokableMethod getBranchTakenProbabilityInvokable = (receiver, args) -> ((ProfilingInfo) receiver).getBranchTakenProbability((int) args[0]);

    @Override
    public double getBranchTakenProbability(int bci) {
        return (double) handle(getBranchTakenProbabilityMethod, getBranchTakenProbabilityInvokable, bci);
    }

    private static final SymbolicMethod getSwitchProbabilitiesMethod = method("getSwitchProbabilities", int.class);
    private static final InvokableMethod getSwitchProbabilitiesInvokable = (receiver, args) -> ((ProfilingInfo) receiver).getSwitchProbabilities((int) args[0]);

    @Override
    public double[] getSwitchProbabilities(int bci) {
        return (double[]) handle(getSwitchProbabilitiesMethod, getSwitchProbabilitiesInvokable, bci);
    }

    private static final SymbolicMethod getTypeProfileMethod = method("getTypeProfile", int.class);
    private static final InvokableMethod getTypeProfileInvokable = (receiver, args) -> ((ProfilingInfo) receiver).getTypeProfile((int) args[0]);

    @Override
    public JavaTypeProfile getTypeProfile(int bci) {
        return (JavaTypeProfile) handle(getTypeProfileMethod, getTypeProfileInvokable, bci);
    }

    private static final SymbolicMethod getMethodProfileMethod = method("getMethodProfile", int.class);
    private static final InvokableMethod getMethodProfileInvokable = (receiver, args) -> ((ProfilingInfo) receiver).getMethodProfile((int) args[0]);

    @Override
    public JavaMethodProfile getMethodProfile(int bci) {
        return (JavaMethodProfile) handle(getMethodProfileMethod, getMethodProfileInvokable, bci);
    }

    private static final SymbolicMethod getExceptionSeenMethod = method("getExceptionSeen", int.class);
    private static final InvokableMethod getExceptionSeenInvokable = (receiver, args) -> ((ProfilingInfo) receiver).getExceptionSeen((int) args[0]);

    @Override
    public TriState getExceptionSeen(int bci) {
        return (TriState) handle(getExceptionSeenMethod, getExceptionSeenInvokable, bci);
    }

    private static final SymbolicMethod getNullSeenMethod = method("getNullSeen", int.class);
    private static final InvokableMethod getNullSeenInvokable = (receiver, args) -> ((ProfilingInfo) receiver).getNullSeen((int) args[0]);

    @Override
    public TriState getNullSeen(int bci) {
        return (TriState) handle(getNullSeenMethod, getNullSeenInvokable, bci);
    }

    private static final SymbolicMethod getExecutionCountMethod = method("getExecutionCount", int.class);
    private static final InvokableMethod getExecutionCountInvokable = (receiver, args) -> ((ProfilingInfo) receiver).getExecutionCount((int) args[0]);

    @Override
    public int getExecutionCount(int bci) {
        return (int) handle(getExecutionCountMethod, getExecutionCountInvokable, bci);
    }

    public static final SymbolicMethod getDeoptimizationCountMethod = method("getDeoptimizationCount", DeoptimizationReason.class);
    private static final InvokableMethod getDeoptimizationCountInvokable = (receiver, args) -> ((ProfilingInfo) receiver).getDeoptimizationCount((DeoptimizationReason) args[0]);

    @Override
    public int getDeoptimizationCount(DeoptimizationReason reason) {
        return (int) handle(getDeoptimizationCountMethod, getDeoptimizationCountInvokable, reason);
    }

    public static final SymbolicMethod setCompilerIRSizeMethod = method("setCompilerIRSize", Class.class, int.class);
    private static final InvokableMethod setCompilerIRSizeInvokable = (receiver, args) -> ((ProfilingInfo) receiver).setCompilerIRSize((Class<?>) args[0], (int) args[1]);

    @Override
    public boolean setCompilerIRSize(Class<?> irType, int irSize) {
        return (boolean) handle(setCompilerIRSizeMethod, setCompilerIRSizeInvokable, irType, irSize);
    }

    private static final SymbolicMethod getCompilerIRSizeMethod = method("getCompilerIRSize", Class.class);
    private static final InvokableMethod getCompilerIRSizeInvokable = (receiver, args) -> ((ProfilingInfo) receiver).getCompilerIRSize((Class<?>) args[0]);

    @Override
    public int getCompilerIRSize(Class<?> irType) {
        return (int) handle(getCompilerIRSizeMethod, getCompilerIRSizeInvokable, irType);
    }

    public static final SymbolicMethod isMatureMethod = method("isMature");
    private static final InvokableMethod isMatureInvokable = (receiver, args) -> ((ProfilingInfo) receiver).isMature();

    @Override
    public boolean isMature() {
        return (boolean) handle(isMatureMethod, isMatureInvokable);
    }

    private static final SymbolicMethod setMatureMethod = method("setMature");
    private static final InvokableMethod setMatureInvokable = (receiver, args) -> {
        ((ProfilingInfo) receiver).setMature();
        return null;
    };

    @Override
    public void setMature() {
        handle(setMatureMethod, setMatureInvokable);
    }
}
