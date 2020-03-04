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

import org.graalvm.options.OptionValues;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.Utils;
import com.oracle.truffle.espresso.meta.EspressoError;

/**
 * All VM properties are overridable via options, some properties (e.g. boot classpath) are derived
 * from <b>java.home</b> and will be updated accordingly when <b>java.home</b> is updated.
 *
 * The platform dependent bits are encapsulated away from the Espresso VM, future
 * OS/architecture-specific properties should be added here.
 */
public interface EspressoProperties {
    Path javaHome();

    Path espressoLibraryPath();

    List<Path> classpath();

    List<Path> bootClasspath();

    List<Path> javaLibraryPath();

    List<Path> bootLibraryPath();

    List<Path> extDirs();

    abstract class Builder {
        private Path javaHome;
        private Path espressoLibraryPath;
        private List<Path> classpath;
        private List<Path> bootClasspath;
        private List<Path> javaLibraryPath;
        private List<Path> bootLibraryPath;
        private List<Path> extDirs;

        abstract Path defaultJavaHome();

        abstract Path defaultEspressoLibraryPath();

        abstract List<Path> defaultClasspath();

        abstract List<Path> defaultBootClasspath();

        abstract List<Path> defaultJavaLibraryPath();

        abstract List<Path> defaultBootLibraryPath();

        abstract List<Path> defaultExtDirs();

        public Builder javaHome(Path newJavaHome) {
            this.javaHome = newJavaHome;
            return this;
        }

        public Path javaHome() {
            return javaHome != null ? javaHome : defaultJavaHome();
        }

        public Builder bootClasspath(List<Path> newBootClasspath) {
            this.bootClasspath = newBootClasspath;
            return this;
        }

        public List<Path> bootClasspath() {
            return bootClasspath != null ? bootClasspath : defaultBootClasspath();
        }

        public Builder classpath(List<Path> newClasspath) {
            this.classpath = newClasspath;
            return this;
        }

        public List<Path> classpath() {
            return classpath != null ? classpath : defaultClasspath();
        }

        public Builder javaLibraryPath(List<Path> newJavaLibraryPath) {
            this.javaLibraryPath = newJavaLibraryPath;
            return this;
        }

        public List<Path> javaLibraryPath() {
            return javaLibraryPath != null ? javaLibraryPath : defaultJavaLibraryPath();
        }

        public Builder bootLibraryPath(List<Path> newBootLibraryPath) {
            this.bootLibraryPath = newBootLibraryPath;
            return this;
        }

        public List<Path> bootLibraryPath() {
            return bootLibraryPath != null ? bootLibraryPath : defaultBootLibraryPath();
        }

        public Builder extDirs(List<Path> newExtDirs) {
            this.extDirs = newExtDirs;
            return this;
        }

        public List<Path> extDirs() {
            return extDirs != null ? extDirs : defaultExtDirs();
        }

        public Builder espressoLibraryPath(Path newEspressoLibraryPath) {
            this.espressoLibraryPath = newEspressoLibraryPath;
            return this;
        }

        public Path espressoLibraryPath() {
            return espressoLibraryPath != null ? espressoLibraryPath : defaultEspressoLibraryPath();
        }

        public EspressoProperties build() {
            return new EspressoProperties() {
                private final Path javaHome = Objects.requireNonNull(Builder.this.javaHome(), "javaHome not defined");
                private final List<Path> classpath = Objects.requireNonNull(Builder.this.classpath(), "classpath not defined");
                private final List<Path> bootClasspath = Objects.requireNonNull(Builder.this.bootClasspath(), "bootClasspath not defined");
                private final List<Path> javaLibraryPath = Objects.requireNonNull(Builder.this.javaLibraryPath(), "javaLibraryPath not defined");
                private final List<Path> bootLibraryPath = Objects.requireNonNull(Builder.this.bootLibraryPath(), "bootLibraryPath not defined");
                private final List<Path> extDirs = Objects.requireNonNull(Builder.this.extDirs(), "extDirs not defined");
                private final Path espressoLibraryPath = Objects.requireNonNull(Builder.this.espressoLibraryPath(), "espressoLibraryPath not defined");

                @Override
                public Path javaHome() {
                    return javaHome;
                }

                @Override
                public Path espressoLibraryPath() {
                    return espressoLibraryPath;
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
            };
        }
    }

