/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.VerifyPhase;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Checks that {@code sun.misc.Unsafe} is never used.
 */
public class VerifyUnsafeAccess extends VerifyPhase<CoreProviders> {
    private static final Class<?> UNSAFE_CLASS;

    static {
        try {
            UNSAFE_CLASS = Class.forName("sun.misc.Unsafe");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        MetaAccessProvider metaAccess = context.getMetaAccess();
        final ResolvedJavaType unsafeType = metaAccess.lookupJavaType(UNSAFE_CLASS);

        ResolvedJavaMethod caller = graph.method();

        if (caller.getSignature().getReturnType(caller.getDeclaringClass()).equals(unsafeType)) {
            throw new VerificationError("Returning sun.misc.Unsafe at callsite %s is prohibited.", caller.format("%H.%n(%p)"));
        }

        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            if (callee.getDeclaringClass().equals(unsafeType)) {
                throw new VerificationError("Call to %s at callsite %s is prohibited.",
                                callee.format("%H.%n(%p)"),
                                caller.format("%H.%n(%p)"));
            }
        }
    }

    @Override
    public void verifyClass(Class<?> c, MetaAccessProvider metaAccess) {
        for (Field field : c.getDeclaredFields()) {
            if (field.getType() == UNSAFE_CLASS) {
                throw new VerificationError("Field of type sun.misc.Unsafe at callsite %s is prohibited.", field);
            }
        }
    }
}
