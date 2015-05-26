/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle;

import com.oracle.jvmci.code.CompilationResult;
import com.oracle.graal.nodes.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Enables implementations of this interface to listen to compilation related events of the Graal
 * Truffle runtime. The states for a particular {@link OptimizedCallTarget} that is compiled using
 * the Graal Truffle system can be described using the following deterministic automata: * <code>
 * <pre>
 * ( (split | (queue . unqueue))*
 *    . queue . started
 *    . (truffleTierFinished . graalTierFinished . success)
 *      | ([truffleTierFinished] . [graalTierFinished] . failed)
 *    . invalidate )*
 * </pre>
 * </code>
 * <p>
 * Note: <code>|</code> is the 'or' and <code>.</code> is the sequential operator. The
 * <code>*</code> represents the Kleene Closure.
 * </p>
 *
 * @see GraalTruffleRuntime#addCompilationListener(GraalTruffleCompilationListener)
 */
public interface GraalTruffleCompilationListener {

    void notifyCompilationSplit(OptimizedDirectCallNode callNode);

    /**
     * Invoked if a call target was queued to the compilation queue.
     */
    void notifyCompilationQueued(OptimizedCallTarget target);

    /**
     * Invoked if a call target was unqueued from the compilation queue.
     *
     * @param source the source object that caused the compilation to be unqueued. For example the
     *            source {@link Node} object. May be <code>null</code>.
     * @param reason a textual description of the reason why the compilation was unqueued. May be
     *            <code>null</code>.
     */
    void notifyCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason);

    void notifyCompilationFailed(OptimizedCallTarget target, StructuredGraph graph, Throwable t);

    void notifyCompilationStarted(OptimizedCallTarget target);

    void notifyCompilationTruffleTierFinished(OptimizedCallTarget target, StructuredGraph graph);

    void notifyCompilationGraalTierFinished(OptimizedCallTarget target, StructuredGraph graph);

    void notifyCompilationSuccess(OptimizedCallTarget target, StructuredGraph graph, CompilationResult result);

    /**
     * Invoked if a compiled call target was invalidated.
     *
     * @param source the source object that caused the compilation to be invalidated. For example
     *            the source {@link Node} object. May be <code>null</code>.
     * @param reason a textual description of the reason why the compilation was invalidated. May be
     *            <code>null</code>.
     */
    void notifyCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason);

    /**
     * Invoked as the compiler gets shut down.
     */
    void notifyShutdown(GraalTruffleRuntime runtime);

    /**
     * Invoked as soon as the compiler is ready to use.
     */
    void notifyStartup(GraalTruffleRuntime runtime);

}
