package com.oracle.graal.pointsto.reports.causality.events;

import java.net.URI;

public final class ConfigurationFile extends CausalityEvent {
    public final URI uri;

    ConfigurationFile(URI uri) {
        this.uri = uri;
    }

    @Override
    public String toString() {
        String path;

        if (uri.getPath() != null)
            path = uri.getPath();
        else {
            path = uri.toString();
            if (path.startsWith("jar:file:"))
                path = path.substring(9);
        }

        return path + " [Configuration File]";
    }

    @Override
    public boolean root() {
        return true;
    }
}
