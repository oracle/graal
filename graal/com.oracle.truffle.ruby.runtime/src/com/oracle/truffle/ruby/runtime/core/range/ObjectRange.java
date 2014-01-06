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

public class ObjectRange extends RubyRange {

    private final Object begin;
    private final Object end;
    private final boolean excludeEnd;

    public ObjectRange(RubyClass rangeClass, Object begin, Object end, boolean excludeEnd) {
        super(rangeClass);
        this.begin = begin;
        this.end = end;
        this.excludeEnd = excludeEnd;
    }

    @Override
    public RubyArray toArray() {
        throw new UnsupportedOperationException();
    }

    public Object getBegin() {
        return begin;
    }

    public Object getEnd() {
        return end;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((begin == null) ? 0 : begin.hashCode());
        result = prime * result + ((end == null) ? 0 : end.hashCode());
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
        if (!(obj instanceof ObjectRange)) {
            return false;
        }
        ObjectRange other = (ObjectRange) obj;
        if (begin == null) {
            if (other.begin != null) {
                return false;
            }
        } else if (!begin.equals(other.begin)) {
            return false;
        }
        if (end == null) {
            if (other.end != null) {
                return false;
            }
        } else if (!end.equals(other.end)) {
            return false;
        }
        if (excludeEnd != other.excludeEnd) {
            return false;
        }
        return true;
    }

    @Override
    public boolean doesExcludeEnd() {
        // TODO Auto-generated method stub
        return false;
    }

}
