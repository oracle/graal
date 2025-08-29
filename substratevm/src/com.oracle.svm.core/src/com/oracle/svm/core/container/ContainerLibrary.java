/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.container;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.util.BasedOnJDKFile;

/**
 * Provides Java-level access to the native {@code libsvm_container} implementation.
 *
 * The native code is base on the container implementation in the JDK. The {@link BasedOnJDKFile}
 * annotations below allow us to track upstream changes. Note that the referenced revisions/tags do
 * not necessarily denote the date when the file was last imported (although often that is the
 * case), but rather the last time upstream changes where reviewed. If there are changes that are
 * irrelevant for SVM, we might omit updating our copies. That said, full updates are done
 * regularly. See also the README file in
 * {@code substratevm/src/com.oracle.svm.native.libcontainer/README.md}.
 */
@CContext(ContainerLibraryDirectives.class)
@CLibrary(value = "svm_container", requireStatic = true, dependsOn = "m")
// The following annotations are for files in `src/hotspot`, which are copied from the JDK
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/java.base/share/native/include/jni.h")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/java.base/unix/native/include/jni_md.h")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/os/linux/cgroupSubsystem_linux.cpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/os/linux/cgroupSubsystem_linux.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/os/linux/cgroupUtil_linux.cpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/os/linux/cgroupUtil_linux.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/os/linux/cgroupV1Subsystem_linux.cpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/os/linux/cgroupV1Subsystem_linux.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/os/linux/cgroupV2Subsystem_linux.cpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/os/linux/cgroupV2Subsystem_linux.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/os/linux/osContainer_linux.cpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/os/linux/osContainer_linux.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+11/src/hotspot/os/linux/os_linux.cpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/os/linux/os_linux.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/os/linux/os_linux.inline.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/os/posix/include/jvm_md.h")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+10/src/hotspot/os/posix/os_posix.cpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/os/posix/os_posix.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/os/posix/os_posix.inline.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/share/memory/allocation.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/share/memory/allocation.inline.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/share/memory/allStatic.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+5/src/hotspot/share/nmt/memTag.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+12/src/hotspot/share/runtime/os.cpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+10/src/hotspot/share/runtime/os.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/share/runtime/os.inline.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/share/utilities/checkedCast.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/share/utilities/compilerWarnings_gcc.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/share/utilities/compilerWarnings.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+9/src/hotspot/share/utilities/globalDefinitions_gcc.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+11/src/hotspot/share/utilities/globalDefinitions.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/share/utilities/macros.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+9/src/hotspot/share/utilities/ostream.cpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+11/src/hotspot/share/utilities/ostream.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/share/utilities/permitForbiddenFunctions.hpp")
// The following annotations are for files in `src/svm`, which are completely customized for SVM
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+13/src/hotspot/share/logging/log.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/share/memory/allocation.cpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+12/src/hotspot/share/runtime/globals.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+9/src/hotspot/share/utilities/debug.cpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+4/src/hotspot/share/utilities/debug.hpp")
public class ContainerLibrary {
    static final int VERSION = 240100;

    // keep in sync with svm_container.hpp
    static final int SUCCESS_IS_NOT_CONTAINERIZED = 0;
    static final int SUCCESS_IS_CONTAINERIZED = 1;
    static final int ERROR_LIBCONTAINER_TOO_OLD = 2;
    static final int ERROR_LIBCONTAINER_TOO_NEW = 3;

    /**
     * Initializes the native container library.
     *
     * @param version should always be called with {@link #VERSION}
     * @return {@link #SUCCESS_IS_CONTAINERIZED}, if native image runs in a container,
     *         {@link #SUCCESS_IS_NOT_CONTAINERIZED} if not. If the native library version does not
     *         match {@link #VERSION}, either {@link #ERROR_LIBCONTAINER_TOO_OLD} or
     *         {@link #ERROR_LIBCONTAINER_TOO_NEW} is returned.
     */
    @CFunction(value = "svm_container_initialize", transition = Transition.NO_TRANSITION)
    public static native int initialize(int version);

    @CFunction(value = "svm_container_physical_memory", transition = Transition.NO_TRANSITION)
    public static native UnsignedWord physicalMemory();

    @CFunction(value = "svm_container_memory_limit_in_bytes", transition = Transition.NO_TRANSITION)
    public static native long getMemoryLimitInBytes();

    @CFunction(value = "svm_container_memory_and_swap_limit_in_bytes", transition = Transition.NO_TRANSITION)
    public static native long getMemoryAndSwapLimitInBytes();

    @CFunction(value = "svm_container_memory_soft_limit_in_bytes", transition = Transition.NO_TRANSITION)
    public static native long getMemorySoftLimitInBytes();

    @CFunction(value = "svm_container_memory_usage_in_bytes", transition = Transition.NO_TRANSITION)
    public static native long getMemoryUsageInBytes();

    @CFunction(value = "svm_container_memory_max_usage_in_bytes", transition = Transition.NO_TRANSITION)
    public static native long getMemoryMaxUsageInBytes();

    @CFunction(value = "svm_container_rss_usage_in_bytes", transition = Transition.NO_TRANSITION)
    public static native long getRssUsageInBytes();

    @CFunction(value = "svm_container_cache_usage_in_bytes", transition = Transition.NO_TRANSITION)
    public static native long getCacheUsageInBytes();

    @CFunction(value = "svm_container_active_processor_count", transition = Transition.NO_TRANSITION)
    public static native int getActiveProcessorCount();
}

@Platforms(Platform.HOSTED_ONLY.class)
class ContainerLibraryDirectives implements CContext.Directives {
    /**
     * True if {@link ContainerLibrary} should be linked.
     */
    @Override
    public boolean isInConfiguration() {
        return Container.isSupported();
    }
}
