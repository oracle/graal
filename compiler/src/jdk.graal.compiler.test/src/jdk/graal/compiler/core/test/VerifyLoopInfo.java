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

import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.VerifyPhase;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Verify that every new code added to Graal is very cautious about its usage of the loop API in
 * {@link CountedLoopInfo}.
 */
public class VerifyLoopInfo extends VerifyPhase<CoreProviders> {

    private static EconomicSet<String> allowedCallers;
    static {
        allowedCallers = EconomicSet.create();
        allowedCallers.add("getBodyIVStart");
        allowedCallers.add("limitCheckedPreviousOrRootEntryValue");
        allowedCallers.add("getBodyIVEqualsLimitCheckedIV");
        allowedCallers.add("getBodyIVExitValue");
        allowedCallers.add("getBodyIVExtremum");
    }

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        MetaAccessProvider metaAccess = context.getMetaAccess();
        final ResolvedJavaType loopInfo = metaAccess.lookupJavaType(CountedLoopInfo.class);
        ResolvedJavaMethod bodyIVMethod = null;
        ResolvedJavaMethod caller = graph.method();
        if (allowedCallers.contains(caller.getName())) {
            return;
        }
        for (ResolvedJavaMethod m : loopInfo.getDeclaredMethods()) {
            if (m.getName().equals("getBodyIV")) {
                bodyIVMethod = m;
                break;
            }
        }
        assert bodyIVMethod != null;
        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            if (callee.equals(bodyIVMethod)) {
                throw new VerificationError(
                                "Call to %s at callsite %s is prohibited only special methods can call getBodyIV, discuss with the Graal Compiler Optimizer team on how to solve this error.",
                                callee.format("%H.%n(%p)"),
                                caller.format("%H.%n(%p)"));
            }
        }
    }
}
