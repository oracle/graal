/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.handles;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.word.PointerBase;

@AutomaticallyRegisteredFeature
final class PinnedPrimitiveArrayViewFeature implements InternalFeature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (!ImageSingletons.contains(PrimitiveArrayViewSupport.class)) {
            ImageSingletons.add(PrimitiveArrayViewSupport.class, new PinnedPrimitiveArrayViewSupportImpl());
        }
    }
}

final class PinnedPrimitiveArrayViewSupportImpl implements PrimitiveArrayViewSupport {
    static final class PinnedPrimitiveElementArrayReferenceImpl implements PrimitiveArrayView {
        private final PinnedObject pinnedObject;

        PinnedPrimitiveElementArrayReferenceImpl(Object object) {
            pinnedObject = PinnedObject.create(object);
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void close() {
            pinnedObject.close();
        }

        @Override
        public void untrack() {
            pinnedObject.close();
        }

        @Override
        public <T extends PointerBase> T addressOfArrayElement(int index) {
            return pinnedObject.addressOfArrayElement(index);
        }

        @Override
        public boolean isCopy() {
            return false;
        }

        @Override
        public void syncToHeap() {
        }
    }

    @Override
    public PrimitiveArrayView createForReading(Object object) {
        return new PinnedPrimitiveElementArrayReferenceImpl(object);
    }

    @Override
    public PrimitiveArrayView createForReadingAndWriting(Object object) {
        return new PinnedPrimitiveElementArrayReferenceImpl(object);
    }
}
