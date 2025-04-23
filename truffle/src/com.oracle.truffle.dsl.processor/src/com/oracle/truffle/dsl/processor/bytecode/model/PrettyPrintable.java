/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.bytecode.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public interface PrettyPrintable {

    default List<String> pp() {
        PrettyPrinter printer = new PrettyPrinter();
        pp(printer);
        return printer.lines;
    }

    void pp(PrettyPrinter printer);

    final class PrettyPrinter {
        private String nextIndent = "";
        private String indent = "";
        private final List<String> lines = new ArrayList<>();

        public void print(String text) {
            lines.add(nextIndent + text.replace("\n", "\n" + indent));
            nextIndent = indent;
        }

        public void print(String format, Object... args) {
            print(String.format(format, args));
        }

        public void print(Object obj) {
            if (obj instanceof PrettyPrintable) {
                ((PrettyPrintable) obj).pp(this);
            } else if (obj instanceof Collection<?>) {
                for (Object elem : (Collection<?>) obj) {
                    nextIndent = nextIndent + "  - ";
                    String old = indent;
                    indent += "    ";
                    print(elem);
                    indent = old;
                }
            } else {
                print(Objects.toString(obj));
            }
        }

        public void field(String fieldName, Object fieldValue) {
            if (fieldValue instanceof PrettyPrintable field) {
                print("%s:", fieldName);
                String old = indent;
                indent += "  ";
                field.pp(this);
                indent = old;
            } else if (fieldValue instanceof Collection<?> collection) {
                print("%s:", fieldName);
                for (Object obj : collection) {
                    nextIndent = nextIndent + "  - ";
                    String old = indent;
                    indent += "    ";
                    print(obj);
                    indent = old;
                    nextIndent = indent;
                }
            } else {
                print("%s: %s", fieldName, fieldValue);
            }
        }
    }
}
