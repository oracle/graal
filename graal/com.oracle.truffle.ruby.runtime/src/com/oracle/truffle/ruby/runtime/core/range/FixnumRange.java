/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.core.range;

import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.core.array.*;

/**
 * A range that has {@code Fixnum} begin and end.
 */
public class FixnumRange extends RubyRange {

    private final int begin;
    private final int end;
    private final boolean excludeEnd;

    public FixnumRange(RubyClass rangeClass, int begin, int end, boolean excludeEnd) {
        super(rangeClass);
        this.begin = begin;
        this.end = end;
        this.excludeEnd = excludeEnd;
    }

    @Override
    public String toString() {
        if (excludeEnd) {
            return begin + "..." + end;
        } else {
            return begin + ".." + end;
        }
    }

    @Override
    public RubyArray toArray() {
        final int length = getLength();

        if (length < 0) {
            return new RubyArray(getRubyClass().getContext().getCoreLibrary().getArrayClass());
        } else {
            final int[] values = new int[length];

            for (int n = 0; n < length; n++) {
                values[n] = begin + n;
            }

            return new RubyArray(getRubyClass().getContext().getCoreLibrary().getArrayClass(), new FixnumArrayStore(values));
        }
    }

    private int getLength() {
        if (excludeEnd) {
            return end - begin;
        } else {
            return end - begin + 1;
        }
    }

    public final int getBegin() {
        return begin;
    }

    public final int getEnd() {
        return end;
    }

    public final int getInclusiveEnd() {
        if (excludeEnd) {
            return end - 1;
        } else {
            return end;
        }
    }

    public final int getExclusiveEnd() {
        if (excludeEnd) {
            return end;
        } else {
            return end + 1;
        }
    }

    @Override
    public boolean doesExcludeEnd() {
        return excludeEnd;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + begin;
        result = prime * result + end;
        result = prime * result + (excludeEnd ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof FixnumRange)) {
            return false;
        }
        FixnumRange other = (FixnumRange) obj;
        if (begin != other.begin) {
            return false;
        }
        if (end != other.end) {
            return false;
        }
        if (excludeEnd != other.excludeEnd) {
            return false;
        }
        return true;
    }

}
