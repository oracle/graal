/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.PlatformKind;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.hotspot.HotSpotLIRGenerator;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import com.oracle.graal.word.Word;
import com.oracle.graal.word.WordTypes;

/**
 * Gets the address of the C++ JavaThread object for the current thread.
 */
@NodeInfo
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
