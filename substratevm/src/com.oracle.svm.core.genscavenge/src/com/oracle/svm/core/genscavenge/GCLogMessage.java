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
package com.oracle.svm.core.genscavenge;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.log.NativeMemoryBuffer;
import com.oracle.svm.core.logging.HasXlogSupport;
import com.oracle.svm.core.logging.LogMessage;
import com.oracle.svm.core.logging.LogTagSet;
import com.oracle.svm.guest.staging.SubstrateGCOptions;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalLong;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.TimeUtils;

import jdk.graal.compiler.api.replacements.Fold;

/// Shares GC text formatting while keeping legacy delivery out of unified logging.
/// The build-time selection retains either the unified facade or the legacy native buffer,
/// never unified routing or decorations merely to support legacy GC options.
final class GCLogMessage {
    /// Captures one timestamp for all lines of a legacy event; omitted when the route is unused.
    private static final FastThreadLocalLong legacyUptime = FastThreadLocalFactory.createLong("GCLogMessage.legacyUptime");

    @Fold
    static GCLogMessage singleton() {
        return GCImpl.getLogMessage();
    }

    /// Present only in images with unified logging.
    private final LogMessage unified;

    /// Present only in images using legacy GC logging.
    private final NativeMemoryBuffer legacy;

    @Platforms(Platform.HOSTED_ONLY.class)
    GCLogMessage() {
        unified = HasXlogSupport.get() ? LogTagSet.gc.messageFacade() : null;
        legacy = HasXlogSupport.get() ? null : new NativeMemoryBuffer(NativeMemoryBuffer.PRIMARY, false);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static void registerCleanup() {
        if (!HasXlogSupport.get()) {
            singleton().legacy.registerCleanup();
        }
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static boolean isInfo() {
        // Verbose legacy logging includes summaries even when PrintGC is unset.
        return HasXlogSupport.get() ? LogTagSet.gc.isInfo() : SubstrateGCOptions.PrintGC.getValue() || SubstrateGCOptions.VerboseGC.getValue();
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static boolean isDebug() {
        return HasXlogSupport.get() ? LogTagSet.gc.isDebug() : SubstrateGCOptions.VerboseGC.getValue();
    }

    /// Opens one event on the build-time-selected route.
    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_OWNER, callerMustBe = true)
    static GCLogMessage open() {
        if (HasXlogSupport.get()) {
            LogTagSet.gc.message();
        } else {
            singleton().legacy.reset();
            legacyUptime.set(Isolates.isStartTimeAssigned() ? System.nanoTime() - Isolates.getStartTimeNanos() : 0);
        }
        return singleton();
    }

    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_OWNER, callerMustBe = true)
    NativeMemoryBuffer info() {
        return prefix(HasXlogSupport.get() ? unified.info() : legacyLine());
    }

    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_OWNER, callerMustBe = true)
    NativeMemoryBuffer debug() {
        return prefix(HasXlogSupport.get() ? unified.debug() : legacyLine());
    }

    /// Starts another physical GC line without delegating prefix policy to the output layer.
    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_OWNER, callerMustBe = true)
    static NativeMemoryBuffer newline(NativeMemoryBuffer buffer) {
        buffer.newline();
        return prefix(buffer);
    }

    /// Restores the collection prefix after a chunk formatter's newline and indentation.
    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_OWNER, callerMustBe = true)
    static void ensureLinePrefix(NativeMemoryBuffer buffer) {
        int position = buffer.getPosition();
        while (position > 0 && buffer.getBuffer().read(position - 1) == ' ') {
            position--;
        }
        if (position == 0 || buffer.getBuffer().read(position - 1) == '\n') {
            prefix(buffer);
        }
    }

    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_OWNER, callerMustBe = true)
    private static NativeMemoryBuffer prefix(NativeMemoryBuffer buffer) {
        GCImpl.getGCImpl().logPrefix(buffer);
        return buffer;
    }

    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_OWNER, callerMustBe = true)
    private NativeMemoryBuffer legacyLine() {
        if (legacy.getPosition() != 0) {
            legacy.newline();
        }
        long nanos = legacyUptime.get();
        long millis = (nanos + TimeUtils.nanosPerMilli / 2) / TimeUtils.nanosPerMilli;
        legacy.character('[').rational(millis, 1_000, 3).string("s] ");
        return legacy;
    }

    /// Delivers the completed event through exactly one logging contract.
    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_OWNER, callerMustBe = true)
    void close() {
        if (HasXlogSupport.get()) {
            unified.close();
        } else if (legacy.getPosition() != 0) {
            legacy.newline();
            writeLegacy(legacy.getBuffer(), legacy.getPosition());
        }
    }

    /// Legacy handlers keep their existing interruptible contract. This bridge is unreachable in
    /// images with unified logging, whose output never enters a legacy handler.
    @Uninterruptible(reason = "Preserves the existing legacy GC logging contract.", calleeMustBe = false)
    private static void writeLegacy(CCharPointer bytes, int length) {
        Log.log().string(bytes, length);
    }
}
