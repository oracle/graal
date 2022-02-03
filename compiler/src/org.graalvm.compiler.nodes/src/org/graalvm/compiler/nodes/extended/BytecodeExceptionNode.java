/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.InputType.Value;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.AbstractMemoryCheckpoint;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * A node that represents an exception thrown implicitly by a Java bytecode. It can be lowered to
 * either a {@linkplain ForeignCallDescriptor foreign} call or a pre-allocated exception object.
 */
// @formatter:off
@NodeInfo(allowedUsageTypes = {Value, Memory},
          cycles = CYCLES_8,
          cyclesRationale = "Node will be lowered to a foreign call.",
          size = SIZE_8)
// @formatter:on
public final class BytecodeExceptionNode extends AbstractMemoryCheckpoint implements Lowerable, SingleMemoryKill, Canonicalizable {

    public enum BytecodeExceptionKind {
        /**
         * Represents a {@link NullPointerException}. No arguments are allowed.
         */
        NULL_POINTER(0, NullPointerException.class),

        /**
         * Represents a {@link ArrayIndexOutOfBoundsException}. Two arguments are required:
         * <ol>
         * <li>The array index that could not be accessed (type: int)</li>
         * <li>The length of the array that was accessed (type: int)</li>
         * </ol>
         */
        OUT_OF_BOUNDS(2, ArrayIndexOutOfBoundsException.class),

        /**
         * Represents a {@link ArrayIndexOutOfBoundsException} in an intrinsic. No arguments are
         * allowed.
         */
        INTRINSIC_OUT_OF_BOUNDS(0, ArrayIndexOutOfBoundsException.class),

        /**
         * Represents a {@link ClassCastException}. Two arguments are required:
         * <ol>
         * <li>The object that could not be cast (type: java.lang.Object, non-null)</li>
         * <li>The class that the object should have been casted to (type: java.lang.Class,
         * non-null)</li>
         * </ol>
         */
        CLASS_CAST(2, ClassCastException.class),

        /**
         * Represents a {@link ArrayStoreException}. One arguments is required:
         * <ol>
         * <li>The value that could not be stored (type: java.lang.Object, non-null)</li>
         * </ol>
         */
        ARRAY_STORE(1, ArrayStoreException.class),

        /** Represents a {@link AssertionError} without arguments. */
        ASSERTION_ERROR_NULLARY(0, AssertionError.class),

        /**
         * Represents a {@link AssertionError} with one required Object argument for the error
         * message.
         */
        ASSERTION_ERROR_OBJECT(1, AssertionError.class),

        /**
         * Represents a {@link IllegalArgumentException} with a fixed message. No additional
         * arguments are allowed.
         */
        ILLEGAL_ARGUMENT_EXCEPTION_NEGATIVE_LENGTH(0, IllegalArgumentException.class, "Negative length"),

        /**
         * Represents a {@link IllegalArgumentException} with a fixed message. No additional
         * arguments are allowed.
         */
        ILLEGAL_ARGUMENT_EXCEPTION_ARGUMENT_IS_NOT_AN_ARRAY(0, IllegalArgumentException.class, "Argument is not an array"),

        /**
         * Represents a {@link NegativeArraySizeException} with one required int argument for the
         * length of the array.
         */
        NEGATIVE_ARRAY_SIZE(1, NegativeArraySizeException.class),

        /**
         * Represents a {@link ArithmeticException}, with the exception message indicating a
         * division by zero. No arguments are allowed.
         */
        DIVISION_BY_ZERO(0, ArithmeticException.class),

        /**
         * Represents a {@link ArithmeticException}, with the exception message indicating an
         * integer overflow. No arguments are allowed.
         */
        INTEGER_EXACT_OVERFLOW(0, ArithmeticException.class),

        /**
         * Represents a {@link ArithmeticException}, with the exception message indicating a long
         * overflow. No arguments are allowed.
         */
        LONG_EXACT_OVERFLOW(0, ArithmeticException.class);

        final int numArguments;
        final Class<? extends Throwable> exceptionClass;
        final String exceptionMessage;

        BytecodeExceptionKind(int numArguments, Class<? extends Throwable> exceptionClass) {
            this(numArguments, exceptionClass, null);
        }

        BytecodeExceptionKind(int numArguments, Class<? extends Throwable> exceptionClass, String exceptionMessage) {
            this.numArguments = numArguments;
            this.exceptionClass = exceptionClass;
            this.exceptionMessage = exceptionMessage;
        }

        public String getExceptionMessage() {
            return exceptionMessage;
        }

        public int getNumArguments() {
            return numArguments;
        }
    }

    public static final NodeClass<BytecodeExceptionNode> TYPE = NodeClass.create(BytecodeExceptionNode.class);
    protected final BytecodeExceptionKind exceptionKind;
    @Input NodeInputList<ValueNode> arguments;

    public BytecodeExceptionNode(MetaAccessProvider metaAccess, BytecodeExceptionKind exceptionKind, ValueNode... arguments) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createExactTrusted(metaAccess.lookupJavaType(exceptionKind.exceptionClass))));
        this.exceptionKind = exceptionKind;
        this.arguments = new NodeInputList<>(this, arguments);
        GraalError.guarantee(arguments.length == exceptionKind.numArguments, "Mismatch in argument count for BytecodeExceptionNode");
    }

    public BytecodeExceptionKind getExceptionKind() {
        return exceptionKind;
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(verbosity) + "#" + exceptionKind;
        }
        return super.toString(verbosity);
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && (hasNoUsages() || (hasExactlyOneUsage() && usages().first() == stateAfter))) {
            return null;
        }
        return this;
    }

    public NodeInputList<ValueNode> getArguments() {
        return arguments;
    }

    /**
     * Create a new stateDuring for use by a foreign call.
     */
    public FrameState createStateDuring() {
        boolean rethrowException = false;
        boolean duringCall = true;
        return stateAfter.duplicateModified(graph(), stateAfter.bci, rethrowException, duringCall,
                        JavaKind.Object, null, null, null);
    }

}
