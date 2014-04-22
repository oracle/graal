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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.hotspot.meta.HotSpotForeignCallsProviderImpl.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.nodes.PiNode.*;
import static com.oracle.graal.replacements.SnippetTemplate.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;

/**
 * Snippet for loading the exception object at the start of an exception dispatcher.
 */
public class LoadExceptionObjectSnippets implements Snippets {

    /**
     * Alternative way to implement exception object loading.
     */
    private static final boolean USE_C_RUNTIME = Boolean.getBoolean("graal.loadExceptionObject.useCRuntime");

    @Snippet
    public static Throwable loadException(@ConstantParameter Register threadRegister) {
        Word thread = registerAsWord(threadRegister);
        Object exception = readExceptionOop(thread);
        writeExceptionOop(thread, null);
        writeExceptionPc(thread, Word.zero());
        return piCast(exception, StampFactory.forNodeIntrinsic());
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo loadException = snippet(LoadExceptionObjectSnippets.class, "loadException");

        public Templates(HotSpotProviders providers, TargetDescription target) {
            super(providers, providers.getSnippetReflection(), target);
        }

        public void lower(LoadExceptionObjectNode loadExceptionObject, HotSpotRegistersProvider registers, LoweringTool tool) {
            if (USE_C_RUNTIME) {
                StructuredGraph graph = loadExceptionObject.graph();
                ReadRegisterNode thread = graph.add(new ReadRegisterNode(registers.getThreadRegister(), true, false));
                graph.addBeforeFixed(loadExceptionObject, thread);
                ForeignCallNode loadExceptionC = graph.add(new ForeignCallNode(providers.getForeignCalls(), LOAD_AND_CLEAR_EXCEPTION, thread));
                loadExceptionC.setStateAfter(loadExceptionObject.stateAfter());
                graph.replaceFixedWithFixed(loadExceptionObject, loadExceptionC);
            } else {
                Arguments args = new Arguments(loadException, loadExceptionObject.graph().getGuardsStage(), tool.getLoweringStage());
                args.addConst("threadRegister", registers.getThreadRegister());
                template(args).instantiate(providers.getMetaAccess(), loadExceptionObject, DEFAULT_REPLACER, args);
            }
        }
    }
}
