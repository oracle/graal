package com.oracle.truffle.api.frame;

import com.oracle.truffle.api.nodes.UnexpectedResultException;

/**
 * Internal frame accessor methods for internal Truffle components that are trusted.
 */
public abstract class FrameExtensions {

    protected FrameExtensions() {
    }

    public abstract byte getTag(Frame frame, int slot);

    /**
     * Reads an object from the frame.
     *
     * @since 24.2
     */
    public abstract Object getObject(Frame frame, int slot) throws FrameSlotTypeException;

    /**
     * Reads a boolean from the frame.
     *
     * @since 24.2
     */
    public abstract boolean getBoolean(Frame frame, int slot) throws FrameSlotTypeException;

    /**
     * Reads an int from the frame.
     *
     * @since 24.2
     */
    public abstract int getInt(Frame frame, int slot) throws FrameSlotTypeException;

    /**
     * Reads a long from the frame.
     *
     * @since 24.2
     */
    public abstract long getLong(Frame frame, int slot) throws FrameSlotTypeException;

    /**
     * Reads a byte from the frame.
     *
     * @since 24.2
     */
    public abstract byte getByte(Frame frame, int slot) throws FrameSlotTypeException;

    /**
     * Reads a float from the frame.
     *
     * @since 24.2
     */
    public abstract float getFloat(Frame frame, int slot) throws FrameSlotTypeException;

    /**
     * Reads a double from the frame.
     *
     * @since 24.2
     */
    public abstract double getDouble(Frame frame, int slot);

    /**
     * Stores an Object into the frame.
     *
     * @since 24.2
     */
    public abstract void setObject(Frame frame, int slot, Object value);

    /**
     * Stores a boolean into the frame.
     *
     * @since 24.2
     */
    public abstract void setBoolean(Frame frame, int slot, boolean value);

    /**
     * Stores a byte into the frame.
     *
     * @since 24.2
     */
    public abstract void setByte(Frame frame, int slot, byte value);

    /**
     * Stores an int into the frame.
     *
     * @since 24.2
     */
    public abstract void setInt(Frame frame, int slot, int value);

    /**
     * Stores a long into the frame.
     *
     * @since 24.2
     */
    public abstract void setLong(Frame frame, int slot, long value);

    /**
     * Stores a float into the frame.
     *
     * @since 24.2
     */
    public abstract void setFloat(Frame frame, int slot, float value);

    /**
     * Stores a double into the frame.
     *
     * @since 24.2
     */
    public abstract void setDouble(Frame frame, int slot, double value);

    /**
     * Reads a boolean from the frame, throwing an {@link UnexpectedResultException} with the slot
     * value when the tag does not match.
     *
     * @since 24.2
     */
    public abstract boolean expectBoolean(Frame frame, int slot) throws UnexpectedResultException;

    /**
     * Reads a byte from the frame, throwing an {@link UnexpectedResultException} with the slot
     * value when the tag does not match.
     *
     * @since 24.2
     */
    public abstract byte expectByte(Frame frame, int slot) throws UnexpectedResultException;

    /**
     * Reads an int from the frame, throwing an {@link UnexpectedResultException} with the slot
     * value when the tag does not match.
     *
     * @since 24.2
     */
    public abstract int expectInt(Frame frame, int slot) throws UnexpectedResultException;

    /**
     * Reads a long from the frame, throwing an {@link UnexpectedResultException} with the slot
     * value when the tag does not match.
     *
     * @since 24.2
     */
    public abstract long expectLong(Frame frame, int slot) throws UnexpectedResultException;

    /**
     * Reads an Object from the frame, throwing an {@link UnexpectedResultException} with the slot
     * value when the tag does not match.
     *
     * @since 24.2
     */
    public abstract Object expectObject(Frame frame, int slot) throws UnexpectedResultException;

    /**
     * Reads a float from the frame, throwing an {@link UnexpectedResultException} with the slot
     * value when the tag does not match.
     *
     * @since 24.2
     */
    public abstract float expectFloat(Frame frame, int slot) throws UnexpectedResultException;

    /**
     * Reads a double from the frame, throwing an {@link UnexpectedResultException} with the slot
     * value when the tag does not match.
     *
     * @since 24.2
     */
    public abstract double expectDouble(Frame frame, int slot) throws UnexpectedResultException;

    /**
     * Reads an Object from the frame, recovering gracefully when the slot is not Object.
     *
     * @since 24.2
     */
    public final Object requireObject(Frame frame, int slot) {
        try {
            return expectObject(frame, slot);
        } catch (UnexpectedResultException e) {
            return e.getResult();
        }
    }

    /**
     * Reads an object from the frame without checking the slot's tag.
     *
     * @since 24.2
     */
    public abstract Object uncheckedGetObject(Frame frame, int slot);

    /**
     * Copies a value from one slot to another.
     *
     * @since 24.2
     */
    public abstract void copy(Frame frame, int srcSlot, int dstSlot);

    /**
     * Copies a range of values from one frame to another.
     *
     * @since 24.2
     */
    public abstract void copyTo(Frame srcFrame, int srcOffset, Frame dstFrame, int dstOffset, int length);

    /**
     * Copies an Object from one slot to another.
     *
     * @since 24.2
     */
    public abstract void copyObject(Frame frame, int srcSlot, int dstSlot);

    /**
     * Copies a primitive from one slot to another.
     *
     * @since 24.2
     */
    public abstract void copyPrimitive(Frame frame, int srcSlot, int dstSlot);

    /**
     * Clears a frame slot.
     *
     * @since 24.2
     */
    public abstract void clear(Frame frame, int slot);

    /**
     * Reads the value of a slot from the frame.
     *
     * @since 24.2
     */
    @SuppressWarnings("static-method")
    public final Object getValue(Frame frame, int slot) {
        return frame.getValue(slot);
    }

}
