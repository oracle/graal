/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.shared.lookup;

import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.shared.meta.ModifiersProvider;

/**
 * Provides a simple way of filtering elements with {@linkplain ModifiersProvider modifiers} (such
 * as classes, methods or fields), based on their declared flags.
 */
public final class LookupMode {
    public static final LookupMode ALL = new LookupMode(0, 0);
    public static final LookupMode INSTANCE_ONLY = new LookupMode(0, Constants.ACC_STATIC);
    public static final LookupMode STATIC_ONLY = new LookupMode(Constants.ACC_STATIC, 0);
    public static final LookupMode NON_STATIC_NON_PRIVATE = new LookupMode(0, Constants.ACC_STATIC | Constants.ACC_PRIVATE);
    public static final LookupMode PUBLIC_NON_STATIC = new LookupMode(Constants.ACC_PUBLIC, Constants.ACC_STATIC);

    private final int positives;
    private final int negatives;

    /**
     * Creates a new element filter.
     * 
     * @param positives The flags that need to be set
     * @param negatives The flags that must not be set.
     */
    public LookupMode(int positives, int negatives) {
        assert (positives & negatives) == 0 : "Empty mode: +" + Integer.toHexString(positives) + " and -" + Integer.toHexString(negatives);
        this.positives = positives;
        this.negatives = negatives;
    }

    /**
     * Returns whether the given element is accepted by {@code this} filter. If {@code m} is
     * {@code null}, then this method returns {@code false}.
     */
    public boolean include(ModifiersProvider m) {
        if (m == null) {
            return false;
        }
        int flags = m.getModifiers();
        return hasPositives(flags) && hasNoNegatives(flags);
    }

    /**
     * Combines this filter with another.
     * <p>
     * Calling the resulting {@link #include(ModifiersProvider e)} is equivalent to
     * {@code this.include(e) && other.include(e)}.
     */
    public LookupMode combine(LookupMode other) {
        int newPositives = this.positives | other.negatives;
        int newNegatives = this.negatives | other.negatives;
        if (newPositives == positives && newNegatives == negatives) {
            return this;
        }
        if (newPositives == other.positives && newNegatives == other.negatives) {
            return other;
        }
        return new LookupMode(newPositives, newNegatives);
    }

    private boolean hasPositives(int flags) {
        return (flags & positives) == positives;
    }

    private boolean hasNoNegatives(int flags) {
        return (flags & negatives) == 0;
    }
}
