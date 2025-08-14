package com.oracle.svm.core.debug.gdb;

import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;

import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.code.InstalledCodeObserver;

@RawStructure
public interface GdbJitHandle extends InstalledCodeObserver.InstalledCodeObserverHandle {
    int INITIALIZED = 0;
    int ACTIVATED = 1;
    int RELEASED = 2;

    @RawField
    GdbJitInterface.JITCodeEntry getRawHandle();

    @RawField
    void setRawHandle(GdbJitInterface.JITCodeEntry value);

    @RawField
    NonmovableArray<Byte> getDebugInfoData();

    @RawField
    void setDebugInfoData(NonmovableArray<Byte> data);

    @RawField
    int getState();

    @RawField
    void setState(int value);
}
