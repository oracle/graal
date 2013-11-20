/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;

/**
 * Access the value of a specific register.
 */
@NodeInfo(nameTemplate = "ReadRegister %{p#register}")
public final class ReadRegisterNode extends FixedWithNextNode implements LIRGenLowerable {

    /**
     * The fixed register to access.
     */
    private final Register register;

    /**
     * When true, subsequent uses of this node use the fixed register; when false, the value is
     * moved into a new virtual register so that the fixed register is not seen by uses.
     */
    private final boolean directUse;

    /**
     * When true, this node is also an implicit definition of the value for the register allocator,
     * i.e., the register is an implicit incoming value; when false, the register must be defined in
     * the same method or must be an register excluded from register allocation.
     */
    private final boolean incoming;

    public ReadRegisterNode(Register register, Kind kind, boolean directUse, boolean incoming) {
        super(StampFactory.forKind(kind));
        assert register != null;
        this.register = register;
        this.directUse = directUse;
        this.incoming = incoming;
    }

    /**
     * Constructor to be used by node intrinsics where the stamp is inferred from the intrinsic
     * definition.
     */
    public ReadRegisterNode(Register register, boolean directUse, boolean incoming) {
        super(StampFactory.forNodeIntrinsic());
        assert register != null;
        this.register = register;
        this.directUse = directUse;
        this.incoming = incoming;
    }

    @Override
    public void generate(LIRGenerator generator) {
        Value result = register.asValue(kind());
        if (incoming) {
            generator.emitIncomingValues(new Value[]{result});
        }
        if (!directUse) {
            result = generator.emitMove(result);
        }
        generator.setResult(this, result);
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
