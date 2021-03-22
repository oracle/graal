package com.oracle.svm.core.genscavenge.remset;

import java.util.List;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.GreyToBlackObjectVisitor;
import com.oracle.svm.core.genscavenge.Space;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.util.HostedByteBufferPointer;

import jdk.vm.ci.meta.MetaAccessProvider;

public interface RememberedSet {
    @Fold
    public static RememberedSet get() {
        return ImageSingletons.lookup(RememberedSet.class);
    }

    public BarrierSet createBarrierSet(MetaAccessProvider metaAccess);

    public UnsignedWord getHeaderSizeOfAlignedChunk();

    public UnsignedWord getHeaderSizeOfUnalignedChunk();

    public void enableRememberedSetForChunk(AlignedHeader chunk);

    public void enableRememberedSetForChunk(UnalignedHeader chunk);

    public void enableRememberedSetForObject(AlignedHeader chunk, Object obj);

    @Platforms(Platform.HOSTED_ONLY.class)
    public void enableRememberedSetForAlignedChunk(HostedByteBufferPointer chunk, int chunkOffset, List<ImageHeapObject> objects);

    @Platforms(Platform.HOSTED_ONLY.class)
    public void enableRememberedSetForUnalignedChunk(HostedByteBufferPointer chunk);

    @AlwaysInline("GC performance")
    public boolean hasRememberedSet(UnsignedWord header);

    @AlwaysInline("GC performance")
    public void dirtyCardForAlignedObject(Object object, boolean verifyOnly);

    @AlwaysInline("GC performance")
    public void dirtyCardForUnalignedObject(Object object, boolean verifyOnly);

    @AlwaysInline("GC performance")
    public void dirtyCardIfNecessary(Object holderObject, Object object);

    public void walkDirtyObjects(AlignedHeader chunk, GreyToBlackObjectVisitor visitor);

    public void walkDirtyObjects(UnalignedHeader chunk, GreyToBlackObjectVisitor visitor);

    public void walkDirtyObjects(Space space, GreyToBlackObjectVisitor visitor);

    public boolean verify(AlignedHeader firstAlignedHeapChunk);

    public boolean verify(UnalignedHeader firstUnalignedHeapChunk);
}
