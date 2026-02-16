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

import java.util.Objects;

import org.graalvm.word.LocationIdentity;

/**
 * Effect over {@link LocationIdentity} instances. Differentiates between empty location (noEffect),
 * single location, and any location (anyEffect).
 *
 * @formatter:off
 *               any-location
 *               /     |    \
 *         location-0 ... location-n
 *              \      |     /
 *              empty-location
 * @formatter:on
 */
public sealed class LocationEffect
                implements TesaEffect<LocationEffect> permits LocationEffect.Any, LocationEffect.Empty, LocationEffect.Single {

    @Override
    public boolean isAnyEffect() {
        return this == Any.INSTANCE;
    }

    @Override
    public boolean hasNoEffects() {
        return this == Empty.INSTANCE;
    }

    public static Any anyEffect() {
        return Any.INSTANCE;
    }

    public static Single singleLocation(LocationIdentity location) {
        return new Single(location);
    }

    public static Empty noEffect() {
        return Empty.INSTANCE;
    }

    @Override
    public LocationEffect combineEffects(LocationEffect other) {
        if (this.isAnyEffect() || other.isAnyEffect()) {
            return anyEffect();
        }
        if (other.hasNoEffects()) {
            return this;
        }
        if (this.hasNoEffects()) {
            return other;
        }
        var thisLoc = ((Single) this).location;
        var otherLoc = ((Single) other).location;
        if (thisLoc.equals(otherLoc)) {
            return this;
        }
        return anyEffect();
    }

    public static final class Any extends LocationEffect {
        private static final Any INSTANCE = new Any();

        private Any() {
        }

        @Override
        public String toString() {
            return "AnyLocation";
        }
    }

    public static final class Empty extends LocationEffect {
        private static final Empty INSTANCE = new Empty();

        private Empty() {
        }

        @Override
        public String toString() {
            return "EmptyLocation";
        }
    }

    public static final class Single extends LocationEffect {
        public final LocationIdentity location;

        private Single(LocationIdentity location) {
            this.location = location;
        }

        @Override
        public String toString() {
            return "SingleLocation(" + location + ")";
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Single that)) {
                return false;
            }
            return Objects.equals(location, that.location);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(location);
        }
    }

}
