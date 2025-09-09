/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import java.util.Collections;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    public static final String DEFAULT_VENDOR_ENTRY = "*";

    /**
     * Minimum JVMCI version supported by Graal. This maps from {@code java.specification.version}
     * to {@code java.vm.vendor} to {@link Version}. {@link #DEFAULT_VENDOR_ENTRY} can be used as a
     * default/fallback entry.
     */
    // Checkstyle: stop stable iteration order check
    private static final Map<String, Map<String, Version>> JVMCI_MIN_VERSIONS = Map.of(
                    "26", Map.of(
                                    "Oracle Corporation", createLabsJDKVersion("26+13", 1),
                                    DEFAULT_VENDOR_ENTRY, createLabsJDKVersion("26+13", 1)));
    // Checkstyle: resume stable iteration order check

    private static final int NA = 0;
    /**
     * Minimum Java release supported by Graal.
     */
    private static final int JAVA_MIN_RELEASE = 26;

    /**
     * Convenience factory for the current version scheme that only uses the JDK version and the
     * JVMCI build number.
     */
    public static Version createLabsJDKVersion(String jdkVersionString, int jvmciBuild) {
        return new Version(jdkVersionString, NA, NA, jvmciBuild, false, false);
    }

    /**
     * Convenience factory for the current version scheme that only uses the JDK version
     * <em>without</em> a JVMCI build number. This is used when running on a plain OpenJDK, not a
     * custom LabsJDK build.
     */
    public static Version createOpenJDKVersion(String jdkVersionString) {
        return new Version(jdkVersionString, NA, NA, NA, false, true);
    }

    /**
     * Legacy factory for versions without JDK version. This force sets {@link Version#jdkVersion}
     * to {@code 21}. While this is not entirely correct, it works for our purposes.
     */
    public static Version createLegacyVersion(int jvmciMajor, int jvmciMinor, int jvmciBuild) {
        return new Version("21", jvmciMajor, jvmciMinor, jvmciBuild, true, false);
    }

    public static final class Version {
        private final Runtime.Version jdkVersion;
        private final int jvmciMajor;
        private final int jvmciMinor;
        private final int jvmciBuild;
        private final boolean legacy;
        private final boolean isOpenJDK;

        static Version parse(String vmVersion) {
            Matcher m = Pattern.compile("(.+)-jvmci(-(\\d+)\\.(\\d+))?-b(\\d+).*").matcher(vmVersion);
            if (m.matches()) {
                try {
                    if (m.group(3) == null) {
                        assert m.group(4) == null : "if jvmciMajor is null jvmciMinor must also be null";
                        String jdkVersion = m.group(1);
                        int jvmciBuild = Integer.parseInt(m.group(5));
                        return createLabsJDKVersion(jdkVersion, jvmciBuild);
                    } else {
                        int jvmciMajor = Integer.parseInt(m.group(3));
                        int jvmciMinor = Integer.parseInt(m.group(4));
                        int jvmciBuild = Integer.parseInt(m.group(5));
                        return createLegacyVersion(jvmciMajor, jvmciMinor, jvmciBuild);
                    }

                } catch (NumberFormatException e) {
                    // cannot parse JVMCI version numbers -> be on the safe side and ignore
                }
            } else {
                try {
                    var rv = Runtime.Version.parse(vmVersion);
                    if (rv.pre().isEmpty() || "ea".equals(rv.pre().get())) {
                        // release or early access build
                        return createOpenJDKVersion(stripVersion(rv));
                    }
                } catch (IllegalArgumentException e) {
                    // unexpected version string -> be on the safe side and ignore
                }
            }
            return null;
        }

        /**
         * Returns a {@linkplain java.lang.Runtime.Version version string} without
         * {@link java.lang.Runtime.Version#pre()} and {@link java.lang.Runtime.Version#optional()}.
         */
        private static String stripVersion(Runtime.Version rv) {
            var sb = new StringBuilder(rv.version().stream().map(Object::toString).collect(Collectors.joining(".")));
            if (rv.build().isPresent()) {
                sb.append("+").append(rv.build().get());
            }
            return sb.toString();
        }

        private Version(String jdkVersionString, int jvmciMajor, int jvmciMinor, int jvmciBuild, boolean legacy, boolean isOpenJDK) {
            this(Runtime.Version.parse(jdkVersionString), jvmciMajor, jvmciMinor, jvmciBuild, legacy, isOpenJDK);
        }

        private Version(Runtime.Version jdkVersion, int jvmciMajor, int jvmciMinor, int jvmciBuild, boolean legacy, boolean isOpenJDK) {
            this.jdkVersion = jdkVersion;
            this.jvmciMajor = jvmciMajor;
            this.jvmciMinor = jvmciMinor;
            this.jvmciBuild = jvmciBuild;
            this.legacy = legacy;
            this.isOpenJDK = isOpenJDK;
        }

        boolean isGreaterThan(Version other) {
            if (!isLessThan(other)) {
                return !equals(other);
            }
            return false;
        }

        public boolean isLessThan(Version other) {
            if (this.legacy && !other.legacy) {
                return true;
            }
            if (this.legacy == other.legacy) {
                int compareTo = this.legacy ? 0 : this.jdkVersion.compareToIgnoreOptional(other.jdkVersion);
                if (compareTo < 0) {
                    return true;
                }
                if (compareTo == 0) {
                    if (this.isOpenJDK != other.isOpenJDK) {
                        // comparing OpenJDK version with LabsJDK version.
                        return false;
                    }
                    if (this.jvmciMajor < other.jvmciMajor) {
                        return true;
                    }
                    if (this.jvmciMajor == other.jvmciMajor) {
                        if (this.jvmciMinor < other.jvmciMinor) {
                            return true;
                        }
                        if (this.jvmciMinor == other.jvmciMinor && this.jvmciBuild < other.jvmciBuild) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Version that) {
                return this.jdkVersion.equals(that.jdkVersion) && this.jvmciMajor == that.jvmciMajor && this.jvmciMinor == that.jvmciMinor && this.jvmciBuild == that.jvmciBuild;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return this.jdkVersion.hashCode() ^ this.jvmciMajor ^ this.jvmciMinor ^ this.jvmciBuild;
        }

        public static final String AS_TAG_FORMAT_22_AND_LATER = "%s-jvmci-b%02d";
        public static final String AS_TAG_FORMAT_21_AND_EARLIER = "jvmci-%d.%d-b%02d";

        @Override
        public String toString() {
            if (isOpenJDK) {
                return jdkVersion.toString();
            }
            if (!legacy) {
                return String.format(AS_TAG_FORMAT_22_AND_LATER, jdkVersion, jvmciBuild);
            } else {
                return String.format(AS_TAG_FORMAT_21_AND_EARLIER, jvmciMajor, jvmciMinor, jvmciBuild);
            }
        }

        public String printFormat(PrintFormat format) {
            return switch (format) {
                case TUPLE -> String.format("%s,%d,%d,%d", jdkVersion, jvmciMajor, jvmciMinor, jvmciBuild);
                case AS_TAG -> toString();
            };
        }

        public Version stripLTS() {
            if ("LTS".equals(jdkVersion.optional().orElse(null))) {
                String version = jdkVersion.toString();
                assert version.endsWith("-LTS");
                String stripped = version.substring(0, version.length() - 4);
                return new Version(stripped, jvmciMajor, jvmciMinor, jvmciBuild, legacy, isOpenJDK);
            } else {
                return this;
            }
        }
    }

    public static final String OPEN_LABSJDK_RELEASE_URL_PATTERN = "https://github.com/graalvm/labs-openjdk/releases";

    private final String javaSpecVersion;
    private final String vmVersion;
    private final Map<String, String> props;

    private JVMCIVersionCheck(Map<String, String> props, String javaSpecVersion, String vmVersion) {
        this.props = props;
        this.javaSpecVersion = javaSpecVersion;
        this.vmVersion = vmVersion;
    }

    enum PrintFormat {
        TUPLE,
        AS_TAG
    }

    private static String getRequiredProperty(Map<String, String> props, String name) {
        return Objects.requireNonNull(props.get(name), "missing required property: " + name);
    }

    public static Version getMinVersion(Map<String, String> props, Map<String, Map<String, Version>> jvmciMinVersions) {
        String javaSpecVersion = getRequiredProperty(props, "java.specification.version");
        String javaVmVendor = getRequiredProperty(props, "java.vm.vendor");
        Map<String, Version> versionMap = jvmciMinVersions.getOrDefault(javaSpecVersion, Collections.emptyMap());
        return versionMap.getOrDefault(javaVmVendor, versionMap.get(DEFAULT_VENDOR_ENTRY));
    }

    static void check(Map<String, String> props, boolean exitOnFailure, PrintFormat format) {
        check(props, exitOnFailure, format, JVMCI_MIN_VERSIONS);
    }

    /**
     * Checks the JVMCI version. This method expects the following properties to be present in
     * {@code props}:
     * <ul>
     * <li>{@code java.specification.version}: Java specification version, e.g., {@code "21"}</li>
     * <li>{@code java.vm.version}: Full Java VM version string, e.g, {@code "21+35"}</li>
     * <li>{@code java.vm.vendor}: The vendor of the Java VM, e.g., {@code "GraalVM Community"}</li>
     * </ul>
     */
    public static void check(Map<String, String> props, boolean exitOnFailure, PrintFormat format, Map<String, Map<String, Version>> jvmciMinVersions) {
        JVMCIVersionCheck checker = newJVMCIVersionCheck(props);
        String reason = checker.run(getMinVersion(props, jvmciMinVersions), format, jvmciMinVersions);
        if (reason != null) {
            Formatter errorMessage = new Formatter().format("%s%n", reason);
            errorMessage.format("Set the JVMCI_VERSION_CHECK environment variable to \"ignore\" to suppress ");
            errorMessage.format("this error or to \"warn\" to emit a warning and continue execution.%n");
            checker.appendJVMInfo(errorMessage);
            failVersionCheck(exitOnFailure, errorMessage.toString());
        }
    }

    private static JVMCIVersionCheck newJVMCIVersionCheck(Map<String, String> props) {
        String javaSpecVersion = getRequiredProperty(props, "java.specification.version");
        String javaVmVersion = getRequiredProperty(props, "java.vm.version");
        return new JVMCIVersionCheck(props, javaSpecVersion, javaVmVersion);
    }

    private void appendJVMInfo(Formatter formatter) {
        String javaHome = props.get("java.home");
        String vmName = props.get("java.vm.name");
        formatter.format("Currently used Java home directory is %s.%n", javaHome);
        formatter.format("Currently used VM configuration is: %s%n", vmName);
        if (vmVersion.contains("-jvmci-")) {
            formatter.format("Download the latest Labs OpenJDK from " + OPEN_LABSJDK_RELEASE_URL_PATTERN);
        } else {
            formatter.format("Download JDK %s or later.", JAVA_MIN_RELEASE);
        }
    }

    private static void failVersionCheck(boolean exit, String errorMessage) {
        String value = System.getenv("JVMCI_VERSION_CHECK");
        if ("warn".equals(value)) {
            System.err.println(errorMessage);
        } else if ("ignore".equals(value)) {
            return;
        } else if (exit) {
            System.err.println(errorMessage);
            System.exit(-1);
        } else {
            throw new InternalError(errorMessage);
        }
    }

    /**
     * Checks the JVMCI version, returning an error message if an issue is found, or {@code null} if
     * no error is detected.
     *
     * @param props system properties with the following required properties:
     *            <ul>
     *            <li>{@code java.specification.version}: Java specification version, e.g.,
     *            {@code "21"}</li>
     *            <li>{@code java.vm.version}: Full Java VM version string, e.g.,
     *            {@code "21+35"}</li>
     *            <li>{@code java.vm.vendor}: The vendor of the Java VM, e.g.,
     *            {@code "GraalVM Community"}</li>
     *            </ul>
     * @return an error message if the version check fails, or {@code null} if no error is detected
     */
    public static String check(Map<String, String> props) {
        JVMCIVersionCheck checker = newJVMCIVersionCheck(props);
        String reason = checker.run(getMinVersion(props, JVMCI_MIN_VERSIONS), null, JVMCI_MIN_VERSIONS);
        if (reason != null) {
            Formatter errorMessage = new Formatter().format("%s%n", reason);
            checker.appendJVMInfo(errorMessage);
            return errorMessage.toString();
        }
        return null;
    }

    /**
     * Performs the JVMCI version check.
     *
     * @return an error message if the version check fails, or {@code null} if no error is detected
     */
    private String run(Version minVersion, PrintFormat format, Map<String, Map<String, Version>> jvmciMinVersions) {
        if (javaSpecVersion.compareTo(Integer.toString(JAVA_MIN_RELEASE)) < 0) {
            return "Graal requires JDK " + JAVA_MIN_RELEASE + " or later.";
        } else {
            if (vmVersion.contains("SNAPSHOT")) {
                return null;
            }
            if (vmVersion.contains("internal")) {
                // Allow local builds
                return null;
            }
            if (!vmVersion.contains("-jvmci-")) {
                var rv = Runtime.Version.parse(vmVersion);
                if (rv.pre().isPresent() && !"ea".equals(rv.pre().get())) {
                    // Not a release or early access OpenJDK version
                    return null;
                }
            }
            // A "labsjdk" or a known OpenJDK
            if (minVersion == null) {
                OptionalInt max = jvmciMinVersions.keySet().stream().mapToInt(Integer::parseInt).max();
                if (max.isPresent()) {
                    int maxVersion = max.getAsInt();
                    int specVersion = Integer.parseInt(javaSpecVersion);
                    if (specVersion < maxVersion) {
                        return String.format("The VM does not support JDK %s. Please update to JDK %s.", specVersion, maxVersion);
                    }
                }
                // No minimum JVMCI version specified for JDK version
                return null;
            }
            Version v = Version.parse(vmVersion);
            if (v != null) {
                if (format != null) {
                    System.out.println(v.stripLTS().printFormat(format));
                }
                if (v.isLessThan(minVersion)) {
                    return String.format("The VM does not support the minimum JVMCI API version required by Graal: %s < %s.", v, minVersion);
                }
                return null;
            }
            return String.format("The VM does not support the minimum JVMCI API version required by Graal.%n" +
                            "Cannot read JVMCI version from java.vm.version property: %s.", vmVersion);
        }
    }

    /**
     * Command line interface for performing the check.
     */
    public static void main(String[] args) {
        Properties sprops = System.getProperties();
        Map<String, String> props = new LinkedHashMap<>(sprops.size());
        for (String name : sprops.stringPropertyNames()) {
            props.put(name, sprops.getProperty(name));
        }
        PrintFormat format = PrintFormat.TUPLE;
        boolean minVersion = false;
        for (String arg : args) {
            if (arg.equals("--as-tag")) {
                format = PrintFormat.AS_TAG;
            } else if (arg.equals("--min-version")) {
                minVersion = true;
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }
        if (minVersion) {
            String javaSpecVersion = props.get("java.specification.version");
            Version v = getMinVersion(props, JVMCI_MIN_VERSIONS);
            if (v == null) {
                System.out.printf("No minimum JVMCI version specified for JDK version %s.%n", javaSpecVersion);
            } else {
                System.out.println(v.printFormat(format));
            }
        } else {
            check(props, true, format);
        }
    }
}
