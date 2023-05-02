package com.oracle.truffle.espresso.runtime.dispatch.messages;

import java.util.function.Consumer;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.nodes.RootNode;

public final class InteropMessageRootNode extends RootNode {
    private final InteropMessage node;
    private final Consumer<InteropException> interopExceptionHandler;

    public InteropMessageRootNode(TruffleLanguage<?> language, InteropMessage node) {
        this(language, node, (ex) -> {
            throw sneakyThrow(ex);
        });
    }

    public InteropMessageRootNode(TruffleLanguage<?> language, InteropMessage node, Consumer<InteropException> handler) {
        super(language);
        this.node = insert(node);
        this.interopExceptionHandler = handler;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            return node.execute(frame.getArguments());
        } catch (InteropException e) {
            interopExceptionHandler.accept(e);
            return null;
        }
    }

    @Override
    public String getName() {
        return "RootNode for interop message: '" + node.name() + "'.";
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable ex) throws T {
        throw (T) ex;
    }
}
