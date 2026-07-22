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

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.core.util.UserError;
import com.oracle.svm.shared.option.HostedOptionKey;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.ide.IDEReport;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;

public final class IDEReportOptions {
    private IDEReportOptions() {
    }

    @Option(help = "Select IDE report storage: off, export, embed, embed,export, split, or embed,split.", type = OptionType.Expert) //
    public static final HostedOptionKey<String> IDEReportStorage = new HostedOptionKey<>(null) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, String oldValue, String newValue) {
            if (newValue != null && !newValue.strip().equalsIgnoreCase("off")) {
                GraalOptions.TrackNodeSourcePosition.update(values, true);
            }
        }
    };

    @Option(help = "Select the IDE report payload scope: full or minimal.", type = OptionType.Expert) //
    public static final HostedOptionKey<String> IDEReportPayloadScope = new HostedOptionKey<>(com.oracle.svm.hosted.ide.IDEReportPayloadScope.FULL.serializedName());

    public record Configuration(Set<IDEReportStorageMode> storageModes, IDEReportPayloadScope payloadScope, boolean enabled, boolean legacyExport) {
        public Configuration {
            storageModes = Set.copyOf(storageModes);
        }
    }

    public static Configuration resolve(OptionValues options) {
        try {
            return resolve(
                            IDEReport.Options.IDEReport.getValue(options),
                            IDEReport.Options.IDEReport.hasBeenSet(options),
                            IDEReportStorage.getValue(options),
                            IDEReportStorage.hasBeenSet(options),
                            IDEReportPayloadScope.getValue(options),
                            IDEReportPayloadScope.hasBeenSet(options));
        } catch (IllegalArgumentException exception) {
            throw UserError.abort("%s", exception.getMessage());
        }
    }

    public static Configuration resolve(boolean reportEnabled, boolean reportOptionSet, String storageValue, boolean storageOptionSet, String payloadScopeValue, boolean payloadScopeOptionSet) {
        IDEReportPayloadScope payloadScope = com.oracle.svm.hosted.ide.IDEReportPayloadScope.parse(payloadScopeValue);
        if (!storageOptionSet) {
            if (payloadScopeOptionSet) {
                throw new IllegalArgumentException("IDEReportPayloadScope requires an explicit non-off IDEReportStorage value.");
            }
            return new Configuration(reportEnabled ? EnumSet.of(IDEReportStorageMode.EXPORT) : EnumSet.noneOf(IDEReportStorageMode.class), payloadScope, reportEnabled, reportEnabled);
        }

        EnumSet<IDEReportStorageMode> storageModes = parseStorageModes(storageValue);
        if (storageModes.isEmpty()) {
            if (reportEnabled) {
                throw new IllegalArgumentException("IDEReportStorage=off conflicts with enabled IDE reporting.");
            }
            if (payloadScopeOptionSet) {
                throw new IllegalArgumentException("IDEReportPayloadScope cannot be used with IDEReportStorage=off.");
            }
            return new Configuration(storageModes, payloadScope, false, false);
        }
        if (reportOptionSet && !reportEnabled) {
            throw new IllegalArgumentException("-H:-IDEReport conflicts with non-off IDEReportStorage.");
        }
        return new Configuration(storageModes, payloadScope, true, false);
    }

    private static EnumSet<IDEReportStorageMode> parseStorageModes(String value) {
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.equals("off")) {
            return EnumSet.noneOf(IDEReportStorageMode.class);
        }
        EnumSet<IDEReportStorageMode> result = EnumSet.noneOf(IDEReportStorageMode.class);
        for (String component : normalized.split(",", -1)) {
            IDEReportStorageMode mode = switch (component.strip()) {
                case "export" -> IDEReportStorageMode.EXPORT;
                case "embed" -> IDEReportStorageMode.EMBED;
                case "split" -> IDEReportStorageMode.SPLIT;
                default -> throw new IllegalArgumentException("Unsupported IDE report storage value '" + value + "'.");
            };
            if (!result.add(mode)) {
                throw new IllegalArgumentException("Duplicate IDE report storage mode in '" + value + "'.");
            }
        }
        if (result.equals(EnumSet.of(IDEReportStorageMode.EXPORT)) || result.equals(EnumSet.of(IDEReportStorageMode.EMBED)) || result.equals(EnumSet.of(IDEReportStorageMode.SPLIT)) ||
                        result.equals(EnumSet.of(IDEReportStorageMode.EMBED, IDEReportStorageMode.EXPORT)) || result.equals(EnumSet.of(IDEReportStorageMode.EMBED, IDEReportStorageMode.SPLIT))) {
            return result;
        }
        throw new IllegalArgumentException("Unsupported IDE report storage combination '" + value + "'.");
    }
}
