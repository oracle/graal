package org.graalvm.visualizer.data.serialization.lazy;

import org.graalvm.visualizer.data.serialization.BinaryMap;

import jdk.graal.compiler.graphio.parsing.DocumentFactory;
import jdk.graal.compiler.graphio.parsing.ModelBuilder;
import jdk.graal.compiler.graphio.parsing.NameTranslator;
import jdk.graal.compiler.graphio.parsing.ParseMonitor;
import jdk.graal.compiler.graphio.parsing.model.*;

/**
 * Base class for other model builders in this package.
 */
public class LazyModelBuilder extends ModelBuilder {

    public LazyModelBuilder(GraphDocument rootDocument, ParseMonitor monitor) {
        super(rootDocument, monitor);
    }

    public LazyModelBuilder(DocumentFactory factory, ParseMonitor monitor) {
        super(factory, monitor);
    }

    @Override
    public NameTranslator prepareNameTranslator() {
        BinaryMap versions = BinaryMap.versions();
        Properties.Entity versionHolder = folder() != null ? (Properties.Entity) folder() : getEntity();
        final String PREFIX = "version.";
        for (Property<?> p : versionHolder.getProperties()) {
            if (p.getName().startsWith(PREFIX)) {
                versions.request(p.getName().substring(PREFIX.length()), p.getValue().toString());
            }
        }
        return versions;
    }

    @Override
    public void reportLoadingError(String logMessage) {
        super.reportLoadingError(logMessage);
        FolderElement parent = graph();
        Folder folder = folder();
        if (parent == null) {
            parent = folder;
            folder = null;
        }
        ReaderErrors.addError(parent, folder, logMessage);
    }
}
