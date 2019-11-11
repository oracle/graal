/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.replacements;

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_64;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_16;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractStateSplit;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.memory.MemoryNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_64, size = SIZE_16, allowedUsageTypes = {Memory})
public class AESCryptNode extends AbstractStateSplit implements Lowerable, MemoryCheckpoint.Single, MemoryAccess {

    public static final NodeClass<AESCryptNode> TYPE = NodeClass.create(AESCryptNode.class);

    @OptionalInput(Memory) Node lastLocationAccess;
    @Input NodeInputList<ValueNode> values;

    final boolean encrypt;
    final boolean withOriginalKey;

    public AESCryptNode(boolean encrypt, boolean withOriginalKey, ValueNode... cipherArgs) {
        super(TYPE, StampFactory.forVoid());
        this.encrypt = encrypt;
        this.withOriginalKey = withOriginalKey;
        values = new NodeInputList<>(this, cipherArgs);
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return NamedLocationIdentity.getArrayLocation(JavaKind.Byte);
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return getKilledLocationIdentity();
    }

    @Override
    public MemoryNode getLastLocationAccess() {
        return (MemoryNode) lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryNode lla) {
        Node newLla = ValueNodeUtil.asNode(lla);
        updateUsages(lastLocationAccess, newLla);
        lastLocationAccess = newLla;
    }

    @NodeIntrinsic(AESCryptNode.class)
    public static native void encryptBlock(@ConstantNodeParameter boolean encrypt, @ConstantNodeParameter boolean withOriginalKey, Word in, Word out, Pointer key);

    @NodeIntrinsic(AESCryptNode.class)
    public static native void decryptBlock(@ConstantNodeParameter boolean encrypt, @ConstantNodeParameter boolean withOriginalKey, Word in, Word out, Pointer key);

    @NodeIntrinsic(AESCryptNode.class)
    public static native void decryptBlockWithOriginalKey(@ConstantNodeParameter boolean encrypt, @ConstantNodeParameter boolean withOriginalKey, Word in, Word out, Pointer key, Pointer originalKey);

}
