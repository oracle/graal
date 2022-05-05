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

import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.options.OptionValues;

import java.util.Collections;
import java.util.Set;

import static java.lang.reflect.Modifier.isStatic;

public abstract class IntegerLowerThanCommonArithmeticTestBase extends GraalCompilerTest {
    protected static final Set<DeoptimizationReason> EMPTY = Collections.<DeoptimizationReason> emptySet();
    private Object[] bindArgs = null;

    @Override
    protected void bindArguments(StructuredGraph graph, Object[] argsToBind) {
        ResolvedJavaMethod m = graph.method();
        Object receiver = isStatic(m.getModifiers()) ? null : this;
        Object[] args = argsWithReceiver(receiver, argsToBind);
        JavaType[] parameterTypes = m.toParameterTypes();
        assert parameterTypes.length == args.length;
        for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
            Object arg = args[param.index()];
            if (arg == NO_BIND) {
                // no binding
            } else if (arg instanceof Stamp) {
                // insert Pi
                ValueNode replacement = graph.addOrUnique(PiNode.create(param, (Stamp) arg));
                if (replacement != param) {
                    param.replaceAtUsages(replacement, n -> n != replacement);
                }
            } else {
                JavaConstant c = getSnippetReflection().forBoxed(parameterTypes[param.index()].getJavaKind(), arg);
                ConstantNode replacement = ConstantNode.forConstant(c, getMetaAccess(), graph);
                param.replaceAtUsages(replacement);
            }
        }
    }

    @Override
    protected Object[] getArgumentToBind() {
        return bindArgs;
    }

    protected abstract Object[] getBindArgs(Object[] args);

    protected void runTest(String name, Object... args) {
        runTest(getInitialOptions(), name, args);
    }

    protected void runTest(OptionValues options, String name, Object... args) {
        runTest(options, EMPTY, name, args);
    }

    protected void runTest(Set<DeoptimizationReason> shouldNotDeopt, String name, Object... args) {
        runTest(getInitialOptions(), shouldNotDeopt, name, args);
    }

    protected void runTest(OptionValues options, Set<DeoptimizationReason> shouldNotDeopt, String name, Object... args) {
        ResolvedJavaMethod method = getResolvedJavaMethod(name);
        Object receiver = method.isStatic() ? null : this;

        Result expect = executeExpected(method, receiver, args);

        testAgainstExpected(options, method, expect, shouldNotDeopt, receiver, args);
        if (args.length > 0) {
            this.bindArgs = getBindArgs(args);
            testAgainstExpected(options, method, expect, shouldNotDeopt, receiver, args);
            this.bindArgs = null;
        }
    }
}
