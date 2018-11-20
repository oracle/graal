package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.nodes.Node;

public class EspressoExitException extends RuntimeException implements TruffleException {

    private final int status;

    public EspressoExitException(int status) {
        this.status = status;
    }

    @Override
    public Node getLocation() {
        return null;
    }

    @Override
    public boolean isExit() {
        return true;
    }

    @Override
    public int getExitStatus() {
        return status;
    }
}
