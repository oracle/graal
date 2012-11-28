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

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.RuntimeCall.Descriptor;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.snippets.*;

/**
 * Node implementing a call to HotSpot's {@code new_[object|type]_array} stub.
 */
public class NewArrayStubCall extends FixedWithNextNode implements LIRGenLowerable {

    private static final Stamp defaultStamp = StampFactory.objectNonNull();

    @Input private final ValueNode hub;
    @Input private final ValueNode length;
    private final boolean isObjectArray;

    public static final Descriptor NEW_OBJECT_ARRAY = new Descriptor("new_object_array", false, Kind.Object, wordKind(), Kind.Int);

    public static final Descriptor NEW_TYPE_ARRAY = new Descriptor("new_type_array", false, Kind.Object, wordKind(), Kind.Int);

    public NewArrayStubCall(boolean isObjectArray, ValueNode hub, ValueNode length) {
        super(defaultStamp);
        this.isObjectArray = isObjectArray;
        this.hub = hub;
        this.length = length;
    }

    @Override
    public boolean inferStamp() {
        if (stamp() == defaultStamp && hub.isConstant()) {
            updateStamp(StampFactory.exactNonNull(HotSpotResolvedObjectType.fromMetaspaceKlass(hub.asConstant())));
            return true;
        }
        return false;
    }

    @Override
    public void generate(LIRGenerator gen) {
        RuntimeCall stub = gen.getRuntime().lookupRuntimeCall(isObjectArray ? NewArrayStubCall.NEW_OBJECT_ARRAY : NewArrayStubCall.NEW_TYPE_ARRAY);
        Variable result = gen.emitCall(stub, stub.getCallingConvention(), true, gen.operand(hub), gen.operand(length));
        gen.setResult(this, result);
    }

    @NodeIntrinsic
    public static native Object call(@ConstantNodeParameter boolean isObjectArray, Word hub, int length);
}
