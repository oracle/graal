package com.oracle.graal.sparc;

import com.oracle.graal.api.code.*;

public class SPARCScratchRegister implements AutoCloseable {
    private final ThreadLocal<Boolean> locked = new ThreadLocal<>();
    private final Register register;
    private static final SPARCScratchRegister scratch = new SPARCScratchRegister(SPARC.g3);

    private SPARCScratchRegister(Register register) {
        super();
        this.register = register;
    }

    public Register getRegister() {
        if (locked.get() == null) {
            locked.set(false);
        }
        boolean isLocked = locked.get();
        if (isLocked) {
            throw new RuntimeException("Temp Register is already taken!");
        } else {
            locked.set(true);
            return register;
        }
    }

    public void close() {
        boolean isLocked = locked.get();
        if (isLocked) {
            locked.set(false);
        } else {
            throw new RuntimeException("Temp Register is not taken!");
        }
    }

    public static SPARCScratchRegister get() {
        return scratch;
    }

}
