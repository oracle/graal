package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

import com.oracle.truffle.api.TruffleLanguage.Env;

public final class PELangContext {

    private final PELang pelang;
    private final Env env;

    public PELangContext(PELang pelang, Env env) {
        this.pelang = pelang;
        this.env = env;
    }

    public PELang getLanguage() {
        return pelang;
    }

    public Env getEnv() {
        return env;
    }

}
