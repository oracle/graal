
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.function.Function;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

@SuppressWarnings("unchecked")
public class Embedding {
    public static void main(String... args) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context ctx = Context.newBuilder().out(out).err(out).build()) {
            Engine eng = ctx.getEngine();
            Instrument instrument = eng.getInstruments().get("insight");
            assert instrument != null : "Insight instrument found";
            OptionDescriptors ops = instrument.getOptions();
            assert ops != null : "Options found";
            OptionDescriptor script = ops.get("insight");
            assert script != null : "agent script: " + script;

            Value fib = ctx.eval("js", "(function fib(n) {\n" +
                    "  if (n <= 2) return 1;\n" +
                    "  return fib(n - 1) + fib(n - 2);\n" +
                    "})"
            );
            int fib10 = fib.execute(10).asInt();
            assert fib10 == 55 : "fib(10) == " + fib10;


            assert out.size() == 0 : "No output yet: " + out.toString("UTF-8");


            try (AutoCloseable c = registerAgent(instrument)) {
                int fib11 = fib.execute(11).asInt();
                assert fib11 == 89 : "fib(11) == " + fib11;

                String txt = out.toString("UTF-8");
                assert txt.contains("calling fib with 5") : "Expecting debug output:\n" + txt;
            }

            out.reset();
            assert out.size() == 0 : "Empty again";

            int fib12 = fib.execute(12).asInt();
            assert fib12 == 144 : "fib(12) == " + fib12;

            assert out.size() == 0 : "No output again:\n" + out.toString("UTF-8");
        }
        System.out.println("Everything is OK!");
    }

    private static AutoCloseable registerAgent(Instrument instrument) throws InterruptedException, IOException {
        Function<Source,?> api = instrument.lookup(Function.class);
        assert api != null : "Instrument exposes a function like API";
        ClassLoader l = api.getClass().getClassLoader();
        assert l == null : "No special loader found: " + l;

        URL agentScript = Embedding.class.getResource("agent-embedding.js");
        assert agentScript != null : "Script found";

        Source src = Source.newBuilder("js", agentScript).build();
        Object[] handle = { null };
        Thread register = new Thread("register agent") {
            @Override
            public void run() {
                handle[0] = api.apply(src);
            }
        };
        register.start();
        register.join();

        assert handle[0] != null : "Enabling agent script returns its closable handle";

        return (AutoCloseable) handle[0];
    }
}
