package com.oracle.svm.core.genscavenge.remset;

import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.GreyToBlackObjectVisitor;
import com.oracle.svm.core.genscavenge.Space;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;

import jdk.vm.ci.meta.MetaAccessProvider;

public interface RememberedSet {
    public static RememberedSet get() {
        return ImageSingletons.lookup(RememberedSet.class);
    }

    public BarrierSet createBarrierSet(MetaAccessProvider metaAccess);

    public UnsignedWord getHeaderSizeOfAlignedChunk();

    public UnsignedWord getHeaderSizeOfUnalignedChunk();

    public void enableRememberedSetForObject(AlignedHeader chunk, Object obj);

    public void enableRememberedSetForChunk(AlignedHeader chunk);

    public void enableRememberedSetForChunk(UnalignedHeader chunk);

    @AlwaysInline("GC performance")
    public boolean hasRememberedSet(UnsignedWord header);

    @AlwaysInline("GC performance")
    public void dirtyCardForAlignedObject(Object object, boolean verifyOnly);

    @AlwaysInline("GC performance")
    public void dirtyCardForUnalignedObject(Object object, boolean verifyOnly);

    @AlwaysInline("GC performance")
    public void dirtyCardIfNecessary(Object holderObject, Object object);

    public void cleanCardTable(AlignedHeader chunk);

    public void cleanCardTable(UnalignedHeader chunk);

    public void cleanCardTable(Space space);

    public boolean walkDirtyObjects(AlignedHeader chunk, GreyToBlackObjectVisitor visitor, boolean clean);

    public boolean walkDirtyObjects(UnalignedHeader chunk, GreyToBlackObjectVisitor visitor, boolean clean);

    public boolean walkDirtyObjects(Space space, GreyToBlackObjectVisitor visitor, boolean clean);

    public boolean verify(AlignedHeader chunk);

    public boolean verify(UnalignedHeader chunk);

    public boolean verifyOnlyCleanCards(AlignedHeader chunk);

    public boolean verifyOnlyCleanCards(UnalignedHeader chunk);

    public boolean verifyDirtyCards(Space space);
}
