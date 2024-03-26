package com.oracle.svm.hosted;

import static com.oracle.svm.core.jdk.Resources.NEGATIVE_QUERY_MARKER;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.jdk.resources.ResourceStorageEntryBase;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.util.json.JsonPrinter;
import com.oracle.svm.core.util.json.JsonWriter;

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

    private static List<ResourceReportEntry> getResourceReportEntryList(ConcurrentHashMap<Resources.ModuleResourceRecord, List<String>> collection) {
        List<ResourceReportEntry> resourceInfoList = new ArrayList<>();
        EconomicMap<Resources.ModuleResourceRecord, ResourceStorageEntryBase> resourceStorage = Resources.singleton().getResourceStorage();
        resourceStorage.getKeys().forEach(key -> {
            Module module = key.module();
            String resourceName = key.resource();

            ResourceStorageEntryBase storageEntry = resourceStorage.get(key);
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
