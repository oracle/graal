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
package com.oracle.svm.hosted.webimage.codegen.oop;

import static com.oracle.svm.hosted.webimage.codegen.RuntimeConstants.RUNTIME_SYMBOL;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSObject;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.meta.HostedClass;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.JSCodeBuffer;
import com.oracle.svm.hosted.webimage.Labeler;
import com.oracle.svm.hosted.webimage.codegen.JSCodeGenTool;
import com.oracle.svm.hosted.webimage.codegen.WebImageTypeControl;
import com.oracle.svm.hosted.webimage.js.JSKeyword;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.hosted.webimage.snippets.JSSnippet;
import com.oracle.svm.hosted.webimage.snippets.JSSnippets;
import com.oracle.svm.hosted.webimage.util.metrics.MethodMetricsCollector;
import com.oracle.svm.webimage.hightiercodegen.CodeBuffer;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

/**
 * Generates code for a Java class that is a subclass of {@link org.graalvm.webimage.api.JSObject}.
 *
 * This class overrides parts of the code generation logic that is used for regular Java classes.
 * Concretely, the preamble emits the JavaScript mirror class, and the Java mirror's JavaScript
 * constructor and Java constructor are both modified.
 *
 * Some terms:
 *
 * <ul>
 * <li>Java mirror -- the JavaScript object that represents the user Java object in the Java
 * code.</li>
 * <li>JavaScript mirror -- the corresponding JavaScript object that represents the user Java object
 * after it is passed to JavaScript code.</li>
 * <li>JavaScript mirror's constructor -- the JavaScript "constructor" method of the JavaScript
 * mirror, which users can invoke.</li>
 * <li>Java mirror's JavaScript constructor -- the "constructor" method of the generated JavaScript
 * class.</li>
 * <li>Java mirror's Java constructor -- the Java constructor method, property of the generated
 * JavaScript class.</li>
 * <li>Handshake -- setting the {@code javaNative} property on the JS mirror and associating the
 * Java mirror with the JS mirror (see {@code conversion.setJavaScriptNative}).</li>
 * </ul>
 *
 * Objectives of this class:
 *
 * <ul>
 * <li>JavaScript mirror's constructor must correctly instantiate an initialized object, and
 * handshake the mirrors.</li>
 * <li>Java mirror's Java constructor must correctly instantiate an initialized object, and
 * handshake the mirrors.</li>
 * <li>In case of inheritance, Java constructors must be called in the order that conforms with the
 * Java source code, in which the {@link JSObject} subclass was declared.</li>
 * <li>In case of an import via {@link org.graalvm.webimage.api.JS.Import}, the JavaScript
 * constructor of the imported JavaScript must be invoked by the constructor of the Java facade
 * class (i.e. of the class that's annotated with {@link org.graalvm.webimage.api.JS.Import}).</li>
 * </ul>
 *
 * We have some constraints. Java constructor semantics do not allow using {@code this} before
 * calling {@code super}, nor any statement before {@code super}. JVM constructor semantics are
 * somewhat relaxed compared to this -- {@code this} can be used to access private fields of the
 * current class. JavaScript constructor semantics allow any statement before {@code super}, but
 * {@code this} can only be referenced after {@code super} returns. Note that this already
 * constrains us more generally, as some classfiles (generated by e.g. compilers other than javac)
 * are not handled properly (but that's a more general problem, orthogonal to interop).
 *
 * These constraints imply that we must invoke the JavaScript mirror's constructor call-chain
 * separately from the Java mirror's Java constructor call-chain. This is easily achievable, because
 * the JavaScript mirror's constructor bodies have no logic. The more difficult part are the
 * imported JavaScript classes, because there the Java mirror's Java constructor must call the
 * constructor of the imported JavaScript class.
 *
 * The full solution is to not extend the imported classes via "extends", but to instead patch the
 * prototype chain after the JavaScript mirror is created, as outlined in more detail below.
 *
 * <h2>JavaScript mirror's constructor</h2>
 *
 * The first step is to find out if the caller is (1) the JavaScript user code, or the caller is (2)
 * the Java constructor, or (3) the caller is a super from a subclass.
 *
 * In the first case, we will need to create an empty Java mirror instance first (by calling the
 * Java mirror's JavaScript constructor), and call super to satisfy the JavaScript mirror's
 * constructor call chain. The JavaScript user code then provides arguments that then must be
 * forwarded to the Java mirror's Java constructor. The JavaScript mirror instance is passed to the
 * Java mirror's Java constructor using {@code conversion.setJavaScriptNative}. The Java mirror's
 * Java constructor then performs the handshake. The JavaScript mirror's constructor then returns to
 * JavaScript code.
 *
 * In the second and the third case, the Java mirror's Java constructor will provide a special
 * {@code skipJavaCtor} value to denote that the JavaScript mirror does not need to create the Java
 * mirror. The super call is performed, forwarding the {@code skipJavaCtor}. The JavaScript mirror's
 * constructor then returns. The Java mirror's Java constructor then performs the handshake.
 *
 * For subclasses of imported classes, we do the following trick. If the class is a subclass of an
 * imported JavaScript class, then the JavaScript mirror instance (which corresponds to an object of
 * the imported class) is obtained by instantiating the imported class, and this JavaScript mirror
 * instance is used in the handshake. The prototype of the "imported" JavaScript mirror instance is
 * set to the prototype of the current class -- this is doable because the Java mirror's constructor
 * has an instance of the JavaScript mirror created by the JavaScript mirror's constructor. If we
 * are dealing with case (1) from above, then the JavaScript mirror's constructor returns the
 * imported mirror (a "return" in a JavaScript constructor can override the instance that "new"
 * would normally allocate).
 *
 * <h2>Java mirror's Java constructor</h2>
 *
 * The Java mirror's Java constructor may have been called by (1) the Java user code, or (2) by the
 * JavaScript mirror's constructor, or (3) by the {@code super} call in a subclass constructor.
 *
 * In the first case, the Java mirror is not yet associated with the JavaScript mirror. The
 * JavaScript mirror instance is therefore created (with the {@code skipJavaCtor} argument) and
 * associated with the Java mirror (see {@code conversion.setJavaScriptNative}). The super
 * constructor is then called. The {@link JSObject} constructor then completes the handshake: it
 * extracts the JavaScript mirror instance, and stores the {@code javaNative} property.
 *
 * In the second and the third case, the JavaScript mirror instance exists and it is associated with
 * the Java mirror.
 *
 * For subclasses of imported classes, everything is the same, with the exception that the Java
 * mirror's Java constructor will additionally (after calling the {@link JSObject} constructor via
 * {@code super}) overwrite the association to the JS mirror with the instance of the imported
 * JavaScript class.
 *
 * <h2>Patching the prototype chain</h2>
 *
 * In the generated code of the JavaScript mirrors, direct-subclass JavaScript mirror of an imported
 * JavaScript class does not extend the imported JavaScript class. Instead, the prototype of the
 * JavaScript mirror class is modified so that its prototype is the prototype of the imported
 * JavaScript class. As explained earlier, prototype of every allocated object is also set to point
 * to the prototype of the JavaScript mirror class that is the subclass of the imported JavaScript
 * class (this happens in the JavaScript mirror constructor).
 *
 * That's all folks.
 */
