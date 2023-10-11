/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.hotspot.nodes;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_1;

import jdk.compiler.graal.core.common.LIRKind;
import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.hotspot.HotSpotLIRGenerator;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.calc.FloatingNode;
import jdk.compiler.graal.nodes.spi.LIRLowerable;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.compiler.graal.word.Word;
import jdk.compiler.graal.word.WordTypes;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;

/**
 * Gets the address of the C++ JavaThread object for the current thread.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public final class CurrentJavaThreadNode extends FloatingNode implements LIRLowerable {
    public static final NodeClass<CurrentJavaThreadNode> TYPE = NodeClass.create(CurrentJavaThreadNode.class);

    public CurrentJavaThreadNode(@InjectedNodeParameter WordTypes wordTypes) {
        this(wordTypes.getWordKind());
    }

    public CurrentJavaThreadNode(JavaKind wordKind) {
        super(TYPE, StampFactory.forKind(wordKind));
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Register rawThread = ((HotSpotLIRGenerator) gen.getLIRGeneratorTool()).getProviders().getRegisters().getThreadRegister();
        PlatformKind wordKind = gen.getLIRGeneratorTool().target().arch.getWordKind();
        gen.setResult(this, rawThread.asValue(LIRKind.value(wordKind)));
    }

    @NodeIntrinsic
    public static native Word get();
}
