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

package com.oracle.svm.core.jfr;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.util.VMError;

/**
 * Dummy implementation of a {@link JfrChunkWriter} that does not perform any file system
 * operations.
 */
public final class JfrChunkNoWriter implements JfrChunkWriter {

    private static final String ERROR_MESSAGE = "JfrChunkWriter does not permit write operations, " +
                    "so reaching this method during runtime indicates a semantic error in the code.";

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrChunkNoWriter() {
    }

    @Override
    public void initialize(long maxChunkSize) {
        /* Nothing to do. */
    }

    @Override
    public JfrChunkWriter lock() {
        /* Nothing to do. */
        return this;
    }

    @Override
    public boolean isLockedByCurrentThread() {
        /* Nothing to do. */
        return false;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean hasOpenFile() {
        /* Nothing to do. */
        return false;
    }

    @Override
    public void unlock() {
        /* Nothing to do. */
    }

    @Override
    public long getChunkStartNanos() {
        /* Nothing to do. */
        return -1L;
    }

    @Override
    public void setFilename(String filename) {
        /* Nothing to do. */
    }

    @Override
    public void maybeOpenFile() {
        /* Nothing to do. */
    }

    @Override
    public void openFile(String outputFile) {
        VMError.shouldNotReachHere(ERROR_MESSAGE);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void write(JfrBuffer buffer) {
        VMError.shouldNotReachHere(ERROR_MESSAGE);
    }

    @Override
    public void flush() {
        VMError.shouldNotReachHere(ERROR_MESSAGE);
    }

    @Override
    public void markChunkFinal() {
        /* Nothing to do. */
    }

    @Override
    public void closeFile() {
        VMError.shouldNotReachHere(ERROR_MESSAGE);
    }

    @Override
    public void setMetadata(byte[] bytes) {
        /* Nothing to do. */
    }

    @Override
    public boolean shouldRotateDisk() {
        /* Nothing to do. */
        return false;
    }

    @Override
    public long beginEvent() {
        throw VMError.shouldNotReachHere(ERROR_MESSAGE);
    }

    @Override
    public void endEvent(long start) {
        VMError.shouldNotReachHere(ERROR_MESSAGE);
    }

    @Override
    public void writeBoolean(boolean value) {
        VMError.shouldNotReachHere(ERROR_MESSAGE);
    }

    @Override
    public void writeByte(byte value) {
        VMError.shouldNotReachHere(ERROR_MESSAGE);
    }

    @Override
    public void writeBytes(byte[] values) {
        VMError.shouldNotReachHere(ERROR_MESSAGE);
    }

    @Override
    public void writeCompressedInt(int value) {
        VMError.shouldNotReachHere(ERROR_MESSAGE);
    }

    @Override
    public void writePaddedInt(long value) {
        VMError.shouldNotReachHere(ERROR_MESSAGE);
    }

    @Override
    public void writeCompressedLong(long value) {
        VMError.shouldNotReachHere(ERROR_MESSAGE);
    }

    @Override
    public void writeString(String str) {
        VMError.shouldNotReachHere(ERROR_MESSAGE);
    }
}
