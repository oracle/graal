/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test.examples;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.dsl.AOTSupport;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.examples.AOTTutorialFactory.AddNodeGen;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.nodes.ExecutionSignature;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.sl.nodes.expression.SLAddNode;

/**
 * A basic example language for AOT compilation.
 *
 * See also the <a href= "https://github.com/oracle/graal/blob/master/truffle/docs/AOT.md">usage
 * tutorial</a> on the website.
 */
public class AOTTutorial {

    /**
     * We annotate our language base node with {@link GenerateAOT} to indicate that we intend to
     * support AOT for all subclasses of this type.
     */
    @GenerateAOT
    abstract static class BaseNode extends Node {

        abstract Object execute(VirtualFrame frame);

    }

    /**
     * As with any Truffle language we need to declare a root node. In addition to normal root nodes
     * this class also implements {@link RootNode#prepareForAOT}.
     */
    static final class AOTRootNode extends RootNode {

        @Child private BaseNode body;
        private final Class<?> returnType;
        private final Class<?>[] argumentTypes;

        protected AOTRootNode(AOTTestLanguage language, BaseNode body, Class<?> returnType, Class<?>[] argumentTypes) {
            super(language);
            this.returnType = returnType;
            this.argumentTypes = argumentTypes;
            this.body = body;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return body.execute(frame);
        }

        @Override
        public String getName() {
            return "sample";
        }

        @Override
        public String toString() {
            return getName();
        }

        /*
         * {@link #prepareForAOT()} needs to be implemented for AOT support. Any non
         * <code>null</code> value will indicate that this root node can be compiled without prior
         * execution.
         */
        @Override
        protected ExecutionSignature prepareForAOT() {
            /*
             * Calling AOTSupport.prepareForAOT traverses all the AST nodes and initializes them for
             * AOT recursively.
             */
            AOTSupport.prepareForAOT(this);

            /*
             * By returning the execution signature to Truffle we allow the compiler to specialize
             * the argument and return types of this root without prior execution.
             */
            return ExecutionSignature.create(returnType, argumentTypes);

            /*
             * A real language would typically need to do additional tasks during preparation. For
             * example initialize the frame descriptor of this root node.
             */
        }

    }

    /**
     * This is a non-specializing node to read the argument from a particular index. Since this node
     * does not do any specialization no preparation for AOT is necessary.
     */
    static final class ArgumentNode extends BaseNode {

        final int index;

        ArgumentNode(int index) {
            this.index = index;
        }

        @Override
        Object execute(VirtualFrame frame) {
            return frame.getArguments()[index];
        }

    }

    /**
     * This node represents a binary addition of either integer or string values. It extends
     * {@link BaseNode} and therefore inherits {@link GenerateAOT} semantics. This is a subset of
     * the supported specializations of SL's {@link SLAddNode}.
     *
     * It may be helpful to read the generated code for this node here
     * {@link AddNodeGen#prepareForAOT(TruffleLanguage, RootNode) prepareForAOT} and resetAOT
     * respectively.
     */
    @NodeChild("left")
    @NodeChild("right")
    abstract static class AddNode extends BaseNode {

        /**
         * By default all specializations are included for AOT and will be set active. In this case
         * doInt and doString do not bind any cached values from dynamic parameters and therefore
         * can be prepared for AOT without problem.
         */
        @Specialization
        static int doInt(int left, int right) {
            return left + right;
        }

        @Specialization(guards = "isString(left, right)")
        @TruffleBoundary
        protected static String doString(Object left, Object right) {
            return left.toString() + right.toString();
        }

        protected static boolean isString(Object a, Object b) {
            return a instanceof String || b instanceof String;
        }

        /**
         * The doDouble specialization is only enabled depending on an option stored in the language
         * instance.
         *
         * In this case since doublesEnabled is not set to true this specialization will not produce
         * any code after AOT preparation.
         */
        @Specialization(guards = "language.doublesEnabled")
        @TruffleBoundary
        @SuppressWarnings("unused")
        protected static double doDouble(double left, double right,
                        @Cached(value = "getASTLanguage()", neverDefault = true) AOTTestLanguage language) {
            return left + right;
        }

        final AOTTestLanguage getASTLanguage() {
            return getRootNode().getLanguage(AOTTestLanguage.class);
        }

        /**
         * Also language libraries can be prepared for AOT. However, in order for that to work the
         * library needs to declare {@link GenerateAOT} on the library declaration. And library
         * exports need to enable use for AOT with {@link ExportLibrary#useForAOT()}.
         */
        @Specialization(limit = "3", guards = "useLibrary()")
        Object doAddLibrary(Object left, Object right,
                        @CachedLibrary("left") AddLibrary addLib) {
            return addLib.add(left, right);
        }

        @Idempotent
        static boolean useLibrary() {
            /*
             * This library is not really useful and only here to show-case how to use libraries
             * with AOT.
             */
            return false;
        }

