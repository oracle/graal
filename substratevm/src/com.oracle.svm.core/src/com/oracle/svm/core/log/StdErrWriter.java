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
package com.oracle.svm.core.log;

import static com.oracle.svm.guest.staging.core.jdk.UninterruptibleUtils.Math.min;
import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.guest.staging.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.shared.Uninterruptible;

public interface StdErrWriter {
    /// Writes diagnostics without allocating or invoking a configurable legacy log handler.
    @Uninterruptible(reason = "The temporary native buffer must stay on the current carrier.")
    static void write(String text) {
        final int capacity = 256;
        CCharPointer bytes = UnsafeStackValue.get(capacity);
        for (int offset = 0; offset < text.length();) {
            int count = min(capacity, text.length() - offset);
            for (int index = 0; index < count; index++) {
                bytes.write(index, (byte) UninterruptibleUtils.String.charAt(text, offset + index));
            }
            ImageSingletons.lookup(StdErrWriter.class).log(bytes, Word.unsigned(count));
            offset += count;
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void log(CCharPointer bytes, UnsignedWord length);

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void flush();
}
