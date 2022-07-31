/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime;

import java.util.List;

import com.oracle.truffle.espresso.impl.EspressoClassLoadingException;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;

public class PolyglotInterfaceMappings {

    private final boolean hasMappings;
    private final List<String> mappings;
    private UnmodifiableEconomicMap<String, ObjectKlass> resolvedKlasses;

    PolyglotInterfaceMappings(List<String> mappings) {
        this.mappings = mappings;
        this.hasMappings = !mappings.isEmpty();
    }

    @TruffleBoundary
    void resolve(EspressoContext context) {
        assert mappings != null;
        if (!hasMappings) {
            return;
        }
        EconomicMap<String, ObjectKlass> temp = EconomicMap.create(mappings.size());
        StaticObject bindingsLoader = context.getBindings().getBindingsLoader();

        try {
            for (String mapping : mappings) {
                Klass parent = context.getRegistries().loadKlass(context.getTypes().fromClassGetName(mapping), bindingsLoader, StaticObject.NULL);
                if (parent.isInterface()) {
                    temp.put(mapping, (ObjectKlass) parent);
                } else {
                    throw new IllegalStateException("invalid interface type mapping specified: " + mapping);
                }
            }
        } catch (EspressoClassLoadingException e) {
            throw e.asGuestException(context.getMeta());
        }
        resolvedKlasses = EconomicMap.create(temp);
    }

    @TruffleBoundary
    public ObjectKlass mapName(String name) {
        assert resolvedKlasses != null;
        return resolvedKlasses.get(name);
    }

    public boolean hasMappings() {
        return hasMappings;
    }
}
