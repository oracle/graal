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
package jdk.graal.compiler.replacements;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import jdk.graal.compiler.replacements.nodes.MacroNode;
import org.graalvm.collections.UnmodifiableEconomicMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.replacements.nodes.FallbackInvokeWithExceptionNode;
import jdk.graal.compiler.replacements.nodes.MacroWithExceptionNode;

/**
 * A node that lowers a non-side effecting snippet.
 */
@NodeInfo(nameTemplate = "SnippetSubstitution#{p#snippet/s}", cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
public class SnippetSubstitutionNode extends MacroWithExceptionNode implements Lowerable {
    public static final NodeClass<SnippetSubstitutionNode> TYPE = NodeClass.create(SnippetSubstitutionNode.class);

    protected Object[] constantArguments;

    protected final SnippetTemplate.SnippetInfo snippet;
    protected final SnippetTemplate.AbstractTemplates templates;

    public <T extends SnippetTemplate.AbstractTemplates> SnippetSubstitutionNode(T templates, SnippetTemplate.SnippetInfo snippet, MacroNode.MacroParams params) {
        super(TYPE, params);
        this.snippet = snippet;
        this.templates = templates;
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
        SnippetTemplate template = templates.template(tool, this, args);
        UnmodifiableEconomicMap<Node, Node> duplicates = template.instantiate(tool.getMetaAccess(), this, SnippetTemplate.DEFAULT_REPLACER, args, true);
        for (Node original : duplicates.getKeys()) {
            if (original instanceof FallbackInvokeWithExceptionNode) {
                Node replacement = duplicates.get(original);
                if (replacement instanceof Lowerable) {
                    tool.getLowerer().lower(replacement, tool);
                }
            }
        }
    }
}
