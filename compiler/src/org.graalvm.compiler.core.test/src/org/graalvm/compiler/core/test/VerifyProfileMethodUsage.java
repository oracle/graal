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
package org.graalvm.compiler.core.test;

import java.lang.reflect.Method;

import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.ProfileProvider;
import org.graalvm.compiler.nodes.spi.ResolvedJavaMethodProfileProvider;
import org.graalvm.compiler.nodes.spi.StableProfileProvider;
import org.graalvm.compiler.phases.VerifyPhase;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Check that Graal code accesses profile information through a {@link ProfileProvider} so that a
 * compilation can correctly interpose on the reading of it.
 */
public class VerifyProfileMethodUsage extends VerifyPhase<CoreProviders> {
    private static final Method GET_PROFILING_INFO;
    private static final Method GET_PROFILING_INFO_2;
    private static final EconomicSet<Class<?>> ALLOWED_CLASSES = EconomicSet.create();

    static {
        try {
            GET_PROFILING_INFO = ResolvedJavaMethod.class.getDeclaredMethod("getProfilingInfo");
            GET_PROFILING_INFO_2 = ResolvedJavaMethod.class.getDeclaredMethod("getProfilingInfo", boolean.class, boolean.class);
        } catch (NoSuchMethodException e) {
            throw new GraalError(e);
        }

        ALLOWED_CLASSES.add(StableProfileProvider.CachingProfilingInfo.class);
        ALLOWED_CLASSES.add(ResolvedJavaMethodProfileProvider.class);
        ALLOWED_CLASSES.add(ResolvedJavaMethod.class);
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        boolean allowed = false;
        for (Class<?> cls : ALLOWED_CLASSES) {
            ResolvedJavaType declaringClass = graph.method().getDeclaringClass();
            if (context.getMetaAccess().lookupJavaType(cls).isAssignableFrom(declaringClass)) {
                allowed = true;
            }
        }
        if (!allowed) {
            ResolvedJavaMethod getProfilingInfo = context.getMetaAccess().lookupJavaMethod(GET_PROFILING_INFO);
            ResolvedJavaMethod getProfilingInfo2 = context.getMetaAccess().lookupJavaMethod(GET_PROFILING_INFO_2);
            for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
                ResolvedJavaMethod callee = t.targetMethod();
                if (callee.equals(getProfilingInfo) || callee.equals(getProfilingInfo2)) {
                    throw new VerificationError("Profiling information must be accessed through the profile provider in method '%s' of class '%s'..",
                                    graph.method().getName(), graph.method().getDeclaringClass().getName());
                }
            }
        }
    }
}
