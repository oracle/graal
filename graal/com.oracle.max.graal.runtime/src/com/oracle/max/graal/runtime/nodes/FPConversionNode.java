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

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.gen.LIRGenerator.LIRGeneratorOp;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;


public final class FPConversionNode extends FloatingNode implements Node.GlobalValueNumberable {
    @Input private Value value;

    public Value value() {
        return value;
    }

    public void setValue(Value x) {
        updateUsages(value, x);
        value = x;
    }

    public FPConversionNode(CiKind kind, Value value, Graph graph) {
        super(kind, graph);
        this.setValue(value);
    }


    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == LIRGeneratorOp.class) {
            return (T) LIRGEN;
        }
        if (clazz == CanonicalizerOp.class) {
            return (T) CANON;
        }
        return super.lookup(clazz);
    }

    @Override
    public void print(LogStream out) {
        out.print("fp conversion node ").print(value());
    }

    private static final CanonicalizerOp CANON = new CanonicalizerOp() {
        @Override
        public Node canonical(Node node, NotifyReProcess reProcess) {
            FPConversionNode conv = (FPConversionNode) node;
            Value value = conv.value();
            if (value instanceof Constant) {
                CiKind toKind = conv.kind;
                CiKind fromKind = value.kind;
                if (toKind == CiKind.Int && fromKind == CiKind.Float) {
                    return Constant.forInt(Float.floatToRawIntBits(((Constant) value).asConstant().asFloat()), node.graph());
                } else if (toKind == CiKind.Long && fromKind == CiKind.Double) {
                    return Constant.forLong(Double.doubleToRawLongBits(((Constant) value).asConstant().asDouble()), node.graph());
                } else if (toKind == CiKind.Float && fromKind == CiKind.Int) {
                    return Constant.forFloat(Float.intBitsToFloat(((Constant) value).asConstant().asInt()), node.graph());
                } else if (toKind == CiKind.Double && fromKind == CiKind.Long) {
                    return Constant.forDouble(Double.longBitsToDouble(((Constant) value).asConstant().asLong()), node.graph());
                }
            }
            return conv;
        }
    };

    private static final LIRGeneratorOp LIRGEN = new LIRGeneratorOp() {
        @Override
        public void generate(Node n, LIRGenerator generator) {
            FPConversionNode conv = (FPConversionNode) n;
            CiValue reg = generator.createResultVariable(conv);
            CiValue value = generator.load(conv.value());
            CiValue tmp = generator.forceToSpill(value, conv.kind, false);
            generator.lir().move(tmp, reg);
        }
    };
}
