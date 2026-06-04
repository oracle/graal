/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.pltgot;

import java.nio.ByteBuffer;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.code.DynamicMethodAddressResolutionHeapSupport;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider.Access;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;
import com.oracle.svm.guest.staging.c.function.CEntryPointErrors;
import com.oracle.svm.guest.staging.config.SubstrateGuestTarget;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.nodes.PauseNode;

public abstract class GOTHeapSupport extends DynamicMethodAddressResolutionHeapSupport {

    public static final String IMAGE_GOT_END_SYMBOL_NAME = "__svm_got_end";
    public static final CGlobalData<Pointer> IMAGE_GOT_END = CGlobalDataFactory.forSymbol(IMAGE_GOT_END_SYMBOL_NAME);
    public static final String IMAGE_GOT_BEGIN_SYMBOL_NAME = "__svm_got_begin";
    public static final CGlobalData<Pointer> IMAGE_GOT_BEGIN = CGlobalDataFactory.forSymbol(IMAGE_GOT_BEGIN_SYMBOL_NAME);

    private static final SignedWord GOT_UNINITIALIZED = Word.signed(-1);
    private static final SignedWord GOT_INITIALIZATION_IN_PROGRESS = Word.signed(-2);
    private static final CGlobalData<Pointer> GOT_STATUS = CGlobalDataFactory.createWord(GOT_UNINITIALIZED);
    static final CGlobalData<WordPointer> GOT_START_ADDRESS = CGlobalDataFactory.createWord();

    private static final CGlobalData<Word> NUMBER_OF_GOT_ENTRIES = CGlobalDataFactory.createBytes(GOTHeapSupport::createNumberOfGOTEntriesBytes);

    @Platforms(Platform.HOSTED_ONLY.class) //
    private long numberOfGOTEntries = -1;

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setNumberOfGOTEntries(long value) {
        assert numberOfGOTEntries == -1 : String.format("Number of GOT entries was already initialized to %d", numberOfGOTEntries);
        numberOfGOTEntries = value;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static byte[] createNumberOfGOTEntriesBytes() {
        long numberOfEntries = GOTHeapSupport.get().numberOfGOTEntries;
        assert numberOfEntries >= 0 : "Number of GOT entries was not initialized.";
        int wordSize = SubstrateGuestTarget.getWordSize();
        return ByteBuffer.allocate(wordSize).order(SubstrateGuestTarget.getByteOrder()).putLong(numberOfEntries).array();
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected static UnsignedWord getGOTSectionSize() {
        return IMAGE_GOT_END.get().subtract(IMAGE_GOT_BEGIN.get());
    }

    /*
     * Empty GOT-related Mach-O sections must be physically padded to one word for object-file symbol
     * validity, so for empty GOT on Mach-O getGOTSectionSize() / wordSize will not be equal to the NUMBER_OF_GOT_ENTRIES.
     * Runtime code must use this logical flag, not section allocation size, to decide whether GOT initialization is needed.
     */
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean hasGOTEntries() {
        Word numberOfEntries = NUMBER_OF_GOT_ENTRIES.get().readWord(0);
        return numberOfEntries.aboveThan(0);
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected static UnsignedWord getPageAlignedGOTSize() {
        UnsignedWord gotSectionSize = getGOTSectionSize();
        UnsignedWord pageSize = VirtualMemoryProvider.get().getGranularity();
        return PointerUtils.roundUp((PointerBase) gotSectionSize, pageSize);
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected static UnsignedWord getGOTOffsetFromStartOfMapping() {
        return getPageAlignedGOTSize().subtract(getGOTSectionSize());
    }

    @Override
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public UnsignedWord getRequiredPreHeapMemoryInBytes() {
        return getPageAlignedGOTSize();
    }

    @Fold
    public static GOTHeapSupport get() {
        return (GOTHeapSupport) ImageSingletons.lookup(DynamicMethodAddressResolutionHeapSupport.class);
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void makeGOTWritable() {
        if (hasGOTEntries()) {
            changeGOTMappingProtection(true);
        }
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void makeGOTReadOnly() {
        if (hasGOTEntries()) {
            changeGOTMappingProtection(false);
        }
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected abstract int mapGOT(Pointer address);

    @Override
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int install(Pointer heapBase) {
        if (!hasGOTEntries()) {
            return CEntryPointErrors.NO_ERROR;
        }
        return mapGOT(getPreHeapMappingStartAddress(heapBase));
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected Pointer getPreHeapMappingStartAddress() {
        return getPreHeapMappingStartAddress(KnownIntrinsics.heapBase());
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected Pointer getPreHeapMappingStartAddress(PointerBase heapBase) {
        return ((Pointer) heapBase).subtract(getRequiredPreHeapMemoryInBytes());
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void changeGOTMappingProtection(boolean writable) {
        Pointer gotMappingStartAddress = GOT_START_ADDRESS.get().read();
        VMError.guarantee(gotMappingStartAddress.isNonNull());
        int access = Access.READ;
        if (writable) {
            access |= Access.WRITE;
        }
        int ret = VirtualMemoryProvider.get().protect(gotMappingStartAddress, getPageAlignedGOTSize(), access);
        VMError.guarantee(ret == 0, "Failed to change GOT protection.");
    }

    @Override
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int initialize() {
        if (!hasGOTEntries()) {
            return CEntryPointErrors.NO_ERROR;
        }
        boolean isFirstIsolate = GOT_STATUS.get().logicCompareAndSwapWord(0, GOT_UNINITIALIZED, GOT_INITIALIZATION_IN_PROGRESS, LocationIdentity.ANY_LOCATION);
        if (!isFirstIsolate) {
            while (true) {
                SignedWord status = GOT_STATUS.get().readWordVolatile(0, LocationIdentity.ANY_LOCATION);
                if (status.notEqual(GOT_INITIALIZATION_IN_PROGRESS)) {
                    long rawStatus = status.rawValue();
                    assert rawStatus == (int) rawStatus;
                    return (int) rawStatus;
                }
                /* Being nice to the CPU while busy waiting */
                PauseNode.pause();
            }
        }

        // Only the first isolate can reach here.
        int ret = initialize(GOT_START_ADDRESS.get());
        if (ret == CEntryPointErrors.NO_ERROR) {
            makeGOTReadOnly();
        }
        GOT_STATUS.get().writeWordVolatile(0, Word.signed(ret));
        return ret;
    }

    /**
     * Initialize the GOT and write its address to {@code gotStartAddress}. Return
     * {@link CEntryPointErrors#NO_ERROR} on success, an error code otherwise.
     */
    protected abstract int initialize(WordPointer gotStartAddress);
}
