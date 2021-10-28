/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import java.util.Map;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.graphbuilderconf.GeneratedPluginInjectionProvider;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;

@NodeInfo(nameTemplate = "PluginReplacementWithException/{p#pluginName}", cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED)
public final class PluginReplacementWithExceptionNode extends WithExceptionNode implements PluginReplacementInterface {
    public static final NodeClass<PluginReplacementWithExceptionNode> TYPE = NodeClass.create(PluginReplacementWithExceptionNode.class);

    @Input protected NodeInputList<ValueNode> args;
    private final ReplacementWithExceptionFunction function;
    private final String pluginName;

    public PluginReplacementWithExceptionNode(Stamp stamp, ValueNode[] args, ReplacementWithExceptionFunction function, String pluginName) {
        super(TYPE, stamp);
        this.args = new NodeInputList<>(this, args);
        this.function = function;
        this.pluginName = pluginName;
    }

    @Override
    public boolean replace(GraphBuilderContext b, GeneratedPluginInjectionProvider injection) {
        return function.replace(b, injection, stamp, args);
    }

    public interface ReplacementWithExceptionFunction {
        boolean replace(GraphBuilderContext b, GeneratedPluginInjectionProvider injection, Stamp stamp, NodeInputList<ValueNode> args);
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Short) {
            return super.toString(verbosity) + "/" + pluginName;
        }
        return super.toString(verbosity);
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        map.put("name", pluginName);
        return super.getDebugProperties(map);
    }
}
