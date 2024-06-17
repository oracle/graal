/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.graal.compiler.hotspot.amd64;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.core.amd64.AMD64AddressNode;
import jdk.graal.compiler.core.amd64.AMD64CompressAddressLowering;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.nodes.CompressionNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.code.Register;

public class AMD64HotSpotAddressLowering extends AMD64CompressAddressLowering {

    private final long heapBase;
    private final Register heapBaseRegister;

    public AMD64HotSpotAddressLowering(GraalHotSpotVMConfig config, Register heapBaseRegister) {
        this.heapBase = config.getOopEncoding().getBase();
        if (heapBase == 0) {
            this.heapBaseRegister = null;
        } else {
            this.heapBaseRegister = heapBaseRegister;
        }
    }

    @Override
    protected final boolean improveUncompression(AMD64AddressNode addr, CompressionNode compression, ValueNode other) {
        CompressEncoding encoding = compression.getEncoding();
        if (!AMD64Address.isScaleShiftSupported(encoding.getShift())) {
            return false;
        }

        if (heapBaseRegister != null && encoding.getBase() == heapBase) {
            if (other == null) {
                ValueNode base = compression.graph().unique(new HeapBaseNode(heapBaseRegister));
                addr.setBase(base);
            } else {
                return false;
            }
        } else if (encoding.getBase() != 0) {
            if (updateDisplacement(addr, encoding.getBase(), false)) {
                addr.setBase(other);
            } else {
                return false;
            }
        } else {
            addr.setBase(other);
        }

        Stride stride = Stride.fromLog2(encoding.getShift());
        addr.setScale(stride);
        addr.setIndex(compression.getValue());
        return true;
    }
}
