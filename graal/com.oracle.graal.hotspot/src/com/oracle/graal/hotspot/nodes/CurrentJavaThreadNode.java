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
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.word.*;

/**
 * Gets the address of the C++ JavaThread object for the current thread.
 */
public final class CurrentJavaThreadNode extends FloatingNode implements LIRLowerable {

    private CurrentJavaThreadNode(Kind kind) {
        super(StampFactory.forKind(kind));
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Register rawThread = ((HotSpotLIRGenerator) gen.getLIRGeneratorTool()).getProviders().getRegisters().getThreadRegister();
        gen.setResult(this, rawThread.asValue(this.getKind()));
    }

    private static int eetopOffset() {
        try {
            return (int) UnsafeAccess.unsafe.objectFieldOffset(Thread.class.getDeclaredField("eetop"));
        } catch (Exception e) {
            throw new GraalInternalError(e);
        }
    }

    @NodeIntrinsic(setStampFromReturnType = true)
    public static Word get(@SuppressWarnings("unused") @ConstantNodeParameter Kind kind) {
        return Word.unsigned(unsafeReadWord(Thread.currentThread(), eetopOffset()));
    }
}
