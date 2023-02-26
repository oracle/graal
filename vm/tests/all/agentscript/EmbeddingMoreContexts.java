
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.function.Function;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

@SuppressWarnings("unchecked")
public class EmbeddingMoreContexts {
    public static void main(String... args) throws Exception {
        Integer repeat = Integer.parseInt(args[0]);
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Engine eng = Engine.create();
        Instrument instrument = eng.getInstruments().get("insight");
        assert instrument != null : "Insight instrument found";
        
        Source fibSrc = Source.create("js", "(function fib(n) {\n" +
            "  if (n <= 2) return 1;\n" +
            "  return fib(n - 1) + fib(n - 2);\n" +
            "})"
        );
        
        try (AutoCloseable c = registerAgent(instrument)) {
            for (int i = 1; i <= repeat; i++) {
                out.reset();
                assert out.size() == 0 : "Output is clear " + out.size();
                try (Context ctx = Context.newBuilder().engine(eng).out(out).err(out).build()) {
                    Value fib = ctx.eval(fibSrc);

                    int value = fib.execute(10 + i).asInt();
                    String txt = out.toString("UTF-8");
                    assert txt.contains("calling fib with 5") : "Expecting debug output (round " + i + ") :\n" + txt;
                    System.out.println(txt.split("\n")[1]);
                    System.out.println("result is " + value);
                }
            }
        }
        
        System.out.println("OK " + repeat + " times!");
    }

    private static AutoCloseable registerAgent(Instrument instrument) throws InterruptedException, IOException {
        Function<Source,AutoCloseable> api = instrument.lookup(Function.class);
        assert api != null : "Instrument exposes a function like API";
        ClassLoader l = api.getClass().getClassLoader();
        assert l == null : "No special loader found: " + l;

        URL agentScript = EmbeddingMoreContexts.class.getResource("agent-embedding.js");
        assert agentScript != null : "Script found";

        Source src = Source.newBuilder("js", agentScript).build();
        return api.apply(src);
    }
}
