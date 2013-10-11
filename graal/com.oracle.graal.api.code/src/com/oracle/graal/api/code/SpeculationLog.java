/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.api.code;

import java.util.*;

import com.oracle.graal.api.meta.*;

/**
 * Manages a list of unique deoptimization reasons and returns a unique index for each reason. This
 * class is not thread safe and assumes that at every point in time there is only a single Graal
 * compilation accessing this object.
 * 
 */
public final class SpeculationLog {

    public static final int MAX_CACHE_SIZE = 1 << 15;

    private List<Object> speculations = new ArrayList<>();
    private boolean[] map = new boolean[10];
    private Set<Object> snapshot = new HashSet<>();

    private short addSpeculation(Object reason) {
        short index = (short) speculations.indexOf(reason);
        if (index != -1) {
            // Nothing to add, reason already registered.
            return index;
        }
        if (speculations.size() >= MAX_CACHE_SIZE) {
            throw new BailoutException("Too many deoptimization reasons recorded");
        }
        speculations.add(reason);
        if (map.length < speculations.size()) {
            map = Arrays.copyOf(map, map.length * 2);
        }
        return (short) (speculations.size() - 1);
    }

    public boolean[] getRawMap() {
        return map;
    }

    public void snapshot() {
        for (int i = 0; i < speculations.size(); ++i) {
            if (map[i]) {
                snapshot.add(speculations.get(i));
            }
        }
    }

    public Constant maySpeculate(Object reason) {
        if (snapshot.contains(reason)) {
            return null;
        }
        return Constant.forShort(addSpeculation(reason));
    }
}
