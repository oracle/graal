/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Version represents a component version. It has the form agreed for Graal product:
 * {@code Year.Update.Patch.OneOffChange-release}. Pre-release versions (Year.UpdateX) have their
 * Patch version set to 0; so for example 2020.0-0.beta.1
 * <p/>
 * "Installation" versions are versions with the same Year.Update.Patch-Release. Each Installation
 * version will go to a separate directory.
 * 
 * @author sdedic
 */
public final class Version implements Comparable<Version> {
    /**
     * Represents no version at all. All versions are greater than this one.
     */
    public static final Version NO_VERSION = new Version("0.0.0.0", false);
    private static final String WILDCARD = "*";

    private final String versionString;
    private final String normalizedString;
    private final List<String> releaseParts;
    private final List<String> versionParts;

    /**
     * Contains wildcard.
     */
    private final boolean wildcard;

    /**
     * Entered by user, displayed 'as is'.
     */
    private final boolean user;

    Version(String versionString, boolean fromUser) throws IllegalArgumentException {
        this.versionString = versionString;

        boolean wc = false;
        String normalized = fromUser ? versionString : SystemUtils.normalizeOldVersions(versionString);
        List<String> vp;
        int releaseDash = normalized.indexOf('-');
        if (releaseDash == -1) {
            if (fromUser) {
                wc = true;
                releaseParts = Collections.singletonList(WILDCARD);
            } else {
                releaseParts = Collections.emptyList();
            }
            vp = parseParts(normalized);
        } else {
            String vS = normalized.substring(0, releaseDash);
            String rS = normalized.substring(releaseDash + 1);
            vp = parseParts(vS);
            List<String> rp = parseParts(rS);
            if (fromUser && !rp.isEmpty()) {
                String first = rp.get(0);
                try {
                    Integer.parseInt(first);
                } catch (NumberFormatException ex) {
                    // if (rp.size() > 1 && !Character.isDigit(rp.get(1).charAt(0))) {
                    rp.add(0, WILDCARD);
                    wc = true;
                    // }
                }
                rp.add(WILDCARD);
            }
            releaseParts = Collections.unmodifiableList(rp);
        }
        if (vp.size() < 2 || vp.size() > 4 || !Character.isDigit(vp.get(0).charAt(0)) || !Character.isDigit(vp.get(1).charAt(0))) {
            throw new IllegalArgumentException("A format Year.Release[.Update[.Patch]] is required. Got: " + versionString);
        }
        // normalize all, not just releases, as releases are now "-1".
        if (vp.size() < 4 && !isOldStyleVersion(vp.get(0))) {
            vp = new ArrayList<>(vp);
            while (vp.size() < 4) {
                vp.add("0");
            }
            this.normalizedString = print(vp, releaseParts);
        } else {
            normalizedString = normalized;
        }
        versionParts = vp;
        wildcard = wc;
        user = fromUser;
    }

    static boolean isOldStyleVersion(String s) {
        try {
            // now mainly for ancient test data. PEDNING: update test data.
            return Integer.parseInt(s) < 1;
        } catch (NumberFormatException ex) {
            // expected
            return false;
        }
    }

