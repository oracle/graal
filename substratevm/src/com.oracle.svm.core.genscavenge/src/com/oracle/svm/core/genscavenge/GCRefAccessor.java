/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.heap.ReferenceAccess;

public class GCRefAccessor {
    static void initialize() {
        ImageSingletons.add(GCRefAccessor.class, new GCRefAccessor());
    }

    @Fold
    public static GCRefAccessor singleton() {
        return ImageSingletons.lookup(GCRefAccessor.class);
    }

    public Pointer readRef(Pointer objRef, boolean compressed, @SuppressWarnings("unused") int numPieces) {
        assert numPieces == 1;
        return ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
    }

    public void writeRefUpdate(Pointer objRef, Object value, boolean compressed, @SuppressWarnings("unused") int numPieces) {
        assert numPieces == 1;
        ReferenceAccess.singleton().writeObjectAt(objRef, value, compressed);
    }

}

@AutomaticFeature
@Platforms(Platform.AMD64.class)
class GCRefAccessorFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        GCRefAccessor.initialize();
    }
}
