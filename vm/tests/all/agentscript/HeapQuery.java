
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.graalvm.visualvm.lib.jfluid.heap.Heap;
import org.graalvm.visualvm.lib.jfluid.heap.HeapFactory;
import org.graalvm.visualvm.lib.profiler.oql.engine.api.OQLEngine;

public class HeapQuery {
    public static void main(String... args) throws Exception {
        final File file = new File(args[0]);
        if (!file.exists()) {
            throw new IOException("Cannot find " + file);
        }
        Heap heap = HeapFactory.createHeap(file);
        System.setProperty("polyglot.js.nashorn-compat", "true");
        final OQLEngine eng = new OQLEngine(heap);
        final String script;
        if (args[1].equals("-e")) {
            script = args[2];
        } else {
            script = new String(Files.readAllBytes(new File(args[1]).toPath()), StandardCharsets.UTF_8);
        }
        eng.executeQuery(script, OQLEngine.ObjectVisitor.DEFAULT);
    }
}
