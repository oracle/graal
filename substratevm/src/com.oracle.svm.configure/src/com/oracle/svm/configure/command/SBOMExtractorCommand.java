/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import com.oracle.svm.configure.ConfigurationUsageException;
import com.oracle.svm.configure.extractor.ResourceExtractor;

/**
 * Implements an `extract-sbom` command for extracting embedded SBOMs from native images.
 *
 * Embedded SBOMs are stored in gzip format and have two associated exported symbols. One symbol
 * points to the start of the SBOM ("sbom") and another symbol points to the size of the SBOM
 * ("sbom_length").
 *
 * To extract an embedded SBOM:
 *
 * <pre>
 * // Prints SBOM content to stdout
 * $ native-image-utils extract-sbom --image-path=<image-path>
 *
 * // Writes SBOM content to the output file
 * $ native-image-utils extract-sbom --image-path=<image-path> --output-file=<output-path>
 * </pre>
 *
 * Note: If the need arises to extract other resources that are embedded in images, we can easily
 * implement that since the {@link ResourceExtractor} is already symbol-agnostic. But since SBOMs
 * are currently the only resource that is embedded, we made the command SBOM specific to keep the
 * API descriptive and minimal.
 */
public class SBOMExtractorCommand extends ConfigurationCommand {
    private static final String OPTION_IMAGE_PATH = "--image-path";
    private static final String OPTION_OUTPUT_FILE = "--output-file";
    private static final String SBOM_SYMBOL = "sbom";

    private record Arguments(Path imagePath, Path outputPath) {
        static Arguments parse(Iterator<String> argumentsIterator) throws IOException {
            Arguments args = parseImpl(argumentsIterator);
            validate(args);
            return args;
        }

        private static Arguments parseImpl(Iterator<String> argumentsIterator) throws IOException {
            Path imagePath = null;
            Path outputPath = null;

            while (argumentsIterator.hasNext()) {
                String argument = argumentsIterator.next();
                String[] parts = argument.split("=", 2);
                if (parts.length != 2) {
                    throw new ConfigurationUsageException(String.format("Invalid option format: %s", argument));
                }
                String option = parts[0];
                String value = parts[1];
                switch (option) {
                    case OPTION_IMAGE_PATH -> imagePath = Path.of(value);
                    case OPTION_OUTPUT_FILE -> outputPath = getOrCreateFile(option, value);
                    default -> throw new ConfigurationUsageException(String.format("Unknown option: %s", option));
                }
            }

            return new Arguments(imagePath, outputPath);
        }

        private static void validate(Arguments args) throws IOException {
            if (args.imagePath == null) {
                throw new ConfigurationUsageException(String.format(
                                "The image path was not specified. Specify the image path using the %s=<path> option.", OPTION_IMAGE_PATH));
            }
            if (!Files.exists(args.imagePath)) {
                throw new ConfigurationUsageException(String.format("The image path does not exist: %s", args.imagePath));
            }
            if (Files.isDirectory(args.imagePath)) {
                throw new ConfigurationUsageException(String.format("The image path points to a directory: %s", args.imagePath));
            }
            if (Files.size(args.imagePath) == 0) {
                throw new ConfigurationUsageException(String.format("The image path points to an empty file: %s", args.imagePath));
            }
        }
    }

    @Override
    public String getName() {
        return "extract-sbom";
    }

    @Override
    public void apply(Iterator<String> argumentsIterator) throws IOException {
        Arguments args = Arguments.parse(argumentsIterator);

        try {
            extractEmbeddedSBOM(args);
        } catch (IOException | ConfigurationUsageException e) {
            String errorMessage = """
                            Failed to extract embedded SBOM from '%s'. Reason: %s

                            Verify the following:
                            - The file was generated by GraalVM Native Image.
                            - The SBOM feature was enabled when building the image.

                            Oracle GraalVM 25 and above embeds SBOMs by default. For earlier Oracle GraalVM versions, ensure '--enable-sbom' is passed to 'native-image'.
                            """
                            .formatted(args.imagePath, e.getMessage());
            throw new ConfigurationUsageException(errorMessage);
        }
    }

    private static void extractEmbeddedSBOM(Arguments args) throws IOException {
        if (args.outputPath == null) {
            ResourceExtractor.extract(args.imagePath, SBOM_SYMBOL, System.out);
        } else {
            try (OutputStream out = Files.newOutputStream(args.outputPath)) {
                ResourceExtractor.extract(args.imagePath, SBOM_SYMBOL, out);
            }
        }
    }

    @Override
    protected String getDescription0() {
        return String.format("""
                                      extract embedded SBOMs from native images.
                            %s=<path>
                                                  the path to the Native Image executable or shared library
                                                  containing the embedded SBOM.
                            %s=<path>
                                                  writes the extracted SBOM to <path> if specified;
                                                  otherwise to stdout.
                        """, OPTION_IMAGE_PATH, OPTION_OUTPUT_FILE).replace("\n", System.lineSeparator());
    }
}
