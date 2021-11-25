/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.calc;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_32;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.vm.ci.meta.JavaConstant;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.interpreter.value.InterpreterValue;
import org.graalvm.compiler.interpreter.value.InterpreterValuePrimitive;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.util.InterpreterState;

@NodeInfo(cycles = CYCLES_32, size = SIZE_1)
public abstract class IntegerDivRemNode extends FixedBinaryNode implements Lowerable, IterableNodeType {

    public static final NodeClass<IntegerDivRemNode> TYPE = NodeClass.create(IntegerDivRemNode.class);

    public enum Op {
        DIV,
        REM
    }

    public enum Type {
        SIGNED,
        UNSIGNED
    }

    @OptionalInput(InputType.Guard) private GuardingNode zeroCheck;

    private final Op op;
    private final Type type;
    private final boolean canDeopt;

    protected IntegerDivRemNode(NodeClass<? extends IntegerDivRemNode> c, Stamp stamp, Op op, Type type, ValueNode x, ValueNode y, GuardingNode zeroCheck) {
        super(c, stamp, x, y);
        this.zeroCheck = zeroCheck;
        this.op = op;
        this.type = type;

        // Assigning canDeopt during constructor, because it must never change during lifetime of
        // the node.
        IntegerStamp yStamp = (IntegerStamp) getY().stamp(NodeView.DEFAULT);
        this.canDeopt = (yStamp.contains(0) && zeroCheck == null) || yStamp.contains(-1);
    }

    public final GuardingNode getZeroCheck() {
        return zeroCheck;
    }

    public final Op getOp() {
        return op;
    }

    public final Type getType() {
        return type;
    }

    @Override
    public boolean canDeoptimize() {
        return canDeopt;
    }

    @Override
    public FixedNode interpret(InterpreterState interpreter) {
        InterpreterValue divisor = interpreter.interpretExpr(getY());

        GraalError.guarantee(divisor.isPrimitive(), "divisor doesn't interpret to primitive value");
        GraalError.guarantee(divisor.getJavaKind().isNumericInteger(), "divisor doesn't interpret to an integer");
        GraalError.guarantee(!(divisor.asPrimitiveConstant().isDefaultForKind()), "divisor evaluates to 0");

        InterpreterValue dividend = interpreter.interpretExpr(getX());

        GraalError.guarantee(dividend.isPrimitive(), "dividend doesn't interpret to primitive value");
        GraalError.guarantee(dividend.getJavaKind().isNumericInteger(), "dividend doesn't interpret to an integer");

        long result;
        if (getOp() == Op.DIV) {
            if (getType() == Type.SIGNED) {
                result = dividend.asPrimitiveConstant().asLong() / divisor.asPrimitiveConstant().asLong();
            } else { // UNSIGNED
                result = Long.divideUnsigned(dividend.asPrimitiveConstant().asLong(), divisor.asPrimitiveConstant().asLong());
            }
        } else { // REM
            if (getType() == Type.SIGNED) {
                result = dividend.asPrimitiveConstant().asLong() % divisor.asPrimitiveConstant().asLong();
            } else { // UNSIGNED
                result = Long.remainderUnsigned(dividend.asPrimitiveConstant().asLong(), divisor.asPrimitiveConstant().asLong());
            }
        }

        InterpreterValue resultValue = InterpreterValuePrimitive.ofPrimitiveConstant(JavaConstant.forIntegerKind(dividend.getJavaKind(), result));
        interpreter.setNodeLookupValue(this, resultValue);

        return next();
    }
}
