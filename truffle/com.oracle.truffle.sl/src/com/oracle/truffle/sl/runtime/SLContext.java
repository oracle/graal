/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.runtime;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ExecutionContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.builtins.SLAssertFalseBuiltinFactory;
import com.oracle.truffle.sl.builtins.SLAssertTrueBuiltinFactory;
import com.oracle.truffle.sl.builtins.SLBuiltinNode;
import com.oracle.truffle.sl.builtins.SLDefineFunctionBuiltinFactory;
import com.oracle.truffle.sl.builtins.SLEvalBuiltinFactory;
import com.oracle.truffle.sl.builtins.SLHelloEqualsWorldBuiltinFactory;
import com.oracle.truffle.sl.builtins.SLImportBuiltinFactory;
import com.oracle.truffle.sl.builtins.SLNanoTimeBuiltinFactory;
import com.oracle.truffle.sl.builtins.SLNewObjectBuiltinFactory;
import com.oracle.truffle.sl.builtins.SLPrintlnBuiltin;
import com.oracle.truffle.sl.builtins.SLPrintlnBuiltinFactory;
import com.oracle.truffle.sl.builtins.SLReadlnBuiltin;
import com.oracle.truffle.sl.builtins.SLReadlnBuiltinFactory;
import com.oracle.truffle.sl.builtins.SLStackTraceBuiltinFactory;
import com.oracle.truffle.sl.nodes.SLExpressionNode;
import com.oracle.truffle.sl.nodes.SLRootNode;
import com.oracle.truffle.sl.nodes.local.SLReadArgumentNode;
import com.oracle.truffle.sl.parser.Parser;
import com.oracle.truffle.sl.parser.SLNodeFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;

/**
 * The run-time state of SL during execution. One context is instantiated before any source code is
 * parsed, and this context is passed around to all methods that need access to it. For example, the
 * context is used during {@link SLNodeFactory parsing} and by {@link SLBuiltinNode#getContext()
 * builtin functions}.
 * <p>
 * It would be an error to have two different context instances during the execution of one script.
 * However, if two separate scripts run in one Java VM at the same time, they have a different
 * context. Therefore, the context is not a singleton.
 */
public final class SLContext extends ExecutionContext {
    private static final Layout LAYOUT = Layout.createLayout();

    private final BufferedReader input;
    private final PrintWriter output;
    private final SLFunctionRegistry functionRegistry;
    private final Shape emptyShape;
    private final TruffleLanguage.Env env;

    public SLContext(TruffleLanguage.Env env, BufferedReader input, PrintWriter output) {
        this(env, input, output, true);
    }

    public SLContext() {
        this(null, null, null, false);
    }

    private SLContext(TruffleLanguage.Env env, BufferedReader input, PrintWriter output, boolean installBuiltins) {
        this.input = input;
        this.output = output;
        this.env = env;
        this.functionRegistry = new SLFunctionRegistry();
        installBuiltins(installBuiltins);

        this.emptyShape = LAYOUT.createShape(new SLObjectType());
    }

    /**
     * Returns the default input, i.e., the source for the {@link SLReadlnBuiltin}. To allow unit
     * testing, we do not use {@link System#in} directly.
     */
    public BufferedReader getInput() {
        return input;
    }

    /**
     * The default default, i.e., the output for the {@link SLPrintlnBuiltin}. To allow unit
     * testing, we do not use {@link System#out} directly.
     */
    public PrintWriter getOutput() {
        return output;
    }

    /**
     * Returns the registry of all functions that are currently defined.
     */
    public SLFunctionRegistry getFunctionRegistry() {
        return functionRegistry;
    }

