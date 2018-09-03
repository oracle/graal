package com.oracle.truffle.espresso.meta;

/**
 * Maps bytecode indexes to source line numbers.
 *
 * @see "https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.12"
 */
public class LineNumberTable {

    private final int[] lineNumbers;
    private final int[] bcis;

    /**
     *
     * @param lineNumbers an array of source line numbers. This array is now owned by this object
     *            and should not be mutated by the caller.
     * @param bcis an array of bytecode indexes the same length at {@code lineNumbers} whose entries
     *            are sorted in ascending order. This array is now owned by this object and must not
     *            be mutated by the caller.
     */
    // @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "caller transfers ownership of
    // `lineNumbers` and `bcis`")
    public LineNumberTable(int[] lineNumbers, int[] bcis) {
        assert bcis.length == lineNumbers.length;
        this.lineNumbers = lineNumbers;
        this.bcis = bcis;
    }

    /**
     * Gets a source line number for bytecode index {@code atBci}.
     */
    public int getLineNumber(int atBci) {
        for (int i = 0; i < this.bcis.length - 1; i++) {
            if (this.bcis[i] <= atBci && atBci < this.bcis[i + 1]) {
                return lineNumbers[i];
            }
        }
        return lineNumbers[lineNumbers.length - 1];
    }

    /**
     * Gets a copy of the array of line numbers that was passed to this object's constructor.
     */
    public int[] getLineNumbers() {
        return lineNumbers.clone();
    }

    /**
     * Gets a copy of the array of bytecode indexes that was passed to this object's constructor.
     */
    public int[] getBcis() {
        return bcis.clone();
    }
}
