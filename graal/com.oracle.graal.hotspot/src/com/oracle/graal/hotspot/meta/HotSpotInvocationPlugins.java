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
package com.oracle.graal.hotspot.meta;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderPlugin.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.replacements.StandardGraphBuilderPlugins.BoxPlugin;

/**
 * Extension of {@link InvocationPlugins} that disables plugins based on runtime configuration.
 */
final class HotSpotInvocationPlugins extends InvocationPlugins {
    final HotSpotVMConfig config;

    public HotSpotInvocationPlugins(HotSpotVMConfig config, MetaAccessProvider metaAccess, int estimatePluginCount) {
        super(metaAccess, estimatePluginCount);
        this.config = config;
    }

    @Override
    public void register(InvocationPlugin plugin, Class<?> declaringClass, String name, Class<?>... argumentTypes) {
        if (!config.usePopCountInstruction) {
            if (name.equals("bitCount")) {
                assert declaringClass.equals(Integer.class) || declaringClass.equals(Long.class);
                return;
            }
        }
        if (!config.useCountLeadingZerosInstruction) {
            if (name.equals("numberOfLeadingZeros")) {
                assert declaringClass.equals(Integer.class) || declaringClass.equals(Long.class);
                return;
            }
        }
        if (!config.useCountTrailingZerosInstruction) {
            if (name.equals("numberOfTrailingZeros")) {
                assert declaringClass.equals(Integer.class);
                return;
            }
        }

        if (config.useHeapProfiler) {
            if (plugin instanceof BoxPlugin) {
                // The heap profiler wants to see all allocations related to boxing
                return;
            }
        }
        super.register(plugin, declaringClass, name, argumentTypes);
    }
}
