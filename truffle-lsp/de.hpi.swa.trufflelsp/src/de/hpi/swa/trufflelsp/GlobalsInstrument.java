package de.hpi.swa.trufflelsp;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

//@Registration(id = GlobalsInstrument.ID, services = Object.class)
public final class GlobalsInstrument extends TruffleInstrument {

    public static final String ID = "lsp-globals";

    @Override
    protected void onCreate(final Env env) {
        env.registerService(this);
        env.getInstrumenter().attachListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
            @Override
            public void onEnter(EventContext context, VirtualFrame frame) {
                Iterable<Scope> topScopes = env.findTopScopes("python");
                for (Scope scope : topScopes) {
                    Object variables = scope.getVariables();
                    System.out.println("vars: " + variables);
                }
            }

            @Override
            public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
            }

            @Override
            public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
            }
        });

    }

}
