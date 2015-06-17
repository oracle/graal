/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.dsl.internal;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Lazy rewrite event that implements {@link CharSequence} to be provided as message in
 * {@link Node#replace(Node, CharSequence)}.
 */
abstract class SlowPathEvent implements CharSequence {

    protected final SpecializationNode source;
    protected final String reason;
    protected final Frame frame;
    private String message;

    SlowPathEvent(SpecializationNode source, String reason, Frame frame) {
        this.source = source;
        this.reason = reason;
        this.frame = frame;
    }

    public int length() {
        return getMessage().length();
    }

    public char charAt(int index) {
        return getMessage().charAt(index);
    }

    public CharSequence subSequence(int start, int end) {
        return getMessage().subSequence(start, end);
    }

    @Override
    public String toString() {
        return getMessage();
    }

    private String getMessage() {
        if (message == null) {
            message = createMessage();
        }
        return message;
    }

    private String createMessage() {
        StringBuilder builder = new StringBuilder();
        builder.append(source);
        builder.append(" ");
        builder.append(reason);
        Object[] values = getValues();
        if (values.length > 0) {
            builder.append(" with parameters (");
            String sep = "";
            for (Object value : values) {
                builder.append(sep);
                if (value == null) {
                    builder.append("null");
                } else {
                    builder.append(value).append(" (").append(value.getClass().getSimpleName()).append(")");
                }

                sep = ", ";
            }
            builder.append(")");
        }
        return builder.toString();
    }

    public abstract Object[] getValues();

    static class SlowPathEvent0 extends SlowPathEvent {

        private static final Object[] EMPTY = new Object[0];

        public SlowPathEvent0(SpecializationNode source, String reason, Frame frame) {
            super(source, reason, frame);
        }

        @Override
        public final Object[] getValues() {
            return EMPTY;
        }

    }

    static class SlowPathEvent1 extends SlowPathEvent {

        protected final Object o1;

        public SlowPathEvent1(SpecializationNode source, String reason, Frame frame, Object o1) {
            super(source, reason, frame);
            this.o1 = o1;
        }

        @Override
        public final Object[] getValues() {
            return new Object[]{o1};
        }

    }

    static class SlowPathEvent2 extends SlowPathEvent {

        protected final Object o1;
        protected final Object o2;

        public SlowPathEvent2(SpecializationNode source, String reason, Frame frame, Object o1, Object o2) {
            super(source, reason, frame);
            this.o1 = o1;
            this.o2 = o2;
        }

        @Override
        public final Object[] getValues() {
            return new Object[]{o1, o2};
        }

    }

    static class SlowPathEvent3 extends SlowPathEvent {

        protected final Object o1;
        protected final Object o2;
        protected final Object o3;

        public SlowPathEvent3(SpecializationNode source, String reason, Frame frame, Object o1, Object o2, Object o3) {
            super(source, reason, frame);
            this.o1 = o1;
            this.o2 = o2;
            this.o3 = o3;
        }

        @Override
        public final Object[] getValues() {
            return new Object[]{o1, o2, o3};
        }

    }

    static class SlowPathEvent4 extends SlowPathEvent {

        protected final Object o1;
        protected final Object o2;
        protected final Object o3;
        protected final Object o4;

        public SlowPathEvent4(SpecializationNode source, String reason, Frame frame, Object o1, Object o2, Object o3, Object o4) {
            super(source, reason, frame);
            this.o1 = o1;
            this.o2 = o2;
            this.o3 = o3;
            this.o4 = o4;
        }

        @Override
        public final Object[] getValues() {
            return new Object[]{o1, o2, o3, o4};
        }

    }

    static class SlowPathEvent5 extends SlowPathEvent {

        protected final Object o1;
        protected final Object o2;
        protected final Object o3;
        protected final Object o4;
        protected final Object o5;

        public SlowPathEvent5(SpecializationNode source, String reason, Frame frame, Object o1, Object o2, Object o3, Object o4, Object o5) {
            super(source, reason, frame);
            this.o1 = o1;
            this.o2 = o2;
            this.o3 = o3;
            this.o4 = o4;
            this.o5 = o5;
        }

        @Override
        public final Object[] getValues() {
            return new Object[]{o1, o2, o3, o4, o5};
        }

    }

    static class SlowPathEventN extends SlowPathEvent {

        protected final Object[] args;

        public SlowPathEventN(SpecializationNode source, String reason, Frame frame, Object... args) {
            super(source, reason, frame);
            this.args = args;
        }

        @Override
        public final Object[] getValues() {
            return args;
        }

    }
}
