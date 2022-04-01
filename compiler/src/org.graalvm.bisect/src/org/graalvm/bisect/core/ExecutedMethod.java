/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.bisect.core;

import org.graalvm.bisect.core.optimization.Optimization;

import java.util.List;

/**
 * Represents a graal-compiled and executed method.
 */
public interface ExecutedMethod {
    /**
     * Gets the compilation ID of the executed method as reported in the optimization log. Matches "compileId" in the
     * proftool output.
     * @return the compilation ID
     */
    String getCompilationId();

    /**
     * Gets the full signature of the method including parameter types as reported in the optimization log. Multiple
     * executed methods may share the same compilation method name, because a method may get compiled several times. It\
     * can be used to identify a group of compilations of a single Java method.
     * @return the compilation method name
     */
    String getCompilationMethodName();

    /**
     * Gets the list of optimizations applied to this method during compilation.
     * @return the list of optimizations applied during compilation
     */
    List<Optimization> getOptimizations();

    /**
     * Gets the hot flag of the executed method. The hotness is not included in the proftool output.
     * @return the hot status of the method
     * @see HotMethodPolicy
     */
    boolean isHot();

    /**
     * Sets the hot status of the method.
     * @param hot the hot status of the method
     */
    void setHot(boolean hot);

    /**
     * Gets the period of execution as reported by proftool.
     * @return the period of execution
     */
    long getPeriod();
}
