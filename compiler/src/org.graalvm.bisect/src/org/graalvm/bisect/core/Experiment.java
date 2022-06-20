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

import java.util.List;
import java.util.Map;

import org.graalvm.bisect.util.Writer;

/**
 * A parsed experiment consisting of all graal-compiled methods and metadata.
 */
public interface Experiment {
    /**
     * Gets the experiment ID.
     * @return the experiment ID
     */
    ExperimentId getExperimentId();

    /**
     * Gets the execution ID.
     * @return the execution ID
     */
    String getExecutionId();

    /**
     * Gets the total period of all executed methods including non-graal executions.
     * @return the total period of execution
     */
    long getTotalPeriod();

    /**
     * Gets the sum of periods of all graal-compiled methods.
     * @return the total period of graal-compiled methods
     */
    long getGraalPeriod();

    /**
     * Gets the list of all graal-compiled methods, including non-hot methods.
     * @return the list of graal-compiled methods
     */
    List<ExecutedMethod> getExecutedMethods();

    /**
     * Groups hot graal-compiled methods by compilation method name.
     * @see ExecutedMethod#getCompilationMethodName()
     * @return a map of lists of executed methods grouped by compilation method name
     */
    Map<String, List<ExecutedMethod>> groupHotMethodsByName();

    /**
     * Gets a list of methods with the given compilation method name.
     * @param compilationMethodName the compilation method name
     * @return a list of methods with matching compilation method name
     */
    List<ExecutedMethod> getMethodsByName(String compilationMethodName);

    /**
     * Writes a summary of the experiment. Includes the number of methods collected (proftool and
     * optimization log), relative period of graal-compiled methods, the number and relative period
     * of hot methods.
     * 
     * @param writer the destination writer
     */
    void writeExperimentSummary(Writer writer);

    /**
     * Writes the list of compilations (including compilation ID, share of the execution and hotness
     * for each compilation) for a method with header identifying this experiment.
     * 
     * @param writer the destination writer
     * @param compilationMethodName the compilation method name to summarize
     */
    void writeMethodCompilationList(Writer writer, String compilationMethodName);
}
