/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.test;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.oracle.svm.hosted.image.NativeImageHeap;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.test.VerifyIdentityHashCodeUsage;
import jdk.graal.compiler.core.test.VerifyPhase;
import jdk.graal.compiler.core.test.VerifyPhase.VerificationError;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;

/** Checks that Native Image's non-folding exceptions leave graph transformations protected. */
public class VerifySVMIdentityHashCodeUsageTest extends GraalCompilerTest {
    public static ConstantNode foldIdentityHashCode(ConstantReflectionProvider reflection, JavaConstant constant) {
        return ConstantNode.forInt(reflection.identityHashCode(constant));
    }

    @Test
    public void allowsImageHeapIdentityQuery() {
        verify(parseEager(getResolvedJavaMethod(NativeImageHeap.class, "computeIdentityHashCode"), AllowAssumptions.NO));
    }

    @Test(expected = VerificationError.class)
    public void rejectsGraphFoldingBypass() {
        verify(parseEager("foldIdentityHashCode", AllowAssumptions.NO));
    }

    @Test(expected = VerificationError.class)
    public void rejectsOtherImageHeapMethods() {
        StructuredGraph graph = parseEager("foldIdentityHashCode", AllowAssumptions.NO);
        // Model the same bypass added elsewhere in a class with a legitimate identity query.
        verify(graph.copy(getResolvedJavaMethod(NativeImageHeap.class, "getConstantInfo"), graph.getOptions(), graph.getDebug(), false));
    }

    private void verify(StructuredGraph graph) {
        var tool = new CheckSVMInvariants().new SVMInvariantsTool();
        var verifier = new VerifyIdentityHashCodeUsage(tool);
        List<VerifyPhase<CoreProviders>> verifiers = new ArrayList<>(List.of(verifier));
        tool.updateVerifiers(verifiers);
        assertTrue("Native Image must retain the identity-hash verifier", verifiers.contains(verifier));
        try (DebugCloseable _ = graph.getDebug().disableIntercept()) {
            verifier.apply(graph, getProviders());
        }
    }
}
