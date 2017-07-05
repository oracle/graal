package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertSame;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.LanguageSPITestLanguage.LanguageContext;

@TruffleLanguage.Registration(id = LanguageSPITestLanguage.ID, name = LanguageSPITestLanguage.ID, version = "1.0", mimeType = LanguageSPITestLanguage.ID)
public class LanguageSPITestLanguage extends TruffleLanguage<LanguageContext> {

    static final String ID = "LanguageSPITest";

    static class LanguageContext {

        int disposeCalled;

    }

    public static LanguageContext getContext() {
        return getCurrentContext(LanguageSPITestLanguage.class);
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        return null;
    }

    @Override
    protected LanguageContext createContext(Env env) {
        LanguageSPITest.langContext = new LanguageContext();
        return LanguageSPITest.langContext;
    }

    @Override
    protected void disposeContext(LanguageContext context) {
        assertSame(getContext(), context);
        assertSame(context, getContextReference().get());

        assertSame(context, new RootNode(this) {
            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }
        }.getLanguage(LanguageSPITestLanguage.class).getContextReference().get());

        context.disposeCalled++;
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