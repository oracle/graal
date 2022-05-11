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

import org.graalvm.bisect.core.optimization.OptimizationPhase;


/**
 * Builder for an executed method. Used during parsing to build a complete executed method.
 * @see ExecutedMethod
 */
public class ExecutedMethodBuilder {
    public void setCompilationId(String compilationId) {
        this.compilationId = compilationId;
    }

    public void setCompilationMethodName(String compilationMethodName) {
        this.compilationMethodName = compilationMethodName;
    }

    public void setPeriod(long period) {
        this.period = period;
    }

    public String getCompilationId() {
        return compilationId;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public void setExperiment(Experiment experiment) {
        this.experiment = experiment;
    }

    private String executionId;
    private String compilationId;
    private String compilationMethodName;
    private OptimizationPhase rootPhase;
    private Experiment experiment;

    /**
     * The period of execution of this method as reported by proftool. If not explicitly set, assume that the method was
     * not executed.
     */
    private long period = 0;

    public ExecutedMethod build() {
        assert compilationId != null;
        assert compilationMethodName != null;
        assert experiment != null;
        return new ExecutedMethodImpl(compilationId, compilationMethodName, rootPhase, period, experiment);
    }

    public void setRootPhase(OptimizationPhase rootPhase) {
        this.rootPhase = rootPhase;
    }
}
