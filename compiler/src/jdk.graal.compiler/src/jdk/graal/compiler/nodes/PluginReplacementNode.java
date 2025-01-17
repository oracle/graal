/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import java.util.Map;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.GeneratedPluginInjectionProvider;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.spi.Replacements;

/**
 * This node represents a {@link jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderPlugin
 * plugin} which was deferred by
 * {@link jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderTool#shouldDeferPlugin(GeneratedInvocationPlugin)}
 * during graph encoding that must be replaced when the graph is decoded. This primarily exists to
 * deal with graphs that have been encoded by {@link GraphEncoder} for cases where a
 * {@link jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderPlugin} couldn't be applied at parse
 * time. Usually {@link GraphDecoder} handles this by reapplying the plugins during decoding of the
 * original {@link Invoke}. In the context of libgraal snippets that would create a lot of
 * complexity because snippet methods aren't fully functional ResolvedJavaMethods. Using a
 * placeholder instead avoids supporting the full GraphBuilder machinery in this admittedly weird
 * case.
 */
@NodeInfo(nameTemplate = "PluginReplacement/{p#pluginName}", cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED)
public final class PluginReplacementNode extends FixedWithNextNode implements PluginReplacementInterface {
    public static final NodeClass<PluginReplacementNode> TYPE = NodeClass.create(PluginReplacementNode.class);

    @Input protected NodeInputList<ValueNode> args;
    private final ReplacementFunction function;
    private final String pluginName;

    public PluginReplacementNode(Stamp stamp, ValueNode[] args, ReplacementFunction function, String pluginName) {
        super(TYPE, stamp);
        this.args = new NodeInputList<>(this, args);
        this.function = function;
        this.pluginName = pluginName;
    }

    @Override
    public boolean replace(GraphBuilderContext b, Replacements injection) {
        return function.replace(b, injection, args.toArray(ValueNode.EMPTY_ARRAY));
    }

    /**
     * This is the work of the original
     * {@link jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderPlugin} decoupled from the
     * plugin.
     */
    public interface ReplacementFunction {
        boolean replace(GraphBuilderContext b, GeneratedPluginInjectionProvider injection, ValueNode[] args);
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
