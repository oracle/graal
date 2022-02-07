/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_METAACCESS;
import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.nodes.java.InstanceOfNode.doInstanceof;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.ReplacementsUtil;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.replacements.nodes.FallbackInvokeWithExceptionNode;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.WordFactory;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public class DigestBaseSnippets implements Snippets {

    public static class Templates extends SnippetTemplate.AbstractTemplates {

        public Templates(OptionValues options, Providers providers) {
            super(options, providers);
        }

        public final SnippetTemplate.SnippetInfo implCompressMultiBlock0 = snippet(DigestBaseSnippets.class, "implCompressMultiBlock0");
    }

    @Snippet
    static int implCompressMultiBlock0(Object receiver, byte[] buf, int ofs, int limit,
                    @Snippet.ConstantParameter ResolvedJavaType receiverType,
                    @Snippet.ConstantParameter ResolvedJavaType sha1type,
                    @Snippet.ConstantParameter ResolvedJavaType sha256type,
                    @Snippet.ConstantParameter ResolvedJavaType sha512type) {
        Object realReceiver = PiNode.piCastNonNull(receiver, receiverType);

        Word bufAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(buf, ReplacementsUtil.getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Byte) + ofs));
        if (useSHA1Intrinsics(INJECTED_VMCONFIG) && doInstanceof(sha1type, realReceiver)) {
            Object sha1obj = PiNode.piCastNonNull(realReceiver, sha1type);
            Object state = RawLoadNode.load(sha1obj, HotSpotReplacementsUtil.getFieldOffset(sha1type, "state"), JavaKind.Object, LocationIdentity.any());
            Word stateAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(state, ReplacementsUtil.getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Int)));
            return HotSpotBackend.shaImplCompressMBStub(bufAddr, stateAddr, ofs, limit);
        } else if (useSHA256Intrinsics(INJECTED_VMCONFIG) && doInstanceof(sha256type, realReceiver)) {
            Object sha256obj = PiNode.piCastNonNull(realReceiver, sha256type);
            Object state = RawLoadNode.load(sha256obj, HotSpotReplacementsUtil.getFieldOffset(sha256type, "state"), JavaKind.Object, LocationIdentity.any());
            Word stateAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(state, ReplacementsUtil.getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Int)));
            return HotSpotBackend.sha2ImplCompressMBStub(bufAddr, stateAddr, ofs, limit);
        } else if (useSHA512Intrinsics(INJECTED_VMCONFIG) && doInstanceof(sha512type, realReceiver)) {
            Object sha512obj = PiNode.piCastNonNull(realReceiver, sha512type);
            Object state = RawLoadNode.load(sha512obj, HotSpotReplacementsUtil.getFieldOffset(sha512type, "state"), JavaKind.Object, LocationIdentity.any());
            Word stateAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(state, ReplacementsUtil.getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Int)));
            return HotSpotBackend.sha5ImplCompressMBStub(bufAddr, stateAddr, ofs, limit);
        } else {
            return FallbackInvokeWithExceptionNode.fallbackFunctionCallInt();
        }
    }

    @Fold
    public static boolean useSHA1Intrinsics(@Fold.InjectedParameter GraalHotSpotVMConfig config) {
        return config.useSHA1Intrinsics();
    }

    @Fold
    public static boolean useSHA256Intrinsics(@Fold.InjectedParameter GraalHotSpotVMConfig config) {
        return config.useSHA256Intrinsics();
    }

    @Fold
    public static boolean useSHA512Intrinsics(@Fold.InjectedParameter GraalHotSpotVMConfig config) {
        return config.useSHA512Intrinsics();
    }
}