public class ClassWithMirrorLowerer extends ClassLowerer {
    private static final String UNSPECIFIED_IMPORTED_NAME_VALUE = "";
    private final boolean isImportedClass;
    private final boolean isSourceIncluded;
    private final boolean isDirectSubclassOfImport;
    private final boolean isSubclassOfImport;
    private JSCodeGenTool.ExternClassDescriptor externClassDescriptor;

    public ClassWithMirrorLowerer(OptionValues options, DebugContext debug, JSCodeGenTool jsLTools, Map<HostedMethod, StructuredGraph> methodGraphs, Labeler labeler,
                    MethodMetricsCollector methodMetricsCollector, Consumer<Integer> compiledMethodBytesCounter, HostedType type) {
        super(options, debug, jsLTools, methodGraphs, labeler, methodMetricsCollector, compiledMethodBytesCounter, type);
        this.isImportedClass = type.isAnnotationPresent(JS.Import.class);
        this.isSourceIncluded = type.isAnnotationPresent(JS.Code.Include.class) || type.isAnnotationPresent(JS.Code.class);
        this.isDirectSubclassOfImport = type.getSuperclass().isAnnotationPresent(JS.Import.class);
        this.isSubclassOfImport = isSubclassOfImport(type);
        this.externClassDescriptor = null;
    }

