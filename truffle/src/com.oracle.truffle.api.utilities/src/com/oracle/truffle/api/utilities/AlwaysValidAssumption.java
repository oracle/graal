/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

/**
 * An assumption that is always valid. Used as a placeholder where an assumption is needed but never
 * invalidated.
 *
 * @since 0.8 or earlier
 */
public final class AlwaysValidAssumption implements Assumption {
    /** @since 0.8 or earlier */
    public static final AlwaysValidAssumption INSTANCE = new AlwaysValidAssumption();

    private AlwaysValidAssumption() {
    }

    /** @since 0.8 or earlier */
    @Override
    public void check() throws InvalidAssumptionException {
    }

    /** @since 0.8 or earlier */
    @Override
    public void invalidate() {
        throw new UnsupportedOperationException("Cannot invalidate this assumption - it is always valid");
    }

    /** @since 0.33 */
    @Override
    public void invalidate(String message) {
        invalidate();
    }

    /** @since 0.8 or earlier */
    @Override
    public String getName() {
        return getClass().getName();
    }

    /** @since 0.8 or earlier */
    @Override
    public boolean isValid() {
        return true;
    }

}
