package com.oracle.svm.core.genscavenge.remset;

import java.util.List;

import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.GCImpl;
import com.oracle.svm.core.genscavenge.GreyToBlackObjectVisitor;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.HeapPolicy;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.Space;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.genscavenge.graal.SubstrateCardTableBarrierSet;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.util.HostedByteBufferPointer;

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
    @Platforms(Platform.HOSTED_ONLY.class)
    public void enableRememberedSetForAlignedChunk(HostedByteBufferPointer chunk, int chunkPosition, List<ImageHeapObject> objects) {
        AlignedChunkRememberedSet.enableRememberedSet(chunk, chunkPosition, objects);
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public void enableRememberedSetForUnalignedChunk(HostedByteBufferPointer chunk) {
        UnalignedChunkRememberedSet.enableRememberedSet(chunk);
    }

    @Override
    public void enableRememberedSetForChunk(AlignedHeader chunk) {
        AlignedChunkRememberedSet.enableRememberedSet(chunk);
    }

    @Override
    public void enableRememberedSetForChunk(UnalignedHeader chunk) {
        UnalignedChunkRememberedSet.enableRememberedSet(chunk);
    }

    @Override
    public void enableRememberedSetForObject(AlignedHeader chunk, Object obj) {
        AlignedChunkRememberedSet.enableRememberedSetForObject(chunk, obj);
    }

    @Override
    public void clearRememberedSet(AlignedHeader chunk) {
        AlignedChunkRememberedSet.clearRememberedSet(chunk);
    }

    @Override
    public void clearRememberedSet(UnalignedHeader chunk) {
        UnalignedChunkRememberedSet.clearRememberedSet(chunk);
    }

    @Override
    @AlwaysInline("GC performance")
    public boolean isRememberedSetEnabled(UnsignedWord header) {
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
        if (isRememberedSetEnabled(objectHeader)) {
            if (ObjectHeaderImpl.isAlignedObject(holderObject)) {
                AlignedChunkRememberedSet.dirtyCardForObject(holderObject, false);
            } else {
                assert ObjectHeaderImpl.isUnalignedObject(holderObject) : "sanity";
                UnalignedChunkRememberedSet.dirtyCardForObject(holderObject, false);
            }
        }
    }

    @Override
    public void walkDirtyObjects(AlignedHeader chunk, GreyToBlackObjectVisitor visitor) {
        AlignedChunkRememberedSet.walkDirtyObjects(chunk, visitor);
    }

    @Override
    public void walkDirtyObjects(UnalignedHeader chunk, GreyToBlackObjectVisitor visitor) {
        UnalignedChunkRememberedSet.walkDirtyObjects(chunk, visitor);
    }

    @Override
    public void walkDirtyObjects(Space space, GreyToBlackObjectVisitor visitor) {
        AlignedHeader aChunk = space.getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            walkDirtyObjects(aChunk, visitor);
            aChunk = HeapChunk.getNext(aChunk);
        }

        UnalignedHeader uChunk = space.getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            walkDirtyObjects(uChunk, visitor);
            uChunk = HeapChunk.getNext(uChunk);
        }
    }

    @Override
    public boolean verify(AlignedHeader firstAlignedHeapChunk) {
        boolean success = true;
        AlignedHeader aChunk = firstAlignedHeapChunk;
        while (aChunk.isNonNull()) {
            success &= AlignedChunkRememberedSet.verify(aChunk);
            aChunk = HeapChunk.getNext(aChunk);
        }
        return success;
    }

    @Override
    public boolean verify(UnalignedHeader firstUnalignedHeapChunk) {
        boolean success = true;
        UnalignedHeader uChunk = firstUnalignedHeapChunk;
        while (uChunk.isNonNull()) {
            success &= UnalignedChunkRememberedSet.verify(uChunk);
            uChunk = HeapChunk.getNext(uChunk);
        }
        return success;
    }
}
