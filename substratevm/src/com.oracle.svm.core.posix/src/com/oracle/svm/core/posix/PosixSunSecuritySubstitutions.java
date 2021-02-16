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
package com.oracle.svm.core.posix;

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.RuntimeSupport;

/**
 * The {@code NativePRNG} backend for {@link SecureRandom} on Linux and Darwin opens file handles
 * for {@code /dev/random} and {@code /dev/urandom} without ever closing them. This leak can cause a
 * native image to hit the open files limit when it repeatedly spawns isolates, so we close these
 * handles on isolate tear-down.
 *
 * As of Java 11, there is only a dummy implementation of {@code NativePRNG} on Windows which does
 * not open file descriptors that would need to be closed.
 */
@AutomaticFeature
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
class NativeSecureRandomFilesCloser implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        access.registerReachabilityHandler(NativeSecureRandomFilesCloser::registerShutdownHook, Target_sun_security_provider_NativePRNG.class);
    }

    private static void registerShutdownHook(@SuppressWarnings("unused") DuringAnalysisAccess access) {
        RuntimeSupport.getRuntimeSupport().addTearDownHook(new Runnable() {
            @Override
            public void run() {
                Target_sun_security_provider_NativePRNG_RandomIO instance = Target_sun_security_provider_NativePRNG.INSTANCE;
                if (instance != null) {
                    close(instance.nextIn);
                    close(instance.seedIn);
                    close(instance.seedOut);
                }
            }

            private void close(Closeable stream) {
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
        });
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
}
