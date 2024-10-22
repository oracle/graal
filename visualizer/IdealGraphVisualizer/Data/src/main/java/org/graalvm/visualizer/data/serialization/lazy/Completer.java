/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.data.serialization.lazy;

import jdk.graal.compiler.graphio.parsing.model.Group.Feedback;

import java.util.concurrent.Future;

/**
 * Completer interface for lazy group/graph
 */
interface Completer<T> {
    /**
     * Completes the contents providing feedback during the load. Returns Future promising the
     * content data
     *
     * @param feedback feedback callback
     * @return future content
     */
    public Future<T> completeContents(Feedback feedback);

    /**
     * Retrieves partial data. May return {@code null} to indicate that no partial data is
     * expected at all
     *
     * @return partial data or {@code null}
     */
    public T partialData();

    /**
     * Determines whether completer can be called now. Returns false if invoked during completion
     * itself. Use to avoid deadlocks and diagnostics
     *
     * @return true, if the caller can ask for the completion
     */
    public boolean canComplete();
}
