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

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.stream.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.jtt.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.jvmci.common.*;

/**
 * Base class for LIR tests.
 * <p>
 * It provides facilities to replace methods with {@link LIRTestSpecification arbitrary LIR
 * instructions}.
 */
public abstract class LIRTest extends JTTTest {

    public abstract static class LIRTestSpecification {
        private Value result;

        public void generate(LIRGeneratorTool gen) {
            defaultHandler(gen);
        }

        public void generate(LIRGeneratorTool gen, Value arg0) {
            defaultHandler(gen, arg0);
        }

        public void generate(LIRGeneratorTool gen, Value arg0, Value arg1) {
            defaultHandler(gen, arg0, arg1);
        }

        public void generate(LIRGeneratorTool gen, Value arg0, Value arg1, Value arg2) {
            defaultHandler(gen, arg0, arg1, arg2);
        }

        public void generate(LIRGeneratorTool gen, Value arg0, Value arg1, Value arg2, Value arg3) {
            defaultHandler(gen, arg0, arg1, arg2, arg3);
        }

        public void generate(LIRGeneratorTool gen, Value arg0, Value arg1, Value arg2, Value arg3, Value arg4) {
            defaultHandler(gen, arg0, arg1, arg2, arg3, arg4);
        }

        private static void defaultHandler(@SuppressWarnings("unused") LIRGeneratorTool gen, Value... args) {
            throw new JVMCIError("LIRTestSpecification cannot handle generate() with %d arguments", args.length);
        }

        void generate(LIRGeneratorTool gen, Value[] values) {
            if (values.length == 0) {
                generate(gen);
            } else if (values.length == 1) {
                generate(gen, values[0]);
            } else if (values.length == 2) {
                generate(gen, values[0], values[1]);
            } else if (values.length == 3) {
                generate(gen, values[0], values[1], values[2]);
            } else if (values.length == 4) {
                generate(gen, values[0], values[1], values[2], values[3]);
            } else if (values.length == 5) {
                generate(gen, values[0], values[1], values[2], values[3], values[4]);
            } else {
                JVMCIError.unimplemented();
            }

        }

        public void setResult(Value value) {
            result = value;
        }

        public Value getResult() {
            assert result != null : "Result not set (using setResult())";
            return result;
        }
    }

    @NodeInfo
    private static final class FixedLIRTestNode extends FixedWithNextNode implements LIRLowerable {

        public static final NodeClass<FixedLIRTestNode> TYPE = NodeClass.create(FixedLIRTestNode.class);
        @Input protected ValueNode opsNode;
        @Input protected NodeInputList<ValueNode> values;
        public final SnippetReflectionProvider snippetReflection;

        public FixedLIRTestNode(SnippetReflectionProvider snippetReflection, ValueNode opsNode, ValueNode[] values) {
            super(TYPE, StampFactory.forVoid());
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
        }

        private LIRTestSpecification getLIROperations() {
            assert getLIROpsNode().isConstant();
            LIRTestSpecification spec = snippetReflection.asObject(LIRTestSpecification.class, getLIROpsNode().asJavaConstant());
            return spec;
        }
    }

    @NodeInfo
    private static final class FloatingLIRTestNode extends FloatingNode implements LIRLowerable {

        public static final NodeClass<FloatingLIRTestNode> TYPE = NodeClass.create(FloatingLIRTestNode.class);
        @Input protected ValueNode opsNode;
        @Input protected NodeInputList<ValueNode> values;
        public final SnippetReflectionProvider snippetReflection;

        public FloatingLIRTestNode(SnippetReflectionProvider snippetReflection, Kind kind, ValueNode opsNode, ValueNode[] values) {
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

        private LIRTestSpecification getLIROperations() {
            assert getLIROpsNode().isConstant();
            LIRTestSpecification spec = snippetReflection.asObject(LIRTestSpecification.class, getLIROpsNode().asJavaConstant());
            return spec;
        }
    }

    private InvocationPlugin fixedLIRNodePlugin = new InvocationPlugin() {
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec) {
            b.add(new FixedLIRTestNode(getSnippetReflection(), spec, new ValueNode[]{}));
            return true;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec, ValueNode arg0) {
            b.add(new FixedLIRTestNode(getSnippetReflection(), spec, new ValueNode[]{arg0}));
            return true;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec, ValueNode arg0, ValueNode arg1) {
            b.add(new FixedLIRTestNode(getSnippetReflection(), spec, new ValueNode[]{arg0, arg1}));
            return true;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec, ValueNode arg0, ValueNode arg1, ValueNode arg2) {
            b.add(new FixedLIRTestNode(getSnippetReflection(), spec, new ValueNode[]{arg0, arg1, arg2}));
            return true;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec, ValueNode arg0, ValueNode arg1, ValueNode arg2, ValueNode arg3) {
            b.add(new FixedLIRTestNode(getSnippetReflection(), spec, new ValueNode[]{arg0, arg1, arg2, arg3}));
            return true;
        }
    };

    private InvocationPlugin floatingLIRNodePlugin = new InvocationPlugin() {
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec) {
            b.addPush(new FloatingLIRTestNode(getSnippetReflection(), targetMethod.getSignature().getReturnKind(), spec, new ValueNode[]{}));
            return true;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec, ValueNode arg0) {
            b.addPush(new FloatingLIRTestNode(getSnippetReflection(), targetMethod.getSignature().getReturnKind(), spec, new ValueNode[]{arg0}));
            return true;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec, ValueNode arg0, ValueNode arg1) {
            b.addPush(new FloatingLIRTestNode(getSnippetReflection(), targetMethod.getSignature().getReturnKind(), spec, new ValueNode[]{arg0, arg1}));
            return true;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec, ValueNode arg0, ValueNode arg1, ValueNode arg2) {
            b.addPush(new FloatingLIRTestNode(getSnippetReflection(), targetMethod.getSignature().getReturnKind(), spec, new ValueNode[]{arg0, arg1, arg2}));
            return true;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode spec, ValueNode arg0, ValueNode arg1, ValueNode arg2, ValueNode arg3) {
            b.addPush(new FloatingLIRTestNode(getSnippetReflection(), targetMethod.getSignature().getReturnKind(), spec, new ValueNode[]{arg0, arg1, arg2, arg3}));
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

                if (m.getReturnType().equals(void.class)) {
                    invocationPlugins.register(fixedLIRNodePlugin, c, m.getName(), p);
                } else {
                    invocationPlugins.register(floatingLIRNodePlugin, c, m.getName(), p);
                }
            }
        }
        return super.editGraphBuilderConfiguration(conf);
    }

    @java.lang.annotation.Retention(RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(ElementType.METHOD)
    public static @interface LIRIntrinsic {
    }

}
