/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.test.VerifyPhase;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class VerifyRistrettoInvariants extends VerifyPhase<CoreProviders> {

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        ResolvedJavaMethod method = graph.method();
        if (method == null) {
            return;
        }
        if (method.getDeclaringClass().getName().toLowerCase().contains("ristretto")) {
            for (Node n : graph.getNodes()) {
                if (n instanceof Invoke invoke) {
                    if (invoke.getTargetMethod().getDeclaringClass().getName().toLowerCase().contains("truffle")) {
                        ResolvedJavaMethod caller = graph.method();
                        ResolvedJavaMethod callee = invoke.callTarget().targetMethod();
                        throw new VerificationError("Call to %s at callsite %s is prohibited, ristretto JIT must not have truffle dependencies",
                                        callee.format("%H.%n(%p)"),
                                        caller.format("%H.%n(%p)"));
                    }
                }
            }
        }
    }
}
