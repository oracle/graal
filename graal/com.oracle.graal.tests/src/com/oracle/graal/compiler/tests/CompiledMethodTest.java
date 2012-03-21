/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.compiler.tests;

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.cri.ri.RiCompiledMethod.MethodInvalidatedException;

/**
 * In the following tests, the usages of local variable "a" are replaced with the integer constant 0. Then
 * canonicalization is applied and it is verified that the resulting graph is equal to the graph of the method that just
 * has a "return 1" statement in it.
 */
public class CompiledMethodTest extends GraphTest {

    public static Object testMethod(Object arg1, Object arg2, Object arg3) {
        return arg1 + " " + arg2 + " " + arg3;
    }

    @Test
    public void test1() {
        Method method = getMethod("testMethod");
        final StructuredGraph graph = parse(method);
        new CanonicalizerPhase(null, runtime(), null).apply(graph);
        new DeadCodeEliminationPhase().apply(graph);

        for (Node node : graph.getNodes()) {
            if (node instanceof ConstantNode) {
                ConstantNode constant = (ConstantNode) node;
                if (constant.kind() == CiKind.Object && " ".equals(constant.value.asObject())) {
                    graph.replaceFloating(constant, ConstantNode.forObject("-", runtime, graph));
                }
            }
        }

        final RiResolvedMethod riMethod = runtime.getRiMethod(method);
        CiTargetMethod targetMethod = runtime.compile(riMethod, graph);
        RiCompiledMethod compiledMethod = runtime.addMethod(riMethod, targetMethod);
        try {
            Object result = compiledMethod.execute("1", "2", "3");
            Assert.assertEquals("1-2-3", result);
        } catch (MethodInvalidatedException t) {
            Assert.fail("method invalidated");
        }

    }

    @Test
    public void test2() throws NoSuchMethodException, SecurityException {
        Method method = CompilableObjectImpl.class.getDeclaredMethod("executeHelper", ObjectCompiler.class, String.class);
        RiResolvedMethod riMethod = runtime.getRiMethod(method);
        StructuredGraph graph = new StructuredGraph(riMethod);
        new GraphBuilderPhase(runtime, GraphBuilderConfiguration.getSnippetDefault(), OptimisticOptimizations.NONE).apply(graph);
        new CanonicalizerPhase(null, runtime, null).apply(graph);
        new DeadCodeEliminationPhase().apply(graph);

        for (Node node : graph.getNodes()) {
            if (node instanceof ConstantNode) {
                ConstantNode constant = (ConstantNode) node;
                if (constant.kind() == CiKind.Object && "1 ".equals(constant.value.asObject())) {
                    graph.replaceFloating(constant, ConstantNode.forObject("1-", runtime, graph));
                }
            }
        }

        CiTargetMethod targetMethod = runtime.compile(riMethod, graph);
        final RiCompiledMethod compiledMethod = runtime.addMethod(riMethod, targetMethod);

        final CompilableObject compilableObject = new CompilableObjectImpl(0);

        Object result;
        result = compilableObject.execute(new ObjectCompilerImpl(compiledMethod), "3");
        Assert.assertEquals("1-3", result);
    }

    public abstract class CompilableObject {

        private CompiledObject compiledObject;
        private final int compileThreshold;
        private int counter;

        public CompilableObject(int compileThreshold) {
            this.compileThreshold = compileThreshold;
        }

        public final Object execute(ObjectCompiler compiler, String args) {
            if (counter++ < compileThreshold || compiler == null) {
                return executeHelper(compiler, args);
            } else {
                compiledObject = compiler.compile(this);
                return compiledObject.execute(compiler, args);
            }
        }

        protected abstract Object executeHelper(ObjectCompiler context, String args);
    }

    private final class CompilableObjectImpl extends CompilableObject {

        private CompilableObjectImpl(int compileThreshold) {
            super(compileThreshold);
        }

        @Override
        protected Object executeHelper(ObjectCompiler compiler, String args) {
            return "1 " + args;
        }
    }

    public interface CompiledObject {
        Object execute(ObjectCompiler context, String args);
    }

    public interface ObjectCompiler {
        CompiledObject compile(CompilableObject node);
    }

    private final class ObjectCompilerImpl implements ObjectCompiler {

        private final RiCompiledMethod compiledMethod;

        private ObjectCompilerImpl(RiCompiledMethod compiledMethod) {
            this.compiledMethod = compiledMethod;
        }

        @Override
        public CompiledObject compile(final CompilableObject node) {
            return new CompiledObject() {
                @Override
                public Object execute(ObjectCompiler compiler, String args) {
                    return compiledMethod.execute(node, compiler, args);
                }
            };
        }
    }
}
