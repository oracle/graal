package com.oracle.truffle.espresso.descriptors;

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.impl.Stable;

/**
 * A <tt>ByteSequence</tt> is a readable sequence of <code>byte</code> values. This interface
 * provides uniform, read-only access to different kinds of <code>byte</code> sequences.
 */
abstract class ByteSequence {

    protected final int hashCode;

    @Stable @CompilationFinal(dimensions = 1) //
    protected final byte[] value;

    public ByteSequence(byte[] underlyingBytes, int hashCode) {
        this.value = Objects.requireNonNull(underlyingBytes);
        this.hashCode = hashCode;
    }

    static int hashOfRange(byte[] bytes, int offset, int length) {
        int h = 0;
        if (length > 0) {
            h = 1;
            for (int i = 0; i < length; ++i) {
                h = 31 * h + bytes[offset + i];
            }
        }
        return h;
    }

    public static ByteSequence wrap(byte[] underlyingBytes, int offset, int length) {
        return new ByteSequence(underlyingBytes, hashOfRange(underlyingBytes, offset, length)) {
            @Override
            public final int length() {
                return length;
            }

            @Override
            public final int offset() {
                return offset;
            }
        };
    }

    /**
     * Returns the length of this character sequence. The length is the number of <code>byte</code>s
     * in the sequence.
     *
     * @return the number of <code>byte</code>s in this sequence
     */
    public abstract int length();

    public abstract int offset();

    /**
     * Returns the <code>byte</code> value at the specified index. An index ranges from zero to
     * <tt>length() - 1</tt>. The first <code>byte</code> value of the sequence is at index zero,
     * the next at index one, and so on, as for array indexing.
     *
     * @param index the index of the <code>byte</code> value to be returned
     *
     * @return the specified <code>byte</code> value
     *
     * @throws IndexOutOfBoundsException if the <tt>index</tt> argument is negative or not less than
     *             <tt>length()</tt>
     */
    public byte byteAt(int index) {
        return value[index + offset()];
    }

    final byte[] getUnderlyingBytes() {
        return value;
    }

    @Override
    public final int hashCode() {
        return hashCode;
    }

    public final ByteSequence subSequence(int offset, int length) {
        if (offset == 0 && length == length()) {
            return this;
        }
        return wrap(getUnderlyingBytes(), offset() + offset, length);
    }
}
