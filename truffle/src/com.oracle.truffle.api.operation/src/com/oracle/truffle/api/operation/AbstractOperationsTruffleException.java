package com.oracle.truffle.api.operation;

import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.Node;

public abstract class AbstractOperationsTruffleException extends AbstractTruffleException {

    public AbstractOperationsTruffleException() {
        super();
    }

    public AbstractOperationsTruffleException(AbstractOperationsTruffleException prototype) {
        super(prototype);
    }

    public AbstractOperationsTruffleException(Node location, int bci) {
        super(getLocation(location, bci));
    }

    public AbstractOperationsTruffleException(String message, Node location, int bci) {
        super(message, getLocation(location, bci));
    }

    public AbstractOperationsTruffleException(String message, Throwable cause, int stackTraceElementLimit, Node location, int bci) {
        super(message, cause, stackTraceElementLimit, getLocation(location, bci));
    }

    public AbstractOperationsTruffleException(String message) {
        super(message);
    }

    private static Node getLocation(Node location, int bci) {
        if (bci >= 0) {
            return ((OperationsNode) location).createLocationNode(bci);
        } else {
            return location;
        }
    }
}
