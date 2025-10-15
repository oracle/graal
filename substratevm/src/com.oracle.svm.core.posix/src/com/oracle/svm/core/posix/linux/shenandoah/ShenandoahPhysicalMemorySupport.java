/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.linux.shenandoah;

import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.gc.shenandoah.ShenandoahCommittedMemoryProvider;
import com.oracle.svm.core.gc.shenandoah.UseShenandoahGC;
import com.oracle.svm.core.heap.PhysicalMemory.PhysicalMemorySupport;
import com.oracle.svm.core.posix.linux.LinuxPhysicalMemorySupportImpl;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Disallowed;
import com.oracle.svm.core.traits.SingletonTraits;

@AutomaticallyRegisteredImageSingleton(value = PhysicalMemorySupport.class, onlyWith = UseShenandoahGC.class)
@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Disallowed.class)
public class ShenandoahPhysicalMemorySupport extends LinuxPhysicalMemorySupportImpl {
    @Override
    public UnsignedWord size() {
        return ShenandoahCommittedMemoryProvider.getInstance().getPhysicalMemorySize();
    }
}
