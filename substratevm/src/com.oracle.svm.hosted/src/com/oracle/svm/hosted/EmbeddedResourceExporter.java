/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import static com.oracle.svm.core.jdk.Resources.NEGATIVE_QUERY_MARKER;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.configure.ConditionalRuntimeValue;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.jdk.resources.ResourceStorageEntryBase;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.util.json.JsonPrinter;
import jdk.graal.compiler.util.json.JsonWriter;

@Platforms(Platform.HOSTED_ONLY.class)
public class EmbeddedResourceExporter {

    public record SourceSizePair(String source, int size) {
    }

    public record ResourceReportEntry(Module module, String resourceName, List<SourceSizePair> entries, boolean isDirectory, boolean isMissing) {
    }

    public static void printReport(JsonWriter writer) throws IOException {
        JsonPrinter.printCollection(writer,
                        getResourceReportEntryList(EmbeddedResourcesInfo.singleton().getRegisteredResources()),
                        Comparator.comparing(EmbeddedResourceExporter.ResourceReportEntry::resourceName),
                        EmbeddedResourceExporter::resourceReportElement);
    }

    private static void resourceReportElement(ResourceReportEntry p, JsonWriter w) throws IOException {
        w.indent().newline();
        w.appendObjectStart().newline();
        w.appendKeyValue("name", p.resourceName()).appendSeparator();
        w.newline();
        if (p.module() != null) {
            w.appendKeyValue("module", p.module().getName()).appendSeparator();
            w.newline();
        }

        if (p.isDirectory()) {
            w.appendKeyValue("is_directory", true).appendSeparator();
            w.newline();
        }

        if (p.isMissing()) {
            w.appendKeyValue("is_missing", true).appendSeparator();
            w.newline();
        }

        w.quote("entries").append(":");
        JsonPrinter.printCollection(w, p.entries(), Comparator.comparing(SourceSizePair::source), EmbeddedResourceExporter::sourceElement);
        w.unindent().newline().appendObjectEnd();
    }

    private static void sourceElement(SourceSizePair p, JsonWriter w) throws IOException {
        w.indent().newline();
        w.appendObjectStart().newline();
        w.appendKeyValue("origin", p.source()).appendSeparator();
        w.newline();
        w.appendKeyValue("size", p.size());
        w.newline().appendObjectEnd();
        w.unindent();
    }

    private static List<ResourceReportEntry> getResourceReportEntryList(ConcurrentHashMap<Resources.ModuleResourceKey, List<String>> collection) {
        if (collection.isEmpty()) {
            LogUtils.warning("Attempting to write information about resources without data being collected. " +
                            "Either the GenerateEmbeddedResourcesFile hosted option is disabled " +
                            "or the application doesn't have any resource registered");

            return Collections.emptyList();
        }

        List<ResourceReportEntry> resourceInfoList = new ArrayList<>();
        EconomicMap<Resources.ModuleResourceKey, ConditionalRuntimeValue<ResourceStorageEntryBase>> resourceStorage = Resources.singleton().getResourceStorage();
        resourceStorage.getKeys().forEach(key -> {
            Module module = key.module();
            String resourceName = key.resource();

            ResourceStorageEntryBase storageEntry = resourceStorage.get(key).getValueUnconditionally();
            List<String> registeredEntrySources = collection.get(key);

            if (registeredEntrySources == null && storageEntry != NEGATIVE_QUERY_MARKER) {
                throw VMError.shouldNotReachHere("Resource: " + resourceName +
                                " from module: " + module +
                                " wasn't register from ResourcesFeature. It should never happen except for NEGATIVE_QUERIES in some cases");
            }

            if (storageEntry == NEGATIVE_QUERY_MARKER) {
                resourceInfoList.add(new ResourceReportEntry(module, resourceName, new ArrayList<>(), false, true));
                return;
            }

            List<EmbeddedResourceExporter.SourceSizePair> sources = new ArrayList<>();
            for (int i = 0; i < registeredEntrySources.size(); i++) {
                String source = registeredEntrySources.get(i);
                int size = storageEntry.getData().get(i).length;
                sources.add(new SourceSizePair(source, size));
            }

            boolean isDirectory = storageEntry.isDirectory();
            resourceInfoList.add(new ResourceReportEntry(module, resourceName, sources, isDirectory, false));
        });

        return resourceInfoList;
    }
}
