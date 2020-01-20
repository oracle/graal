/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Arm Limited. All rights reserved.
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
package com.oracle.svm.core.genscavenge.aarch64;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform.AARCH64;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.CodeSynchronizationOperations;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.genscavenge.GCRefAccessor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.aarch64.AArch64ReferenceAccess;
import com.oracle.svm.core.thread.VMThreads;

public class AArch64GCRefAccessor extends GCRefAccessor {
    static void initialize() {
        ImageSingletons.add(GCRefAccessor.class, new AArch64GCRefAccessor());
    }

    @Override
    public Pointer readRef(Pointer objRef, boolean compressed, int numPieces) {
        if (numPieces == 1) {
            return ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
        } else {
            return ((AArch64ReferenceAccess) ReferenceAccess.singleton()).readSplitObjectAsUntrackedPointer(objRef, compressed, numPieces);
        }
    }

    @Override
    public void writeRefUpdate(Pointer objRef, Object value, boolean compressed, int numPieces) {
        if (numPieces == 1) {
            ReferenceAccess.singleton().writeObjectAt(objRef, value, compressed);
        } else {
            ((AArch64ReferenceAccess) ReferenceAccess.singleton()).writeSplitObjectAt(objRef, value, compressed, numPieces);
            CodeSynchronizationOperations.clearCache(objRef.rawValue(), numPieces * 4);
            VMThreads.ActionOnTransitionToJavaSupport.requestAllThreadsSynchronizeCode();
        }
    }

}

@AutomaticFeature
@Platforms(AARCH64.class)
class AArch64GCRefAccessorFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        AArch64GCRefAccessor.initialize();
    }
}
