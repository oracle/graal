package de.hpi.swa.trufflelsp;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

@Registration(id = EnvironmentProvider.ID, services = EnvironmentProvider.class)
public class EnvironmentProvider extends TruffleInstrument {
    public static final String ID = "lsp-environment";

    private Env env;

    public Env getEnv() {
        return env;
    }

    public void setEnv(Env env) {
        this.env = env;
    }

    @Override
    protected void onCreate(@SuppressWarnings("hiding") Env env) {
        this.setEnv(env);
        env.registerService(this);
    }
}
