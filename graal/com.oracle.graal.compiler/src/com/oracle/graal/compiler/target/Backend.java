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
package com.oracle.graal.compiler.target;

import java.lang.reflect.*;

import com.oracle.max.asm.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.cri.xir.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;

/**
 * The {@code Backend} class represents a compiler backend for Graal.
 */
public abstract class Backend {
    public final RiRuntime runtime;
    public final CiTarget target;

    protected Backend(RiRuntime runtime, CiTarget target) {
        this.runtime = runtime;
        this.target = target;
    }

    public static Backend create(CiArchitecture arch, RiRuntime runtime, CiTarget target) {
        String className = arch.getClass().getName().replace("com.oracle.max.asm", "com.oracle.graal.compiler") + "Backend";
        try {
            Class<?> c = Class.forName(className);
            Constructor<?> cons = c.getDeclaredConstructor(RiRuntime.class, CiTarget.class);
            return (Backend) cons.newInstance(runtime, target);
        } catch (Exception e) {
            throw new Error("Could not instantiate " + className, e);
        }
    }

    public abstract FrameMap newFrameMap(RiRegisterConfig registerConfig);
    public abstract LIRGenerator newLIRGenerator(Graph graph, FrameMap frameMap, RiResolvedMethod method, LIR lir, RiXirGenerator xir);
    public abstract AbstractAssembler newAssembler(RiRegisterConfig registerConfig);
    public abstract CiXirAssembler newXirAssembler();

}
