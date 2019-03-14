/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.graalvm.component.installer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Version represents a component version. It has the form agreed for Graal product:
 * {@code Year.Update.Patch.OneOffChange-release}. Pre-release versions (Year.UpdateX) have
 * their Patch version set to 0; so for example 2020.0-0.beta.1
 * <p/>
 * "Installation" versions are versions with the same Year.Update.Patch-Release. Each 
 * Installation version will go to a separate directory.
 * 
 * @author sdedic
 */
public final class Version implements Comparable<Version> {
    /**
     * Represents no version at all. All versions are greater than this one.
     */
    public static final Version NO_VERSION = new Version("0.0.0.0");
    
    private final String versionString;
    private final List<String> releaseParts;
    private final List<String> versionParts;
            
    Version(String versionString) throws IllegalArgumentException {
        this.versionString = versionString;
        
        int releaseDash = versionString.indexOf('-');
        if (releaseDash == -1) {
            releaseParts = Collections.emptyList();
            versionParts = parseParts(versionString);
        } else {
            String vS = versionString.substring(0, releaseDash);
            String rS = versionString.substring(releaseDash + 1);
            versionParts = parseParts(vS);
            releaseParts = parseParts(rS);
        }
        if (versionParts.size() < 2) {
            throw new IllegalArgumentException("At least Year.Update is required. Got: " + versionString);
        }
    }
    
    Version(List<String> vParts, List<String> rParts) {
        this.versionParts = new ArrayList<>(vParts);
        this.releaseParts = new ArrayList<>(rParts);
        
        StringBuilder sb = new StringBuilder();
        for (String vp : versionParts) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(vp);
        }
        if (!releaseParts.isEmpty()) {
            sb.append('-');
            for (String rp : releaseParts) {
                if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(rp);
            }
        }
        this.versionString = sb.toString();
    }
    
    /**
     * Returns a Version variant, which can be compared to discover
     * eligible updates.
     * 
     * 1..0-0.beta.1 -> 1..0.x yes 1..0-0.beta.1 -> 1..0-0.rc.1 yes 1..0.0 -> 1..0.1 yes 1..0.0 ->
     * 1..1.x no
     * 
     * @return 
     */
    public Version updatable() {
        List<String> vp = new ArrayList<>(versionParts);
        vp.subList(3, vp.size()).clear();
        if (vp.size() < 3) {
            vp.add("0");
        }
        return new Version(vp, Collections.emptyList());
    }
    
    public Version onlyVersion() {
        return new Version(versionParts, Collections.emptyList());
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
        if (!Objects.equals(this.versionString, other.versionString)) {
            return false;
        }
        return true;
    }
    
    
    
    @Override
    public String toString() {
        return versionString;
    }
    
    private static List<String> parseParts(String s) throws IllegalArgumentException {
        return Arrays.asList(s.split("[^\\p{Alnum}]", -1)); // NOI18N
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
        int c = compareVersionParts(versionParts, o.versionParts);
        if (c != 0) {
            return c;
        }
        return compareVersionParts(releaseParts, o.releaseParts);
    }
    
    private static int compareVersionParts(List<String> pA, List<String> pB) {
        Iterator<String> iA = pA.iterator();
        Iterator<String> iB = pB.iterator();
        int res = 0;
        while ((res == 0) && iA.hasNext() && iB.hasNext()) {
            res = compareVersionPart(iA.next(), iB.next());
        }
        if (res != 0 || !(iA.hasNext() || iB.hasNext())) {
            return res;
        }
        if (iA.hasNext()) {
            return 1;
        } else if (iB.hasNext()) {
            return -1;
        } else {
            throw new IllegalStateException("Should not happen"); // NOI18N
        }
    }
    
    /**
     * Compares version parts, RPM-like rules. Acts like a {@link Comparator}.
     * 
     * @param a first version
     * @param b second version.
     * @return 
     */
    public static int compareVersionPart(String a, String b) {
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
    
    public Version installVersion() {
        StringBuilder sb = new StringBuilder();
        sb.append(versionParts.get(0)).append(".").append(versionParts.get(1));
        if (versionParts.size() > 2) {
            sb.append(".").append(versionParts.get(2));
        }
        if (!releaseParts.isEmpty()) {
            sb.append("-").append(String.join(".", releaseParts));
        }
        return fromString(sb.toString());
    }
    
    public Match match(Match.Type type) {
        return new Match(this, type);
    }
    
    /**
     * Parses a new Version object.
     * @param versionString the String specification of the version
     * @return 
     */
    public static Version fromString(String versionString) {
        return versionString == null ? NO_VERSION : new Version(versionString);
    }
    
    /**
     * Parses ID and optional version specification into ID and version selector.
     * @param idSpec ID specification
     * @param matchOut the version match
     * @return id
     */
    public static String idAndVersion(String idSpec, Version.Match[] matchOut) {
        int eqIndex = idSpec.indexOf('=');
        int moreIndex = idSpec.indexOf('~');
        int i = -1;
        Version.Match.Type type = null;
        
        if (eqIndex > 0) {
            type = Match.Type.EXACT;
            i = eqIndex;
        } else if (moreIndex > 0) {
            type = Match.Type.GREATER;
            i = moreIndex;
        } else {
            matchOut[0] = Version.NO_VERSION.match(Match.Type.MOSTRECENT);
            return idSpec;
        }
        matchOut[0] = Version.fromString(idSpec.substring(i + 1)).match(type);
        return idSpec.substring(0, i);
    }
    
    public final static class Match implements Predicate<Version> {
        public enum Type {
            EXACT, GREATER, MOSTRECENT
        }
        
        private final Type  matchType;
        private final Version version;

        public Match(Version version, Type matchType) {
            this.matchType = matchType;
            this.version = version;
        }

        @Override
        public boolean test(Version t) {
            if (t == null) {
                return matchType != Type.EXACT;
            }
            int d = version.compareTo(t);
            switch (matchType) {
                case EXACT:
                    return d == 0;
                    
                case GREATER:
                case MOSTRECENT:
                    return d <= 0;
            }
            return false;
        }
        
        public Type getType() {
            return matchType;
        }
    }
}
