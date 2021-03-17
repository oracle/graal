package com.oracle.svm.core.genscavenge.remset;

import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.GCImpl;
import com.oracle.svm.core.genscavenge.GreyToBlackObjectVisitor;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.HeapPolicy;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.Space;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.log.Log;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public class CardTableBasedRememberedSet implements RememberedSet {
    @Platforms(Platform.HOSTED_ONLY.class)
    public CardTableBasedRememberedSet() {
    }

    @Override
    public BarrierSet createBarrierSet(MetaAccessProvider metaAccess) {
        ResolvedJavaType objectArrayType = metaAccess.lookupJavaType(Object[].class);
        return new SubstrateCardTableBarrierSet(objectArrayType);
    }

    @Override
    public UnsignedWord getHeaderSizeOfAlignedChunk() {
        return AlignedChunkRememberedSet.getHeaderSize();
    }

    @Override
    public UnsignedWord getHeaderSizeOfUnalignedChunk() {
        return UnalignedChunkRememberedSet.getHeaderSize();
    }

    @Override
    public void enableRememberedSetForObject(AlignedHeader chunk, Object obj) {
        AlignedChunkRememberedSet.enableRememberedSetForObject(chunk, obj);
    }

    @Override
    public void enableRememberedSetForChunk(AlignedHeader chunk) {
        AlignedChunkRememberedSet.enableRememberedSetForChunk(chunk);
    }

    @Override
    public void enableRememberedSetForChunk(UnalignedHeader chunk) {
        UnalignedChunkRememberedSet.enableRememberedSetForChunk(chunk);
    }

    @Override
    @AlwaysInline("GC performance")
    public boolean hasRememberedSet(UnsignedWord header) {
        return ObjectHeaderImpl.hasRememberedSet(header);
    }

    @Override
    @AlwaysInline("GC performance")
    public void dirtyCardForAlignedObject(Object object, boolean verifyOnly) {
        AlignedChunkRememberedSet.dirtyCardForObject(object, verifyOnly);
    }

    @Override
    @AlwaysInline("GC performance")
    public void dirtyCardForUnalignedObject(Object object, boolean verifyOnly) {
        UnalignedChunkRememberedSet.dirtyCardForObject(object, verifyOnly);
    }

    @Override
    @AlwaysInline("GC performance")
    public void dirtyCardIfNecessary(Object holderObject, Object object) {
        if (HeapPolicy.getMaxSurvivorSpaces() == 0 || holderObject == null || object == null || GCImpl.getGCImpl().isCompleteCollection() ||
                        !HeapImpl.getHeapImpl().getYoungGeneration().contains(object)) {
            return;
        }

        UnsignedWord objectHeader = ObjectHeaderImpl.readHeaderFromObject(holderObject);
        if (ObjectHeaderImpl.hasRememberedSet(objectHeader)) {
            if (ObjectHeaderImpl.isAlignedObject(holderObject)) {
                AlignedChunkRememberedSet.dirtyCardForObject(holderObject, false);
            } else {
                assert ObjectHeaderImpl.isUnalignedObject(holderObject) : "sanity";
                UnalignedChunkRememberedSet.dirtyCardForObject(holderObject, false);
            }
        }
    }

    @Override
    public void cleanCardTable(AlignedHeader chunk) {
        AlignedChunkRememberedSet.cleanCardTable(chunk);
    }

    @Override
    public void cleanCardTable(UnalignedHeader chunk) {
        UnalignedChunkRememberedSet.cleanCardTable(chunk);
    }

    @Override
    public void cleanCardTable(Space space) {
        cleanAlignedHeapChunks(space);
        cleanUnalignedHeapChunks(space);
    }

    @Override
    public boolean walkDirtyObjects(AlignedHeader chunk, GreyToBlackObjectVisitor visitor, boolean clean) {
        return AlignedChunkRememberedSet.walkDirtyObjects(chunk, visitor, clean);
    }

    @Override
    public boolean walkDirtyObjects(UnalignedHeader chunk, GreyToBlackObjectVisitor visitor, boolean clean) {
        return UnalignedChunkRememberedSet.walkDirtyObjects(chunk, visitor, clean);
    }

    @Override
    public boolean walkDirtyObjects(Space space, GreyToBlackObjectVisitor visitor, boolean clean) {
        Log trace = Log.noopLog().string("[walkDirtyObjects:  space: ").string(space.getName()).string("  clean: ").bool(clean);
        AlignedHeapChunk.AlignedHeader aChunk = space.getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            trace.newline().string("  aChunk: ").hex(aChunk);
            if (!walkDirtyObjects(aChunk, visitor, clean)) {
                Log failureLog = Log.log().string("[Space.walkDirtyObjects:");
                failureLog.string("  aChunk.walkDirtyObjects fails").string("]").newline();
                return false;
            }
            aChunk = HeapChunk.getNext(aChunk);
        }
        UnalignedHeapChunk.UnalignedHeader uChunk = space.getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            trace.newline().string("  uChunk: ").hex(uChunk);
            if (!walkDirtyObjects(uChunk, visitor, clean)) {
                Log failureLog = Log.log().string("[Space.walkDirtyObjects:");
                failureLog.string("  uChunk.walkDirtyObjects fails").string("]").newline();
                return false;
            }
            uChunk = HeapChunk.getNext(uChunk);
        }
        trace.string("]").newline();
        return true;
    }

    private static void cleanAlignedHeapChunks(Space space) {
        Log trace = Log.noopLog().string("[Space.cleanRememberedSetAlignedHeapChunks:").string("  space: ").string(space.getName());
        AlignedHeapChunk.AlignedHeader aChunk = space.getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            trace.newline().string("  aChunk: ").hex(aChunk);
            AlignedChunkRememberedSet.cleanCardTable(aChunk);
            aChunk = HeapChunk.getNext(aChunk);
        }
        trace.string("]").newline();
    }

    private static void cleanUnalignedHeapChunks(Space space) {
        Log trace = Log.noopLog().string("[Space.cleanRememberedSetUnalignedHeapChunks:").string("  space: ").string(space.getName());
        UnalignedHeapChunk.UnalignedHeader uChunk = space.getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            trace.newline().string("  uChunk: ").hex(uChunk);
            UnalignedChunkRememberedSet.cleanCardTable(uChunk);
            uChunk = HeapChunk.getNext(uChunk);
        }
        trace.string("]").newline();
    }

    @Override
    public boolean verify(AlignedHeader chunk) {
        return AlignedChunkRememberedSet.verify(chunk);
    }

    @Override
    public boolean verify(UnalignedHeader chunk) {
        return UnalignedChunkRememberedSet.verify(chunk);
    }

    @Override
    public boolean verifyOnlyCleanCards(AlignedHeader chunk) {
        return AlignedChunkRememberedSet.verifyOnlyCleanCards(chunk);
    }

    @Override
    public boolean verifyOnlyCleanCards(UnalignedHeader chunk) {
        return UnalignedChunkRememberedSet.verifyOnlyCleanCards(chunk);
    }

    @Override
    public boolean verifyDirtyCards(Space space) {
        AlignedHeapChunk.AlignedHeader aChunk = space.getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            if (!verify(aChunk)) {
                return false;
            }
            aChunk = HeapChunk.getNext(aChunk);
        }
        return true;
    }
}
