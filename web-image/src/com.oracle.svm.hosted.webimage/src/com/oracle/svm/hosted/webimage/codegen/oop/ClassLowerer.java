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

import static com.oracle.svm.hosted.webimage.metrickeys.UniverseMetricKeys.EMITTED_METHODS;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.graalvm.webimage.api.JSResource;

import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.Labeler;
import com.oracle.svm.hosted.webimage.codegen.JSCodeGenTool;
import com.oracle.svm.hosted.webimage.codegen.WebImageTypeControl;
import com.oracle.svm.hosted.webimage.codegen.irwalk.WebImageJSIRWalker;
import com.oracle.svm.hosted.webimage.codegen.long64.Long64Lowerer;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.ReconstructionData;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.ScheduleWithReconstructionResult;
import com.oracle.svm.hosted.webimage.codegen.type.ClassMetadataLowerer;
import com.oracle.svm.hosted.webimage.logging.LoggerContext;
import com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys;
import com.oracle.svm.hosted.webimage.metrickeys.MethodMetricKeys;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.hosted.webimage.util.AnnotationUtil;
import com.oracle.svm.hosted.webimage.util.metrics.CodeSizeCollector;
import com.oracle.svm.hosted.webimage.util.metrics.MethodMetricsCollector;
import com.oracle.svm.webimage.hightiercodegen.CodeBuffer;

import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Lowering of the Java class.
 */
public class ClassLowerer {

    public static final String HUB_PROP_NAME = "hub";

    private static final List<Function<ResolvedJavaType, String[]>> resourceProviders = new ArrayList<>();
    @SuppressWarnings("unused") private final OptionValues options;
    private final DebugContext debug;
    protected final JSCodeGenTool codeGenTool;
    private final CodeBuffer codeBuffer;
    private final Map<HostedMethod, StructuredGraph> methodGraphs;
    private final Labeler labeler;
    private final MethodMetricsCollector methodMetricsCollector;
    private final Consumer<Integer> compiledMethodBytesCounter;
    protected final HostedType type;

    public ClassLowerer(OptionValues options, DebugContext debug, JSCodeGenTool codeGenTool, Map<HostedMethod, StructuredGraph> methodGraphs, Labeler labeler,
                    MethodMetricsCollector methodMetricsCollector, Consumer<Integer> compiledMethodBytesCounter, HostedType type) {
        this.options = options;
        this.debug = debug;
        this.codeGenTool = codeGenTool;
        this.codeBuffer = codeGenTool.getCodeBuffer();
        this.methodGraphs = methodGraphs;
        this.labeler = labeler;
        this.methodMetricsCollector = methodMetricsCollector;
        this.compiledMethodBytesCounter = compiledMethodBytesCounter;
        this.type = type;
    }

    @SuppressWarnings("unused")
    protected void lowerPreamble(JSCodeGenTool tool) {
    }

    private void lowerClassHeader() {
        HostedType superClass = type.getSuperclass();

        CodeBuffer masm = codeGenTool.getCodeBuffer();

        codeGenTool.genClassHeader(type);

        codeBuffer.emitMethodHeader(null, "constructor", NEW_INSTANCE_SIG, new ArrayList<>());

        if (superClass != null) {
            /*
             * emit super call for property assignment
             */
            masm.emitNewLine();
            masm.emitText("super()");
            codeGenTool.genResolvedVarDeclPostfix(null);
        }

        /*
         * we only collect the instance fields declared by this type, inherited fields are resolved
         * via a super call
         */
        for (HostedField field : type.getInstanceFields(false)) {
            if (!ClassWithMirrorLowerer.isFieldRepresentedInJavaScript(field)) {
                genFieldInitialization(codeGenTool, masm, field);
            }
        }

        /*
         * NOTE: for every instance type that needs a hashcode as it might be exposed to
         * system.identityhashcode() substrate adds an additional field to a type at the end of the
         * instance so the instance original size is size+sizeof(hashcodefield) where the hashcode
         * field will be 0 always at the beginning and initialized on the 1. call, in Web Image we
         * simply generate an additional special field that serves as the hashcode field (property)
         * of each instance
         */
        if (type instanceof HostedInstanceClass) {
            codeGenTool.genComment("Generated Hash Code Field");
            codeGenTool.genResolvedVarDeclThisPrefix(ClassMetadataLowerer.INSTANCE_TYPE_HASHCODE_FIELD_NAME);
            masm.emitIntLiteral(0);
            codeGenTool.genResolvedVarDeclPostfix(null);
        }

        masm.emitFunctionEnd();
    }

