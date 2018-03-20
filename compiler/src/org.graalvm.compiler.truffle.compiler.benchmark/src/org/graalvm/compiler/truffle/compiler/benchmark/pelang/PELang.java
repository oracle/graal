package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

import com.oracle.truffle.api.TruffleLanguage;

@TruffleLanguage.Registration(id = PELang.ID, name = "PELang", version = "0.1", mimeType = PELang.MIME_TYPE)
public final class PELang extends TruffleLanguage<PELangContext> {

    public static final String ID = "pelang";
    public static final String MIME_TYPE = "application/x-pelang";

    @Override
    protected PELangContext createContext(Env env) {
        return new PELangContext(this, env);
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return false;
    }

}
