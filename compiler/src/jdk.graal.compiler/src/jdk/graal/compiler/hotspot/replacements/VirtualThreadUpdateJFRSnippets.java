/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replacements;

import static jdk.graal.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.HOTSPOT_CARRIER_THREAD_OOP_HANDLE_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_CARRIER_THREAD_OBJECT_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.javaLangThreadJFREpochOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.javaLangThreadTIDOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.jfrThreadLocalVthreadEpochOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.jfrThreadLocalVthreadExcludedOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.jfrThreadLocalVthreadIDOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.jfrThreadLocalVthreadOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.registerAsWord;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.threadCarrierThreadOffset;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.LIKELY_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;
import static jdk.graal.compiler.nodes.extended.MembarNode.memoryBarrier;
import static jdk.graal.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;
import static jdk.graal.compiler.word.Word.objectToTrackedPointer;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.meta.HotSpotRegistersProvider;
import jdk.graal.compiler.hotspot.nodes.VirtualThreadUpdateJFRNode;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.SnippetTemplate.AbstractTemplates;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.word.Word;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.code.Register;

/**
 * Snippet for updating JFR thread local data on {@code Thread#setCurrentThread} events.
 */
// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/be2b92bd8b43841cc2b9c22ed4fde29be30d47bb/src/hotspot/share/opto/library_call.cpp#L3484-L3610",
          sha1 = "1f980401f5d7d9a363577635fd57fc1e24505d91")
// @formatter:on
public class VirtualThreadUpdateJFRSnippets implements Snippets {

    private static final int EXCLUDED_MASK = 0x8000;
    private static final int EPOCH_MASK = 0x7FFF;

    private static final byte BOOL_TRUE = (byte) 1;
    private static final byte BOOL_FALSE = (byte) 0;

    public static final LocationIdentity JAVA_LANG_THREAD_JFR_EPOCH = NamedLocationIdentity.mutable("java/lang/Thread.jfrEpoch");
    public static final LocationIdentity JAVA_LANG_THREAD_TID = NamedLocationIdentity.mutable("java/lang/Thread.tid");

    public static final LocationIdentity JFR_THREAD_LOCAL_VTHREAD_ID = NamedLocationIdentity.mutable("JfrThreadLocal::_vthread_id");
    public static final LocationIdentity JFR_THREAD_LOCAL_VTHREAD_EPOCH = NamedLocationIdentity.mutable("JfrThreadLocal::_vthread_epoch");
    public static final LocationIdentity JFR_THREAD_LOCAL_VTHREAD_EXCLUDED = NamedLocationIdentity.mutable("JfrThreadLocal::_vthread_excluded");
    public static final LocationIdentity JFR_THREAD_LOCAL_VTHREAD = NamedLocationIdentity.mutable("JfrThreadLocal::_vthread");

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
    public static void virtualThreadUpdateJFR(@ConstantParameter Register javaThreadRegister, Object threadObj) {
        // Note that the following implementation originates from the C2 implementation at
        // LibraryCallKit::extend_setCurrentThread, but does not match the memory access order as
        // the pseudo-code.
        Word javaThread = registerAsWord(javaThreadRegister);
        Word carrierThreadHandle = javaThread.readWord(threadCarrierThreadOffset(INJECTED_VMCONFIG), JAVA_THREAD_CARRIER_THREAD_OBJECT_LOCATION);
        Word thread = objectToTrackedPointer(threadObj);

        if (probability(LIKELY_PROBABILITY, thread.notEqual(carrierThreadHandle.readWord(0, HOTSPOT_CARRIER_THREAD_OOP_HANDLE_LOCATION)))) {
            int vthreadEpochRaw = thread.readShort(javaLangThreadJFREpochOffset(INJECTED_VMCONFIG), JAVA_LANG_THREAD_JFR_EPOCH);
            Word tid = thread.readWord(javaLangThreadTIDOffset(INJECTED_VMCONFIG), JAVA_LANG_THREAD_TID);
            javaThread.writeWord(jfrThreadLocalVthreadIDOffset(INJECTED_VMCONFIG), tid, JFR_THREAD_LOCAL_VTHREAD_ID);

            if (probability(LIKELY_PROBABILITY, (vthreadEpochRaw & EXCLUDED_MASK) == 0)) {
                javaThread.writeChar(jfrThreadLocalVthreadEpochOffset(INJECTED_VMCONFIG), (char) (vthreadEpochRaw & EPOCH_MASK), JFR_THREAD_LOCAL_VTHREAD_EPOCH);
                javaThread.writeByte(jfrThreadLocalVthreadExcludedOffset(INJECTED_VMCONFIG), BOOL_FALSE, JFR_THREAD_LOCAL_VTHREAD_EXCLUDED);
            } else {
                javaThread.writeByte(jfrThreadLocalVthreadExcludedOffset(INJECTED_VMCONFIG), BOOL_TRUE, JFR_THREAD_LOCAL_VTHREAD_EXCLUDED);
            }
            memoryBarrier(MembarNode.FenceKind.STORE_RELEASE);
            javaThread.writeByte(jfrThreadLocalVthreadOffset(INJECTED_VMCONFIG), BOOL_TRUE, JFR_THREAD_LOCAL_VTHREAD);
        } else {
            memoryBarrier(MembarNode.FenceKind.STORE_RELEASE);
            javaThread.writeByte(jfrThreadLocalVthreadOffset(INJECTED_VMCONFIG), BOOL_FALSE, JFR_THREAD_LOCAL_VTHREAD);
        }
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo virtualThreadUpdateJFR;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, HotSpotProviders providers) {
            super(options, providers);

            this.virtualThreadUpdateJFR = snippet(providers,
                            VirtualThreadUpdateJFRSnippets.class,
                            "virtualThreadUpdateJFR",
                            JAVA_THREAD_CARRIER_THREAD_OBJECT_LOCATION,
                            HOTSPOT_CARRIER_THREAD_OOP_HANDLE_LOCATION,
                            JAVA_LANG_THREAD_JFR_EPOCH,
                            JAVA_LANG_THREAD_TID,
                            JFR_THREAD_LOCAL_VTHREAD_ID,
                            JFR_THREAD_LOCAL_VTHREAD_EPOCH,
                            JFR_THREAD_LOCAL_VTHREAD_EXCLUDED,
                            JFR_THREAD_LOCAL_VTHREAD);
        }

        public void lower(VirtualThreadUpdateJFRNode virtualThreadUpdateJFRNode, HotSpotRegistersProvider registers, LoweringTool tool) {
            Arguments args = new Arguments(virtualThreadUpdateJFR, virtualThreadUpdateJFRNode.graph().getGuardsStage(), tool.getLoweringStage());
            args.addConst("javaThreadRegister", registers.getThreadRegister());
            args.add("threadObj", virtualThreadUpdateJFRNode.getThread());

            template(tool, virtualThreadUpdateJFRNode, args).instantiate(tool.getMetaAccess(), virtualThreadUpdateJFRNode, DEFAULT_REPLACER, args);
        }
    }
}
