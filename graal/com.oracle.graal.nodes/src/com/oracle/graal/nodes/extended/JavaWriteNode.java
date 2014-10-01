/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Write a raw memory location according to Java field or array write semantics. It will perform
 * write barriers, implicit conversions and optionally oop compression.
 */
@NodeInfo
public class JavaWriteNode extends AbstractWriteNode implements Lowerable, StateSplit, MemoryAccess, MemoryCheckpoint.Single {

    protected final boolean compressible;

    public static JavaWriteNode create(ValueNode object, ValueNode value, ValueNode location, BarrierType barrierType, boolean compressible, boolean initialization) {
        return USE_GENERATED_NODES ? new JavaWriteNodeGen(object, value, location, barrierType, compressible, initialization) : new JavaWriteNode(object, value, location, barrierType, compressible,
                        initialization);
    }

    JavaWriteNode(ValueNode object, ValueNode value, ValueNode location, BarrierType barrierType, boolean compressible, boolean initialization) {
        super(object, value, location, barrierType, initialization);
        this.compressible = compressible;
    }

    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    public boolean canNullCheck() {
        return true;
    }

    public boolean isCompressible() {
        return compressible;
    }
}
