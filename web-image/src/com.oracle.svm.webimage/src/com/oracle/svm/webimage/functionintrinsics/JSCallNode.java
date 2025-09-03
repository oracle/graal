/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.webimage.functionintrinsics;

import static com.oracle.svm.webimage.functionintrinsics.JSCallNode.WEB_IMAGE_CYCLES_RATIONALE;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.vm.ci.meta.JavaKind.Int;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.vm.ci.meta.JavaKind;

/**
 * This node represents an invoke of a {@link JSSystemFunction}.
 * <p>
 * The node's stamp matches {@link JSSystemFunction#stamp()}.
 */
@NodeInfo(nameTemplate = "JSCall#{p#function}", cycles = CYCLES_UNKNOWN, cyclesRationale = WEB_IMAGE_CYCLES_RATIONALE, size = NodeSize.SIZE_IGNORED)
@Node.NodeIntrinsicFactory
public class JSCallNode extends FixedWithNextNode {

    public static final NodeClass<JSCallNode> TYPE = NodeClass.create(JSCallNode.class);

    public static final String WEB_IMAGE_CYCLES_RATIONALE = "Setting a value for the cycles does not make sense in the Web Image context";
    public static final Stamp DOUBLE_STAMP = StampFactory.forKind(JavaKind.Double);
    public static final Stamp LONG_STAMP = StampFactory.forKind(JavaKind.Long);
    public static final Stamp INTEGER_STAMP = StampFactory.forKind(Int);

    public static final JSSystemFunction LLOG = new JSSystemFunction("llog", JavaKind.Object);

    public static final JSSystemFunction SET_EXIT_CODE = new JSSystemFunction("runtime.setExitCode", Int);

    public static final JSSystemFunction ARRAY_COPY = new JSSystemFunction("arrayCopy", JavaKind.Object, Int, JavaKind.Object, Int, Int);
    public static final JSSystemFunction SHOULD_NOT_REACH_HERE = new JSSystemFunction("ShouldNotReachHere", JavaKind.Object);
    public static final JSSystemFunction CURRENT_TIME_MILLIS_DATE = new JSSystemFunction("Date.now", DOUBLE_STAMP);
    /**
     * High resolution timestamp in milliseconds relative to some origin.
     * <p>
     * Provides a monotonic clock with up to microsecond precision.
     */
    public static final JSSystemFunction PERFORMANCE_NOW = new JSSystemFunction("performance.now", DOUBLE_STAMP);
    // without the hub
    public static final JSSystemFunction ARRAYS_COPY_OF_0 = new JSSystemFunction("arraysCopyOf", StampFactory.objectNonNull(), JavaKind.Object, Int, null);
    // with the hub
    public static final JSSystemFunction ARRAYS_COPY_OF_1 = new JSSystemFunction("arraysCopyOfWithHub", StampFactory.objectNonNull(), JavaKind.Object, Int, JavaKind.Object, null);

    public static final JSSystemFunction PRINT_CHARS_OUT = new JSSystemFunction("stdoutWriter.printChars", JavaKind.Object);
    public static final JSSystemFunction PRINT_CHARS_ERR = new JSSystemFunction("stderrWriter.printChars", JavaKind.Object);

    public static final JSSystemFunction PRINT_FLUSH_OUT = new JSSystemFunction("stdoutWriter.flush");
    public static final JSSystemFunction PRINT_CLOSE_OUT = new JSSystemFunction("stdoutWriter.close");

    public static final JSSystemFunction PRINT_FLUSH_ERR = new JSSystemFunction("stderrWriter.flush");
    public static final JSSystemFunction PRINT_CLOSE_ERR = new JSSystemFunction("stderrWriter.close");
    public static final JSSystemFunction GEN_BACKTRACE = new JSSystemFunction("genBacktrace", StampFactory.objectNonNull());
    public static final JSSystemFunction FORMAT_STACKTRACE = new JSSystemFunction("formatStackTrace", StampFactory.objectNonNull(), JavaKind.Object);
    public static final JSSystemFunction GEN_CALL_STACK = new JSSystemFunction("gen_call_stack", StampFactory.objectNonNull());
    public static final JSSystemFunction GET_CURRENT_WORKING_DIRECTORY = new JSSystemFunction("getCurrentWorkingDirectory", StampFactory.objectNonNull());
    /**
     * For debugging purposes.
     */
    public static final JSSystemFunction PRINT_STACK_TRACE = new JSSystemFunction("console.trace");