    /**
     * Public and protected fields in {@link JSObject} subclasses are represented in the JavaScript
     * mirror.
     * <p>
     * Accesses to those fields must be intercepted. The fields also do not appear in the Java
     * object.
     */
    public static boolean isFieldRepresentedInJavaScript(ResolvedJavaField field) {
        return !field.isStatic() && isJSObjectSubtype(OriginalClassProvider.getJavaClass(field.getDeclaringClass()));
    }

    /**
     * An imported Javascript class needs extern file for Closure compiler if the source code is not
     * included.
     */
    private boolean needExternDeclaration() {
        return isImportedClass && !isSourceIncluded;
    }

    private static boolean isSubclassOfImport(HostedType type) {
        return type != null && (type.isAnnotationPresent(JS.Import.class) || isSubclassOfImport(type.getSuperclass()));
    }

    public static boolean isJSObjectSubtype(Class<?> cls) {
        return JSObject.class.isAssignableFrom(cls);
    }

    public static List<HostedField> getOwnFieldOnJSSide(HostedType type) {
        List<HostedField> fields = new ArrayList<>();

        for (HostedField instanceField : type.getInstanceFields(false)) {
            if (isFieldRepresentedInJavaScript(instanceField)) {
                fields.add(instanceField);
            }
        }

        return fields;
    }

    @Override
    public void lower(WebImageTypeControl typeControl) {
        if (needExternDeclaration()) {
            externClassDescriptor = codeGenTool.addExternJSClass(importedName(type));
        }
        super.lower(typeControl);
    }

    @Override
    protected void lowerPreamble(JSCodeGenTool tool) {
        JSCodeBuffer buffer = (JSCodeBuffer) tool.getCodeBuffer();
        buffer.emitNewLine();
        buffer.emitConstDeclPrefix(internalMirrorClassName(codeGenTool, type));
        HostedClass superclass = type.getSuperclass();
        if (isImportedClass) {
            // The mirror class is the imported JavaScript class.
            String importedName = importedName(type);
            buffer.emitText(importedName);
            buffer.emitKeyword(JSKeyword.Semicolon);
            buffer.emitNewLine();

            if (needExternDeclaration()) {
                // We need to mark the fields in the externs file.
                for (HostedField field : getOwnFieldOnJSSide(type)) {
                    externClassDescriptor.addProperty(field.getName());
                }
            }
        } else if (type.getJavaClass().equals(JSObject.class)) {
            // JSObject does not have fields, nor superclasses.
            suppressClassWarnings(buffer);
            buffer.emitText("class ");
            buffer.emitScopeBegin();
            buffer.emitText("constructor(...args) {}");
            buffer.emitNewLine();
            buffer.emitScopeEnd();
        } else {
            // JSObject subclasses must declare their fields.
            suppressClassWarnings(buffer);
            if (isDirectSubclassOfImport) {
                buffer.emitText("class ");
            } else {
                buffer.emitText("class extends " + internalMirrorClassName(codeGenTool, superclass));
                buffer.emitText(" ");
            }
            buffer.emitScopeBegin();
            genJavaScriptMirrorConstructor(tool, buffer);
            genBridgeMethods(buffer);
            buffer.emitScopeEnd();
        }
        buffer.emitNewLine();
        buffer.emitKeyword(JSKeyword.Semicolon);
        buffer.emitNewLine();
        buffer.emitNewLine();

        if (type.getAnnotation(JS.Export.class) != null) {
            genJavaScriptExportMirrorClassDefinition();
        }
    }

