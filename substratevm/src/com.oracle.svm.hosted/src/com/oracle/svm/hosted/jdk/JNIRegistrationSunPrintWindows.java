package com.oracle.svm.hosted.jdk;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

/**
 * @see JNIRegistrationJavaAwtWindows
 */
@Platforms(Platform.WINDOWS.class)
@AutomaticFeature
@SuppressWarnings("unused")
public class JNIRegistrationSunPrintWindows extends JNIRegistrationAwtUtil implements Feature {


    @Override
    public void duringSetup(DuringSetupAccess a) {
        initializeAtRunTime(a,
                // from windows specific code
                // WPrinterJob.cpp
                SunPrint.PRINT_SERVICE_LOOKUP_PROVIDER,
                SunPrint.WIN32_PRINT_JOB,
                SunPrint.WIN32_PRINT_SERVICE
        );
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        // from windows specific code
        // WPrinterJob.cpp
        registerClassHandler(a, JNIRegistrationSunPrintWindows::registerPrintServiceLookupProviderClass, SunPrint.PRINT_SERVICE_LOOKUP_PROVIDER);
        registerClassHandler(a, JNIRegistrationSunPrintWindows::registerWin32PrintJobClass, SunPrint.WIN32_PRINT_JOB);
        registerClassHandler(a, JNIRegistrationSunPrintWindows::registerWin32PrintServiceClass, SunPrint.WIN32_PRINT_SERVICE);

    }

    private static void registerPrintServiceLookupProviderClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(String.class);
    }

    private static void registerWin32PrintJobClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunPrint.WIN32_PRINT_JOB, "hPrintJob"));
    }

    private static void registerWin32PrintServiceClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(String.class);
    }
}