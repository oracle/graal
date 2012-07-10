/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Access the value of a specific register.
 */
@NodeInfo(nameTemplate = "Register %{p#register}")
public final class RegisterNode extends FixedWithNextNode implements LIRLowerable {

    private final Register register;

    public RegisterNode(Register register, Kind kind) {
        super(StampFactory.forKind(kind));
        this.register = register;
    }

    @Override
    public void generate(LIRGeneratorTool generator) {
        Value result;
        if (generator.attributes(register).isAllocatable()) {
            // The register allocator would prefer us not to tie up an allocatable
            // register for the complete lifetime of this node.
            result = generator.newVariable(kind());
            generator.emitMove(register.asValue(kind()), result);
        } else {
            result = register.asValue(kind());
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

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static <T> T register(@ConstantNodeParameter Register register, @ConstantNodeParameter Kind kind) {
        throw new UnsupportedOperationException();
    }
}
