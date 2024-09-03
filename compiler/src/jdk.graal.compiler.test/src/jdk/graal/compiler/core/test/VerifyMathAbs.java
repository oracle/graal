/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.VerifyPhase;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Verify that no code in Graal calls {@link Math#abs(long)} because that can result in overflows
 * for {@link Long#MIN_VALUE}.
 */
public class VerifyMathAbs extends VerifyPhase<CoreProviders> {

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        MetaAccessProvider metaAccess = context.getMetaAccess();
        final ResolvedJavaType mathType = metaAccess.lookupJavaType(Math.class);
        final ResolvedJavaType numUtil = metaAccess.lookupJavaType(NumUtil.class);

        ResolvedJavaMethod absInt = null;
        ResolvedJavaMethod absLong = null;

        ResolvedJavaMethod caller = graph.method();

        for (ResolvedJavaMethod m : mathType.getDeclaredMethods()) {
            if (m.getName().equals("abs")) {
                switch (m.getSignature().getReturnKind()) {
                    case Int:
                        assert absInt == null;
                        absInt = m;
                        break;
                    case Long:
                        assert absLong == null;
                        absLong = m;
                        break;
                    default:
                        break;
                }
            }
        }

        assert absInt != null;
        assert absLong != null;

        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            if (callee.equals(absInt) || callee.equals(absLong)) {
                if (caller.getDeclaringClass().equals(numUtil) && caller.getName().equals("unsafeAbs")) {
                    // only allowed usages of abs
                    continue;
                }
                throw new VerificationError("Call to %s at callsite %s is prohibited, use absExact or NumUtil.safeAbs.",
                                callee.format("%H.%n(%p)"),
                                caller.format("%H.%n(%p)"));
            }
        }

    }

}
