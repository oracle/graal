package de.hpi.swa.trufflelsp;

import com.oracle.truffle.api.TruffleContext;

public class TruffleContextWrapper implements AutoCloseable {
    private TruffleContext context;
    private Object enterObject;

    private TruffleContextWrapper(TruffleContext context) {
        this.context = context;
    }

    private void enter() {
        this.enterObject = this.context.enter();
    }

    public void close() {
        this.context.leave(this.enterObject);
        this.context.close();
    }

    public static TruffleContextWrapper createAndEnter(TruffleContext context) {
        TruffleContextWrapper wrapper = new TruffleContextWrapper(context);
        wrapper.enter();
        return wrapper;
    }
}
