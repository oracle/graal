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
 * Default implementation of {@link GraphBuilderPlugins} that uses a map.
 */
public class DefaultGraphBuilderPlugins implements GraphBuilderPlugins {

    private final Map<ResolvedJavaMethod, InvocationPlugin> plugins = new HashMap<>();

    /**
     * Registers an invocation plugin for a given method. There must be no plugin currently
     * registered for {@code method}.
     */
    public void register(ResolvedJavaMethod method, InvocationPlugin plugin) {
        assert InvocationPluginChecker.check(method, plugin);
        GraphBuilderPlugin oldValue = plugins.put(method, plugin);
        // System.out.println("registered: " + plugin);
        assert oldValue == null;
    }

    /**
     * Gets the plugin for a given method.
     *
     * @param method the method to lookup
     * @return the plugin associated with {@code method} or {@code null} if none exists
     */
    public InvocationPlugin lookupInvocation(ResolvedJavaMethod method) {
        return plugins.get(method);
    }

    public DefaultGraphBuilderPlugins copy() {
        DefaultGraphBuilderPlugins result = new DefaultGraphBuilderPlugins();
        result.plugins.putAll(plugins);
        return result;
    }

    @Override
    public String toString() {
        return plugins.keySet().stream().map(m -> m.format("%H.%n(%p)")).collect(Collectors.joining(", "));
    }
}
