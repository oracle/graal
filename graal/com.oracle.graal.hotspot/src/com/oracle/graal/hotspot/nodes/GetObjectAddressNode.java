/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Intrinsification for getting the address of an object. The code path(s) between a call to
 * {@link #get(Object)} and all uses of the returned value must be atomic. The only exception to
 * this is if the usage is not an attempt to dereference the value.
 */
@NodeInfo
public class GetObjectAddressNode extends FixedWithNextNode implements LIRLowerable {

    @Input private ValueNode object;

    public static GetObjectAddressNode create(ValueNode obj) {
        return new GetObjectAddressNodeGen(obj);
    }

    protected GetObjectAddressNode(ValueNode obj) {
        super(StampFactory.forKind(Kind.Long));
        this.object = obj;
    }

    @NodeIntrinsic
    public static native long get(Object array);

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        AllocatableValue obj = gen.getLIRGeneratorTool().newVariable(LIRKind.derivedReference(gen.getLIRGeneratorTool().target().wordKind));
        gen.getLIRGeneratorTool().emitMove(obj, gen.operand(object));
        gen.setResult(this, obj);
    }
}
