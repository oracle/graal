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
package org.graalvm.tools.lsp.server;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * This service interface defines methods to submit a task, i.e. a {@link Callable}, which will be
 * executed in a Polyglot-Context-entered Thread to access features of the Truffle API.
 *
 */
public interface ContextAwareExecutor {

    /**
     * Execute a task in a default Polyglot Context. Use this method to access the Truffle API to
     * parse source code or do language specific look-ups. To execute source code, use
     * {@link ContextAwareExecutor#executeWithNestedContext(Callable)}.
     *
     * @param taskWithResult a task which shall be executed in a Polyglot-Context-entered Thread
     * @return a {@link Future} to await the task's result
     */
    <T> Future<T> executeWithDefaultContext(Callable<T> taskWithResult);

    /**
     * Execute a task in a newly created Polyglot Context. This is useful if the task executes
     * arbitrary source code which will change the state of the language contexts. The created
     * Polyglot Context will be closed and removed afterwards. Remember, that creating new Polyglot
     * Contexts might be expensive, depending on the installed languages.
     *
     *
     * @param taskWithResult a task which shall be executed in a Polyglot-Context-entered Thread,
     *            which might have unwanted side-effects
     * @return a {@link Future} to await the task's result
     */
    default <T> Future<T> executeWithNestedContext(Callable<T> taskWithResult) {
        return executeWithNestedContext(taskWithResult, false);
    }

    /**
     * Same as {@link ContextAwareExecutor#executeWithNestedContext(Callable)}, but allowing to
     * reuse nested Context instances.
     *
     * @param taskWithResult a task which shall be executed in a Polyglot-Context-entered Thread,
     *            which might have unwanted side-effects
     * @param cached As creating new Polyglot Context instances might be expensive, this parameter
     *            allows to reuse the last nested Context instance for this call. To enforce using a
     *            fresh Context instance, {@link ContextAwareExecutor#resetContextCache()} can be
     *            called.
     * @return a {@link Future} to await the task's result
     */
    <T> Future<T> executeWithNestedContext(Callable<T> taskWithResult, boolean cached);

    /**
     * Explicitly closes and removes all cached nested Context instances.
     */
    void resetContextCache();

    /**
     * Shutdown this executor without waiting for tasks to finish.
     */
    void shutdown();

}
