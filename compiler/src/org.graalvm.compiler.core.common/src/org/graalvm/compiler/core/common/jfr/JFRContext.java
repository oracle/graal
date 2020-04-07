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
package org.graalvm.compiler.core.common.jfr;

import java.util.function.Consumer;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.jfr.JFRProvider.CompilerPhaseEvent;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * A facility for event generation.
 *
 * A {@code JFRContext} object must only be used on the thread that created it. This means it needs
 * to be passed around as a parameter. For convenience, it can be encapsulated in a widely used
 * object that is in scope wherever a {@code JFRContext} is needed. However, care must be taken when
 * such objects can be exposed to multiple threads (e.g., they are in a non-thread-local cache).
 */
public final class JFRContext implements AutoCloseable {

    /**
     * Singleton used to represent a disabled JFR context.
     */
    public static final JFRContext DISABLED_JFR = new JFRContext(CompilationIdentifier.INVALID_COMPILATION_ID, null);

    private final JFRProvider jfrProvider;

    /**
     * A unique identifier.
     */
    private final CompilationIdentifier compileId;

    /**
     * Creates a {@link JFRContext}.
     *
     * @param compileId an unique identifier associated with this computation
     * @param jfrProvider JFR service provider
     * @return JFR event context
     */
    public static JFRContext create(CompilationIdentifier compileId, JFRProvider jfrProvider) {
        return jfrProvider != null ? new JFRContext(compileId, jfrProvider) : DISABLED_JFR;
    }

    private JFRContext(CompilationIdentifier compileId, JFRProvider jfrProvider) {
        this.compileId = compileId;
        this.jfrProvider = jfrProvider;
    }

    /**
     * Determines if JFR events are enabled.
     */
    public boolean isEnabled() {
        return jfrProvider != null;
    }

    /**
     * @return identifier of the computation associated with this event context
     */
    public CompilationIdentifier compileId() {
        return compileId;
    }

    public interface Scope extends AutoCloseable {

        @Override
        void close();
    }

    /**
     * Creates a phase event object.
     *
     * @param writer contains all info for the event
     * @return an {@link AutoCloseable} phase event scope object
     */
    public Scope openCompilerPhaseScope(Consumer<CompilerPhaseEvent> writer) {
        if (jfrProvider != null) {
            CompilerPhaseEvent event = jfrProvider.openCompilerPhase(writer, phaseNesting++);
            return new Scope() {

                @Override
                public void close() {
                    event.close();
                    --phaseNesting;
                }
            };
        }
        return null;
    }

    private int phaseNesting = 1;

    /**
     * Notify an inline event to JfrProvider.
     *
     * @param caller caller method
     * @param callee callee method
     * @param succeeded true if inlining succeeded
     * @param message extra information on inlining
     * @param bci byte code index of invoke
     */
    public void notifyInlining(ResolvedJavaMethod caller, ResolvedJavaMethod callee, boolean succeeded, String message, int bci) {
        if (jfrProvider != null) {
            jfrProvider.notifyInlining(compileId, caller, callee, succeeded, message, bci);
        }
    }

    @Override
    public void close() {
    }
}
