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

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Read a raw memory location according to Java field or array read semantics. It will perform read
 * barriers, implicit conversions and optionally oop uncompression.
 */
@NodeInfo
public class JavaReadNode extends FixedAccessNode implements Lowerable, GuardingNode {

    private final boolean compressible;

    public static JavaReadNode create(ValueNode object, LocationNode location, BarrierType barrierType, boolean compressible) {
        return USE_GENERATED_NODES ? new JavaReadNodeGen(object, location, barrierType, compressible) : new JavaReadNode(object, location, barrierType, compressible);
    }

    JavaReadNode(ValueNode object, LocationNode location, BarrierType barrierType, boolean compressible) {
        super(object, location, StampFactory.forKind(location.getValueKind()), barrierType);
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
