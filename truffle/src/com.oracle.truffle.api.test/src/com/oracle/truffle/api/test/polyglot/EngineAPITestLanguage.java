package com.oracle.truffle.api.test.polyglot;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.test.polyglot.EngineAPITestLanguage.LanguageContext;

@TruffleLanguage.Registration(id = EngineAPITestLanguage.ID, implementationName = EngineAPITestLanguage.IMPL_NAME, name = EngineAPITestLanguage.NAME, version = EngineAPITestLanguage.VERSION, mimeType = EngineAPITestLanguage.MIME)
public class EngineAPITestLanguage extends TruffleLanguage<LanguageContext> {

    static final String ID = "EngineAPITestLanguage";
    static final String NAME = "Name";
    static final String IMPL_NAME = "ImplName";
    static final String VERSION = "Version";
    static final String MIME = "Mime";

    static final String Option1_HELP = "Option1_HELP";
    static final boolean Option1_DEPRECATED = false;
    static final OptionCategory Option1_CATEGORY = OptionCategory.USER;
    static final String Option1_NAME = EngineAPITestLanguage.ID + ".Option1";
    static final String Option1_DEFAULT = EngineAPITestLanguage.ID + "Option1_Default";

    static final String Option2_HELP = "Option2_HELP";
    static final boolean Option2_DEPRECATED = true;
    static final OptionCategory Option2_CATEGORY = OptionCategory.EXPERT;
    static final String Option2_NAME = EngineAPITestLanguage.ID + "";
    static final String Option2_DEFAULT = EngineAPITestLanguage.ID + "Option2_Default";

    static final String Option3_HELP = "Option2_HELP";
    static final boolean Option3_DEPRECATED = true;
    static final OptionCategory Option3_CATEGORY = OptionCategory.DEBUG;
    static final String Option3_NAME = EngineAPITestLanguage.ID + ".Option3";
    static final String Option3_DEFAULT = EngineAPITestLanguage.ID + "Option3_Default";

    @Option(category = OptionCategory.USER, help = Option1_HELP, deprecated = Option1_DEPRECATED) //
    static final OptionKey<String> Option1 = new OptionKey<>(Option1_DEFAULT);

    @Option(category = OptionCategory.EXPERT, name = "", help = Option2_HELP, deprecated = Option2_DEPRECATED) //
    static final OptionKey<String> Option2 = new OptionKey<>(Option2_DEFAULT);

    @Option(category = OptionCategory.DEBUG, help = Option3_HELP, deprecated = Option3_DEPRECATED) //
    static final OptionKey<String> Option3 = new OptionKey<>(Option3_DEFAULT);

    static class LanguageContext {
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new EngineAPITestLanguageOptionDescriptors();
    }

    public static LanguageContext getContext() {
        return getCurrentContext(EngineAPITestLanguage.class);
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        return null;
    }

    @Override
    protected LanguageContext createContext(Env env) {
        EngineAPITest.langContext = new LanguageContext();
        return EngineAPITest.langContext;
    }

    @Override
    protected void disposeContext(LanguageContext context) {

    }

    @Override
    protected Object lookupSymbol(LanguageContext context, String symbolName) {
        return super.lookupSymbol(context, symbolName);
    }

    @Override
    protected Object getLanguageGlobal(LanguageContext context) {
        return null;
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return false;
    }

}