/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graalvm.locator;

import com.oracle.truffle.api.impl.HomeFinder;
import com.oracle.truffle.api.TruffleOptions;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.graalvm.nativeimage.ImageInfo;

public final class DefaultHomeFinder extends HomeFinder {

    private static final boolean STATIC_VERBOSE = Boolean.getBoolean("com.oracle.graalvm.locator.verbose");

    private static final Path FORCE_GRAAL_HOME;
    private static final Path GRAAL_HOME_RELATIVE_PATH;
    private static final Path LANGUAGE_HOME_RELATIVE_PATH;
    static {
        String forcedHome = System.getProperty("org.graalvm.launcher.home");
        String relativeHome = System.getProperty("org.graalvm.launcher.relative.home");
        String relativeLanguageHome = System.getProperty("org.graalvm.launcher.relative.language.home");
        if (forcedHome != null && forcedHome.length() > 0) {
            FORCE_GRAAL_HOME = Paths.get(forcedHome);
        } else {
            FORCE_GRAAL_HOME = null;
        }
        if (relativeHome != null && relativeHome.length() > 0) {
            GRAAL_HOME_RELATIVE_PATH = Paths.get(relativeHome);
        } else {
            GRAAL_HOME_RELATIVE_PATH = null;
        }
        if (relativeLanguageHome != null && relativeLanguageHome.length() > 0) {
            LANGUAGE_HOME_RELATIVE_PATH = Paths.get(relativeLanguageHome);
        } else {
            LANGUAGE_HOME_RELATIVE_PATH = null;
        }
        assert !(GRAAL_HOME_RELATIVE_PATH != null && LANGUAGE_HOME_RELATIVE_PATH != null) : "Can not set both org.graalvm.launcher.relative.home and org.graalvm.launcher.relative.language.home";
    }

    private static final String ALT_GRAALVM_VERSION_PROPERTY = "graalvm.version";
    private static final String GRAALVM_VERSION_PROPERTY = "org.graalvm.version";
    private static final String GRAALVM_VERSION;
    static {
        String version = System.getProperty(GRAALVM_VERSION_PROPERTY);
        String altVersion = System.getProperty(ALT_GRAALVM_VERSION_PROPERTY);
        if (version != null && altVersion == null) {
            GRAALVM_VERSION = version;
        } else if (altVersion != null && version == null) {
            GRAALVM_VERSION = altVersion;
        } else if (version != null && version.equals(altVersion)) {
            GRAALVM_VERSION = version;
        } else {
            GRAALVM_VERSION = null;
        }
    }

    private volatile Boolean verbose;
    private volatile Path graalHome;
    private volatile String version;
    private volatile Map<String, Path> languageHomes;
    private volatile Map<String, Path> toolHomes;
    private volatile Map.Entry<String, Path> launcherLanguageHome;

    public DefaultHomeFinder() {
    }

    @Override
    public Path getHomeFolder() {
        Path res = graalHome;
        if (res == null) {
            if (isVerbose()) {
                System.err.println("FORCE_GRAAL_HOME: " + FORCE_GRAAL_HOME);
                System.err.println("LANGUAGE_HOME_RELATIVE_PATH: " + LANGUAGE_HOME_RELATIVE_PATH);
                System.err.println("GRAAL_HOME_RELATIVE_PATH: " + GRAAL_HOME_RELATIVE_PATH);
            }
            if (FORCE_GRAAL_HOME != null) {
                if (isVerbose()) {
                    System.err.println("GraalVM home forced to: " + FORCE_GRAAL_HOME);
                }
                res = FORCE_GRAAL_HOME;
            } else {
                boolean aot = TruffleOptions.AOT;
                if (aot) {
                    String graalvmHomeValue = System.getProperty("graalvm.home");
                    if (graalvmHomeValue == null) {
                        graalvmHomeValue = System.getProperty("org.graalvm.home");
                    }
                    if (graalvmHomeValue != null) {
                        if (isVerbose()) {
                            System.err.println("GraalVM home already set to: " + graalvmHomeValue);
                        }
                        res = Paths.get(graalvmHomeValue);
                    } else {
                        res = getGraalVmHome();
                        if (isVerbose()) {
                            System.err.println("Found GraalVM home: " + res);
                        }
                        if (res == null) {
                            return null;
                        }
                    }
                    if (!Files.exists(res)) {
                        throw new AssertionError("GraalVM home is not reachable.");
                    }
                } else {
                    Path javaHome = Paths.get(System.getProperty("java.home"));
                    if (!Files.exists(javaHome)) {
                        throw new AssertionError("Java home is not reachable.");
                    }
                    Path jre = javaHome.resolve("jre");
                    if (Files.exists(jre)) {
                        res = javaHome;
                    } else {
                        res = javaHome.getParent();
                    }
                    if (isVerbose()) {
                        System.err.println("GraalVM home found by java.home property as: " + res);
                    }
                }
            }
            if (!ImageInfo.inImageBuildtimeCode()) {
                graalHome = res;
            }
        }
        return res;
    }

