/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.utilities;

import com.oracle.truffle.api.nodes.NodeCloneable;

/**
 * @deprecated use {@link com.oracle.truffle.api.profiles.ValueProfile} instead
 * @since 0.8 or earlier
 */
@Deprecated
@SuppressWarnings("deprecation")
public abstract class ValueProfile extends NodeCloneable {
    /**
     * @since 0.8 or earlier
     * @deprecated don't use
     */
    @Deprecated
    public ValueProfile() {
    }

    /** @since 0.8 or earlier */
    public abstract <T> T profile(T value);

    /**
     * @deprecated use
     *             {@link com.oracle.truffle.api.profiles.PrimitiveValueProfile#createEqualityProfile()}
     *             instead
     * @since 0.8 or earlier
     */
    @Deprecated
    public static PrimitiveValueProfile createPrimitiveProfile() {
        return new PrimitiveValueProfile();
    }

    /**
     * @deprecated use {@link com.oracle.truffle.api.profiles.ValueProfile#createClassProfile()}
     *             instead
     * @since 0.8 or earlier
     */
    @Deprecated
    public static ValueProfile createClassProfile() {
        return new ExactClassValueProfile();
    }

    /**
     * @deprecated use {@link com.oracle.truffle.api.profiles.ValueProfile#createIdentityProfile()}
     *             instead
     * @since 0.8 or earlier
     */
    @Deprecated
    public static ValueProfile createIdentityProfile() {
        return new IdentityValueProfile();
    }

}
