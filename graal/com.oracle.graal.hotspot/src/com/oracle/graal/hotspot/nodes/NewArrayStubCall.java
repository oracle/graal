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

import static com.oracle.graal.hotspot.target.amd64.AMD64NewArrayStubCallOp.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.target.amd64.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;

/**
 * Node implementing a call to HotSpot's {@code new_[object|type]_array} stub.
 *
 * @see AMD64NewArrayStubCallOp
 */
public class NewArrayStubCall extends FixedWithNextNode implements LIRGenLowerable {

    private static final Stamp defaultStamp = StampFactory.objectNonNull();

    @Input private final ValueNode hub;
    @Input private final ValueNode length;
    private final boolean isObjectArray;

    public NewArrayStubCall(boolean isObjectArray, ValueNode hub, ValueNode length) {
        super(defaultStamp);
        this.isObjectArray = isObjectArray;
        this.hub = hub;
        this.length = length;
    }

    @Override
    public boolean inferStamp() {
        if (stamp() == defaultStamp && hub.isConstant()) {
            HotSpotKlassOop klassOop = (HotSpotKlassOop) this.hub.asConstant().asObject();
            updateStamp(StampFactory.exactNonNull(klassOop.type));
            return true;
        }
        return false;
    }

    @Override
    public void generate(LIRGenerator gen) {
        RegisterValue hubFixed = HUB.asValue(Kind.Object);
        RegisterValue lengthFixed = LENGTH.asValue(Kind.Int);
        RegisterValue resultFixed = RESULT.asValue(Kind.Object);
        gen.emitMove(gen.operand(length), lengthFixed);
        gen.emitMove(gen.operand(hub), hubFixed);
        LIRFrameState info = gen.state();
        gen.append(new AMD64NewArrayStubCallOp(isObjectArray, resultFixed, hubFixed, lengthFixed, info));
        Variable result = gen.emitMove(resultFixed);
        gen.setResult(this, result);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static Object call(@ConstantNodeParameter boolean isObjectArray, Object hub, int length) {
        throw new UnsupportedOperationException();
    }
}
