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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
     * The path to the directory which contains the optimization logs of this experiment.
     */
    private final String optimizationLogPath;

    /**
     * The file path to the JSON proftool output (mx profjson) of this experiment.
     */
    private final String proftoolOutputPath;

    /**
     * Constructs experiment files from paths to the proftool output and the directory with an
     * optimization log.
     *
     * @param experimentId the ID of this experiment
     * @param proftoolOutputPath the file path to the JSON proftool output (mx profjson)
     * @param optimizationLogPath the path to the directory which contains the optimization logs
     */
    public ExperimentFilesImpl(ExperimentId experimentId, String proftoolOutputPath, String optimizationLogPath) {
        this.experimentId = experimentId;
        this.optimizationLogPath = optimizationLogPath;
        this.proftoolOutputPath = proftoolOutputPath;
    }

    @Override
    public NamedReader getProftoolOutput() throws FileNotFoundException {
        return new NamedReader(proftoolOutputPath, new FileReader(proftoolOutputPath));
    }

    /**
     * Gets the list of readers reading an optimization log. Each reader describes one compiled
     * method. Individual optimization logs are discovered by listing the files in the provided
     * {@link #optimizationLogPath}.
     *
     * The optimization log may sometimes produce an empty file. In that case, the file skipped and
     * a warning is printed to stderr.
     *
     * @return the list of readers each reading an optimization log
     */
    @Override
    public List<NamedReader> getOptimizationLogs() throws IOException {
        File[] files = new File(optimizationLogPath).listFiles();
        if (files == null) {
            throw new IOException("The provided optimization log path does not denote a directory");
        }
        List<NamedReader> readers = new ArrayList<>();
        for (File file : files) {
            if (file.length() == 0) {
                System.err.println("Warning: The file " + file.getPath() + " is empty.");
                continue;
            }
            readers.add(new NamedReader(file.getPath(), new FileReader(file)));
        }
        return readers;
    }

    @Override
    public ExperimentId getExperimentId() {
        return experimentId;
    }
}
