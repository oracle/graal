/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.auximage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.JavaMemoryUtil;
import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.CodeInfoTether;
import com.oracle.svm.core.code.RuntimeCodeInfoAccess;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.code.UntetheredCodeInfoAccess;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.heap.CodeReferenceMapDecoder;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.UnsignedUtils;
import com.oracle.truffle.compiler.OptimizedAssumptionDependency;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.JavaKind;

/** Representation of runtime-compiled code that can be reinstalled in the auxiliary image. */
abstract class PersistedRuntimeCode {

    final SubstrateInstalledCode installedCode;

    PersistedRuntimeCode(SubstrateInstalledCode installedCode) {
        this.installedCode = installedCode;
    }

    static PersistedRuntimeCode create(SubstrateInstalledCode code, AuxiliaryImageObjectReplacer.Access access) {
        PersistedRuntimeCode persisted = null;
        if (code.isAlive()) {
            long ip = code.getEntryPoint();
            persisted = ValidPersistedRuntimeCode.create0(code, ip, access); // can return null
        }
        if (persisted == null) {
            return new InvalidPersistedRuntimeCode(code);
        }
        return persisted;
    }

    abstract boolean isValid();

    @Fold
    static boolean isSupportedInCurrentImage() {
        return RuntimeCompilation.isEnabled();
    }
}

/** Collects reachable runtime-compiled code and prepares it for persisting. */
final class PersistedRuntimeCodeReplacer implements AuxiliaryImageObjectReplacer {
    private final PersistedRuntimeCode[] discoveredSentinel;
    private final List<PersistedRuntimeCode> discovered = new ArrayList<>();

    private final AuxiliaryImageCodeObserver[] observersSentinel;
    private final List<AuxiliaryImageCodeObserver> observers = new ArrayList<>();

    PersistedRuntimeCodeReplacer(AuxiliaryImageMetadata metaObj) {
        this.discoveredSentinel = metaObj.code;
        this.observersSentinel = metaObj.codeObservers;
    }

    @Override
    public Object replace(Object obj, Access access) {
        if (obj instanceof SubstrateInstalledCode) {
            if (!PersistedRuntimeCode.isSupportedInCurrentImage()) {
                throw new UnsupportedOperationException("Installed code not supported: runtime compilation not available");
            }
            SubstrateInstalledCode code = (SubstrateInstalledCode) obj;
            PersistedRuntimeCode persisted = ValidPersistedRuntimeCode.create(code, access);
            ((AuxiliaryImagePersistence.InternalReplacersAccess) access).addObject(persisted, code, false);
            discovered.add(persisted);
        } else if (obj instanceof InstalledCode) {
            throw new UnsupportedOperationException("Not supported: object subclassing InstalledCode, but not implementing SubstrateInstalledCode");
        }
        if (obj instanceof AuxiliaryImageCodeObserver) {
            observers.add((AuxiliaryImageCodeObserver) obj);
        }
        return obj;
    }

    @Override
    public void epilogue(EpilogueAccess access) {
        // The array determines the order in which code will be reinstalled. Place
        // Truffle code at the end so that any adapter code will be installed first.
        discovered.sort(Comparator.comparing(c -> (c.installedCode instanceof OptimizedAssumptionDependency) ? 1 : 0));
        access.replaceLate(discoveredSentinel, discovered.toArray(new PersistedRuntimeCode[0]));

        access.replaceLate(observersSentinel, observers.toArray(new AuxiliaryImageCodeObserver[0]));
    }
}

/**
 * Representation of runtime code objects that are invalid at persist time and therefore do not need
 * to be reinstalled. However {@link SubstrateInstalledCode#clearAddress()} needs to be invoked for
 * all instances of {@link SubstrateInstalledCode} at installation time.
 */
final class InvalidPersistedRuntimeCode extends PersistedRuntimeCode {

    InvalidPersistedRuntimeCode(SubstrateInstalledCode installedCode) {
        super(installedCode);
    }

    @Override
    boolean isValid() {
        return false;
    }
}

/** Representation of runtime-compiled code that can be reinstalled in the auxiliary image. */
final class ValidPersistedRuntimeCode extends PersistedRuntimeCode {

