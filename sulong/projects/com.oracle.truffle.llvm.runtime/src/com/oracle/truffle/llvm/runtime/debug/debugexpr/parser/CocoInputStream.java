/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 * */

package com.oracle.truffle.llvm.runtime.debug.debugexpr.parser;

import java.io.IOException;
import java.io.InputStream;

/**
 * Converts a given String into an InputStream such that it can be used by the Coco/R-generated
 * Parser.
 */
public class CocoInputStream extends InputStream {
    private final String s;
    private int pos;

    public CocoInputStream(String s) {
        this.s = s;
        pos = 0;
    }

    public CocoInputStream(CharSequence cs) {
        this(cs.toString());
    }

    @Override
    public int read() throws IOException {
        if (s == null) {
            throw new IOException("String is null");
        }
        if (pos < s.length()) {
            return s.charAt(pos++);
        }
        return -1;
    }

}