    public static final JSSystemFunction STRICT_MATH_SIN = new JSSystemFunction("Math.sin", DOUBLE_STAMP, JavaKind.Double);
    public static final JSSystemFunction STRICT_MATH_COS = new JSSystemFunction("Math.cos", DOUBLE_STAMP, JavaKind.Double);
    public static final JSSystemFunction STRICT_MATH_TAN = new JSSystemFunction("Math.tan", DOUBLE_STAMP, JavaKind.Double);
    public static final JSSystemFunction STRICT_MATH_ASIN = new JSSystemFunction("Math.asin", DOUBLE_STAMP, JavaKind.Double);
    public static final JSSystemFunction STRICT_MATH_ACOS = new JSSystemFunction("Math.acos", DOUBLE_STAMP, JavaKind.Double);
    public static final JSSystemFunction STRICT_MATH_ATAN = new JSSystemFunction("Math.atan", DOUBLE_STAMP, JavaKind.Double);
    public static final JSSystemFunction STRICT_MATH_EXP = new JSSystemFunction("Math.exp", DOUBLE_STAMP, JavaKind.Double);
    public static final JSSystemFunction STRICT_MATH_LOG = new JSSystemFunction("Math.log", DOUBLE_STAMP, JavaKind.Double);
    public static final JSSystemFunction STRICT_MATH_LOG10 = new JSSystemFunction("Math.log10", DOUBLE_STAMP, JavaKind.Double);
    public static final JSSystemFunction STRICT_MATH_SQRT = new JSSystemFunction("Math.sqrt", DOUBLE_STAMP, JavaKind.Double);
    public static final JSSystemFunction STRICT_MATH_ATAN2 = new JSSystemFunction("Math.atan2", DOUBLE_STAMP, JavaKind.Double, JavaKind.Double);
    public static final JSSystemFunction STRICT_MATH_SINH = new JSSystemFunction("Math.sinh", DOUBLE_STAMP, JavaKind.Double);
    public static final JSSystemFunction STRICT_MATH_COSH = new JSSystemFunction("Math.cosh", DOUBLE_STAMP, JavaKind.Double);
    public static final JSSystemFunction STRICT_MATH_TANH = new JSSystemFunction("Math.tanh", DOUBLE_STAMP, JavaKind.Double);
    public static final JSSystemFunction STRICT_MATH_HYPOT = new JSSystemFunction("Math.hypot", DOUBLE_STAMP, JavaKind.Double, JavaKind.Double);
    public static final JSSystemFunction STRICT_MATH_EXPM1 = new JSSystemFunction("Math.exmp1", DOUBLE_STAMP, JavaKind.Double);
    public static final JSSystemFunction STRICT_MATH_LOG1P = new JSSystemFunction("Math.log1p", DOUBLE_STAMP, JavaKind.Double);
    public static final JSSystemFunction STRICT_MATH_POW = new JSSystemFunction("Math.pow", DOUBLE_STAMP, JavaKind.Double, JavaKind.Double);
    public static final JSSystemFunction STRICT_MATH_CBRT = new JSSystemFunction("Math.cbrt", DOUBLE_STAMP, JavaKind.Double);
    public static final JSSystemFunction MATH_IMUL = new JSSystemFunction("Math.imul", INTEGER_STAMP, Int, Int);

    public static final JSSystemFunction MEM_MALLOC = new JSSystemFunction("heap.malloc", LONG_STAMP, JavaKind.Long);
    public static final JSSystemFunction MEM_CALLOC = new JSSystemFunction("heap.calloc", LONG_STAMP, JavaKind.Long);
    public static final JSSystemFunction MEM_REALLOC = new JSSystemFunction("heap.realloc", LONG_STAMP, JavaKind.Long, JavaKind.Long);
    public static final JSSystemFunction MEM_FREE = new JSSystemFunction("heap.free", JavaKind.Long);

    @Input protected NodeInputList<ValueNode> arguments;
    protected final JSSystemFunction function;

    public JSSystemFunction getFunctionDefinition() {
        return function;
    }

    public NodeInputList<ValueNode> getArguments() {
        return arguments;
    }

    @SuppressWarnings("this-escape")
    public JSCallNode(JSSystemFunction function, Stamp s, ValueNode... args) {
        super(TYPE, s);
        this.arguments = new NodeInputList<>(this, args);
        this.function = function;

        assert args.length == function.getNrOfArgs() : "Function " + function + " has " + function.getNrOfArgs() + " arguments, but " + args.length + " were given.";

        for (int i = 0; i < args.length; i++) {
            JavaKind argKind = args[i].getStackKind();
            JavaKind expected = function.getArgKinds()[i];

            if (expected == null) {
                continue;
            }

            assert expected.getStackKind() == argKind : function + ": For argument " + i + ", expected " + expected + ", but got " + argKind;
        }
    }

    public static boolean intrinsify(GraphBuilderContext b, @InjectedNodeParameter Stamp s, JSSystemFunction function, ValueNode... args) {
        JSCallNode node = new JSCallNode(function, s, args);
        node.asNode().setStamp(s);

        JavaKind returnKind = s.getStackKind();
        if (returnKind == JavaKind.Void) {
            b.add(node.asNode());
        } else {
            b.addPush(returnKind, node.asNode());
        }

        return true;
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return "JSCall#" + function;
        } else {
            return super.toString(verbosity);
        }
    }

    @NodeIntrinsic
    public static native void call(@ConstantNodeParameter JSSystemFunction function);

    @NodeIntrinsic
    public static native void call1(@ConstantNodeParameter JSSystemFunction function, Object arg1);

    @NodeIntrinsic
    public static native Object supplier(@ConstantNodeParameter JSSystemFunction function);

    @NodeIntrinsic
    public static native Object function(@ConstantNodeParameter JSSystemFunction function, Object arg1);

    @NodeIntrinsic
    public static native double doubleSupplier(@ConstantNodeParameter JSSystemFunction function);

    @NodeIntrinsic
    public static native double toDoubleFunction(@ConstantNodeParameter JSSystemFunction function, double a);

    @NodeIntrinsic
    public static native double toDoubleBiFunction(@ConstantNodeParameter JSSystemFunction function, double a, double b);

    @NodeIntrinsic
    public static native long toLongFunction(@ConstantNodeParameter JSSystemFunction function, long arg0);

    @NodeIntrinsic
    public static native long toLongBiFunction(@ConstantNodeParameter JSSystemFunction function, long arg0, long arg1);

    @NodeIntrinsic
    public static native void longConsumer(@ConstantNodeParameter JSSystemFunction function, long ptr);

    @NodeIntrinsic
    public static native void intConsumer(@ConstantNodeParameter JSSystemFunction function, int ptr);

    @NodeIntrinsic
    public static native void arrayCopy(@ConstantNodeParameter JSSystemFunction function, Object fromArray, int fromIndex, Object toArray, int toIndex, int length);

}
