package com.oracle.svm.hosted;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.util.json.JsonPrinter;
import com.oracle.svm.core.util.json.JsonWriter;

public class ResourceReporter {

    public static void printReport(Collection<Resources.ModuleResourceRecord> registeredResources) {
        Path reportLocation = NativeImageGenerator.generatedFiles(HostedOptionValues.singleton()).resolve("registered_resources.json");
        try (JsonWriter writer = new JsonWriter(reportLocation)) {
            JsonPrinter.printCollection(writer, registeredResources, Resources.ModuleResourceRecord.comparator(), ResourceReporter::resourceReportElement);
        } catch (IOException e) {
            throw VMError.shouldNotReachHere("Json writer cannot write to: " + reportLocation + "\n Reason: " + e.getMessage());
        }
    }

    private static void resourceReportElement(Resources.ModuleResourceRecord p, JsonWriter w) throws IOException {
        w.indent().newline();
        w.append('{').indent().newline();
        w.quote("resource").append(':').quote(p.resource());
        w.newline();
        w.quote("module").append(':').quote(p.module() == null ? "ALL_UNNAMED" : p.module().getName());
        w.unindent().newline().append('}');
        w.unindent();
    }
}
