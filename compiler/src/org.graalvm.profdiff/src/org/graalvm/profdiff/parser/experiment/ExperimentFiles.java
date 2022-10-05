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
import java.io.Reader;
import java.util.List;

import org.graalvm.profdiff.core.ExperimentId;

/**
 * Represents a list of files from which an experiment is parsed.
 */
public interface ExperimentFiles {

    /**
     * {@link Reader A reader} with a name to facilitate better error messages.
     */
    class NamedReader {
        private final String name;

        private final Reader reader;

        public NamedReader(String name, Reader reader) {
            this.name = name;
            this.reader = reader;
        }

        /**
         * Gets the name of the reader.
         */
        public String getName() {
            return name;
        }

        /**
         * Gets the reader.
         */
        public Reader getReader() {
            return reader;
        }
    }

    /**
     * Gets the ID of the experiment to which the files belong.
     *
     * @return the ID of the experiment
     */
    ExperimentId getExperimentId();

    /**
     * Gets the {@link NamedReader} reading the JSON output of proftool (mx profjson).
     *
     * @return the reader with the proftool output
     */
    NamedReader getProftoolOutput() throws FileNotFoundException;

    /**
     * Gets the list of readers reading an optimization log. Each reader describes one compiled
     * method.
     *
     * @return the list of readers each reading an optimization log
     */
    List<NamedReader> getOptimizationLogs() throws IOException;
}
