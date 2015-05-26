/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements;

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.replacements.SnippetTemplate.*;
import static com.oracle.jvmci.common.UnsafeAccess.*;

import java.util.*;

import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.word.*;
import com.oracle.jvmci.code.*;
import com.oracle.jvmci.common.*;
import com.oracle.jvmci.meta.*;

/**
 * This node can be used to add a counter to the code that will estimate the dynamic number of calls
 * by adding an increment to the compiled code. This should of course only be used for
 * debugging/testing purposes.
 *
 * A unique counter will be created for each unique name passed to the constructor. Depending on the
 * value of withContext, the name of the root method is added to the counter's name.
 */
@NodeInfo
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

    public void lower(LoweringTool tool) {
        if (graph().getGuardsStage().areFrameStatesAtDeopts()) {
            SnippetCounterSnippets.Templates templates = tool.getReplacements().getSnippetTemplateCache(SnippetCounterSnippets.Templates.class);
            templates.lower(this, tool);
        }
    }

    /**
     * When {@link #SnippetCounters} are enabled make sure {@link #SNIPPET_COUNTER_LOCATION} is part
     * of the private locations.
     *
     * @param privateLocations
     * @return a copy of privateLocations with any needed locations added
     */
    public static LocationIdentity[] addSnippetCounters(LocationIdentity[] privateLocations) {
        if (SnippetCounters.getValue()) {
            for (LocationIdentity location : privateLocations) {
                if (location.equals(SNIPPET_COUNTER_LOCATION)) {
                    return privateLocations;
                }
            }
            LocationIdentity[] result = Arrays.copyOf(privateLocations, privateLocations.length + 1);
            result[result.length - 1] = SnippetCounterNode.SNIPPET_COUNTER_LOCATION;
            return result;
        }
        return privateLocations;
    }

    /**
     * We do not want to use the {@link LocationIdentity} of the {@link SnippetCounter#value} field,
     * so that the usage in snippets is always possible. If a method accesses the counter via the
     * field and the snippet, the result might not be correct though.
     */
    public static final LocationIdentity SNIPPET_COUNTER_LOCATION = NamedLocationIdentity.mutable("SnippetCounter");

    static class SnippetCounterSnippets implements Snippets {

        @Fold
        private static int countOffset() {
            try {
                return (int) unsafe.objectFieldOffset(SnippetCounter.class.getDeclaredField("value"));
            } catch (Exception e) {
                throw new JVMCIError(e);
            }
        }

        @Snippet
        public static void add(@ConstantParameter SnippetCounter counter, int increment) {
            long loadedValue = ObjectAccess.readLong(counter, countOffset(), SNIPPET_COUNTER_LOCATION);
            ObjectAccess.writeLong(counter, countOffset(), loadedValue + increment, SNIPPET_COUNTER_LOCATION);
        }

        public static class Templates extends AbstractTemplates {

            private final SnippetInfo add = snippet(SnippetCounterSnippets.class, "add", SNIPPET_COUNTER_LOCATION);

            public Templates(Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
                super(providers, snippetReflection, target);
            }

            public void lower(SnippetCounterNode counter, LoweringTool tool) {
                StructuredGraph graph = counter.graph();
                Arguments args = new Arguments(add, graph.getGuardsStage(), tool.getLoweringStage());
                args.addConst("counter", counter.getCounter());
                args.add("increment", counter.getIncrement());

                template(args).instantiate(providers.getMetaAccess(), counter, DEFAULT_REPLACER, args);
            }
        }
    }

}
