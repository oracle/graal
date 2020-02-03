/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core;

import java.io.CharConversionException;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.util.Utf8;
import com.oracle.svm.core.util.VMError;

public final class VM {
    @Platforms(Platform.HOSTED_ONLY.class) //
    public static final String valueSeparator = "=";
    @Platforms(Platform.HOSTED_ONLY.class) //
    private static final String versionValue = getVersionValue();

    @Platforms(Platform.HOSTED_ONLY.class)
    private static String getVersionValue() {
        String version = System.getProperty("org.graalvm.version");
        VMError.guarantee(version != null);
        version = VM.class.getName() + valueSeparator + "GraalVM " + version;
        version += " Java " + JavaVersionUtil.JAVA_SPEC;
        String config = System.getProperty("org.graalvm.config", "");
        if (!config.isEmpty()) {
            version += " " + config;
        }
        return version;
    }

    private static final String VERSION_INFO_SYMBOL_NAME = "__svm_version_info";
    private static final CGlobalData<CCharPointer> VERSION_INFO = CGlobalDataFactory.createCString(versionValue, VERSION_INFO_SYMBOL_NAME);

    private static final int versionValueHash = versionValue.hashCode();

    public static String getVersion() {
        try {
            CCharPointer versionInfoBytes = VERSION_INFO.get();
            String version = Utf8.utf8ToString(true, CTypeConversion.asByteBuffer(versionInfoBytes, Math.toIntExact(SubstrateUtil.strlen(versionInfoBytes).rawValue())));
            VMError.guarantee(version.hashCode() == versionValueHash,
                            "HashCode mismatch for " + VERSION_INFO_SYMBOL_NAME + ": actual " + version.hashCode() + " (expected " + versionValueHash + ")");
            return SubstrateUtil.split(version, valueSeparator)[1];
        } catch (CharConversionException ignore) {
            throw VMError.shouldNotReachHere("Invalid version info in " + VERSION_INFO_SYMBOL_NAME);
        }
    }
}
