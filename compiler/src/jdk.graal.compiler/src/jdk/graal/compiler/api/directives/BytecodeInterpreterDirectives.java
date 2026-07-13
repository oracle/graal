/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.api.directives;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Canonical compiler directives for bytecode interpreter outlining and tail call threading.
 *
 * @since 25.1
 */
public final class BytecodeInterpreterDirectives {

    private BytecodeInterpreterDirectives() {
    }

    /**
     * Annotates a method that serves as a bytecode interpreter bytecode handler, that is, a method
     * that implements the complete semantics of one or more bytecode instructions.
     *
     * @since 25.1
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface BytecodeInterpreterHandler {
        /**
         * The opcodes handled by this bytecode handler.
         */
        int[] value();

        /**
         * Indicates whether to enable tail call threading at the end of this handler. If
         * {@code false}, threading terminates after this handler and control returns to the
         * interpreter.
         */
        boolean threading() default true;

        /**
         * Indicates whether this handler can be dispatched while a template variable is in a
         * non-zero state. If {@code false}, non-zero template handler tables dispatch this handler's
         * opcodes to a generated fallback stub so control returns to the interpreter switch before
         * the handler executes. The default is {@code true}, meaning the handler is compatible with
         * all template states.
         */
        boolean templateCompatible() default true;

        /**
         * Indicates whether execution of this handler should include a safepoint check.
         */
        boolean safepoint() default true;
    }

    /**
     * Configuration for all bytecode interpreter handler arguments, including the receiver. This
     * annotation is placed on the bytecode interpreter method and is interpreted relative to each
     * {@link BytecodeInterpreterHandler} it calls.
     *
     * @since 25.1
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface BytecodeInterpreterHandlerConfig {
        /**
         * Configuration for one {@link BytecodeInterpreterHandlerConfig} argument.
         *
         * @since 25.1
         */
        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.METHOD)
        @interface Argument {
            /**
             * Describes how a bytecode handler argument is made available to outlined handlers.
             */
            enum ExpansionKind {
                /**
                 * The argument is passed unchanged.
                 */
                NONE,

                /**
                 * The argument is passed together with selected materialized fields.
                 */
                MATERIALIZED,

                /**
                 * The argument is replaced by its fields and the original argument is not passed.
                 */
                VIRTUAL,
            }

            /**
             * Configuration for one expanded field of an argument.
             *
             * @since 25.1
             */
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.METHOD)
            @interface Field {
                /**
                 * Name of the field to expand.
                 */
                String name();

                /**
                 * Indicates that this field is always non-null. This property is irrelevant for
                 * primitive fields.
                 */
                boolean nonNull() default true;

                /**
                 * Marks this field as scratch state when template mode is enabled. Scratch fields
                 * are carried between threaded bytecode handler stubs, but they are not initialized
                 * from the original Java object on entry, are not written back to the original Java
                 * object on exit, and are not preserved through pending exception state. This is
                 * intended for register-resident interpreter state that is valid only while threaded
                 * execution remains inside the generated handler stubs. When template mode is not
                 * enabled, this metadata is ignored and the field remains an ordinary expanded
                 * argument.
                 * <p>
                 * This property is only supported for fields of {@link ExpansionKind#VIRTUAL}
                 * arguments. It cannot be combined with {@link #templateVariable()}.
                 */
                boolean scratch() default false;

                /**
                 * Marks the expanded field as the template variable used to specialize template
                 * variants and select the template variant of the next threaded bytecode handler.
                 * A value of {@code 0} means that the field is not a template variable. A value of
                 * {@code N >= 2} makes the field a template variable with {@code N} variants.
                 * {@code 1} is invalid. If multiple template variables are configured, the total
                 * template count is the product of their variant counts. Template variables are
                 * encoded in expanded-field order using mixed-radix indexing.
                 * <p>
                 * At a threaded dispatch, each template variable value must be a constant or a phi
                 * whose inputs recursively resolve to constants. If multiple template variables are
                 * non-constant phis at the same dispatch, those phis must be produced by the same
                 * merge. Independent branch merges for different template variables are rejected.
                 * <p>
                 * When template mode is enabled, template variables are initialized from the
                 * selected template variant and do not occupy a stub ABI argument slot. Their value
                 * at the end of a threaded handler selects the variant of the next threaded handler.
                 * When template mode is not enabled, this metadata is ignored and the field remains
                 * an ordinary expanded argument.
                 * <p>
                 * The field must be an {@code int} field of a {@link ExpansionKind#VIRTUAL}
                 * argument.
                 */
                int templateVariable() default 0;
            }

            /**
             * Indicates that this argument is updated with the bytecode handler return value.
             */
            boolean returnValue() default false;

            /**
             * Indicates whether this argument is expanded for bytecode handlers.
             */
            ExpansionKind expand() default ExpansionKind.NONE;

            /**
             * Fields to expand when {@link #expand()} is {@link ExpansionKind#MATERIALIZED}.
             */
            Field[] fields() default {};

            /**
             * Indicates that this argument is always non-null. This property is irrelevant for
             * primitive arguments.
             */
            boolean nonNull() default true;
        }

        /**
         * The maximum unsigned opcode value that can be handled by this interpreter.
         */
        int maximumOperationCode();

        /**
         * Configuration for each handler method argument. For non-static methods, the first element
         * corresponds to the receiver.
         */
        Argument[] arguments();

        /**
         * Indicates that the annotated method implements a secondary partition of a bytecode
         * interpreter switch.
         * <p>
         * A secondary switch is expected to be inlined into a primary bytecode interpreter switch
         * during host compilation. Its handler configuration is retained so that handler calls
         * originating from the inlined secondary switch can be mapped to the primary switch's
         * handler stubs.
         * <p>
         * When compiled as a separate method, however, handler calls in a secondary switch are not
         * outlined. In particular, a deoptimization target may invoke the separately compiled
         * secondary switch without first passing through host inlining. Keeping its handler calls
         * ordinary prevents such execution from entering threaded handler stubs without the
         * primary switch's exception and state-management paths.
         *
         * @return {@code true} if the annotated method is a secondary switch partition whose
         *         handler calls must not be outlined when the method is compiled separately
         */
        boolean secondarySwitch() default false;
    }

    /**
     * Annotates the method that fetches the next opcode. The annotated method must be side-effect
     * free and share the same signature with {@link BytecodeInterpreterHandler}-annotated methods
     * in the same enclosing class. It must not throw an exception.
     *
     * @since 25.1
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    public @interface BytecodeInterpreterFetchOpcode {
    }

}
