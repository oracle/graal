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
package jdk.compiler.graal.hotspot.stubs;

import static jdk.vm.ci.meta.DeoptimizationReason.RuntimeConstraint;
import static jdk.compiler.graal.hotspot.stubs.StubUtil.fatal;

import jdk.compiler.graal.api.replacements.Fold;
import jdk.compiler.graal.api.replacements.Fold.InjectedParameter;
import jdk.compiler.graal.api.replacements.Snippet;
import jdk.compiler.graal.hotspot.GraalHotSpotVMConfig;
import jdk.compiler.graal.hotspot.meta.HotSpotProviders;
import jdk.compiler.graal.hotspot.nodes.DeoptimizeCallerNode;
import jdk.compiler.graal.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.compiler.graal.hotspot.word.KlassPointer;
import jdk.compiler.graal.nodes.NamedLocationIdentity;
import jdk.compiler.graal.nodes.PiNode;
import jdk.compiler.graal.nodes.SnippetAnchorNode;
import jdk.compiler.graal.nodes.extended.GuardingNode;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.replacements.SnippetTemplate.AbstractTemplates;
import jdk.compiler.graal.replacements.SnippetTemplate.SnippetInfo;
import jdk.compiler.graal.replacements.Snippets;
import jdk.compiler.graal.word.Word;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import jdk.vm.ci.meta.DeoptimizationAction;

public class ForeignCallSnippets implements Snippets {

    public static class Templates extends AbstractTemplates {

        final SnippetInfo handlePendingException;
        final SnippetInfo getAndClearObjectResult;
        final SnippetInfo verifyObject;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, HotSpotProviders providers) {
            super(options, providers);

            this.handlePendingException = snippet(providers, ForeignCallSnippets.class, "handlePendingException");
            this.getAndClearObjectResult = snippet(providers, ForeignCallSnippets.class, "getAndClearObjectResult", OBJECT_RESULT_LOCATION);
            this.verifyObject = snippet(providers, ForeignCallSnippets.class, "verifyObject");
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
            Word verifyOopCounter = WordFactory.unsigned(HotSpotReplacementsUtil.verifyOopCounterAddress(GraalHotSpotVMConfig.INJECTED_VMCONFIG));
            verifyOopCounter.writeInt(0, verifyOopCounter.readInt(0) + 1);

            Pointer oop = Word.objectToTrackedPointer(object);
            if (object != null) {
                GuardingNode anchorNode = SnippetAnchorNode.anchor();
                // make sure object is 'reasonable'
                if (!oop.and(WordFactory.unsigned(HotSpotReplacementsUtil.verifyOopMask(GraalHotSpotVMConfig.INJECTED_VMCONFIG))).equal(
                                WordFactory.unsigned(HotSpotReplacementsUtil.verifyOopBits(GraalHotSpotVMConfig.INJECTED_VMCONFIG)))) {
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

    @Fold
    static long verifyOopMask(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.verifyOopMask;
    }

    @Fold
    static long verifyOopBits(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.verifyOopBits;
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
