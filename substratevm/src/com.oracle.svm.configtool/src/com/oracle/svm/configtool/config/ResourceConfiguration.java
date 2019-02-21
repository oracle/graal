package com.oracle.svm.configtool.config;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ResourceConfiguration {
    private final Set<String> resources = new HashSet<>();

    public void add(String resource) {
        resources.add(resource);
    }

    public void addLocationIndependent(String resource) {
        add(resource);
    }

    public Set<String> getResources() {
        return Collections.unmodifiableSet(resources);
    }

    public void write(Writer writer) throws IOException {
        List<String> sorted = new ArrayList<>(resources);
        Collections.sort(sorted);

        for (String resource : sorted) {
            writer.append("-H:IncludeResources=.*/").append(resource).append(System.lineSeparator());
        }
    }
}