    private static void suppressClassWarnings(CodeBuffer buffer) {
        if (WebImageOptions.ClosureCompiler.getValue()) {
            buffer.emitText("/** @suppress {checkTypes|undefinedVars} */ ");
        }
    }

    private void genJavaScriptExportMirrorClassDefinition() {
        Class<?> javaClass = type.getJavaClass();
        String className = javaClass.getName();
        String packageName = javaClass.getPackage().getName();
        if (packageName.length() > 0) {
            className = className.substring(packageName.length() + 1);
        }
        String hub = null;
        if (!((ClassInitializationSupport) ImageSingletons.lookup(RuntimeClassInitializationSupport.class)).maybeInitializeAtBuildTime(type)) {
            hub = codeGenTool.getJSProviders().typeControl().requestHubName(type);
        }
        JSSnippet snippet = JSSnippets.instantiateExportMirrorClassDefinition(packageName, className, hub, internalMirrorClassName(codeGenTool, type));
        snippet.lower(codeGenTool);
    }

    private static String internalMirrorClassName(JSCodeGenTool codeGenTool, HostedType t) {
        return "$$" + codeGenTool.getJSProviders().typeControl().requestTypeName(t);
    }

    private void genJavaScriptMirrorConstructor(JSCodeGenTool tool, JSCodeBuffer buffer) {
        buffer.emitText("constructor(...args) ");
        buffer.emitScopeBegin();

        if (isDirectSubclassOfImport) {
            // Direct subclasses of imported JavaScript classes do not call the super constructor,
            // because they do not extend the imported class using the "extends" keyword.
        } else {
            buffer.emitText("super(SYM.skipJavaCtor);");
            buffer.emitNewLine();
        }

        // Initialize properties.
        for (HostedField field : getOwnFieldOnJSSide(type)) {
            tool.genResolvedVarDeclThisPrefix(field.getName());
            genDefaultValue(tool, buffer, field);
            tool.genResolvedVarDeclPostfix(null);
        }

        // Run the initialization protocol as described in the top-level comment.
        buffer.emitIfHeaderLeft();
        buffer.emitText("args[0] !== SYM.skipJavaCtor");
        buffer.emitIfHeaderRight();

        // Case 1:
        // Call Java mirror's JavaScript constructor.
        buffer.emitConstDeclPrefix("javaMirror");
        buffer.emitNew();
        tool.genTypeName(type);
        buffer.emitText("();");
        buffer.emitNewLine();
        // Set the mirror fields.
        buffer.emitText("conversion.setJavaScriptNative(javaMirror, this);");
        buffer.emitNewLine();
        buffer.emitText("this[SYM.javaNative] = javaMirror;");
        buffer.emitNewLine();
        // Call Java mirror's Java constructor.
        // We use the ProxyHandler's overload resolution.
        buffer.emitConstDeclPrefix("handler");
        buffer.emitText("conversion.getOrCreateProxyHandler(");
        tool.genTypeName(type);
        buffer.emitText(");");
        buffer.emitNewLine();
        buffer.emitText("handler._getJavaConstructorMethod()(this, ...args);");
        buffer.emitNewLine();

        // In imported classes, the "this" created by "new" is replaced with the imported instance,
        // as described in the top-level comment.
        if (isSubclassOfImport) {
            buffer.emitConstDeclPrefix("importedThis");
            buffer.emitText("conversion.extractJavaScriptNative(javaMirror);");
            buffer.emitNewLine();
            buffer.emitText("Object.setPrototypeOf(importedThis, " + internalMirrorClassName(codeGenTool, type) + ");");
            buffer.emitNewLine();
            buffer.emitText("return importedThis;");
            buffer.emitNewLine();
        }

        // Case 2 and 3: no need to create the Java mirror.
        buffer.emitScopeEnd();
        buffer.emitNewLine();

        // End constructor.
        buffer.emitScopeEnd();
        buffer.emitNewLine();
    }

