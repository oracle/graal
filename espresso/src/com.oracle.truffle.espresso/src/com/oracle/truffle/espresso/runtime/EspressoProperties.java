/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.graalvm.home.HomeFinder;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.substitutions.ModuleExtension;

/**
 * All VM properties are overridable via options, some properties (e.g. boot classpath) are derived
 * from <b>java.home</b> and will be updated accordingly when <b>java.home</b> is updated.
 *
 * The platform dependent bits are encapsulated away from the Espresso VM, future
 * OS/architecture-specific properties should be added here.
 */
public interface EspressoProperties {
    String BOOT_MODULES_NAME = "modules";

    Path espressoLibs();

    BootClassPathType bootClassPathType();

    Path javaHome();

    List<Path> jvmLibraryPath();

    List<Path> classpath();

    List<Path> bootClasspath();

    List<Path> javaLibraryPath();

    List<Path> bootLibraryPath();

    List<Path> extDirs();

    abstract class Builder {
        private final Path espressoLibs;
        private BootClassPathType version;
        private Path javaHome;
        private List<Path> jvmLibraryPath;
        private List<Path> classpath;
        private List<Path> bootClasspath;
        private List<Path> javaLibraryPath;
        private List<Path> bootLibraryPath;
        private List<Path> extDirs;

        protected Builder(Path espressoLibs) {
            this.espressoLibs = espressoLibs;
        }

        abstract Path defaultJavaHome();

        abstract List<Path> defaultJvmLibraryPath();

        abstract List<Path> defaultClasspath();

        abstract List<Path> defaultBootClasspath();

        abstract List<Path> defaultJavaLibraryPath();

        abstract List<Path> defaultBootLibraryPath();

        abstract List<Path> defaultExtDirs();

        public Path espressoLibs() {
            return espressoLibs;
        }

        public BootClassPathType bootClassPathVersion() {
            return version;
        }

        public Builder bootClassPathVersion(BootClassPathType bootClasspathVersion) {
            this.version = bootClasspathVersion;
            return this;
        }

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

        public List<Path> jvmLibraryPath() {
            return jvmLibraryPath != null ? jvmLibraryPath : defaultJvmLibraryPath();
        }

        public Builder jvmLibraryPath(List<Path> newJvmLibraryPath) {
            this.jvmLibraryPath = newJvmLibraryPath;
            return this;
        }

