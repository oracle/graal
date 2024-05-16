package org.graalvm.igvutil;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import jdk.graal.compiler.graphio.parsing.BinaryReader;
import jdk.graal.compiler.graphio.parsing.ModelBuilder;
import jdk.graal.compiler.graphio.parsing.StreamSource;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.util.json.JsonBuilder;
import jdk.graal.compiler.util.json.JsonPrettyWriter;
import jdk.graal.compiler.util.json.JsonWriter;

public class IgvUtility {
    public static void main(String[] args) throws IOException {
        String bgvFile = args[0];
        GraphDocument document = openDocument(bgvFile);
        Set<String> documentProperties = null;
        if (args.length > 1) {
            documentProperties = Arrays.stream(args).skip(1).collect(Collectors.toSet());
        }
        try (JsonWriter json = new JsonPrettyWriter(new PrintWriter(System.out));
             JsonBuilder.ObjectBuilder documentBuilder = json.objectBuilder()) {
            JsonExporter exporter = new JsonExporter(null, documentProperties);
            exporter.writeElement(document, documentBuilder);
        }
    }

    private static InputStream openBGVStream(String filename) {
        try {
            InputStream stream = Files.newInputStream(Path.of(filename));
            if (filename.endsWith(".bgv.gz")) {
                return new GZIPInputStream(stream, 8192);
            } else {
                return new BufferedInputStream(stream);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static GraphDocument openDocument(String filename) {
        InputStream stream = openBGVStream(filename);
        GraphDocument doc = new GraphDocument();
        ModelBuilder mb = new ModelBuilder(doc, null);
        try {
            new BinaryReader(new StreamSource(stream), mb).parse();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return doc;
    }
}
