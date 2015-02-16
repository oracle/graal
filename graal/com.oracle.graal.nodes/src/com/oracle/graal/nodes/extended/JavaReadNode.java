/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Read a raw memory location according to Java field or array read semantics. It will perform read
 * barriers, implicit conversions and optionally oop uncompression.
 */
@NodeInfo
public final class JavaReadNode extends FixedAccessNode implements Lowerable, GuardingNode, Canonicalizable {

    public static final NodeClass TYPE = NodeClass.get(JavaReadNode.class);
    protected final Kind readKind;
    protected final boolean compressible;

    public JavaReadNode(Kind readKind, ValueNode object, LocationNode location, BarrierType barrierType, boolean compressible) {
        super(TYPE, object, location, StampFactory.forKind(readKind), barrierType);
        this.readKind = readKind;
        this.compressible = compressible;
    }

    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    public boolean canNullCheck() {
        return true;
    }

    public Kind getReadKind() {
        return readKind;
    }

    public boolean isCompressible() {
        return compressible;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        return ReadNode.canonicalizeRead(this, location(), object(), tool);
    }
}