    static String print(List<String> vParts, List<String> rParts) {
        StringBuilder sb = new StringBuilder();
        for (String vp : vParts) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(vp);
        }
        if (!rParts.isEmpty()) {
            sb.append('-');
            boolean n = false;
            for (String rp : rParts) {
                if (n) {
                    sb.append('.');
                }
                sb.append(rp);
                n = true;
            }
        }
        return sb.toString();
    }

    Version(String orig, List<String> vParts, List<String> rParts, boolean wc, boolean fromUser) {
        this.versionParts = new ArrayList<>(vParts);
        this.releaseParts = new ArrayList<>(rParts);
        this.versionString = orig != null ? orig : print(vParts, rParts);
        if (vParts.size() < 4 && isOldStyleVersion(vParts.get(0))) {
            while (versionParts.size() < 4) {
                versionParts.add("0");
            }
            this.normalizedString = print(versionParts, releaseParts);
        } else {
            normalizedString = versionString;
        }
        wildcard = wc;
        user = fromUser;
    }

    /**
     * Returns a Version variant, which can be compared to discover eligible updates.
     * 
     * 1..0-0.beta.1 -> 1..0.x yes 1..0-0.beta.1 -> 1..0-0.rc.1 yes 1..0.0 -> 1..0.1 yes 1..0.0 ->
     * 1..1.x no
     * 
     * @return part of version which is the same for all updatable packages.
     */
    public Version updatable() {
        List<String> vp = new ArrayList<>(versionParts);
        vp.subList(3, vp.size()).clear();
        while (vp.size() < 3) {
            vp.add("0");
        }
        return new Version(null, vp, Collections.emptyList(), false, false);
    }

    public Version onlyVersion() {
        return new Version(null, versionParts, Collections.emptyList(), false, false);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 43 * hash + Objects.hashCode(this.versionString);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        if (this == NO_VERSION || obj == NO_VERSION) {
            // the other must be something else, sorry
            return false;
        }
        final Version other = (Version) obj;
        /*
         * if (!Objects.equals(this.versionString, other.versionString)) { return false; } return
         * true;
         */
        return compareTo(other) == 0;
    }

    @Override
    public String toString() {
        return normalizedString;
    }

    public String originalString() {
        return versionString;
    }

    private static List<String> parseParts(String s) throws IllegalArgumentException {
        List<String> parts = new ArrayList<>();
        final int l = s.length();
        boolean digit = false;
        int lastP = -1;

        for (int i = 0; i < l; i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                // separator
                if (lastP >= 0) {
                    parts.add(s.substring(lastP, i));
                    lastP = -1;
                }
                continue;
            }
            boolean nowDigit = Character.isDigit(c);
            if (nowDigit != digit && lastP >= 0) {
                parts.add(s.substring(lastP, i));
                lastP = i;
            } else if (lastP < 0) {
                lastP = i;
            }
            digit = nowDigit;
        }
        if (lastP >= 0) {
            parts.add(s.substring(lastP));
        }
        return parts;
    }

    @Override
    public int compareTo(Version o) {
        if (o == this) {
            return 0;
        } else if (o == NO_VERSION) {
            return 1;
        } else if (this == NO_VERSION) {
            return -1;
        } else if (o == null) {
            return 1;
        }
        int c = compareVersionParts(versionParts, o.versionParts, false);
        if (c != 0) {
            return c;
        }
        return compareVersionParts(releaseParts, o.releaseParts, true);
    }

    private static int compareVersionParts(List<String> pA, List<String> pB, boolean release) {
        Iterator<String> iA = pA.iterator();
        Iterator<String> iB = pB.iterator();
        int res = 0;
        String sA = null;
        String sB = null;
        while ((res == 0) && iA.hasNext() && iB.hasNext()) {
            sA = iA.next();
            sB = iB.next();
            res = compareVersionPart(sA, sB);
        }
        if (res != 0 || !(iA.hasNext() || iB.hasNext())) {
            return res;
        }
        if (iA.hasNext()) {
            if (release) {
                /*
                 * if (WILDCARD.equals(iA.next()) || (WILDCARD.equals(sB))) { return 0; }
                 */
                if (WILDCARD.equals(iA.next()) && !iA.hasNext()) {
                    return 0;
                }
                if (WILDCARD.equals(sB)) {
                    return 0;
                }
            }
            return 1;
        } else if (iB.hasNext()) {
            if (release) {
                if (WILDCARD.equals(iB.next()) && !iB.hasNext()) {
                    return 0;
                }
                if (WILDCARD.equals(sA)) {
                    return 0;
                }
                // special case: if there's just one part and that one is zero, define it the same
                if (pB.size() == 1 && "0".equals(pB.get(0))) {
                    return 0;
                }
            }
            return -1;
        }
        throw new IllegalStateException("Should not happen"); // NOI18N
    }

    /**
     * Compares version parts, RPM-like rules. Acts like a {@link java.util.Comparator}.
     * 
     * @param a first version
     * @param b second version.
     * @return less than 0, 0 or more than 0, as with Comparator.
     */
    public static int compareVersionPart(String a, String b) {
        if (WILDCARD.equals(a) || WILDCARD.equals(b)) {
            return 0;
        }
        // handle of the parts == null
        if (a == null) {
            if (b != null) {
                return -1;
            } else {
                return 0;
            }
        } else if (b == null) {
            return 1;
        }
        boolean dA = Character.isDigit(a.charAt(0));
        boolean dB = Character.isDigit(a.charAt(0));
        if (dA != dB) {
            // numeric part is always later
            return dA ? 1 : -1;
        }
        if (dA && dB) {
            int l = a.length() - b.length();
            if (l != 0) {
                return l;
            }
        }
        // if at this point dA == dB == true, all numbers have the same length, so we
        // can use lexicographic comparison to numeric parts as well.
        return a.compareTo(b);
    }

    public boolean hasWildcard() {
        return wildcard;
    }

    public Version installVersion() {
        List<String> vps = new ArrayList<>();
        vps.add(versionParts.get(0));
        vps.add(versionParts.get(1));
        if (versionParts.size() > 2) {
            vps.add(versionParts.get(2));
        } else {
            vps.add("0");
        }
        vps.add("0");
        return new Version(null, vps, releaseParts, wildcard, user);
    }

    /**
     * Produces a 'human readable' version. The intention is to hide complexities of prerelease
     * versioning from the poor user.
     * 
     * @return human-readable string.
     */
    public String displayString() {
        if (user) {
            return originalString();
        }
        StringBuilder sb = new StringBuilder();
        List<String> vps = new ArrayList<>(versionParts);
        if (vps.size() == 4 && "0".equals(vps.get(3))) { // NOI18N
            vps.remove(3);
        }
        for (String vp : vps) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(vp);
        }

        if (releaseParts.isEmpty()) {
            // no release part, no magic
            return sb.toString();
        }
        String firstRel = releaseParts.get(0);
        List<String> printedParts = new ArrayList<>(releaseParts);
        try {
            Integer.parseInt(firstRel);
            printedParts.remove(0);
        } catch (NumberFormatException ex) {
        }

        if (printedParts.isEmpty()) {
            return sb.toString();
        }

        boolean name = false;
        boolean next = false;
        int patchChar = -1;
        sb.append("-");
        for (String s : printedParts) {
            if (s.isEmpty() || WILDCARD.equals(s)) { // NOI18N
                // skip artifical wildcards
                continue;
            }
            if (Character.isDigit(s.charAt(0))) {
                if (next && !name) {
                    sb.append("."); // NOI18N
                }
                if (name && patchChar != -1) {
                    sb.setCharAt(patchChar, '.'); // patch to .nameXX
                }
                sb.append(s);
                name = false;
                patchChar = -1;
            } else {
                patchChar = -1;
                if (name) {
                    // was name, now is also name
                    sb.append("_"); // NOI18N
                } else {
                    if (next) {
                        patchChar = sb.length();
                        sb.append("-"); // NOI18N
                    }
                }
                sb.append(s);

                name = true;
            }
            next = true;
        }
        return sb.toString();
    }

    public Match match(Match.Type type) {
        return new Match(this, type);
    }

    /**
     * Parses a new Version object.
     * 
     * @param versionString the String specification of the version
     * @return constructed Version object.
     */
    public static Version fromString(String versionString) {
        return versionString == null ? NO_VERSION : new Version(versionString, false);
    }

    public static Version fromUserString(String userVersion) {
        return userVersion == null ? NO_VERSION : new Version(userVersion, true);
    }

    public static final String EXACT_VERSION = "="; // NOI18N
    public static final String GREATER_VERSION = "+"; // NOI18N
    public static final String COMPATIBLE_VERSION = "~"; // NOI18N

    /**
     * Parses ID and optional version specification into ID and version selector.
     * 
     * @param idSpec ID specification
     * @param matchOut the version match
     * @return id
     */
    public static String idAndVersion(String idSpec, Version.Match[] matchOut) {
        int eqIndex = idSpec.indexOf(EXACT_VERSION);
        int moreIndex = idSpec.indexOf(GREATER_VERSION);
        int compatibleIndex = idSpec.indexOf(COMPATIBLE_VERSION);
        int i = -1;
        Version.Match.Type type = null;

        if (eqIndex > 0) {
            type = Match.Type.EXACT;
            i = eqIndex;
        } else if (moreIndex > 0) {
            type = Match.Type.INSTALLABLE;
            i = moreIndex;
        } else if (compatibleIndex > 0) {
            type = Match.Type.COMPATIBLE;
            i = compatibleIndex;
        } else {
            matchOut[0] = Version.NO_VERSION.match(Match.Type.MOSTRECENT);
            return idSpec;
        }
        matchOut[0] = Version.fromUserString(idSpec.substring(i + 1)).match(type);
        return idSpec.substring(0, i);
    }

    /**
     * Accepts version specification and creates a version filter.
     * <p>
     * Version-input must start with "+", "~", "=" or a digit. Non-version inputs will produce
     * {@code null}.
     * </p>
     * <ul>
     * <li>=version means exactly the version specified ({@link Match.Type#EXACT})
     * <li>+version means the specified version, or above ({@link Match.Type#INSTALLABLE})
     * <li>~version or version means that version or greater, but within a release range (
     * {@link Match.Type#COMPATIBLE}
     * </ul>
     * 
     * @param spec
     * @return version match or {@code null}.
     */
    public static Version.Match versionFilter(String spec) {
        if (spec == null || spec.isEmpty()) {
            return null;
        }

        int eqIndex = spec.indexOf(EXACT_VERSION);
        int moreIndex = spec.indexOf(GREATER_VERSION);
        int compatibleIndex = spec.indexOf(COMPATIBLE_VERSION);
        int i = -1;
        Version.Match.Type type = null;

        if (eqIndex >= 0) {
            type = Match.Type.EXACT;
            i = eqIndex;
        } else if (moreIndex >= 0) {
            type = Match.Type.INSTALLABLE;
            i = moreIndex;
        } else if (compatibleIndex >= 0) {
            type = Match.Type.COMPATIBLE;
            i = compatibleIndex;
        } else {
            if (Character.isDigit(spec.charAt(0))) {
                return Version.fromString(spec).match(Match.Type.COMPATIBLE);
            } else {
                return null;
            }
        }
        return Version.fromString(spec.substring(i + 1)).match(type);
    }

    public static final class Match implements Predicate<Version> {
        public enum Type {
            /**
             * The exact version number.
             */
            EXACT,

            /**
             * Versions greater or equal than the one defined.
             */
            INSTALLABLE,

            /**
             * Also greater, but also indicates the most recent one is needed.
             */
            MOSTRECENT,

            /**
             * Versions compatible. For version r.x.y.z, all versions with the same r.x.y are
             * compatible.
             */
            COMPATIBLE,

            /**
             * Version which is equal or greater.
             */
            GREATER,

            /**
             * Dependency check. The tested install version must be the same, and the patchlevel
             * must be lower or equal.
             */
            SATISFIES,
        }

        private final Type matchType;
        private final Version version;

        Match(Version version, Type matchType) {
            this.matchType = matchType;
            this.version = version;
        }

        @Override
        public boolean test(Version t) {
            if (t == null) {
                return matchType != Type.EXACT;
            }
            switch (matchType) {
                case EXACT:
                    return version.equals(t);

                case GREATER:
                    return version.compareTo(t) <= 0;

                case INSTALLABLE:
                    return version.installVersion().compareTo(t.installVersion()) <= 0;

                case MOSTRECENT:
                    throw new IllegalArgumentException();

                case COMPATIBLE:
                    return version.installVersion().equals(t.installVersion());

                case SATISFIES:
                    int a = version.installVersion().compareTo(t.installVersion());
                    if (a < 0) {
                        return true;
                    }
                    return version.onlyVersion().compareTo(t.onlyVersion()) >= 0;
            }
            return false;
        }

        public Version getVersion() {
            return version;
        }

        public Type getType() {
            return matchType;
        }

        @Override
        public String toString() {
            return matchType.toString() + "[" + version + "]";
        }

        /**
         * Attempts to turn a wildcard version match into normal one.
         * 
         * @param allVersions all versions to select from
         * @return resolved match, or match that will produce an error on test.
         */
        public Match resolveWildcards(Collection<Version> allVersions, Feedback fb) {
            if (!version.hasWildcard()) {
                return this;
            }
            List<Version> ordered = new ArrayList<>(allVersions);
            Collections.sort(ordered);
            Version candidate = null;
            switch (matchType) {
                case MOSTRECENT:
                    throw new IllegalArgumentException();

                case EXACT:
                    // exact: find a matching version,
                    // if not found return the suppressed wildcard one for reporting
                    for (Version v : ordered) {
                        if (v.compareTo(version) == 0) {
                            if (candidate != null) {
                                // should not really happen
                                throw new IllegalArgumentException();
                            }
                            candidate = v;
                        }
                    }
                    if (candidate == null) {
                        return this;
                    } else {
                        return new Match(candidate, matchType);
                    }

                case SATISFIES:
                case INSTALLABLE:
                case GREATER:
                    // the lowest possible matching version
                    // exact: find a matching version,
                    // if not found return the suppressed wildcard one for reporting
                    for (Version v : ordered) {
                        if (v.compareTo(version) >= 0) {
                            candidate = v;
                            break;
                        }
                    }
                    if (candidate == null) {
                        return this;
                    } else {
                        return new Match(candidate, matchType);
                    }

                case COMPATIBLE:
                    Version myInst = version.installVersion();
                    Version myBase = myInst.onlyVersion();
                    Version report = null;
                    for (Version v : ordered) {
                        Version inst = v.installVersion();
                        if (myInst.equals(inst)) {
                            candidate = v;
                        }
                        if (myBase.equals(inst.onlyVersion())) {
                            report = v;
                        }
                    }
                    if (candidate == null) {
                        String msg;
                        if (report == null) {
                            msg = fb.withBundle(Version.class).l10n(
                                            "VERSION_UnknownVersion1", version.displayString(), null);
                        } else {
                            msg = fb.withBundle(Version.class).l10n(
                                            "VERSION_UnknownVersion2", version.displayString(), report.displayString());
                        }
                        throw new UnknownVersionException(
                                        msg,
                                        version, report);
                    } else {
                        return new Match(candidate, matchType);
                    }
                default:
                    throw new IllegalStateException();
            }
        }
    }
}
