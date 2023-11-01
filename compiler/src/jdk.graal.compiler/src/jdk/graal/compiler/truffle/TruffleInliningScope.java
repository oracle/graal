/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle;

import java.io.Closeable;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.truffle.phases.inlining.CallTree;

/**
 * Scope class intended to capture the inlined call tree during dumping. This scope has no effect if
 * debug dumping is not enabled.
 */
public final class TruffleInliningScope implements Closeable {

    private static final TruffleInliningScope DISABLED = new TruffleInliningScope();
    private static final ThreadLocal<TruffleInliningScope> CURRENT_SCOPE = new ThreadLocal<>();

    static TruffleInliningScope open(DebugContext debug) {
        if (debug.isDumpEnabled(DebugContext.BASIC_LEVEL)) {
            TruffleInliningScope scope = new TruffleInliningScope();
            CURRENT_SCOPE.set(scope);
            return scope;
        }
        return DISABLED;
    }

    private CallTree callTree;

    TruffleInliningScope() {
    }

    public static TruffleInliningScope getCurrent(DebugContext debug) {
        if (debug.isDumpEnabled(DebugContext.BASIC_LEVEL)) {
            return CURRENT_SCOPE.get();
        }
        return DISABLED;
    }

    public void setCallTree(CallTree callTree) {
        if (this == DISABLED) {
            return;
        }
        if (this.callTree != null) {
            throw GraalError.shouldNotReachHere("Only one call tree expected.");
        }
        this.callTree = callTree;
    }

    public CallTree getCallTree() {
        return callTree;
    }

    @Override
    public void close() {
        if (this == DISABLED) {
            return;
        }
        this.callTree = null;
    }

}
