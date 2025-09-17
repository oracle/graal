/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkageImpl;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.vm.ci.meta.Value;

/**
 * Represents the finalized foreign call linkages from a recorded compilation.
 *
 * @param linkages a list of individual linkages mapped by {@link ForeignCallSignature#toString()}
 */
public record RecordedForeignCallLinkages(EconomicMap<String, RecordedForeignCallLinkage> linkages) {
    /**
     * A finalized foreign call linkage from a recorded compilation.
     *
     * @param address the address of the callee
     * @param temporaries the slots and registers killed by the callee
     */
    record RecordedForeignCallLinkage(long address, Value[] temporaries) {
        RecordedForeignCallLinkage {
            GraalError.guarantee(address != 0L, "only finalized linkages should be recorded");
        }
    }

    /**
     * Creates an instance of {@link RecordedForeignCallLinkages} using the finalized linkages from
     * the given foreign calls provider.
     *
     * @param provider foreign calls provider
     * @return the recorded linkages
     */
    public static RecordedForeignCallLinkages createFrom(HotSpotHostForeignCallsProvider provider) {
        EconomicMap<String, RecordedForeignCallLinkage> linkages = EconomicMap.create();
        provider.forEachForeignCall((signature, linkage) -> {
            if (linkage == null || !linkage.hasAddress()) {
                return;
            }
            linkages.put(signature.toString(), new RecordedForeignCallLinkage(linkage.getAddress(), linkage.getTemporaries()));
        });
        return new RecordedForeignCallLinkages(linkages);
    }

    /**
     * Sets the address and temporaries of a foreign call linkage to the recorded address and
     * temporaries. This is a no-op if there is no recorded linkage with a matching signature.
     *
     * @param signature the signature of the foreign call
     * @param linkage the linkage to be updated
     * @return true if the linkage was finalized
     */
    boolean finalizeForeignCallLinkage(ForeignCallSignature signature, HotSpotForeignCallLinkage linkage) {
        RecordedForeignCallLinkage recordedLinkage = linkages.get(signature.toString());
        if (recordedLinkage == null) {
            return false;
        }
        ((HotSpotForeignCallLinkageImpl) linkage).finalizeExternally(recordedLinkage.address, recordedLinkage.temporaries);
        return true;
    }
}
