/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.configure.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import com.oracle.svm.configure.ConfigurationUsageException;
import com.oracle.svm.configure.command.sbom.SbomExtractLibrary;

public final class ConfigurationCommandExtractSbom extends ConfigurationCommand {

    private static final String IMAGE_PATH_OPT = "--image-path";
    private static final String EXTRACT_CMD = "extract-sbom";

    @Override
    public String getName() {
        return EXTRACT_CMD;
    }

    @Override
    public void apply(Iterator<String> argumentsIterator) throws IOException {
        Path imagePath = null;
        while (argumentsIterator.hasNext()) {
            String[] optionValue = argumentsIterator.next().split(OPTION_VALUE_SEP, OPTION_VALUE_LENGTH);
            String option = optionValue[OPTION_INDEX];
            String value = (optionValue.length > 1) ? optionValue[VALUE_INDEX] : null;
            switch (option) {
                case IMAGE_PATH_OPT:
                    imagePath = requirePath(option, value);
                    break;
            }
        }
        if (imagePath == null) {
            throw new ConfigurationUsageException("Argument must be provided for: " + IMAGE_PATH_OPT);
        }
        if (!Files.exists(imagePath)) {
            throw new ConfigurationUsageException("Binary does not exist or is not readable: " + imagePath);
        }
        int exitCode = SbomExtractLibrary.extractSbom(imagePath);
        if (exitCode != 0) {
            throw new RuntimeException("Failed to extract SBOM. See previous messages for defails.");
        }
    }

    @Override
    public String getUsage() {
        return String.format("native-image-utils %s %s=<native-binary>", EXTRACT_CMD, IMAGE_PATH_OPT);
    }

    @Override
    protected String getDescription0() {
        return """
                                      extracts an embedded SBOM from a native image binary.
                            --image-path=<path>
                                                  the path to the binary where the SBOM is embedded.
                        """.replace("\n", System.lineSeparator());
    }
}
