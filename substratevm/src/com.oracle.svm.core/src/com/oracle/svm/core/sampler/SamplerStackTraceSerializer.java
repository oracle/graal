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

package com.oracle.svm.core.sampler;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.Pointer;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * An interface for serializing stack traces. Subtypes should implement the actual serialization
 * logic based on their specific requirements.
 */
public interface SamplerStackTraceSerializer {

    @Fold
    static SamplerStackTraceSerializer singleton() {
        return ImageSingletons.lookup(SamplerStackTraceSerializer.class);
    }

    /**
     * Serializes a stack trace sample and related information.
     *
     * @param rawStackTrace The pointer to the beginning of the stack trace in the buffer.
     * @param bufferEnd The pointer to the end of the buffer.
     * @param sampleSize The size of the stack trace sample.
     * @param sampleHash A unique hash code representing the sample.
     * @param isTruncated Indicates whether the sample is truncated.
     * @param sampleTick The timestamp of the sample.
     * @param threadId A unique identifier for the sampled thread.
     * @param threadState The state of the sampled thread.
     * @return A pointer to the next stack trace entry or the end of the buffer.
     */
    Pointer serializeStackTrace(Pointer rawStackTrace, Pointer bufferEnd, int sampleSize, int sampleHash,
                    boolean isTruncated, long sampleTick, long threadId, long threadState);
}
