/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.jfr.JFRProvider;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.hotspot.JFR;

import java.util.function.Consumer;

/**
 * A HotSpot JFR implementation of {@link JFRProvider}.
 */
@ServiceProvider(JFRProvider.class)
public final class HotSpotJFRProvider implements JFRProvider {

    @Override
    public void registerCompilerPhases(String[] phaseNames) {
        JFR.CompilerPhaseScope.registerPhases(phaseNames);
    }

    @Override
    public CompilerPhaseEvent openCompilerPhase(Consumer<CompilerPhaseEvent> writer, int nesting) {
        return new HotSpotJFRCompilerPhaseEvent(writer, nesting);
    }

    /**
     * A JFR compiler phase event.
     */
    public static final class HotSpotJFRCompilerPhaseEvent implements CompilerPhaseEvent {

        private final long startTime;
        private final Consumer<CompilerPhaseEvent> writer;
        private final int nesting;

        private HotSpotJFRCompilerPhaseEvent(Consumer<CompilerPhaseEvent> writer, int nesting) {
            startTime = JFR.Ticks.now();
            this.writer = writer;
            this.nesting = nesting;
        }

        @Override
        public void write(String phase, CompilationIdentifier compileId) {
            if (compileId instanceof HotSpotCompilationIdentifier) {
                JFR.CompilerPhaseScope.write(startTime, phase, ((HotSpotCompilationIdentifier) compileId).getRequest().getId(), nesting);
            }
        }

        @Override
        public void close() {
            if (writer != null) {
                writer.accept(this);
            }
        }
    }

    @Override
    public void notifyInlining(CompilationIdentifier compileId, ResolvedJavaMethod caller, ResolvedJavaMethod callee, boolean succeeded, String message, int bci) {
        if (compileId instanceof HotSpotCompilationIdentifier) {
            JFR.CompilerInliningEvent.write(((HotSpotCompilationIdentifier) compileId).getRequest().getId(), caller, callee, succeeded, message, bci);
        }
    }

}
