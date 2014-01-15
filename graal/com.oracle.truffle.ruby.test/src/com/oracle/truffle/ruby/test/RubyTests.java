/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.test;

import static org.junit.Assert.*;

import java.io.*;
import java.util.*;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.ruby.nodes.core.*;
import com.oracle.truffle.ruby.parser.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.configuration.*;
import com.oracle.truffle.ruby.runtime.core.*;

/**
 * Base class for Ruby tests.
 */
public class RubyTests {

    @BeforeClass
    public static void applyDefaultLocale() {
        // Avoid printing comparison issues
        Locale.setDefault(Locale.ENGLISH);
    }

    /**
     * Executes some Ruby code and asserts that it prints an expected string. Remember to include
     * the newline characters.
     */
    public static void assertPrints(String expectedOutput, String code, String... args) {
        assertPrintsWithInput(expectedOutput, code, "", args);
    }

    /**
     * Executes some Ruby code and asserts that it prints an expected string. Remember to include
     * the newline characters. Allows input for {@code Kernel#gets} to be passed in.
     */
    public static void assertPrintsWithInput(String expectedOutput, String code, String input, String... args) {
        assertPrints(null, expectedOutput, "(test)", code, input, args);
    }

    /**
     * Executes some Ruby code and asserts that it prints an expected string. Remember to include
     * the newline characters. Also takes a string to simulate input.
     */
    public static void assertPrints(Configuration configuration, String expectedOutput, String fileName, String code, String input, String... args) {
        final ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        final PrintStream printStream = new PrintStream(byteArray);

        ConfigurationBuilder configurationBuilder;

        if (configuration == null) {
            configurationBuilder = new ConfigurationBuilder();
        } else {
            configurationBuilder = new ConfigurationBuilder(configuration);
        }

        configurationBuilder.setStandardOut(printStream);

        final BufferedReader inputReader = new BufferedReader(new StringReader(input));

        configurationBuilder.setInputReader(new InputReader() {

            @Override
            public String readLine(String prompt) throws IOException {
                return inputReader.readLine();
            }

        });

        final RubyContext context = new RubyContext(new Configuration(configurationBuilder), new JRubyParser());

        CoreMethodNodeManager.addMethods(context.getCoreLibrary().getObjectClass());
        context.getCoreLibrary().initializeAfterMethodsAdded();

        for (String arg : args) {
            context.getCoreLibrary().getArgv().push(new RubyString(context.getCoreLibrary().getStringClass(), arg));
        }

        final Source source = context.getSourceManager().getFakeFile(fileName, code);

        context.execute(context, source, RubyParser.ParserContext.TOP_LEVEL, context.getCoreLibrary().getMainObject(), null);
        context.shutdown();

        assertEquals(expectedOutput, byteArray.toString().replaceAll("\r\n", "\n"));
    }
}
