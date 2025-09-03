/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.nodes;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.replacements.nodes.MacroNode.MacroParams;
import jdk.vm.ci.amd64.AMD64;

// JaCoCo Exclude

/**
 * This node converts a given codepoint index to a byte index on a <i>correctly encoded</i> UTF-8 or
 * UTF-16 string, see {@code AMD64CodepointIndexToByteIndexOp} for details.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_128)
public final class StringCodepointIndexToByteIndexMacroNode extends MacroWithExceptionNode implements Canonicalizable {

    public static final NodeClass<StringCodepointIndexToByteIndexMacroNode> TYPE = NodeClass.create(StringCodepointIndexToByteIndexMacroNode.class);

    private final StringCodepointIndexToByteIndexNode.InputEncoding inputEncoding;
    private final LocationIdentity locationIdentity;

    public StringCodepointIndexToByteIndexMacroNode(MacroParams p, StringCodepointIndexToByteIndexNode.InputEncoding inputEncoding, LocationIdentity locationIdentity) {
        this(TYPE, p, inputEncoding, locationIdentity);
    }

    public StringCodepointIndexToByteIndexMacroNode(NodeClass<? extends MacroWithExceptionNode> c, MacroParams p, StringCodepointIndexToByteIndexNode.InputEncoding inputEncoding,
                    LocationIdentity locationIdentity) {
        super(c, p);
        this.inputEncoding = inputEncoding;
        this.locationIdentity = locationIdentity;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        AMD64 arch = (AMD64) tool.getLowerer().getTarget().arch;
        boolean intrinsifiable = arch.getFeatures().containsAll(StringCodepointIndexToByteIndexNode.minFeaturesAMD64());
        if (intrinsifiable) {
            return new StringCodepointIndexToByteIndexNode(getArgument(1), getArgument(2), getArgument(3), getArgument(4), inputEncoding, locationIdentity);
        }

        return this;
    }
}