        /**
         * Even though this is a static language we should still support dynamic Truffle interop
         * values. Since interop libraries require dynamic parameters to be initialized we need to
         * exclude it from AOT by using the {@link GenerateAOT.Exclude} annotation. This means that
         * the AOT compiled code will deoptimize in case it will encounter this specialization.
         * After deoptimization the node reset its profiling information on the next execution in
         * the interpreter.
         *
         * Without the exclude annotation this specialization would result in a compile-time error.
         */
        @GenerateAOT.Exclude
        @Specialization(limit = "3")
        Object doInterop(Object left, Object right,
                        @CachedLibrary("left") InteropLibrary leftLib,
                        @CachedLibrary("right") InteropLibrary rightLib) {
            try {
                if (leftLib.fitsInInt(left) && rightLib.fitsInInt(right)) {
                    return doInt(leftLib.asInt(leftLib), rightLib.asInt(rightLib));
                } else if (leftLib.isString(left) && rightLib.isString(right)) {
                    return doString(leftLib.asString(leftLib), rightLib.asString(rightLib));
                } else {
                    throw CompilerDirectives.shouldNotReachHere("not-implemened");
                }
            } catch (InteropException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

    }

    /**
     * An example of a custom library that supports AOT.
     */
    @GenerateLibrary
    @GenerateAOT
    public abstract static class AddLibrary extends Library {

        public abstract Object add(Object receiver, Object other);

    }

    /**
     * An example for library export that supports AOT. Note that this also works with dynamic
     * dispatched receiver types, default types. A priority must be specified to determine the order
     * of the AOT exports for AOT preparation.
     * <p>
     * The receiver type must be final for that to work, otherwise the generated code might not be
     * ideal. There can be any number of exports specified, they are loaded eagerly when AOT is used
     * for the first time even without instances of {@link Addable} used.
     */
    @ExportLibrary(value = AddLibrary.class, useForAOT = true, useForAOTPriority = 42)
    public static final class Addable {

        @SuppressWarnings({"static-method", "unused"})
        @ExportMessage
        Object add(Object other) {
            return 42;
        }

    }

    @Registration(id = "AOTTestLanguage", name = "AOTTestLanguage")
    public static class AOTTestLanguage extends TruffleLanguage<Env> {

        /*
         * This is an option to show how language references can be used during AOT initialization.
         * See AddNode#doDouble for an example on how to use it from the DSL.
         */
        @CompilationFinal boolean doublesEnabled;

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            /*
             * A real language would call into a parser here. This language omits parsing for the
             * sake of simplicity. We just always return a sample AST.
             */
            if (request.getSource().getCharacters().toString().equals("sample")) {
                /*
                 * We assume that the parser could infer the arguments from the source code of our
                 * example language. If the parser would fail at inferring an individual argument
                 * null could be used.
                 */
                Class<?>[] argumentTypes = new Class<?>[]{Integer.class, Integer.class, String.class};

                /*
                 * From the argument types and because our language is a static language we assume
                 * that the parser could infer a return type. If the parser failed at doing so we
                 * could also just use null to indicate that the type is not known.
                 */
                Class<?> returnType = String.class;

                // we assume that the parser produced the following AST for the body of the
                // program
                // this is equivalent to (arg0 + arg1) + arg2
                BaseNode body = AddNodeGen.create(AddNodeGen.create(new ArgumentNode(0), new ArgumentNode(1)), new ArgumentNode(2));
                return new AOTRootNode(this, body, returnType, argumentTypes).getCallTarget();
            } else {
                throw CompilerDirectives.shouldNotReachHere("not-implemented");
            }
        }

    }

    @Test
    public void testAOT() {
        // The log output is Graal specific and therefore can only be asserted in a Graal
        // runtime.
        Assume.assumeTrue(isGraalRuntime());

        ByteArrayOutputStream log = new ByteArrayOutputStream();

        try (Context context = Context.newBuilder().//
                        logHandler(log).//
                        allowExperimentalOptions(true).//
                        option("engine.TraceCompilation", "true").//
                        option("engine.CompileImmediately", "false").//
                        option("engine.CompileAOTOnCreate", "true").build()) {

            // Since we set engine.CompileAOTOnCreate to true the sample function will be
            // immediately compiled at parse time without prior execution.
            Value v = context.parse("AOTTestLanguage", "sample");
            String beforeExecute = log.toString();
            assertTrue(beforeExecute, beforeExecute.contains("[engine] opt done") && beforeExecute.contains("sample"));

            // we can compile the function and it is executed compiled immediately.
            // note that if we would use any other types than the ones used during AOT
            // preparation
            // the code would deoptimize and the initialized specialization profile would be
            // automatically reset.
            String result = v.execute(1, 3, "2").asString();

            // the log should not contain any invalidations after execute
            assertEquals(beforeExecute, log.toString());

            // of (1 + 3) + "2" equals "42"
            assertEquals("42", result);
        }
    }

    private static boolean isGraalRuntime() {
        Engine engine = Engine.create();
        try {
            return engine.getImplementationName().contains("Graal");
        } finally {
            engine.close();
        }
    }

}
