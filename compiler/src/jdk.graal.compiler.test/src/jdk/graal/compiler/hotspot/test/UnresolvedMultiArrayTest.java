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

package jdk.graal.compiler.hotspot.test;

import org.junit.Test;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests parsing unresolved reference array allocations with multiple dimensions.
 */
public class UnresolvedMultiArrayTest extends GraalCompilerTest {

    static final class MultiArrayElement {
    }

    public static Object unresolvedMultiArraySnippet(boolean allocate) {
        if (allocate) {
            return new MultiArrayElement[1][2][2];
        }
        return null;
    }

    @Test
    public void unresolvedMultiArrayWithSpeculationLog() {
        test("unresolvedMultiArraySnippet", false);
    }

    /**
     * Uses the default graph builder configuration so the allocation remains unresolved after the
     * interpreter has only executed the {@code false} path.
     */
    @Override
    protected StructuredGraph parseForCompile(ResolvedJavaMethod method, CompilationIdentifier compilationId, OptionValues options) {
        return parse(builder(method, AllowAssumptions.YES, compilationId, options), getDefaultGraphBuilderSuite());
    }
}
