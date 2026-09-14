/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.logging;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.c.CIsolateData;
import com.oracle.svm.core.c.CIsolateDataFactory;
import com.oracle.svm.core.logging.LogAsyncWriterStructures.QueueState;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFilePath;
import com.oracle.svm.shared.Uninterruptible;

import jdk.graal.compiler.api.replacements.Fold;

/// Provides support for unified logging, bypassing Java libraries which may not be initialized.
public abstract class LoggingSupport {
    /// Native queue state belongs to the image singleton so each image build gets its own entry. It
    /// is initialized before analysis, once native structure sizes are available.
    private CIsolateData<QueueState> asyncLogWriterQueueState;

    /// Gets the platform implementation.
    @Fold
    public static LoggingSupport singleton() {
        return ImageSingletons.lookup(LoggingSupport.class);
    }

    /// Creates the native queue state after C interface processing has completed.
    @Platforms(Platform.HOSTED_ONLY.class)
    final void initialize() {
        assert asyncLogWriterQueueState == null;
        asyncLogWriterQueueState = CIsolateDataFactory.createStruct("logAsyncWriterQueue", QueueState.class);
    }

    /// Gets the native queue state used by the asynchronous writer.
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final QueueState asyncLogWriterQueueState() {
        return asyncLogWriterQueueState.get();
    }

    /// Writes a native byte range to standard output or standard error without a thread status
    /// transition. A blocked synchronous write can therefore delay safepoint progress.
    ///
    /// @return true on success
    @Uninterruptible(reason = "Synchronous logging uses a no-transition platform write.")
    public abstract boolean write(boolean stderr, CCharPointer bytes, UnsignedWord length);

    /// Deletes a path converted to native memory during configuration.
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public abstract boolean delete(RawFilePath path);

    /// Renames `source` to `target`.
    ///
    /// @param source the name of the file to be renamed
    /// @param target the new name of the file
    /// @return 0 on success, platform-dependent error code on failure
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public abstract int rename(RawFilePath source, RawFilePath target);

    /// Gets the native host name without depending on the initialized networking library.
    public abstract String hostname();
}
