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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.ExperimentId;

/**
 * Represents files belonging to a single experiment.
 */
public class ExperimentFilesImpl implements ExperimentFiles {
    /**
     * The ID of this experiment.
     */
    private final ExperimentId experimentId;

    /**
     * The kind of compilation of this experiment, i.e., whether it was compiled just-in-time or
     * ahead-of-time.
     */
    private final Experiment.CompilationKind compilationKind;

    /**
     * The path to the directory which contains the optimization logs of this experiment.
     */
    private final String optimizationLogPath;

    /**
     * The file path to the JSON proftool output (mx profjson) of this experiment.
     */
    private final String proftoolOutputPath;

    /**
     * Constructs experiment files from paths with a proftool output and a directory with an
     * optimization log.
     *
     * @param experimentId the ID of this experiment
     * @param compilationKind the compilation kind of this experiment
     * @param proftoolOutputPath the file path to the JSON proftool output (mx profjson), can be
     *            {@code null} if the experiment does not have any associated proftool output
     * @param optimizationLogPath the path to the directory containing optimization logs
     */
    public ExperimentFilesImpl(ExperimentId experimentId, Experiment.CompilationKind compilationKind, String proftoolOutputPath, String optimizationLogPath) {
        this.experimentId = experimentId;
        this.compilationKind = compilationKind;
        this.optimizationLogPath = optimizationLogPath;
        this.proftoolOutputPath = proftoolOutputPath;
    }

    @Override
    public Optional<FileView> getProftoolOutput() {
        if (proftoolOutputPath == null) {
            return Optional.empty();
        }
        return Optional.of(FileView.fromFile(new File(proftoolOutputPath)));
    }

    /**
     * Gets an iterable of file views representing the optimization log. Each file view may contain
     * several JSON-encoded compilation units separated by a {@code '\n'}. Individual files are
     * discovered by listing the files in the provided {@link #optimizationLogPath}.
     *
     * @return an iterable of file views representing an optimization log
     * @throws IOException the optimization log is not a directory
     */
    @Override
    public Iterable<FileView> getOptimizationLogs() throws IOException {
        File[] files = new File(optimizationLogPath).listFiles();
        if (files == null) {
            throw new IOException("The provided optimization log path does not denote a directory");
        }
        return () -> Arrays.stream(files).map(FileView::fromFile).iterator();
    }

    @Override
    public Experiment.CompilationKind getCompilationKind() {
        return compilationKind;
    }

    @Override
    public ExperimentId getExperimentId() {
        return experimentId;
    }
}
