package de.hpi.swa.trufflelsp;

import org.graalvm.polyglot.Context;

public class TruffleContextWrapper implements AutoCloseable {
    private Context context;

    private TruffleContextWrapper(Context context) {
        this.context = context;
    }

    private void enter() {
        this.context.enter();
    }

    public void close() {
        this.context.leave();
        this.context.close();
    }

    public static TruffleContextWrapper createAndEnter(Context context) {
        TruffleContextWrapper wrapper = new TruffleContextWrapper(context);
        wrapper.enter();
        return wrapper;
    }
}
