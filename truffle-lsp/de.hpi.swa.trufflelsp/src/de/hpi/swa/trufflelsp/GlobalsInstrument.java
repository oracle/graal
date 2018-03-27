package de.hpi.swa.trufflelsp;

import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

@Registration(id = GlobalsInstrument.ID, services = GlobalsInstrument.class)
public final class GlobalsInstrument extends TruffleInstrument {

    public static final String ID = "lsp-globals";
    public Set<URI> uris = new HashSet<>();

    private static class MySourceFilter implements SourcePredicate {

        public boolean test(Source source) {
            return TruffleAdapter.SOURCE_SECTION_ID.equals(source.getName());
        }

    }

    private static class MySourceFilterRuby implements SourcePredicate {

        public boolean test(Source source) {
// return TruffleAdapter.SOURCE_SECTION_ID_RUBY.equals(source.getName());

            return TruffleAdapter.RUBY_DUMMY_URI.equals(source.getURI());
        }

    }

    private static class MyRubyFilter implements SourcePredicate {

        public boolean test(Source source) {
            return "RubySampleSection".equals(source.getName());
        }
    }

    private static class MyPythonFilter implements SourcePredicate {

        public boolean test(Source source) {
            return "PythonSampleSection".equals(source.getName());
        }
    }

    private Env env;

    @Override
    protected void onCreate(final Env env) {
        this.setEnv(env);
        env.registerService(this);

        env.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().sourceIs(new MyRubyFilter()).build(), new ExecutionEventListener() {

            public void onEnter(EventContext context, VirtualFrame frame) {
                System.out.println("onEnter ruby");
            }

            public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                System.out.println("onReturnValue ruby");
            }

            public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                System.out.println("onReturnExceptional ruby");
            }

        });

        env.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().sourceIs(new MyPythonFilter()).build(), new ExecutionEventListener() {

            public void onEnter(EventContext context, VirtualFrame frame) {
                System.out.println("onEnter python");
            }

            public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                System.out.println("onReturnValue python");
            }

            public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                System.out.println("onReturnExceptional python");
            }

        });

// env.getInstrumenter().attachListener(SourceSectionFilter.newBuilder().lineIs(1).build(), new
// ExecutionEventListener() {
//
// public void onEnter(EventContext context, VirtualFrame frame) {
//// System.out.println("onEnter " +
//// context.getInstrumentedNode().getSourceSection().getSource().getURI());
// uris.add(context.getInstrumentedNode().getSourceSection().getSource().getURI());
// }
//
// public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
//// System.out.println("onReturn");
// }
//
// public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
//// System.out.println("onExceptional");
// }
// });

        env.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().sourceIs(new MySourceFilter()).build(), new ExecutionEventListener() {
            @Override
            public void onEnter(EventContext context, VirtualFrame frame) {
// Iterable<Scope> topScopes = env.findTopScopes("ruby");
// for (Scope scope : topScopes) {
// Object variables = scope.getVariables();
// if (variables instanceof TruffleObject) {
// TruffleObject truffleObj = (TruffleObject) variables;
// try {
// TruffleObject keys = ForeignAccess.sendKeys(Message.KEYS.createNode(), truffleObj, true);
// boolean hasSize = ForeignAccess.sendHasSize(Message.HAS_SIZE.createNode(), keys);
// if (!hasSize) {
// System.out.println("No size!!!");
// }
// Map<Object, Object> map = ObjectStructures.asMap(new ObjectStructures.MessageNodes(),
// truffleObj);
// if (!map.isEmpty()) {
// System.out.println(map);
// }
// } catch (UnsupportedMessageException e) {
// e.printStackTrace();
// }
// }
// }

// try {
// CallTarget callTarget =
// env.parse(Source.newBuilder("2+2+asd").name("sample.py").mimeType("application/x-python").build());
// System.out.println(callTarget);
//// callTarget.call();
// } catch (IOException e) {
// e.printStackTrace();
// }
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
                            Map<Object, Object> map = ObjectStructures.asMap(new ObjectStructures.MessageNodes(),
                                            truffleObj);
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
                            Map<Object, Object> map = ObjectStructures.asMap(new ObjectStructures.MessageNodes(),
                                            truffleObj);
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

    public Env getEnv() {
        return env;
    }

    private void setEnv(Env env) {
        this.env = env;
    }

}
