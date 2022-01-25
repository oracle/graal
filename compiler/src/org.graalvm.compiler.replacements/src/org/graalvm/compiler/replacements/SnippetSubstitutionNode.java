/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * A node that lowers a non-side effecting snippet.
 */
@NodeInfo(nameTemplate = "SnippetSubstitution#{p#snippet/s}", cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
public class SnippetSubstitutionNode extends FixedWithNextNode implements Lowerable {
    public static final NodeClass<SnippetSubstitutionNode> TYPE = NodeClass.create(SnippetSubstitutionNode.class);

    @Input protected NodeInputList<ValueNode> arguments;

    protected Object[] constantArguments;

    protected final SnippetTemplate.SnippetInfo snippet;
    protected final SnippetTemplate.AbstractTemplates templates;
    protected final ResolvedJavaMethod targetMethod;

    public SnippetSubstitutionNode(SnippetTemplate.AbstractTemplates templates, SnippetTemplate.SnippetInfo snippet, ResolvedJavaMethod targetMethod,
                    Stamp stamp, ValueNode... arguments) {
        this(TYPE, templates, snippet, targetMethod, stamp, arguments);
    }

    protected SnippetSubstitutionNode(NodeClass<? extends SnippetSubstitutionNode> c, SnippetTemplate.AbstractTemplates templates, SnippetTemplate.SnippetInfo snippet, ResolvedJavaMethod targetMethod,
                    Stamp stamp, ValueNode... arguments) {
        super(c, stamp);
        this.arguments = new NodeInputList<>(this, arguments);
        this.snippet = snippet;
        this.templates = templates;
        this.targetMethod = targetMethod;
    }

    public ValueNode getArgument(int i) {
        return arguments.get(i);
    }

    public int getArgumentCount() {
        return arguments.size();
    }

    public void setConstantArguments(Object[] arguments) {
        this.constantArguments = arguments;
    }

    @Override
    public void lower(LoweringTool tool) {
        SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(snippet, this.graph().getGuardsStage(), tool.getLoweringStage());
        int arg = 0;
        for (; arg < arguments.size(); arg++) {
            args.add(snippet.getParameterName(arg), arguments.get(arg));
        }
        if (constantArguments != null) {
            for (Object argument : constantArguments) {
                args.addConst(snippet.getParameterName(arg), argument);
                arg++;
            }
        }
        SnippetTemplate template = templates.template(this, args);
        UnmodifiableEconomicMap<Node, Node> duplicates = template.instantiate(tool.getMetaAccess(), this, SnippetTemplate.DEFAULT_REPLACER, args, false);
        fixupNodes(tool, template, duplicates);
        GraphUtil.killCFG(this);
    }

    /**
     * Perform any fixup required after instantiating the snippet.
     *
     * @param tool
     * @param template the template for the snippet
     * @param duplicates the map returned from
     *            {@link SnippetTemplate.AbstractTemplates#instantiate(MetaAccessProvider, FixedNode, SnippetTemplate.UsageReplacer, SnippetTemplate.Arguments, boolean)}
     */
    protected void fixupNodes(LoweringTool tool, SnippetTemplate template, UnmodifiableEconomicMap<Node, Node> duplicates) {
    }

}
