/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.examples.intrinsics;

import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;


public final class SafeAdd extends IntegerArithmetic {
    public SafeAdd(Value x, Value y, Graph graph) {
        super(CiKind.Long, Bytecodes.LADD, x, y, graph);
    }

    @Override
    public Node copy(Graph into) {
        return new SafeAdd(null, null, into);
    }

    @Override
    public String shortName() {
        return "[+]";
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == LIRGenerator.LIRGeneratorOp.class) {
            return (T) GENERATOR_OP;
        }
        return super.lookup(clazz);
    }

    private static final LIRGenerator.LIRGeneratorOp GENERATOR_OP = new LIRGenerator.LIRGeneratorOp() {
        @Override
        public void generate(Node n, LIRGenerator generator) {
            SafeAdd add = (SafeAdd) n;
            generator.arithmeticOpLong(Bytecodes.LADD, generator.createResultVariable(add), generator.load(add.x()), generator.load(add.y()));
            generator.deoptimizeOn(Condition.OF);
        }
    };
}
