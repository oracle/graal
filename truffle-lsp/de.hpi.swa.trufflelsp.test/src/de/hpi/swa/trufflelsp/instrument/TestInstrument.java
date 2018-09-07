package de.hpi.swa.trufflelsp.instrument;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapperRegistry;
import de.hpi.swa.trufflelsp.api.VirtualLanguageServerFileProvider;
import de.hpi.swa.trufflelsp.instrument.TestInstrumentOptionDescriptors;
import de.hpi.swa.trufflelsp.server.TruffleAdapter;

@Registration(id = "lspTestInstrument", name = "LspTestInstrument", version = "0.1", services = {VirtualLanguageServerFileProvider.class, ContextAwareExecutorWrapperRegistry.class,
                TruffleAdapterProvider.class})
public class TestInstrument extends TruffleInstrument implements TruffleAdapterProvider {

    private TruffleAdapter truffleAdapter;

    @com.oracle.truffle.api.Option(name = "", help = "", category = OptionCategory.USER) //
    static final OptionKey<Boolean> LspTestInstrument = new OptionKey<>(true);

    @Override
    protected void onCreate(Env env) {
        truffleAdapter = new TruffleAdapter(env);
        env.registerService(truffleAdapter);
        env.registerService(this);
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new TestInstrumentOptionDescriptors();
    }

    public TruffleAdapter getTruffleAdapter() {
        return truffleAdapter;
    }

}
