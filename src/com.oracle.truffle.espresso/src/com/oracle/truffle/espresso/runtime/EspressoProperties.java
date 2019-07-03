/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Engine;

import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.Utils;
import com.oracle.truffle.espresso.meta.EspressoError;

public interface EspressoProperties {

    String CPU_ARCH = System.getProperty("os.arch");

    String VM_SPECIFICATION_VERSION = "1.8";
    String VM_SPECIFICATION_NAME = "Java Virtual Machine Specification";
    String VM_SPECIFICATION_VENDOR = "Oracle Corporation";

    String VM_VERSION = "1.8.0_212";
    String VM_VENDOR = "Oracle Corporation";
    String VM_NAME = "Espresso 64-Bit VM";
    String VM_INFO = "mixed mode";

    Path javaHome();

    List<Path> classpath();

    List<Path> bootClasspath();

    List<Path> javaLibraryPath();

    List<Path> bootLibraryPath();

    List<Path> extDirs();

    List<Path> espressoLibraryPath();

    final class Builder {

        private Path javaHome;
        private List<Path> espressoLibraryPath;
        private List<Path> classpath;
        private List<Path> bootClasspath;
        private List<Path> javaLibraryPath;
        private List<Path> bootLibraryPath;
        private List<Path> extDirs;

        // TODO(peterssen): These paths are Linux-specific. Refactor these hardcoded paths to work
        // on differnt platforms/OSs.
        private final Path SYS_EXT_DIR = Paths.get("/usr/java/packages");
        private final Path EXTENSIONS_DIR = Paths.get("/lib/ext");
        private final List<Path> DEFAULT_LIBPATH = Arrays.asList(
                        Paths.get("/usr/lib64"),
                        Paths.get("/lib64"),
                        Paths.get("/lib"),
                        Paths.get("/usr/lib"));

        public Builder javaHome(Path newJavaHome) {
            this.javaHome = newJavaHome;
            return this;
        }

        public Path javaHome() {
            return javaHome != null
                            ? javaHome
                            : Engine.findHome().resolve("jre");
        }

        public Builder bootClasspath(List<Path> newBootClasspath) {
            this.bootClasspath = newBootClasspath;
            return this;
        }

        public List<Path> bootClasspath() {
            return bootClasspath != null
                            ? bootClasspath
                            : Stream.of(
                                            Paths.get("lib", "resources.jar"),
                                            Paths.get("lib", "rt.jar"),
                                            Paths.get("lib", "sunrsasign.jar"),
                                            Paths.get("lib", "jsse.jar"),
                                            Paths.get("lib", "jce.jar"),
                                            Paths.get("lib", "charsets.jar"),
                                            Paths.get("lib", "jfr.jar"),
                                            Paths.get("classes")).map(p -> javaHome().resolve(p)).collect(Collectors.toList());
        }

        public Builder classpath(List<Path> newClasspath) {
            this.classpath = newClasspath;
            return this;
        }

        public List<Path> classpath() {
            return classpath;

        }

        public Builder javaLibraryPath(List<Path> newJavaLibraryPath) {
            this.javaLibraryPath = newJavaLibraryPath;
            return this;
        }

        public List<Path> javaLibraryPath() {
            if (javaLibraryPath != null) {
                return javaLibraryPath;
            }
            List<Path> paths = new ArrayList<>();
            paths.add(SYS_EXT_DIR.resolve("lib").resolve(CPU_ARCH));
            paths.addAll(DEFAULT_LIBPATH);
            return paths;

        }

        public Builder bootLibraryPath(List<Path> newBootLibraryPath) {
            this.bootLibraryPath = newBootLibraryPath;
            return this;
        }

        public List<Path> bootLibraryPath() {
            return bootLibraryPath != null
                            ? bootLibraryPath
                            : Collections.singletonList(javaHome().resolve("lib").resolve(CPU_ARCH));
        }

        public Builder extDirs(List<Path> newExtDirs) {
            this.extDirs = newExtDirs;
            return this;
        }

        public List<Path> extDirs() {
            return extDirs != null
                            ? extDirs
                            : Arrays.asList(
                                            javaHome().resolve(EXTENSIONS_DIR),
                                            SYS_EXT_DIR.resolve(EXTENSIONS_DIR));
        }

        public Builder espressoLibraryPath(List<Path> newEspressoLibraryPath) {
            this.espressoLibraryPath = newEspressoLibraryPath;
            return this;
        }

        public List<Path> espressoLibraryPath() {
            return espressoLibraryPath;
        }

