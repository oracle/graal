/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.snippets;

import static com.oracle.graal.hotspot.nodes.CastFromHub.*;
import static com.oracle.graal.hotspot.nodes.RegisterNode.*;
import static com.oracle.graal.hotspot.snippets.DirectObjectStoreNode.*;
import static com.oracle.graal.nodes.extended.UnsafeLoadNode.*;
import static com.oracle.graal.snippets.SnippetTemplate.Arguments.*;
import static com.oracle.graal.snippets.nodes.ExplodeLoopNode.*;
import static com.oracle.max.asm.target.amd64.AMD64.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.cri.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.snippets.Snippet.ConstantParameter;
import com.oracle.graal.snippets.Snippet.Fold;
import com.oracle.graal.snippets.Snippet.Parameter;
import com.oracle.graal.snippets.SnippetTemplate.Arguments;
import com.oracle.graal.snippets.SnippetTemplate.Cache;
import com.oracle.graal.snippets.SnippetTemplate.Key;

/**
 * Snippets used for implementing NEW.
 */
public class NewInstanceSnippets implements SnippetsInterface {

    @Snippet
    public static Word allocate(@ConstantParameter("size") int size) {
        Word thread = asWord(register(r15, wordKind()));
        Word top = loadWord(thread, threadTlabTopOffset());
        Word end = loadWord(thread, threadTlabEndOffset());
        Word newTop = top.plus(size);
        if (newTop.belowOrEqual(end)) {
            store(thread, 0, threadTlabTopOffset(), newTop);
            return top;
        }
        return Word.zero();
    }

    @Snippet
    public static Object initialize(
                    @Parameter("memory") Word memory,
                    @Parameter("hub") Object hub,
                    @Parameter("prototypeHeader") Word headerPrototype,
                    @ConstantParameter("size") int size) {

        if (memory == Word.zero()) {
            return NewInstanceStubCall.call(hub);
        }
        formatObject(hub, size, memory, headerPrototype);
        Object instance = memory.toObject();
        return castFromHub(verifyOop(instance), hub);
    }

    private static Object verifyOop(Object object) {
        if (verifyOops()) {
            VerifyOopStubCall.call(object);
        }
        return object;
    }

    private static Word asWord(Object object) {
        return Word.fromObject(object);
    }

    private static Word loadWord(Word address, int offset) {
        Object value = loadObject(address, 0, offset, true);
        return asWord(value);
    }

    /**
     * Formats some allocated memory with an object header zeroes out the rest.
     */
    private static void formatObject(Object hub, int size, Word memory, Word headerPrototype) {
        store(memory, 0, 0, headerPrototype);
        store(memory, 0, hubOffset(), hub);
        explodeLoop();
        for (int offset = 2 * wordSize(); offset < size; offset += wordSize()) {
            store(memory, 0, offset, 0);
        }
    }

    @Fold
    private static boolean verifyOops() {
        return HotSpotGraalRuntime.getInstance().getConfig().verifyOops;
    }

    @Fold
    private static int threadTlabTopOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().threadTlabTopOffset;
    }

    @Fold
    private static int threadTlabEndOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().threadTlabEndOffset;
    }

    @Fold
    private static Kind wordKind() {
        return HotSpotGraalRuntime.getInstance().getTarget().wordKind;
    }

    @Fold
    private static int wordSize() {
        return HotSpotGraalRuntime.getInstance().getTarget().wordSize;
    }

    @Fold
    private static int hubOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().hubOffset;
    }

    public static class Templates {

        private final Cache cache;
        private final ResolvedJavaMethod allocate;
        private final ResolvedJavaMethod initialize;
        private final CodeCacheProvider runtime;
        private final boolean useTLAB;

        public Templates(CodeCacheProvider runtime, boolean useTLAB) {
            this.runtime = runtime;
            this.cache = new Cache(runtime);
            this.useTLAB = useTLAB;
            try {
                allocate = runtime.getResolvedJavaMethod(NewInstanceSnippets.class.getDeclaredMethod("allocate", int.class));
                initialize = runtime.getResolvedJavaMethod(NewInstanceSnippets.class.getDeclaredMethod("initialize", Word.class, Object.class, Word.class, int.class));
            } catch (NoSuchMethodException e) {
                throw new GraalInternalError(e);
            }
        }

        /**
         * Lowers a {@link NewInstanceNode}.
         */
        @SuppressWarnings("unused")
        public void lower(NewInstanceNode newInstanceNode, CiLoweringTool tool) {
            StructuredGraph graph = (StructuredGraph) newInstanceNode.graph();
            HotSpotResolvedJavaType type = (HotSpotResolvedJavaType) newInstanceNode.instanceClass();
            HotSpotKlassOop hub = type.klassOop();
            int size = type.instanceSize();
            assert (size % wordSize()) == 0;
            assert size >= 0;

            ValueNode memory;
            if (!useTLAB) {
                memory = ConstantNode.forObject(null, runtime, graph);
            } else {
                TLABAllocateNode tlabAllocateNode = graph.add(new TLABAllocateNode(size, wordKind()));
                graph.addBeforeFixed(newInstanceNode, tlabAllocateNode);
                memory = tlabAllocateNode;
            }
            InitializeNode initializeNode = graph.add(new InitializeNode(memory, type));
            graph.replaceFixedWithFixed(newInstanceNode, initializeNode);
        }

        @SuppressWarnings("unused")
        public void lower(TLABAllocateNode tlabAllocateNode, CiLoweringTool tool) {
            StructuredGraph graph = (StructuredGraph) tlabAllocateNode.graph();
            int size = tlabAllocateNode.size();
            assert (size % wordSize()) == 0;
            assert size >= 0;
            Key key = new Key(allocate).add("size", size);
            Arguments arguments = new Arguments();
            SnippetTemplate template = cache.get(key);
            Debug.log("Lowering fastAllocate in %s: node=%s, template=%s, arguments=%s", graph, tlabAllocateNode, template, arguments);
            template.instantiate(runtime, tlabAllocateNode, tlabAllocateNode, arguments);
        }

        @SuppressWarnings("unused")
        public void lower(InitializeNode initializeNode, CiLoweringTool tool) {
            StructuredGraph graph = (StructuredGraph) initializeNode.graph();
            HotSpotResolvedJavaType type = (HotSpotResolvedJavaType) initializeNode.type();
            HotSpotKlassOop hub = type.klassOop();
            int size = type.instanceSize();
            assert (size % wordSize()) == 0;
            assert size >= 0;
            Key key = new Key(initialize).add("size", size);
            ValueNode memory = initializeNode.memory();
            //assert memory instanceof AllocateNode || memory instanceof ConstantNode : memory;
            Arguments arguments = arguments("memory", memory).add("hub", hub).add("prototypeHeader", type.prototypeHeader());
            SnippetTemplate template = cache.get(key);
            Debug.log("Lowering initialize in %s: node=%s, template=%s, arguments=%s", graph, initializeNode, template, arguments);
            template.instantiate(runtime, initializeNode, initializeNode, arguments);
        }
    }
}