    @Override
    public String getVersion() {
        String res = version;
        if (res == null) {
            if (GRAALVM_VERSION != null) {
                res = GRAALVM_VERSION;
            } else {
                res = "snapshot";
                Path home = getHomeFolder();
                if (home != null) {
                    Path releaseFile = home.resolve("release");
                    if (Files.exists(releaseFile)) {
                        try (InputStream in = new BufferedInputStream(Files.newInputStream(releaseFile, StandardOpenOption.READ))) {
                            Properties properties = new Properties();
                            properties.load(in);
                            Object loadedVersion = properties.get("GRAALVM_VERSION");
                            if (loadedVersion != null) {
                                res = loadedVersion.toString();
                                if (res.startsWith("\"")) {
                                    res = res.substring(1, res.length());
                                }
                                if (res.endsWith("\"")) {
                                    res = res.substring(0, res.length() - 1);
                                }
                            }
                        } catch (IOException ioe) {
                            // pass with res = "snapshot"
                        }
                    }
                }
            }
            if (!ImageInfo.inImageBuildtimeCode()) {
                version = res;
            }
        }
        return res;
    }

    @Override
    public Map<String, Path> getLanguageHomes() {
        Map<String, Path> res = languageHomes;
        if (res == null) {
            Path home = getHomeFolder();
            Map.Entry<String, Path> launcherLang = launcherLanguageHome;
            launcherLanguageHome = null;
            if (home == null) {
                res = launcherLang != null ? Collections.singletonMap(launcherLang.getKey(), launcherLang.getValue()) : Collections.emptyMap();
            } else {
                res = Collections.unmodifiableMap(collectHomes(home.resolve(Paths.get("jre", "languages"))));
            }
            if (!ImageInfo.inImageBuildtimeCode()) {
                languageHomes = res;
            }
        }
        return res;
    }

    @Override
    public Map<String, Path> getToolHomes() {
        Map<String, Path> res = toolHomes;
        if (res == null) {
            Path home = getHomeFolder();
            if (home == null) {
                res = Collections.emptyMap();
            } else {
                res = Collections.unmodifiableMap(collectHomes(home.resolve(Paths.get("jre", "tools"))));
            }
            if (!ImageInfo.inImageBuildtimeCode()) {
                toolHomes = res;
            }
        }
        return res;
    }

