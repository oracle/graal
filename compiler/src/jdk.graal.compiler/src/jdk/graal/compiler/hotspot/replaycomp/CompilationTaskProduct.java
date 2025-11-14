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
package jdk.graal.compiler.hotspot.replaycomp;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.printer.CanonicalStringGraphPrinter;

/**
 * The product of a recording or replay compilation task, which may be a successfully compiled graph
 * or an exception.
 */
public sealed interface CompilationTaskProduct {
    /**
     * Returns {@code true} if the product represents a successfully completed compilation task.
     */
    boolean isSuccess();

    /**
     * The product of a compilation task that failed with an exception.
     *
     * @param className the class name of the exception
     * @param stackTrace the stack trace of the exception
     */
    record CompilationTaskException(String className, String stackTrace) implements CompilationTaskProduct {
        /**
         * Represents an unknown exception recorded by an earlier compiler version, which did not
         * record this information. This is temporarily needed for compatibility with older replay
         * files.
         */
        public static final CompilationTaskException UNKNOWN = new CompilationTaskException("unknown", "unknown");

        @Override
        public boolean isSuccess() {
            return false;
        }
    }

    /**
     * The artifacts produced by a successful compilation task on this VM.
     *
     * @param graph the final graph
     * @param result the compilation result
     */
    record CompilationTaskArtifacts(StructuredGraph graph, CompilationResult result) implements CompilationTaskProduct {
        /**
         * Returns the canonical graph string for the final graph.
         */
        public String finalCanonicalGraph() {
            return CanonicalStringGraphPrinter.getCanonicalGraphString(graph, false, true);
        }

        /**
         * Converts the object to a serializable representation that can be loaded during replay.
         */
        public RecordedCompilationTaskArtifacts asRecordedArtifacts() {
            return new RecordedCompilationTaskArtifacts(finalCanonicalGraph());
        }

        @Override
        public boolean isSuccess() {
            return true;
        }
    }

    /**
     * The final graph produced by a successful compilation task. This is a serializable subset of
     * {@link CompilationTaskArtifacts} used for recorded compilation tasks.
     *
     * @param finalGraph the final canonical graph
     */
    record RecordedCompilationTaskArtifacts(String finalGraph) implements CompilationTaskProduct {
        @Override
        public boolean isSuccess() {
            return true;
        }
    }
}
