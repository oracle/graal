package org.graalvm.igvutil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import jdk.graal.compiler.graphio.parsing.BinaryReader;
import jdk.graal.compiler.graphio.parsing.DataBinaryWriter;
import jdk.graal.compiler.graphio.parsing.ModelBuilder;
import jdk.graal.compiler.graphio.parsing.StreamSource;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames;

public final class Flattener {
    private final GraphDocument newDoc = new GraphDocument();
    private final Map<String, Group> groups = new HashMap<>();
    private final String flattenKey;

    public Flattener(String flattenKey) {
        this.flattenKey = flattenKey;
    }

    public GraphDocument getFlattenedDocument() {
        return newDoc;
    }

    public void save(String filename) {
        try {
            DataBinaryWriter.export(new File(filename), newDoc, null, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void visit(InputGraph graph) {
        Group group = (Group) graph.getParent();
        String id = graph.getProperties().get(flattenKey, String.class);
        if (id == null) {
            id = "<none>";
        }
        Group newGroup = groups.get(id);
        if (newGroup == null) {
            newGroup = new Group(newDoc, group.getID());
            newGroup.setMethod(group.getMethod());
            newGroup.updateProperties(group.getProperties());
            newGroup.getProperties().setProperty(KnownPropertyNames.PROPNAME_NAME, id);
            newDoc.addElement(newGroup);
            groups.put(id, newGroup);
        }
        newGroup.addElement(graph);
    }

    public void visitDump(InputStream stream) throws IOException {
        ModelBuilder mb = new ModelBuilder(new GraphDocument(), null) {
            @Override
            public InputGraph endGraph() {
                InputGraph graph = super.endGraph();
                visit(graph);
                return graph;
            }
        };
        new BinaryReader(new StreamSource(new BufferedInputStream(stream)), mb).parse();
    }
}
