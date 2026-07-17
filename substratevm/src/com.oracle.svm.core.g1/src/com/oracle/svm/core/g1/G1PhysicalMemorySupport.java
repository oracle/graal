/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.g1;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.heap.PlatformPhysicalMemorySupport;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.DisallowLayered;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = DisallowLayered.class)
public class G1PhysicalMemorySupport extends PlatformPhysicalMemorySupport {
    private final PlatformPhysicalMemorySupport physicalMemory;

    @Platforms(Platform.HOSTED_ONLY.class)
    public G1PhysicalMemorySupport(PlatformPhysicalMemorySupport physicalMemory) {
        this.physicalMemory = physicalMemory;
    }

    @Override
    public UnsignedWord size() {
        /*
         * Return the size that was computed on the C++ side to ensure that Native Image
         * and G1 use the same values.
        */
        return G1CommittedMemoryProvider.getInstance().getPhysicalMemorySize();
    }

    @Override
    public long usedSize() {
        return physicalMemory.usedSize();
    }
}
