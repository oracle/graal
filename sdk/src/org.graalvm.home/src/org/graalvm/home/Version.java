/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.home;

import java.util.Arrays;
import java.util.Objects;

/**
 * A version utility to canonicalize and compare GraalVM versions. The GraalVM version string is not
 * standardized and may change without notice. This class is designed to evolve with GraalVM to
 * cover all used version formats in use. It allows to create, validate and compare GraalVM
 * versions. Do not rely on the format of the raw version string or the result of
 * {@link #toString()}, only use it to produce output for humans.
 * <p>
 * To create version instances of a particular version use the {@link #create(int...)} factory
 * method. Use {@link #getCurrent()} to lookup the current GraalVM version or {@link #parse(String)}
 * to parse it from a raw string.
 * <p>
 *
 * <h3>Usage example:</h3> This code example compares the current GraalVM version to be at least
 * 19.3 and fails if it is not.
 *
 * <pre>
 * if (Version.getCurrent().compareTo(19, 3) < 0) {
 *     throw new IllegalStateException("Invalid GraalVM version. Must be at least 19.3.");
 * }
 * </pre>
 * <p>
 * Note: this class has a natural ordering that is inconsistent with equals.
 *
 * @see HomeFinder#getVersion()
 * @since 19.3
 */
public final class Version implements Comparable<Version> {

    private static final String SNAPSHOT_STRING = "snapshot";
    private static final String SNAPSHOT_SUFFIX = "dev";
    private static final int MIN_VERSION_DIGITS = 3;

    private final int[] versions;
    private final String suffix;
    private final boolean snapshot;

    Version(int... versions) {
        this.versions = versions;
        this.suffix = null;
        this.snapshot = false;
    }

    Version(String v) {
        if (v.equals(SNAPSHOT_STRING)) {
            snapshot = true;
            versions = new int[0];
            suffix = SNAPSHOT_STRING;
        } else {
            int dash = v.indexOf('-');
            int end;
            if (dash != -1) {
                suffix = v.substring(dash + 1, v.length());
                snapshot = suffix.equals(SNAPSHOT_SUFFIX);
                end = dash;
            } else {
                suffix = null;
                snapshot = false;
                end = v.length();
            }
            String versionsString = v.substring(0, end);
            // -1 to also include trailing empty strings.
            String[] versionChunks = versionsString.split("\\.", -1);
            int[] intVersions = new int[versionChunks.length];
            for (int i = 0; i < versionChunks.length; i++) {
                try {
                    intVersions[i] = Integer.parseInt(versionChunks[i]);
                } catch (NumberFormatException f) {
                    throw invalid(v);
                }
                // versions cannot be negative as we would already
                // cut at the first occurrence of '-'
                assert intVersions[i] >= 0;
            }
            // trim trailing zeros
            intVersions = trimTrailingZeros(intVersions);
            if (intVersions.length == 0) {
                throw invalid(v);
            }
            this.versions = intVersions;
        }
    }

    private static int[] trimTrailingZeros(int[] intVersions) {
        int trimVersions = intVersions.length - 1;
        for (; trimVersions >= 0; trimVersions--) {
            if (intVersions[trimVersions] != 0) {
                break;
            }
        }
        if (trimVersions != intVersions.length - 1) {
            return Arrays.copyOf(intVersions, trimVersions + 1);
        } else {
            return intVersions;
        }
    }

    private static IllegalArgumentException invalid(String v) {
        return new IllegalArgumentException("Invalid version string '" + v + "'.");
    }

    /**
     * Returns <code>true</code> if this is a supported release build of GraalVM else
     * <code>false</code>. Use this for implementation assertions that verify that only releases are
     * deployed to production.
     *
     * @see #isSnapshot()
     * @since 19.3
     */
    public boolean isRelease() {
        return !snapshot;
    }

    /**
     * Returns <code>true</code> if this is an unsupported snapshot build of GraalVM else
     * <code>false</code>. Use this for implementation assertions that verify that only releases are
     * deployed to production.
     *
     * @see #isSnapshot()
     * @since 19.3
     */
    public boolean isSnapshot() {
        return snapshot;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.3
     */
    @Override
    public int compareTo(Version o) {
        int[] thisVersions = versions;
        int[] otherVersions = o.versions;
        for (int i = 0; i < Math.max(otherVersions.length, thisVersions.length); i++) {
            int version = i >= thisVersions.length ? 0 : thisVersions[i];
            int otherVersion = i >= otherVersions.length ? 0 : otherVersions[i];
            int cmp = Integer.compare(version, otherVersion);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    /**
     * Compares this version to another GraalVM version. This is equivalent to using: <code>
     * compareTo(Version.create(compareVersions))
     * </code>.
     *
     * @since 19.3
     */
    public int compareTo(int... compareVersions) {
        return compareTo(Version.create(compareVersions));
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.3
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Version)) {
            return false;
        }
        Version other = (Version) obj;
        return Arrays.equals(this.versions, other.versions) && this.snapshot == other.snapshot && Objects.equals(suffix, other.suffix);
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.3
     */
    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(this.versions), snapshot, suffix);
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.3
     */
    @Override
    public String toString() {
        if (versions.length == 0) {
            assert suffix != null && suffix.equals(SNAPSHOT_STRING);
            return suffix;
        }
        StringBuilder b = new StringBuilder();
        String sep = "";
        for (int i = 0; i < Math.max(MIN_VERSION_DIGITS, versions.length); i++) {
            b.append(sep);
            if (i < versions.length) {
                b.append(versions[i]);
            } else {
                b.append(0);
            }
            sep = ".";
        }
        if (suffix != null) {
            b.append("-").append(suffix);
        }
        return b.toString();
    }

    /**
     * Parses a GraalVM version from its String raw format. Throws {@link IllegalArgumentException}
     * if the passed string is not a valid GraalVM version.
     *
     * @since 19.3
     */
    public static Version parse(String versionString) throws IllegalArgumentException {
        Objects.requireNonNull(versionString);
        return new Version(versionString);
    }

    /**
     * Constructs a new GraalVM version from a list of version numbers. The versions must not be
     * <code>null</code> and none of the version numbers must be negative. At least one version
     * number must be non-zero.
     *
     * @see #compareTo(int...)
     * @since 19.3
     */
    public static Version create(int... versions) throws IllegalArgumentException {
        Objects.requireNonNull(versions);
        int[] useVersions = trimTrailingZeros(versions);
        if (useVersions.length == 0) {
            throw new IllegalArgumentException("At least one non-zero version must be specified.");
        }
        for (int i = 0; i < useVersions.length; i++) {
            if (useVersions[i] < 0) {
                throw new IllegalArgumentException("Versions must not be negative.");
            }
        }
        return new Version(useVersions);
    }

    /**
     * Returns the current GraalVM version of the installed component. Never <code>null</code>.
     *
     * @see HomeFinder#getVersion()
     * @since 19.3
     */
    public static Version getCurrent() {
        return parse(HomeFinder.getInstance().getVersion());
    }

}
