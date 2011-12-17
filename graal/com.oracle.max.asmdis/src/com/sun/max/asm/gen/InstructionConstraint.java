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

import java.lang.reflect.*;
import java.util.*;

import com.sun.max.*;
import com.sun.max.asm.*;
import com.sun.max.program.*;

/**
 * An instruction constraint can be specified as part of an {@link InstructionDescription} to specify a constraint on one
 * or more parameters of the assembler method that will be generated from the instruction description.
 * <p>
 * An instruction constraint implements a predicate on a complete assembler instruction and it's used by the
 * {@link AssemblyTester test framework} in concert with {@link Parameter#getLegalTestArguments()} to generate test
 * cases.
 */
public interface InstructionConstraint {

    /**
     * Determines if a given set of parameter bindings (i.e. arguments) for an assembler method is valid.
     *
     * @param template   an instruction template
     * @param arguments  the list of arguments to check
     * @return true if the argument list is valid, false otherwise
     */
    boolean check(Template template, List<Argument> arguments);

    /**
     * @return a Java expression that performs the {@link #check check}
     */
    String asJavaExpression();

    /**
     * Determines if this constraint has {@code parameter} as one of its terms.
     */
    boolean referencesParameter(Parameter parameter);

    /**
     * Gets the method that implements the predicate of the constraint.
     *
     * @return {@code null} if this is a simple expression constraint
     */
    Method predicateMethod();

    public abstract static class SimpleInstructionConstraint implements InstructionConstraint {
        public Method predicateMethod() {
            return null;
        }

        @Override
        public String toString() {
            return asJavaExpression();
        }
    }

    public static final class Static {

        private Static() {
        }

        /**
         * Creates an instruction constraint on a parameter such that it must be a given symbol.
         */
        public static InstructionConstraint eq(final Parameter first, final SymbolicArgument symbol) {
            return new SimpleInstructionConstraint() {

                public boolean check(Template template, List<Argument> arguments) {
                    return template.bindingFor(first, arguments) == symbol;
                }

                public String asJavaExpression() {
                    return first.variableName() + " == " + symbol.name();
                }

                public boolean referencesParameter(Parameter parameter) {
                    return parameter == first;
                }
            };
        }

        /**
         * Creates an instruction constraint between two parameters such that their values cannot be the same.
         */
        public static InstructionConstraint ne(final Parameter first, final Parameter second) {
            return new SimpleInstructionConstraint() {

                public boolean check(Template template, List<Argument> arguments) {
                    return template.bindingFor(first, arguments).asLong() != template.bindingFor(second, arguments).asLong();
                }

                public String asJavaExpression() {
                    return first.valueString() + " != " + second.valueString();
                }

                public boolean referencesParameter(Parameter parameter) {
                    return parameter == first || parameter == second;
                }
            };
        }

        /**
         * Creates an instruction constraint on a parameter such that it must not be a given symbol.
         */
        public static InstructionConstraint ne(final Parameter first, final SymbolicArgument symbol) {
            return new SimpleInstructionConstraint() {

                public boolean check(Template template, List<Argument> arguments) {
                    return template.bindingFor(first, arguments) != symbol;
                }

                public String asJavaExpression() {
                    return first.variableName() + " != " + symbol.name();
                }

                public boolean referencesParameter(Parameter parameter) {
                    return parameter == first;
                }
            };
        }

        /**
         * Creates an instruction constraint between two parameters such that the value of the first parameter must be
         * less than the value of the second.
         */
        public static InstructionConstraint lt(final Parameter first, final Parameter second) {
            return new SimpleInstructionConstraint() {

                public boolean check(Template template, List<Argument> arguments) {
                    return template.bindingFor(first, arguments).asLong() < template.bindingFor(second, arguments).asLong();
                }

                public String asJavaExpression() {
                    return first.valueString() + " < " + second.valueString();
                }

                public boolean referencesParameter(Parameter parameter) {
                    return parameter == first || parameter == second;
                }

            };
        }

        /**
         * Creates an instruction constraint between two parameters such that the value of the first parameter must be
         * less than or equal to the value of the second.
         */
        public static InstructionConstraint le(final Parameter first, final Parameter second) {
            return new SimpleInstructionConstraint() {

                public boolean check(Template template, List<Argument> arguments) {
                    return template.bindingFor(first, arguments).asLong() <= template.bindingFor(second, arguments).asLong();
                }

                public String asJavaExpression() {
                    return first.valueString() + " <= " + second.valueString();
                }

                public boolean referencesParameter(Parameter parameter) {
                    return parameter == first || parameter == second;
                }

            };
        }

        /**
         * Creates an instruction constraint on a given parameter such that its value is greater than a given value.
         */
        public static InstructionConstraint gt(final Parameter first, final long value) {
            return new SimpleInstructionConstraint() {

                public boolean check(Template template, List<Argument> arguments) {
                    return template.bindingFor(first, arguments).asLong() > value;
                }

                public String asJavaExpression() {
                    return first.valueString() + " > " + value;
                }

                public boolean referencesParameter(Parameter parameter) {
                    return parameter == first;
                }

            };
        }

        /**
         * Creates an instruction constraint on a given parameter such that its value is less than a given value.
         */
        public static InstructionConstraint lt(final Parameter first, final long value) {
            return new SimpleInstructionConstraint() {

                public boolean check(Template template, List<Argument> arguments) {
                    return template.bindingFor(first, arguments).asLong() < value;
                }

                public String asJavaExpression() {
                    return first.valueString() + " < " + value;
                }

                public boolean referencesParameter(Parameter parameter) {
                    return parameter == first;
                }

            };
        }

