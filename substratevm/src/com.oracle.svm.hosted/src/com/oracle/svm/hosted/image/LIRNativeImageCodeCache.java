/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.image;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.graalvm.collections.Pair;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.DeadlockWatchdog;
import com.oracle.svm.hosted.code.HostedDirectCallTrampolineSupport;
import com.oracle.svm.hosted.code.HostedImageHeapConstantPatch;
import com.oracle.svm.hosted.code.HostedPatcher;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectInfo;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.code.CompilationResult.CodeAnnotation;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.Indent;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.code.site.Reference;

public class LIRNativeImageCodeCache extends NativeImageCodeCache {

    private static final byte CODE_FILLER_BYTE = (byte) 0xCC;

    private final Map<HostedMethod, Map<HostedMethod, Integer>> trampolineMap;
    private final Map<HostedMethod, List<Pair<HostedMethod, Integer>>> orderedTrampolineMap;
    private final Map<HostedMethod, Integer> compilationPosition;

    private final TargetDescription target;

    @SuppressWarnings("this-escape")
    public LIRNativeImageCodeCache(Map<HostedMethod, CompilationResult> compilations, NativeImageHeap imageHeap) {
        super(compilations, imageHeap);
        target = ConfigurationValues.getTarget();
        trampolineMap = new HashMap<>();
        orderedTrampolineMap = new HashMap<>();

        compilationPosition = new HashMap<>();
        int compilationPos = 0;
        for (var entry : getOrderedCompilations()) {
            compilationPosition.put(entry.getLeft(), compilationPos);
            compilationPos++;
        }
    }

    @Override
    public int getCodeCacheSize() {
        return getCodeAreaSize();
    }

    @Override
    public int codeSizeFor(HostedMethod method) {
        int methodStart = method.getCodeAddressOffset();
        int methodEnd;

        if (orderedTrampolineMap.containsKey(method)) {
            List<Pair<HostedMethod, Integer>> trampolineList = orderedTrampolineMap.get(method);
            int lastTrampolineStart = trampolineList.get(trampolineList.size() - 1).getRight();
            methodEnd = computeNextMethodStart(lastTrampolineStart, HostedDirectCallTrampolineSupport.singleton().getTrampolineSize());
        } else {
            methodEnd = computeNextMethodStart(methodStart, compilationResultFor(method).getTargetCodeSize());
        }

        return methodEnd - methodStart;
    }

    private boolean verifyMethodLayout() {
        HostedDirectCallTrampolineSupport trampolineSupport = HostedDirectCallTrampolineSupport.singleton();
        int currentPos = 0;
        for (Pair<HostedMethod, CompilationResult> entry : getOrderedCompilations()) {
            HostedMethod method = entry.getLeft();
            CompilationResult compilation = entry.getRight();

            int methodStart = method.getCodeAddressOffset();
            assert currentPos == methodStart;

            currentPos += compilation.getTargetCodeSize();

            if (orderedTrampolineMap.containsKey(method)) {
                for (var trampoline : orderedTrampolineMap.get(method)) {
                    int trampolineOffset = trampoline.getRight();

                    currentPos = NumUtil.roundUp(currentPos, trampolineSupport.getTrampolineAlignment());
                    assert trampolineOffset == currentPos;

                    currentPos += trampolineSupport.getTrampolineSize();
                }
            }

            currentPos = computeNextMethodStart(currentPos, 0);
            int size = currentPos - method.getCodeAddressOffset();
            assert codeSizeFor(method) == size;
        }

        return true;
    }

