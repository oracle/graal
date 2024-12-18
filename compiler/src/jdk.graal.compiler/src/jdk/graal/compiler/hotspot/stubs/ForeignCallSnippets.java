/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.stubs;

import static jdk.graal.compiler.hotspot.stubs.StubUtil.fatal;
import static jdk.vm.ci.meta.DeoptimizationReason.RuntimeConstraint;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.api.replacements.Fold.InjectedParameter;
import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.nodes.DeoptimizeCallerNode;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.hotspot.word.KlassPointer;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.SnippetAnchorNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.SnippetTemplate.AbstractTemplates;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.DeoptimizationAction;

public class ForeignCallSnippets implements Snippets {

    public static class Templates extends AbstractTemplates {

        final SnippetInfo handlePendingException;
        final SnippetInfo getAndClearObjectResult;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, HotSpotProviders providers) {
            super(options, providers);

            this.handlePendingException = snippet(providers, ForeignCallSnippets.class, "handlePendingException");
            this.getAndClearObjectResult = snippet(providers, ForeignCallSnippets.class, "getAndClearObjectResult", OBJECT_RESULT_LOCATION);
        }
    }

    /**
     * See {@link ForeignCallStub#getGraph}.
     */
    @Snippet(allowMissingProbabilities = true)
    public static void handlePendingException(Word thread, boolean shouldClearException, boolean isObjectResult) {
        if ((shouldClearException && HotSpotReplacementsUtil.clearPendingException(thread) != null) || (!shouldClearException && HotSpotReplacementsUtil.getPendingException(thread) != null)) {
            if (isObjectResult) {
                getAndClearObjectResult(thread);
            }
            DeoptimizeCallerNode.deopt(DeoptimizationAction.None, RuntimeConstraint);
        }
    }

    /**
     * Verifies that a given object value is well formed if {@code -XX:+VerifyOops} is enabled.
     */
    @Snippet(allowMissingProbabilities = true)
    public static Object verifyObject(Object object) {
        if (HotSpotReplacementsUtil.verifyOops(GraalHotSpotVMConfig.INJECTED_VMCONFIG)) {
            Word verifyOopCounter = Word.unsigned(HotSpotReplacementsUtil.verifyOopCounterAddress(GraalHotSpotVMConfig.INJECTED_VMCONFIG));
            verifyOopCounter.writeInt(0, verifyOopCounter.readInt(0) + 1);

            Pointer oop = Word.objectToTrackedPointer(object);
            if (object != null) {
                GuardingNode anchorNode = SnippetAnchorNode.anchor();
                // make sure object is 'reasonable'
                if (!oop.and(Word.unsigned(HotSpotReplacementsUtil.verifyOopMask(GraalHotSpotVMConfig.INJECTED_VMCONFIG))).equal(
                                Word.unsigned(HotSpotReplacementsUtil.verifyOopBits(GraalHotSpotVMConfig.INJECTED_VMCONFIG)))) {
                    fatal("oop not in heap: %p", oop.rawValue());
                }

                KlassPointer klass = HotSpotReplacementsUtil.loadHubIntrinsic(PiNode.piCastNonNull(object, anchorNode));
                if (klass.isNull()) {
                    fatal("klass for oop %p is null", oop.rawValue());
                }
            }
        }
        return object;
    }

    /**
     * Gets and clears the object result from a runtime call stored in a thread local.
     *
     * @return the object that was in the thread local
     */
    @Snippet
    public static Object getAndClearObjectResult(Word thread) {
        Object result = thread.readObject(objectResultOffset(GraalHotSpotVMConfig.INJECTED_VMCONFIG), OBJECT_RESULT_LOCATION);
        thread.writeObject(objectResultOffset(GraalHotSpotVMConfig.INJECTED_VMCONFIG), null, OBJECT_RESULT_LOCATION);
        return result;
    }

    public static final LocationIdentity OBJECT_RESULT_LOCATION = NamedLocationIdentity.mutable("ObjectResult");

    @Fold
    static int objectResultOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.threadObjectResultOffset;
    }
}
