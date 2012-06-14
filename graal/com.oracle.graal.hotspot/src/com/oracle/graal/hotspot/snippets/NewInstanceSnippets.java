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

import static com.oracle.graal.hotspot.nodes.RegisterNode.*;
import static com.oracle.graal.hotspot.snippets.DirectObjectStoreNode.*;
import static com.oracle.graal.nodes.calc.Condition.*;
import static com.oracle.graal.nodes.extended.UnsafeCastNode.*;
import static com.oracle.graal.nodes.extended.UnsafeLoadNode.*;
import static com.oracle.graal.snippets.SnippetTemplate.Arguments.*;
import static com.oracle.graal.snippets.nodes.ExplodeLoopNode.*;
import static com.oracle.max.asm.target.amd64.AMD64.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.phases.*;
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

    private static final boolean LOG_ALLOCATION = Boolean.getBoolean("graal.traceAllocation");

    @Snippet
    public static Object newInstance(
                    @Parameter("hub") Object hub,
                    @ConstantParameter("size") int size,
                    @ConstantParameter("checkInit") boolean checkInit,
                    @ConstantParameter("useTLAB") boolean useTLAB,
                    @ConstantParameter("logType") String logType) {

        if (checkInit) {
            int klassState = load(hub, 0, klassStateOffset(), Kind.Int);
            if (klassState != klassStateFullyInitialized()) {
                if (logType != null) {
                    Log.print(logType);
                    Log.println(" - uninitialized");
                }
                return NewInstanceStubCall.call(hub);
            }
        }

        if (useTLAB) {
            Word thread = asWord(register(r15, wordKind()));
            Word top = loadWord(thread, threadTlabTopOffset());
            Word end = loadWord(thread, threadTlabEndOffset());
            Word newTop = top.plus(size);
            if (newTop.cmp(BE, end)) {
                Object instance = cast(top, Object.class);
                store(thread, 0, threadTlabTopOffset(), newTop);
                return formatInstance(hub, size, instance, logType);
            } else {
                if (logType != null) {
                    Log.print(logType);
                    Log.println(" - stub allocate");
                }
                return NewInstanceStubCall.call(hub);
            }
        } else {
            return NewInstanceStubCall.call(hub);
        }
    }

    private static Word asWord(Object object) {
        return cast(object, Word.class);
    }

    private static Word loadWord(Object object, int offset) {
        return cast(load(object, 0, offset, wordKind()), Word.class);
    }

    /**
     * Formats the header of a created instance and zeroes out its body.
     */
    private static Object formatInstance(Object hub, int size, Object instance, String logType) {
        Word headerPrototype = cast(load(hub, 0, instanceHeaderPrototypeOffset(), wordKind()), Word.class);
        store(instance, 0, 0, headerPrototype);
        store(instance, 0, hubOffset(), hub);
        explodeLoop();
        for (int offset = 2 * wordSize(); offset < size; offset += wordSize()) {
            store(instance, 0, offset, 0);
        }
        if (logType != null) {
            Log.print("allocated instance of ");
            Log.print(logType);
            Log.print(" at ");
            Log.printlnAddress(instance);
        }
        return instance;
    }

    @Fold
    private static int klassStateOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().klassStateOffset;
    }

    @Fold
    private static int klassStateFullyInitialized() {
        return HotSpotGraalRuntime.getInstance().getConfig().klassStateFullyInitialized;
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
    private static int instanceHeaderPrototypeOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().instanceHeaderPrototypeOffset;
    }

    @Fold
    private static int hubOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().hubOffset;
    }

    public static class Templates {

        private final Cache cache;
        private final ResolvedJavaMethod newInstance;
        private final CodeCacheProvider runtime;
        private final boolean useTLAB;

        public Templates(CodeCacheProvider runtime, boolean useTLAB) {
            this.runtime = runtime;
            this.cache = new Cache(runtime);
            this.useTLAB = useTLAB;
            try {
                newInstance = runtime.getResolvedJavaMethod(NewInstanceSnippets.class.getDeclaredMethod("newInstance", Object.class, int.class, boolean.class, boolean.class, String.class));
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
            int instanceSize = type.instanceSize();
            Key key = new Key(newInstance).add("size", instanceSize).add("checkInit", !type.isInitialized()).add("useTLAB", useTLAB).add("logType", LOG_ALLOCATION ? type.name() : null);
            Arguments arguments = arguments("hub", hub);
            SnippetTemplate template = cache.get(key);
            Debug.log("Lowering newInstance in %s: node=%s, template=%s, arguments=%s", graph, newInstanceNode, template, arguments);
            //System.out.printf("Lowering newInstance in %s: node=%s, template=%s, arguments=%s%n", graph, newInstanceNode, template, arguments);
            template.instantiate(runtime, newInstanceNode, newInstanceNode, arguments);
            new DeadCodeEliminationPhase().apply(graph);
        }
    }
}
