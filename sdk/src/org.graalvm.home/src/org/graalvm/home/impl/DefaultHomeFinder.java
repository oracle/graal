/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.home.impl;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.graalvm.home.HomeFinder;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ProcessProperties;

/**
 * This is a private implementation class. It is only public for ServiceLoader to work.
 */
public final class DefaultHomeFinder extends HomeFinder {

    private static final boolean STATIC_VERBOSE = Boolean.getBoolean("com.oracle.graalvm.locator.verbose");

    static final Path FORCE_GRAAL_HOME;
    static final Path GRAAL_HOME_RELATIVE_PATH;
    static final Map<String, Path> LANGUAGE_RELATIVE_HOMES = new HashMap<>();

    /**
     * Languages included in the native image. The variable is set at the native image build time.
     * When the languages are known, there is no need to list directories at image execution time to
     * find the language homes.
     */
    private static Set<String> nativeImageLanguages;
    /**
     * Tools included in the native image. The variable is set at the native image build time. When
     * the tools are known, there is no need to list directories at image execution time to find the
     * tool homes.
     */
    private static Set<String> nativeImageTools;

    static {
        final String forcedHome = System.getProperty("org.graalvm.launcher.home");
        if (forcedHome != null && forcedHome.length() > 0) {
            FORCE_GRAAL_HOME = Paths.get(forcedHome);
        } else {
            FORCE_GRAAL_HOME = null;
        }

        final String relativeHome = System.getProperty("org.graalvm.launcher.relative.home");
        if (relativeHome != null && relativeHome.length() > 0) {
            GRAAL_HOME_RELATIVE_PATH = Paths.get(relativeHome);
        } else {
            GRAAL_HOME_RELATIVE_PATH = null;
        }

        // Save relative paths from the launcher's directory to other language homes of the form:
        // org.graalvm.launcher.relative.LANGUAGE_ID.home
        // Meant to be only used for standalones, not for regular GraalVM.
        for (Object property : System.getProperties().keySet()) {
            if (property instanceof String) {
                String name = ((String) property);
                if (name.startsWith("org.graalvm.launcher.relative.") && name.endsWith(".home")) {
                    String after = name.substring("org.graalvm.launcher.relative.".length());
                    if (after.length() > ".home".length()) {
                        String languageId = after.substring(0, after.length() - ".home".length());
                        LANGUAGE_RELATIVE_HOMES.put(languageId, Paths.get(System.getProperty(name)));
                    }
                }
            }
        }
    }

    private static final String GRAALVM_VERSION_PROPERTY = "org.graalvm.version";
    private static final String GRAALVM_VERSION = System.getProperty(GRAALVM_VERSION_PROPERTY);

    private static final Object HOME_NOT_FOUND = new Object();

    private volatile Boolean verbose;
    private volatile String version;
    private volatile Object graalVMHome;
    private volatile Map<String, Path> languageHomes;
    private volatile Map<String, Path> toolHomes;

    public DefaultHomeFinder() {
    }

    @Override
    public Path getHomeFolder() {
        Object home = graalVMHome;
        if (home == null) {
            Object result = searchHomeFolder();
            home = result != null ? result : HOME_NOT_FOUND;
            if (!ImageInfo.inImageBuildtimeCode()) {
                graalVMHome = home;
            }
        }

        if (home instanceof Path) {
            return (Path) home;
        } else {
            assert home == HOME_NOT_FOUND;
            return null;
        }
    }