    private void genBridgeMethods(CodeBuffer buffer) {
        HashSet<String> staticOverloads = new HashSet<>();
        HashSet<String> instanceOverloads = new HashSet<>();
        for (HostedMethod method : type.getDeclaredMethods(false)) {
            if (!method.isPublic() || method.isConstructor()) {
                continue;
            }
            if (method.isStatic()) {
                staticOverloads.add(method.getName());
            } else {
                instanceOverloads.add(method.getName());
            }
        }
        for (String name : instanceOverloads) {
            genBridgeMethod(buffer, name, false);
        }
        for (String name : staticOverloads) {
            genBridgeMethod(buffer, name, true);
        }
    }

    private void genBridgeMethod(CodeBuffer buffer, String name, boolean isStatic) {
        if (isStatic) {
            buffer.emitText("static ");
        }
        buffer.emitText(name);
        buffer.emitKeyword(JSKeyword.LPAR);
        buffer.emitText("...args");
        buffer.emitKeyword(JSKeyword.RPAR);
        buffer.emitWhiteSpace();
        buffer.emitScopeBegin();
        codeGenTool.genResolvedConstDeclPrefix("handler");
        buffer.emitText("conversion.getOrCreateProxyHandler(");
        codeGenTool.genTypeName(type);
        buffer.emitText(");");
        buffer.emitNewLine();
        buffer.emitText("return handler.");
        buffer.emitText(isStatic ? "_getStaticMethods()[" : "_getMethods()[");
        buffer.emitStringLiteral(name);
        buffer.emitText("].apply(");
        buffer.emitText(isStatic ? "null, args);" : "this, args);");
        buffer.emitNewLine();
        buffer.emitScopeEnd();
    }

    private static void genDefaultValue(JSCodeGenTool tool, CodeBuffer buffer, HostedField field) {
        switch (field.getJavaKind()) {
            case Boolean:
                buffer.emitText("false");
                break;
            case Byte:
            case Short:
            case Char:
            case Int:
            case Float:
            case Long:
            case Double:
                buffer.emitText("0");
                break;
            case Object:
                tool.genNull();
                break;
            default:
                throw GraalError.shouldNotReachHere(field.getJavaKind().toString()); // ExcludeFromJacocoGeneratedReport
        }
    }

    private static String importedName(HostedType type) {
        String importedName = type.getAnnotation(JS.Import.class).value();
        return importedName.equals(UNSPECIFIED_IMPORTED_NAME_VALUE) ? computeImportedName(type) : importedName;
    }

    private static String computeImportedName(HostedType type) {
        // Checkstyle: allow Class.getSimpleName
        String simpleName = type.getJavaClass().getSimpleName();
        // Checkstyle: disallow Class.getSimpleName
        if (type.getEnclosingType() == null) {
            return simpleName;
        } else {
            return computeImportedName(type.getEnclosingType()) + "." + simpleName;
        }
    }

