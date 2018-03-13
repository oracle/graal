/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.graphio;

/**
 * Provides source location information about compiled code. This interface is an extension of
 * {@link GraphElements} - by default the elements work with classical {@link StackTraceElement}.
 * Should the default behavior not be sufficient, feel free to implement the additional operations
 * available in this interface and register your implementation when {@link GraphOutput.Builder
 * building} the {@link GraphOutput} instance.
 *
 * @param <M> type representing methods
 * @param <P> type representing source code location
 * @param <L> represeting {@link StackTraceElement stack element} location
 *
 * @since 0.33 part of GraalVM 0.33
 */
public interface GraphLocations<M, P, L> {
    /**
     * Stack trace element for a method, index and position.
     *
     * @param method the method
     * @param bci the index
     * @param pos the position
     * @return stack trace element for the method, index and position
     */
    L methodStackTraceElement(M method, int bci, P pos);

    /**
     * File name of the location.
     * 
     * @param location the location
     * @return the file name for the given location
     */
    String locationFileName(L location);

    /**
     * Line number of a location.
     * 
     * @param location the location
     * @return the line number for given location
     */
    int locationLineNumber(L location);
}
