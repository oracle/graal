/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test.builtins;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.sl.nodes.SLRootNode;
import com.oracle.truffle.sl.runtime.SLFunction;
import com.oracle.truffle.sl.runtime.SLNull;

/**
 * Disables splitting for a given {@link SLFunction} instance. If no function is given the splitting
 * will be disabled for the calling function.
 */
@NodeInfo(shortName = "disableSplitting")
public abstract class SLDisableSplittingBuiltin extends SLGraalRuntimeBuiltin {

    @Specialization
    @TruffleBoundary
    public SLFunction disableSplitting(SLFunction function) {
        OptimizedCallTarget target = (OptimizedCallTarget) function.getCallTarget();
        ((SLRootNode) target.getRootNode()).setCloningAllowed(false);
        return function;
    }

    @Specialization
    @TruffleBoundary
    public SLNull disableSplitting(@SuppressWarnings("unused") SLNull argument) {
        RootNode parentRoot = Truffle.getRuntime().getCallerFrame().getCallNode().getRootNode();
        ((SLRootNode) parentRoot).setCloningAllowed(false);
        return SLNull.SINGLETON;
    }

}