        public EspressoProperties build() {
            return new EspressoProperties() {
                private final Path espressoLibs = Builder.this.espressoLibs;
                private final BootClassPathType javaVersion = Builder.this.bootClassPathVersion();
                private final Path javaHome = Objects.requireNonNull(Builder.this.javaHome(), "javaHome not defined");
                private final List<Path> classpath = Objects.requireNonNull(Builder.this.classpath(), "classpath not defined");
                private final List<Path> bootClasspath = Objects.requireNonNull(Builder.this.bootClasspath(), "bootClasspath not defined");
                private final List<Path> javaLibraryPath = Objects.requireNonNull(Builder.this.javaLibraryPath(), "javaLibraryPath not defined");
                private final List<Path> bootLibraryPath = Objects.requireNonNull(Builder.this.bootLibraryPath(), "bootLibraryPath not defined");
                private final List<Path> extDirs = Objects.requireNonNull(Builder.this.extDirs(), "extDirs not defined");
                private final List<Path> jvmLibraryPath = Objects.requireNonNull(Builder.this.jvmLibraryPath(), "jvmLibraryPath not defined");

                @Override
                public Path espressoLibs() {
                    return espressoLibs;
                }

                @Override
                public BootClassPathType bootClassPathType() {
                    return javaVersion;
                }

                @Override
                public Path javaHome() {
                    return javaHome;
                }

                @Override
                public List<Path> jvmLibraryPath() {
                    return jvmLibraryPath;
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

    static Builder processOptions(Builder builder, OptionValues options, EspressoContext context) {
        // Always set JavaHome first.
        Path javaHome = options.hasBeenSet(EspressoOptions.JavaHome)
                        ? options.get(EspressoOptions.JavaHome)
                        : builder.javaHome();
        /*
         * On Java 8, --java.JavaHome must point to the /jre folder, this is a usability
         * improvement/hack so users do not have to worry about appending /jre or not depending on
         * the version.
         */
        Path java8Home = javaHome.resolve("jre");
        if (Files.isDirectory(java8Home)) {
            javaHome = java8Home;
        }
        builder.javaHome(javaHome);

        if (options.hasBeenSet(EspressoOptions.JVMLibraryPath)) {
            builder.jvmLibraryPath(options.get(EspressoOptions.JVMLibraryPath));
        } /*
           * else infer JVMLibraryPath from EspressoHome, assuming we run from within a GraalVM.
           */

        if (options.hasBeenSet(EspressoOptions.Classpath)) {
            builder.classpath(options.get(EspressoOptions.Classpath));
        }

        // The boot classpath is an aggregation of several options, the logical order is:
        // PrependBootClasspath + BootClasspath + polyglot.jar + AppendBootClasspath.
        List<Path> bootClasspath = new ArrayList<>(builder.bootClasspath());
        if (options.hasBeenSet(EspressoOptions.BootClasspath)) {
            bootClasspath = new ArrayList<>(options.get(EspressoOptions.BootClasspath));
        }

        Path espressoLibs = context.getEspressoLibs();

        for (ModuleExtension me : ModuleExtension.getAllExtensions(context)) {
            Path jarPath = espressoLibs.resolve(me.jarName());
            if (Files.isReadable(jarPath)) {
                TruffleLogger.getLogger(EspressoLanguage.ID).fine("Adding " + me.jarName() + " to the boot classpath");
                bootClasspath.add(jarPath);
            } else {
                TruffleLogger.getLogger(EspressoLanguage.ID).warning(jarPath + " not found at " + espressoLibs);
            }
        }

        // Process boot classpath + append and prepend options.
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

    static Builder newPlatformBuilder(Path espressoLibs) {
        OS os = OS.getCurrent();
        switch (os) {
            case Linux:
                return new LinuxBuilder(espressoLibs);
            case Darwin:
                return new DarwinBuilder(espressoLibs);
            case Windows:
                return new WindowsBuilder(espressoLibs);
            default:
                throw EspressoError.shouldNotReachHere(os + " not supported");
        }
    }
}

abstract class PlatformBuilder extends EspressoProperties.Builder {

    private static final Path RT_JAR = Paths.get("lib", "rt.jar");

    private static final Path MODULES_IMAGE = Paths.get("lib", EspressoProperties.BOOT_MODULES_NAME);

    private static final Path MODULES_EXPLODED = Paths.get(EspressoProperties.BOOT_MODULES_NAME, Classpath.JAVA_BASE);

    private static final List<Path> BOOT_CLASSPATH = Collections.unmodifiableList(
                    Arrays.asList(
                                    RT_JAR,
                                    Paths.get("lib", "resources.jar"),
                                    Paths.get("lib", "sunrsasign.jar"),
                                    Paths.get("lib", "jsse.jar"),
                                    Paths.get("lib", "jce.jar"),
                                    Paths.get("lib", "charsets.jar"),
                                    Paths.get("lib", "jfr.jar"),
                                    Paths.get("classes")));

    private static final int PATHS_SIZE = BOOT_CLASSPATH.size();

    // TODO(peterssen): Enforce we are on amd64.
    // Note that os.arch may yield different values for the same architecture e.g. amd64 vs x86_64.
    static final String CPU_ARCH = System.getProperty("os.arch");

    static final Path EXTENSIONS_DIR = Paths.get("lib", "ext");

    PlatformBuilder(Path espressoLibs) {
        super(espressoLibs);
    }

    @Override
    List<Path> defaultClasspath() {
        return Collections.emptyList();
    }

    @Override
    List<Path> defaultBootClasspath() {
        Path path = javaHome().resolve(RT_JAR);
        if (Files.isReadable(path)) {
            bootClassPathVersion(BootClassPathType.RT_JAR);
            List<Path> paths = new ArrayList<>(PATHS_SIZE);
            for (Path p : BOOT_CLASSPATH) {
                paths.add(javaHome().resolve(p));
            }
            return paths;
        }
        path = javaHome().resolve(MODULES_IMAGE);
        if (Files.isReadable(path)) {
            bootClassPathVersion(BootClassPathType.IMAGE);
            List<Path> paths = new ArrayList<>(1);
            paths.add(path);
            return paths;
        }
        path = javaHome().resolve(MODULES_EXPLODED);
        if (Files.isDirectory(path)) {
            bootClassPathVersion(BootClassPathType.EXPLODED);
            List<Path> paths = new ArrayList<>(1);
            paths.add(path);
            return paths;
        }
        throw EspressoError.shouldNotReachHere("Cannot find boot class path for java home: " + javaHome());
    }

    protected static void expandEnvToPath(String envName, List<Path> paths) {
        String envPath = System.getenv(envName);
        if (envPath != null) {
            for (String e : envPath.split(File.pathSeparator)) {
                paths.add(Paths.get(e));
            }
        }
    }

    @Override
    Path defaultJavaHome() {
        throw EspressoError.shouldNotReachHere("Java home not defined, use --java.JavaHome=/path/to/java/home");
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

    LinuxBuilder(Path espressoLibs) {
        super(espressoLibs);
    }

    @Override
    List<Path> defaultJavaLibraryPath() {
        List<Path> paths = new ArrayList<>();
        expandEnvToPath("LD_LIBRARY_PATH", paths);
        paths.add(SYS_EXT_DIR.resolve("lib").resolve(CPU_ARCH));
        paths.add(SYS_EXT_DIR.resolve("lib"));
        paths.addAll(DEFAULT_LIBPATH);
        return paths;
    }

    @Override
    List<Path> defaultBootLibraryPath() {
        List<Path> paths = new ArrayList<>();
        paths.add(javaHome().resolve("lib").resolve(CPU_ARCH));
        paths.add(javaHome().resolve("lib"));
        return paths;
    }

    @Override
    List<Path> defaultJvmLibraryPath() {
        List<Path> paths = new ArrayList<>();
        Path graalvmHome = HomeFinder.getInstance().getHomeFolder();
        if (graalvmHome != null && Files.isDirectory(graalvmHome)) {
            // library used by java -truffle
            paths.add(graalvmHome.resolve("lib").resolve(CPU_ARCH).resolve("truffle"));
            paths.add(graalvmHome.resolve("lib").resolve("truffle"));
        }
        // library in resources or home
        paths.add(espressoLibs());
        return paths;
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

    DarwinBuilder(Path espressoLibs) {
        super(espressoLibs);
    }

    private static Path userHomeDir() {
        return Paths.get(System.getProperty("user.home"));
    }

    @Override
    List<Path> defaultJavaLibraryPath() {
        List<Path> paths = new ArrayList<>();
        expandEnvToPath("DYLD_LIBRARY_PATH", paths);
        expandEnvToPath("JAVA_LIBRARY_PATH", paths);
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
    List<Path> defaultJvmLibraryPath() {
        List<Path> paths = new ArrayList<>();
        Path graalvmHome = HomeFinder.getInstance().getHomeFolder();
        if (graalvmHome != null && Files.isDirectory(graalvmHome)) {
            paths.add(graalvmHome.resolve("lib").resolve("truffle"));
        }
        paths.add(espressoLibs());
        return paths;
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

final class WindowsBuilder extends PlatformBuilder {

    private final Path windowsRoot = Paths.get(System.getenv("SystemRoot"));

    private static final Path PACKAGE_DIR = Paths.get("Sun", "Java");

    WindowsBuilder(Path espressoLibs) {
        super(espressoLibs);
    }

    @Override
    List<Path> defaultJavaLibraryPath() {
        // Win32 library search order (See the documentation for LoadLibrary):
        //
        // 1. The directory from which application is loaded.
        // 2. The system wide Java Extensions directory (Java only)
        // 3. System directory (GetSystemDirectory)
        // 4. Windows directory (GetWindowsDirectory)
        // 5. The PATH environment variable
        // 6. The current directory
        List<Path> libraryPath = new ArrayList<>();

        // 1. The directory from which application is loaded.
        // HotSpot's uses the following snippet:
        // GetModuleFileName(NULL, tmp, sizeof(tmp));
        // *(strrchr(tmp, '\\')) = '\0';
        // strcat(library_path, tmp);
        //
        // Since Espresso may run standalone, we point to the "would be" path as if Espresso was the
        // "java.exe" executable e.g. "jre/bin"
        libraryPath.add(javaHome().resolve("bin"));

        // 2. The system wide Java Extensions directory (Java only)
        libraryPath.add(windowsRoot.resolve(PACKAGE_DIR).resolve("bin"));

        // 3. System directory (GetSystemDirectory)
        libraryPath.add(windowsRoot.resolve("system32"));

        // 4. Windows directory (GetWindowsDirectory)
        libraryPath.add(windowsRoot);

        // 5. The PATH environment variable
        expandEnvToPath("PATH", libraryPath);

        // 6. The current directory
        libraryPath.add(Paths.get("."));

        return libraryPath;
    }

    @Override
    List<Path> defaultBootLibraryPath() {
        return Collections.singletonList(javaHome().resolve("bin"));
    }

    @Override
    List<Path> defaultJvmLibraryPath() {
        List<Path> paths = new ArrayList<>();
        Path graalvmHome = HomeFinder.getInstance().getHomeFolder();
        if (graalvmHome != null && Files.isDirectory(graalvmHome)) {
            paths.add(graalvmHome.resolve("bin").resolve("truffle"));
        }
        paths.add(espressoLibs());
        return paths;
    }

    @Override
    List<Path> defaultExtDirs() {
        return Arrays.asList(
                        javaHome().resolve(EXTENSIONS_DIR),
                        windowsRoot.resolve(PACKAGE_DIR).resolve(EXTENSIONS_DIR));
    }
}
