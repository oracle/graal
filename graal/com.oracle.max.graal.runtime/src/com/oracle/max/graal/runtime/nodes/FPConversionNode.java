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
package com.oracle.max.graal.runtime.nodes;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;


public final class FPConversionNode extends FloatingNode implements Canonicalizable {

    @Input private ValueNode value;

    public ValueNode value() {
        return value;
    }

    public void setValue(ValueNode x) {
        updateUsages(value, x);
        value = x;
    }

    public FPConversionNode(CiKind kind, ValueNode value, Graph graph) {
        super(kind, graph);
        this.setValue(value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == LIRGeneratorOp.class) {
            return (T) LIRGEN;
        }
        return super.lookup(clazz);
    }

    private static final LIRGeneratorOp LIRGEN = new LIRGeneratorOp() {

        @Override
        public void generate(Node n, LIRGeneratorTool generator) {
            FPConversionNode conv = (FPConversionNode) n;
            CiValue reg = generator.createResultVariable(conv);
            CiValue value = generator.load(conv.value());
            CiValue tmp = generator.forceToSpill(value, conv.kind, false);
            generator.emitMove(tmp, reg);
        }
    };

    @Override
    public Node canonical(NotifyReProcess reProcess) {
        if (value instanceof ConstantNode) {
            CiKind fromKind = value.kind;
            if (kind == CiKind.Int && fromKind == CiKind.Float) {
                return ConstantNode.forInt(Float.floatToRawIntBits(((ConstantNode) value).asConstant().asFloat()), graph());
            } else if (kind == CiKind.Long && fromKind == CiKind.Double) {
                return ConstantNode.forLong(Double.doubleToRawLongBits(((ConstantNode) value).asConstant().asDouble()), graph());
            } else if (kind == CiKind.Float && fromKind == CiKind.Int) {
                return ConstantNode.forFloat(Float.intBitsToFloat(((ConstantNode) value).asConstant().asInt()), graph());
            } else if (kind == CiKind.Double && fromKind == CiKind.Long) {
                return ConstantNode.forDouble(Double.longBitsToDouble(((ConstantNode) value).asConstant().asLong()), graph());
            }
        }
        return this;
    }
}
