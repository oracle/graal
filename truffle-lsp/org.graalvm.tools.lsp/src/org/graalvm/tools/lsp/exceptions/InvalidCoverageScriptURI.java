package org.graalvm.tools.lsp.exceptions;

import java.nio.file.InvalidPathException;

public class InvalidCoverageScriptURI extends Exception {

    private static final long serialVersionUID = 2253144060104500867L;
    private final int index;
    private final String reason;
    private int length = 0;

    public InvalidCoverageScriptURI(InvalidPathException cause, int offset, int length) {
        super(cause);
        this.index = offset + ((cause.getIndex() >= 0) ? cause.getIndex() : 0);
        this.reason = null;
        this.length = length;
    }

    public InvalidCoverageScriptURI(int offset, String reason, int length) {
        this.index = offset;
        this.reason = reason;
        this.length = length;
    }

    public int getIndex() {
        return index;
    }

    public String getReason() {
        return reason != null ? reason : ((InvalidPathException) getCause()).getReason();
    }

    public int getLength() {
        return length;
    }
}
