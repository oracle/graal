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
package com.oracle.svm.hosted.ide;

import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.svm.core.VM;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;

import jdk.graal.compiler.ide.IDEReport;
import jdk.graal.compiler.ide.IDEReportSnapshot;

/** Build-scoped IDE report bytes shared by export, split, and embedded storage. */
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
public final class IDEReportStorageData {
    private final IDEReportOptions.Configuration configuration;
    private final IDEReport report;

    private IDEReportSnapshot snapshot;
    private byte[] canonicalPayload;
    private byte[] envelope;

    public IDEReportStorageData(IDEReportOptions.Configuration configuration, IDEReport report) {
        this.configuration = configuration;
        this.report = report;
    }

    public boolean embeddingEnabled() {
        return configuration.storageModes().contains(IDEReportStorageMode.EMBED);
    }

    public void prepare() {
        if (snapshot != null) {
            return;
        }
        IDEReportSnapshot preparedSnapshot;
        try (var _ = TimerCollection.createTimerAndStart("ide-report-snapshot")) {
            preparedSnapshot = report.snapshot();
        }
        byte[] preparedCanonicalPayload = null;
        byte[] preparedEnvelope = null;
        if (!configuration.legacyExport()) {
            try (var _ = TimerCollection.createTimerAndStart("ide-report-serialization")) {
                preparedCanonicalPayload = IDEReportCanonicalPayload.create(preparedSnapshot, configuration.payloadScope());
            }
            if (embeddingEnabled() || configuration.storageModes().contains(IDEReportStorageMode.SPLIT)) {
                try (var _ = TimerCollection.createTimerAndStart("ide-report-compression")) {
                    preparedEnvelope = IDEReportEnvelope.encode(preparedCanonicalPayload, VM.getVendorVersion());
                }
            }
        }
        snapshot = preparedSnapshot;
        canonicalPayload = preparedCanonicalPayload;
        envelope = preparedEnvelope;
    }

    public IDEReportSnapshot snapshot() {
        ensurePrepared();
        return snapshot;
    }

    public byte[] canonicalPayload() {
        ensurePrepared();
        if (canonicalPayload == null) {
            throw new IllegalStateException("Legacy IDE reports do not have a canonical payload");
        }
        return canonicalPayload.clone();
    }

    public byte[] envelope() {
        ensurePrepared();
        if (envelope == null) {
            throw new IllegalStateException("IDE report envelope was not requested by the configured storage modes");
        }
        return envelope.clone();
    }

    private void ensurePrepared() {
        if (snapshot == null) {
            throw new IllegalStateException("IDE report storage data has not been prepared");
        }
    }
}
