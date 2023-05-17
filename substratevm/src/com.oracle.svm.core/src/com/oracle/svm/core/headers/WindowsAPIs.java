package com.oracle.svm.core.headers;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;

public class WindowsAPIs {
    public static int GetLastError() {
        return win().GetLastError();
    }

    public static int WSAGetLastError() {
        return win().WSAGetLastError();
    }

    @Fold
    public static boolean isSupported() {
        return ImageSingletons.contains(WindowsAPIsSupport.class);
    }

    @Fold
    static WindowsAPIsSupport win() {
        return ImageSingletons.lookup(WindowsAPIsSupport.class);
    }
}
