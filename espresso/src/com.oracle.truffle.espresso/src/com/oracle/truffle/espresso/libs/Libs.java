/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libs;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * A collection of known boot libraries.
 * 
 * @implNote Even though the underlying mechanism for registering native method implementations
 *           re-uses the {@link com.oracle.truffle.espresso.substitutions.Substitution} annotation,
 *           it differs from regulars substitution in that these methods will not eagerly replace
 *           non-native methods.
 * 
 * @see Lib
 */
public final class Libs {

    private final EconomicMap<String, Lib.Factory> knownLibs = EconomicMap.create();

    public Libs() {
        for (Lib.Factory lib : LibsCollector.getInstances(Lib.Factory.class)) {
            knownLibs.put(lib.name(), lib);
        }
    }

    public Lib loadLibrary(EspressoContext ctx, String name) {
        Lib.Factory factory = knownLibs.get(name);
        if (factory != null) {
            return factory.create(ctx);
        }
        return null;
    }

    public boolean isKnown(String name) {
        return knownLibs.containsKey(name);
    }
}
