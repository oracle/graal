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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.word.*;

/**
 * Node implementing a call to {@code GraalRuntime::new_multi_array}.
 */
public class NewMultiArrayStubCall extends ForeignCallNode {

    private static final Stamp defaultStamp = StampFactory.objectNonNull();

    @Input private ValueNode hub;
    @Input private ValueNode dims;
    private final int rank;

    public static final ForeignCallDescriptor NEW_MULTI_ARRAY = new ForeignCallDescriptor("new_multi_array", Object.class, Word.class, int.class, Word.class);

    public NewMultiArrayStubCall(@InjectedNodeParameter ForeignCallsProvider foreignCalls, ValueNode hub, int rank, ValueNode dims) {
        super(foreignCalls, NEW_MULTI_ARRAY, defaultStamp);
        this.hub = hub;
        this.rank = rank;
        this.dims = dims;
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
    protected Value[] operands(NodeLIRBuilderTool gen) {
        return new Value[]{gen.operand(hub), Constant.forInt(rank), gen.operand(dims)};
    }

    @NodeIntrinsic
    public static native Object call(Word hub, @ConstantNodeParameter int rank, Word dims);
}
