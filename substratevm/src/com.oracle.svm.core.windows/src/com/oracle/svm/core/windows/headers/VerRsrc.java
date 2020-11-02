package com.oracle.svm.core.windows.headers;

// Checkstyle: stop

import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;

/**
 * Definitions for Windows verrsrc.h
 */

public class VerRsrc {

    @CStruct
    public interface VS_FIXEDFILEINFO extends PointerBase {
        @CField
        int dwSignature();

        @CField
        int dwStrucVersion();

        @CField
        int dwFileVersionMS();

        @CField
        int dwFileVersionLS();

        @CField
        int dwProductVersionMS();

        @CField
        int dwProductVersionLS();

        @CField
        int dwFileFlagsMask();

        @CField
        int dwFileFlags();

        @CField
        int dwFileOS();

        @CField
        int dwFileType();

        @CField
        int dwFileSubtype();

        @CField
        int dwFileDateMS();

        @CField
        int dwFileDateLS();
    }
}
