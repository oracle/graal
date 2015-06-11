/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.hotspot.amd64;

import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.AMD64Address.Scale;
import com.oracle.graal.compiler.amd64.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.nodes.CompressionNode.CompressionOp;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.jvmci.code.*;
import com.oracle.jvmci.hotspot.HotSpotVMConfig.CompressEncoding;
import com.oracle.jvmci.meta.*;

public class AMD64HotSpotAddressLowering extends AMD64AddressLowering {

    private final long heapBase;
    private final Register heapBaseRegister;

    @NodeInfo
    public static class HeapBaseNode extends FloatingNode implements LIRLowerable {

        public static final NodeClass<HeapBaseNode> TYPE = NodeClass.create(HeapBaseNode.class);

        private final Register heapBaseRegister;

        public HeapBaseNode(Register heapBaseRegister) {
            super(TYPE, StampFactory.pointer());
            this.heapBaseRegister = heapBaseRegister;
        }

        public void generate(NodeLIRBuilderTool generator) {
            LIRKind kind = generator.getLIRGeneratorTool().getLIRKind(stamp());
            generator.setResult(this, heapBaseRegister.asValue(kind));
        }
    }

    public AMD64HotSpotAddressLowering(CodeCacheProvider codeCache, long heapBase, Register heapBaseRegister) {
        super(codeCache);
        this.heapBase = heapBase;
        if (heapBase == 0) {
            this.heapBaseRegister = null;
        } else {
            this.heapBaseRegister = heapBaseRegister;
        }
    }

    @Override
    protected boolean improve(AMD64AddressNode addr) {
        if (addr.getScale() == Scale.Times1) {
            if (addr.getBase() == null && addr.getIndex() instanceof CompressionNode) {
                if (improveUncompression(addr, (CompressionNode) addr.getIndex())) {
                    return true;
                }
            }

            if (addr.getIndex() == null && addr.getBase() instanceof CompressionNode) {
                if (improveUncompression(addr, (CompressionNode) addr.getBase())) {
                    return true;
                }
            }
        }

        return super.improve(addr);
    }

    private boolean improveUncompression(AMD64AddressNode addr, CompressionNode compression) {
        if (compression.getOp() == CompressionOp.Uncompress) {
            CompressEncoding encoding = compression.getEncoding();
            Scale scale = Scale.fromShift(encoding.shift);
            if (scale == null) {
                return false;
            }

            if (heapBaseRegister != null && encoding.base == heapBase) {
                ValueNode base = compression.graph().unique(new HeapBaseNode(heapBaseRegister));
                addr.setBase(base);
            } else if (encoding.base != 0) {
                long disp = addr.getDisplacement() + encoding.base;
                if (NumUtil.isInt(disp)) {
                    addr.setDisplacement((int) disp);
                    addr.setBase(null);
                } else {
                    return false;
                }
            } else {
                addr.setBase(null);
            }

            addr.setScale(scale);
            addr.setIndex(compression.getValue());
            return true;
        } else {
            return false;
        }
    }
}
