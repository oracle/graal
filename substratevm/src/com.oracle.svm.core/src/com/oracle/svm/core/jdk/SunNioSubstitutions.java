/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

// Checkstyle: allow reflection

import java.io.FileDescriptor;
import java.nio.channels.spi.SelectorProvider;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

@TargetClass(className = "sun.nio.ch.Util")
final class Target_sun_nio_ch_Util {

    @Substitute
    private static Target_java_nio_DirectByteBuffer newMappedByteBuffer(int size, long addr, FileDescriptor fd, Runnable unmapper) {
        return new Target_java_nio_DirectByteBuffer(size, addr, fd, unmapper);
    }

    @Substitute
    static Target_java_nio_DirectByteBufferR newMappedByteBufferR(int size, long addr, FileDescriptor fd, Runnable unmapper) {
        return new Target_java_nio_DirectByteBufferR(size, addr, fd, unmapper);
    }
}

@TargetClass(java.nio.channels.spi.SelectorProvider.class)
final class Target_java_nio_channels_spi_SelectorProvider {

    @Alias//
    static SelectorProvider provider;

    @Substitute
    static SelectorProvider provider() {
        VMError.guarantee(provider != null, "java.nio.channels.spi.SelectorProvider.provider must be initialized during image generation");
        return provider;
    }

    static {
        /*
         * Calling the method during image generation triggers initialization. This ensures that we
         * have a correctly initialized provider available at run time. It also means that the
         * system property and service loader configuration that allow influencing the
         * SelectorProvider implementation are accessed during image generation, i.e., it is not
         * possible to overwrite the implementation class at run time anymore by changing the system
         * property at run time.
         */
        SelectorProvider result = java.nio.channels.spi.SelectorProvider.provider();
        assert result != null;
    }
}

@SuppressWarnings({"unused"})
@TargetClass(sun.nio.ch.Net.class)
final class Target_sun_nio_ch_Net {

    @Substitute
    static int getInterface4(FileDescriptor fd) {
        throw VMError.unsupportedFeature("Unimplemented:  sun.nio.ch.Net.getInterface4(FileDescriptor)");
    }

    @Substitute
    static int getInterface6(FileDescriptor fd) {
        throw VMError.unsupportedFeature("Unimplemented:  sun.nio.ch.Net.getInterface6(FileDescriptor)");
    }

    @Substitute
    static void setInterface4(FileDescriptor fd, int interf) {
        throw VMError.unsupportedFeature("Unimplemented: sun.nio.ch.Net.setInterface4(FileDescriptor, int)");
    }

    @Substitute
    static void setInterface6(FileDescriptor fd, int index) {
        throw VMError.unsupportedFeature("Unimplemented:  sun.nio.ch.Net.setInterface6(FileDescriptor, int)");
    }
}

@SuppressWarnings({"unused"})
@TargetClass(className = "sun.nio.ch.DatagramDispatcher")
final class Target_sun_nio_ch_DatagramDispatcher {

    @Substitute
    static int write0(FileDescriptor fd, long address, int len) {
        throw VMError.unsupportedFeature("Unimplemented: sun.nio.ch.DatagramChannelImpl.receive0(FileDescriptor, long, int)");
    }
}

/** Dummy class to have a class with the file's name. */
public final class SunNioSubstitutions {
}
