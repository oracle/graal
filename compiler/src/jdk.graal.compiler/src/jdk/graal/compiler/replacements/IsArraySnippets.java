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
package jdk.graal.compiler.replacements;

import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.unknownProbability;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ClassIsArrayNode;
import jdk.graal.compiler.nodes.extended.ObjectIsArrayNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;

public abstract class IsArraySnippets implements Snippets {

    @Snippet
    Object objectIsArraySnippet(@Snippet.NonNullParameter Object object, Object trueValue, Object falseValue) {
        return unknownProbability(classIsArray(object.getClass())) ? trueValue : falseValue;
    }

    @Snippet
    Object classIsArraySnippet(@Snippet.NonNullParameter Class<?> clazz, Object trueValue, Object falseValue) {
        return unknownProbability(classIsArray(clazz)) ? trueValue : falseValue;
    }

    protected abstract boolean classIsArray(Class<?> clazz);

    public static class Templates extends InstanceOfSnippetsTemplates {
        private final SnippetTemplate.SnippetInfo objectIsArraySnippet;
        private final SnippetTemplate.SnippetInfo classIsArraySnippet;

        @SuppressWarnings("this-escape")
        public Templates(IsArraySnippets receiver, OptionValues options, Providers providers) {
            super(options, providers);
            objectIsArraySnippet = snippet(providers, IsArraySnippets.class, "objectIsArraySnippet", null, receiver);
            classIsArraySnippet = snippet(providers, IsArraySnippets.class, "classIsArraySnippet", null, receiver);
        }

        @Override
        protected SnippetTemplate.Arguments makeArguments(InstanceOfUsageReplacer replacer, LoweringTool tool) {
            ValueNode node = replacer.instanceOf;
            SnippetTemplate.Arguments args;
            if (node instanceof ObjectIsArrayNode) {
                args = new SnippetTemplate.Arguments(objectIsArraySnippet, node.graph().getGuardsStage(), tool.getLoweringStage());
                args.add("object", ((ObjectIsArrayNode) node).getValue());
            } else if (replacer.instanceOf instanceof ClassIsArrayNode) {
                args = new SnippetTemplate.Arguments(classIsArraySnippet, node.graph().getGuardsStage(), tool.getLoweringStage());
                args.add("clazz", ((ClassIsArrayNode) node).getValue());
            } else {
                throw GraalError.shouldNotReachHere(node + " " + replacer); // ExcludeFromJacocoGeneratedReport
            }

            args.add("trueValue", replacer.trueValue);
            args.add("falseValue", replacer.falseValue);
            return args;
        }
    }
}
