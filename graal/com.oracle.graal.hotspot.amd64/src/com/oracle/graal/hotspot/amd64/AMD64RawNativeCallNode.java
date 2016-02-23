/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.amd64;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;

import com.oracle.graal.compiler.amd64.AMD64NodeLIRBuilder;
import com.oracle.graal.compiler.common.type.RawPointerStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.NodeInputList;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

@NodeInfo
public final class AMD64RawNativeCallNode extends FixedWithNextNode implements LIRLowerable {
    public static final NodeClass<AMD64RawNativeCallNode> TYPE = NodeClass.create(AMD64RawNativeCallNode.class);

    protected final JavaConstant functionPointer;
    @Input NodeInputList<ValueNode> args;

    public AMD64RawNativeCallNode(JavaKind returnType, JavaConstant functionPointer, ValueNode[] args) {
        super(TYPE, StampFactory.forKind(returnType));
        this.functionPointer = functionPointer;
        this.args = new NodeInputList<>(this, args);
    }

    private static class PointerType implements JavaType {

        public String getName() {
            return "void*";
        }

        public JavaType getComponentType() {
            return null;
        }

        public JavaType getArrayClass() {
            return null;
        }

        public JavaKind getJavaKind() {
            // native pointers and java objects use the same registers in the calling convention
            return JavaKind.Object;
        }

        public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
            return null;
        }
    }

    private static JavaType toJavaType(Stamp stamp, MetaAccessProvider metaAccess) {
        if (stamp instanceof RawPointerStamp) {
            return new PointerType();
        } else {
            return stamp.javaType(metaAccess);
        }
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        AMD64NodeLIRBuilder gen = (AMD64NodeLIRBuilder) generator;
        Value[] parameter = new Value[args.count()];
        JavaType[] parameterTypes = new JavaType[args.count()];
        for (int i = 0; i < args.count(); i++) {
            parameter[i] = generator.operand(args.get(i));
            parameterTypes[i] = toJavaType(args.get(i).stamp(), gen.getLIRGeneratorTool().getMetaAccess());
        }
        JavaType returnType = toJavaType(stamp(), gen.getLIRGeneratorTool().getMetaAccess());
        CallingConvention cc = generator.getLIRGeneratorTool().getCodeCache().getRegisterConfig().getCallingConvention(HotSpotCallingConventionType.NativeCall, returnType, parameterTypes,
                        generator.getLIRGeneratorTool().target());
        gen.getLIRGeneratorTool().emitCCall(functionPointer.asLong(), cc, parameter, countFloatingTypeArguments(args));
        if (this.getStackKind() != JavaKind.Void) {
            generator.setResult(this, gen.getLIRGeneratorTool().emitMove(cc.getReturn()));
        }
    }

    private static int countFloatingTypeArguments(NodeInputList<ValueNode> args) {
        int count = 0;
        for (ValueNode n : args) {
            if (n.getStackKind() == JavaKind.Double || n.getStackKind() == JavaKind.Float) {
                count++;
            }
        }
        if (count > 8) {
            return 8;
        }
        return count;
    }

}
