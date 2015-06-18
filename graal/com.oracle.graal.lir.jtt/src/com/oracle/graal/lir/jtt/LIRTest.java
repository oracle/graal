/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.jtt;

import static com.oracle.graal.lir.LIRValueUtil.*;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.stream.*;

import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.jtt.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.jvmci.meta.*;

/**
 * Base class for LIR tests.
 * <p>
 * It provides facilities to replace methods with {@link LIRTestSpecification arbitrary LIR
 * instructions}.
 */
public abstract class LIRTest extends JTTTest {

    @NodeInfo
    private static final class LIRTestNode extends FixedWithNextNode implements LIRLowerable {

        public static final NodeClass<LIRTestNode> TYPE = NodeClass.create(LIRTestNode.class);
        @Input protected ValueNode opsNode;
        @Input protected NodeInputList<ValueNode> values;
        public final SnippetReflectionProvider snippetReflection;

        public LIRTestNode(SnippetReflectionProvider snippetReflection, Kind kind, ValueNode opsNode, ValueNode[] values) {
            super(TYPE, StampFactory.forKind(kind));
            this.opsNode = opsNode;
            this.values = new NodeInputList<>(this, values);
            this.snippetReflection = snippetReflection;
        }

        public NodeInputList<ValueNode> values() {
            return values;
        }

        public ValueNode getLIROpsNode() {
            return opsNode;
        }

        @Override
        public void generate(NodeLIRBuilderTool gen) {
            LIRTestSpecification ops = getLIROperations();
            Stream<Value> v = values().stream().map(node -> gen.operand(node));

            ops.generate(gen.getLIRGeneratorTool(), v.toArray(size -> new Value[size]));
            gen.setResult(this, ops.getResult());
        }

        public LIRTestSpecification getLIROperations() {
            assert getLIROpsNode().isConstant();
            LIRTestSpecification spec = snippetReflection.asObject(LIRTestSpecification.class, getLIROpsNode().asJavaConstant());
            return spec;
        }
    }

    @NodeInfo
    private static final class LIRValueNode extends FixedWithNextNode implements LIRLowerable {

        public static final NodeClass<LIRValueNode> TYPE = NodeClass.create(LIRValueNode.class);
        @Input protected ValueNode opsNode;
        @Input protected ValueNode name;
        public final SnippetReflectionProvider snippetReflection;

        public LIRValueNode(SnippetReflectionProvider snippetReflection, Kind kind, ValueNode opsNode, ValueNode name) {
            super(TYPE, StampFactory.forKind(kind));
            this.opsNode = opsNode;
            this.name = name;
            this.snippetReflection = snippetReflection;
        }

        public ValueNode getLIROpsNode() {
            return opsNode;
        }

        @Override
        public void generate(NodeLIRBuilderTool gen) {
            LIRTestSpecification spec = getLIROperations();
            Value output = spec.getOutput(getName());
            gen.setResult(this, isVariable(output) ? output : gen.getLIRGeneratorTool().emitMove(output));
        }

        private String getName() {
            assert name.isConstant();
            return snippetReflection.asObject(String.class, name.asJavaConstant());
        }

        private LIRTestSpecification getLIROperations() {
            assert getLIROpsNode().isConstant();
            return snippetReflection.asObject(LIRTestSpecification.class, getLIROpsNode().asJavaConstant());
        }

    }

    private InvocationPlugin lirTestPlugin = new InvocationPlugin() {
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec) {
            Kind returnKind = targetMethod.getSignature().getReturnKind();
            b.addPush(returnKind, new LIRTestNode(getSnippetReflection(), returnKind, spec, new ValueNode[]{}));
            return true;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec, ValueNode arg0) {
            Kind returnKind = targetMethod.getSignature().getReturnKind();
            b.addPush(returnKind, new LIRTestNode(getSnippetReflection(), returnKind, spec, new ValueNode[]{arg0}));
            return true;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec, ValueNode arg0, ValueNode arg1) {
            Kind returnKind = targetMethod.getSignature().getReturnKind();
            b.addPush(returnKind, new LIRTestNode(getSnippetReflection(), returnKind, spec, new ValueNode[]{arg0, arg1}));
            return true;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec, ValueNode arg0, ValueNode arg1, ValueNode arg2) {
            Kind returnKind = targetMethod.getSignature().getReturnKind();
            b.addPush(returnKind, new LIRTestNode(getSnippetReflection(), returnKind, spec, new ValueNode[]{arg0, arg1, arg2}));
            return true;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec, ValueNode arg0, ValueNode arg1, ValueNode arg2, ValueNode arg3) {
            Kind returnKind = targetMethod.getSignature().getReturnKind();
            b.addPush(returnKind, new LIRTestNode(getSnippetReflection(), returnKind, spec, new ValueNode[]{arg0, arg1, arg2, arg3}));
            return true;
        }

    };

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        InvocationPlugins invocationPlugins = conf.getPlugins().getInvocationPlugins();

        Class<? extends LIRTest> c = getClass();
        for (Method m : c.getMethods()) {
            if (m.getAnnotation(LIRIntrinsic.class) != null) {
                assert Modifier.isStatic(m.getModifiers());
                Class<?>[] p = m.getParameterTypes();
                assert p.length > 0;
                assert LIRTestSpecification.class.isAssignableFrom(p[0]);

                invocationPlugins.register(lirTestPlugin, c, m.getName(), p);
            }
        }
        InvocationPlugin outputPlugin = new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec, ValueNode name, ValueNode expected) {
                Kind returnKind = targetMethod.getSignature().getReturnKind();
                b.addPush(returnKind, new LIRValueNode(getSnippetReflection(), returnKind, spec, name));
                return true;
            }
        };
        invocationPlugins.register(outputPlugin, LIRTest.class, "getOutput", new Class<?>[]{LIRTestSpecification.class, String.class, Object.class});
        invocationPlugins.register(outputPlugin, LIRTest.class, "getOutput", new Class<?>[]{LIRTestSpecification.class, String.class, int.class});
        return super.editGraphBuilderConfiguration(conf);
    }

    @SuppressWarnings("unused")
    public static byte getOutput(LIRTestSpecification spec, String name, byte expected) {
        return expected;
    }

    @SuppressWarnings("unused")
    public static short getOutput(LIRTestSpecification spec, String name, short expected) {
        return expected;
    }

    @SuppressWarnings("unused")
    public static int getOutput(LIRTestSpecification spec, String name, int expected) {
        return expected;
    }

    @SuppressWarnings("unused")
    public static long getOutput(LIRTestSpecification spec, String name, long expected) {
        return expected;
    }

    @SuppressWarnings("unused")
    public static float getOutput(LIRTestSpecification spec, String name, float expected) {
        return expected;
    }

    @SuppressWarnings("unused")
    public static double getOutput(LIRTestSpecification spec, String name, double expected) {
        return expected;
    }

    @SuppressWarnings("unused")
    public static Object getOutput(LIRTestSpecification spec, String name, Object expected) {
        return expected;
    }

    @java.lang.annotation.Retention(RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(ElementType.METHOD)
    public static @interface LIRIntrinsic {
    }

}
