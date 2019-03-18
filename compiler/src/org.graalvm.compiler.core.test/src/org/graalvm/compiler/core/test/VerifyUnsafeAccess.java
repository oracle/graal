/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.phases.VerifyPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.misc.Unsafe;

/**
 * Checks that the {@link Unsafe} singleton instance is only accessed via well known classes such as
 * {@link GraalUnsafeAccess}.
 */
public class VerifyUnsafeAccess extends VerifyPhase<PhaseContext> {

    @Override
    protected boolean verify(StructuredGraph graph, PhaseContext context) {
        MetaAccessProvider metaAccess = context.getMetaAccess();
        final ResolvedJavaType unsafeType = metaAccess.lookupJavaType(Unsafe.class);

        ResolvedJavaMethod caller = graph.method();
        String holderQualified = caller.format("%H");
        String holderUnqualified = caller.format("%h");
        String packageName = holderQualified.substring(0, holderQualified.length() - holderUnqualified.length() - 1);
        if ((holderQualified.equals(GraalUnsafeAccess.class.getName()) ||
                        holderQualified.equals("org.graalvm.compiler.truffle.runtime.UnsafeAccess") ||
                        holderQualified.equals("jdk.vm.ci.hotspot.UnsafeAccess")) &&
                        caller.getName().equals("initUnsafe")) {
            // This is the blessed way access Unsafe in Graal, GraalTruffleRuntime and JVMCI
            return true;
        } else if (packageName.startsWith("com.oracle.truffle")) {
            // Truffle does not depend on Graal and so cannot use GraalUnsafeAccess
            return true;
        }

        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            if (callee.getDeclaringClass().equals(unsafeType)) {
                if (callee.getName().equals("getUnsafe")) {
                    throw new VerificationError("Call to %s at callsite %s is prohibited. Call Services.getSavedProperties().get(String) instead.",
                                    callee.format("%H.%n(%p)"),
                                    caller.format("%H.%n(%p)"));
                }
            }
        }

        for (InstanceOfNode node : graph.getNodes().filter(InstanceOfNode.class)) {
            TypeReference typeRef = node.type();
            if (typeRef != null) {
                if (unsafeType.isAssignableFrom(typeRef.getType())) {
                    throw new VerificationError("Cast to %s in %s is prohibited as it implies accessing Unsafe.theUnsafe via reflection. Use Graal.Unsafe.UNSAFE instead.",
                                    unsafeType.toJavaName(),
                                    caller.format("%H.%n(%p)"));

                }
            }
        }
        return true;
    }
}
