/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.windows;

import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.FIXUP_TABLE;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.NEXT_SECTION;
import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.CodeSynchronizationOperations;
import com.oracle.svm.core.imagelayer.BuildingImageLayerPredicate;
import com.oracle.svm.core.imagelayer.ImageLayerRuntimeSupport;
import com.oracle.svm.core.imagelayer.ImageLayerSection;
import com.oracle.svm.core.windows.headers.FileAPI;
import com.oracle.svm.core.windows.headers.LibLoaderAPI;
import com.oracle.svm.core.windows.headers.MemoryAPI;
import com.oracle.svm.core.windows.headers.WinBase;
import com.oracle.svm.core.windows.headers.WinBase.HANDLE;
import com.oracle.svm.core.windows.headers.WinBase.HMODULE;
import com.oracle.svm.core.windows.headers.WindowsLibC;
import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;
import com.oracle.svm.guest.staging.c.function.CEntryPointActions;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.PauseNode;

/**
 * Windows implementation of image-layer runtime fixups. It resolves PE/COFF forward references to
 * application-layer symbols and patches their recorded slots before process-wide initialization can
 * observe them.
 */
@AutomaticallyRegisteredImageSingleton(value = ImageLayerRuntimeSupport.class, onlyWith = BuildingImageLayerPredicate.class)
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public class WindowsImageLayerRuntimeSupport extends ImageLayerRuntimeSupport {

    private static final class ForwardSymbolPatchingState {
        static final Word UNINITIALIZED = Word.zero();
        static final Word IN_PROGRESS = Word.unsigned(1);
        static final Word SUCCESSFUL = Word.unsigned(2);
    }

    private static final CGlobalData<Word> FORWARD_SYMBOL_PATCHING_STATE = CGlobalDataFactory.createWord(ForwardSymbolPatchingState.UNINITIALIZED);

    private static final CGlobalData<CCharPointer> MSG_GET_MODULE_FAILED = CGlobalDataFactory.createCString("GetModuleHandleA(NULL) failed");
    private static final CGlobalData<CCharPointer> MSG_GET_PROC_FAILED = CGlobalDataFactory.createCString("GetProcAddress failed for layer symbol");
    private static final CGlobalData<CCharPointer> MSG_GET_PROC_FAILED_PREFIX = CGlobalDataFactory.createCString("GetProcAddress failed for layer symbol: ");
    private static final CGlobalData<CCharPointer> MSG_VIRTUAL_QUERY_FAILED = CGlobalDataFactory.createCString("VirtualQuery failed for PE/COFF layer fixup");
    private static final CGlobalData<CCharPointer> MSG_VIRTUAL_PROTECT_FAILED = CGlobalDataFactory.createCString("VirtualProtect failed for PE/COFF layer fixup");
    private static final CGlobalData<CCharPointer> MSG_VIRTUAL_PROTECT_RESTORE_FAILED = CGlobalDataFactory.createCString("VirtualProtect restore failed for PE/COFF layer fixup");
    private static final CGlobalData<CCharPointer> MSG_NEWLINE = CGlobalDataFactory.createCString("\n");

    /**
     * On PE/COFF layered builds, some forward symbol references cannot be resolved at link time.
     * Patch them now, before process-wide initialization can read runtime state that uses those
     * references.
     */
    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void patchForwardSymbolReferences() {
        Word stateAddr = FORWARD_SYMBOL_PATCHING_STATE.get();
        boolean firstIsolate = stateAddr.logicCompareAndSwapWord(0, ForwardSymbolPatchingState.UNINITIALIZED, ForwardSymbolPatchingState.IN_PROGRESS,
                        NamedLocationIdentity.OFF_HEAP_LOCATION);

        if (!firstIsolate) {
            Word state = stateAddr.readWordVolatile(0, NamedLocationIdentity.OFF_HEAP_LOCATION);
            while (state.equal(ForwardSymbolPatchingState.IN_PROGRESS)) {
                PauseNode.pause();
                state = stateAddr.readWordVolatile(0, NamedLocationIdentity.OFF_HEAP_LOCATION);
            }
            return;
        }

        patchNextSectionPointers();
        patchForwardReferenceSlots();
        stateAddr.writeWordVolatile(0, ForwardSymbolPatchingState.SUCCESSFUL);
    }

    /**
     * Patch the NEXT_SECTION linked list that could not be populated at link time on PE/COFF. Uses
     * {@code GetProcAddress} to find the final application layer's section symbol in the main
     * executable module.
     * <p>
     * GR-58631: This currently supports one shared layer before the final application layer.
     * Multi-layer chains need per-layer section metadata/exporting so each shared layer's
     * NEXT_SECTION entry can be patched to the next layer.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void patchNextSectionPointers() {
        CGlobalData<CCharPointer> nextSymbolName = ImageLayerSection.getNextLayerSectionSymbolName();
        if (nextSymbolName == null) {
            return;
        }
        CGlobalData<Pointer> initialLayerSection = ImageLayerSection.getInitialLayerSection();
        if (initialLayerSection == null) {
            return;
        }
        Pointer section = initialLayerSection.get();
        if (section.isNull()) {
            return;
        }
        HMODULE exeModule = LibLoaderAPI.GetModuleHandleA(Word.nullPointer());
        if (exeModule.isNull()) {
            CEntryPointActions.failFatally(0xBAD1, MSG_GET_MODULE_FAILED.get());
        }
        PointerBase nextSection = LibLoaderAPI.GetProcAddress(exeModule, nextSymbolName.get());
        if (nextSection.isNull()) {
            CEntryPointActions.failFatally(0xBAD2, MSG_GET_PROC_FAILED.get());
        }
        section.writeWord(ImageLayerSection.getEntryOffset(NEXT_SECTION), nextSection);
    }

    /**
     * Resolve forward-reference slots using each layer's runtime fixup table. Each table is in the
     * .svm_fix section and maps slot addresses to symbol names. For each entry, we look up the
     * symbol in the application layer's module via GetProcAddress and write the resolved address to
     * the slot.
     *
     * <pre>
     * Fixup table layout:
     *   int32: numEntries
     *   int32: stringDataOffset (from table start)
     *   int64[numEntries]: absolute addresses of slots (resolved by PE loader)
     *   int32[numEntries]: string offsets (relative to stringDataOffset)
     *   char[]: null-terminated symbol name strings
     * </pre>
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void patchForwardReferenceSlots() {
        HMODULE exeModule = LibLoaderAPI.GetModuleHandleA(Word.nullPointer());
        if (exeModule.isNull()) {
            CEntryPointActions.failFatally(0xBAD1, MSG_GET_MODULE_FAILED.get());
        }

        CGlobalData<Pointer> initialLayerSection = ImageLayerSection.getInitialLayerSection();
        if (initialLayerSection == null) {
            return;
        }
        Pointer currentSection = initialLayerSection.get();
        while (currentSection.isNonNull()) {
            Pointer fixupTable = currentSection.readWord(ImageLayerSection.getEntryOffset(FIXUP_TABLE));
            if (fixupTable.isNonNull()) {
                patchForwardReferenceTable(fixupTable, exeModule);
            }
            currentSection = currentSection.readWord(ImageLayerSection.getEntryOffset(NEXT_SECTION));
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void patchForwardReferenceTable(Pointer fixupTable, HMODULE exeModule) {
        int numEntries = fixupTable.readInt(0);
        if (numEntries == 0) {
            return;
        }

        int stringDataOffset = fixupTable.readInt(4);
        Pointer slotPtrs = fixupTable.add(8);
        Pointer nameOffsets = slotPtrs.add(Word.unsigned(numEntries).multiply(8));
        Pointer stringData = fixupTable.add(stringDataOffset);

        for (int i = 0; i < numEntries; i++) {
            Pointer slotAddr = slotPtrs.readWord(i * 8);
            int nameOff = nameOffsets.readInt(i * 4);
            CCharPointer symbolName = (CCharPointer) stringData.add(nameOff);

            PointerBase resolved = LibLoaderAPI.GetProcAddress(exeModule, symbolName);
            if (resolved.isNonNull()) {
                patchForwardReferenceSlot(slotAddr, resolved);
            } else {
                failGetProcAddress(symbolName);
            }
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void patchForwardReferenceSlot(Pointer slotAddr, PointerBase resolved) {
        /*
         * Forward-reference fixups can point into read-only data sections or executable code
         * sections. If the page is not already writable, make it writable only for the duration of
         * the patch while preserving execute permission for executable pages.
         */
        int originalProtection = queryPageProtection(slotAddr);
        boolean executablePage = isExecutablePageProtection(originalProtection);
        boolean writeablePage = isWritablePageProtection(originalProtection);
        CIntPointer oldProtect = StackValue.get(CIntPointer.class);
        if (!writeablePage) {
            changePageProtection(slotAddr, writablePageProtectionFor(originalProtection), oldProtect, MSG_VIRTUAL_PROTECT_FAILED.get());
        }

        slotAddr.writeWord(0, resolved);
        if (!writeablePage) {
            changePageProtection(slotAddr, originalProtection, oldProtect, MSG_VIRTUAL_PROTECT_RESTORE_FAILED.get());
        }

        if (executablePage) {
            CodeSynchronizationOperations.clearCache(slotAddr.rawValue(), Long.BYTES);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int queryPageProtection(Pointer slotAddr) {
        MemoryAPI.MEMORY_BASIC_INFORMATION memoryInfo = StackValue.get(MemoryAPI.MEMORY_BASIC_INFORMATION.class);
        if (MemoryAPI.VirtualQuery(slotAddr, memoryInfo, SizeOf.unsigned(MemoryAPI.MEMORY_BASIC_INFORMATION.class)).equal(0)) {
            CEntryPointActions.failFatally(WinBase.GetLastError(), MSG_VIRTUAL_QUERY_FAILED.get());
        }
        return memoryInfo.Protect();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void changePageProtection(Pointer slotAddr, int newProtection, CIntPointer oldProtection, CCharPointer failureMessage) {
        if (MemoryAPI.VirtualProtect(slotAddr, Word.unsigned(Long.BYTES), newProtection, oldProtection) == 0) {
            CEntryPointActions.failFatally(WinBase.GetLastError(), failureMessage);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int writablePageProtectionFor(int protection) {
        return isExecutablePageProtection(protection) ? MemoryAPI.PAGE_EXECUTE_READWRITE() : MemoryAPI.PAGE_READWRITE();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isWritablePageProtection(int protection) {
        return protection == MemoryAPI.PAGE_READWRITE() || protection == MemoryAPI.PAGE_WRITECOPY() || protection == MemoryAPI.PAGE_EXECUTE_READWRITE() ||
                        protection == MemoryAPI.PAGE_EXECUTE_WRITECOPY();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isExecutablePageProtection(int protection) {
        return protection == MemoryAPI.PAGE_EXECUTE() || protection == MemoryAPI.PAGE_EXECUTE_READ() || protection == MemoryAPI.PAGE_EXECUTE_READWRITE() ||
                        protection == MemoryAPI.PAGE_EXECUTE_WRITECOPY();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void failGetProcAddress(CCharPointer symbolName) {
        HANDLE stderr = FileAPI.NoTransition.GetStdHandle(FileAPI.STD_ERROR_HANDLE());
        WindowsUtils.writeUninterruptibly(stderr, MSG_GET_PROC_FAILED_PREFIX.get(), WindowsLibC.strlen(MSG_GET_PROC_FAILED_PREFIX.get()));
        WindowsUtils.writeUninterruptibly(stderr, symbolName, WindowsLibC.strlen(symbolName));
        WindowsUtils.writeUninterruptibly(stderr, MSG_NEWLINE.get(), WindowsLibC.strlen(MSG_NEWLINE.get()));
        CEntryPointActions.failFatally(47827, MSG_GET_PROC_FAILED.get());
    }
}
