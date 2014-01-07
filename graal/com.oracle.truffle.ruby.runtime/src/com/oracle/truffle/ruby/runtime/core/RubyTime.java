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

import java.text.*;
import java.util.*;

import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Represents the Ruby {@code Time} class. This is a very rough implementation and is only really
 * enough to run benchmark harnesses.
 */
public class RubyTime extends RubyObject {

    /**
     * The class from which we create the object that is {@code Time}. A subclass of
     * {@link RubyClass} so that we can override {@link #newInstance} and allocate a
     * {@link RubyTime} rather than a normal {@link RubyBasicObject}.
     */
    public static class RubyTimeClass extends RubyClass {

        public RubyTimeClass(RubyClass objectClass) {
            super(null, objectClass, "Time");
        }

        @Override
        public RubyBasicObject newInstance() {
            return new RubyTime(this, milisecondsToNanoseconds(System.currentTimeMillis()));
        }

    }

    private final long nanoseconds;

    public RubyTime(RubyClass timeClass, long nanoseconds) {
        super(timeClass);
        this.nanoseconds = nanoseconds;
    }

    /**
     * Subtract one time from another, producing duration in seconds.
     */
    public double subtract(RubyTime other) {
        return nanosecondsToSecond(nanoseconds - other.nanoseconds);
    }

    @Override
    public String toString() {
        /*
         * I think this is ISO 8601 with a custom time part. Note that Ruby's time formatting syntax
         * is different to Java's.
         */

        return new SimpleDateFormat("Y-MM-d H:m:ss Z").format(toDate());
    }

    private Date toDate() {
        return new Date(nanosecondsToMiliseconds(nanoseconds));
    }

    public static RubyTime fromDate(RubyClass timeClass, long timeMiliseconds) {
        return new RubyTime(timeClass, milisecondsToNanoseconds(timeMiliseconds));
    }

    private static long milisecondsToNanoseconds(long miliseconds) {
        return miliseconds * 1000000;
    }

    private static long nanosecondsToMiliseconds(long nanoseconds) {
        return nanoseconds / 1000000;
    }

    private static double nanosecondsToSecond(long nanoseconds) {
        return nanoseconds / 1e9;
    }

}