    @SuppressWarnings({"try", "resource"})
    @Override
    public void layoutMethods(DebugContext debug, BigBang bb) {

        try (Indent indent = debug.logAndIndent("layout methods")) {
            // Assign initial location to all methods.
            HostedDirectCallTrampolineSupport trampolineSupport = HostedDirectCallTrampolineSupport.singleton();
            Map<HostedMethod, Integer> curOffsetMap = trampolineSupport.mayNeedTrampolines() ? new HashMap<>() : null;

            int curPos = 0;
            for (Pair<HostedMethod, CompilationResult> entry : getOrderedCompilations()) {
                HostedMethod method = entry.getLeft();
                CompilationResult compilation = entry.getRight();

                if (!trampolineSupport.mayNeedTrampolines()) {
                    method.setCodeAddressOffset(curPos);
                } else {
                    curOffsetMap.put(method, curPos);
                }
                curPos = computeNextMethodStart(curPos, compilation.getTargetCodeSize());
            }

            if (trampolineSupport.mayNeedTrampolines()) {

                // check for and add any needed trampolines
                addDirectCallTrampolines(curOffsetMap);

                // record final code address offsets and trampoline metadata
                for (Pair<HostedMethod, CompilationResult> pair : getOrderedCompilations()) {
                    HostedMethod method = pair.getLeft();
                    int methodStartOffset = curOffsetMap.get(method);
                    method.setCodeAddressOffset(methodStartOffset);
                    Map<HostedMethod, Integer> trampolines = trampolineMap.get(method);
                    if (trampolines.size() != 0) {
                        // assign an offset to each trampoline
                        List<Pair<HostedMethod, Integer>> sortedTrampolines = new ArrayList<>(trampolines.size());
                        int position = methodStartOffset + pair.getRight().getTargetCodeSize();
                        /*
                         * Need to have snapshot of trampoline key set since we update their
                         * positions.
                         */
                        for (HostedMethod callTarget : trampolines.keySet().toArray(HostedMethod.EMPTY_ARRAY)) {
                            position = NumUtil.roundUp(position, trampolineSupport.getTrampolineAlignment());
                            trampolines.put(callTarget, position);
                            sortedTrampolines.add(Pair.create(callTarget, position));
                            position += trampolineSupport.getTrampolineSize();
                        }
                        orderedTrampolineMap.put(method, sortedTrampolines);
                    }

                    DeadlockWatchdog.singleton().recordActivity();
                }
            }

            Pair<HostedMethod, CompilationResult> lastCompilation = getLastCompilation();
            HostedMethod lastMethod = lastCompilation.getLeft();

            // the total code size is the hypothetical start of the next method
            int totalSize;
            if (orderedTrampolineMap.containsKey(lastMethod)) {
                var trampolines = orderedTrampolineMap.get(lastMethod);
                int lastTrampolineStart = trampolines.get(trampolines.size() - 1).getRight();
                totalSize = computeNextMethodStart(lastTrampolineStart, trampolineSupport.getTrampolineSize());
            } else {
                totalSize = computeNextMethodStart(lastCompilation.getLeft().getCodeAddressOffset(), lastCompilation.getRight().getTargetCodeSize());
            }

            setCodeAreaSize(totalSize);

            assert verifyMethodLayout();

        }
    }

    private static int computeNextMethodStart(int current, int addend) {
        int result;
        try {
            result = NumUtil.roundUp(Math.addExact(current, addend), SubstrateOptions.codeAlignment());
        } catch (ArithmeticException e) {
            throw VMError.shouldNotReachHere("Code size is larger than 2GB");
        }

        return result;
    }

    /**
     * After the initial method layout, on some platforms some direct calls between methods may be
     * too far apart. When this happens a trampoline must be inserted to reach the call target.
     */
    @SuppressWarnings("resource")
    private void addDirectCallTrampolines(Map<HostedMethod, Integer> curOffsetMap) {
        HostedDirectCallTrampolineSupport trampolineSupport = HostedDirectCallTrampolineSupport.singleton();

        boolean changed;
        do {
            int callerCompilationNum = 0;
            int curPos = 0;
            changed = false;
            for (Pair<HostedMethod, CompilationResult> entry : getOrderedCompilations()) {
                HostedMethod caller = entry.getLeft();
                CompilationResult compilation = entry.getRight();

                int originalStart = curOffsetMap.get(caller);
                int newStart = curPos;
                curOffsetMap.put(caller, newStart);

                // move curPos to the end of the method code
                curPos += compilation.getTargetCodeSize();
                int newEnd = curPos;

                Map<HostedMethod, Integer> trampolines = trampolineMap.computeIfAbsent(caller, k -> new HashMap<>());

                // update curPos to account for current trampolines
                for (int j = 0; j < trampolines.size(); j++) {
                    curPos = NumUtil.roundUp(curPos, trampolineSupport.getTrampolineAlignment());
                    curPos += trampolineSupport.getTrampolineSize();
                }
                for (Infopoint infopoint : compilation.getInfopoints()) {
                    if (infopoint instanceof Call && ((Call) infopoint).direct) {
                        Call call = (Call) infopoint;
                        HostedMethod callee = (HostedMethod) call.target;

                        if (trampolines.containsKey(callee)) {
                            /*
                             * If trampoline already exists, then don't have to do anything.
                             */
                            continue;
                        }

                        int calleeStart = curOffsetMap.get(callee);

                        int maxDistance;
                        int calleeCompilationNum = compilationPosition.get(callee);
                        if (calleeCompilationNum < callerCompilationNum) {
                            /*
                             * Callee is before caller.
                             *
                             * both have already been updated; compare against new start.
                             */
                            maxDistance = newEnd - calleeStart;
                        } else {
                            /*
                             * Caller is before callee.
                             *
                             * callee's method start hasn't been updated yet; compare against
                             * original start.
                             */
                            maxDistance = calleeStart - originalStart;
                        }

                        if (maxDistance > trampolineSupport.getMaxCallDistance()) {
                            // need to add another trampoline
                            changed = true;
                            trampolines.put(callee, 0);
                            curPos = NumUtil.roundUp(curPos, trampolineSupport.getTrampolineAlignment());
                            curPos += trampolineSupport.getTrampolineSize();
                        }
                    }
                }
                // align curPos for start of next method
                curPos = computeNextMethodStart(curPos, 0);
                callerCompilationNum++;
            }

            DeadlockWatchdog.singleton().recordActivity();
        } while (changed);
    }

