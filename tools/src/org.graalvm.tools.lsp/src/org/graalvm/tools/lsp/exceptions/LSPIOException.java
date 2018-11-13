package org.graalvm.tools.lsp.exceptions;

import java.io.IOException;

import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.nodes.Node;

public class LSPIOException extends RuntimeException implements TruffleException {

    private static final long serialVersionUID = 310418381621312260L;

    public LSPIOException(String message, IOException e) {
        super(message, e);
    }

    public Node getLocation() {
        return null;
    }

    @Override
    public boolean isExit() {
        return true;
    }
}
