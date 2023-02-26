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
package org.graalvm.profdiff.parser.experiment;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Optional;

import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.ExperimentId;

/**
 * Represents a list of files from which an experiment is parsed.
 */
public interface ExperimentFiles {
    /**
     * Gets the ID of the experiment to which the files belong.
     *
     * @return the ID of the experiment
     */
    ExperimentId getExperimentId();

    /**
     * Gets a file view containing the JSON output of proftool (mx profjson). Returns
     * {@link Optional#empty()} if the experiment is not associated with a proftool output.
     *
     * @return the file with the proftool output
     */
    Optional<FileView> getProftoolOutput() throws FileNotFoundException;

    /**
     * Gets an iterable of file views representing the optimization log. Each file view may contain
     * several JSON-encoded compilation units separated by a {@code '\n'}.
     *
     * @return an iterable of file views representing an optimization log
     */
    Iterable<FileView> getOptimizationLogs() throws IOException;

    /**
     * Gets whether the experiment was compiled just-in-time or ahead-of-time.
     */
    Experiment.CompilationKind getCompilationKind();
}
