/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.amd64;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.nodes.FloatingWordCastNode;
import com.oracle.svm.core.graal.nodes.SubstrateCompressionNode;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.core.amd64.AMD64AddressNode;
import org.graalvm.compiler.core.amd64.AMD64CompressAddressLowering;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.nodes.CompressionNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.GetObjectAddressNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.SubstrateOptions;

import jdk.vm.ci.code.Register;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.IntegerConvertNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.ReinterpretNode;
import org.graalvm.compiler.nodes.calc.ShiftNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;

public class SubstrateAMD64AddressLowering extends AMD64CompressAddressLowering {
    private final long heapBase;
    private final Register heapBaseRegister;

    public SubstrateAMD64AddressLowering(CompressEncoding encoding) {
        heapBase = encoding.getBase();
        heapBaseRegister = ReservedRegisters.singleton().getHeapBaseRegister();
    }

    @Override
    protected final boolean improveUncompression(AMD64AddressNode addr, CompressionNode compression, ValueNode other) {
        assert SubstrateOptions.SpawnIsolates.getValue();

        CompressEncoding encoding = compression.getEncoding();
        ValueNode idx = compression.getValue();
        if (!AMD64Address.isScaleShiftSupported(encoding.getShift())) {
            return false;
        }

        long encodingBase = encoding.getBase();
        ValueNode base = other;
        if (heapBaseRegister != null && encodingBase == heapBase) {
            if (other != null) {
                return false;
            }
            base = compression.graph().unique(new HeapBaseNode(heapBaseRegister));

            // FIXME
            // TODO, if the displacement is below a certain threshold we can optimize probably.
            //  which means assume that the displacement fits the shift and don't compute all the nodes
            //  but only the AND node.
            if (compression.getOp() == CompressionNode.CompressionOp.Uncompress && compression.getEncoding().getShift() == 3) {
                // ADDR is of the type [base + index*scale + displacement]
                // TODO ConstantNode.forInt()

                ConstantNode maskNode = compression.graph().addWithoutUnique(new ConstantNode(JavaConstant.forLong((1L << 35)-1), IntegerStamp.create(64)));
                ConfigurationValues.getObjectLayout().getReferenceSize();
                FloatingWordCastNode compressAsAddress = compression.graph().addWithoutUnique(new FloatingWordCastNode(IntegerStamp.create(ConfigurationValues.getObjectLayout().getReferenceSize()*8), compression.getValue()));
                AMD64AddressNode indexAddressNode = compression.graph().addWithoutUnique(new AMD64AddressNode(null, compressAsAddress));
                indexAddressNode.setScale(Stride.fromLog2(encoding.getShift()));
                indexAddressNode.setDisplacement(addr.getDisplacement());
                indexAddressNode.setStamp(maskNode.stamp(NodeView.DEFAULT));
                idx = compression.graph().addWithoutUnique(new AndNode(indexAddressNode, maskNode));
                
//                ConstantNode scaleNode = compression.graph().addOrUnique(new ConstantNode(JavaConstant.forInt(encoding.getShift()), IntegerStamp.create(32)));
//                LeftShiftNode shiftNode = compression.graph().addOrUnique(new LeftShiftNode(compressAsAddress, scaleNode));
//                ConstantNode displacementNode = compression.graph().addOrUnique(new ConstantNode(JavaConstant.forLong(addr.getDisplacement()), IntegerStamp.create(64)));
//                AddNode addNode = compression.graph().addOrUnique(new AddNode(shiftNode, displacementNode));
//                ConstantNode maskNode = compression.graph().addOrUnique(new ConstantNode(JavaConstant.forLong((1L << 35)-1), IntegerStamp.create(64)));
//                idx = compression.graph().addOrUnique(new AndNode(addNode, maskNode));




                addr.setBase(base);
                addr.setDisplacement(0);
                addr.setScale(Stride.S1);
                addr.setIndex(idx);

                return true;
            }
        } else if (encodingBase != 0) {
            if (!updateDisplacement(addr, encodingBase, false)) {
                return false;
            }
        }

        Stride stride = Stride.fromLog2(encoding.getShift());
        addr.setBase(base);
        addr.setScale(stride);
//        addr.setIndex(compression.getValue());
        addr.setIndex(idx);
        return true;
    }
}
