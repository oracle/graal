/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polybench.ast;

public interface Expr extends Stat {
    record BinaryOp(Expr lhs, Expr rhs, Operator op) implements Expr {
    }

    record FunctionCall(Reference ref, Expr[] arguments) implements Expr {

        public static FunctionCall of(Reference ref) {
            return new FunctionCall(ref, null);
        }

        public static FunctionCall of(Reference ref, Expr[] arguments) {
            return new FunctionCall(ref, arguments);
        }
    }

    record ConstructorCall(Reference.Ident ident, Expr[] arguments) implements Expr {
        public static ConstructorCall of(Reference.Ident ident) {
            return new ConstructorCall(ident, null);
        }
    }

    record LogCall(Expr message) implements Expr {
    }

    record AppendToListCall(Expr list, Expr element) implements Expr {
    }

    record ListLengthCall(Expr list) implements Expr {
    }

    record ListSortCall(Expr list) implements Expr {
    }

    record StringConcatenation(Expr[] arguments) implements Expr {
    }

    interface Reference extends Expr {
        record Ident(java.lang.String value) implements Reference, Atom {
        }

        record This() implements Reference, Atom {
        }

        record Super() implements Reference, Atom {
        }

        record CompoundReference(Reference... components) implements Reference {
        }
    }

    interface Atom extends Expr {
        record Floating(float value) implements Atom {
        }

        record Int(int value) implements Atom {
        }

        record Bool(boolean value) implements Atom {
        }

        record Null() implements Atom {
        }

        record String(java.lang.String value) implements Atom {
        }

        record EmptyList() implements Atom {
        }
    }
}
