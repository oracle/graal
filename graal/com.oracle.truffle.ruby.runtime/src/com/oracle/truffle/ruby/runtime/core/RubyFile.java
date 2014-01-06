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

import java.io.*;

import com.oracle.truffle.ruby.runtime.*;

/**
 * Represents the Ruby {@code File} class.
 */
public class RubyFile extends RubyObject {

    private final Reader reader;
    private final Writer writer;

    public RubyFile(RubyClass rubyClass, Reader reader, Writer writer) {
        super(rubyClass);
        this.reader = reader;
        this.writer = writer;
    }

    public void close() {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static String expandPath(String fileName) {
        // TODO(cs): see the other expandPath

        try {
            return new File(fileName).getCanonicalPath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String expandPath(String fileName, String dir) {
        /*
         * TODO(cs): this isn't quite correct - I think we want to collapse .., but we don't want to
         * resolve symlinks etc. This might be where we want to start borrowing JRuby's
         * implementation, but it looks quite tied to their data structures.
         */

        try {
            return new File(dir, fileName).getCanonicalPath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static RubyFile open(RubyContext context, String fileName, String mode) {
        Reader reader;
        Writer writer;

        if (mode.equals("rb")) {
            try {
                reader = new InputStreamReader(new FileInputStream(fileName));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }

            writer = null;
        } else if (mode.equals("w")) {
            reader = null;

            try {
                writer = new OutputStreamWriter(new FileOutputStream(fileName));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new UnsupportedOperationException();
        }

        final RubyFile file = new RubyFile(context.getCoreLibrary().getFileClass(), reader, writer);

        return file;
    }

    public Reader getReader() {
        return reader;
    }

    public Writer getWriter() {
        return writer;
    }

}
