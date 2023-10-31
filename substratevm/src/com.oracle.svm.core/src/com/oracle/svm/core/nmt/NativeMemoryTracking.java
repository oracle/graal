/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.nmt;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.util.UnsignedUtils;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.WordFactory;
import com.oracle.svm.core.config.ConfigurationValues;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.Platform;

import org.graalvm.nativeimage.ImageSingletons;

public class NativeMemoryTracking {
    public com.oracle.svm.core.nmt.MallocMemorySnapshot mallocMemorySnapshot;
    private VirtualMemorySnapshot virtualMemorySnapshot;
    private boolean enabled;

    @Platforms(Platform.HOSTED_ONLY.class)
    public NativeMemoryTracking() {
        enabled = true; // TODO enable via the same runtime options in hotspot
        mallocMemorySnapshot = new MallocMemorySnapshot();
        virtualMemorySnapshot = new VirtualMemorySnapshot();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setEnabled(boolean enabled) {
        get().enabled = enabled;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getHeaderSize() {
        if (HasNmtSupport.get() && get().enabled) { // *** SubstrateJVM calls HasJfrSupport
            return UnsignedUtils.roundUp(SizeOf.unsigned(MallocHeader.class), WordFactory.unsigned(ConfigurationValues.getTarget().wordSize));
        }
        return WordFactory.zero();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static NativeMemoryTracking get() {
        return ImageSingletons.lookup(NativeMemoryTracking.class);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer recordMalloc(Pointer outerPointer, UnsignedWord size, int flag) {
        if (HasNmtSupport.get() && get().enabled) {
            get().mallocMemorySnapshot.getInfoByCategory(flag).recordMalloc(size);
            get().mallocMemorySnapshot.getTotalInfo().recordMalloc(size);
            return initializeHeader(outerPointer, size, flag);
        }
        return outerPointer;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void deaccountMalloc(PointerBase ptr) {
        if (HasNmtSupport.get() && get().enabled) {
            MallocHeader header = (MallocHeader) ((Pointer) ptr).subtract(getHeaderSize());
            get().mallocMemorySnapshot.getInfoByCategory(header.getFlag()).deaccountMalloc(header.getSize());
            get().mallocMemorySnapshot.getTotalInfo().deaccountMalloc(header.getSize());
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static Pointer initializeHeader(Pointer outerPointer, UnsignedWord size, int flag) {
        MallocHeader mallocHeader = (MallocHeader) outerPointer;
        mallocHeader.setSize(size.rawValue());
        mallocHeader.setFlag(flag);
        return ((Pointer) mallocHeader).add(NativeMemoryTracking.getHeaderSize());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void recordReserve(UnsignedWord size, int flag) {
        if (HasNmtSupport.get() && get().enabled) {
            get().virtualMemorySnapshot.getInfoByCategory(flag).recordReserved(size);
            get().virtualMemorySnapshot.getTotalInfo().recordReserved(size);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void recordCommit(UnsignedWord size, int flag) {
        if (HasNmtSupport.get() && get().enabled) {
            get().virtualMemorySnapshot.getInfoByCategory(flag).recordCommitted(size);
            get().virtualMemorySnapshot.getTotalInfo().recordCommitted(size);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void recordUncommit(UnsignedWord size, int flag) {
        if (HasNmtSupport.get() && get().enabled) {
            get().virtualMemorySnapshot.getInfoByCategory(flag).recordUncommit(size);
            get().virtualMemorySnapshot.getTotalInfo().recordUncommit(size);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void recordFree(UnsignedWord size, int flag) {
        if (HasNmtSupport.get() && get().enabled) {
            get().virtualMemorySnapshot.getInfoByCategory(flag).recordFree(size);
            get().virtualMemorySnapshot.getTotalInfo().recordFree(size);
        }
    }

    public static void printStats() {
        if (HasNmtSupport.get()) {
            System.out.println("MALLOC");
            for (int i = 0; i < com.oracle.svm.core.nmt.NmtFlag.values().length; i++) {

            }
            System.out.println("total size:" + get().mallocMemorySnapshot.getTotalInfo().getSize());
            System.out.println("total count:" + get().mallocMemorySnapshot.getTotalInfo().getCount());
            System.out.println("Virtual");
            System.out.println("total reserved:" + get().virtualMemorySnapshot.getTotalInfo().getReservedSize());
            System.out.println("total committed:" + get().virtualMemorySnapshot.getTotalInfo().getCommittedSize());
        }
    }
}
