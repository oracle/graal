/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.lir.LIRValueUtil;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.VerifyPhase;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;

/**
 * Checks that LIR {@link Value}s are only type-checked against {@link Variable} or cast to
 * {@link Variable} inside well-known utility methods such as
 * {@link LIRValueUtil#isVariable(Value)}, {@link LIRValueUtil#asVariable(Value)}.
 */
public class VerifyVariableCasts extends VerifyPhase<CoreProviders> {

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        MetaAccessProvider metaAccess = context.getMetaAccess();
        final ResolvedJavaType variableType = metaAccess.lookupJavaType(Variable.class);
        ResolvedJavaMethod method = graph.method();
        String holderQualified = method.format("%H");

        if ((holderQualified.equals(LIRValueUtil.class.getName()) && (method.getName().equals("asVariable") || method.getName().equals("isVariable"))) ||
                        (holderQualified.equals(Variable.class.getName()) && method.getName().equals("equals"))) {
            // These are the only places allowed to do raw type checks or casts on Variable.
            return;
        }

        for (PiNode cast : graph.getNodes().filter(PiNode.class)) {
            Stamp stamp = cast.piStamp();
            if (stamp instanceof AbstractObjectStamp) {
                ResolvedJavaType castType = ((AbstractObjectStamp) stamp).javaType(metaAccess);
                if (variableType.isAssignableFrom(castType)) {
                    throw new VerificationError("Cast to %s in %s is prohibited as it might skip checks for LIR CastValues. Use LIRValueUtil.asVariable instead.",
                                    variableType.toJavaName(),
                                    method.format("%H.%n(%p)"));
                }
            }
        }

        for (InstanceOfNode instanceOf : graph.getNodes().filter(InstanceOfNode.class)) {
            TypeReference typeRef = instanceOf.type();
            if (typeRef != null) {
                if (variableType.isAssignableFrom(typeRef.getType())) {
                    throw new VerificationError("Instanceof check on %s in %s is prohibited as it might skip checks for LIR CastValues. Use LIRValueUtil.isVariable instead.",
                                    variableType.toJavaName(),
                                    method.format("%H.%n(%p)"));
                }
            }
        }
    }
}
