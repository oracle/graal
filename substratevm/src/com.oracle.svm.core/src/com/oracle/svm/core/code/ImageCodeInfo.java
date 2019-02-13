/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.annotate.AutomaticFeature;

public class ImageCodeInfo extends AbstractCodeInfo {

    public static final String CODE_INFO_NAME = "image code";

    @Platforms(Platform.HOSTED_ONLY.class)
    public ImageCodeInfo() {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setData(CFunctionPointer codeStart, UnsignedWord codeSize) {
        super.setData(codeStart, codeSize);
    }

    /** Walk the image code with a MemoryWalker. */
    public boolean walkImageCode(MemoryWalker.Visitor visitor) {
        return visitor.visitImageCode(this, ImageSingletons.lookup(MemoryWalkerAccessImpl.class));
    }

    @Override
    public String getName() {
        return CODE_INFO_NAME;
    }

    /** Methods for MemoryWalker to access image code information. */
    public static final class MemoryWalkerAccessImpl implements MemoryWalker.ImageCodeAccess<ImageCodeInfo> {

        /** A private constructor used only to make up the singleton instance. */
        @Platforms(Platform.HOSTED_ONLY.class)
        protected MemoryWalkerAccessImpl() {
            super();
        }

        @Override
        public UnsignedWord getStart(ImageCodeInfo imageCodeInfo) {
            return (UnsignedWord) imageCodeInfo.getCodeStart();
        }

        @Override
        public UnsignedWord getSize(ImageCodeInfo imageCodeInfo) {
            return imageCodeInfo.getCodeSize();
        }

        @Override
        public String getRegion(ImageCodeInfo imageCodeInfo) {
            return CODE_INFO_NAME;
        }
    }
}

@AutomaticFeature
class ImageCodeInfoMemoryWalkerAccessFeature implements Feature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(ImageCodeInfo.MemoryWalkerAccessImpl.class, new ImageCodeInfo.MemoryWalkerAccessImpl());
    }
}
