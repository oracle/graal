/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replacements;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.replacements.nodes.IdentityHashCodeNode;

@NodeInfo(cycles = NodeCycles.CYCLES_4, size = NodeSize.SIZE_16)
public class HotSpotIdentityHashCodeNode extends IdentityHashCodeNode {

    public static final NodeClass<HotSpotIdentityHashCodeNode> TYPE = NodeClass.create(HotSpotIdentityHashCodeNode.class);

    public HotSpotIdentityHashCodeNode(ValueNode object, int bci) {
        super(TYPE, object, bci);
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return HotSpotReplacementsUtil.MARK_WORD_LOCATION;
    }
}
