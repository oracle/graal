/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.ir;

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.Canonicalizable;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.NotifyReProcess;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

/**
 * The {@code InstanceOf} instruction represents an instanceof test.
 */
public final class InstanceOf extends TypeCheck implements Canonicalizable {

    /**
     * Constructs a new InstanceOf instruction.
     *
     * @param targetClass the target class of the instanceof check
     * @param object the instruction producing the object input to this instruction
     * @param graph
     */
    public InstanceOf(Constant targetClassInstruction, Value object, boolean nullIsTrue, Graph graph) {
        super(targetClassInstruction, object, CiKind.Illegal, graph);
    }

    @Override
    public void accept(ValueVisitor v) {
    }

    @Override
    public void print(LogStream out) {
        out.print("instanceof(").print(object()).print(") ").print(CiUtil.toJavaName(targetClass()));
    }

    @Override
    public Node canonical(NotifyReProcess reProcess) {
        if (object().exactType() != null) {
            return Constant.forBoolean(object().exactType().isSubtypeOf(targetClass()), graph());
        }
        CiConstant constant = object().asConstant();
        if (constant != null) {
            assert constant.kind == CiKind.Object;
            if (constant.isNull()) {
                return Constant.forBoolean(false, graph());
            } else {
                // this should never happen - non-null constants are always expected to provide an exactType
                assert false;
            }
        }
        return this;
    }
}
