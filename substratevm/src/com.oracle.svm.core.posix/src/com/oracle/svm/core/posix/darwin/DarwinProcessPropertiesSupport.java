/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.ProcessPropertiesSupport;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.posix.PosixProcessPropertiesSupport;
import com.oracle.svm.core.posix.headers.darwin.DarwinDyld;
import com.oracle.svm.core.util.VMError;

public class DarwinProcessPropertiesSupport extends PosixProcessPropertiesSupport {

    @Override
    public String getExecutableName() {
        /* Find out how long the executable path is. */
        final CIntPointer sizePointer = StackValue.get(CIntPointer.class);
        sizePointer.write(0);
        if (DarwinDyld._NSGetExecutablePath(WordFactory.nullPointer(), sizePointer) != -1) {
            VMError.shouldNotReachHere("DarwinProcessPropertiesSupport.getExecutableName: Executable path length is 0?");
        }
        /* Allocate a correctly-sized buffer and ask again. */
        final byte[] byteBuffer = new byte[sizePointer.read()];
        try (PinnedObject pinnedBuffer = PinnedObject.create(byteBuffer)) {
            final CCharPointer bufferPointer = pinnedBuffer.addressOfArrayElement(0);
            if (DarwinDyld._NSGetExecutablePath(bufferPointer, sizePointer) == -1) {
                /* Failure to find executable path. */
                return null;
            }
            final String executableString = CTypeConversion.toJavaString(bufferPointer);
            return realpath(executableString);
        }
    }

    @AutomaticFeature
    public static class ImagePropertiesFeature implements Feature {

        @Override
        public void afterRegistration(AfterRegistrationAccess access) {
            ImageSingletons.add(ProcessPropertiesSupport.class, new DarwinProcessPropertiesSupport());
        }
    }
}
