/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.api.dsl.test.processor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import static org.junit.Assert.*;

final class Compile implements DiagnosticListener<JavaFileObject> {
    private final List<Diagnostic<? extends JavaFileObject>> errors = new ArrayList<>();
    private final Map<String, byte[]> classes;
    private final String sourceLevel;

    private Compile(Class<?> processor, String code, String sl) {
        this.sourceLevel = sl;
        classes = compile(processor, code);
    }

    /**
     * Performs compilation of given HTML page and associated Java code.
     */
    public static Compile create(Class<?> processor, String code) {
        return new Compile(processor, code, "1.7");
    }

    /** Checks for given class among compiled resources. */
    public byte[] get(String res) {
        return classes.get(res);
    }

    /**
     * Obtains errors created during compilation.
     */
    public List<Diagnostic<? extends JavaFileObject>> getErrors() {
        List<Diagnostic<? extends JavaFileObject>> err;
        err = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : errors) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                err.add(diagnostic);
            }
        }
        return err;
    }

    private Map<String, byte[]> compile(Class<?> processor, final String code) {
        StandardJavaFileManager sjfm = ToolProvider.getSystemJavaCompiler().getStandardFileManager(this, null, null);

        final Map<String, ByteArrayOutputStream> class2BAOS;
        class2BAOS = new HashMap<>();

        JavaFileObject file = new SimpleJavaFileObject(URI.create("mem://mem"), Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return code;
            }
        };

        JavaFileManager jfm = new ForwardingJavaFileManager<JavaFileManager>(sjfm) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String className, Kind kind, FileObject sibling) throws IOException {
                if (kind == Kind.CLASS) {
                    final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                    class2BAOS.put(className.replace('.', '/') + ".class", buffer);
                    return new SimpleJavaFileObject(sibling.toUri(), kind) {
                        @Override
                        public OutputStream openOutputStream() {
                            return buffer;
                        }
                    };
                }

                if (kind == Kind.SOURCE) {
                    final String n = className.replace('.', '/') + ".java";
                    final URI un;
                    try {
                        un = new URI("mem://" + n);
                    } catch (URISyntaxException ex) {
                        throw new IOException(ex);
                    }
                    return new VirtFO(un/* sibling.toUri() */, kind, n);
                }

                throw new IllegalStateException();
            }

            @Override
            public boolean isSameFile(FileObject a, FileObject b) {
                if (a instanceof VirtFO && b instanceof VirtFO) {
                    return ((VirtFO) a).getName().equals(((VirtFO) b).getName());
                }

                return super.isSameFile(a, b);
            }

            class VirtFO extends SimpleJavaFileObject {

                private final String n;

                public VirtFO(URI uri, Kind kind, String n) {
                    super(uri, kind);
                    this.n = n;
                }

                private final ByteArrayOutputStream data = new ByteArrayOutputStream();

                @Override
                public OutputStream openOutputStream() {
                    return data;
                }

                @Override
                public String getName() {
                    return n;
                }

                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
                    data.close();
                    return new String(data.toByteArray());
                }
            }
        };
        List<String> args = Arrays.asList("-source", sourceLevel, "-target", "1.7", //
                        "-processor", processor.getName());

        ToolProvider.getSystemJavaCompiler().getTask(null, jfm, this, args, null, Arrays.asList(file)).call();

        Map<String, byte[]> result = new HashMap<>();

        for (Map.Entry<String, ByteArrayOutputStream> e : class2BAOS.entrySet()) {
            result.put(e.getKey(), e.getValue().toByteArray());
        }

        return result;
    }

    @Override
    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
        errors.add(diagnostic);
    }

    void assertErrors() {
        assertFalse("There are supposed to be some errors", getErrors().isEmpty());
    }

    void assertNoErrors() {
        assertTrue("There are supposed to be no errors: " + getErrors(), getErrors().isEmpty());
    }

    void assertError(String expMsg) {
        StringBuilder sb = new StringBuilder();
        sb.append("Can't find ").append(expMsg).append(" among:");
        for (Diagnostic<? extends JavaFileObject> e : errors) {
            String msg = e.getMessage(Locale.US);
            if (msg.contains(expMsg)) {
                return;
            }
            sb.append("\n");
            sb.append(msg);
        }
        fail(sb.toString());
    }
}
