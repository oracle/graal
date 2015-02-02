/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java;

import java.util.*;
import java.util.stream.*;

import com.oracle.graal.api.meta.*;

/**
 * A repository of {@link GraphBuilderPlugin}s.
 */
public class GraphBuilderPlugins {

    private final Map<ResolvedJavaMethod, GraphBuilderPlugin> map = new HashMap<>();

    /**
     * Registers all the constants of an enum that implements {@link GraphBuilderPlugin}.
     */
    public <T extends Enum<T> & GraphBuilderPlugin> void register(MetaAccessProvider metaAccess, Class<T> enumClass) {
        assert Enum.class.isAssignableFrom(enumClass);
        Object[] enumConstants = enumClass.getEnumConstants();
        for (Object o : enumConstants) {
            GraphBuilderPlugin gbp = (GraphBuilderPlugin) o;
            ResolvedJavaMethod target = gbp.getInvocationTarget(metaAccess);
            GraphBuilderPlugin oldValue = map.put(target, gbp);
            // System.out.println("registered: " + gbp);
            assert oldValue == null;
        }
    }

    /**
     * Gets the plugin for a given method registered in the object.
     *
     * @param method the method to lookup
     * @return the plugin associated with {@code method} or {@code null} if none exists
     */
    public GraphBuilderPlugin getPlugin(ResolvedJavaMethod method) {
        return map.get(method);
    }

    @Override
    public String toString() {
        return map.keySet().stream().map(m -> m.format("%H.%n(%p)")).collect(Collectors.joining(", "));
    }
}
