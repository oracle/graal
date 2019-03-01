package com.oracle.svm.configure.config;

import java.io.IOException;
import java.util.Comparator;

import com.oracle.svm.configure.json.JsonPrintable;
import com.oracle.svm.configure.json.JsonWriter;

public class ResourceConfiguration implements JsonPrintable {
    private final MatchSet<String> resources = MatchSet.create(Comparator.naturalOrder(), (String s, JsonWriter w) -> w.append('{').quote("pattern").append(':').quote(s).append('}'));

    public void add(String resource) {
        resources.add(resource);
    }

    public void addLocationIndependent(String resource) {
        add(resource);
    }

    public void printJson(JsonWriter writer) throws IOException {
        writer.append('{').indent().newline();
        writer.quote("resources").append(':');
        resources.printJson(writer);
        writer.unindent().newline().append('}').newline();
    }
}
