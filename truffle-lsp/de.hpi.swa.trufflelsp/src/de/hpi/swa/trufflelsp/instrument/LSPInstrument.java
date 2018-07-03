package de.hpi.swa.trufflelsp.instrument;

import java.io.PrintWriter;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

import de.hpi.swa.trufflelsp.LanguageSpecificHacks;
import de.hpi.swa.trufflelsp.launcher.TruffleLSPLauncher;

@Registration(id = LSPInstrument.ID, name = "Language Server", version = "0.1")
public class LSPInstrument extends TruffleInstrument {
    public static final String ID = "lsp";

// static final OptionType<HostAndPort> ADDRESS_OR_BOOLEAN = new OptionType<>("[[host:]port]",
// DEFAULT_ADDRESS, (address) -> {
// if (address.isEmpty() || address.equals("true")) {
// return DEFAULT_ADDRESS;
// } else {
// int colon = address.indexOf(':');
// String port;
// String host;
// if (colon >= 0) {
// port = address.substring(colon + 1);
// host = address.substring(0, colon);
// } else {
// port = address;
// host = null;
// }
// return new HostAndPort(host, port);
// }
// }, (address) -> address.verify());

    @com.oracle.truffle.api.Option(name = "", help = "Truffle Language Server", category = OptionCategory.USER) //
    static final OptionKey<Boolean> Lsp = new OptionKey<>(true);
//
// @com.oracle.truffle.api.Option(help = "Don't use loopback address. (default:false)", category =
// OptionCategory.EXPERT) //
// static final OptionKey<Boolean> Remote = new OptionKey<>(false);

    @com.oracle.truffle.api.Option(name = "Languagespecific.hacks", help = "Enable language specific hacks to get features which are not supported by some languages yet. (default:true)", category = OptionCategory.EXPERT) //
    static final OptionKey<Boolean> LanguageSpecificHacksOption = new OptionKey<>(true);

    @Override
    protected void onCreate(Env env) {
        OptionValues options = env.getOptions();
        if (options.hasSetOptions()) {
            LanguageSpecificHacks.enableLanguageSpecificHacks = options.get(LanguageSpecificHacksOption).booleanValue();
        }
        TruffleLSPLauncher.singleton().initialize(env, new PrintWriter(env.out()), new PrintWriter(env.out()));
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new LSPInstrumentOptionDescriptors();
    }

}
