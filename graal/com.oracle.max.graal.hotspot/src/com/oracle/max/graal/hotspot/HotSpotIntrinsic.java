/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.hotspot;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.snippets.*;

/**
 * Enumeration of intrinsic methods.
 */
public enum HotSpotIntrinsic {
    Thread_currentThread("java.lang.Thread", "currentThread");

    public final String className;
    public final String methodName;
    public final Class<?>[] parameterTypes;


    private HotSpotIntrinsic(String className, String methodName, Class<?>... parameterTypes) {
        this.className = className;
        this.methodName = methodName;
        this.parameterTypes = parameterTypes;
    }

    public static void installIntrinsics(HotSpotRuntime runtime) {
        assert CompilerImpl.getInstance() != null : "compiler must exist before installing intrinsics";
        if (GraalOptions.Intrinsify) {
            GraalIntrinsics.installIntrinsics(runtime, runtime.getCompiler().getTarget(), PhasePlan.DEFAULT);
            Snippets.install(runtime, runtime.getCompiler().getTarget(), new SystemSnippets(), GraalOptions.PlotSnippets, PhasePlan.DEFAULT);
            Snippets.install(runtime, runtime.getCompiler().getTarget(), new UnsafeSnippets(), GraalOptions.PlotSnippets, PhasePlan.DEFAULT);
        }
    }
}