    private Path searchHomeFolder() {
        if (isVerbose()) {
            System.err.println("FORCE_GRAAL_HOME: " + FORCE_GRAAL_HOME);
            System.err.println("GRAAL_HOME_RELATIVE_PATH: " + GRAAL_HOME_RELATIVE_PATH);
            for (Entry<String, Path> entry : LANGUAGE_RELATIVE_HOMES.entrySet()) {
                System.err.println("relative home of " + entry.getKey() + " from the launcher's directory: " + entry.getValue());
            }
        }

        if (FORCE_GRAAL_HOME != null) {
            verbose("GraalVM home forced to: ", FORCE_GRAAL_HOME);
            return FORCE_GRAAL_HOME;
        }

        final Path home;
        if (ImageInfo.inImageRuntimeCode()) {
            final String graalvmHomeValue = System.getProperty("org.graalvm.home");
            if (graalvmHomeValue != null) {
                verbose("GraalVM home already set to: ", graalvmHomeValue);
                home = Paths.get(graalvmHomeValue);
            } else {
                home = getGraalVmHomeNative();
                verbose("Found GraalVM home: ", home);
                if (home == null) {
                    return null;
                }
            }
            return home;
        } else {
            final String javaHomeProperty = System.getProperty("java.home");
            if (javaHomeProperty == null) {
                throw new AssertionError("The java.home system property is not set");
            }

            final Path javaHome = Paths.get(javaHomeProperty);
            if (!Files.exists(javaHome)) {
                throw new AssertionError("Java home is not reachable.");
            }

            if (Files.exists(javaHome.resolve(Paths.get("lib", "modules")))) {
                home = javaHome;
            } else {
                throw new AssertionError("Missing jimage in java.home: " + javaHome);
            }

            verbose("GraalVM home found by java.home property as: ", home);
            return home;
        }
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
            if (home == null) {
                res = Collections.unmodifiableMap(collectStandaloneHomes());
            } else if (ImageInfo.inImageRuntimeCode() && nativeImageLanguages != null) {
                res = new HashMap<>(nativeImageLanguages.size());
                Path languagesFolder = home.resolve("languages");
                for (String languageId : nativeImageLanguages) {
                    String enforcedHome = System.getProperty("org.graalvm.language." + languageId + ".home");
                    if (enforcedHome == null) {
                        res.put(languageId, languagesFolder.resolve(languageId));
                    } else {
                        res.put(languageId, toRealPath(Path.of(enforcedHome)));
                    }
                }
            } else {
                res = collectHomes(home.resolve("languages"));
                for (Object property : System.getProperties().keySet()) {
                    if (property instanceof String) {
                        String name = ((String) property);
                        if (name.startsWith("org.graalvm.language.") && name.endsWith(".home")) {
                            String after = name.substring("org.graalvm.language.".length());
                            if (after.length() > ".home".length()) {
                                String languageId = after.substring(0, after.length() - ".home".length());
                                if (!languageId.contains(".")) {
                                    res.put(languageId, toRealPath(Paths.get(System.getProperty(name))));
                                }
                            }
                        }
                    }
                }
                res = Collections.unmodifiableMap(res);
            }
            if (!ImageInfo.inImageBuildtimeCode()) {
                languageHomes = res;
            }
        }
        return res;
    }

    private static Path toRealPath(Path p) {
        try {
            return p.toRealPath();
        } catch (NoSuchFileException nsfe) {
            return p;
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    @Override
    public Map<String, Path> getToolHomes() {
        Map<String, Path> res = toolHomes;
        if (res == null) {
            Path home = getHomeFolder();
            if (home == null) {
                res = Collections.emptyMap();
            } else if (ImageInfo.inImageRuntimeCode() && nativeImageTools != null) {
                res = new HashMap<>(nativeImageTools.size());
                Path toolsFolder = home.resolve("tools");
                for (String toolId : nativeImageTools) {
                    res.put(toolId, toolsFolder.resolve(toolId));
                }
            } else {
                res = Collections.unmodifiableMap(collectHomes(home.resolve("tools")));
            }
            if (!ImageInfo.inImageBuildtimeCode()) {
                toolHomes = res;
            }
        }
        return res;
    }

    /*
     * Sets a relative path to a language home if it wasn't already set by system properties that
     * are read in the static initializer of this class.
     *
     * NOTE: this method is called reflectively by TruffleBaseFeature
     */
    @SuppressWarnings("unused")
    private static boolean setRelativeLanguageHomeIfNotAlreadySet(String languageId, Path homePath) {
        if (FORCE_GRAAL_HOME == null && GRAAL_HOME_RELATIVE_PATH == null && LANGUAGE_RELATIVE_HOMES.get(languageId) == null) {
            LANGUAGE_RELATIVE_HOMES.put(languageId, homePath);
            return true;
        }
        return false;
    }

    private static Map<String, Path> collectHomes(Path folder) {
        Map<String, Path> res = new HashMap<>();
        try (DirectoryStream<Path> dirContent = Files.newDirectoryStream(folder, (entry) -> {
            Path fileName = entry.getFileName();
            if (fileName == null) {
                return false;
            } else {
                return !fileName.toString().startsWith(".");
            }
        })) {
            for (Path home : dirContent) {
                Path filename = home.getFileName();
                if (filename != null) {
                    res.put(filename.toString(), home);
                }
            }
        } catch (NoSuchFileException noSuchFile) {
            // folder does not exist, return empty res
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        return res;
    }

    private Map<String, Path> collectStandaloneHomes() {
        Map<String, Path> res = new HashMap<>();

        Path executableOrObjFile = null;
        if (ImageInfo.isExecutable()) {
            executableOrObjFile = getCurrentExecutablePath();
        } else if (ImageInfo.isSharedLibrary()) {
            executableOrObjFile = getCurrentObjectFilePath();
        }

        if (executableOrObjFile != null) {
            Path launcherDir = executableOrObjFile.getParent();

            for (Entry<String, Path> entry : LANGUAGE_RELATIVE_HOMES.entrySet()) {
                Path langHome = launcherDir.resolve(entry.getValue()).normalize();
                String langId = entry.getKey();
                res.put(langId, langHome);
                verbose("Resolved the ", langId, " home as ", langHome);
            }
        }

        return res;
    }

    private Path getGraalVmHomeNative() {
        if (ImageInfo.isExecutable()) {
            final Path executable = getCurrentExecutablePath();
            if (executable != null) {
                Path result = getGraalVmHomeFromRelativeLauncherPath(executable);
                if (result != null) {
                    verbose("GraalVM home found by executable as: ", result);
                    return result;
                }
            }
        } else {
            final Path objectFile = getCurrentObjectFilePath();
            if (objectFile != null) {
                Path result = getGraalVmHomeFromRelativeLauncherPath(objectFile);
                if (result == null) {
                    result = getGraalVmHomeLibPolyglotFallBack(objectFile);
                }
                if (result != null) {
                    verbose("GraalVM home found by object file as: ", result);
                    return result;
                }
            }
        }
        return null;
    }

    private static Path getGraalVmHomeFromRelativeLauncherPath(Path executableOrObjFile) {
        if (GRAAL_HOME_RELATIVE_PATH != null) {
            Path result = trimAbsolutePath(executableOrObjFile, GRAAL_HOME_RELATIVE_PATH);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * Fallback for the GraalVM home using location of libpolyglot in jdk layout.
     *
     * @param objectFile the path to libpolyglot
     * @return the path to GraalVM home or null
     */
    private static Path getGraalVmHomeLibPolyglotFallBack(Path objectFile) {
        // <home>/lib/polyglot/libpolyglot.so
        Path parent = objectFile.getParent();
        if (parent == null || !"polyglot".equals(getFileName(parent))) {
            return null;
        }
        parent = parent.getParent();
        if (parent == null || !"lib".equals(getFileName(parent))) {
            return null;
        }
        Path home = null;
        Path jdk = parent.getParent();
        if (jdk != null) {
            home = jdk;
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
            Path filename = result.getFileName();
            if (filename == null || !filename.equals(p.getFileName())) {
                return null;
            }
            result = result.getParent();
            p = p.getParent();
        }
        return result;
    }

    private static Path getCurrentObjectFilePath() {
        String path = ProcessProperties.getObjectFile(VmLocatorSymbol.SYMBOL);
        return path == null ? null : Paths.get(path);
    }

    private static Path getCurrentExecutablePath() {
        final String path = ProcessProperties.getExecutableName();
        return path == null ? null : Paths.get(path);
    }

    private static String getFileName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? null : fileName.toString();
    }

    private boolean isVerbose() {
        if (ImageInfo.inImageBuildtimeCode()) {
            return STATIC_VERBOSE;
        } else {
            Boolean res = verbose;
            if (res == null) {
                res = STATIC_VERBOSE || Boolean.parseBoolean(System.getenv("VERBOSE_GRAALVM_LOCATOR"));
                verbose = res;
            }
            return res;
        }
    }

    private void verbose(Object... args) {
        if (isVerbose()) {
            StringBuilder builder = new StringBuilder();
            for (Object arg : args) {
                builder.append(arg);
            }
            System.err.println(builder.toString());
        }
    }

    /**
     * Sets languages included in a native image.
     *
     * NOTE: this method is called reflectively by TruffleBaseFeature
     */
    @SuppressWarnings("unused")
    private static void setNativeImageLanguages(Set<String> languages) {
        nativeImageLanguages = languages;
    }

    /**
     * Sets tools included in a native image.
     *
     * NOTE: this method is called reflectively by TruffleBaseFeature
     */
    @SuppressWarnings("unused")
    private static void setNativeImageTools(Set<String> tools) {
        nativeImageTools = tools;
    }
}
