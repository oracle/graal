package com.oracle.truffle.espresso.continuations;

import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * An {@link Enumeration} that emits an element any time {@link #emit(Object)}
 * is called from inside the {@link #generate()} method. Emit can be called
 * anywhere in the call stack. This type of enumeration is sometimes called
 * a <i>generator</i>.
 */
public abstract class ContinuationEnumeration<E> implements Enumeration<E> {
    private final Continuation continuation;

    private E currentElement;

    private boolean hasProduced;

    private Continuation.SuspendCapability suspendCapability;

    @SuppressWarnings("this-escape")
    protected ContinuationEnumeration() {
        continuation = new Continuation(suspendCapability -> {
            this.suspendCapability = suspendCapability;
            generate();
        });
    }

    @Override
    public final boolean hasMoreElements() {
        Continuation.State state = continuation.getState();
        boolean ready = state == Continuation.State.SUSPENDED || state == Continuation.State.NEW;
        if (!ready)
            return false;
        continuation.resume();
        return hasProduced;
    }

    @Override
    public final E nextElement() {
        if (!hasMoreElements())
            throw new NoSuchElementException();
        E el = currentElement;
        currentElement = null;
        hasProduced = false;
        return el;
    }

    /**
     * Call this method to emit an element from inside {@link #generate()}.
     */
    protected final void emit(E element) {
        assert !hasProduced;
        currentElement = element;
        hasProduced = true;
        suspendCapability.suspend();
    }

    /**
     * Implement this method to {@link #emit(Object)} elements from the enumeration.
     */
    protected abstract void generate();
}
