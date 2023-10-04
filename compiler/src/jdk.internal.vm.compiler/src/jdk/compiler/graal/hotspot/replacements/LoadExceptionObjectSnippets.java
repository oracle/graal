/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.hotspot.replacements;

import static jdk.compiler.graal.hotspot.meta.HotSpotForeignCallsProviderImpl.LOAD_AND_CLEAR_EXCEPTION;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.EXCEPTION_OOP_LOCATION;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.EXCEPTION_PC_LOCATION;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.readExceptionOop;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.registerAsWord;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.writeExceptionOop;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.writeExceptionPc;
import static jdk.compiler.graal.hotspot.replacements.HotspotSnippetsOptions.LoadExceptionObjectInVM;
import static jdk.compiler.graal.nodes.PiNode.piCastToSnippetReplaceeStamp;
import static jdk.compiler.graal.replacements.SnippetTemplate.DEFAULT_REPLACER;

import jdk.compiler.graal.api.replacements.Snippet;
import jdk.compiler.graal.api.replacements.Snippet.ConstantParameter;
import jdk.compiler.graal.core.common.type.Stamp;
import jdk.compiler.graal.hotspot.meta.HotSpotProviders;
import jdk.compiler.graal.hotspot.meta.HotSpotRegistersProvider;
import jdk.compiler.graal.hotspot.word.HotSpotWordTypes;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.extended.ForeignCallNode;
import jdk.compiler.graal.nodes.java.LoadExceptionObjectNode;
import jdk.compiler.graal.nodes.spi.LoweringTool;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.replacements.SnippetTemplate.AbstractTemplates;
import jdk.compiler.graal.replacements.SnippetTemplate.Arguments;
import jdk.compiler.graal.replacements.SnippetTemplate.SnippetInfo;
import jdk.compiler.graal.replacements.Snippets;
import jdk.compiler.graal.replacements.nodes.ReadRegisterNode;
import jdk.compiler.graal.word.Word;
import org.graalvm.word.WordFactory;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Snippet for loading the exception object at the start of an exception dispatcher.
 * <p>
 * The frame state upon entry to an exception handler is such that it is a
 * {@link BytecodeFrame#rethrowException rethrow exception} state and the stack contains exactly the
 * exception object (per the JVM spec) to rethrow. This means that the code generated for this node
 * must not cause a deoptimization as the runtime/interpreter would not have a valid location to
 * find the exception object to be rethrown.
 */
public class LoadExceptionObjectSnippets implements Snippets {

    @Snippet
    public static Object loadException(@ConstantParameter Register threadRegister) {
        Word thread = registerAsWord(threadRegister);
        Object exception = readExceptionOop(thread);
        writeExceptionOop(thread, null);
        writeExceptionPc(thread, WordFactory.zero());
        return piCastToSnippetReplaceeStamp(exception);
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo loadException;
        private final HotSpotWordTypes wordTypes;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, HotSpotProviders providers) {
            super(options, providers);

            this.loadException = snippet(providers,
                            LoadExceptionObjectSnippets.class,
                            "loadException",
                            EXCEPTION_OOP_LOCATION,
                            EXCEPTION_PC_LOCATION);
            this.wordTypes = providers.getWordTypes();
        }

        public void lower(LoadExceptionObjectNode loadExceptionObject, HotSpotRegistersProvider registers, LoweringTool tool) {
            StructuredGraph graph = loadExceptionObject.graph();
            if (LoadExceptionObjectInVM.getValue(graph.getOptions())) {
                ResolvedJavaType wordType = tool.getMetaAccess().lookupJavaType(Word.class);
                Stamp stamp = wordTypes.getWordStamp(wordType);
                ReadRegisterNode thread = graph.add(new ReadRegisterNode(stamp, registers.getThreadRegister(), true, false));
                graph.addBeforeFixed(loadExceptionObject, thread);
                ForeignCallNode loadExceptionC = graph.add(new ForeignCallNode(LOAD_AND_CLEAR_EXCEPTION, thread));
                loadExceptionC.setStateAfter(loadExceptionObject.stateAfter());
                loadExceptionC.computeStateDuring(loadExceptionObject.stateAfter());
                graph.replaceFixedWithFixed(loadExceptionObject, loadExceptionC);
            } else {
                Arguments args = new Arguments(loadException, loadExceptionObject.graph().getGuardsStage(), tool.getLoweringStage());
                args.addConst("threadRegister", registers.getThreadRegister());
                template(tool, loadExceptionObject, args).instantiate(tool.getMetaAccess(), loadExceptionObject, DEFAULT_REPLACER, args);
            }
        }
    }
}
