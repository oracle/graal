/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm.gen;

import java.util.*;

import com.sun.max.asm.*;

/**
 * An expression can provide the value of an operand field. This enables synthetic instructions to be generated where
 * the parameters of the generated assembler method are part of an expression whose value is encoded into an operand
 * field.
 */
public interface Expression {

    /**
     * Evaluates the expression given a template and a set of arguments.
     */
    long evaluate(Template template, List<Argument> arguments);

    /**
     * @return a Java expression that performs the {@link #evaluate evaluation}
     */
    String valueString();

    public static final class Static {

        private Static() {
        }

        /**
         * Evaluates a given expression term to a {@code long} value.
         * 
         * @param term
         *            a {@link Number}, {@link Expression} or {@link Parameter} instance
         */
        public static long evaluateTerm(Object term, Template template, List<Argument> arguments) {
            if (term instanceof Number) {
                return ((Number) term).longValue();
            }
            if (term instanceof Expression) {
                return ((Expression) term).evaluate(template, arguments);
            }
            assert term instanceof Parameter;
            return template.bindingFor((Parameter) term, arguments).asLong();
        }

        /**
         * Gets the Java source code representation of a given expression term.
         * 
         * @param term
         *            a {@link Number}, {@link Expression} or {@link Parameter} instance
         */
        public static String termValueString(Object term) {
            if (term instanceof Parameter) {
                return ((Parameter) term).valueString();
            }
            if (term instanceof Expression) {
                return "(" + ((Expression) term).valueString() + ")";
            }
            assert term instanceof Number;
            return term.toString();
        }

        /**
         * Creates and returns an expression that adds its two terms.
         * 
         * @param first
         *            the first term of the addition
         * @param second
         *            the second term of the addition
         */
        public static Expression add(final Object first, final Object second) {
            return new Expression() {

                public long evaluate(Template template, List<Argument> arguments) {
                    return evaluateTerm(first, template, arguments) + evaluateTerm(second, template, arguments);
                }

                public String valueString() {
                    return termValueString(first) + " + " + termValueString(second);
                }
            };
        }

        /**
         * Creates and returns an expression that subtracts its second term from its first term.
         * 
         * @param first
         *            the first term of the subtraction
         * @param second
         *            the second term of the subtraction
         */
        public static Expression sub(final Object first, final Object second) {
            return new Expression() {

                public long evaluate(Template template, List<Argument> arguments) {
                    return evaluateTerm(first, template, arguments) - evaluateTerm(second, template, arguments);
                }

                public String valueString() {
                    return termValueString(first) + " - " + termValueString(second);
                }
            };
        }

        /**
         * Creates and returns an expression that negates its term.
         * 
         * @param term
         *            the term of the negation
         */
        public static Expression neg(final Object term) {
            return new Expression() {

                public long evaluate(Template template, List<Argument> arguments) {
                    return -evaluateTerm(term, template, arguments);
                }

                public String valueString() {
                    return "-" + termValueString(term);
                }
            };
        }

        /**
         * Creates and returns an expression that is term1/term2.
         * 
         * @param term1
         *            dividend
         * 
         * @param term2
         *            divider
         */
        public static Expression div(final Object term1, final Object term2) {
            return new Expression() {

                public long evaluate(Template template, List<Argument> arguments) {
                    return evaluateTerm(term1, template, arguments) / evaluateTerm(term2, template, arguments);
                }

                public String valueString() {
                    return termValueString(term1) + " / " + termValueString(term2);
                }
            };
        }

        /**
         * Creates and returns an expression that is term1 % term2.
         * 
         */
        public static Expression mod(final Object term1, final Object term2) {
            return new Expression() {

                public long evaluate(Template template, List<Argument> arguments) {
                    return evaluateTerm(term1, template, arguments) % evaluateTerm(term2, template, arguments);
                }

                public String valueString() {
                    return termValueString(term1) + " % " + termValueString(term2);
                }
            };
        }

        /**
         * Creates and returns an expression that is term1 >> term2.
         * 
         */
        public static Expression rightShift(final Object term1, final Object term2) {
            return new Expression() {

                public long evaluate(Template template, List<Argument> arguments) {
                    return evaluateTerm(term1, template, arguments) >> evaluateTerm(term2, template, arguments);
                }

                public String valueString() {
                    return termValueString(term1) + " >> " + termValueString(term2);
                }
            };
        }

        /**
         * Creates and returns an expression that is term1 & term2.
         * 
         */
        public static Expression and(final Object term1, final Object term2) {
            return new Expression() {

                public long evaluate(Template template, List<Argument> arguments) {
                    return evaluateTerm(term1, template, arguments) & evaluateTerm(term2, template, arguments);
                }

                public String valueString() {
                    return "(" + termValueString(term1) + " & " + termValueString(term2) + ")";
                }
            };
        }
    }
}
