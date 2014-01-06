/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.test.debug;

import org.junit.*;

import com.oracle.truffle.ruby.runtime.configuration.*;
import com.oracle.truffle.ruby.test.*;

/**
 * Test the debugger.
 */
public class DebugTests extends RubyTests {

    @Test
    public void testBreakContinue() {
        final ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
        configurationBuilder.setDebug(true);
        final Configuration configuration = new Configuration(configurationBuilder);

        final String fakeFileName = "test.rb";
        final String code = "Debug.break; puts 2";
        final String input = "puts 1 \n Debug.continue \n puts 'error' \n puts 'error'";
        final String expected = "1\n=> \n2\n";

        assertPrints(configuration, expected, fakeFileName, code, input, new String[]{});
    }

    @Test
    public void testLineBreak() {
        final ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
        configurationBuilder.setDebug(true);
        final Configuration configuration = new Configuration(configurationBuilder);

        final String fakeFileName = "test.rb";
        final String code = "Debug.break(\"test.rb\", 2) \n puts 2 \n puts 3";
        final String input = "puts 1 \n Debug.continue \n puts 'error' \n puts 'error'";
        final String expected = "1\n=> \n2\n3\n";

        assertPrints(configuration, expected, fakeFileName, code, input, new String[]{});
    }

    @Test
    public void testLineCustomBreak() {
        final ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
        configurationBuilder.setDebug(true);
        final Configuration configuration = new Configuration(configurationBuilder);

        final String fakeFileName = "test.rb";
        final String code = "Debug.break('test.rb', 2) { puts 1; Debug.break }\nputs 3\nputs 4";
        final String input = "puts 2 \n Debug.continue \n puts 'error' \n puts 'error'";
        final String expected = "1\n2\n=> \n3\n4\n";

        assertPrints(configuration, expected, fakeFileName, code, input, new String[]{});
    }

    @Test
    public void testLocalBreak() {
        final ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
        configurationBuilder.setDebug(true);
        final Configuration configuration = new Configuration(configurationBuilder);

        final String fakeFileName = "test.rb";
        final String code = "def foo \n puts 0 \n x = 14 \n end \n Debug.break(:foo, :x) \n foo \n puts 2";
        final String input = "puts 1 \n Debug.continue \n puts 'error' \n puts 'error'";
        final String expected = "0\n1\n=> \n2\n";

        assertPrints(configuration, expected, fakeFileName, code, input, new String[]{});
    }

    @Test
    public void testLocalCustomBreak() {
        final ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
        configurationBuilder.setDebug(true);
        final Configuration configuration = new Configuration(configurationBuilder);

        final String fakeFileName = "test.rb";
        final String code = "def foo \n puts 0 \n x = 14 \n end \n Debug.break(:foo, :x) { |v| puts v; Debug.break } \n foo \n puts 2";
        final String input = "puts 1 \n Debug.continue \n puts 'error' \n puts 'error'";
        final String expected = "0\n14\n1\n=> \n2\n";

        assertPrints(configuration, expected, fakeFileName, code, input, new String[]{});
    }

    @Test
    public void testRemoveLineBreak() {
        final ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
        configurationBuilder.setDebug(true);
        final Configuration configuration = new Configuration(configurationBuilder);

        final String fakeFileName = "test.rb";
        final String code = "Debug.break('test.rb', 3) \n Debug.remove('test.rb', 3) \n puts 2 \n puts 3";
        final String input = "puts 1 \n Debug.continue \n puts 'error' \n puts 'error'";
        final String expected = "2\n3\n";

        assertPrints(configuration, expected, fakeFileName, code, input, new String[]{});
    }

    @Test
    public void testRemoveLocalBreak() {
        final ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
        configurationBuilder.setDebug(true);
        final Configuration configuration = new Configuration(configurationBuilder);

        final String fakeFileName = "test.rb";
        final String code = "def foo \n puts 0 \n x = 14 \n end \n Debug.break(:foo, :x) \n foo \n Debug.remove(:foo, :x) \n foo \n puts 2";
        final String input = "puts 1 \n Debug.continue \n puts 'error' \n puts 'error'";
        final String expected = "0\n1\n=> \n0\n2\n";

        assertPrints(configuration, expected, fakeFileName, code, input, new String[]{});
    }

    @Test
    public void testWhere() {
        final ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
        configurationBuilder.setDebug(true);
        final Configuration configuration = new Configuration(configurationBuilder);

        final String fakeFileName = "test.rb";
        final String code = "puts 1 \n Debug.where \n puts 2";
        final String expected = "1\ntest.rb:2\n2\n";

        assertPrints(configuration, expected, fakeFileName, code, "", new String[]{});
    }

}
