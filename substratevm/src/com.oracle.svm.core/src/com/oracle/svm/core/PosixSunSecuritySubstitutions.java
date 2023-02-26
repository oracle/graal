/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.RuntimeSupport;

final class NativeSecureRandomFilesCloserTearDownHook implements RuntimeSupport.Hook {
    @Override
    public void execute(boolean isFirstIsolate) {
        Target_sun_security_provider_NativePRNG_RandomIO instance = Target_sun_security_provider_NativePRNG.INSTANCE;
        if (instance != null) {
            close(instance.nextIn);
            close(instance.seedIn);
            close(instance.seedOut);
        }
    }

    private static void close(Closeable stream) {
        if (stream != null) {
            Closeable c = stream;
            if (stream instanceof FilterInputStream) {
                // Java 11 wraps RandomIO InputStreams so that they cannot be closed and
                // puts them in a pool for reuse, access the inner stream to close
                Target_java_io_FilterInputStream outer = SubstrateUtil.cast(c, Target_java_io_FilterInputStream.class);
                c = outer.in;
            }
            try {
                c.close();
            } catch (Throwable ignored) {
            }
        }
    }
}

@TargetClass(className = "sun.security.provider.NativePRNG")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_sun_security_provider_NativePRNG {
    @Alias static Target_sun_security_provider_NativePRNG_RandomIO INSTANCE;
}

@TargetClass(className = "sun.security.provider.NativePRNG", innerClass = "RandomIO")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_sun_security_provider_NativePRNG_RandomIO {
    @Alias InputStream seedIn;
    @Alias InputStream nextIn;
    @Alias OutputStream seedOut;
}

@TargetClass(FilterInputStream.class)
final class Target_java_io_FilterInputStream {
    @Alias InputStream in;
}

public final class PosixSunSecuritySubstitutions {
    private PosixSunSecuritySubstitutions() {
    }

    public static RuntimeSupport.Hook getTearDownHook() {
        return new NativeSecureRandomFilesCloserTearDownHook();
    }
}
