/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.graal.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import java.util.Arrays;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate.AbstractTemplates;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.word.ObjectAccess;
import jdk.internal.misc.Unsafe;

/**
 * This node can be used to add a counter to the code that will estimate the dynamic number of calls
 * by adding an increment to the compiled code. This should of course only be used for
 * debugging/testing purposes.
 *
 * A unique counter will be created for each unique name passed to the constructor. Depending on the
 * value of withContext, the name of the root method is added to the counter's name.
 */
@NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
public class SnippetCounterNode extends FixedWithNextNode implements Lowerable {

    public static final NodeClass<SnippetCounterNode> TYPE = NodeClass.create(SnippetCounterNode.class);

    @Input protected ValueNode increment;

    protected final SnippetCounter counter;

    public SnippetCounterNode(SnippetCounter counter, ValueNode increment) {
        super(TYPE, StampFactory.forVoid());
        this.counter = counter;
        this.increment = increment;
    }

    public SnippetCounter getCounter() {
        return counter;
    }

    public ValueNode getIncrement() {
        return increment;
    }

    @NodeIntrinsic
    public static native void add(@ConstantNodeParameter SnippetCounter counter, int increment);

    public static void increment(@ConstantNodeParameter SnippetCounter counter) {
        add(counter, 1);
    }

    @Override
    public void lower(LoweringTool tool) {
        if (graph().getGuardsStage().areFrameStatesAtDeopts()) {
            SnippetCounterSnippets.Templates templates = tool.getReplacements().getSnippetTemplateCache(SnippetCounterSnippets.Templates.class);
            templates.lower(this, tool);
        }
    }

    /**
     * Add {@link #SNIPPET_COUNTER_LOCATION} to {@code privateLocations} if it isn't already there.
     *
     * @param privateLocations
     * @return a copy of privateLocations with any needed locations added
     */
    public static LocationIdentity[] addSnippetCounters(LocationIdentity[] privateLocations) {
        for (LocationIdentity location : privateLocations) {
            if (location.equals(SNIPPET_COUNTER_LOCATION)) {
                return privateLocations;
            }
        }
        LocationIdentity[] result = Arrays.copyOf(privateLocations, privateLocations.length + 1);
        result[result.length - 1] = SnippetCounterNode.SNIPPET_COUNTER_LOCATION;
        return result;
    }

    /**
     * We do not want to use the {@link LocationIdentity} of the {@link SnippetCounter#value()}
     * field, so that the usage in snippets is always possible. If a method accesses the counter via
     * the field and the snippet, the result might not be correct though.
     */
    public static final LocationIdentity SNIPPET_COUNTER_LOCATION = NamedLocationIdentity.mutable("SnippetCounter");

    static class SnippetCounterSnippets implements Snippets {

        private static final Unsafe UNSAFE = Unsafe.getUnsafe();

        @Fold
        static int countOffset() {
            try {
                return (int) UNSAFE.objectFieldOffset(SnippetCounter.class.getDeclaredField("value"));
            } catch (Exception e) {
                throw new GraalError(e);
            }
        }

        @Snippet
        public static void add(@ConstantParameter SnippetCounter counter, int increment) {
            long loadedValue = ObjectAccess.readLong(counter, countOffset(), SNIPPET_COUNTER_LOCATION);
            ObjectAccess.writeLong(counter, countOffset(), loadedValue + increment, SNIPPET_COUNTER_LOCATION);
        }

        public static class Templates extends AbstractTemplates {

            private final SnippetInfo add;

            Templates(OptionValues options, Providers providers) {
                super(options, providers);

                this.add = snippet(providers, SnippetCounterSnippets.class, "add", SNIPPET_COUNTER_LOCATION);
            }

            public void lower(SnippetCounterNode counter, LoweringTool tool) {
                StructuredGraph graph = counter.graph();
                Arguments args = new Arguments(add, graph, tool.getLoweringStage());
                args.add("counter", counter.getCounter());
                args.add("increment", counter.getIncrement());

                template(tool, counter, args).instantiate(tool.getMetaAccess(), counter, DEFAULT_REPLACER, args);
            }
        }
    }
}
