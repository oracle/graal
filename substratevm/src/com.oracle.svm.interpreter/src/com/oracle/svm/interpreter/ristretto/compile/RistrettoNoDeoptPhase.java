/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.compile;

import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractDeoptimizeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.Phase;

/**
 * Phase that analyzes the graph and throws a compilation bailout if {@link AbstractDeoptimizeNode}
 * are found.
 */
public class RistrettoNoDeoptPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        for (Node n : graph.getNodes()) {
            if (n instanceof AbstractDeoptimizeNode deopt) {
                /*
                 * TODO GR-72047 - this will be a non permanent bailout until ristretto support
                 * permanent bailouts
                 */
                throw new PermanentBailoutException("Ristretto must not use deoptimizations when -H:JITUseDeoptimization==false, but found node " + n);
            }
        }
    }
}
