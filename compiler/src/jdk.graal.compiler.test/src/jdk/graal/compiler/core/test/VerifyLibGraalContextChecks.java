/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.VerifyPhase;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.services.Services;

/**
 * Checks invariants related to libgraal. This includes:
 * <ul>
 * <li>no code accesses {@link jdk.vm.ci.services.Services#IS_IN_NATIVE_IMAGE}</li>
 * <li>no calls to Runtime.version() as libgraal might be deployed in different JDK version than the
 * one in which it was built</li>
 * </ul>
 */
public class VerifyLibGraalContextChecks extends VerifyPhase<CoreProviders> {

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        MetaAccessProvider metaAccess = context.getMetaAccess();

        final ResolvedJavaType servicesType = metaAccess.lookupJavaType(Services.class);
        servicesType.getStaticFields();

        List<LoadFieldNode> loads = graph.getNodes().filter(LoadFieldNode.class).snapshot();
        for (LoadFieldNode load : loads) {
            ResolvedJavaField field = load.field();
            if (field.getDeclaringClass().toJavaName().equals(Services.class.getName())) {
                if (field.getName().equals("IS_IN_NATIVE_IMAGE")) {
                    throw new VerificationError("reading %s in %s is prohibited - use %s.isInLibgraal() instead",
                                    field.format("%H.%n"),
                                    graph.method().format("%H.%n(%p)"),
                                    GraalServices.class.getName());

                }
            }
        }

        ResolvedJavaType runtimeType = metaAccess.lookupJavaType(Runtime.class);
        ResolvedJavaType runtimeVersionType = metaAccess.lookupJavaType(Runtime.Version.class);
        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            String calleeName = callee.getName();
            ResolvedJavaType calleeDeclaringClass = callee.getDeclaringClass();
            if (calleeDeclaringClass.equals(runtimeType)) {
                if (calleeName.equals("version")) {
                    throw new VerificationError(t, "call to Runtime.version() not allowed");
                }
            }
            if (runtimeVersionType.equals(callee.getDeclaringClass()) && "feature".equals(callee.getName())) {
                throw new VerificationError("Call to %s at callsite %s is prohibited, use JavaVersionUtil.JAVA_SPEC", callee.format("%H.%n(%p)"), graph.method().format("%H.%n(%p)"));
            }
        }
    }
}
