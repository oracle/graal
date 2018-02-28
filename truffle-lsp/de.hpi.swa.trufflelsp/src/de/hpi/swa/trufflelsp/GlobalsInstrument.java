package de.hpi.swa.trufflelsp;

import java.util.Map;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.source.Source;

@Registration(id = GlobalsInstrument.ID, services = Object.class)
public final class GlobalsInstrument extends TruffleInstrument {

    public static final String ID = "lsp-globals";

    private static class MySourceFilter implements SourcePredicate {

        public boolean test(Source source) {
            return TruffleAdapter.SOURCE_SECTION_ID.equals(source.getName());
        }

    }

    @Override
    protected void onCreate(final Env env) {
        env.registerService(this);
// env.getInstrumenter().attachListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
        env.getInstrumenter().attachListener(SourceSectionFilter.newBuilder().sourceIs(new MySourceFilter()).build(), new ExecutionEventListener() {
            @Override
            public void onEnter(EventContext context, VirtualFrame frame) {
                Iterable<Scope> topScopes = env.findTopScopes("python");
                for (Scope scope : topScopes) {
                    Object variables = scope.getVariables();
                    if (variables instanceof TruffleObject) {
                        TruffleObject truffleObj = (TruffleObject) variables;
                        try {
                            TruffleObject keys = ForeignAccess.sendKeys(Message.KEYS.createNode(), truffleObj, true);
                            boolean hasSize = ForeignAccess.sendHasSize(Message.HAS_SIZE.createNode(), keys);
                            if (!hasSize) {
                                System.out.println("No size!!!");
                            }
                            Map<Object, Object> map = ObjectStructures.asMap(new ObjectStructures.MessageNodes(), truffleObj);
                            if (!map.isEmpty()) {
                                System.out.println(map);
                            }
                        } catch (UnsupportedMessageException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            @Override
            public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
// System.out.println(result);
                for (Scope scope : env.findTopScopes("python")) {
                    Object variables = scope.getVariables();
                    if (variables instanceof TruffleObject) {
                        TruffleObject truffleObj = (TruffleObject) variables;
                        try {
                            TruffleObject keys = ForeignAccess.sendKeys(Message.KEYS.createNode(), truffleObj, true);
                            boolean hasSize = ForeignAccess.sendHasSize(Message.HAS_SIZE.createNode(), keys);
                            if (!hasSize) {
                                System.out.println("No size!!!");
                            }
                            Map<Object, Object> map = ObjectStructures.asMap(new ObjectStructures.MessageNodes(), truffleObj);
                            if (!map.isEmpty()) {
                                System.out.println(map);
                            }
                        } catch (UnsupportedMessageException e) {
                            e.printStackTrace();
                        }
                    }
                }

                for (Scope scope : env.findLocalScopes(context.getInstrumentedNode(), frame)) {
                    Object variables = scope.getVariables();
                    if (variables instanceof TruffleObject) {
                        TruffleObject truffleObj = (TruffleObject) variables;
                        try {
                            TruffleObject keys = ForeignAccess.sendKeys(Message.KEYS.createNode(), truffleObj, true);
                            boolean hasSize = ForeignAccess.sendHasSize(Message.HAS_SIZE.createNode(), keys);
                            if (!hasSize) {
                                System.out.println("No size!!!");
                            }
                            Map<Object, Object> map = ObjectStructures.asMap(new ObjectStructures.MessageNodes(), truffleObj);
                            if (!map.isEmpty()) {
                                System.out.println(map);
                            }
                        } catch (UnsupportedMessageException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            @Override
            public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
            }
        });

    }

}
