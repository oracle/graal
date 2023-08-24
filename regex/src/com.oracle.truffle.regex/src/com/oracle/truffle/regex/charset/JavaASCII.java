package com.oracle.truffle.regex.charset;

// Sets corresponding to the contents of java.util.regex.ASCII
class JavaASCII {
    static final CodePointSet UPPER = CodePointSet.createNoDedup(0x41, 0x5A);

    static final CodePointSet LOWER = CodePointSet.createNoDedup(0x61, 0x7A);

    static final CodePointSet DIGIT = CodePointSet.createNoDedup(0x30, 0x39);

    static final CodePointSet SPACE = CodePointSet.createNoDedup(0x09, 0x0D,
            0x20, 0x20);

    static final CodePointSet PUNCT = CodePointSet.createNoDedup(0x21, 0x2F,
            0x3A, 0x40,
            0x5B, 0x60,
            0x7B, 0x7E);

    static final CodePointSet CNTRL = CodePointSet.createNoDedup(0x00, 0x1F,
            0x7F, 0x7F);

    static final CodePointSet BLANK = CodePointSet.createNoDedup(0x09, 0x09,
            0x20, 0x20);

    static final CodePointSet HEX = CodePointSet.createNoDedup(0x30, 0x39,
            0x41, 0x46,
            0x61, 0x66);

    static final CodePointSet UNDER = CodePointSet.create(0x5F);

    static final CodePointSet ALPHA = UPPER.union(LOWER);

    static final CodePointSet ALNUM = UPPER.union(LOWER).union(DIGIT);

    static final CodePointSet GRAPH = PUNCT.union(UPPER).union(LOWER).union(DIGIT);

    static final CodePointSet WORD = UPPER.union(LOWER).union(UNDER).union(DIGIT);
}
