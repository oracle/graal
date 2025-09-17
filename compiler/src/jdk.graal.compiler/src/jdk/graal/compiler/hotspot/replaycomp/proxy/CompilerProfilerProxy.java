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
package jdk.graal.compiler.hotspot.replaycomp.proxy;

import jdk.graal.compiler.core.common.CompilerProfiler;
import jdk.vm.ci.meta.ResolvedJavaMethod;

//JaCoCo Exclude

public final class CompilerProfilerProxy extends CompilationProxyBase implements CompilerProfiler {
    CompilerProfilerProxy(InvocationHandler handler) {
        super(handler);
    }

    private static SymbolicMethod method(String name, Class<?>... params) {
        return new SymbolicMethod(CompilerProfiler.class, name, params);
    }

    public static final SymbolicMethod getTicksMethod = method("getTicks");
    private static final InvokableMethod getTicksInvokable = (receiver, args) -> ((CompilerProfiler) receiver).getTicks();

    @Override
    public long getTicks() {
        return (long) handle(getTicksMethod, getTicksInvokable);
    }

    public static final SymbolicMethod notifyCompilerPhaseEventMethod = method("notifyCompilerPhaseEvent", int.class, long.class, String.class, int.class);
    private static final InvokableMethod notifyCompilerPhaseEventInvokable = (receiver, args) -> {
        ((CompilerProfiler) receiver).notifyCompilerPhaseEvent((int) args[0], (long) args[1], (String) args[2], (int) args[3]);
        return null;
    };

    @Override
    public void notifyCompilerPhaseEvent(int compileId, long startTime, String name, int nestingLevel) {
        handle(notifyCompilerPhaseEventMethod, notifyCompilerPhaseEventInvokable, compileId, startTime, name, nestingLevel);
    }

    public static final SymbolicMethod notifyCompilerInliningEventMethod = method("notifyCompilerInliningEvent", int.class, ResolvedJavaMethod.class, ResolvedJavaMethod.class, boolean.class,
                    String.class, int.class);
    private static final InvokableMethod notifyCompilerInliningEventInvokable = (receiver, args) -> {
        ((CompilerProfiler) receiver).notifyCompilerInliningEvent((int) args[0], (ResolvedJavaMethod) args[1], (ResolvedJavaMethod) args[2], (boolean) args[3], (String) args[4], (int) args[5]);
        return null;
    };

    @Override
    public void notifyCompilerInliningEvent(int compileId, ResolvedJavaMethod caller, ResolvedJavaMethod callee, boolean succeeded, String message, int bci) {
        handle(notifyCompilerInliningEventMethod, notifyCompilerInliningEventInvokable, compileId, caller, callee, succeeded, message, bci);
    }
}
