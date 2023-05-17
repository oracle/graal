package com.oracle.svm.core.headers;

import com.oracle.svm.core.Uninterruptible;

public interface WindowsAPIsSupport {
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    int GetLastError();

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    int WSAGetLastError();
}
