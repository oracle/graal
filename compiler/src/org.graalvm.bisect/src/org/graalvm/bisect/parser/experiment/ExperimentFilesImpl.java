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
package org.graalvm.bisect.parser.experiment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.bisect.core.ExperimentId;

/**
 * Represents files belonging to a single experiment.
 */
public class ExperimentFilesImpl implements ExperimentFiles {
    public ExperimentId getExperimentId() {
        return experimentId;
    }

    private final ExperimentId experimentId;
    private final String optimizationLogPath;
    private final String proftoolOutputPath;

    /**
     * Constructs experiment files from paths to the proftool output and the directory with an
     * optimization log.
     *
     * @param experimentId the ID of this experiment
     * @param proftoolOutputPath the file path to the JSON proftool output (mx profjson)
     * @param optimizationLogPath the path to the directory which contains optimization logs of the
     *            same execution
     */
    public ExperimentFilesImpl(ExperimentId experimentId, String proftoolOutputPath, String optimizationLogPath) {
        this.experimentId = experimentId;
        this.optimizationLogPath = optimizationLogPath;
        this.proftoolOutputPath = proftoolOutputPath;
    }

    public Reader getProftoolOutput() throws FileNotFoundException {
        return new FileReader(proftoolOutputPath);
    }

    public List<Reader> getOptimizationLogs() throws IOException {
        File[] files = new File(optimizationLogPath).listFiles();
        if (files == null) {
            throw new IOException("The provided optimization log path does not denote a directory");
        }
        List<Reader> readers = new ArrayList<>();
        for (File file : files) {
            readers.add(new FileReader(file));
        }
        return readers;
    }
}
