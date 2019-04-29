/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.darwin;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.headers.Errno;
import com.oracle.svm.core.posix.headers.Sysctl;
import com.oracle.svm.core.posix.headers.darwin.DarwinSysctl;
import com.oracle.svm.core.util.VMError;

@Platforms(InternalPlatform.DARWIN_AND_JNI.class)
class DarwinPhysicalMemory extends PhysicalMemory {

    static class PhysicalMemorySupportImpl implements PhysicalMemorySupport {

        /** A sentinel unset value. */
        static final long UNSET_SENTINEL = Long.MIN_VALUE;

        /** The cached size of physical memory, or an unset value. */
        long cachedSize = UNSET_SENTINEL;

        @Override
        public UnsignedWord size() {
            if (hasSize()) {
                return getSize();
            }
            final CIntPointer namePointer = StackValue.get(2, CIntPointer.class);
            namePointer.write(0, DarwinSysctl.CTL_HW());
            namePointer.write(1, DarwinSysctl.HW_MEMSIZE());
            final CLongPointer physicalMemoryPointer = StackValue.get(CLongPointer.class);
            final CLongPointer physicalMemorySizePointer = StackValue.get(CLongPointer.class);
            physicalMemorySizePointer.write(SizeOf.get(CLongPointer.class));
            final int sysctlResult = Sysctl.sysctl(namePointer, 2, physicalMemoryPointer, (WordPointer) physicalMemorySizePointer, WordFactory.nullPointer(), 0);
            if (sysctlResult != 0) {
                Log.log().string("DarwinPhysicalMemory.PhysicalMemorySupportImpl.size(): sysctl() returns with errno: ").signed(Errno.errno()).newline();
                VMError.shouldNotReachHere("DarwinPhysicalMemory.PhysicalMemorySupportImpl.size() failed.");
            }
            /* Cache the value, races are idempotent. */
            setSize(physicalMemoryPointer.read());
            return getSize();
        }

        /** Check if the cache has a value. */
        @Override
        public boolean hasSize() {
            return (cachedSize != UNSET_SENTINEL);
        }

        /** Update the cached size. */
        void setSize(long value) {
            cachedSize = value;
        }

        /** Get the cached size. */
        UnsignedWord getSize() {
            assert hasSize() : "DarwinPhysicalMemory.PhysicalMemorySupportImpl.getValue(): cachedSize has no value.";
            return WordFactory.unsigned(cachedSize);
        }
    }

    @AutomaticFeature
    static class PhysicalMemoryFeature implements Feature {
        @Override
        public void afterRegistration(AfterRegistrationAccess access) {
            ImageSingletons.add(PhysicalMemorySupport.class, new PhysicalMemorySupportImpl());
        }
    }
}
