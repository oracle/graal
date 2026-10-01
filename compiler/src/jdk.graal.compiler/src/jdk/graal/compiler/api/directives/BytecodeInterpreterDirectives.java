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
                 * Marks this field as template state for threaded bytecode handlers. A value of
                 * {@code N >= 2} creates one handler variant for each field value in the range
                 * {@code [0, N)}. A value of {@code 0} disables template specialization for this
                 * field, and {@code 1} is invalid.
                 * <p>
                 * Each variant starts with the corresponding field value. Before control transfers
                 * to the next threaded handler, every control-flow path must assign a known valid
                 * value or retain the current value. If multiple template fields are updated, their
                 * values must be resolved through the same control-flow merge.
                 * <p>
                 * When threading ends, the current field value is written back to the original
                 * argument. When template mode is disabled, the field behaves like an ordinary
                 * expanded field.
                 * <p>
                 * The field must be an {@code int} field of a {@link ExpansionKind#VIRTUAL}
                 * argument.
                 * <p>
                 * See the <a href=
                 * "https://github.com/oracle/graal/blob/master/truffle/docs/OneCompilationPerBytecodeHandler.md#template">
                 * template handler documentation</a> for examples and additional restrictions.
                 */
                int templateVariable() default 0;

                /**
                 * Names the template-variable field on the same virtual-expanded argument that
                 * controls whether this field's incoming value is valid. An empty name disables
                 * conditional validity and requires {@link #valid()} to be empty.
                 * <p>
                 * Only non-template {@code long} fields of {@link ExpansionKind#VIRTUAL} arguments
                 * support conditional validity. When template mode is disabled, this metadata is
                 * ignored.
                 */
                String validWhen() default "";

                /**
                 * Values of {@link #validWhen()} for which the incoming field value is valid.
                 * Otherwise, the generated handler starts with an arbitrary field value that must
                 * not be observed, including through exception-state materialization. The handler
                 * may overwrite the field before reading it. This contract applies at handler
                 * entry, not to subsequent changes of the template-variable field.
                 */
                int[] valid() default {};
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

        /**
         * Enables tail duplication for this interpreter's threaded bytecode handler stubs. When
         * enabled, the compiler removes the control-flow anchor at the handler return and encourages
         * duplication of the dispatch tail, allowing different handler paths to have separate
         * indirect tail-call sites. Duplication remains subject to compiler safety checks and code
         * size budgets; enabling this option does not guarantee that a tail will be duplicated.
         * <p>
         * Separate dispatch sites can improve branch prediction, but the additional code can also
         * reduce performance. This option is disabled by default and should only be enabled after
         * measuring the effect on the interpreter's workloads. When disabled, the return anchor is
         * retained and no tail-duplication hint is emitted. This option has no effect on non-threaded
         * stubs, which always retain their return anchor.
         */
        boolean enableTailDuplication() default false;
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
