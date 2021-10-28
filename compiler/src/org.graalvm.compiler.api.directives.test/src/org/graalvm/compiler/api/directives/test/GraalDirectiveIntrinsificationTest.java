/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.api.directives.test;

import java.util.List;

import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Binding;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Test to ensure that all directives in {@link GraalDirectives} are intrinsified, even trivial
 * directives expressed in terms of others. We don't want to rely on bytecode parser inlining to
 * ensure directive semantics.
 */
public class GraalDirectiveIntrinsificationTest extends GraalCompilerTest {

    @Test
    public void ensureAllGraalDirectivesIntrinsified() {
        String className = MetaUtil.toInternalName(GraalDirectives.class.getName());
        List<Binding> bindingList = getReplacements().getGraphBuilderPlugins().getInvocationPlugins().getBindings(false).get(className);

        EconomicSet<String> registeredBindings = EconomicSet.create();
        for (Binding b : bindingList) {
            // A binding's string representation includes the name and arguments but no return type.
            registeredBindings.add(b.toString());
        }

        ResolvedJavaType directives = getMetaAccess().lookupJavaType(GraalDirectives.class);
        for (ResolvedJavaMethod method : directives.getDeclaredMethods()) {
            if (method.isStatic()) {
                // A method's descriptor includes the return type, which we must drop so we can
                // compare to the binding strings.
                String fullName = method.getName() + method.getSignature().toMethodDescriptor();
                String bindingName = fullName.substring(0, fullName.lastIndexOf(method.getSignature().getReturnType(null).getName()));

                Assert.assertTrue("Graal directive " + method.format("%h.%n(%p)") + " must be intrinsified with a standard graph builder plugin", registeredBindings.contains(bindingName));
            }
        }
    }
}
