/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.hotspot.*;
import com.oracle.graal.lir.StandardOp.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Saves all allocatable registers.
 */
public class SaveAllRegistersNode extends FixedWithNextNode implements LIRLowerable {

    private SaveRegistersOp saveRegistersOp;

    public SaveAllRegistersNode() {
        super(StampFactory.forKind(Kind.Long));
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        saveRegistersOp = ((HotSpotLIRGenerator) gen.getLIRGeneratorTool()).emitSaveAllRegisters();
    }

    /**
     * @return the map from registers to the stack locations in they are saved
     */
    public SaveRegistersOp getSaveRegistersOp() {
        assert saveRegistersOp != null : "saved registers op has not yet been created";
        return saveRegistersOp;
    }

    /**
     * @return a token that couples this node to an {@link UncommonTrapCallNode} so that the latter
     *         has access to the {@linkplain SaveRegistersOp#getMap register save map}
     */
    @NodeIntrinsic
    public static native long saveAllRegisters();
}
