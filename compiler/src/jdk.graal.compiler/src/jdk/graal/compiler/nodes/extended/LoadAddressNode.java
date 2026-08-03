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
package jdk.graal.compiler.nodes.extended;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FloatingAnchoredNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;

/**
 * Converts an address to an integer.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public final class LoadAddressNode extends FloatingAnchoredNode implements LIRLowerable {
    public static final NodeClass<LoadAddressNode> TYPE = NodeClass.create(LoadAddressNode.class);

    @Input ValueNode address;

    protected LoadAddressNode(JavaKind wordKind, ValueNode address, AnchoringNode anchor) {
        super(TYPE, StampFactory.forKind(wordKind), anchor);
        this.address = address;
    }

    /**
     * Generates nodes to load the given {@code address}. This generates two
     * {@link LoadAddressNode}s: One to load the base address, and a second one to load the entire
     * address value based on that. This latter node is returned.
     *
     * This is necessary because the base address computation may involve an uncompression. Address
     * computations involving both uncompression and offsetting must not be alive across safepoints
     * because the GC would not be able to patch up references after it moves objects. Keeping the
     * base computation separate from the rest allows us to keep both alive for longer and to have
     * correct OopMap entries for both.
     *
     * Generating the two separate nodes is not a problem even when they are not needed: They are
     * simply moves that are eliminated during LIR generation.
     */
    public static LoadAddressNode create(JavaKind wordKind, OffsetAddressNode address, AnchoringNode anchor) {
        LoadAddressNode loadedBase = new LoadAddressNode(wordKind, address.getBase(), anchor);
        OffsetAddressNode offsetLoadedBase = new OffsetAddressNode(loadedBase, address.getOffset());
        LoadAddressNode loadedAddress = new LoadAddressNode(wordKind, offsetLoadedBase, anchor);
        return loadedAddress;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().addressAsAllocatableInteger(gen.operand(address)));
    }
}
