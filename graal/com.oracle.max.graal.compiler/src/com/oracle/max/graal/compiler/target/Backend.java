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
package com.oracle.max.graal.compiler.target;

import java.lang.reflect.*;

import com.oracle.max.asm.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.stub.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;
import com.sun.cri.xir.*;

/**
 * The {@code Backend} class represents a compiler backend for Graal.
 */
public abstract class Backend {
    public final GraalCompiler compiler;

    protected Backend(GraalCompiler compiler) {
        this.compiler = compiler;
    }

    public static Backend create(CiArchitecture arch, GraalCompiler compiler) {
        String className = arch.getClass().getName().replace("com.oracle.max.asm", "com.oracle.max.graal.compiler") + "Backend";
        try {
            Class<?> c = Class.forName(className);
            Constructor<?> cons = c.getDeclaredConstructor(GraalCompiler.class);
            return (Backend) cons.newInstance(compiler);
        } catch (Exception e) {
            throw new Error("Could not instantiate " + className, e);
        }
    }

    public abstract FrameMap newFrameMap(GraalCompilation compilation, RiResolvedMethod method);
    public abstract LIRGenerator newLIRGenerator(GraalCompilation compilation, RiXirGenerator xir);
    public abstract AbstractAssembler newAssembler(RiRegisterConfig registerConfig);
    public abstract CiXirAssembler newXirAssembler();

    public abstract CompilerStub emit(GraalContext context, CompilerStub.Id stub);
    public abstract CompilerStub emit(GraalContext context, CiRuntimeCall runtimeCall);
    public abstract CompilerStub emit(GraalContext context, XirTemplate t);

}
