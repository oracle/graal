package com.oracle.svm.hosted;

import static com.oracle.svm.core.jdk.Resources.NEGATIVE_QUERY_MARKER;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.jdk.resources.ResourceStorageEntryBase;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.util.json.JsonPrinter;
import com.oracle.svm.core.util.json.JsonWriter;

@Platforms(Platform.HOSTED_ONLY.class)
public class EmbeddedResourceExporter {

    public record SourceSizePair(String source, String size) {
        // NOTE: size is string because it could be NEGATIVE QUERY
    }

    public record ResourceReportEntry(Module module, String resourceName, List<SourceSizePair> entries, Boolean isDirectory) {
    }

    private final List<ResourceReportEntry> resources;

    public static EmbeddedResourceExporter singleton() {
        return ImageSingletons.lookup(EmbeddedResourceExporter.class);
    }

    public EmbeddedResourceExporter(ConcurrentHashMap<Resources.ModuleResourceRecord, List<String>> registeredResources) {
        this.resources = getResourceReportEntryList(registeredResources);
    }

    public void printReport(JsonWriter writer) throws IOException {
        JsonPrinter.printCollection(writer, this.resources, Comparator.comparing(EmbeddedResourceExporter.ResourceReportEntry::resourceName), EmbeddedResourceExporter::resourceReportElement);
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
        if (p.isDirectory() != null) {
            w.appendKeyValue("directory", p.isDirectory()).appendSeparator();
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

    private List<ResourceReportEntry> getResourceReportEntryList(ConcurrentHashMap<Resources.ModuleResourceRecord, List<String>> collection) {
        List<ResourceReportEntry> resourceInfoList = new ArrayList<>();
        EconomicMap<Resources.ModuleResourceRecord, ResourceStorageEntryBase> resourceStorage = Resources.singleton().getResourceStorage();
        resourceStorage.getKeys().forEach(key -> {
            Module module = key.module();
            String resourceName = key.resource();

            ResourceStorageEntryBase storageEntry = resourceStorage.get(key);
            List<String> registeredEntrySources = collection.get(key);
            List<EmbeddedResourceExporter.SourceSizePair> sources = new ArrayList<>();

            // if registeredEntrySource is null we are processing
            if (registeredEntrySources == null) {
                if (storageEntry != NEGATIVE_QUERY_MARKER) {
                    VMError.shouldNotReachHere("Resource: " + resourceName +
                                    " from module: " + module +
                                    " wasn't register from ResourcesFeature. It should never happen except for NEGATIVE_QUERIES in some cases");
                }
                sources.add(new EmbeddedResourceExporter.SourceSizePair("n/a", "NEGATIVE QUERY"));
            } else {
                for (int i = 0; i < registeredEntrySources.size(); i++) {
                    String source = registeredEntrySources.get(i);
                    String size = storageEntry == NEGATIVE_QUERY_MARKER ? "NEGATIVE QUERY" : String.valueOf(storageEntry.getData().get(i).length);
                    sources.add(new EmbeddedResourceExporter.SourceSizePair(source, size));
                }
            }

            Boolean isDirectory = storageEntry == NEGATIVE_QUERY_MARKER ? null : storageEntry.isDirectory();
            resourceInfoList.add(new EmbeddedResourceExporter.ResourceReportEntry(module, resourceName, sources, isDirectory));
        });

        return resourceInfoList;
    }
}
