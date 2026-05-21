/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Isolates.IMAGE_HEAP_BEGIN;
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_RELOCATABLE_BEGIN;
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_RELOCATABLE_END;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.FIXUP_TABLE;
import static com.oracle.svm.core.imagelayer.ImageLayerSection.SectionEntries.NEXT_SECTION;
import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.imagelayer.ImageLayerSection;
import com.oracle.svm.core.os.AbstractCopyingImageHeapProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider.Access;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.core.windows.headers.FileAPI;
import com.oracle.svm.core.windows.headers.LibLoaderAPI;
import com.oracle.svm.core.windows.headers.MemoryAPI;
import com.oracle.svm.core.windows.headers.WinBase;
import com.oracle.svm.core.windows.headers.WinBase.HANDLE;
import com.oracle.svm.core.windows.headers.WinBase.HMODULE;
import com.oracle.svm.core.windows.headers.WindowsLibC;
import com.oracle.svm.core.windows.headers.WindowsLibC.WCharPointer;
import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;
import com.oracle.svm.guest.staging.c.function.CEntryPointActions;
import com.oracle.svm.guest.staging.c.function.CEntryPointErrors;
import com.oracle.svm.shared.Uninterruptible;

import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.PauseNode;

/**
 * An image heap provider for Windows that creates image heaps that are copy-on-write clones of the
 * loaded image heap.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public class WindowsImageHeapProvider extends AbstractCopyingImageHeapProvider {

    private static final class ForwardSymbolPatchingState {
        static final Word UNINITIALIZED = Word.zero();
        static final Word IN_PROGRESS = Word.unsigned(1);
        static final Word SUCCESSFUL = Word.unsigned(2);
    }

    private static final CGlobalData<Word> FORWARD_SYMBOL_PATCHING_STATE = CGlobalDataFactory.createWord(ForwardSymbolPatchingState.UNINITIALIZED);

    @Override
    @Uninterruptible(reason = "Called during early isolate initialization.")
    public void prepareBeforeParsingIsolateArguments() {
        /*
         * On PE/COFF layered builds, forward symbol references in CGlobal data cannot be resolved
         * at link time. Patch them now, before argument parsing reads any CGlobal values.
         */
        patchForwardSymbolReferences();
    }

    private static final CGlobalData<CCharPointer> MSG_GET_MODULE_FAILED = CGlobalDataFactory.createCString("GetModuleHandleA(NULL) failed");
    private static final CGlobalData<CCharPointer> MSG_GET_PROC_FAILED = CGlobalDataFactory.createCString("GetProcAddress failed for layer symbol");
    private static final CGlobalData<CCharPointer> MSG_GET_PROC_FAILED_PREFIX = CGlobalDataFactory.createCString("GetProcAddress failed for layer symbol: ");
    private static final CGlobalData<CCharPointer> MSG_VIRTUAL_PROTECT_FAILED = CGlobalDataFactory.createCString("VirtualProtect failed for PE/COFF layer fixup");
    private static final CGlobalData<CCharPointer> MSG_NEWLINE = CGlobalDataFactory.createCString("\n");

    @Uninterruptible(reason = "Called during isolate initialization.")
    private static void patchForwardSymbolReferences() {
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
     * {@code GetProcAddress} to find the next layer's section symbol in the main executable module.
     */
    @Uninterruptible(reason = "Called during isolate initialization.")
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
    @Uninterruptible(reason = "Called during isolate initialization.")
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

    @Uninterruptible(reason = "Called during isolate initialization.")
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
                failGetProcAddress(0xBAD3, symbolName);
            }
        }
    }

    @Uninterruptible(reason = "Called during isolate initialization.")
    private static void patchForwardReferenceSlot(Pointer slotAddr, PointerBase resolved) {
        /*
         * Forward-reference fixups can point into read-only image sections. Make the page writable
         * before patching so Windows does not terminate with an access violation before we can
         * initialize the copied layered image heap.
         */
        CIntPointer oldProtect = StackValue.get(CIntPointer.class);
        if (MemoryAPI.VirtualProtect(slotAddr, Word.unsigned(Long.BYTES), MemoryAPI.PAGE_EXECUTE_READWRITE(), oldProtect) == 0) {
            CEntryPointActions.failFatally(WinBase.GetLastError(), MSG_VIRTUAL_PROTECT_FAILED.get());
        }
        slotAddr.writeWord(0, resolved);
        MemoryAPI.VirtualProtect(slotAddr, Word.unsigned(Long.BYTES), oldProtect.read(), oldProtect);
    }

    @Uninterruptible(reason = "Called during isolate initialization.")
    private static void failGetProcAddress(int code, CCharPointer symbolName) {
        HANDLE stderr = FileAPI.NoTransition.GetStdHandle(FileAPI.STD_ERROR_HANDLE());
        WindowsUtils.writeUninterruptibly(stderr, MSG_GET_PROC_FAILED_PREFIX.get(), WindowsLibC.strlen(MSG_GET_PROC_FAILED_PREFIX.get()));
        WindowsUtils.writeUninterruptibly(stderr, symbolName, WindowsLibC.strlen(symbolName));
        WindowsUtils.writeUninterruptibly(stderr, MSG_NEWLINE.get(), WindowsLibC.strlen(MSG_NEWLINE.get()));
        CEntryPointActions.failFatally(code, MSG_GET_PROC_FAILED.get());
    }

    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    protected int commitAndCopyMemory(Pointer loadedImageHeap, UnsignedWord imageHeapSize, Pointer newImageHeap) {
        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            /*
             * In layered builds, patchLayeredImageHeap() has already modified the loaded image
             * heaps in memory (e.g. cross-layer code pointer patches, heap reference patches, field
             * updates). We must copy from the loaded memory to preserve these patches. The
             * file-mapping optimization would map fresh from the on-disk image, losing the patches
             * applied to the writable heap sections.
             */
            return super.commitAndCopyMemory(loadedImageHeap, imageHeapSize, newImageHeap);
        }

        HANDLE imageHeapFileMapping = getImageHeapFileMapping();
        if (imageHeapFileMapping.isNull()) {
            /* Fall back to copying from memory. */
            return super.commitAndCopyMemory(loadedImageHeap, imageHeapSize, newImageHeap);
        }

        /* Map a copy-on-write view of the image heap. */
        if (VirtualMemoryProvider.get().mapFile(newImageHeap, imageHeapSize, imageHeapFileMapping, getImageHeapFileOffset(),
                        Access.READ | Access.WRITE).isNull()) {
            /* Fall back to copying from memory. */
            return super.commitAndCopyMemory(loadedImageHeap, imageHeapSize, newImageHeap);
        }

        /* Copy relocatable pages. */
        return copyMemory(IMAGE_HEAP_RELOCATABLE_BEGIN.get(), IMAGE_HEAP_RELOCATABLE_END.get().subtract(IMAGE_HEAP_RELOCATABLE_BEGIN.get()),
                        newImageHeap.add(IMAGE_HEAP_RELOCATABLE_BEGIN.get().subtract(IMAGE_HEAP_BEGIN.get())));
    }

    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    protected int copyMemory(Pointer loadedImageHeap, UnsignedWord imageHeapSize, Pointer newImageHeap) {
        LibC.memcpy(newImageHeap, loadedImageHeap, imageHeapSize);
        return CEntryPointErrors.NO_ERROR;
    }

    /**
     * The pseudovariable __ImageBase provided by the MSVC linker represents the DOS header of the
     * module, which is what a Win32 module begins with. In other words, it is the base address of
     * the module and is the same as its HMODULE.
     */
    private static final CGlobalData<PointerBase> IMAGE_BASE = CGlobalDataFactory.forSymbol("__ImageBase");

    /** The cached handle of the image heap file mapping that closes when the process exits. */
    private static final CGlobalData<WordPointer> IMAGE_HEAP_FILE_MAPPING = CGlobalDataFactory.createWord(WindowsUtils.UNINITIALIZED_HANDLE);

    @Uninterruptible(reason = "Called during isolate initialization.")
    private static HANDLE getImageHeapFileMapping() {
        HANDLE value = IMAGE_HEAP_FILE_MAPPING.get().read();
        if (value.equal(WindowsUtils.UNINITIALIZED_HANDLE)) {
            HANDLE fileMapping = createImageHeapFileMapping();
            HANDLE existingMapping = (HANDLE) ((Pointer) IMAGE_HEAP_FILE_MAPPING.get()).compareAndSwapWord(0,
                            WindowsUtils.UNINITIALIZED_HANDLE, fileMapping, LocationIdentity.ANY_LOCATION);

            if (existingMapping.equal(WindowsUtils.UNINITIALIZED_HANDLE)) {
                value = fileMapping;
            } else {
                /* Another thread has already created the mapping, so use that. */
                value = existingMapping;
                WinBase.CloseHandle(fileMapping);
            }
        }
        return value;
    }

    /** Returns a handle to the image heap file mapping or the null pointer in case of an error. */
    @Uninterruptible(reason = "Called during isolate initialization.")
    private static HANDLE createImageHeapFileMapping() {
        /* Get the path of the file that contains the image heap. */
        WCharPointer filePath = StackValue.get(WinBase.MAX_PATH, WCharPointer.class);
        int length = LibLoaderAPI.GetModuleFileNameW((HMODULE) IMAGE_BASE.get(), filePath, WinBase.MAX_PATH);
        if (length == 0 || length == WinBase.MAX_PATH) {
            return Word.nullPointer();
        }

        /* Open the file for mapping. */
        HANDLE fileHandle = FileAPI.CreateFileW(filePath, FileAPI.GENERIC_READ(), FileAPI.FILE_SHARE_READ() | FileAPI.FILE_SHARE_DELETE(),
                        Word.nullPointer(), FileAPI.OPEN_EXISTING(), 0, Word.nullPointer());
        if (fileHandle.equal(WinBase.INVALID_HANDLE_VALUE())) {
            return Word.nullPointer();
        }

        /* Create the mapping and close the file. */
        HANDLE fileMapping = MemoryAPI.CreateFileMappingW(fileHandle, Word.nullPointer(), MemoryAPI.PAGE_READONLY(),
                        0, 0, Word.nullPointer());
        WinBase.CloseHandle(fileHandle);
        return fileMapping;
    }

    private static final CGlobalData<WordPointer> IMAGE_HEAP_FILE_OFFSET = CGlobalDataFactory.createWord();

    @Uninterruptible(reason = "Called during isolate initialization.")
    private static UnsignedWord getImageHeapFileOffset() {
        /*
         * The file offset of a relative virtual address (RVA) in a PE image can be determined by
         * inspecting the PE image headers.
         *
         * We do this using two helper functions from ntdll.dll: RtlImageNtHeader and
         * RtlAddressInSectionTable. Unfortunately, they are not publicly documented, but I think it
         * is unlikely that this will pose a problem (and it would definitely not go unnoticed).
         *
         * Alternatively, we could use equivalent functions from dbghelp.dll (ImageNtHeader and
         * ImageRvaToVa), but this would introduce an additional DLL dependency (which we would like
         * to avoid). Or we could even do it manually. The tricky part would be finding the header
         * of the section containing the RVA.
         */
        UnsignedWord value = IMAGE_HEAP_FILE_OFFSET.get().read();
        if (value.equal(0)) {
            /* Get the NT header of the module that contains the image heap. */
            PointerBase ntHeader = invokeRtlImageNtHeader(IMAGE_BASE.get());
            /* Compute the RVA of the image heap. */
            int rva = (int) (IMAGE_HEAP_BEGIN.get().rawValue() - IMAGE_BASE.get().rawValue());
            /* Get the file offset of the image heap. */
            value = invokeRtlAddressInSectionTable(ntHeader, rva);
            IMAGE_HEAP_FILE_OFFSET.get().write(value);
        }
        return value;
    }

    private static final CGlobalData<CCharPointer> NTDLL_DLL = CGlobalDataFactory.createCString("ntdll.dll");
    private static final CGlobalData<CCharPointer> RTL_IMAGE_NT_HEADER = CGlobalDataFactory.createCString("RtlImageNtHeader");
    private static final CGlobalData<CCharPointer> RTL_ADDRESS_IN_SECTION_TABLE = CGlobalDataFactory.createCString("RtlAddressInSectionTable");

    private static final int ERROR_BAD_EXE_FORMAT = 0xC1;

    /** Locates the IMAGE_NT_HEADERS structure in a PE image and returns a pointer to the data. */
    @Uninterruptible(reason = "Called during isolate initialization.")
    private static PointerBase invokeRtlImageNtHeader(PointerBase imageBase) {
        RtlImageNtHeader rtlImageNtHeader = WindowsUtils.getFunctionPointer(NTDLL_DLL.get(), RTL_IMAGE_NT_HEADER.get(), true);
        PointerBase ntHeader = rtlImageNtHeader.invoke(imageBase);
        if (ntHeader.isNull()) {
            CEntryPointActions.failFatally(ERROR_BAD_EXE_FORMAT, RTL_IMAGE_NT_HEADER.get());
        }
        return ntHeader;
    }

    private interface RtlImageNtHeader extends CFunctionPointer {
        @InvokeCFunctionPointer(transition = NO_TRANSITION)
        PointerBase invoke(PointerBase imageBase);
    }

    /**
     * Locates a relative virtual address (RVA) within the image header of a file that is mapped as
     * a file and returns the offset of the corresponding byte in the file.
     */
    @Uninterruptible(reason = "Called during isolate initialization.")
    private static UnsignedWord invokeRtlAddressInSectionTable(PointerBase ntHeader, int rva) {
        RtlAddressInSectionTable rtlAddressInSectionTable = WindowsUtils.getFunctionPointer(NTDLL_DLL.get(), RTL_ADDRESS_IN_SECTION_TABLE.get(), true);
        UnsignedWord offset = (UnsignedWord) rtlAddressInSectionTable.invoke(ntHeader, Word.nullPointer(), rva);
        if (offset.equal(0)) {
            CEntryPointActions.failFatally(ERROR_BAD_EXE_FORMAT, RTL_ADDRESS_IN_SECTION_TABLE.get());
        }
        return offset;
    }

    private interface RtlAddressInSectionTable extends CFunctionPointer {
        @InvokeCFunctionPointer(transition = NO_TRANSITION)
        PointerBase invoke(PointerBase ntHeader, PointerBase base, int rva);
    }
}
