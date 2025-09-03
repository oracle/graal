/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test.strings;

import java.util.List;
import java.util.stream.Collectors;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class TStringOpsConstantTest<T extends Node> extends TStringOpsTest<T> {

    Object[] constantArgs;

    final byte[] arrayA;
    final long offsetA;
    final int lengthA;

    protected TStringOpsConstantTest(Class<T> nodeClass, byte[] arrayA, int offsetA, int lengthA) {
        super(nodeClass);
        this.arrayA = arrayA;
        this.offsetA = offsetA + byteArrayBaseOffset();
        this.lengthA = lengthA;
    }

    static List<Object[]> reduceTestData(List<Object[]> data, int argIndex, int... accept) {
        // constant folding tests take much longer than intrinsic stub tests, reduce number of test
        // cases
        return data.stream().filter(args -> {
            int length = (int) args[argIndex];
            for (int i : accept) {
                if (length == i) {
                    return true;
                }
            }
            return false;
        }).collect(Collectors.toList());
    }

    void setConstantArgs(Object... args) {
        constantArgs = args;
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        addConstantParameterBinding(conf, constantArgs);
        return super.editGraphBuilderConfiguration(conf);
    }

    @Override
    protected StructuredGraph parseForCompile(ResolvedJavaMethod method, CompilationIdentifier compilationId, OptionValues options) {
        return makeAllArraysStable(super.parseForCompile(method, compilationId, options));
    }

    @Override
    protected InstalledCode getCode(final ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, boolean ignoreForceCompile, boolean ignoreInstallAsDefault, OptionValues options) {
        // Force recompile if constant binding should be done
        return super.getCode(installedCodeOwner, graph, true, false, options);
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        assertConstantReturnForLength(graph, lengthA);
    }
}