        public EspressoProperties build() {
            return new EspressoProps(this);
        }

        public Builder processOptions(OptionValues options) {
            // Always set JavaHome first.
            if (options.hasBeenSet(EspressoOptions.JavaHome)) {
                javaHome(options.get(EspressoOptions.JavaHome));
            }

            if (options.hasBeenSet(EspressoOptions.EspressoLibraryPath)) {
                espressoLibraryPath(options.get(EspressoOptions.EspressoLibraryPath));
            }

            EspressoError.guarantee(options.hasBeenSet(EspressoOptions.Classpath), "Classpath must be defined");
            classpath(options.get(EspressoOptions.Classpath));

            {
                // Process boot classpath + append and prepend options.
                List<Path> bootClasspath_ = new ArrayList<>(bootClasspath());
                if (options.hasBeenSet(EspressoOptions.BootClasspath)) {
                    bootClasspath_ = options.get(EspressoOptions.BootClasspath);
                }
                if (options.hasBeenSet(EspressoOptions.BootClasspathAppend)) {
                    bootClasspath_.addAll(options.get(EspressoOptions.BootClasspathAppend));
                }
                if (options.hasBeenSet(EspressoOptions.BootClasspathPrepend)) {
                    bootClasspath_.addAll(0, options.get(EspressoOptions.BootClasspathPrepend));
                }
                bootClasspath(bootClasspath_);
            }

            if (options.hasBeenSet(EspressoOptions.JavaLibraryPath)) {
                javaLibraryPath(options.get(EspressoOptions.JavaLibraryPath));
            }

            if (options.hasBeenSet(EspressoOptions.ExtDirs)) {
                extDirs(options.get(EspressoOptions.ExtDirs));
            }

            if (options.hasBeenSet(EspressoOptions.BootLibraryPath)) {
                bootLibraryPath(options.get(EspressoOptions.BootLibraryPath));
            }

            if (options.hasBeenSet(EspressoOptions.EspressoLibraryPath)) {
                espressoLibraryPath(options.get(EspressoOptions.EspressoLibraryPath));
            }

            if (options.hasBeenSet(EspressoOptions.EspressoLibraryPath)) {
                espressoLibraryPath(options.get(EspressoOptions.EspressoLibraryPath));
            }

            return this;
        }
    }

    static Builder inheritFromHostVM() {
        return new Builder() //
                        .javaHome(Paths.get(System.getProperty("java.home"))) //
                        .bootClasspath(Utils.parsePaths(System.getProperty("sun.boot.class.path"))) //
                        .javaLibraryPath(Utils.parsePaths(System.getProperty("java.library.path"))) //
                        .bootLibraryPath(Utils.parsePaths(System.getProperty("sun.boot.library.path"))) //
                        .extDirs(Utils.parsePaths(System.getProperty("java.ext.dirs"))) //
                        .espressoLibraryPath(Utils.parsePaths(System.getProperty("espresso.library.path")));
    }
}

final class EspressoProps implements EspressoProperties {
    private final Path javaHome;
    private final List<Path> classpath;
    private final List<Path> bootClasspath;
    private final List<Path> javaLibraryPath;
    private final List<Path> bootLibraryPath;
    private final List<Path> extDirs;
    private final List<Path> espressoLibraryPath;

    EspressoProps(Builder builder) {
        this.javaHome = Objects.requireNonNull(builder.javaHome());
        // TODO(peterssen): Immutable.
        this.classpath = Objects.requireNonNull(builder.classpath());
        this.bootClasspath = Objects.requireNonNull(builder.bootClasspath());
        this.javaLibraryPath = Objects.requireNonNull(builder.javaLibraryPath());
        this.bootLibraryPath = Objects.requireNonNull(builder.bootLibraryPath());
        this.extDirs = Objects.requireNonNull(builder.extDirs());
        this.espressoLibraryPath = Objects.requireNonNull(builder.espressoLibraryPath());
    }

    @Override
    public Path javaHome() {
        return javaHome;
    }

    @Override
    public List<Path> classpath() {
        return classpath;
    }

    @Override
    public List<Path> bootClasspath() {
        return bootClasspath;
    }

    @Override
    public List<Path> javaLibraryPath() {
        return javaLibraryPath;
    }

    @Override
    public List<Path> bootLibraryPath() {
        return bootLibraryPath;
    }

    @Override
    public List<Path> extDirs() {
        return extDirs;
    }

    @Override
    public List<Path> espressoLibraryPath() {
        return espressoLibraryPath;
    }
}
