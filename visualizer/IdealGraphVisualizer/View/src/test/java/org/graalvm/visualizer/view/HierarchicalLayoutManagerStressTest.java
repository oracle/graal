package org.graalvm.visualizer.view;

import jdk.graal.compiler.graphio.parsing.BinaryReader;
import jdk.graal.compiler.graphio.parsing.BinarySource;
import jdk.graal.compiler.graphio.parsing.GraphParser;
import jdk.graal.compiler.graphio.parsing.ModelBuilder;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.GraphDocumentVisitor;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.graalvm.visualizer.data.serialization.lazy.FileContent;
import org.graalvm.visualizer.data.serialization.lazy.LazyModelBuilder;
import org.graalvm.visualizer.data.serialization.lazy.ReaderErrors;
import org.graalvm.visualizer.graph.Block;
import org.graalvm.visualizer.graph.Connection;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.hierarchicallayout.HierarchicalClusterLayoutManager;
import org.graalvm.visualizer.hierarchicallayout.HierarchicalLayoutManager;
import org.graalvm.visualizer.layout.LayoutGraph;
import org.graalvm.visualizer.settings.layout.LayoutSettings;
import org.junit.Assume;
import org.junit.Test;
import org.openide.util.Exceptions;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static org.graalvm.visualizer.settings.layout.LayoutSettings.MAX_BLOCK_LAYER_LENGTH;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.MAX_LAYER_LENGTH;

public class HierarchicalLayoutManagerStressTest {
    static final String STRESS_TEST_PATH = "HierarchicalLayoutManagerStressTest.path";
    static final String STRESS_TEST_VERBOSE = "HierarchicalLayoutManagerStressTest.verbose";

    protected static GraphDocument loadData(File f) {
        try {
            final FileChannel channel = FileChannel.open(f.toPath(), StandardOpenOption.READ);
            FileContent content = new FileContent(f.toPath(), channel);
            BinarySource src = new BinarySource(null, content);

            GraphDocument targetDocument = new GraphDocument();
            ModelBuilder bld = new LazyModelBuilder(targetDocument, null);
            bld.setDocumentId("");
            GraphParser parser = new BinaryReader(src, bld);
            return parser.parse();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    LayoutSettings.LayoutSettingBean layoutSettings = LayoutSettings.getBean();
    boolean verbose = Boolean.getBoolean(STRESS_TEST_VERBOSE);

    @Test
    public void stressHierarchicalLayout() throws IOException {
        // Create BinarySource and parse document
        String pathname = System.getProperty(STRESS_TEST_PATH);
        Assume.assumeTrue("Must set " + STRESS_TEST_PATH + " property to run benchmark", pathname != null);

        Path path = new File(pathname).toPath();
        if (Files.isDirectory(path)) {
            try (Stream<Path> walk = Files.walk(path, 1)) {
                walk.forEach(this::layoutFile);
            }
        } else {
            layoutFile(path);
        }
    }

    private void layoutFile(Path path) {
        if (!path.toString().endsWith(".bgv")) {
            return;
        }
        System.err.println("Processing " + path);
        GraphDocument doc = loadData(path.toFile());
        GraphDocumentVisitor.visitAll(doc, this::doLayout);
    }

    /**
     * Resolve any lazy context.
     */
    private static InputGraph completeGraph(InputGraph g) {
        if (g instanceof Group.LazyContent) {
            Group.LazyContent<?> lg = (Group.LazyContent) g;
            if (!lg.isComplete()) {
                try {
                    lg.completeContents(null).get();
                } catch (InterruptedException | ExecutionException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
        return g;
    }

    private void doLayout(InputGraph inputGraph) {
        if (verbose) {
            System.err.println("Laying out " + inputGraph.getName());
        }

        boolean hasError = ReaderErrors.containsError(inputGraph, false);
        if (hasError) {
            System.err.println("Skipping " + inputGraph.getName() + " because of loading errors");
            return;
        }
        InputGraph graph = completeGraph(inputGraph);
        Diagram d = Diagram.createDiagram(graph, "");

        LayoutGraph layoutGraph = getLayoutGraph(d);

        HierarchicalLayoutManager mgr = new HierarchicalLayoutManager(HierarchicalLayoutManager.Combine.SAME_OUTPUTS, layoutSettings);
        mgr.setMaxLayerLength(layoutSettings.get(Integer.class, MAX_LAYER_LENGTH));
        mgr.doLayout(layoutGraph);

        Dimension size = layoutGraph.getSize();
        if (size == null) {
            throw new RuntimeException("Layout produced no size.");
        }

        layoutGraph = getLayoutGraph(d);

        HierarchicalClusterLayoutManager clusterManager = new HierarchicalClusterLayoutManager(HierarchicalLayoutManager.Combine.SAME_OUTPUTS, layoutSettings);
        clusterManager.getManager().setMaxLayerLength(layoutSettings.get(Integer.class, MAX_BLOCK_LAYER_LENGTH));
        clusterManager.getSubManager().setMaxLayerLength(layoutSettings.get(Integer.class, MAX_LAYER_LENGTH));
        clusterManager.doLayout(layoutGraph);

        size = layoutGraph.getSize();
        if (size == null) {
            throw new RuntimeException("Layout produced no size.");
        }
    }

    private static LayoutGraph getLayoutGraph(Diagram outputDiagram) {
        // Prepare layout input
        for (Figure f : outputDiagram.getFigures()) {
            f.setVisible(true);
            f.setBoundary(false);
        }
        for (Block b : outputDiagram.getBlocks()) {
            b.setVisible(true);
        }
        HashSet<Figure> figures = new HashSet<>(outputDiagram.getFigures());
        HashSet<Connection> edges = new HashSet<>(outputDiagram.getConnections());
        LayoutGraph layoutGraph = new LayoutGraph(edges, figures);
        return layoutGraph;
    }
}
