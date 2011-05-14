/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.target.amd64;

import static com.sun.c1x.C1XCompilation.*;

import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.sun.c1x.*;
import com.sun.c1x.gen.*;
import com.sun.c1x.globalstub.*;
import com.sun.c1x.lir.*;
import com.sun.c1x.target.*;
import com.sun.cri.ri.*;
import com.sun.cri.xir.*;

/**
 * The {@code X86Backend} class represents the backend for the AMD64 architecture.
 *
 * @author Ben L. Titzer
 */
public class AMD64Backend extends Backend {

    public AMD64Backend(C1XCompiler compiler) {
        super(compiler);
    }
    /**
     * Creates a new LIRGenerator for x86.
     * @param compilation the compilation for which to create the LIR generator
     * @return an appropriate LIR generator instance
     */
    @Override
    public LIRGenerator newLIRGenerator(C1XCompilation compilation) {
        return new AMD64LIRGenerator(compilation);
    }

    /**
     * Creates a new LIRAssembler for x86.
     * @param compilation the compilation for which to create the LIR assembler
     * @return an appropriate LIR assembler instance
     */
    @Override
    public LIRAssembler newLIRAssembler(C1XCompilation compilation) {
        return new AMD64LIRAssembler(compilation);
    }

    @Override
    public FrameMap newFrameMap(RiMethod method, int numberOfLocks) {
        return new FrameMap(compilation(), method, numberOfLocks);
    }
    @Override
    public AbstractAssembler newAssembler(RiRegisterConfig registerConfig) {
        return new AMD64MacroAssembler(compiler.target, registerConfig);
    }

    @Override
    public CiXirAssembler newXirAssembler() {
        return new AMD64XirAssembler();
    }

    @Override
    public GlobalStubEmitter newGlobalStubEmitter() {
        return new AMD64GlobalStubEmitter(compiler);
    }
}
