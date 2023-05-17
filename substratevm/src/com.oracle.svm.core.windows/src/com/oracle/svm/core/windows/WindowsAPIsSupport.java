package com.oracle.svm.core.windows;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.windows.headers.WinBase;
import com.oracle.svm.core.windows.headers.WinSock;

@AutomaticallyRegisteredImageSingleton(com.oracle.svm.core.headers.WindowsAPIsSupport.class)
public class WindowsAPIsSupport implements com.oracle.svm.core.headers.WindowsAPIsSupport {
    @Override
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public int GetLastError() {
        return WinBase.GetLastError();
    }

    @Override
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public int WSAGetLastError() {
        return WinSock.WSAGetLastError();
    }
}
