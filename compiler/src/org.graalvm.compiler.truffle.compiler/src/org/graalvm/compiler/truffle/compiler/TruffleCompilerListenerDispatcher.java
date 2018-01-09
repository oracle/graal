/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler;

import java.util.ArrayList;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;

/**
 * A collection for broadcasting {@link GraalCompilationListener} events.
 */
@SuppressWarnings("serial")
final class TruffleCompilerListenerDispatcher extends ArrayList<GraalCompilationListener> implements GraalCompilationListener {

    @Override
    public boolean add(GraalCompilationListener e) {
        if (e != this && !contains(e)) {
            return super.add(e);
        }
        return false;
    }

    @Override
    public void onCompilationFailed(CompilableTruffleAST target, StructuredGraph graph, Throwable t) {
        for (GraalCompilationListener l : this) {
            l.onCompilationFailed(target, graph, t);
        }
    }

    @Override
    public void onCompilationStarted(CompilableTruffleAST target) {
        for (GraalCompilationListener l : this) {
            l.onCompilationStarted(target);
        }
    }

    @Override
    public void onCompilationTruffleTierFinished(CompilableTruffleAST target, StructuredGraph graph) {
        for (GraalCompilationListener l : this) {
            l.onCompilationTruffleTierFinished(target, graph);
        }
    }

    @Override
    public void onCompilationGraalTierFinished(CompilableTruffleAST target, StructuredGraph graph) {
        for (GraalCompilationListener l : this) {
            l.onCompilationGraalTierFinished(target, graph);
        }
    }

    @Override
    public void onCompilationSuccess(CompilableTruffleAST target, StructuredGraph graph, CompilationResult result) {
        for (GraalCompilationListener l : this) {
            l.onCompilationSuccess(target, graph, result);
        }
    }

    @Override
    public void onShutdown() {
        for (GraalCompilationListener l : this) {
            l.onShutdown();
        }
    }

}
