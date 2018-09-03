package com.oracle.truffle.espresso.meta;

import com.oracle.truffle.espresso.types.TypeDescriptor;

import java.util.Objects;

/**
 * Represents an exception handler within the bytecodes.
 */
public final class ExceptionHandler {

    private final int startBCI;
    private final int endBCI;
    private final int handlerBCI;
    private final int catchTypeCPI;
    private final TypeDescriptor catchType;

    /**
     * Creates a new exception handler with the specified ranges.
     *
     * @param startBCI the start index of the protected range
     * @param endBCI the end index of the protected range
     * @param catchBCI the index of the handler
     * @param catchTypeCPI the index of the throwable class in the constant pool
     * @param catchType the type caught by this exception handler
     */
    public ExceptionHandler(int startBCI, int endBCI, int catchBCI, int catchTypeCPI, TypeDescriptor catchType) {
        this.startBCI = startBCI;
        this.endBCI = endBCI;
        this.handlerBCI = catchBCI;
        this.catchTypeCPI = catchTypeCPI;
        this.catchType = catchType;
    }

    /**
     * Returns the start bytecode index of the protected range of this handler.
     */
    public int getStartBCI() {
        return startBCI;
    }

    /**
     * Returns the end bytecode index of the protected range of this handler.
     */
    public int getEndBCI() {
        return endBCI;
    }

    /**
     * Returns the bytecode index of the handler block of this handler.
     */
    public int getHandlerBCI() {
        return handlerBCI;
    }

    /**
     * Returns the index into the constant pool representing the type of exception caught by this
     * handler.
     */
    public int catchTypeCPI() {
        return catchTypeCPI;
    }

    /**
     * Checks whether this handler catches all exceptions.
     *
     * @return {@code true} if this handler catches all exceptions
     */
    public boolean isCatchAll() {
        return catchTypeCPI == 0;
    }

    /**
     * Returns the type of exception caught by this exception handler.
     */
    public TypeDescriptor getCatchType() {
        return catchType;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ExceptionHandler)) {
            return false;
        }
        ExceptionHandler that = (ExceptionHandler) obj;
        if (this.startBCI != that.startBCI || this.endBCI != that.endBCI || this.handlerBCI != that.handlerBCI || this.catchTypeCPI != that.catchTypeCPI) {
            return false;
        }
        return Objects.equals(this.catchType, that.catchType);
    }

    @Override
    public String toString() {
        return "ExceptionHandler<startBCI=" + startBCI + ", endBCI=" + endBCI + ", handlerBCI=" + handlerBCI + ", catchTypeCPI=" + catchTypeCPI + ", catchType=" + catchType + ">";
    }

    @Override
    public int hashCode() {
        return catchTypeCPI ^ endBCI ^ handlerBCI;
    }
}
