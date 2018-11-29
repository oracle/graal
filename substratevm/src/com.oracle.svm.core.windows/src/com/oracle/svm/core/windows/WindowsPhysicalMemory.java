/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.windows;

import com.oracle.svm.core.windows.headers.WinBase;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.heap.PhysicalMemory;

import static org.graalvm.word.WordFactory.nullPointer;

// Checkstyle: stop

@Platforms(Platform.WINDOWS.class)
class WindowsPhysicalMemory extends PhysicalMemory {

    static class WindowsPhysicalMemorySupportImpl implements PhysicalMemorySupport {
        @Override
        public UnsignedWord size() {
            WinBase.MEMORYSTATUSEX memStatusEx = StackValue.get(WinBase.MEMORYSTATUSEX.class);
            memStatusEx.set_dwLength(SizeOf.get(WinBase.MEMORYSTATUSEX.class));
            if (WinBase.GlobalMemoryStatusEx(memStatusEx)) {
                return WordFactory.unsigned(memStatusEx.ullTotalPhys());
            } else {
                return nullPointer();
            }
        }

    }

    @AutomaticFeature
    static class PhysicalMemoryFeature implements Feature {
        @Override
        public void afterRegistration(AfterRegistrationAccess access) {
            ImageSingletons.add(PhysicalMemorySupport.class, new WindowsPhysicalMemorySupportImpl());
        }
    }
}
