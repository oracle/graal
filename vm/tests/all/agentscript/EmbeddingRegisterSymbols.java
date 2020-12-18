
import java.io.ByteArrayOutputStream;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

@SuppressWarnings("unchecked")
public class EmbeddingRegisterSymbols {
    public static void main(String... args) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context ctx = Context.newBuilder().out(out).err(out).build()) {
            Engine eng = ctx.getEngine();
            Instrument instrument = eng.getInstruments().get("insight");
            assert instrument != null : "Insight instrument found";

            BiConsumer<String,Value> registerSymbols = instrument.lookup(BiConsumer.class);
            assert registerSymbols != null : "Insight supports registration of symbols";
            registerSymbols.accept("msg", Value.asValue("Stop: "));
            registerSymbols.accept("count", Value.asValue(42));

            Function<Source,AutoCloseable> registerScripts = instrument.lookup(Function.class);
            assert registerScripts != null : "Insight supports registration of scripts";
            Source src = Source.newBuilder("js",
                "insight.on('enter', (ctx, frame) => { if (--count <= 0) throw msg + count; }, { roots : true });",
                "insight.js"
            ).build();

            try (AutoCloseable close = registerScripts.apply(src)) {
                Value fib = ctx.eval("js", "(function fib(n) {\n" +
                        "  if (n <= 2) return 1;\n" +
                        "  return fib(n - 1) + fib(n - 2);\n" +
                        "})"
                );
                int fib10 = fib.execute(10).asInt();
                assert fib10 == 55 : "fib(10) == " + fib10;
            } finally {
                System.out.println(out.toString("UTF-8"));
            }
        }
        System.out.println("The execution shall end with an exception!");
    }
}
