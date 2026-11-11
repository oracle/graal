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
package jdk.graal.compiler.core.test;

import org.junit.Test;

import jdk.graal.compiler.core.test.VerifyPhase.VerificationError;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.replacements.nodes.IdentityHashCodeNode;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;

public class VerifyIdentityHashCodeUsageTest extends GraalCompilerTest {
    public abstract static class DelegatingReflection implements ConstantReflectionProvider {
        private final ConstantReflectionProvider delegate;

        protected DelegatingReflection(ConstantReflectionProvider delegate) {
            this.delegate = delegate;
        }

        @Override
        public int identityHashCode(JavaConstant constant) {
            return delegate.identityHashCode(constant);
        }
    }

    public static int directQuery(ConstantReflectionProvider reflection, JavaConstant constant) {
        return reflection.identityHashCode(constant);
    }

    public static int concreteProviderQuery(DelegatingReflection reflection, JavaConstant constant) {
        return reflection.identityHashCode(constant);
    }

    @Test(expected = VerificationError.class)
    public void rejectsDirectQuery() {
        verify(parseEager("directQuery", AllowAssumptions.NO));
    }

    @Test(expected = VerificationError.class)
    public void rejectsConcreteProviderQuery() {
        verify(parseEager("concreteProviderQuery", AllowAssumptions.NO));
    }

    @Test
    public void allowsProviderDelegation() {
        verify(parseEager(getResolvedJavaMethod(DelegatingReflection.class, "identityHashCode"), AllowAssumptions.NO));
    }

    @Test
    public void allowsGuardedCanonicalization() {
        verify(parseEager(getResolvedJavaMethod(IdentityHashCodeNode.class, "canonical", ValueNode.class, CoreProviders.class), AllowAssumptions.NO));
    }

    private void verify(StructuredGraph graph) {
        try (DebugCloseable _ = graph.getDebug().disableIntercept()) {
            new VerifyIdentityHashCodeUsage(new CheckGraalInvariants.InvariantsTool()).apply(graph, getProviders());
        }
    }
}
