package com.oracle.objectfile.debuginfo;
import java.util.List;

// class defining interfaces used to allow a native image
// to communicate details of types, code and data to
// the underlying object file so that the object file
// can insert appropriate debug info
public interface DebugInfoProvider {
    // access details of a specific type
    interface DebugTypeInfo {
    }

    // access details of a specific compiled method
    interface DebugCodeInfo {
        String fileName();
        String className();
        String methodName();
        int addressLo();
        int addressHi();
        int line();
        DebugLineInfoProvider lineInfoProvider();
        String paramNames();
        String returnTypeName();
        int getFrameSize();
        List<DebugFrameSizeChange> getFrameSizeChanges();
    }

    // access details of a specific heap object
    interface DebugDataInfo {
    }

    // access details of a specific outer or inlined method at a given line number
    interface DebugLineInfo {
        String fileName();
        String className();
        String methodName();
        int addressLo();
        int addressHi();
        int line();
    }

    interface DebugFrameSizeChange
    {
        enum  Type {EXTEND, CONTRACT};
        int getOffset();
        DebugFrameSizeChange.Type getType();
    }

    // convenience interface defining iterator type
    interface DebugTypeInfoProvider extends Iterable<DebugTypeInfo> {
    }

    // convenience interface defining iterator type
    interface DebugCodeInfoProvider extends Iterable<DebugCodeInfo> {
    }

    // convenience interface defining iterator type
    interface DebugLineInfoProvider extends Iterable<DebugLineInfo>{
    }

    // convenience interface defining iterator type
    interface DebugDataInfoProvider extends Iterable<DebugDataInfo> {
    }

    DebugTypeInfoProvider typeInfoProvider();
    DebugCodeInfoProvider codeInfoProvider();
    DebugDataInfoProvider dataInfoProvider();
}
