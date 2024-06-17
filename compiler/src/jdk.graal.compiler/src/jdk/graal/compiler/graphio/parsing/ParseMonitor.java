/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.graphio.parsing;

import java.util.List;

import jdk.graal.compiler.graphio.parsing.model.FolderElement;

public interface ParseMonitor {
    /**
     * Tick to report progress. The implementation should report the current progress status
     */
    void updateProgress();

    /**
     * Provides state detail information, usually name of object being worked on.
     */
    void setState(String state);

    /**
     * Determines if the work was cancelled. The caller should terminate the work as soon as
     * possible.
     *
     * @return true, if work should be cancelled.
     */
    boolean isCancelled();

    /**
     * Reports loading error. Since the error may occur in the middle of construction of a Group or
     * Graph, and the item may not be (yet) consistent, the error item is only represented by its
     * name. Parents are reported up to (not including) the GraphDocument.
     * <p/>
     * Parent names are provided in parallel because stream data may not be immediately materialized
     * into elements. In that case, list of parents may be truncated or contain {@code nulls}, but
     * the list of names should be correct.
     *
     * @param parents parents of the error item
     * @param parentNames names of parents
     * @param name name (id) of the erroneous item
     * @param errorMessage error report
     */
    void reportError(List<FolderElement> parents, List<String> parentNames, String name, String errorMessage);
}
