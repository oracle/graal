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

import static com.oracle.graal.hotspot.target.HotSpotBackend.*;

import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.target.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;

/**
 * Node implementing a call to HotSpot's {@code graal_monitorexit} stub.
 */
public class MonitorExitStubCall extends FixedWithNextNode implements LIRGenLowerable {

    @Input private final ValueNode object;

    public MonitorExitStubCall(ValueNode object) {
        super(StampFactory.forVoid());
        this.object = object;
    }

    @Override
    public void generate(LIRGenerator gen) {
        HotSpotBackend backend = (HotSpotBackend) HotSpotGraalRuntime.getInstance().getCompiler().backend;
        HotSpotStub stub = backend.getStub(MONITOREXIT_STUB_NAME);
        gen.emitCall(stub.address, stub.cc, true, gen.operand(object), gen.emitLea(gen.peekLock()));
    }

    @NodeIntrinsic
    public static native void call(Object hub);
}
