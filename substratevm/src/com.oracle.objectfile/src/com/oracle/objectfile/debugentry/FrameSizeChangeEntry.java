package com.oracle.objectfile.debugentry;

import com.oracle.objectfile.debuginfo.DebugInfoProvider.FrameSizeChangeType;

public record FrameSizeChangeEntry(int offset, FrameSizeChangeType type) {
    public boolean isExtend() {
        return type == FrameSizeChangeType.EXTEND;
    }

    public boolean isContract() {
        return type == FrameSizeChangeType.CONTRACT;
    }
}
