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
package jdk.compiler.graal.hotspot.replacements;

import static jdk.compiler.graal.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.HOTSPOT_CARRIER_THREAD_OOP_HANDLE_LOCATION;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_CARRIER_THREAD_OBJECT_LOCATION;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.javaLangThreadJFREpochOffset;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.javaLangThreadTIDOffset;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.jfrThreadLocalVthreadEpochOffset;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.jfrThreadLocalVthreadExcludedOffset;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.jfrThreadLocalVthreadIDOffset;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.jfrThreadLocalVthreadOffset;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.registerAsWord;
import static jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil.threadCarrierThreadOffset;
import static jdk.compiler.graal.nodes.extended.BranchProbabilityNode.LIKELY_PROBABILITY;
import static jdk.compiler.graal.nodes.extended.BranchProbabilityNode.probability;
import static jdk.compiler.graal.nodes.extended.MembarNode.memoryBarrier;
import static jdk.compiler.graal.replacements.SnippetTemplate.DEFAULT_REPLACER;
import static jdk.compiler.graal.word.Word.objectToTrackedPointer;

import jdk.compiler.graal.api.replacements.Snippet;
import jdk.compiler.graal.api.replacements.Snippet.ConstantParameter;
import jdk.compiler.graal.hotspot.meta.HotSpotProviders;
import jdk.compiler.graal.hotspot.meta.HotSpotRegistersProvider;
import jdk.compiler.graal.hotspot.nodes.VirtualThreadUpdateJFRNode;
import jdk.compiler.graal.lir.SyncPort;
import jdk.compiler.graal.nodes.NamedLocationIdentity;
import jdk.compiler.graal.nodes.extended.MembarNode;
import jdk.compiler.graal.nodes.spi.LoweringTool;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.replacements.SnippetTemplate.AbstractTemplates;
import jdk.compiler.graal.replacements.SnippetTemplate.Arguments;
import jdk.compiler.graal.replacements.SnippetTemplate.SnippetInfo;
import jdk.compiler.graal.replacements.Snippets;
import jdk.compiler.graal.word.Word;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.code.Register;

/**
 * Snippet for updating JFR thread local data on {@code Thread#setCurrentThread} events.
 */
// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/0a3a925ad88921d387aa851157f54ac0054d347b/src/hotspot/share/opto/library_call.cpp#L3453-L3579",
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