    private static Map<String, Path> collectHomes(Path folder) {
        Map<String, Path> res = new HashMap<>();
        if (Files.exists(folder)) {
            try (DirectoryStream<Path> dirContent = Files.newDirectoryStream(folder, new DirectoryStream.Filter<Path>() {
                @Override
                public boolean accept(Path entry) throws IOException {
                    return !entry.getFileName().toString().startsWith(".");
                }
            })) {
                for (Path home : dirContent) {
                    res.put(home.getFileName().toString(), home);
                }
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
        return res;
    }

    private Path getGraalVmHome() {
        assert TruffleOptions.AOT;
        Path executable = getCurrentExecutablePath();
        if (executable != null) {
            Path result = getGraalVmHome(executable);
            if (result == null) {
                result = getGraalVmHomeFallBack(executable);
            }
            if (result != null) {
                if (isVerbose()) {
                    System.err.println("GraalVM home found by executable as: " + result);
                }
                return result;
            }
        }
        Path objectFile = getCurrentObjectFilePath();
        if (objectFile != null) {
            Path result = getGraalVmHome(objectFile);
            if (result == null) {
                result = getGraalVmHomeLibPolyglotFallBack(objectFile);
            }
            if (result != null) {
                if (isVerbose()) {
                    System.err.println("GraalVM home found by object file as: " + result);
                }
                return result;
            }
        }
        return null;
    }

    private Path getGraalVmHome(Path executableOrObjFile) {
        Path languageHome = getLanguageHome(executableOrObjFile);
        if (languageHome != null) {
            Path graalVmHome = getGraalVMHomeFromLanguageHome(languageHome);
            if (graalVmHome != null) {
                return graalVmHome;
            }
            // We don't have GraalVM home but we have language home, standalone distribution
            // Set at least the home for the launcher language
            String languageId = System.getProperty("org.graalvm.launcher.languageId");
            if (languageId != null) {
                launcherLanguageHome = new AbstractMap.SimpleImmutableEntry<>(languageId, languageHome);
            }
        }
        if (GRAAL_HOME_RELATIVE_PATH != null) {
            Path result = trimAbsolutePath(executableOrObjFile, GRAAL_HOME_RELATIVE_PATH);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static Path getLanguageHome(Path executableOrObjFile) {
        if (LANGUAGE_HOME_RELATIVE_PATH == null) {
            return null;
        }
        return trimAbsolutePath(executableOrObjFile, LANGUAGE_HOME_RELATIVE_PATH);
    }

    private static Path getGraalVMHomeFromLanguageHome(Path languageHome) {
        // jre/<languages_or_tools>/<comp_id>
        Path languagesOrTools = languageHome.getParent();
        String languagesOrToolsString = languagesOrTools.getFileName().toString();
        if (!languagesOrToolsString.equals("languages") && !languagesOrToolsString.equals("tools")) {
            return null;
        }
        Path jreOrJdk = languagesOrTools.getParent();
        Path home;
        if (jreOrJdk.getFileName().toString().equals("jre")) {
            home = jreOrJdk.getParent();
        } else {
            home = jreOrJdk;
        }
        return isJreHome(home) || isJdkHome(home) ? home : null;
    }

    /**
     * Fallback for the GraalVM home using location of execuable jdk/jre layout.
     *
     * @param executable the path to launcher
     * @return the path to GraalVM home or null
     */
    private static Path getGraalVmHomeFallBack(Path executable) {
        Path bin = executable.getParent();
        if (!bin.getFileName().toString().equals("bin")) {
            return null;
        }
        Path jreOrJdk = bin.getParent();
        Path home;
        if (jreOrJdk != null && jreOrJdk.getFileName().toString().equals("jre")) {
            home = jreOrJdk.getParent();
        } else if (jreOrJdk != null) {
            if (isJdkHome(jreOrJdk)) {
                home = jreOrJdk;
            } else {
                // maybe we are in the language home?
                Path languages = jreOrJdk.getParent();
                if (languages != null && languages.getFileName().toString().equals("languages")) {
                    Path jre = languages.getParent();
                    if (jre != null && jre.getFileName().toString().equals("jre")) {
                        home = jre.getParent();
                    } else {
                        home = null;
                    }
                } else {
                    home = null;
                }
            }
        } else {
            home = null;
        }
        if (home != null && !isJreHome(home)) {
            return null;
        }
        return home;
    }

    /**
     * Fallback for the GraalVM home using location of libpolyglot in jdk/jre layout.
     *
     * @param objectFile the path to libpolyglot
     * @return the path to GraalVM home or null
     */
    private static Path getGraalVmHomeLibPolyglotFallBack(Path objectFile) {
        // <home>/jre/lib/polyglot/libpolyglot.so
        Path parent = objectFile.getParent();
        if (parent == null || !parent.getFileName().toString().equals("polyglot")) {
            return null;
        }
        parent = parent.getParent();
        if (parent == null || !parent.getFileName().toString().equals("lib")) {
            return null;
        }
        Path home = null;
        Path jreOrJdk = parent.getParent();
        if (jreOrJdk != null) {
            if (jreOrJdk.getFileName().toString().equals("jre")) {
                home = jreOrJdk.getParent();
            } else {
                home = jreOrJdk;
            }
        }
        return home != null && isJdkHome(home) ? home : null;
    }

    private static boolean isJdkHome(Path path) {
        Path javac = path.resolve(Paths.get("bin", "javac"));
        return isJreHome(path) && Files.isRegularFile(javac) && Files.isExecutable(javac);
    }

    private static boolean isJreHome(Path path) {
        Path java = path.resolve(Paths.get("bin", "java"));
        return Files.isRegularFile(java) && Files.isExecutable(java);
    }

    private static Path trimAbsolutePath(Path absolute, Path expectedRelative) {
        Path p = expectedRelative;
        Path result = absolute;
        while (p != null) {
            if (result == null) {
                return null;
            }
            if (!result.getFileName().equals(p.getFileName())) {
                return null;
            }
            result = result.getParent();
            p = p.getParent();
        }
        return result;
    }

    @SuppressWarnings("deprecation")
    private static Path getCurrentObjectFilePath() {
        String path = (String) Compiler.command(new Object[]{
                        "com.oracle.svm.core.posix.GetObjectFile",
                        VmLocatorSymbol.SYMBOL});
        return path == null ? null : Paths.get(path);
    }

    @SuppressWarnings("deprecation")
    private static Path getCurrentExecutablePath() {
        return Paths.get((String) Compiler.command(new String[]{
                        "com.oracle.svm.core.posix.GetExecutableName"
        }));
    }

    private boolean isVerbose() {
        if (ImageInfo.inImageBuildtimeCode()) {
            return STATIC_VERBOSE;
        } else {
            Boolean res = verbose;
            if (res == null) {
                res = STATIC_VERBOSE || Boolean.valueOf(System.getenv("VERBOSE_GRAALVM_LOCATOR"));
                verbose = res;
            }
            return res;
        }
    }
}