    @Uninterruptible(reason = "Tethering CodeInfo.")
    static ValidPersistedRuntimeCode create0(SubstrateInstalledCode code, long ip, AuxiliaryImageObjectReplacer.Access access) {
        UntetheredCodeInfo untethered = CodeInfoTable.lookupCodeInfo(Word.pointer(ip));
        if (untethered.isNull() || !UntetheredCodeInfoAccess.isAlive(untethered)) {
            return null;
        }
        assert !UntetheredCodeInfoAccess.isAOTImageCode(untethered);
        Object tether = CodeInfoAccess.acquireTether(untethered);
        try {
            CodeInfo info = CodeInfoAccess.convert(untethered, tether);
            return create1(code, info, access);
        } finally {
            CodeInfoAccess.releaseTether(untethered, tether);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code, calls interruptible code.", calleeMustBe = false)
    private static ValidPersistedRuntimeCode create1(SubstrateInstalledCode code, CodeInfo info, AuxiliaryImageObjectReplacer.Access access) {
        return create2(code, info, access);
    }

    private static ValidPersistedRuntimeCode create2(SubstrateInstalledCode code, CodeInfo info, AuxiliaryImageObjectReplacer.Access access) {
        // Copy all arrays to the heap, we can directly use each array as a NonmovableArray when the
        // auxiliary image is loaded and we build CodeInfo structures
        String name = CodeInfoAccess.getName(info);
        SubstrateInstalledCode installedCode = RuntimeCodeInfoAccess.getInstalledCode(info);
        assert installedCode == code;
        SharedMethod method = (SharedMethod) installedCode.getMethod();
        int tier = CodeInfoAccess.getTier(info);
        byte[] stackReferenceMapEncoding = NonmovableArrays.heapCopyOfByteArray(CodeInfoAccess.getStackReferenceMapEncoding(info));
        byte[] codeInfoIndex = NonmovableArrays.heapCopyOfByteArray(CodeInfoAccess.getCodeInfoIndex(info));
        byte[] codeInfoEncodings = NonmovableArrays.heapCopyOfByteArray(CodeInfoAccess.getCodeInfoEncodings(info));
        int codeInfoIndexEntriesPerBlock = CodeInfoAccess.getCodeInfoIndexEntriesPerBlock(info);
        byte[] codeInfoDefaultFrameInfoIndexes = NonmovableArrays.heapCopyOfByteArray(CodeInfoAccess.getCodeInfoDefaultFrameInfoIndexes(info));
        byte[] frameInfoEncodings = NonmovableArrays.heapCopyOfByteArray(CodeInfoAccess.getFrameInfoEncodings(info));
        Object[] objectConstants = NonmovableArrays.heapCopyOfObjectArray(CodeInfoAccess.getObjectConstants(info));
        Class<?>[] classes = NonmovableArrays.heapCopyOfObjectArray(CodeInfoAccess.getClasses(info));
        String[] memberNames = NonmovableArrays.heapCopyOfObjectArray(CodeInfoAccess.getMemberNames(info));
        String[] otherStrings = NonmovableArrays.heapCopyOfObjectArray(CodeInfoAccess.getOtherStrings(info));
        byte[] methodTable = NonmovableArrays.heapCopyOfByteArray(CodeInfoAccess.getMethodTable(info));
        int methodTableFirstId = CodeInfoAccess.getMethodTableFirstId(info);
        int methodTableEntryCount = CodeInfoAccess.getMethodCount(info);
        byte[] codeConstantsReferenceMapEncoding = NonmovableArrays.heapCopyOfByteArray(RuntimeCodeInfoAccess.getCodeConstantsReferenceMapEncoding(info));
        long codeConstantsReferenceMapIndex = RuntimeCodeInfoAccess.getCodeConstantsReferenceMapIndex(info);
        int[] deoptimizationStartOffsets = NonmovableArrays.heapCopyOfIntArray(RuntimeCodeInfoAccess.getDeoptimizationStartOffsets(info));
        byte[] deoptimizationEncodings = NonmovableArrays.heapCopyOfByteArray(RuntimeCodeInfoAccess.getDeoptimizationEncodings(info));
        Object[] deoptimizationObjectConstants = NonmovableArrays.heapCopyOfObjectArray(RuntimeCodeInfoAccess.getDeoptimizationObjectConstants(info));
        int entryPointOffset = UnsignedUtils.safeToInt(CodeInfoAccess.getCodeEntryPointOffset(info));
        RuntimeCodeReferenceWalker walker = RuntimeCodeReferenceWalker.create(info);
        walker.register(access);

        return new ValidPersistedRuntimeCode(name, installedCode, method, tier, stackReferenceMapEncoding, codeInfoIndex, codeInfoEncodings, codeInfoIndexEntriesPerBlock,
                        codeInfoDefaultFrameInfoIndexes, frameInfoEncodings, objectConstants, classes, memberNames, methodTable, methodTableFirstId, methodTableEntryCount,
                        otherStrings, codeConstantsReferenceMapEncoding, codeConstantsReferenceMapIndex, deoptimizationStartOffsets, deoptimizationEncodings,
                        deoptimizationObjectConstants, walker.codeBytes, entryPointOffset, walker.dataOffset, walker.dataBytes);
    }

    final String name;
    final SharedMethod method;
    final int tier;
    final byte[] stackReferenceMapEncoding;
    final byte[] codeInfoIndex;
    final byte[] codeInfoEncodings;
    final int codeInfoIndexEntriesPerBlock;
    final byte[] codeInfoDefaultFrameInfoIndexes;
    final byte[] frameInfoEncodings;
    final Object[] objectConstants;
    final Class<?>[] classes;
    final String[] memberNames;
    final byte[] methodTable;
    final int methodTableFirstId;
    final int methodTableEntryCount;
    final String[] otherStrings;
    final byte[] codeConstantsReferenceMapEncoding;
    final long codeConstantsReferenceMapIndex;
    final int[] deoptimizationStartOffsets;
    final byte[] deoptimizationEncodings;
    final Object[] deoptimizationObjectConstants;
    final byte[] codeBytes;
    final int entryPointOffset;
    final int dataOffset;
    final byte[] dataBytes;
    final Object[] preparedObjectData;

    private ValidPersistedRuntimeCode(String name, SubstrateInstalledCode installedCode, SharedMethod method, int tier, byte[] stackReferenceMapEncoding,
                    byte[] codeInfoIndex, byte[] codeInfoEncodings, int codeInfoIndexEntriesPerBlock, byte[] codeInfoDefaultFrameInfoIndexes,
                    byte[] frameInfoEncodings, Object[] objectConstants, Class<?>[] classes, String[] memberNames, byte[] methodTable, int methodTableFirstId, int methodTableEntryCount,
                    String[] otherStrings, byte[] codeConstantsReferenceMapEncoding, long codeConstantsReferenceMapIndex, int[] deoptimizationStartOffsets, byte[] deoptimizationEncodings,
                    Object[] deoptimizationObjectConstants, byte[] codeBytes, int entryPointOffset, int dataOffset, byte[] dataBytes) {
        super(installedCode);
        this.name = name;
        this.method = method;
        this.tier = tier;
        this.stackReferenceMapEncoding = stackReferenceMapEncoding;
        this.codeInfoIndex = codeInfoIndex;
        this.codeInfoEncodings = codeInfoEncodings;
        this.codeInfoIndexEntriesPerBlock = codeInfoIndexEntriesPerBlock;
        this.codeInfoDefaultFrameInfoIndexes = codeInfoDefaultFrameInfoIndexes;
        this.frameInfoEncodings = frameInfoEncodings;
        this.objectConstants = objectConstants;
        this.classes = classes;
        this.memberNames = memberNames;
        this.methodTable = methodTable;
        this.methodTableFirstId = methodTableFirstId;
        this.methodTableEntryCount = methodTableEntryCount;
        this.otherStrings = otherStrings;
        this.codeConstantsReferenceMapEncoding = codeConstantsReferenceMapEncoding;
        this.codeConstantsReferenceMapIndex = codeConstantsReferenceMapIndex;
        this.deoptimizationStartOffsets = deoptimizationStartOffsets;
        this.deoptimizationEncodings = deoptimizationEncodings;
        this.deoptimizationObjectConstants = deoptimizationObjectConstants;
        this.codeBytes = codeBytes;
        this.entryPointOffset = entryPointOffset;
        this.dataOffset = dataOffset;
        this.dataBytes = dataBytes;

        // Preallocate an acquired tether object on the auxiliary image heap so {@link CodeInfo}
        // references only image heap objects and the GC does not need to visit at them.
        CodeInfoTether tether = new CodeInfoTether(true);

        this.preparedObjectData = RuntimeCodeInfoAccess.prepareHeapObjectData(tether, name, installedCode);
    }

    @Override
    boolean isValid() {
        return true;
    }

    int alignedCodeAndDataSize() {
        UnsignedWord granularity = CommittedMemoryProvider.get().getGranularity();
        return UnsignedUtils.safeToInt(UnsignedUtils.roundUp(Word.unsigned(dataOffset + dataBytes.length), granularity));
    }

}

/**
 * Walks the byte arrays of code and data and the references they contain, as captured by
 * {@link CodeReferencesGatherer}, so that they will be discovered during object traversal and
 * patched for the object locations in the auxiliary image.
 */
final class RuntimeCodeReferenceWalker implements ObjectReferencesWalker {

    static RuntimeCodeReferenceWalker create(CodeInfo info) {
        CodeReferencesGatherer gathered = CodeReferencesGatherer.gather(info);
        return new RuntimeCodeReferenceWalker(gathered.codeBytes, gathered.dataOffset, gathered.dataBytes, gathered.encodedReferenceOffsets, gathered.referenceTargets);
    }

    static int encodeOffset(int offset, boolean compressed) {
        assert offset >= 0;
        return compressed ? -offset : offset;
    }

    static int decodeOffset(int encodedOffset) {
        return encodedOffset < 0 ? -encodedOffset : encodedOffset;
    }

    static boolean isCompressedAtOffset(int encodedOffset) {
        return encodedOffset < 0;
    }

    final byte[] codeBytes;
    final int dataOffset;
    final byte[] dataBytes;
    final int[] encodedOffsets;
    final Object[] targets;

    RuntimeCodeReferenceWalker(byte[] codeBytes, int dataOffset, byte[] dataBytes, int[] encodedOffsets, Object[] targets) {
        this.codeBytes = codeBytes;
        this.dataOffset = dataOffset;
        this.dataBytes = dataBytes;
        this.encodedOffsets = encodedOffsets;
        this.targets = targets;

        assert encodedOffsets.length == targets.length;
        assert codeBytes.length <= dataOffset;
        for (int encodedOffset : encodedOffsets) {
            int offset = decodeOffset(encodedOffset);
            assert (offset >= 0 && offset < codeBytes.length) || (offset >= dataOffset && offset - dataOffset < dataBytes.length);
        }
    }

    public void register(AuxiliaryImageObjectReplacer.Access access) {
        AuxiliaryImagePersistence.InternalReplacersAccess impl = (AuxiliaryImagePersistence.InternalReplacersAccess) access;
        impl.registerCodeWalker(codeBytes, this);
        impl.registerCodeWalker(dataBytes, this);
    }

    @Override
    public void walkReferencesOf(Object obj, ObjectReferencesWalkerVisitor visitor) {
        assert obj == codeBytes || obj == dataBytes;
        byte[] array = (byte[]) obj;
        for (int i = 0; i < encodedOffsets.length; i++) {
            int offset = decodeOffset(encodedOffsets[i]);
            if (array == codeBytes) {
                if (offset >= dataOffset) {
                    continue;
                }
            } else {
                if (offset < dataOffset) {
                    continue;
                }
                offset -= dataOffset;
            }
            assert offset >= 0 && offset < array.length;
            visitor.visitReferenceInCode(array, getByteArrayBaseOffset() + offset, isCompressedAtOffset(encodedOffsets[i]), targets[i]);
        }
    }

    @Fold
    protected static int getByteArrayBaseOffset() {
        return ObjectLayout.singleton().getArrayBaseOffset(JavaKind.Byte);
    }
}

/**
 * Captures a consistent snapshot of the memory comprising the compiled code and its data section
 * and the object references that it contains.
 */
final class CodeReferencesGatherer implements ObjectReferenceVisitor {
    static CodeReferencesGatherer gather(CodeInfo info) {
        return new CodeReferencesGatherer(info).gather();
    }

    private final CodeInfo info;

    private Pointer codeStart;
    byte[] codeBytes;
    byte[] dataBytes;
    int dataOffset;

    int referenceCount = 0;
    int[] encodedReferenceOffsets;
    Object[] referenceTargets;

    private CodeReferencesGatherer(CodeInfo info) {
        this.info = info;
    }

    private CodeReferencesGatherer gather() {
        int codeSize = UnsignedUtils.safeToInt(CodeInfoAccess.getCodeSize(info));
        int dataSize = UnsignedUtils.safeToInt(CodeInfoAccess.getDataSize(info));
        codeBytes = new byte[codeSize];
        dataBytes = new byte[dataSize];

        int estimate = UnsignedUtils.safeToInt(CodeInfoAccess.getCodeSize(info)
                        .unsignedDivide(SubstrateTarget.getWordSize()));
        gather0(estimate);
        if (referenceCount > estimate) {
            int count = referenceCount;
            gather0(count);
            assert referenceCount == count;
        } else if (referenceCount < estimate) {
            encodedReferenceOffsets = Arrays.copyOf(encodedReferenceOffsets, referenceCount);
            referenceTargets = Arrays.copyOf(referenceTargets, referenceCount);
        }
        return this;
    }

    private void gather0(int capacity) {
        this.encodedReferenceOffsets = new int[capacity];
        this.referenceTargets = new Object[capacity];
        gather1();
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Prevent garbage collection.")
    private void gather1() {
        assert areCodeConstantsLive(info) : "Must remain live";
        assert codeBytes.length == UnsignedUtils.safeToInt(CodeInfoAccess.getCodeSize(info)) &&
                        dataBytes.length == UnsignedUtils.safeToInt(CodeInfoAccess.getDataSize(info)) : "Must remain unchanged";

        codeStart = (Pointer) CodeInfoAccess.getCodeStart(info);

        Pointer codeBytesStart = Word.objectToUntrackedPointer(codeBytes).add(RuntimeCodeReferenceWalker.getByteArrayBaseOffset());
        JavaMemoryUtil.copy(codeStart, codeBytesStart, Word.unsigned(codeBytes.length));

        dataOffset = UnsignedUtils.safeToInt(CodeInfoAccess.getDataOffset(info));
        Pointer dataBytesStart = Word.objectToUntrackedPointer(dataBytes).add(RuntimeCodeReferenceWalker.getByteArrayBaseOffset());
        JavaMemoryUtil.copy(codeStart.add(dataOffset), dataBytesStart, Word.unsigned(dataBytes.length));

        CodeReferenceMapDecoder.walkOffsetsFromPointer(CodeInfoAccess.getCodeStart(info), RuntimeCodeInfoAccess.getCodeConstantsReferenceMapEncoding(info),
                        RuntimeCodeInfoAccess.getCodeConstantsReferenceMapIndex(info), this, null);

        codeStart = Word.nullPointer();
    }

    @Uninterruptible(reason = "Calls uninterruptible callee.")
    private static boolean areCodeConstantsLive(CodeInfo info) {
        return UntetheredCodeInfoAccess.isAlive(info);
    }

    @Override
    public void visitObjectReferences(Pointer firstObjRef, boolean compressed, int referenceSize, Object holderObject, int count) {
        Pointer pos = firstObjRef;
        Pointer end = firstObjRef.add(Word.unsigned(count).multiply(referenceSize));
        while (pos.belowThan(end)) {
            visitObjectReference(pos, compressed);
            pos = pos.add(referenceSize);
        }
    }

    private void visitObjectReference(Pointer objRef, boolean compressed) {
        if (referenceCount < encodedReferenceOffsets.length) {
            int offset = UnsignedUtils.safeToInt(objRef.subtract(codeStart));
            encodedReferenceOffsets[referenceCount] = RuntimeCodeReferenceWalker.encodeOffset(offset, compressed);
            referenceTargets[referenceCount] = ReferenceAccess.singleton().readObjectAt(objRef, compressed);
        }
        referenceCount++;
    }
}