    /**
     * Patch references from code to other code and constant data. Generate relocation information
     * in the process. More patching can be done, and correspondingly fewer relocation records
     * generated, if the caller passes a non-null rodataDisplacementFromText.
     *
     * @param relocs a relocation map
     */
    @Override
    @SuppressWarnings("try")
    public void patchMethods(DebugContext debug, RelocatableBuffer relocs, ObjectFile objectFile) {

        /*
         * Patch instructions which reference code or data by address.
         *
         * Note that the image we write happens to be naturally position-independent on x86-64,
         * since both code and data references are PC-relative.
         *
         * So not only can we definitively fix up the all code--code and code--data references as
         * soon as we have assigned all our addresses, but also, the resulting blob can be loaded at
         * any address without relocation (and therefore potentially shared between many processes).
         * (This is true for shared library output only, not relocatable code.)
         *
         * These properties may change. Once the code includes references to external symbols, we
         * will either no longer have a position-independent image (if we stick with the current
         * load-time relocation approach) or will require us to implement a PLT (for
         * {code,data}->code references) and GOT (for code->data references).
         *
         * Splitting text from rodata is straightforward when generating shared libraries or
         * executables, since even in the case where the loader has to pick a different virtual
         * address range than the one preassigned in the object file, it will preserve the offsets
         * between the vaddrs. So, if we're generating a shared library or executable (i.e.
         * something with vaddrs), we always know the offset of our data from our code (and
         * vice-versa). BUT if we're generating relocatable code, we don't know that yet. In that
         * case, the caller will pass a null rodataDisplacecmentFromText, and we behave accordingly
         * by generating extra relocation records.
         */

        // in each compilation result...
        for (Pair<HostedMethod, CompilationResult> pair : getOrderedCompilations()) {

            /* Ensure a full watchdog interval is available per method */
            DeadlockWatchdog.singleton().recordActivity();

            HostedMethod method = pair.getLeft();
            CompilationResult compilation = pair.getRight();

            // the codecache-relative offset of the compilation
            int compStart = method.getCodeAddressOffset();

            // Build an index of PatchingAnnotations
            Map<Integer, HostedPatcher> patches = new HashMap<>();
            ByteBuffer targetCode = null;
            for (CodeAnnotation codeAnnotation : compilation.getCodeAnnotations()) {
                if (codeAnnotation instanceof HostedPatcher) {
                    HostedPatcher priorValue = patches.put(codeAnnotation.getPosition(), (HostedPatcher) codeAnnotation);
                    VMError.guarantee(priorValue == null, "Registering two patchers for same position.");

                } else if (codeAnnotation instanceof HostedImageHeapConstantPatch) {
                    HostedImageHeapConstantPatch patch = (HostedImageHeapConstantPatch) codeAnnotation;

                    ObjectInfo objectInfo = imageHeap.getConstantInfo(patch.constant);
                    long objectAddress = objectInfo.getOffset();

                    if (targetCode == null) {
                        targetCode = ByteBuffer.wrap(compilation.getTargetCode()).order(target.arch.getByteOrder());
                    }
                    int originalValue = targetCode.getInt(patch.getPosition());
                    long newValue = originalValue + objectAddress;
                    VMError.guarantee(NumUtil.isInt(newValue), "Image heap size is limited to 2 GByte");
                    targetCode.putInt(patch.getPosition(), (int) newValue);
                }
            }

            // ... patch direct call sites.
            Map<HostedMethod, Integer> trampolineOffsetMap = trampolineMap.get(method);
            int patchesHandled = 0;
            HashSet<Integer> patchedOffsets = new HashSet<>();
            for (Infopoint infopoint : compilation.getInfopoints()) {
                if (infopoint instanceof Call && ((Call) infopoint).direct) {
                    Call call = (Call) infopoint;

                    // NOTE that for the moment, we don't make static calls to external
                    // (e.g. native) functions. So every static call site has a target
                    // which is also in the code cache (a.k.a. a section-local call).
                    // This will change, and we will have to case-split here... but not yet.
                    HostedMethod callTarget = (HostedMethod) call.target;
                    VMError.guarantee(!callTarget.wrapped.isInBaseLayer(), "Unexpected direct call to base layer method %s. These calls are currently lowered to indirect calls.", callTarget);
                    int callTargetStart = callTarget.getCodeAddressOffset();
                    if (trampolineOffsetMap != null && trampolineOffsetMap.containsKey(callTarget)) {
                        callTargetStart = trampolineOffsetMap.get(callTarget);
                    }

                    // Patch a PC-relative call.
                    // This code handles the case of section-local calls only.
                    int pcDisplacement = callTargetStart - (compStart + call.pcOffset);
                    patches.get(call.pcOffset).patch(compStart, pcDisplacement, compilation.getTargetCode());
                    boolean noPriorMatch = patchedOffsets.add(call.pcOffset);
                    VMError.guarantee(noPriorMatch, "Patching same offset twice.");
                    patchesHandled++;
                }
            }

            for (DataPatch dataPatch : compilation.getDataPatches()) {
                assert dataPatch.note == null : "Unexpected note: " + dataPatch.note;
                Reference ref = dataPatch.reference;
                var patcher = patches.get(dataPatch.pcOffset);
                /*
                 * Constants are (1) allocated offsets in a separate space, which can be emitted as
                 * read-only (.rodata) section, or (2) method pointers that are computed relative to
                 * the PC.
                 */
                patcher.relocate(ref, relocs, compStart);

                boolean noPriorMatch = patchedOffsets.add(dataPatch.pcOffset);
                VMError.guarantee(noPriorMatch, "Patching same offset twice.");
                patchesHandled++;
            }
            VMError.guarantee(patchesHandled == patches.size(), "Not all patches applied.");
            try (DebugContext.Scope ds = debug.scope("After Patching", method.asJavaMethod())) {
                debug.dump(DebugContext.BASIC_LEVEL, compilation, "After patching");
            } catch (Throwable e) {
                throw VMError.shouldNotReachHere(e);
            }
        }
    }

