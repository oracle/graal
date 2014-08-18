/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.extended;

import static com.oracle.graal.compiler.common.UnsafeAccess.*;

import java.lang.reflect.*;

import sun.misc.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Creates a memory barrier.
 */
@NodeInfo(allowedUsageTypes = {InputType.Memory})
public class MembarNode extends FixedWithNextNode implements LIRLowerable, MemoryCheckpoint.Single {

    private final int barriers;

    /**
     * @param barriers a mask of the barrier constants defined in {@link MemoryBarriers}
     */
    public static MembarNode create(int barriers) {
        return new MembarNodeGen(barriers);
    }

    MembarNode(int barriers) {
        super(StampFactory.forVoid());
        this.barriers = barriers;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return LocationIdentity.ANY_LOCATION;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.getLIRGeneratorTool().emitMembar(barriers);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void memoryBarrier(@ConstantNodeParameter int barriers) {
        // Overly conservative but it doesn't matter in the interpreter
        unsafe.putIntVolatile(dummyBase, dummyOffset, 0);
        unsafe.getIntVolatile(dummyBase, dummyOffset);
    }

    /**
     * An unused field that it used to exercise barriers in the interpreter. This can be replaced
     * with direct support for barriers in {@link Unsafe} if/when they become available.
     */
    @SuppressWarnings("unused") private static int dummy;
    private static Object dummyBase;
    private static long dummyOffset;
    static {
        try {
            Field dummyField = MembarNode.class.getDeclaredField("dummy");
            dummyBase = unsafe.staticFieldBase(dummyField);
            dummyOffset = unsafe.staticFieldOffset(dummyField);
        } catch (Exception e) {
            throw new GraalInternalError(e);
        }
    }
}
