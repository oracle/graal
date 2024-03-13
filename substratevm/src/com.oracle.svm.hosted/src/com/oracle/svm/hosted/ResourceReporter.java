package com.oracle.svm.hosted;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.util.json.JsonPrinter;
import com.oracle.svm.core.util.json.JsonWriter;

public class ResourceReporter {

    public record SourceSizePair(String source, String size) {
        // NOTE: size is string because it could be NEGATIVE QUERY
        public static Comparator<SourceSizePair> comparator() {
            return Comparator.comparing(SourceSizePair::source);
        }
    }

    public record ResourceReportEntry(Module module, String resourceName, List<SourceSizePair> entries) {
        public static Comparator<ResourceReportEntry> comparator() {
            return Comparator.comparing(ResourceReportEntry::resourceName);
        }
    }

    public static void printReport(Collection<ResourceReportEntry> registeredResources) {
        Path reportLocation = NativeImageGenerator.generatedFiles(HostedOptionValues.singleton()).resolve("registered_resources.json");
        try (JsonWriter writer = new JsonWriter(reportLocation)) {
            JsonPrinter.printCollection(writer, registeredResources, ResourceReportEntry.comparator(), ResourceReporter::resourceReportElement);
        } catch (IOException e) {
            throw VMError.shouldNotReachHere("Json writer cannot write to: " + reportLocation + "\n Reason: " + e.getMessage());
        }
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
        w.quote("entries").append(":");
        JsonPrinter.printCollection(w, p.entries(), SourceSizePair.comparator(), ResourceReporter::sourceElement);
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
}
