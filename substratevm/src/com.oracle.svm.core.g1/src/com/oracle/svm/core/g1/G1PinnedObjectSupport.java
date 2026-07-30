/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.impl.Word;

import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.core.heap.AbstractPinnedObjectSupport;
import com.oracle.svm.core.g1.nativelib.G1Library;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.DisallowLayered;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

/**
 * If an object is pinned using {@link G1Library#pinObject(Word)}, then a whole heap region is
 * marked as pinned. However, dead objects in that heap region will still be destroyed (except for
 * primitive arrays). Therefore, it is essential that the application always holds a strong
 * reference to the pinned object to keep it from being collected. To guarantee this, we keep all
 * {@link PinnedObject}s in a global linked list and ensure that each {@link PinnedObject} has a
 * strong reference to the pinned Java object.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class, other = DisallowLayered.class)
public final class G1PinnedObjectSupport extends AbstractPinnedObjectSupport {
    @Platforms(Platform.HOSTED_ONLY.class)
    public G1PinnedObjectSupport() {
    }

    @Override
    @Uninterruptible(reason = "Use untracked pointers. Ensure that pinned object counts and PinnedObjects are consistent.", callerMustBe = true)
    protected void pinObject(Object object) {
        G1Library.pinObject(Word.objectToUntrackedWord(object));
    }

    @Override
    @Uninterruptible(reason = "Use untracked pointers. Ensure that pinned object counts and PinnedObjects are consistent.", callerMustBe = true)
    protected void unpinObject(Object object) {
        G1Library.unpinObject(Word.objectToUntrackedWord(object));
    }
}
