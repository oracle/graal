package org.graalvm.tools.lsp.test.instrument;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.tools.lsp.api.ContextAwareExecutorRegistry;
import org.graalvm.tools.lsp.api.VirtualLanguageServerFileProvider;
import org.graalvm.tools.lsp.instrument.LSOptions;
import org.graalvm.tools.lsp.server.TruffleAdapter;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

@Registration(id = "lspTestInstrument", name = "LspTestInstrument", version = "0.1", services = {VirtualLanguageServerFileProvider.class, ContextAwareExecutorRegistry.class,
                TruffleAdapterProvider.class})
public class TestInstrument extends TruffleInstrument implements TruffleAdapterProvider {

    private TruffleAdapter truffleAdapter;

    @com.oracle.truffle.api.Option(name = "", help = "", category = OptionCategory.USER) //
    static final OptionKey<Boolean> LspTestInstrument = new OptionKey<>(true);

    @Override
    protected void onCreate(Env env) {
        truffleAdapter = new TruffleAdapter(env);
        env.getOptions().set(LSOptions.LanguageDeveloperMode, true);
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
