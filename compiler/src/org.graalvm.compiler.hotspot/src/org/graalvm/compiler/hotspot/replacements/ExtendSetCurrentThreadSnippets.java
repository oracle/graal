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
package org.graalvm.compiler.hotspot.replacements;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.HOTSPOT_CARRIER_THREAD_OOP_HANDLE_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_CARRIER_THREAD_OBJECT_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.javaLangThreadJFREpochOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.javaLangThreadTIDOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.jfrThreadLocalVthreadEpochOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.jfrThreadLocalVthreadExcludedOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.jfrThreadLocalVthreadIDOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.jfrThreadLocalVthreadOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.registerAsWord;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.threadCarrierThreadOffset;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.LIKELY_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;
import static org.graalvm.compiler.nodes.extended.MembarNode.memoryBarrier;
import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;
import static org.graalvm.compiler.word.Word.objectToTrackedPointer;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.hotspot.nodes.ExtendSetCurrentThreadNode;
import org.graalvm.compiler.hotspot.word.HotSpotWordTypes;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.code.Register;

public class ExtendSetCurrentThreadSnippets implements Snippets {

    private static final int EXCLUDED_MASK = 0x8000;
    private static final int EPOCH_MASK = 0x7FFF;

    private static final byte BOOL_TRUE = (byte) 1;
    private static final byte BOOL_FALSE = (byte) 0;

    /*
     * The intrinsic is a model of this pseudo-code:
     * @formatter:off
     * JfrThreadLocal* const tl = thread->jfr_thread_local();
     * if (carrierThread != thread) { // is virtual thread
     *   const u2 vthread_epoch_raw = java_lang_Thread::jfr_epoch(thread);
     *   bool excluded = vthread_epoch_raw & excluded_mask;
     *   Atomic::store(&tl->_contextual_tid, java_lang_Thread::tid(thread));
     *   Atomic::store(&tl->_contextual_thread_excluded, is_excluded);
     *   if (!excluded) {
     *     const u2 vthread_epoch = vthread_epoch_raw & epoch_mask;
     *     Atomic::store(&tl->_vthread_epoch, vthread_epoch);
     *   }
     *   Atomic::release_store(&tl->_vthread, true);
     *   return;
     * }
     * Atomic::release_store(&tl->_vthread, false);
     * @formatter:on
     */
    @Snippet
    public static void extendSetCurrentThread(@ConstantParameter Register javaThreadRegister, Object thread) {
        Word javaThread = registerAsWord(javaThreadRegister);
        Word carrierThreadHandle = javaThread.readWord(threadCarrierThreadOffset(INJECTED_VMCONFIG), JAVA_THREAD_CARRIER_THREAD_OBJECT_LOCATION);
        Word virtualThread = objectToTrackedPointer(thread);

        if (probability(LIKELY_PROBABILITY, virtualThread.notEqual(carrierThreadHandle.readWord(0, HOTSPOT_CARRIER_THREAD_OOP_HANDLE_LOCATION)))) {
            int vthreadEpochRaw = virtualThread.readShort(javaLangThreadJFREpochOffset(INJECTED_VMCONFIG));
            Word tid = virtualThread.readWord(javaLangThreadTIDOffset(INJECTED_VMCONFIG));
            javaThread.writeWord(jfrThreadLocalVthreadIDOffset(INJECTED_VMCONFIG), tid);

            if (probability(LIKELY_PROBABILITY, (vthreadEpochRaw & EXCLUDED_MASK) == 0)) {
                javaThread.writeChar(jfrThreadLocalVthreadEpochOffset(INJECTED_VMCONFIG), (char) (vthreadEpochRaw & EPOCH_MASK));
                javaThread.writeByte(jfrThreadLocalVthreadExcludedOffset(INJECTED_VMCONFIG), BOOL_TRUE);
            } else {
                javaThread.writeByte(jfrThreadLocalVthreadExcludedOffset(INJECTED_VMCONFIG), BOOL_FALSE);
            }
            memoryBarrier(MembarNode.FenceKind.STORE_RELEASE);
            javaThread.writeByte(jfrThreadLocalVthreadOffset(INJECTED_VMCONFIG), BOOL_TRUE);
        } else {
            memoryBarrier(MembarNode.FenceKind.STORE_RELEASE);
            javaThread.writeByte(jfrThreadLocalVthreadOffset(INJECTED_VMCONFIG), BOOL_FALSE);
        }
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo extendCurrentThread;
        private final HotSpotWordTypes wordTypes;

        public Templates(OptionValues options, HotSpotProviders providers) {
            super(options, providers);

            this.extendCurrentThread = snippet(providers,
                            ExtendSetCurrentThreadSnippets.class,
                            "extendSetCurrentThread"
            // TODO location identities
            );
            this.wordTypes = providers.getWordTypes();
        }

        public void lower(ExtendSetCurrentThreadNode extendSetCurrentThreadNode, HotSpotRegistersProvider registers, LoweringTool tool) {
            StructuredGraph graph = extendSetCurrentThreadNode.graph();
            Arguments args = new Arguments(extendCurrentThread, graph.getGuardsStage(), tool.getLoweringStage());
            args.addConst("javaThreadRegister", registers.getThreadRegister());
            args.add("thread", extendSetCurrentThreadNode.getThread());

            template(tool, extendSetCurrentThreadNode, args).instantiate(tool.getMetaAccess(), extendSetCurrentThreadNode, DEFAULT_REPLACER, args);
        }
    }
}
