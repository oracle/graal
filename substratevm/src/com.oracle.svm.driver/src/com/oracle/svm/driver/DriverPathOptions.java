/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class DriverPathOptions {

    private enum ValueKind {
        SINGLE_PATH,
        PATH_LIST
    }

    enum Option {
        CLASSPATH(ValueKind.PATH_LIST, true, "-cp", "-classpath", "--class-path") {
            @Override
            void consume(NativeImage nativeImage, String optionSpelling, String rawValue) {
                if (rawValue == null || optionSpelling.endsWith("=") && rawValue.isEmpty()) {
                    NativeImage.showError(optionSpelling + " requires class path specification");
                }
                for (String cp : rawValue.split(File.pathSeparator)) {
                    String cpEntry = cp.isEmpty() ? "." : cp;
                    nativeImage.addCustomImageClasspath(cpEntry);
                }
            }
        },
        MODULE_PATH(ValueKind.PATH_LIST, false, "-p", "--module-path") {
            @Override
            void consume(NativeImage nativeImage, String optionSpelling, String rawValue) {
                if (rawValue == null) {
                    NativeImage.showError(optionSpelling + " requires module path specification");
                }
                for (String mpEntry : rawValue.split(File.pathSeparator)) {
                    nativeImage.addImageModulePath(Paths.get(mpEntry), false, true);
                }
            }
        },
        CONFIGURATIONS_PATH(ValueKind.PATH_LIST, false, "--configurations-path") {
            @Override
            void consume(NativeImage nativeImage, String optionSpelling, String rawValue) {
                if (rawValue == null) {
                    NativeImage.showError(optionSpelling + " requires a " + File.pathSeparator + " separated list of directories");
                }
                for (String configDir : rawValue.split(File.pathSeparator)) {
                    nativeImage.addMacroOptionRoot(Paths.get(configDir));
                }
            }
        },
        JAR(ValueKind.SINGLE_PATH, false, "-jar") {
            @Override
            void consume(NativeImage nativeImage, String optionSpelling, String rawValue) {
                if (rawValue == null) {
                    NativeImage.showError(requireValidJarFileMessage);
                }
                Path jarFilePath = nativeImage.canonicalize(Paths.get(rawValue));
                if (Files.isDirectory(jarFilePath)) {
                    NativeImage.showError(jarFilePath + " is a directory. (" + requireValidJarFileMessage + ")");
                }
                String jarFileName = jarFilePath.getFileName().toString();
                String jarFileNameBase = jarFileName.endsWith(NativeImage.JAR_FILE_EXTENSION) ? jarFileName.substring(0, jarFileName.length() - NativeImage.JAR_FILE_EXTENSION.length()) : jarFileName;
                if (!jarFileNameBase.isEmpty()) {
                    String origin = "manifest from " + jarFilePath.toUri();
                    nativeImage.addPlainImageBuilderArg(nativeImage.oHName + jarFileNameBase, origin, false);
                }
                Path finalFilePath = nativeImage.useBundle() ? nativeImage.bundleSupport.substituteClassPath(jarFilePath) : jarFilePath;
                if (!NativeImage.processJarManifestMainAttributes(finalFilePath, nativeImage::handleManifestFileAttributes)) {
                    NativeImage.showError("No manifest in " + finalFilePath);
                }
                nativeImage.addCustomImageClasspath(finalFilePath);
                nativeImage.setJarOptionMode(true);
            }
        };

        private final ValueKind valueKind;
        private final boolean allowInlineEquals;
        private final List<String> spellings;

        Option(ValueKind valueKind, boolean allowInlineEquals, String... spellings) {
            this.valueKind = valueKind;
            this.allowInlineEquals = allowInlineEquals;
            this.spellings = List.of(spellings);
        }

        abstract void consume(NativeImage nativeImage, String optionSpelling, String rawValue);

        /**
         * Rewrites a raw option value from the bundle source platform to the replay platform.
         * Aggregate path options are split with the source platform separator and re-joined with
         * the local platform separator.
         */
        String rewriteValue(String rawValue, BundlePathMap.PathStyle sourcePathStyle, Function<String, String> pathRewriter) {
            if (valueKind == ValueKind.SINGLE_PATH) {
                return pathRewriter.apply(rawValue);
            }
            String sourceDelimiter = sourcePathStyle == BundlePathMap.PathStyle.Windows ? ";" : ":";
            return Arrays.stream(rawValue.split(Pattern.quote(sourceDelimiter), -1)).map(pathRewriter).collect(Collectors.joining(File.pathSeparator));
        }
    }

    record Match(Option option, String spelling, String inlineValue) {
        boolean consumesNextArg() {
            return inlineValue == null;
        }

        /**
         * Rewrites the matched option value while preserving the option's original aggregate
         * semantics.
         */
        String rewriteValue(String rawValue, BundlePathMap.PathStyle sourcePathStyle, Function<String, String> pathRewriter) {
            return option.rewriteValue(rawValue, sourcePathStyle, pathRewriter);
        }

        String renderInlineArgument(String rewrittenValue) {
            return spelling + rewrittenValue;
        }

        /**
         * Consumes the matched option from the argument queue and dispatches its raw value to the
         * option-specific semantic handler.
         */
        void consume(NativeImage nativeImage, NativeImage.ArgumentQueue args) {
            args.poll();
            option.consume(nativeImage, spelling, inlineValue != null ? inlineValue : args.poll());
        }
    }

    private static final String requireValidJarFileMessage = "-jar requires a valid jarfile";

    private static final List<Option> allOptions = List.of(Option.CLASSPATH, Option.MODULE_PATH, Option.CONFIGURATIONS_PATH, Option.JAR);
    private static final List<Option> defaultOptions = List.of(Option.CLASSPATH, Option.MODULE_PATH, Option.JAR);
    private static final List<Option> cmdLineOptions = List.of(Option.CONFIGURATIONS_PATH);

    private DriverPathOptions() {
    }

    /**
     * Matches any non-API path-taking driver option that can appear in bundle replay input.
     */
    static Match matchAny(String headArg) {
        return match(headArg, allOptions);
    }

    /**
     * Matches non-API path-taking options handled by {@link DefaultOptionHandler}.
     */
    static Match matchDefault(String headArg) {
        return match(headArg, defaultOptions);
    }

    /**
     * Matches non-API path-taking options handled by {@link CmdLineOptionHandler}.
     */
    static Match matchCmdLine(String headArg) {
        return match(headArg, cmdLineOptions);
    }

    private static Match match(String headArg, List<Option> options) {
        for (Option option : options) {
            for (String spelling : option.spellings) {
                if (headArg.equals(spelling)) {
                    return new Match(option, spelling, null);
                }
                if (option.allowInlineEquals && headArg.startsWith(spelling + "=")) {
                    return new Match(option, spelling + "=", headArg.substring(spelling.length() + 1));
                }
            }
        }
        return null;
    }
}
