package com.oracle.truffle.espresso.continuations;

import java.io.Serial;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * An {@link Enumeration} that emits an element any time {@link #emit(Object)} is called from inside
 * the {@link #generate()} method. Emit can be called anywhere in the call stack. This type of
 * enumeration is sometimes called a <i>generator</i>.
 */
public abstract class Generator<E> implements Enumeration<E>, Serializable {
    @Serial
    private static final long serialVersionUID = -5614372125614425080L;

    private final Continuation continuation;
    private Continuation.SuspendCapability suspendCapability;
    private transient E currentElement;
    private transient boolean hasProduced;


    /**
     * This constructor exists only for deserialization purposes. Don't call it directly.
     */
    @SuppressWarnings("this-escape")
    protected Generator() {
        continuation = new Continuation((Continuation.EntryPoint & Serializable) suspendCapability -> {
            this.suspendCapability = suspendCapability;
            generate();
        });
    }

    /**
     * Runs the generator and returns true if it emitted an element. If it finished running, returns
     * false. If the generator throws an exception it will be propagated from this method.
     */
    @Override
    public final boolean hasMoreElements() {
        if (hasProduced)
            return true;
        Continuation.State state = continuation.getState();
        boolean ready = state == Continuation.State.SUSPENDED || state == Continuation.State.NEW;
        if (!ready)
            return false;
        continuation.resume();
        return hasProduced;
    }

    /**
     * Runs the generator if necessary, and returns the element it yielded.
     *
     * @throws NoSuchElementException if the generator has finished and no longer emits elements,
     * or if the generator has previously thrown an exception and failed.
     */
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

    private transient boolean reentrancy = false;

    @Override
    public String toString() {
        // Printing the continuation will invoke toString on everything reachable from the stack,
        // thus we need to cancel the re-entrancy here.
        if (reentrancy)
            return "this generator";
        reentrancy = true;
        String result = continuation.toString();
        reentrancy = false;
        return result;
    }
}
