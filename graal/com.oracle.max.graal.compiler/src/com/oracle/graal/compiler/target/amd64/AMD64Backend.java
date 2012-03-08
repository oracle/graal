/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.target.amd64;

import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.cri.xir.*;
import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.target.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.lir.*;

/**
 * The {@code X86Backend} class represents the backend for the AMD64 architecture.
 */
public class AMD64Backend extends Backend {

    public AMD64Backend(RiRuntime runtime, CiTarget target) {
        super(runtime, target);
    }
    /**
     * Creates a new LIRGenerator for x86.
     * @param compilation the compilation for which to create the LIR generator
     * @return an appropriate LIR generator instance
     */
    @Override
    public LIRGenerator newLIRGenerator(Graph graph, FrameMap frameMap, RiResolvedMethod method, LIR lir, RiXirGenerator xir) {
        return new AMD64LIRGenerator(graph, runtime, target, frameMap, method, lir, xir);
    }

    @Override
    public FrameMap newFrameMap(RiRegisterConfig registerConfig) {
        return new FrameMap(runtime, target, registerConfig);
    }

    @Override
    public AbstractAssembler newAssembler(RiRegisterConfig registerConfig) {
        return new AMD64MacroAssembler(target, registerConfig);
    }

    @Override
    public CiXirAssembler newXirAssembler() {
        return new AMD64XirAssembler(target);
    }
}
