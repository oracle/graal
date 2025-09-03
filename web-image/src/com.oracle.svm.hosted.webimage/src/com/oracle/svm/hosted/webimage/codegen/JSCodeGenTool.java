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
package com.oracle.svm.hosted.webimage.codegen;

import static com.oracle.svm.webimage.functionintrinsics.JSCallNode.SHOULD_NOT_REACH_HERE;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.webimage.api.JSValue;

import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.JSCodeBuffer;
import com.oracle.svm.hosted.webimage.LowerableFile;
import com.oracle.svm.hosted.webimage.WebImageHostedConfiguration;
import com.oracle.svm.hosted.webimage.js.JSBody;
import com.oracle.svm.hosted.webimage.js.JSKeyword;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.webimage.annotation.WebImage;
import com.oracle.svm.webimage.hightiercodegen.CodeGenTool;
import com.oracle.svm.webimage.hightiercodegen.Emitter;
import com.oracle.svm.webimage.hightiercodegen.IEmitter;
import com.oracle.svm.webimage.hightiercodegen.Keyword;
import com.oracle.svm.webimage.hightiercodegen.variables.ResolvedVar;
import com.oracle.svm.webimage.hightiercodegen.variables.VariableAllocation;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public class JSCodeGenTool extends CodeGenTool {

    public static final class ExternClassDescriptor {
        private final String name;
        private final EconomicSet<String> properties;

        private ExternClassDescriptor(String name) {
            this.name = name;
            this.properties = EconomicSet.create();
        }

        public void addProperty(String p) {
            properties.add(p);
        }

        public Iterable<String> getProperties() {
            return properties;
        }

        public String getName() {
            return name;
        }
    }

    private final WebImageJSNodeLowerer nodeLowerer;
    private final WebImageProviders providers;

    @Override
    public WebImageProviders getProviders() {
        return providers;
    }

    /**
     * Only call this method in the JS backend, it will result in a {@link ClassCastException}
     * otherwise.
     */
    public WebImageJSProviders getJSProviders() {
        return (WebImageJSProviders) providers;
    }

    /**
     * Stores descriptor of extern JS classes to ensure that the closure compiler is aware of them.
     */
    private final EconomicMap<String, ExternClassDescriptor> externJSClasses = EconomicMap.create();

    @SuppressWarnings("this-escape")
    public JSCodeGenTool(WebImageProviders providers, JSCodeBuffer codeBuffer, WebImageHostedConfiguration configuration, VariableAllocation variableAllocation) {
        super(codeBuffer, variableAllocation);
        this.providers = providers;
        this.nodeLowerer = configuration.createNodeLowerer(this);
    }

    public MapCursor<String, ExternClassDescriptor> getExternJSClasses() {
        return externJSClasses.getEntries();
    }

    /**
     * Adds the given class name to the set of extern JS classes.
     */
    public ExternClassDescriptor addExternJSClass(String name) {
        ExternClassDescriptor descriptor = externJSClasses.get(name);
        if (descriptor == null) {
            descriptor = new ExternClassDescriptor(name);
            externJSClasses.put(name, descriptor);
        }
        return descriptor;
    }

    @Override
    public void genClassHeader(ResolvedJavaType type) {
        codeBuffer.emitNewLine();
        codeBuffer.emitNewLine();
        genComment(type.toJavaName(true), WebImageOptions.CommentVerbosity.MINIMAL);
        if (WebImageOptions.ClosureCompiler.getValue()) {
            MetaAccessProvider meta = getProviders().getMetaAccess();
            if (meta.lookupJavaType(JSValue.class).isAssignableFrom(type) || meta.lookupJavaType(Class.class).equals(type)) {
                // We assign the javaNative property to JSValue instances and to certain hub
                // objects, so we need bracket access.
                codeBuffer.emitText("/** @unrestricted */");
                codeBuffer.emitNewLine();
            }
        }
        codeBuffer.emitKeyword(JSKeyword.CLASS);
        codeBuffer.emitWhiteSpace();
        genTypeName(type);
        codeBuffer.emitWhiteSpace();

        if (type.getSuperclass() != null) {
            codeBuffer.emitKeyword(JSKeyword.EXTENDS);
            codeBuffer.emitWhiteSpace();
            genTypeName(type.getSuperclass());
            codeBuffer.emitWhiteSpace();
        }

        codeBuffer.emitScopeBegin();
    }

    @Override
    public void genMethodHeader(StructuredGraph graph, ResolvedJavaMethod m, List<ParameterNode> parameters) {
        String functionName = getJSProviders().typeControl().requestMethodName(m);

        Signature s = m.getSignature();

        if (WebImageOptions.ClosureCompiler.getValue()) {
            if (!AnnotationAccess.isAnnotationPresent(m, WebImage.OmitClosureReturnType.class)) {
                codeBuffer.emitNewLine();
                codeBuffer.emitText("/** @return {" + getClosureCompilerAnnotation((ResolvedJavaType) s.getReturnType(null), true) + "} */");
                if (graph.getNodes().filter(JSBody.class::isInstance).isNotEmpty()) {
                    codeBuffer.emitNewLine();
                    codeBuffer.emitText("/** @suppress {checkTypes|externsValidation|undefinedVars} */ ");
                }
            }
        }

        codeBuffer.emitMethodHeader(m, functionName, s, parameters);
    }

    @Override
    protected void genArrayAccessPostfix() {
        codeBuffer.emitWhiteSpace();
        codeBuffer.emitKeyword(JSKeyword.RBRACK);
    }

    @Override
    protected void genArrayAccessPrefix() {
        codeBuffer.emitWhiteSpace();
        codeBuffer.emitKeyword(JSKeyword.LBRACK);
        codeBuffer.emitWhiteSpace();
    }

    @Override
    public void genArrayStore(IEmitter index, ValueNode array, ValueNode value) {
        if (value.getStackKind() == JavaKind.Long) {
            Runtime.BigInt64ArrayStore.emitCall(this, Emitter.of(array), index, Emitter.of(value));
        } else {
            super.genArrayStore(index, Emitter.of(array), Emitter.of(value));
        }
    }

    @Override
    public void genBinaryOperation(Keyword operator, ValueNode leftOperand, ValueNode righOperand) {
        codeBuffer.emitKeyword(JSKeyword.LPAR);

        // left operand
        codeBuffer.emitKeyword(JSKeyword.LPAR);
        lowerValue(leftOperand);
        codeBuffer.emitKeyword(JSKeyword.RPAR);
        // operator
        codeBuffer.emitKeyword(operator);
        // right operand
        codeBuffer.emitKeyword(JSKeyword.LPAR);
        lowerValue(righOperand);
        codeBuffer.emitKeyword(JSKeyword.RPAR);

        codeBuffer.emitKeyword(JSKeyword.RPAR);
    }

    @Override
    public void genUnaryOperation(Keyword operator, ValueNode operand) {
        codeBuffer.emitKeyword(operator);
        lowerValue(operand);
    }

    @Override
    public WebImageJSNodeLowerer nodeLowerer() {
        return nodeLowerer;
    }

    @Override
    public void genResolvedVarDeclPostfix(String comment) {
        if (WebImageOptions.genJSComments()) {
            codeBuffer.emitResolvedBuiltInVarDeclPostfix(comment);
        } else {
            codeBuffer.emitResolvedBuiltInVarDeclPostfix(null);
        }
    }

    @Override
    public void genCommaList(IEmitter... inputs) {
        for (int i = 0; i < inputs.length; i++) {
            if (i != 0) {
                codeBuffer.emitKeyword(JSKeyword.COMMA);
            }
            lower(inputs[i]);
        }
    }

    @Override
    public void genComment(String comment) {
        genComment(comment, null);
    }

    public void genComment(String comment, WebImageOptions.CommentVerbosity verbosity) {
        if (!WebImageOptions.genJSComments(verbosity)) {
            return;
        }
        codeBuffer.emitComment(comment);
        codeBuffer.emitNewLine();
    }

    @Override
    public void genInlineComment(String comment) {
        if (!WebImageOptions.genJSComments()) {
            return;
        }
        codeBuffer.emitInlineComment(comment);
    }

    public void genNumericalPaladinIntLeft() {
        codeBuffer.emitText("( ( ");
    }

    public void genNumericalPaladinIntRight() {
        codeBuffer.emitText(" ) | 0)");
    }

    @Override
    public void genNewInstance(ResolvedJavaType t) {
        genObjectCreate(Emitter.of(t));
    }

    @Override
    public void genNewArray(ResolvedJavaType arrayType, IEmitter length) {
        Array.lowerNewArray((HostedType) arrayType.getComponentType(), length, this);
    }

    /**
     * Generates code to create a JS object with the given prototype and constructor arguments.
     */
    public void genObjectCreate(IEmitter prototype, IEmitter... initParams) {
        codeBuffer.emitNew();
        lower(prototype);
        codeBuffer.emitKeyword(JSKeyword.LPAR);
        genCommaList(initParams);
        codeBuffer.emitKeyword(JSKeyword.RPAR);
    }

    @Override
    public void genEmptyDeclaration(ValueNode node) {
        ResolvedVar resolvedVar = getAllocatedVariable(node);
        assert resolvedVar != null : nodeLowerer().nodeDebugInfo(node);
        codeBuffer.emitKeyword(JSKeyword.VAR);
        codeBuffer.emitWhiteSpace();
        codeBuffer.emitText(resolvedVar.getName());
        genResolvedVarDeclPostfix(node.toString());
        resolvedVar.setDefinitionLowered();
    }

    @Override
    public void genThrow(ValueNode exception) {
        codeBuffer.emitKeyword(JSKeyword.THROW);
        codeBuffer.emitWhiteSpace();
        lowerValue(exception);
    }

    @Override
    public void genAssignment() {
        codeBuffer.emitKeyword(JSKeyword.Assignment);
    }

    public void genResolvedConstDeclPrefix(String name) {
        ((JSCodeBuffer) codeBuffer).emitConstDeclPrefix(name);
    }

    public void genResolvedVarDeclThisPrefix(String name) {
        genPropertyAccess(Emitter.of("this"), Emitter.of(name));
        genAssignment();
    }

    /**
     * Declare a variable for the {@code this} object using the bracket notation. For example, if
     * {@code name} has the value 'foo', we generate:
     *
     * <pre>
     *     this['foo'] =
     * </pre>
     *
     */
    public void genResolvedVarDeclThisBracketPrefix(String name) {
        genPropertyBracketAccess(Emitter.of("this"), name);
        genAssignment();
    }

    @Override
    public void genNull() {
        codeBuffer.emitText(RuntimeConstants.NULL);
    }

    /**
     * Lowers the given map to a JavaScript object. The key will be surrounded by quotes, but
     * lowered as-is without any escaping. The value is also lowered as-is, if you want to emit a
     * string literal, the value also needs to emit the quotes.
     *
     * <pre>
     * {
     *     "key": value,
     * }
     * </pre>
     */
    public void genObject(Map<String, IEmitter> map) {
        codeBuffer.emitScopeBegin();
        for (Map.Entry<String, IEmitter> entry : map.entrySet()) {
            codeBuffer.emitStringLiteral(entry.getKey());
            codeBuffer.emitText(" : ");
            lower(entry.getValue());
            codeBuffer.emitText(",");
            codeBuffer.emitNewLine();
        }
        genScopeEnd();
    }

    public void lowerFile(LowerableFile f) {
        codeBuffer.emitNewLine();
        codeBuffer.emitNewLine();
        genComment(f.getName(), WebImageOptions.CommentVerbosity.MINIMAL);
        f.lower(this);
    }

    @Override
    public void genFieldName(ResolvedJavaField field) {
        codeBuffer.emitText(getJSProviders().typeControl().requestFieldName(field));
    }

    @Override
    public void genFieldName(Field field) {
        genFieldName(getProviders().getMetaAccess().lookupJavaField(field));
    }

    @Override
    public void genTypeName(ResolvedJavaType type) {
        codeBuffer.emitText(getJSProviders().typeControl().requestTypeName(type));
    }

    @Override
    public void genTypeName(Class<?> type) {
        genTypeName(getProviders().getMetaAccess().lookupJavaType(type));
    }

    @Override
    public void genMethodName(ResolvedJavaMethod method) {
        codeBuffer.emitText(getJSProviders().typeControl().requestMethodName(method));
    }

    @Override
    public void genMethodName(Method method) {
        genMethodName(getProviders().getMetaAccess().lookupJavaMethod(method));
    }

    @Override
    protected void genPropertyAccessInfix() {
        codeBuffer.emitKeyword(JSKeyword.DOT);
    }

    public void genPrototypePropertyAccess(IEmitter receiver, IEmitter prop) {
        assert receiver != null;
        lower(receiver);
        codeBuffer.emitText(".prototype.");
        lower(prop);
    }

    /**
     * Generate a property access using the bracket notation. Example - if {@code prop} has the
     * value {@code foo}, we generate:
     *
     * <pre>
     *     receiver['foo']
     * </pre>
     */
    public void genPropertyBracketAccess(IEmitter receiver, String prop) {
        lower(receiver);
        codeBuffer.emitKeyword(JSKeyword.LBRACK);
        codeBuffer.emitKeyword(JSKeyword.SingleQuote);
        codeBuffer.emitText(prop);
        codeBuffer.emitKeyword(JSKeyword.SingleQuote);
        codeBuffer.emitKeyword(JSKeyword.RBRACK);
    }

    public void genPropertyBracketAccessWithExpression(IEmitter receiver, IEmitter expression) {
        lower(receiver);
        codeBuffer.emitKeyword(JSKeyword.LBRACK);
        lower(expression);
        codeBuffer.emitKeyword(JSKeyword.RBRACK);
    }

    @Override
    public void genFunctionCall(IEmitter receiver, IEmitter fun, IEmitter... params) {
        genPropertyAccess(receiver, fun);

        genFunctionParameterInfix();
        genCommaList(params);
        genFunctionParameterPostfix();
    }

    @Override
    protected void genFunctionParameterInfix() {
        codeBuffer.emitKeyword(JSKeyword.LPAR);
    }

    @Override
    protected void genFunctionParameterPostfix() {
        codeBuffer.emitKeyword(JSKeyword.RPAR);
    }

    public void genPrototypeFunctionCall(IEmitter receiver, IEmitter fun, IEmitter... params) {
        genPrototypePropertyAccess(receiver, fun);

        genFunctionParameterInfix();
        genCommaList(params);
        genFunctionParameterPostfix();
    }

    @Override
    public void genStaticMethodReference(ResolvedJavaMethod m) {
        genPropertyAccess(Emitter.of(m.getDeclaringClass()), Emitter.of(m));
    }

    public void genPrototypeMethodReference(ResolvedJavaMethod m) {
        genPrototypePropertyAccess(Emitter.of(m.getDeclaringClass()), Emitter.of(m));
    }

    @Override
    public void genStaticField(ResolvedJavaField m) {
        genPropertyAccess(Emitter.of(m.getDeclaringClass()), Emitter.of(m));
    }

    @Override
    public void genStaticCall(ResolvedJavaMethod m, IEmitter... args) {
        assert m.isStatic() : m;
        assert NumUtil.assertArrayLength(args, m.getSignature().getParameterCount(!m.isStatic()));
        genFunctionCall(Emitter.of(m.getDeclaringClass()), Emitter.of(m), args);
    }

    public void genPrototypeCall(ResolvedJavaMethod m, IEmitter... args) {
        assert NumUtil.assertArrayLength(args, m.getSignature().getParameterCount(!m.isStatic()));
        genPrototypeFunctionCall(Emitter.of(m.getDeclaringClass()), Emitter.of(m), args);
    }

    @Override
    public void genIndirectCall(IEmitter address, IEmitter... args) {
        codeBuffer.emitText("runtime.funtab[Long64.lowBits(");
        address.lower(this);
        codeBuffer.emitText(")].call");
        codeBuffer.emitKeyword(JSKeyword.LPAR);
        IEmitter[] callArgs = new IEmitter[args.length + 1];
        callArgs[0] = Emitter.ofNull();
        System.arraycopy(args, 0, callArgs, 1, args.length);
        genCommaList(callArgs);
        codeBuffer.emitKeyword(JSKeyword.RPAR);
    }

    @Override
    public void genShouldNotReachHere(String msg) {
        /*
         * The ShouldNotReachHere call unconditionally throws an exception, but this helps both
         * readability and tells the closure compiler that the control flow ends here.
         */
        codeBuffer.emitKeyword(JSKeyword.THROW);
        codeBuffer.emitWhiteSpace();
        assert msg != null;
        SHOULD_NOT_REACH_HERE.emitCall(this, Emitter.of(getStringLiteral(msg)));
    }

    public void genInitJsResources(ResolvedJavaType t) {
        String initFun = getJSProviders().typeControl().requestTypeName(t);
        IEmitter target = Emitter.of("runtime.jsResourceInits");
        codeBuffer.emitText("if (");
        codeBuffer.emitStringLiteral(initFun);
        codeBuffer.emitText(" in ");
        lower(target);
        codeBuffer.emitText(") ");
        genFunctionCall(target, Emitter.of(initFun));
        codeBuffer.emitText(", delete ");
        genPropertyAccess(target, Emitter.of(initFun));
        codeBuffer.emitInsEnd();
    }

    public String vmClassName() {
        String imageName = WebImageOptions.VMClassName.getValue(HostedOptionValues.singleton());
        imageName = imageName.replaceAll("[^A-Za-z]", "_");
        return imageName;
    }

    public String getClosureCompilerAnnotation(ResolvedJavaType type, boolean allowsNull) {
        JavaKind kind = type.getJavaKind();

        /*
         * Currently emits very loose type names because more specific types easily conflict with
         * the closure compiler's type inference.
         */
        switch (kind) {
            case Boolean:
            case Byte:
            case Short:
            case Char:
            case Int:
            case Float:
            case Double:
                return "number";
            case Long:
            case Object:
                return (allowsNull ? "?" : "!") + "Object";
            case Void:
                return "void";
            default:
                throw JVMCIError.shouldNotReachHere(kind.toString());
        }
    }

}
