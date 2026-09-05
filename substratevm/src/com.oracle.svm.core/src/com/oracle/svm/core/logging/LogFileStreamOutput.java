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

import java.nio.charset.StandardCharsets;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.UnsignedWord;

/// Writes log messages to `stdout` or `stderr`.
final class LogFileStreamOutput extends LogOutput {

    /// Selects standard error instead of standard output.
    private final boolean isStderr;

    LogFileStreamOutput(boolean isStderr) {
        super(isStderr ? "stderr" : "stdout");
        this.isStderr = isStderr;
    }

    @Override
    protected int writeRaw(CCharPointer bytes, UnsignedWord length) {
        /* Emergency logging cannot recover from a failed native stream write. */
        return LoggingSupport.singleton().write(isStderr, bytes, length) ? 0 : WRITE_FAILED;
    }

    /// Writes undecorated text for configuration diagnostics and help output.
    void writePlain(String text) {
        if (!LoggingSupport.singleton().write(isStderr, text.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException("Could not write unified log output '" + name() + "'.");
        }
    }
}