        /**
         * Creates an instruction constraint on a given parameter such that its value must not be equal to a given value.
         */
        public static InstructionConstraint ne(final Parameter parameter, final long value) {
            return new SimpleInstructionConstraint() {

                public boolean check(Template template, List<Argument> arguments) {
                    return template.bindingFor(parameter, arguments).asLong() != value;
                }

                public String asJavaExpression() {
                    return parameter.valueString() + " != " + value;
                }

                public boolean referencesParameter(Parameter p) {
                    return parameter == p;
                }
            };
        }

        /**
         * Creates an instruction constraint on a given parameter such that its value must be even.
         */
        public static InstructionConstraint even(final Parameter parameter) {
            return new SimpleInstructionConstraint() {

                public boolean check(Template template, List<Argument> arguments) {
                    return template.bindingFor(parameter, arguments).asLong() % 2 == 0;
                }

                public String asJavaExpression() {
                    return "(" + parameter.valueString() + " % 2) == 0";
                }

                public boolean referencesParameter(Parameter p) {
                    return parameter == p;
                }
            };
        }

        /**
         * Gets a reference to a Java method that returns a boolean. This is a convenience wrapper for
         * {@link Class#getDeclaredMethod} that converts the checked {@link NoSuchMethodException} into a
         * {@link ProgramError}.
         *
         * The method must have a boolean return type and only accept parameter types corresponding to the unboxed types
         * of a generated assembler method.
         */
        public static Method getPredicateMethod(Class<?> declaringClass, String methodName, Class... parameterTypes) {
            try {
                return declaringClass.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e) {
                throw ProgramError.unexpected("constraint method not found: " + declaringClass + "." + methodName + "(" + Utils.toString(parameterTypes, ", ") + ")");
            }
        }

        /**
         * Creates an instruction constraint implemented by a Java method.
         *
         * @param predicateMethod
         *            the constraint checking method which must have a {@code boolean} return type
         * @param parameters
         *            the formal parameters of the assembler method whose values are constrained by {@code method}.
         *            There must be one element in this list for each formal parameter of {@code method}
         *            including one for the receiver if {@code method} is not static. The binding between the two
         *            is by position.
         */
        public static InstructionConstraint makePredicate(final Method predicateMethod, final Parameter... parameters) throws IllegalArgumentException {
            ProgramError.check(predicateMethod.getReturnType() == Boolean.TYPE, "predicate method must return a boolean");
            final boolean isStatic = Modifier.isStatic(predicateMethod.getModifiers());
            ProgramError.check(predicateMethod.getParameterTypes().length == (isStatic ? parameters.length : parameters.length - 1), "parameter count != method ");
            return new InstructionConstraint() {

               /**
                * @return the method implementing the constraint
                */
                public Method predicateMethod() {
                    return predicateMethod;
                }

                /**
                 * Determines if the constraint holds for a given set of actual values to a given assembler method.
                 *
                 * @param template
                 *            the template from which the assembler method was generated
                 * @param arguments
                 *            the actual values
                 * @return true if the constraint held for {@code arguments}
                 */
                public boolean check(Template template, List<Argument> arguments) {
                    int parameterIndex;
                    final Object receiver;
                    final Object[] objects;

                    if (isStatic) {
                        parameterIndex = 0;
                        receiver = null;
                        objects = new Object[parameters.length];
                    } else {
                        parameterIndex = 1;
                        objects = new Object[parameters.length - 1];
                        receiver = template.bindingFor(parameters[0], arguments);
                    }

                    for (int i = 0; i != objects.length; ++i, ++parameterIndex) {
                        final Parameter parameter = parameters[parameterIndex];
                        final Argument argument = template.bindingFor(parameter, arguments);
                        objects[i] = (argument instanceof ImmediateArgument) ? ((ImmediateArgument) argument).boxedJavaValue() : argument;
                    }

                    try {
                        return (Boolean) predicateMethod.invoke(receiver, objects);
                    } catch (IllegalArgumentException illegalArgumentException) {
                        throw ProgramError.unexpected("argument type mismatch", illegalArgumentException);
                    } catch (IllegalAccessException illegalAccessException) {
                        throw ProgramError.unexpected("illegal access to predicate method unexpected", illegalAccessException);
                    } catch (InvocationTargetException invocationTargetException) {
                        throw ProgramError.unexpected(invocationTargetException);
                    }
                }

                /**
                 * @return the Java boolean expression that is a call to the constraint method
                 */
                public String asJavaExpression() {
                    final StringBuilder buf = new StringBuilder();
                    int i;
                    if (isStatic) {
                        buf.append(predicateMethod.getDeclaringClass().getSimpleName());
                        i = 0;
                    } else {
                        buf.append(parameters[0].variableName());
                        i = 1;
                    }
                    buf.append('.').append(predicateMethod.getName()).append('(');
                    while (i < parameters.length) {
                        final Parameter parameter = parameters[i];
                        buf.append(parameter.variableName());
                        if (i != parameters.length - 1) {
                            buf.append(", ");
                        }
                        ++i;
                    }
                    buf.append(')');
                    return buf.toString();
                }

                public boolean referencesParameter(Parameter parameter) {
                    return Utils.indexOfIdentical(parameters, parameter) >= 0;
                }
            };
        }
    }
}
