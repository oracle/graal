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
package org.graalvm.bisect.matching.method;

import org.graalvm.bisect.util.Writer;
import org.graalvm.bisect.core.ExecutedMethod;
import org.graalvm.bisect.core.ExperimentId;

/**
 * Represents a Java method that was not matched with any other Java method from the other experiment.
 */
public class ExtraMethod {
    /**
     * Gets the ID of the experiment to which this Java method belongs.
     *
     * @return the ID of the experiment of this Java method
     */
    public ExperimentId getExperimentId() {
        return experimentId;
    }

    private final ExperimentId experimentId;

    /**
     * Gets the compilation method name of this Java method.
     *
     * @return the compilation name of this method
     * @see ExecutedMethod#getCompilationMethodName()
     */
    public String getCompilationMethodName() {
        return compilationMethodName;
    }

    private final String compilationMethodName;

    ExtraMethod(ExperimentId experimentId, String compilationMethodName) {
        this.experimentId = experimentId;
        this.compilationMethodName = compilationMethodName;
    }

    /**
     * Writes a string describing that this method appeared only in one of the experiments.
     * @param writer the destination writer
     */
    public void writeHeader(Writer writer) {
        writer.writeln("Method " + compilationMethodName + " is only in experiment " + experimentId);
    }
}
