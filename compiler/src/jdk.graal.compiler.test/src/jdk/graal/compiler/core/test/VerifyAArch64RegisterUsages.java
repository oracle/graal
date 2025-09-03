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
package jdk.graal.compiler.core.test;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.VerifyPhase;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.util.Arrays;
import java.util.List;

/**
 * Verify that scratch registers are not used in AArch64 specific code. We use a different set for
 * HotSpot (r8/r9) and SubstrateVM (r9/r10).
 */
public class VerifyAArch64RegisterUsages extends VerifyPhase<CoreProviders> {

    @Override
    public boolean checkContract() {
        return false;
    }

    private List<String> potentialScratchRegisters = Arrays.asList("r8", "r9", "r10", "rscratch1", "rscratch2");

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        MetaAccessProvider metaAccess = context.getMetaAccess();
        ResolvedJavaMethod method = graph.method();
        String methodName = method.format("%H.%n");

        switch (methodName) {
            case "jdk.graal.compiler.hotspot.aarch64.AArch64HotSpotRegisterAllocationConfig.<clinit>":
            case "jdk.graal.compiler.hotspot.aarch64.AArch64HotSpotMacroAssembler.<init>":
            case "jdk.graal.compiler.hotspot.aarch64.AArch64HotSpotBackend.emitCodePrefix":
            case "com.oracle.svm.core.aarch64.SubstrateAArch64MacroAssembler.<clinit>":
            case "com.oracle.svm.core.graal.aarch64.SubstrateAArch64RegisterConfig.getCallingConvention":
                // Exempted cases
                return;
            default:
        }

        final ResolvedJavaType jvmCIAArch64 = metaAccess.lookupJavaType(AArch64.class);
        final ResolvedJavaType jvmCIRegister = metaAccess.lookupJavaType(Register.class);

        for (LoadFieldNode t : graph.getNodes().filter(LoadFieldNode.class)) {
            JavaField f = t.field();

            if (!jvmCIAArch64.equals(f.getDeclaringClass()) || !jvmCIRegister.equals(f.getType())) {
                continue;
            }

            if (potentialScratchRegisters.contains(f.getName())) {
                throw new VerificationError("Access to %s register at callsite %s is prohibited.",
                                f, method.format("%H.%n(%p)"), f);
            }
        }
    }
}
