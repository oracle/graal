/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.pgo;

import static jdk.graal.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.pgo.TypeProfileRecorder;
import com.oracle.svm.core.pgo.TypeProfileKey;
import com.oracle.svm.hosted.meta.HostedType;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;

/**
 * Records the exact type of an object in the thread's type profile table: the receiver of an
 * indirect call ({@link ReceiverProfileInstrumentationPhase}) or the locked object of a monitor
 * enter ({@link MonitorProfileInstrumentationPhase}). Escape analysis does not see the node as a
 * use of the object (see {@link #virtualize}), so instrumenting does not change what is
 * scalar-replaced through this node.
 */
@NodeInfo
public final class TypeProfileCounterNode extends PGOInstrumentationNode implements Virtualizable {
    public static final NodeClass<TypeProfileCounterNode> TYPE = NodeClass.create(TypeProfileCounterNode.class);

    /** {@link #knownTypeId} of a probe that reads the type from its object at run time. */
    private static final int DYNAMIC_TYPE = -1;

    /** The object whose type is recorded; null once the type is known at compile time. */
    @OptionalInput(InputType.Value) private ValueNode object;
    private final TypeProfileKey key;
    /** Whether this probe records a locked object rather than a receiver; shown in graph dumps. */
    private final boolean monitorProfile;
    /** Hub type id recorded without reading an object, or {@link #DYNAMIC_TYPE}. */
    private final int knownTypeId;

    /** Records the type of {@code receiver} at a receiver site. */
    public TypeProfileCounterNode(TypeProfileKey key, ValueNode receiver) {
        this(key, receiver, false);
    }

    /** Records the type of {@code object}; {@code monitorProfile} says whether it is a locked object, counted under the monitor key. */
    public TypeProfileCounterNode(TypeProfileKey key, ValueNode object, boolean monitorProfile) {
        this(key, object, monitorProfile, DYNAMIC_TYPE);
    }

    private TypeProfileCounterNode(TypeProfileKey key, ValueNode object, boolean monitorProfile, int knownTypeId) {
        super(TYPE);
        this.key = key;
        this.object = object;
        this.monitorProfile = monitorProfile;
        this.knownTypeId = knownTypeId;
    }

    /**
     * Once escape analysis has replaced the object by a virtual object, the object's exact type is
     * known, so the probe records that type as a constant and drops its input. Otherwise the probe
     * would be a use that forces the object to be materialized, and an instrumented build would
     * scalar-replace, and therefore inline, less than a build without counters.
     */
    @Override
    public void virtualize(VirtualizerTool tool) {
        if (object == null) {
            return;
        }
        ValueNode alias = tool.getAlias(object);
        if (alias instanceof VirtualObjectNode virtualObject && virtualObject.type() instanceof HostedType type && type.getHub() != null) {
            tool.addNode(new TypeProfileCounterNode(key, null, monitorProfile, type.getHub().getTypeID()));
            tool.delete();
        }
    }

    @Override
    protected void lowerToSnippet(LoweringTool tool) {
        tool.getReplacements().getSnippetTemplateCache(Templates.class).lower(this, tool);
    }

    private static final class CounterSnippet implements Snippets {
        /* The names of the snippets and of their parameters, by which Templates binds arguments. */
        static final String RECORD = "record";
        static final String RECORD_KNOWN_TYPE = "recordKnownType";
        static final String SITE_INDEX = "siteIndex";
        static final String OBJECT = "object";
        static final String TYPE_ID = "typeId";

        /*
         * The site index and the type id are not constant parameters, for the reason given at
         * BranchProfileCounterNode: they are bound as constants, and the canonicalizer folds them
         * into the key.
         */
        @Snippet
        private static void record(int siteIndex, Object object) {
            TypeProfileRecorder.record(siteIndex, object);
        }

        @Snippet
        private static void recordKnownType(int siteIndex, int typeId) {
            TypeProfileRecorder.recordKnownType(siteIndex, typeId);
        }
    }

    static final class Templates extends SubstrateTemplates {
        private final SnippetInfo record;
        private final SnippetInfo recordKnownType;

        Templates(OptionValues options, Providers providers) {
            super(options, providers);
            record = snippet(providers, CounterSnippet.class, CounterSnippet.RECORD, TypeProfileRecorder.tablePointerLocation(), TypeProfileRecorder.TABLE_LOCATION);
            recordKnownType = snippet(providers, CounterSnippet.class, CounterSnippet.RECORD_KNOWN_TYPE, TypeProfileRecorder.tablePointerLocation(), TypeProfileRecorder.TABLE_LOCATION);
        }

        void lower(TypeProfileCounterNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            Arguments args;
            if (node.object == null) {
                args = new Arguments(recordKnownType, graph, tool.getLoweringStage());
                /* Numbers the key now: only keys whose counters reach machine code get an index. */
                args.add(CounterSnippet.SITE_INDEX, node.key.siteIndexForCode());
                args.add(CounterSnippet.TYPE_ID, node.knownTypeId);
            } else {
                args = new Arguments(record, graph, tool.getLoweringStage());
                args.add(CounterSnippet.SITE_INDEX, node.key.siteIndexForCode());
                args.add(CounterSnippet.OBJECT, node.object);
            }
            template(tool, node, args).instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }
    }
}
