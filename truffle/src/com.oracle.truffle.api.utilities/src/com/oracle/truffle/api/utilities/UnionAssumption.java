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
 * An assumption that combines two other assumptions. A check on this assumption checks both of the
 * child assumptions.
 *
 * @since 0.8 or earlier
 */
public class UnionAssumption implements Assumption {

    private final String name;
    private final Assumption first;
    private final Assumption second;

    /** @since 0.8 or earlier */
    public UnionAssumption(String name, Assumption first, Assumption second) {
        this.name = name;
        this.first = first;
        this.second = second;
    }

    /** @since 0.8 or earlier */
    public UnionAssumption(Assumption first, Assumption second) {
        this(null, first, second);
    }

    /** @since 0.8 or earlier */
    @Override
    public void check() throws InvalidAssumptionException {
        first.check();
        second.check();
    }

    /** @since 0.8 or earlier */
    @Override
    public void invalidate() {
        first.invalidate();
        second.invalidate();
    }

    /** @since 0.33 */
    @Override
    public void invalidate(String message) {
        first.invalidate(message);
        second.invalidate(message);
    }

    /** @since 0.8 or earlier */
    @Override
    public String getName() {
        return name;
    }

    /** @since 0.8 or earlier */
    @Override
    public boolean isValid() {
        return first.isValid() && second.isValid();
    }

}
