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
package com.oracle.svm.core.jvmti;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jvmti.headers.JvmtiEventCallbacks;

/** Methods related to {@link JvmtiEventCallbacks}. */
public final class JvmtiEventCallbacksUtil {
    @Platforms(Platform.HOSTED_ONLY.class)
    private JvmtiEventCallbacksUtil() {
    }

    public static void setEventCallbacks(JvmtiEventCallbacks envEventCallbacks, JvmtiEventCallbacks newCallbacks, int sizeOfCallbacks) {
        int internalStructSize = SizeOf.get(JvmtiEventCallbacks.class);
        UnmanagedMemoryUtil.fill((Pointer) envEventCallbacks, WordFactory.unsigned(internalStructSize), (byte) 0);

        if (newCallbacks.isNonNull()) {
            int bytesToCopy = UninterruptibleUtils.Math.min(internalStructSize, sizeOfCallbacks);
            UnmanagedMemoryUtil.copy((Pointer) newCallbacks, (Pointer) envEventCallbacks, WordFactory.unsigned(bytesToCopy));
        }
    }
}