    private static void genFieldInitialization(JSCodeGenTool jsLTools, CodeBuffer masm, HostedField field) {
        String fieldName = jsLTools.getJSProviders().typeControl().requestFieldName(field);

        HostedType fieldType = field.getType();
        JavaKind fieldKind = fieldType.getJavaKind();

        jsLTools.genComment(field + " type:" + fieldType);

        /*
         * Annotate fields for the closure compiler.
         *
         * The annotations are necessary for objects and optional for the other types, see
         * https://github.com/google/closure-compiler/issues/3865
         */
        if (WebImageOptions.ClosureCompiler.getValue()) {
            String typeAnnotation = jsLTools.getClosureCompilerAnnotation(fieldType, true);
            masm.emitText("/** @type {" + typeAnnotation + "} */");
            masm.emitWhiteSpace();
        }

        /*
         * vars are generated to the "this" context in the new instance call
         */
        jsLTools.genResolvedVarDeclThisPrefix(fieldName);

        switch (fieldKind) {
            case Boolean:
            case Byte:
            case Int:
            case Short:
            case Char:
            case Double:
            case Float:
                masm.emitText("0");
                break;
            case Long:
                Long64Lowerer.lowerFromConstant(JavaConstant.forLong(0), jsLTools);
                break;
            case Object:
                jsLTools.genNull();
                break;
            default:
                throw GraalError.shouldNotReachHere(fieldKind.toString()); // ExcludeFromJacocoGeneratedReport

        }
        jsLTools.genResolvedVarDeclPostfix(null);
    }

    protected void lowerClassEnd() {
        codeGenTool.genClassEnd();
    }

    /**
     * Signature for the new instance call.
     *
     * New bytecode semantics never has a parameter.
     */
    private static final Signature NEW_INSTANCE_SIG = new Signature() {

        @Override
        public JavaType getReturnType(ResolvedJavaType accessingClass) {
            return null;
        }

        @Override
        public JavaKind getReturnKind() {
            return JavaKind.Void;
        }

        @Override
        public JavaType getParameterType(int index, ResolvedJavaType accessingClass) {
            return null;
        }

        @Override
        public JavaKind getParameterKind(int index) {
            return JavaKind.Void;
        }

        @Override
        public int getParameterCount(boolean receiver) {
            return 0;
        }
    };

    @SuppressWarnings("try")
    public void lower(WebImageTypeControl typeControl) {
        lowerJSResources(type, codeGenTool);

        try (CodeSizeCollector collector = new CodeSizeCollector(ImageBreakdownMetricKeys.TYPE_DECLARATIONS_SIZE, codeBuffer::codeSize)) {
            lowerPreamble(codeGenTool);

            lowerClassHeader();

            // force array hubs to be inspected
            String hubnameObjectName = codeGenTool.getJSProviders().typeControl().requestHubName(type);
            if (!type.isArray()) {
                CodeBuffer masm = codeGenTool.getCodeBuffer();

                // Generate a getter for the generated hub
                masm.emitNewLine();
                masm.emitText("get " + ClassLowerer.HUB_PROP_NAME + "() ");
                masm.emitScopeBegin();
                masm.emitNewLine();
                codeGenTool.genReturn(hubnameObjectName);
                codeGenTool.genScopeEnd();
            }
        }

        for (HostedMethod m : type.getAllDeclaredMethods()) {
            StructuredGraph graph = methodGraphs.get(m);
            if (graph == null) {
                continue;
            }

            try (DebugContext.Scope s = debug.scope("Compiling", graph, m);
                            MethodMetricsCollector.Collector c = methodMetricsCollector.collect(m)) {
                LoggerContext.counter(MethodMetricKeys.NUM_SPLITS).add(graph.getNodes().filter(ControlSplitNode.class).count());
                typeControl.setDefaultReason(m);
                lowerMethod(m);
                typeControl.resetDefaultReason();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw JVMCIError.shouldNotReachHere(t);
            }

            // collect code information
            compiledMethodBytesCounter.accept(m.getCodeSize());
        }

        try (CodeSizeCollector collector = new CodeSizeCollector(ImageBreakdownMetricKeys.TYPE_DECLARATIONS_SIZE, codeBuffer::codeSize)) {
            lowerClassEnd();
            ClassMetadataLowerer.lowerClassMetadata(type, codeGenTool, methodGraphs);
        }
    }

