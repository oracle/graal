package com.oracle.graal.sparc;

import com.oracle.graal.api.code.*;

public class SPARCScratchRegister implements AutoCloseable {
    private final ThreadLocal<Boolean> locked = new ThreadLocal<>();
    private final ThreadLocal<Exception> where = new ThreadLocal<>();
    private final Register register;
    private static final SPARCScratchRegister scratch1 = new SPARCScratchRegister(SPARC.g3);
    private static final SPARCScratchRegister scratch2 = new SPARCScratchRegister(SPARC.g1);

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
            where.get().printStackTrace();
            throw new RuntimeException("Temp Register is already taken!");
        } else {
            where.set(new Exception());
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
        if (scratch1.isLocked()) {
            return scratch2;
        } else {
            return scratch1;
        }
    }

    public boolean isLocked() {
        Boolean isLocked = locked.get();
        if (isLocked == null) {
            return false;
        } else {
            return isLocked;
        }
    }
}
