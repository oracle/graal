
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.source.Source;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.graalvm.tools.insight.Insight;

@TruffleInstrument.Registration(
    id = "registerSymbols",
    name = "Registers new symbols for Insight",
    version = "demo",
    services = { BiConsumer.class, Insight.SymbolProvider.class }
)
@SuppressWarnings("unchecked")
public final class EmbRegisterSymbolInstrument extends TruffleInstrument
implements BiConsumer<String, String> {
    private final Map<String,String> values = new HashMap<>();

    @Override
    protected void onCreate(Env env) {
        env.registerService(new Insight.SymbolProvider() {
            @Override
            public Map<String, Object> symbolsWithValues() throws Exception {
                Map<String, Object> map = new HashMap<>();
                for (Map.Entry<String, String> symbolAndCode : values.entrySet()) {
                    Source src = Source.newBuilder("js", symbolAndCode.getValue(), symbolAndCode.getKey() + ".js").build();
                    CallTarget target = env.parse(src);
                    Object value = target.call();
                    map.put(symbolAndCode.getKey(), value);
                }
                return map;
            }
        });
        env.registerService(this);
    }


    @Override
    public void accept(String n, String v) {
        values.put(n, v);
    }
}