    static Builder inheritFromHostVM() {
        return newPlatformBuilder() //
                        .javaHome(Paths.get(System.getProperty("java.home"))) //
                        .bootClasspath(Utils.parsePaths(System.getProperty("sun.boot.class.path"))) //
                        .javaLibraryPath(Utils.parsePaths(System.getProperty("java.library.path"))) //
                        .bootLibraryPath(Utils.parsePaths(System.getProperty("sun.boot.library.path"))) //
                        .extDirs(Utils.parsePaths(System.getProperty("java.ext.dirs")));
    }

    static Builder processOptions(EspressoLanguage language, Builder builder, OptionValues options) {
        // Always set JavaHome first.
        if (options.hasBeenSet(EspressoOptions.JavaHome)) {
            builder.javaHome(options.get(EspressoOptions.JavaHome));
        }

        if (options.hasBeenSet(EspressoOptions.EspressoLibraryPath)) {
            builder.espressoLibraryPath(options.get(EspressoOptions.EspressoLibraryPath));
        } else {
            Path espressoHome = Paths.get(language.getEspressoHome());
            builder.espressoLibraryPath(espressoHome.resolve("lib"));
        }

        if (options.hasBeenSet(EspressoOptions.Classpath)) {
            builder.classpath(options.get(EspressoOptions.Classpath));
        }

        // Process boot classpath + append and prepend options.
        List<Path> bootClasspath = new ArrayList<>(builder.bootClasspath());
        if (options.hasBeenSet(EspressoOptions.BootClasspath)) {
            bootClasspath = options.get(EspressoOptions.BootClasspath);
        }
        if (options.hasBeenSet(EspressoOptions.BootClasspathAppend)) {
            bootClasspath.addAll(options.get(EspressoOptions.BootClasspathAppend));
        }
        if (options.hasBeenSet(EspressoOptions.BootClasspathPrepend)) {
            bootClasspath.addAll(0, options.get(EspressoOptions.BootClasspathPrepend));
        }
        builder.bootClasspath(bootClasspath);

        if (options.hasBeenSet(EspressoOptions.BootLibraryPath)) {
            builder.bootLibraryPath(options.get(EspressoOptions.BootLibraryPath));
        }

        if (options.hasBeenSet(EspressoOptions.JavaLibraryPath)) {
            builder.javaLibraryPath(options.get(EspressoOptions.JavaLibraryPath));
        }

        if (options.hasBeenSet(EspressoOptions.ExtDirs)) {
            builder.extDirs(options.get(EspressoOptions.ExtDirs));
        }

        return builder;
    }

    static Builder newPlatformBuilder() {
        OS os = OS.getCurrent();
        switch (os) {
            case Linux:
                return new LinuxBuilder();
            case Darwin:
                return new DarwinBuilder();
            default:
                throw EspressoError.shouldNotReachHere(os + " not supported");
        }
    }
}

abstract class PlatformBuilder extends EspressoProperties.Builder {

    private static final List<Path> BOOT_CLASSPATH = Collections.unmodifiableList(
                    Arrays.asList(
                                    Paths.get("lib", "resources.jar"),
                                    Paths.get("lib", "rt.jar"),
                                    Paths.get("lib", "sunrsasign.jar"),
                                    Paths.get("lib", "jsse.jar"),
                                    Paths.get("lib", "jce.jar"),
                                    Paths.get("lib", "charsets.jar"),
                                    Paths.get("lib", "jfr.jar"),
                                    Paths.get("classes")));

    // TODO(peterssen): Enforce we are on amd64.
    // Note that os.arch may yield different values for the same architecture e.g. amd64 vs x86_64.
    static final String CPU_ARCH = System.getProperty("os.arch");

    static final Path EXTENSIONS_DIR = Paths.get("lib", "ext");

    @Override
    List<Path> defaultClasspath() {
        return Collections.singletonList(Paths.get("."));
    }

