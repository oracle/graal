package com.oracle.svm.core.os;

import java.util.EnumSet;

import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

public abstract class VirtualMemoryCommittedMemoryProvider implements CommittedMemoryProvider {

    @Override
    public boolean protect(PointerBase start, UnsignedWord nbytes, EnumSet<Access> accessFlags) {
        int vmAccessBits = VirtualMemoryProvider.Access.NONE;
        if (accessFlags.contains(CommittedMemoryProvider.Access.READ)) {
            vmAccessBits |= VirtualMemoryProvider.Access.READ;
        }
        if (accessFlags.contains(CommittedMemoryProvider.Access.WRITE)) {
            vmAccessBits |= VirtualMemoryProvider.Access.WRITE;
        }
        if (accessFlags.contains(CommittedMemoryProvider.Access.EXECUTE)) {
            vmAccessBits |= VirtualMemoryProvider.Access.EXECUTE;
        }
        int success = VirtualMemoryProvider.get().protect(start, nbytes, vmAccessBits);
        return success == 0;
    }

}