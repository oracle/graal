/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso;

import com.oracle.truffle.api.Option;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.*;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Option.Group(EspressoLanguage.ID)
public final class EspressoOptions {

    private static final Path EMPTY = Paths.get("");

    private static final OptionType<List<Path>> PATHS_OPTION_TYPE = new OptionType<>("Paths",
                    new Function<String, List<Path>>() {
                        @Override
                        public List<Path> apply(String paths) {
                            try {
                                return Arrays.stream(paths.split(File.pathSeparator)).map(Paths::get).collect(Collectors.toList());
                            } catch (InvalidPathException e) {
                                throw new IllegalArgumentException(e);
                            }
                        }
                    });

    private static final OptionType<Path> PATH_OPTION_TYPE = new OptionType<>("Path",
                    new Function<String, Path>() {
                        @Override
                        public Path apply(String path) {
                            try {
                                return Paths.get(path);
                            } catch (InvalidPathException e) {
                                throw new IllegalArgumentException(e);
                            }
                        }
                    });

    @Option(help = "User-defined system properties.", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    public static final OptionKey<OptionMap<String>> Properties = OptionKey.mapOf(String.class);

    // Injecting java.io.File.pathSeparator is a hack for OptionProcessor.
    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories, JAR archives, and ZIP archives to search for class files.", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    public static final OptionKey<List<Path>> Classpath = new OptionKey<>(Collections.emptyList(), PATHS_OPTION_TYPE);

    @Option(help = "Installation directory for Java Runtime Environment (JRE)", category = OptionCategory.EXPERT, stability = OptionStability.STABLE) //
    public static final OptionKey<Path> JavaHome = new OptionKey<>(EMPTY, PATH_OPTION_TYPE);

    // Injecting java.io.File.pathSeparator is a hack for OptionProcessor.
    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories to search for user Espresso native libraries.", category = OptionCategory.EXPERT, stability = OptionStability.STABLE) //
    public static final OptionKey<List<Path>> EspressoLibraryPath = new OptionKey<>(Collections.emptyList(), PATHS_OPTION_TYPE);

    // Injecting java.io.File.pathSeparator is a hack for OptionProcessor.
    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories, JAR files, and ZIP archives to search for boot class files. These are used in place of the boot class files included in the JDK.", category = OptionCategory.EXPERT) //
    public static final OptionKey<List<Path>> BootClasspath = new OptionKey<>(Collections.emptyList(), PATHS_OPTION_TYPE);

    // Injecting java.io.File.pathSeparator is a hack for OptionProcessor.
    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories, JAR files, and ZIP archives to append to the front of the default bootstrap class path.", category = OptionCategory.EXPERT) //
    public static final OptionKey<List<Path>> BootClasspathAppend = new OptionKey<>(Collections.emptyList(), PATHS_OPTION_TYPE);

    // Injecting java.io.File.pathSeparator is a hack for OptionProcessor.
    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories, JAR files, and ZIP archives to prepend to the front of the default bootstrap class path.", category = OptionCategory.EXPERT) //
    public static final OptionKey<List<Path>> BootClasspathPrepend = new OptionKey<>(Collections.emptyList(), PATHS_OPTION_TYPE);

    // Injecting java.io.File.pathSeparator is a hack for OptionProcessor.
    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories to search for user libraries.", category = OptionCategory.EXPERT, stability = OptionStability.STABLE) //
    public static final OptionKey<List<Path>> JavaLibraryPath = new OptionKey<>(Collections.emptyList(), PATHS_OPTION_TYPE);

    // Injecting java.io.File.pathSeparator is a hack for OptionProcessor.
    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories to search for system libraries.", category = OptionCategory.EXPERT, stability = OptionStability.STABLE) //
    public static final OptionKey<List<Path>> BootLibraryPath = new OptionKey<>(Collections.emptyList(), PATHS_OPTION_TYPE);

    // Injecting java.io.File.pathSeparator is a hack for OptionProcessor.
    @Option(help = "A \" + java.io.File.pathSeparator + \" separated list of directories to search for extensions.", category = OptionCategory.EXPERT, stability = OptionStability.STABLE) //
    public static final OptionKey<List<Path>> ExtDirs = new OptionKey<>(Collections.emptyList(), PATHS_OPTION_TYPE);

    @Option(help = "Enable assertions.", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> EnableAssertions = new OptionKey<>(false);

    @Option(help = "Enable system assertions.", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> EnableSystemAssertions = new OptionKey<>(false);

    public enum VerifyMode {
        NONE,
        REMOTE, // Verifies all bytecodes not loaded by the bootstrap class loader.
        ALL
    }

    private static final OptionType<VerifyMode> VERIFY_MODE_OPTION_TYPE = new OptionType<>("VerifyMode",
                    new Function<String, VerifyMode>() {
                        @Override
                        public VerifyMode apply(String s) {
                            try {
                                return VerifyMode.valueOf(s.toUpperCase());
                            } catch (IllegalArgumentException e) {
                                throw new IllegalArgumentException("-Xverify: Mode can be 'none', 'remote' or 'all'.");
                            }
                        }
                    });

    @Option(help = "Sets the mode of the bytecode verifier.", category = OptionCategory.EXPERT) //
    public static final OptionKey<VerifyMode> Verify = new OptionKey<>(VerifyMode.REMOTE, VERIFY_MODE_OPTION_TYPE);

    // Threads are enabled by default.
    public static final boolean ENABLE_THREADS = (System.getProperty("espresso.EnableThreads") == null) || Boolean.getBoolean("espresso.EnableThreads");

    public static final boolean RUNNING_ON_SVM = ImageInfo.inImageCode();

    public static final String INCEPTION_NAME = System.getProperty("espresso.inception.name", "#");
}
