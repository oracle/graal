/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.stubs;

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.Speculative;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.truffle.TruffleBytecodeHandlerCallsite;
import jdk.graal.compiler.truffle.TruffleBytecodeHandlerCallsite.ArgumentInfo;
import jdk.graal.compiler.truffle.host.HostInliningPhase;
import jdk.graal.compiler.truffle.hotspot.HotSpotOutlineBytecodeHandlerPhase;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * A stub implementation for calling Truffle bytecode handler.
 *
 * @see TruffleBytecodeHandlerCallsite#createStub
 */
public class HotSpotTruffleBytecodeHandlerStub extends Stub {

    private final TruffleBytecodeHandlerCallsite callsite;

    public HotSpotTruffleBytecodeHandlerStub(OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage, TruffleBytecodeHandlerCallsite callsite) {
        super(options, providers, linkage);
        this.callsite = callsite;
    }

    @Override
    protected StructuredGraph getGraph(DebugContext debug, CompilationIdentifier compilationId) {
        try {
            HotSpotGraphKit kit = new HotSpotGraphKit(debug, callsite.getEnclosingMethod(), providers, providers.getGraphBuilderPlugins(), compilationId, callsite.getStubName(), false, true);
            return callsite.createStub(kit, callsite.getEnclosingMethod(), false, null, null);
        } catch (Exception e) {
            throw GraalError.shouldNotReachHere(e); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    protected ResolvedJavaMethod getInstalledCodeOwner() {
        return callsite.getTargetMethod();
    }

    @Override
    protected Object debugScopeContext() {
        return getLinkage().getDescriptor().getName();
    }

    @Override
    protected Suites createSuites() {
        Suites suites = providers.getSuites().getDefaultSuites(options, providers.getLowerer().getTarget().arch).copy();
        // ResolvedJavaMethod for this stub is null and cannot invoke
        // TruffleHostEnvironment.get(method)
        suites.getHighTier().removeSubTypePhases(HostInliningPhase.class);
        // This stub contains an invocation to the original bytecode handler.
        // Do not replace it with a foreign call to this stub itself.
        suites.getHighTier().removeSubTypePhases(HotSpotOutlineBytecodeHandlerPhase.class);
        // We do not have a SpeculationLog for this stub
        suites.getHighTier().removeSubTypePhases(Speculative.class);
        suites.getMidTier().removeSubTypePhases(Speculative.class);
        suites.getLowTier().removeSubTypePhases(Speculative.class);
        return suites;
    }

    @Override
    public AllocatableValue[] getAdditionalReturns(CallingConvention callingConvention) {
        List<AllocatableValue> result = new ArrayList<>();

        // This stub is responsible for storing the updated value into the same argument location.
        for (ArgumentInfo argumentInfo : callsite.getArgumentInfos()) {
            if (!argumentInfo.isImmutable()) {
                result.add(callingConvention.getArgument(argumentInfo.index()));
            }
        }

        return result.toArray(AllocatableValue.NONE);
    }
}
