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
package com.oracle.graal.word.nodes;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.word.phases.*;

/**
 * Cast between Word and Object that is introduced by the {@link WordTypeRewriterPhase}. It has an
 * impact on the pointer maps for the GC, so it must not be scheduled or optimized away.
 */
@NodeInfo
public class WordCastNode extends FixedWithNextNode implements LIRLowerable, Canonicalizable {

    @Input ValueNode input;

    public static WordCastNode wordToObject(ValueNode input, Kind wordKind) {
        assert input.getKind() == wordKind;
        return WordCastNode.create(StampFactory.object(), input);
    }

    public static WordCastNode objectToWord(ValueNode input, Kind wordKind) {
        assert input.getKind() == Kind.Object;
        return WordCastNode.create(StampFactory.forKind(wordKind), input);
    }

    public static WordCastNode create(Stamp stamp, ValueNode input) {
        return new WordCastNodeGen(stamp, input);
    }

    WordCastNode(Stamp stamp, ValueNode input) {
        super(stamp);
        this.input = input;
    }

    public ValueNode getInput() {
        return input;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (usages().count() == 0) {
            /* If the cast is unused, it can be eliminated. */
            return input;
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        assert getKind() != input.getKind();
        assert generator.getLIRGeneratorTool().target().getSizeInBytes(getKind()) == generator.getLIRGeneratorTool().target().getSizeInBytes(input.getKind());

        Value value = generator.operand(input);
        LIRKind kind = generator.getLIRGeneratorTool().getLIRKind(stamp());
        if (kind.isValue()) {
            // only add reference information, but never drop it
            kind = value.getLIRKind().changeType(kind.getPlatformKind());
        }

        AllocatableValue result = generator.getLIRGeneratorTool().newVariable(kind);
        generator.getLIRGeneratorTool().emitMove(result, value);
        generator.setResult(this, result);
    }
}
