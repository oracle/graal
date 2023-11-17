/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.sampler;

import jdk.graal.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.util.UnsignedUtils;

/**
 * Used to access the raw memory of a {@link SamplerBuffer}.
 */
public final class SamplerBufferAccess {

    private SamplerBufferAccess() {
    }

    @Fold
    public static UnsignedWord getHeaderSize() {
        return UnsignedUtils.roundUp(SizeOf.unsigned(SamplerBuffer.class), WordFactory.unsigned(ConfigurationValues.getTarget().wordSize));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void reinitialize(SamplerBuffer buffer) {
        assert buffer.isNonNull();
        Pointer dataStart = getDataStart(buffer);
        buffer.setPos(dataStart);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer getDataStart(SamplerBuffer buffer) {
        assert buffer.isNonNull();
        return ((Pointer) buffer).add(getHeaderSize());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isEmpty(SamplerBuffer buffer) {
        assert buffer.isNonNull();
        return getDataStart(buffer).equal(buffer.getPos());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer getDataEnd(SamplerBuffer buffer) {
        assert buffer.isNonNull();
        return getDataStart(buffer).add(buffer.getSize());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean verify(SamplerBuffer buffer) {
        if (buffer.isNull()) {
            return false;
        }

        Pointer start = getDataStart(buffer);
        Pointer end = getDataEnd(buffer);
        return buffer.getPos().aboveOrEqual(start) && buffer.getPos().belowOrEqual(end);
    }
}
