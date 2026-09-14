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

import java.util.Locale;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.SubstrateUtil;

/// Writes log messages to `stdout`, `stderr`, or [Log#log()].
final class LogFileStreamOutput extends LogOutput {

    /// Selects the destination used for a stream.
    enum Target {
        /// Denotes standard output.
        STDOUT,

        /// Denotes standard error.
        STDERR,

        /// Denotes the low-level VM log accessed by [Log#log()].
        VMLOG
    }

    /// Destination used for this output.
    private final Target target;

    /// Serializes writes to this native process stream.
    private final VMMutex mutex;

    LogFileStreamOutput(Target target) {
        super(target.name().toLowerCase(Locale.ROOT));
        this.target = target;
        this.mutex = new VMMutex("LogOutput." + name());
    }

    @Override
    protected int writeRaw(CCharPointer bytes, UnsignedWord length) {
        if (target == Target.VMLOG) {
            Log.log().string(bytes, (int) length.rawValue());
            return 0;
        }
        return writeRawLocked(LoggingSupport.singleton(), bytes, length);
    }

    /// Writes a native byte range while serializing access to the stream. The complete critical
    /// section is uninterruptible so that a thread cannot stop at a safepoint while owning the
    /// mutex.
    @Uninterruptible(reason = "The output mutex must not be held across a safepoint.")
    private int writeRawLocked(LoggingSupport loggingSupport, CCharPointer bytes, UnsignedWord length) {
        mutex.lockNoTransition();
        try {
            /* Synchronous logging intentionally uses a no-transition write, as HotSpot does. */
            return loggingSupport.write(target == Target.STDERR, bytes, length) ? 0 : WRITE_FAILED;
        } finally {
            mutex.unlock();
        }
    }

    /// Writes undecorated text for configuration diagnostics and help output.
    void writePlain(String text) {
        if (target == Target.VMLOG) {
            Log.log().string(text);
            return;
        }
        try (CTypeConversion.CCharPointerHolder holder = CTypeConversion.toCString(text)) {
            CCharPointer bytes = holder.get();
            /* Configuration diagnostics are best effort and must not reject an otherwise valid option. */
            writeRawLocked(LoggingSupport.singleton(), bytes, SubstrateUtil.strlen(bytes));
        }
    }
}
