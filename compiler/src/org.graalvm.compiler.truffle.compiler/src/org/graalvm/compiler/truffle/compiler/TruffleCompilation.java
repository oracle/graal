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
package org.graalvm.compiler.truffle.compiler;

import java.util.function.Consumer;

import org.graalvm.compiler.debug.LogStream;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;

/**
 * Represents a truffle compilation bundling compilable and task into a single object. Also installs
 * the TTY filter to forward log messages to the truffle runtime.
 */
public final class TruffleCompilation implements AutoCloseable {

    private final CompilableTruffleAST compilable;
    private final TruffleCompilationTask task;
    private final TTY.Filter ttyFilter;

    TruffleCompilationIdentifier compilationId;

    TruffleCompilation(TruffleCompilerRuntime runtime, TruffleCompilationTask task, CompilableTruffleAST compilable) {
        this.compilable = compilable;
        this.task = task;
        this.ttyFilter = new TTY.Filter(new LogStream(new TTYToPolyglotLoggerBridge(runtime, compilable)));
    }

    public void setCompilationId(TruffleCompilationIdentifier compilationId) {
        this.compilationId = compilationId;
    }

    public TruffleCompilationIdentifier getCompilationId() {
        return compilationId;
    }

    public CompilableTruffleAST getCompilable() {
        return compilable;
    }

    public TruffleCompilationTask getTask() {
        return task;
    }

    public static boolean isTruffleCompilation(StructuredGraph graph) {
        return lookupCompilable(graph) != null;
    }

    @Override
    public void close() {
        ttyFilter.close();
    }

    public static TruffleCompilationTask lookupTask(StructuredGraph graph) {
        if (graph.compilationId() instanceof TruffleCompilationIdentifier id) {
            return id.getTask();
        }
        return null;
    }

    public static CompilableTruffleAST lookupCompilable(StructuredGraph graph) {
        if (graph.compilationId() instanceof TruffleCompilationIdentifier id) {
            return id.getCompilable();
        }
        return null;
    }

    static final class TTYToPolyglotLoggerBridge implements Consumer<String> {

        private final CompilableTruffleAST compilable;
        private final TruffleCompilerRuntime runtime;

        TTYToPolyglotLoggerBridge(TruffleCompilerRuntime runtime, CompilableTruffleAST compilable) {
            this.compilable = compilable;
            this.runtime = runtime;
        }

        @Override
        public void accept(String message) {
            runtime.log("graal", compilable, message);
        }
    }
}