    public static void addResourceProvider(Function<ResolvedJavaType, String[]> resourceProvider) {
        ClassLowerer.resourceProviders.add(resourceProvider);
    }

    /**
     * Emits a static method on the given type for initializing JS resource, if any are present.
     */
    private static void lowerJSResources(HostedType type, JSCodeGenTool loweringTool) {
        var requiredJSResources = AnnotationUtil.getDeclaredAnnotationsByType(type, JSResource.class, JSResource.Group.class, JSResource.Group::value);

        /*
         * JavaScriptResource is annotated as @Repeatable(JavaScriptResource.Group.class).
         * getDeclaredAnnotationsByType() must detect @Repeatable and thus also look for
         * JavaScriptResource.Group.
         */
        assert requiredJSResources.size() != 0 || !type.isAnnotationPresent(JSResource.Group.class) : "Repeated annotation not detected by getDeclaredAnnotationsByType";

        List<String> resourceNames = new ArrayList<>(requiredJSResources.size());

        requiredJSResources.stream().map(JSResource::value).forEachOrdered(resourceNames::add);
        for (Function<ResolvedJavaType, String[]> resourceProvider : resourceProviders) {
            String[] resources = resourceProvider.apply(type);
            resourceNames.addAll(List.of(resources));
        }

        CodeBuffer masm = loweringTool.getCodeBuffer();

        if (!resourceNames.isEmpty()) {
            Class<?> clazz = type.getJavaClass();
            masm.emitNewLine();
            String initFun = loweringTool.getJSProviders().typeControl().requestTypeName(type);
            masm.emitText("runtime.jsResourceInits." + initFun + " = () => {");
            masm.emitNewLine();
            for (String resName : resourceNames) {
                loweringTool.genComment(resName);
                try (InputStream is = clazz.getResourceAsStream(resName)) {
                    if (is == null) {
                        throw new FileNotFoundException(resName);
                    }

                    masm.emitText("(0,eval)(");
                    masm.emitNewLine();
                    masm.emitEscapedStringLiteral(new InputStreamReader(is));
                    masm.emitNewLine();
                    masm.emitText(");");
                    masm.emitNewLine();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            masm.emitText("}");
            masm.emitNewLine();
            masm.emitText("runtime.jsResourceInits." + initFun + "();");
            masm.emitNewLine();
        }
    }

    @SuppressWarnings("try")
    private void lowerMethod(HostedMethod hMethod) {
        StructuredGraph g = methodGraphs.get(hMethod);
        codeGenTool.prepareForMethod(g);
        try (Labeler.Injection injection = labeler.injectMethodLabel(codeBuffer, hMethod)) {
            lower(g);
        }
        LoggerContext.counter(EMITTED_METHODS).increment();
    }

    private void lower(StructuredGraph g) {
        assert g != null;

        ReconstructionData reconstructionData = ((ScheduleWithReconstructionResult) g.getLastSchedule()).reconstructionData();
        BlockMap<List<Node>> blockToNodeMap = g.getLastSchedule().getBlockToNodesMap();

        ControlFlowGraph cfg = g.getLastSchedule().getCFG();

        HostedMethod method = (HostedMethod) g.method();

        if (method.isClassInitializer() && method.getDeclaringClass().isInitialized()) {
            // initializer already executed
            return;
        } else {
            codeGenTool.genMethodHeader(g, method, getParameters(g));
        }

        codeGenTool.genComment(reconstructionData.toString(), WebImageOptions.CommentVerbosity.VERBOSE);

        if (g.method().isConstructor()) {
            genJavaMirrorJavaConstructorPreamble(g);
        }

        new WebImageJSIRWalker(codeGenTool, cfg, blockToNodeMap, cfg.getNodeToBlock(), reconstructionData).lowerFunction(g.getDebug());
        codeGenTool.genFunctionEnd();
    }

    @SuppressWarnings("unused")
    protected void genJavaMirrorJavaConstructorPreamble(StructuredGraph g) {
    }

    private static List<ParameterNode> getParameters(StructuredGraph graph) {
        List<ParameterNode> params = new ArrayList<>();
        for (ParameterNode n : graph.getNodes(ParameterNode.TYPE)) {
            params.add(n);
        }
        return params;
    }
}
