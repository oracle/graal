package com.oracle.truffle.espresso.continuations;

import java.io.Serial;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * An {@link Enumeration} that emits an element any time {@link #emit(Object)}
 * is called from inside the {@link #generate()} method. Emit can be called
 * anywhere in the call stack. This type of enumeration is sometimes called
 * a <i>generator</i>.
 */
public abstract class ContinuationEnumeration<E> implements Enumeration<E>, Serializable {
    @Serial
    private static final long serialVersionUID = -5614372125614425080L;

    private final Continuation continuation;

    private transient E currentElement;

    private transient boolean hasProduced;

    private Continuation.SuspendCapability suspendCapability;

    /**
     * This constructor exists only for deserialization purposes. Don't call it directly.
     * @hidden
     */
    @SuppressWarnings("this-escape")
    protected ContinuationEnumeration() {
        continuation = new Continuation((Continuation.EntryPoint & Serializable) suspendCapability -> {
            this.suspendCapability = suspendCapability;
            generate();
        });
    }

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
        if (reentrancy)
            return "this generator";
        reentrancy = true;
        String result = continuation.toString();
        reentrancy = false;
        return result;
    }
//
//    @Serial
//    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
//        if (hasProduced)
//            throw new IllegalStateException("You cannot serialize a generator between a call to hasMoreElements() and nextElement().");
//        out.defaultWriteObject();
//    }
//
//    @Serial
//    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
//        in.defaultReadObject();
//    }
}