    @Override
    List<Path> defaultBootClasspath() {
        List<Path> paths = new ArrayList<>(BOOT_CLASSPATH.size());
        for (Path p : BOOT_CLASSPATH) {
            paths.add(javaHome().resolve(p));
        }
        return paths;
    }

    @Override
    Path defaultJavaHome() {
        throw EspressoError.shouldNotReachHere("Java 8 home not defined, use --java.JavaHome=/path/to/java8/home/jre");
    }

    @Override
    Path defaultEspressoLibraryPath() {
        throw EspressoError.shouldNotReachHere("Espresso library path not defined, use --java.EspressoLibraryPath=/path/to/espresso/lib/");
    }
}

enum OS {
    Darwin,
    Linux,
    Solaris,
    Windows;

    private static final OS current = findCurrent();

    private static OS findCurrent() {
        final String name = System.getProperty("os.name");
        if (name.equals("Linux")) {
            return OS.Linux;
        }
        if (name.equals("SunOS")) {
            return OS.Solaris;
        }
        if (name.equals("Mac OS X") || name.equals("Darwin")) {
            return OS.Darwin;
        }
        if (name.startsWith("Windows")) {
            return OS.Windows;
        }
        throw EspressoError.shouldNotReachHere("unknown OS: " + name);
    }

    public static OS getCurrent() {
        return current;
    }

    public static boolean isWindows() {
        return getCurrent() == OS.Windows;
    }

    public static boolean isUnix() {
        return getCurrent() != OS.Windows;
    }
}

final class LinuxBuilder extends PlatformBuilder {
    private static final Path SYS_EXT_DIR = Paths.get("/usr/java/packages");

    private static final List<Path> DEFAULT_LIBPATH = Collections.unmodifiableList(
                    Arrays.asList(
                                    Paths.get("/usr/lib64"),
                                    Paths.get("/lib64"),
                                    Paths.get("/lib"),
                                    Paths.get("/usr/lib")));

    @Override
    List<Path> defaultJavaLibraryPath() {
        List<Path> paths = new ArrayList<>();
        paths.add(SYS_EXT_DIR.resolve("lib").resolve(CPU_ARCH));
        paths.addAll(DEFAULT_LIBPATH);
        return paths;
    }

    @Override
    List<Path> defaultBootLibraryPath() {
        return Collections.singletonList(javaHome().resolve("lib").resolve(CPU_ARCH));
    }

    @Override
    List<Path> defaultExtDirs() {
        return Arrays.asList(
                        javaHome().resolve(EXTENSIONS_DIR),
                        SYS_EXT_DIR.resolve(EXTENSIONS_DIR));
    }
}

final class DarwinBuilder extends PlatformBuilder {

    private static final Path ROOT = Paths.get("/");

    private static final Path SYS_EXTENSIONS_DIR = Paths.get("Library", "Java", "Extensions");

    private static final List<Path> SYS_EXTENSIONS_DIRS = Collections.unmodifiableList(
                    Arrays.asList(
                                    ROOT.resolve(SYS_EXTENSIONS_DIR),
                                    Paths.get("/Network").resolve(SYS_EXTENSIONS_DIR),
                                    Paths.get("/System").resolve(SYS_EXTENSIONS_DIR),
                                    Paths.get("/usr/lib/java")));

    private static Path userHomeDir() {
        return Paths.get(System.getProperty("user.home"));
    }

    @Override
    List<Path> defaultJavaLibraryPath() {
        List<Path> paths = new ArrayList<>();
        paths.add(userHomeDir().resolve(SYS_EXTENSIONS_DIR));
        paths.addAll(SYS_EXTENSIONS_DIRS);
        paths.add(Paths.get("."));
        return paths;
    }

    @Override
    List<Path> defaultBootLibraryPath() {
        return Collections.singletonList(javaHome().resolve("lib"));
    }

    @Override
    List<Path> defaultExtDirs() {
        List<Path> paths = new ArrayList<>();
        paths.add(userHomeDir().resolve(SYS_EXTENSIONS_DIR));
        paths.add(javaHome().resolve(EXTENSIONS_DIR));
        paths.addAll(SYS_EXTENSIONS_DIRS);
        return paths;
    }
}
