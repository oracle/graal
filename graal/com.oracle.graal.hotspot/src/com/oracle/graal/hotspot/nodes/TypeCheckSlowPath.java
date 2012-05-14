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

import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.hotspot.target.amd64.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.max.cri.ci.*;

/**
 * A node for calling the HotSpot stub implementing the slow path of a type check.
 * This stub does not use any registers.
 */
public final class TypeCheckSlowPath extends FloatingNode implements LIRGenLowerable {

    @Input private ValueNode objectHub;
    @Input private ValueNode hub;

    public ValueNode objectHub() {
        return objectHub;
    }

    public ValueNode hub() {
        return hub;
    }

    public TypeCheckSlowPath(ValueNode objectHub, ValueNode hub) {
        super(StampFactory.forKind(CiKind.Boolean));
        this.objectHub = objectHub;
        this.hub = hub;
    }

    @Override
    public void generate(LIRGenerator gen) {
        CiValue objectHubOpr = gen.operand(objectHub);
        Variable result = gen.newVariable(CiKind.Boolean);
        AMD64TypeCheckSlowPathOp op = new AMD64TypeCheckSlowPathOp(result, objectHubOpr, gen.operand(hub));
        gen.append(op);
        gen.setResult(this, result);
    }

    /**
     * Checks if {@code objectHub} is a subclass of {@code hub}.
     *
     * @return {@code true} if {@code objectHub} is a subclass of {@code hub}, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static boolean check(Object objectHub, Object hub) {
        throw new UnsupportedOperationException("This method may only be compiled with the Graal compiler");
    }


}
