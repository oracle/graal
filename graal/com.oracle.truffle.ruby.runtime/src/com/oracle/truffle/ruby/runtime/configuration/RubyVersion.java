/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.configuration;

/**
 * A Ruby version that we want to be compatible with.
 */
public enum RubyVersion {
    RUBY_18("1.8.7", 374), RUBY_19("1.9.3", 448), RUBY_20("2.0.0", 247), RUBY_21("2.1.0", 0);

    private final String version;
    private final int patch;

    private RubyVersion(String version, int patch) {
        this.version = version;
        this.patch = patch;
    }

    public boolean is18OrEarlier() {
        return this.compareTo(RUBY_18) <= 0;
    }

    public boolean is19OrLater() {
        return this.compareTo(RUBY_19) >= 0;
    }

    public String getVersion() {
        return version;
    }

    public int getPatch() {
        return patch;
    }

    public String getShortName() {
        return "Ruby" + getVersion();
    }

}
