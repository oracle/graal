/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;

/**
 * A feature that detects whether a native image may be vulnerable to Log4Shell.
 *
 * This feature first checks whether a vulnerable version of log4j is present in the native image.
 * If a vulnerable version is detected, the feature will then check whether any vulnerable methods
 * are reachable.
 */
@AutomaticallyRegisteredFeature
public class Log4ShellFeature implements InternalFeature {
    private static final String log4jClassName = "org.apache.logging.log4j.Logger";
    private static final String log4jVulnerableErrorMessage = "A vulnerable version of log4j has been detected. Please update to log4j version 2.17.1 or later.%nVulnerable Method(s):";
    private static final String log4jUnknownVersion = "The log4j library has been detected, but the version is unavailable. Due to Log4Shell, please ensure log4j is at version 2.17.1 or later.";

    /* Different versions of log4j overload all these methods. */
    private static final Set<String> targetMethods = Set.of("debug", "error", "fatal", "info", "log", "trace", "warn");

    private static Optional<String> getPomVersion(Class<?> log4jClass) {
        ProtectionDomain pd = log4jClass.getProtectionDomain();
        CodeSource cs = pd.getCodeSource();

        if (cs == null) {
            return Optional.empty();
        }
        URL location = cs.getLocation();
        if (location == null) {
            return Optional.empty();
        }

        try {
            ClassLoader nullClassLoader = null;
            FileSystem jarFileSystem = FileSystems.newFileSystem(Paths.get(location.toURI()), nullClassLoader);
            Stream<Path> files = Files.walk(jarFileSystem.getPath("/META-INF"));
            return files.filter(file -> file.endsWith("pom.properties"))
                            .map(file -> {
                                Properties properties = new Properties();
                                try {
                                    InputStream inputStream = Files.newInputStream(file);
                                    if (inputStream != null) {
                                        properties.load(inputStream);
                                    }
                                } catch (IOException ex) {
                                    /* Skip over properties we cannot read. */
                                }
                                return properties;
                            })
                            .filter(properties -> {
                                String groupId = properties.getProperty("groupId");
                                String artifactId = properties.getProperty("artifactId");
                                return "org.apache.logging.log4j".equals(groupId) && "log4j-core".equals(artifactId);
                            })
                            .map(properties -> properties.getProperty("version"))
                            .findFirst();
        } catch (IOException ex) {
            /* We encountered an IO error while looking up the log4j jar file's version. */
        } catch (URISyntaxException ex) {
            /* Obtaining a Path from the log4j jar file URL failed. */
        }

        return Optional.empty();
    }

    private static boolean vulnerableLog4jOne(String[] components) {
        String minor = components[1];
        if ("2".equals(minor)) {
            return true;
        }
        return false;
    }

    private static boolean vulnerableLog4jTwo(String[] components) {
        /* Every minor version since 0 is vulnerable to an exploit. */
        String minor = components[1];

        /* Recognize alpha and beta builds. */
        if (minor.charAt(0) == '0') {
            return true;
        }

        try {
            int minorVersion = Integer.valueOf(minor);
            if (minorVersion <= 16) {
                return true;
            }

            if (components.length == 3) {
                int patchVersion = Integer.valueOf(components[2]);
                if (minorVersion == 17 && patchVersion == 0) {
                    return true;
                }
            }
        } catch (NumberFormatException ex) {
            LogUtils.warning(log4jUnknownVersion);
        }

        return false;
    }

    private AfterAnalysisAccess afterAnalysisAccess;

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        this.afterAnalysisAccess = access;
    }

    public String getUserWarning() {
        Class<?> log4jClass = afterAnalysisAccess.findClassByName(log4jClassName);
        if (log4jClass == null) {
            return null;
        }

        Package log4jPackage = log4jClass.getPackage();
        String version = log4jPackage.getImplementationVersion();

        if (version == null) {
            Optional<String> pomVersion = getPomVersion(log4jClass);
            if (pomVersion.isPresent()) {
                version = pomVersion.get();
            }
        }

        /* We were unable to get the version, do not risk raising a false positive. */
        if (version == null) {
            return log4jUnknownVersion;
        }

        String[] components = version.split("\\.");

        /* Something is wrong with the version string, stop here. */
        if (components.length < 2) {
            return log4jUnknownVersion;
        }

        Set<String> vulnerableMethods = new HashSet<>();

        if (("1".equals(components[0]) && vulnerableLog4jOne(components)) || ("2".equals(components[0]) && vulnerableLog4jTwo(components))) {
            for (Method method : log4jClass.getMethods()) {
                String methodName = method.getName();
                if (targetMethods.contains(methodName) && (afterAnalysisAccess.isReachable(method) || (afterAnalysisAccess.reachableMethodOverrides(method).size() > 0))) {
                    vulnerableMethods.add(method.getDeclaringClass().getName() + "." + method.getName());
                }
            }
        }

        if (vulnerableMethods.size() == 0) {
            return null;
        }

        StringBuilder renderedErrorMessage = new StringBuilder(String.format(log4jVulnerableErrorMessage));
        for (String method : vulnerableMethods) {
            renderedErrorMessage.append(System.lineSeparator() + "    - " + method);
        }
        return renderedErrorMessage.toString();
    }
}
