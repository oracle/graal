package com.oracle.truffle.espresso.runtime.dispatch.messages;

public abstract class InteropNodes {
    private final InteropNodes superInstance;
    private final Class<?> dispatchClass;

    public InteropNodes(Class<?> dispatchClass, InteropNodes superInstance) {
        this.dispatchClass = dispatchClass;
        this.superInstance = superInstance;
    }

    public final void register() {
        InteropNodes curr = this;
        while (curr != null) {
            curr.registerMessages(dispatchClass);
            curr = curr.superInstance;
        }
    }

    protected abstract void registerMessages(Class<?> dispatchClass);
}