    /**
     * Adds all builtin functions to the {@link SLFunctionRegistry}. This method lists all
     * {@link SLBuiltinNode builtin implementation classes}.
     */
    private void installBuiltins(boolean registerRootNodes) {
        installBuiltin(SLReadlnBuiltinFactory.getInstance(), registerRootNodes);
        installBuiltin(SLPrintlnBuiltinFactory.getInstance(), registerRootNodes);
        installBuiltin(SLNanoTimeBuiltinFactory.getInstance(), registerRootNodes);
        installBuiltin(SLDefineFunctionBuiltinFactory.getInstance(), registerRootNodes);
        installBuiltin(SLStackTraceBuiltinFactory.getInstance(), registerRootNodes);
        installBuiltin(SLHelloEqualsWorldBuiltinFactory.getInstance(), registerRootNodes);
        installBuiltin(SLAssertTrueBuiltinFactory.getInstance(), registerRootNodes);
        installBuiltin(SLAssertFalseBuiltinFactory.getInstance(), registerRootNodes);
        installBuiltin(SLNewObjectBuiltinFactory.getInstance(), registerRootNodes);
        installBuiltin(SLEvalBuiltinFactory.getInstance(), registerRootNodes);
        installBuiltin(SLImportBuiltinFactory.getInstance(), registerRootNodes);
    }

    public void installBuiltin(NodeFactory<? extends SLBuiltinNode> factory, boolean registerRootNodes) {
        /*
         * The builtin node factory is a class that is automatically generated by the Truffle DSL.
         * The signature returned by the factory reflects the signature of the @Specialization
         * methods in the builtin classes.
         */
        int argumentCount = factory.getExecutionSignature().size();
        SLExpressionNode[] argumentNodes = new SLExpressionNode[argumentCount];
        /*
         * Builtin functions are like normal functions, i.e., the arguments are passed in as an
         * Object[] array encapsulated in SLArguments. A SLReadArgumentNode extracts a parameter
         * from this array.
         */
        for (int i = 0; i < argumentCount; i++) {
            argumentNodes[i] = new SLReadArgumentNode(null, i);
        }
        /* Instantiate the builtin node. This node performs the actual functionality. */
        SLBuiltinNode builtinBodyNode = factory.createNode(argumentNodes, this);
        /* The name of the builtin function is specified via an annotation on the node class. */
        String name = lookupNodeInfo(builtinBodyNode.getClass()).shortName();

        final SourceSection srcSection = SourceSection.createUnavailable(SLLanguage.builtinKind, name);
        /* Wrap the builtin in a RootNode. Truffle requires all AST to start with a RootNode. */
        SLRootNode rootNode = new SLRootNode(this, new FrameDescriptor(), builtinBodyNode, srcSection, name);

        if (registerRootNodes) {
            /* Register the builtin function in our function registry. */
            getFunctionRegistry().register(name, rootNode);
        } else {
            // make sure the function is known
            getFunctionRegistry().lookup(name);
        }
    }

    public static NodeInfo lookupNodeInfo(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        NodeInfo info = clazz.getAnnotation(NodeInfo.class);
        if (info != null) {
            return info;
        } else {
            return lookupNodeInfo(clazz.getSuperclass());
        }
    }

    /**
     * Evaluate a source, causing any definitions to be registered (but not executed).
     *
     * @param source The {@link Source} to parse.
     */
    public void evalSource(Source source) {
        Parser.parseSL(this, source);
    }

    /**
     * Allocate an empty object.
     */
    public DynamicObject createObject() {
        return emptyShape.newInstance();
    }

    public static boolean isSLObject(TruffleObject value) {
        return value instanceof DynamicObject && isSLObject((DynamicObject) value);
    }

    public static boolean isSLObject(DynamicObject value) {
        return value.getShape().getObjectType() instanceof SLObjectType;
    }

    public static DynamicObject castSLObject(Object value) {
        return LAYOUT.getType().cast(value);
    }

    public static Object fromForeignValue(Object a) {
        if (a instanceof Long || a instanceof BigInteger || a instanceof String) {
            return a;
        } else if (a instanceof Number) {
            return ((Number) a).longValue();
        } else if (a instanceof TruffleObject) {
            return a;
        } else if (a instanceof SLContext) {
            return a;
        }
        throw new IllegalStateException(a + " is not a Truffle value");
    }

    public CallTarget parse(Source source) throws IOException {
        return env.parse(source);
    }

    /**
     * Goes through the other registered languages to find an exported global symbol of the
     * specified name. The expected return type is either <code>TruffleObject</code>, or one of
     * wrappers of Java primitive types ({@link Integer}, {@link Double}).
     *
     * @param name the name of the symbol to search for
     * @return object representing the symbol or <code>null</code>
     */
    @TruffleBoundary
    public Object importSymbol(String name) {
        Object object = env.importSymbol(name);
        Object slValue = fromForeignValue(object);
        return slValue;
    }
}
