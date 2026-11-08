/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.analysis.tesa.effect;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.graalvm.word.LocationIdentity;

/**
 * Set-based effect over {@link LocationIdentity} instances. Differentiates between empty location
 * (noEffect), sets of locations with cardinality up to a pre-defined threshold
 * {@link #MAX_SET_SIZE}, and any location (anyEffect).
 */
public sealed class LocationSetEffect implements TesaEffect<LocationSetEffect> permits LocationSetEffect.Any, LocationSetEffect.Empty, LocationSetEffect.LocationSet {
    public static final int MAX_SET_SIZE = 16;

    public static final class Any extends LocationSetEffect {
        private static final Any INSTANCE = new Any();

        private Any() {
        }

        @Override
        public String toString() {
            return "AnyLocation";
        }
    }

    public static LocationSetEffect anyEffect() {
        return LocationSetEffect.Any.INSTANCE;
    }

    public static final class Empty extends LocationSetEffect {
        private static final LocationSetEffect.Empty INSTANCE = new Empty();

        private Empty() {
        }

        @Override
        public String toString() {
            return "EmptyLocation";
        }
    }

    public static LocationSetEffect noEffect() {
        return LocationSetEffect.Empty.INSTANCE;
    }

    public static final class LocationSet extends LocationSetEffect {
        public final Set<LocationIdentity> identitySet;

        private LocationSet(Set<LocationIdentity> identitySet) {
            this.identitySet = identitySet;
        }

        @Override
        public String toString() {
            return "LocationSet" + identitySet;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            LocationSet that = (LocationSet) o;
            return Objects.equals(identitySet, that.identitySet);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(identitySet);
        }
    }

    public static LocationSetEffect of(Set<LocationIdentity> identitySet) {
        assert identitySet.size() <= MAX_SET_SIZE : "Only sets smaller than " + MAX_SET_SIZE + " should be represented explicitly.";
        assert !identitySet.isEmpty() : "Empty sets should be represented by a special singleton.";
        assert identitySet.stream().noneMatch(LocationIdentity::isAny) : "Identity set contains elements that should lead immediately to the Any singleton: " + identitySet;
        return new LocationSetEffect.LocationSet(identitySet);
    }

    @Override
    public boolean isAnyEffect() {
        return this == LocationSetEffect.Any.INSTANCE;
    }

    @Override
    public boolean hasNoEffects() {
        return this == LocationSetEffect.Empty.INSTANCE;
    }

    @Override
    public LocationSetEffect combineEffects(LocationSetEffect other) {
        if (this.isAnyEffect() || other.isAnyEffect()) {
            return anyEffect();
        }
        if (this.hasNoEffects()) {
            return other;
        }
        if (other.hasNoEffects()) {
            return this;
        }
        var res = new HashSet<>(((LocationSetEffect.LocationSet) this).identitySet);
        res.addAll(((LocationSetEffect.LocationSet) other).identitySet);
        if (res.size() > MAX_SET_SIZE) {
            return anyEffect();
        }
        return new LocationSetEffect.LocationSet(res);
    }
}