    @Override
    protected void genJavaMirrorJavaConstructorPreamble(StructuredGraph g) {
        ResolvedJavaMethod method = g.method();
        JSCodeBuffer codeBuffer = (JSCodeBuffer) codeGenTool.getCodeBuffer();
        String thisName = JSCodeBuffer.getParamName(0);

        codeBuffer.emitConstDeclPrefix("initialJavaScriptMirror");
        codeBuffer.emitText("conversion.extractJavaScriptNative(" + thisName + ");");
        codeBuffer.emitNewLine();

        if (isImportedClass) {
            // Replace the initial JavaScript mirror with the instance of the imported JavaScript
            // class. If the runtime type is the imported class, then the initial instance is
            // undefined. Otherwise, the initial mirror instance is an instance of the subclass
            // mirror class.

            // First do the super call.
            codeBuffer.emitConstDeclPrefix("importedMirror");
            codeBuffer.emitText("new (" + internalMirrorClassName(codeGenTool, type) + ")(");
            Signature signature = method.getSignature();
            ResolvedJavaMethod.Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                if (i > 0) {
                    codeBuffer.emitKeyword(JSKeyword.COMMA);
                }
                String boxedParam = boxedParameter((HostedType) signature.getParameterType(i, null).resolve(null), i);
                codeBuffer.emitText("conversion.javaToJavaScript(" + boxedParam + ")");
            }
            codeBuffer.emitText(");");
            codeBuffer.emitNewLine();

            codeBuffer.emitIfHeaderLeft();
            codeBuffer.emitText("initialJavaScriptMirror === null");
            codeBuffer.emitIfHeaderRight();

            // Case 1:
            // Create the JavaScript mirror, and store it.
            // This can only happen if the runtime type is the imported class itself.

            // Case 3 (note that case 2 cannot happen for imported classes -- the mirror constructor
            // does not call the Java constructor).
            codeBuffer.emitElse();

            codeBuffer.emitText("conversion.copyOwnFields(initialJavaScriptMirror, importedMirror);");
            codeBuffer.emitNewLine();
            codeBuffer.emitText("Object.setPrototypeOf(importedMirror, Object.getPrototypeOf(initialJavaScriptMirror));");
            codeBuffer.emitNewLine();

            codeBuffer.emitScopeEnd();
            codeBuffer.emitNewLine();

            // In both cases, just hand-shake at the end.
            codeBuffer.emitText("conversion.setJavaScriptNative(" + thisName + ", importedMirror);");
            codeBuffer.emitNewLine();
            codeBuffer.emitText("conversion.extractJavaScriptNative(" + thisName + ")[SYM.javaNative] = " + thisName + ";");
            codeBuffer.emitNewLine();
        } else {
            codeBuffer.emitIfHeaderLeft();
            codeBuffer.emitText("initialJavaScriptMirror === null");
            codeBuffer.emitIfHeaderRight();

            // Case 1:
            // Create the JavaScript mirror, and store it.
            codeBuffer.emitText("conversion.setJavaScriptNative(" + thisName + ", new (" + internalMirrorClassName(codeGenTool, type) + ")(SYM.skipJavaCtor));");
            codeBuffer.emitNewLine();
            codeBuffer.emitText("conversion.extractJavaScriptNative(" + thisName + ")[SYM.javaNative] = " + thisName + ";");
            codeBuffer.emitNewLine();

            // Cases 2 and 3: nothing special to do.
            codeBuffer.emitScopeEnd();
            codeBuffer.emitNewLine();
        }
    }

    private String boxedParameter(HostedType t, int i) {
        String p = "p" + (i + 1);
        if (t.isPrimitive()) {
            String hub = codeGenTool.getJSProviders().typeControl().requestHubName(t);
            return hub + "[" + RUNTIME_SYMBOL + ".box](" + p + ")";
        } else {
            return p;
        }
    }

    @Override
    protected void lowerClassEnd() {
        super.lowerClassEnd();

        JSCodeBuffer buffer = (JSCodeBuffer) codeGenTool.getCodeBuffer();

        // Store the mapping from the imported JavaScript class constructor to the Java facade class
        // under which the JavaScript class was imported.
        if (isImportedClass) {
            buffer.emitScopeBegin();
            buffer.emitLetDeclPrefix("facades");
            buffer.emitText("runtime.ensureFacadeSetFor(" + internalMirrorClassName(codeGenTool, type) + ");");
            buffer.emitNewLine();
            buffer.emitText("facades.add(");
            buffer.emitText(codeGenTool.getJSProviders().typeControl().requestTypeName(type));
            buffer.emitText(");");
            buffer.emitNewLine();
            buffer.emitScopeEnd();
            buffer.emitNewLine();
            buffer.emitNewLine();
        }
    }
}
