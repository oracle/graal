/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.target;

import java.lang.reflect.*;

import com.oracle.max.asm.*;
import com.sun.c1x.*;
import com.sun.c1x.gen.*;
import com.sun.c1x.globalstub.*;
import com.sun.c1x.lir.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;
import com.sun.cri.xir.*;

/**
 * The {@code Backend} class represents a compiler backend for C1X.
 *
 * @author Ben L. Titzer
 */
public abstract class Backend {
    public final C1XCompiler compiler;

    protected Backend(C1XCompiler compiler) {
        this.compiler = compiler;
    }

    public static Backend create(CiArchitecture arch, C1XCompiler compiler) {
        String className = arch.getClass().getName().replace("com.oracle.max.asm", "com.sun.c1x") + "Backend";
        try {
            Class<?> c = Class.forName(className);
            Constructor<?> cons = c.getDeclaredConstructor(C1XCompiler.class);
            return (Backend) cons.newInstance(compiler);
        } catch (Exception e) {
            throw new Error("Could not instantiate " + className, e);
        }
    }

    public abstract FrameMap newFrameMap(RiMethod method, int numberOfLocks);
    public abstract LIRGenerator newLIRGenerator(C1XCompilation compilation);
    public abstract LIRAssembler newLIRAssembler(C1XCompilation compilation);
    public abstract AbstractAssembler newAssembler(RiRegisterConfig registerConfig);
    public abstract GlobalStubEmitter newGlobalStubEmitter();
    public abstract CiXirAssembler newXirAssembler();
}
