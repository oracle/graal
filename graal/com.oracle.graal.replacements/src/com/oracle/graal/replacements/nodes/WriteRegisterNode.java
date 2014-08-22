/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.nodes;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Changes the value of a specific register.
 */
@NodeInfo(nameTemplate = "WriteRegister %{p#register}")
public class WriteRegisterNode extends FixedWithNextNode implements LIRLowerable {

    /**
     * The fixed register to access.
     */
    private final Register register;

    /**
     * The new value assigned to the register.
     */
    @Input ValueNode value;

    public static WriteRegisterNode create(Register register, ValueNode value) {
        return new WriteRegisterNodeGen(register, value);
    }

    protected WriteRegisterNode(Register register, ValueNode value) {
        super(StampFactory.forVoid());
        this.register = register;
        this.value = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        Value val = generator.operand(value);
        generator.getLIRGeneratorTool().emitMove(register.asValue(val.getLIRKind()), val);
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(Verbosity.Name) + "%" + register;
        } else {
            return super.toString(verbosity);
        }
    }
}
