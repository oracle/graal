/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.hsail.replacements;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.replacements.Snippet.Fold;
import com.oracle.graal.word.*;

//JaCoCo Exclude

/**
 * A collection of methods used in HSAIL-specific snippets and substitutions.
 */
public class HSAILHotSpotReplacementsUtil extends HotSpotReplacementsUtil {

    private static HotSpotRegistersProvider hsailRegisters;

    public static void initialize(HotSpotRegistersProvider registers) {
        hsailRegisters = registers;
    }

    public static final LocationIdentity TLAB_INFO_LOCATION = new NamedLocationIdentity("TlabInfo");
    public static final LocationIdentity TLABINFO_LASTGOODTOP_LOCATION = new NamedLocationIdentity("TlabInfoLastGoodTop");
    public static final LocationIdentity TLABINFO_END_LOCATION = new NamedLocationIdentity("TlabInfoEnd");
    public static final LocationIdentity TLABINFO_TOP_LOCATION = new NamedLocationIdentity("TlabInfoTop");
    public static final LocationIdentity TLABINFO_START_LOCATION = new NamedLocationIdentity("TlabInfoStart");
    public static final LocationIdentity TLABINFO_ALLOCINFO_LOCATION = new NamedLocationIdentity("TlabInfoAllocInfo");
    public static final LocationIdentity TLABINFO_ORIGINALTOP_LOCATION = new NamedLocationIdentity("TlabInfoOriginalTop");
    public static final LocationIdentity TLABINFO_TLAB_LOCATION = new NamedLocationIdentity("TlabInfoTlab");

    public static final LocationIdentity ALLOCINFO_TLABINFOSPOOLNEXT_LOCATION = new NamedLocationIdentity("AllocInfoTlabInfosPoolNext");
    public static final LocationIdentity ALLOCINFO_TLABINFOSPOOLEND_LOCATION = new NamedLocationIdentity("AllocInfoTlabInfosPoolEnd");
    public static final LocationIdentity ALLOCINFO_TLABALIGNRESERVEBYTES_LOCATION = new NamedLocationIdentity("AllocInfoTlabAlignreservebytes");

    /**
     * Gets the value of the thread register as a Word. There is a level of indirection here. Thread
     * register actually points to a holder for tlab info.
     */
    public static Word getTlabInfoPtr() {
        Word threadRegAsWord = registerAsWord(threadRegister(), true, false);
        return threadRegAsWord.readWord(0, TLAB_INFO_LOCATION);
    }

    public static Word getTlabInfoPtrLoadAcquire() {
        Word threadRegAsWord = registerAsWord(threadRegister(), true, false);
        return Word.unsigned(HSAILDirectLoadAcquireNode.loadAcquireLong(threadRegAsWord));
    }

    public static void writeTlabInfoPtrStoreRelease(Word val) {
        // this only gets done in the waiting loop so we will always use Store Release
        Word threadRegAsWord = registerAsWord(threadRegister(), true, false);
        HSAILDirectStoreReleaseNode.storeReleaseLong(threadRegAsWord, val.rawValue());
    }

    @Fold
    public static Register threadRegister() {
        return hsailRegisters.getThreadRegister();
    }

    public static Word atomicGetAndAddTlabInfoTop(Word tlabInfo, int delta) {
        return Word.unsigned(AtomicReadAndAddNode.getAndAddLong(null, tlabInfo.rawValue() + config().hsailTlabInfoTopOffset, delta, TLABINFO_TOP_LOCATION));
    }

    public static Word readTlabInfoEnd(Word tlabInfo) {
        return tlabInfo.readWord(config().hsailTlabInfoEndOffset, TLABINFO_END_LOCATION);
    }

    public static Word readTlabInfoStart(Word tlabInfo) {
        return tlabInfo.readWord(config().hsailTlabInfoStartOffset, TLABINFO_START_LOCATION);
    }

    public static void writeTlabInfoLastGoodTop(Word tlabInfo, Word val) {
        tlabInfo.writeWord(config().hsailTlabInfoLastGoodTopOffset, val, TLABINFO_LASTGOODTOP_LOCATION);
    }

    public static void writeTlabInfoStart(Word tlabInfo, Word val) {
        tlabInfo.writeWord(config().hsailTlabInfoStartOffset, val, TLABINFO_START_LOCATION);
    }

    public static void writeTlabInfoTop(Word tlabInfo, Word val) {
        tlabInfo.writeWord(config().hsailTlabInfoTopOffset, val, TLABINFO_TOP_LOCATION);
    }

    public static void writeTlabInfoEnd(Word tlabInfo, Word val) {
        tlabInfo.writeWord(config().hsailTlabInfoEndOffset, val, TLABINFO_END_LOCATION);
    }

    public static Word readTlabInfoAllocInfo(Word tlabInfo) {
        return tlabInfo.readWord(config().hsailTlabInfoAllocInfoOffset, TLABINFO_ALLOCINFO_LOCATION);
    }

    public static void writeTlabInfoAllocInfo(Word tlabInfo, Word val) {
        tlabInfo.writeWord(config().hsailTlabInfoAllocInfoOffset, val, TLABINFO_ALLOCINFO_LOCATION);
    }

    public static void writeTlabInfoOriginalTop(Word tlabInfo, Word val) {
        tlabInfo.writeWord(config().hsailTlabInfoOriginalTopOffset, val, TLABINFO_ORIGINALTOP_LOCATION);
    }

    public static void writeTlabInfoTlab(Word tlabInfo, Word val) {
        tlabInfo.writeWord(config().hsailTlabInfoTlabOffset, val, TLABINFO_TLAB_LOCATION);
    }

    public static Word readTlabInfoTlab(Word tlabInfo) {
        return tlabInfo.readWord(config().hsailTlabInfoTlabOffset, TLABINFO_TLAB_LOCATION);
    }

    public static Word readAllocInfoTlabInfosPoolEnd(Word allocInfo) {
        return allocInfo.readWord(config().hsailAllocInfoTlabInfosPoolEndOffset, ALLOCINFO_TLABINFOSPOOLEND_LOCATION);
    }

    public static Word readAllocInfoTlabAlignReserveBytes(Word allocInfo) {
        return allocInfo.readWord(config().hsailAllocInfoTlabAlignReserveBytesOffset, ALLOCINFO_TLABALIGNRESERVEBYTES_LOCATION);
    }

    public static Word atomicGetAndAddAllocInfoTlabInfosPoolNext(Word allocInfo, int delta) {
        return Word.unsigned(AtomicReadAndAddNode.getAndAddLong(null, allocInfo.rawValue() + config().hsailAllocInfoTlabInfosPoolNextOffset, delta, ALLOCINFO_TLABINFOSPOOLNEXT_LOCATION));
    }

}