    @Override
    public void writeCode(RelocatableBuffer buffer) {
        ByteBuffer bufferBytes = buffer.getByteBuffer();
        int startPos = bufferBytes.position();
        /*
         * Compilation start offsets are relative to the beginning of the code cache (since the heap
         * size is not fixed at the time they are computed). This is just startPos, i.e. we start
         * emitting the code wherever the buffer is positioned when we're called.
         */
        for (Pair<HostedMethod, CompilationResult> compilationPair : getOrderedCompilations()) {
            HostedMethod method = compilationPair.getLeft();
            CompilationResult compilation = compilationPair.getRight();

            bufferBytes.position(startPos + method.getCodeAddressOffset());
            bufferBytes.put(compilation.getTargetCode(), 0, compilation.getTargetCodeSize());

            int curPos = bufferBytes.position();
            List<Pair<HostedMethod, Integer>> trampolines = orderedTrampolineMap.get(method);
            if (trampolines != null) {
                // need to write the trampoline info here
                HostedDirectCallTrampolineSupport trampolineSupport = HostedDirectCallTrampolineSupport.singleton();
                int trampolineSize = trampolineSupport.getTrampolineSize();
                for (Pair<HostedMethod, Integer> trampoline : trampolines) {
                    // align to start of trampoline
                    for (int i = curPos; i < NumUtil.roundUp(curPos, trampolineSupport.getTrampolineAlignment()); i++) {
                        bufferBytes.put(CODE_FILLER_BYTE);
                    }
                    curPos = bufferBytes.position();
                    assert curPos == trampoline.getRight();

                    byte[] trampolineCode = trampolineSupport.createTrampoline(target, trampoline.getLeft(), curPos);
                    assert trampolineCode.length == trampolineSize;

                    bufferBytes.put(trampolineCode, 0, trampolineSize);
                    curPos += trampolineSize;
                }
            }

            for (int i = curPos; i < NumUtil.roundUp(curPos, SubstrateOptions.codeAlignment()); i++) {
                bufferBytes.put(CODE_FILLER_BYTE);
            }
        }
        bufferBytes.position(startPos);
    }

    @Override
    public NativeTextSectionImpl getTextSectionImpl(RelocatableBuffer buffer, ObjectFile objectFile, NativeImageCodeCache codeCache) {
        return new NativeTextSectionImpl(buffer, objectFile, codeCache);
    }

    private static final class NativeTextSectionImpl extends NativeImage.NativeTextSectionImpl {
        private NativeTextSectionImpl(RelocatableBuffer buffer, ObjectFile objectFile, NativeImageCodeCache codeCache) {
            super(buffer, objectFile, codeCache);
        }

        @Override
        protected void defineMethodSymbol(String name, boolean global, ObjectFile.Element section, HostedMethod method, CompilationResult result) {
            final int size = result == null ? 0 : result.getTargetCodeSize();
            objectFile.createDefinedSymbol(name, section, method.getCodeAddressOffset(), size, true, global);
        }
    }

    @Override
    public List<ObjectFile.Symbol> getSymbols(ObjectFile objectFile) {
        return StreamSupport.stream(objectFile.getSymbolTable().spliterator(), false).collect(Collectors.toList());
    }
}
