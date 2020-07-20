
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Function;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

@SuppressWarnings("unchecked")
public class EmbeddingDoubled {
    public static void main(String... args) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context ctx = Context.newBuilder().out(out).err(out).build()) {
            Engine eng = ctx.getEngine();
            Instrument instrument = eng.getInstruments().get("agentscript");
            assert instrument != null : "AgentScript instrument found";

            Value fib = ctx.eval("js", "(function fib(n) {\n" +
                    "  if (n <= 2) return 1;\n" +
                    "  return fib(n - 1) + fib(n - 2);\n" +
                    "})"
            );
            int fib10 = fib.execute(10).asInt();
            assert fib10 == 55 : "fib(10) == " + fib10;
            assert out.size() == 0 : "No output yet: " + out.toString("UTF-8");

            String firstCode = "function fibenter(ctx, frame) {\n"
                    + "  print(`fib called with ${frame.n}`);\n"
                    + "}\n"
                    + "insight.on('enter', fibenter, {\n"
                    + "  roots: true,\n"
                    + "  rootNameFilter: (n) => n.indexOf('fib') >= 0\n"
                    + "});\n"
                    + "\n";
            String secondCode = "\n"
                    + "function fibreturn(ctx, frame) {\n"
                    + "  print(`fib return for ${frame['n']}`);\n"
                    + "}\n"
                    + "insight.on('return', fibreturn, {\n"
                    + "  roots: true,\n"
                    + "  rootNameFilter: '.*fib.*'\n"
                    + "});\n"
                    + "\n";

            try (
                AutoCloseable first = registerAgent(instrument, firstCode, "first.js");
                AutoCloseable second = registerAgent(instrument, secondCode, "second.js")
            ) {
                int fib11 = fib.execute(11).asInt();
                assert fib11 == 89 : "fib(11) == " + fib11;

                String txt = out.toString("UTF-8");
                assert txt.contains("fib called with 7") : "Expecting debug output:\n" + txt;
                assert txt.contains("fib return for 7") : "Expecting debug output:\n" + txt;
            }

            out.reset();
            assert out.size() == 0 : "Empty again";

            int fib12 = fib.execute(12).asInt();
            assert fib12 == 144 : "fib(12) == " + fib12;

            assert out.size() == 0 : "No output again:\n" + out.toString("UTF-8");
        }
        System.out.println("Everything is OK!");
    }

    private static AutoCloseable registerAgent(Instrument instrument, String code, String name) throws InterruptedException, IOException {
        Function<Source,?> api = instrument.lookup(Function.class);
        Source src = Source.newBuilder("js", code, name).build();
        Object handle = api.apply(src);
        return (AutoCloseable) handle;
    }
}
