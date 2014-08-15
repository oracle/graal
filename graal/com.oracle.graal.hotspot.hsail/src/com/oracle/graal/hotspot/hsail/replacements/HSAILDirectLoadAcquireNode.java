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
package com.oracle.graal.hotspot.hsail.replacements;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.hsail.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;

@NodeInfo
public class HSAILDirectLoadAcquireNode extends DirectReadNode {

    public HSAILDirectLoadAcquireNode(ValueNode address, Kind readKind) {
        super(address, readKind);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        HSAILHotSpotLIRGenerator hsailgen = (HSAILHotSpotLIRGenerator) (gen.getLIRGeneratorTool());
        LIRKind kind = hsailgen.getLIRKind(stamp());
        Value result = hsailgen.emitLoadAcquire(kind, gen.operand(getAddress()), null);
        gen.setResult(this, result);
    }

    @NodeIntrinsic
    public static native long loadAcquire(long address, @ConstantNodeParameter Kind kind);

    public static long loadAcquireLong(Word address) {
        return loadAcquire(address.rawValue(), Kind.Long);
    }

}
