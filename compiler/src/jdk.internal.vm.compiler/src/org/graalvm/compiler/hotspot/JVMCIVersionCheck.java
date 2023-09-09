/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mechanism for checking that the current Java runtime environment supports the minimum JVMCI API
 * required by Graal. The {@code JVMCI_VERSION_CHECK} environment variable can be used to ignore a
 * failed check ({@code JVMCI_VERSION_CHECK=ignore}) or print a warning (
 * {@code JVMCI_VERSION_CHECK=warn}) and continue. Otherwise, a failed check results in an
 * {@link InternalError} being raised or, if called from {@link #main(String[])}, the VM exiting
 * with a result code of {@code -1}
 *
 * This class only depends on the JDK so that it can be used without building Graal.
 */
public final class JVMCIVersionCheck {

    /**
     * Minimum JVMCI version supported by Graal.
     */
    private static final Version JVMCI_MIN_VERSION = new Version(23, 1, 15);

    /**
     * Minimum Java release supported by Graal.
     */
    private static final int JAVA_MIN_RELEASE = 17;

    public static class Version {
        private final int major;
        private final int minor;
        private final int build;

        static Version parse(String vmVersion) {
            Matcher m = Pattern.compile(".*-jvmci-(\\d+)\\.(\\d+)-b(\\d+).*").matcher(vmVersion);
            if (m.matches()) {
                try {
                    int major = Integer.parseInt(m.group(1));
                    int minor = Integer.parseInt(m.group(2));
                    int build = Integer.parseInt(m.group(3));
                    return new Version(major, minor, build);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
            return null;
        }

        public Version(int major, int minor, int build) {
            this.major = major;
            this.minor = minor;
            this.build = build;
        }

        boolean isGreaterThan(Version other) {
            if (!isLessThan(other)) {
                return !equals(other);
            }
            return false;
        }

        public boolean isLessThan(Version other) {
            if (this.major < other.major) {
                return true;
            }
            if (this.major == other.major) {
                if (this.minor < other.minor) {
                    return true;
                }
                if (this.minor == other.minor && this.build < other.build) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Version) {
                Version that = (Version) obj;
                return this.major == that.major && this.minor == that.minor && this.build == that.build;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return this.major ^ this.minor ^ this.build;
        }

        public static final String AS_TAG_FORMAT = "jvmci-%d.%d-b%02d";

        @Override
        public String toString() {
            return String.format(AS_TAG_FORMAT, major, minor, build);
        }
    }

    public static final String OPEN_LABSJDK_RELEASE_URL_PATTERN = "https://github.com/graalvm/labs-openjdk-*/releases";

    private void failVersionCheck(boolean exit, String reason, Object... args) {
        Formatter errorMessage = new Formatter().format(reason, args);
        String javaHome = props.get("java.home");
        String vmName = props.get("java.vm.name");
        errorMessage.format("Set the JVMCI_VERSION_CHECK environment variable to \"ignore\" to suppress ");
        errorMessage.format("this error or to \"warn\" to emit a warning and continue execution.%n");
        errorMessage.format("Currently used Java home directory is %s.%n", javaHome);
        errorMessage.format("Currently used VM configuration is: %s%n", vmName);
        if (vmVersion.contains("-jvmci-")) {
            errorMessage.format("Download the latest Labs OpenJDK from " + OPEN_LABSJDK_RELEASE_URL_PATTERN);
        } else {
            errorMessage.format("Download JDK %s or later.", JAVA_MIN_RELEASE);
        }
        String value = System.getenv("JVMCI_VERSION_CHECK");
        if ("warn".equals(value)) {
            System.err.println(errorMessage.toString());
        } else if ("ignore".equals(value)) {
            return;
        } else if (exit) {
            System.err.println(errorMessage.toString());
            System.exit(-1);
        } else {
            throw new InternalError(errorMessage.toString());
        }
    }

    private final String javaSpecVersion;
    private final String vmVersion;
    private final Map<String, String> props;

    private JVMCIVersionCheck(Map<String, String> props, String javaSpecVersion, String vmVersion) {
        this.props = props;
        this.javaSpecVersion = javaSpecVersion;
        this.vmVersion = vmVersion;
    }

    static void check(Map<String, String> props, boolean exitOnFailure, String format) {
        JVMCIVersionCheck checker = new JVMCIVersionCheck(props, props.get("java.specification.version"), props.get("java.vm.version"));
        checker.run(exitOnFailure, JVMCI_MIN_VERSION, format);
    }

    /**
     * Entry point for testing.
     */
    public static void check(Map<String, String> props,
                    Version minVersion,
                    String javaSpecVersion,
                    String javaVmVersion, boolean exitOnFailure) {
        JVMCIVersionCheck checker = new JVMCIVersionCheck(props, javaSpecVersion, javaVmVersion);
        checker.run(exitOnFailure, minVersion, null);
    }

    private void run(boolean exitOnFailure, Version minVersion, String format) {
        if (javaSpecVersion.compareTo(Integer.toString(JAVA_MIN_RELEASE)) < 0) {
            failVersionCheck(exitOnFailure, "Graal requires JDK " + JAVA_MIN_RELEASE + " or later.%n");
        } else {
            if (vmVersion.contains("SNAPSHOT")) {
                return;
            }
            if (vmVersion.contains("internal")) {
                // Allow local builds
                return;
            }
            if (vmVersion.contains("-jvmci-")) {
                // A "labsjdk"
                Version v = Version.parse(vmVersion);
                if (v != null) {
                    if (format != null) {
                        System.out.printf(format + "%n", v.major, v.minor, v.build);
                    }
                    Version actualMinVersion = minVersion;
                    if (javaSpecVersion.equals("19")) {
                        // Last JVMCI update for JDK 19
                        actualMinVersion = new Version(23, 0, 5);
                    }
                    if (v.isLessThan(actualMinVersion)) {
                        failVersionCheck(exitOnFailure, "The VM does not support the minimum JVMCI API version required by Graal: %s < %s.%n", v, actualMinVersion);
                    }
                    return;
                }
                failVersionCheck(exitOnFailure, "The VM does not support the minimum JVMCI API version required by Graal.%n" +
                                "Cannot read JVMCI version from java.vm.version property: %s.%n", vmVersion);
            } else {
                // Graal is compatible with all JDK versions as of JAVA_MIN_RELEASE
            }
        }
    }

    /**
     * Command line interface for performing the check.
     */
    public static void main(String[] args) {
        Properties sprops = System.getProperties();
        Map<String, String> props = new HashMap<>(sprops.size());
        for (String name : sprops.stringPropertyNames()) {
            props.put(name, sprops.getProperty(name));
        }
        String format = "%d,%d,%d";
        for (String arg : args) {
            if (arg.equals("--as-tag")) {
                format = Version.AS_TAG_FORMAT;
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }
        check(props, true, format);
    }
}
