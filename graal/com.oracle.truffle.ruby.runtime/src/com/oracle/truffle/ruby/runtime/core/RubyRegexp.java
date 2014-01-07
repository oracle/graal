/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.core;

import java.util.regex.*;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Represents the Ruby {@code Regexp} class.
 */
public class RubyRegexp extends RubyObject {

    /**
     * The class from which we create the object that is {@code Regexp}. A subclass of
     * {@link RubyClass} so that we can override {@link #newInstance} and allocate a
     * {@link RubyRegexp} rather than a normal {@link RubyBasicObject}.
     */
    public static class RubyRegexpClass extends RubyClass {

        public RubyRegexpClass(RubyClass objectClass) {
            super(null, objectClass, "Regexp");
        }

        @Override
        public RubyBasicObject newInstance() {
            return new RubyRegexp(getContext().getCoreLibrary().getRegexpClass());
        }

    }

    @CompilationFinal private Pattern pattern;

    public RubyRegexp(RubyClass regexpClass) {
        super(regexpClass);
    }

    public RubyRegexp(RubyClass regexpClass, String pattern) {
        this(regexpClass);
        initialize(compile(pattern));
    }

    public RubyRegexp(RubyClass regexpClass, Pattern pattern) {
        this(regexpClass);
        initialize(pattern);
    }

    public void initialize(String setPattern) {
        pattern = compile(setPattern);
    }

    public void initialize(Pattern setPattern) {
        pattern = setPattern;
    }

    public Object matchOperator(Frame frame, String string) {
        final RubyContext context = getRubyClass().getContext();

        final Matcher matcher = pattern.matcher(string);

        if (matcher.find()) {
            for (int n = 1; n < matcher.groupCount() + 1; n++) {
                final FrameSlot slot = frame.getFrameDescriptor().findFrameSlot("$" + n);

                if (slot != null) {
                    frame.setObject(slot, context.makeString(matcher.group(n)));
                }
            }

            return matcher.start();
        } else {
            return NilPlaceholder.INSTANCE;
        }
    }

    public Pattern getPattern() {
        return pattern;
    }

    public Object match(String string) {
        final RubyContext context = getRubyClass().getContext();

        final Matcher matcher = pattern.matcher(string);

        if (!matcher.find()) {
            return NilPlaceholder.INSTANCE;
        }

        final Object[] values = new Object[matcher.groupCount() + 1];

        for (int n = 0; n < matcher.groupCount() + 1; n++) {
            final String group = matcher.group(n);

            if (group == null) {
                values[n] = NilPlaceholder.INSTANCE;
            } else {
                values[n] = context.makeString(group);
            }
        }

        return new RubyMatchData(context.getCoreLibrary().getMatchDataClass(), values);
    }

    @Override
    public int hashCode() {
        return pattern.pattern().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof RubyRegexp)) {
            return false;
        }
        RubyRegexp other = (RubyRegexp) obj;
        if (pattern == null) {
            if (other.pattern != null) {
                return false;
            }
        } else if (!pattern.pattern().equals(other.pattern.pattern())) {
            return false;
        }
        return true;
    }

    public static Pattern compile(String pattern) {
        return Pattern.compile(pattern, Pattern.MULTILINE | Pattern.UNIX_LINES);
    }
}
