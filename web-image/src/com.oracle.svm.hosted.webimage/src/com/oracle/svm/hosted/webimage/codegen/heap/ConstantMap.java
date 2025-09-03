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
package com.oracle.svm.hosted.webimage.codegen.heap;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.core.jdk.StringInternSupport;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.webimage.Labeler;
import com.oracle.svm.hosted.webimage.WebImageHostedConfiguration;
import com.oracle.svm.hosted.webimage.codegen.JSCodeGenTool;
import com.oracle.svm.hosted.webimage.codegen.WebImageJSProviders;
import com.oracle.svm.hosted.webimage.js.JSKeyword;
import com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys;
import com.oracle.svm.hosted.webimage.util.metrics.CodeSizeCollector;
import com.oracle.svm.webimage.functionintrinsics.JSGenericFunctionDefinition;
import com.oracle.svm.webimage.hightiercodegen.CodeBuffer;
import com.oracle.svm.webimage.object.ConstantIdentityMapping;
import com.oracle.svm.webimage.object.ConstantIdentityMapping.IdentityNode;
import com.oracle.svm.webimage.object.ObjectInspector;
import com.oracle.svm.webimage.object.ObjectInspector.ObjectType;

import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.spi.IdentityHashCodeProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 *
 * Holds the different constants that resolve during code generation.
 *
 * Constants are resolved on access, but they are generated at the end of code generation.
 *
 * Every function local constant node resolves the attached constant and if the constant was defined
 * gets the "old" name or a new one if undefined.
 *
 */
public class ConstantMap {

    public final ConstantIdentityMapping identityMapping;
    private final WebImageJSProviders providers;

    /**
     * Reason passed to {@link ObjectInspector} when inspecing objects.
     */
    private Object defaultReason = null;

    public ConstantMap(WebImageJSProviders providers) {
        identityMapping = new ConstantIdentityMapping();
        this.providers = providers;
    }

    /**
     * resolve a constant node ( the constant value/object attached to this object is used for
     * identity check) if the attached value is a primitive no caching happens, if it's is an object
     * it will be resolved, already resolved objects are cached (via identity).
     *
     * @param cNode the constant node to inspect
     * @return the variable name to reference the constant from code
     */
    public String resolveConstantNode(ConstantNode cNode) {
        assert cNode != null;
        JavaConstant c = cNode.asJavaConstant();
        assert c != null;
        return resolveConstant(c);
    }

    /**
     *
     * Does basically the same as @see ConstantMap#resolveConstantNode(ConstantNode) just with a
     * constant and not a node (conveniance method).
     *
     * @param c the constant to resolve
     * @return the variable name to reference the constant from code
     */
    public String resolveConstant(JavaConstant c) {
        return saveObject(c);
    }

    private ObjectInspector.ObjectDefinition saveConstantObject(JavaConstant c) {
        return providers.objectInspector().inspectObject(c, defaultReason, identityMapping);
    }

    public String saveObject(JavaConstant c) {
        return saveObjectGetNode(c).requestName();
    }

    public IdentityNode saveHubGetNode(ResolvedJavaType t) {
        return saveObjectGetNode((JavaConstant) providers.getConstantReflection().asObjectHub(t));
    }

    public IdentityNode saveObjectGetNode(JavaConstant c) {
        saveConstantObject(c);
        return identityMapping.getIdentityNode(c);
    }

    /**
     * Processes statically collected interned strings and injects them into the final image.
     *
     * The strings themselves are already in the image, the only missing thing is updating the
     * 'imageInternedStrings' field in {@code ImageInternedStrings} to point to an array of these
     * strings.
     *
     * Since {@code ImageInternedStrings} was already inspected, we can't just set the field. We
     * have to create the array, inspect it and rewrite the field pointer in the existing inspected
     * {@link ObjectType}.
     */
    public void processInternedStrings() {
        HostedMetaAccess metaAccess = (HostedMetaAccess) providers.getMetaAccess();
        HostedField internedStringsField = metaAccess.optionalLookupJavaField(StringInternSupport.getInternedStringsField());
        boolean usesInternedStrings = internedStringsField != null && internedStringsField.isReachable();
        JavaConstant imageInternedStringsConstant = providers.getSnippetReflection().forObject(StringInternSupport.getImageInternedStrings());

        /*
         * Only add the interned strings if they are used.
         *
         * The `ImageInternedStrings` instance is not guaranteed to be in the constant map even if
         * the class is reachable.
         *
         * This is expected because we only put a value in the constant map if it is reachable from
         * static fields.
         */
        if (!usesInternedStrings || !identityMapping.hasMappingForObject(imageInternedStringsConstant)) {
            return;
        }

        String[] internedStrings = identityMapping.getInternedStrings();

        /*
         * After this we must no longer intern additional strings because the interned String array
         * cannot change anymore
         */
        identityMapping.disallowInternStrings();

        if (internedStrings.length == 0) {
            return;
        }

        /*
         * The already inspected instance of ImageInternedStrings.
         *
         * We inspect a new array with all the interned strings and replace the imageInternedStrings
         * field with it so that the updated array is emitted in the image.
         */
        ObjectType imageInternedStringsObject = (ObjectType) identityMapping.getDefByObject(imageInternedStringsConstant);
        StringInternSupport.setImageInternedStrings(internedStrings);

        /*
         * The index of the 'imageInternedStrings' field in the field list is the same as in the
         * 'members' list.
         */
        int imageInternedStringsFieldIndex = -1;
        for (int i = 0; i < imageInternedStringsObject.fields.fields.size(); i++) {
            if (imageInternedStringsObject.fields.fields.get(i).getName().equals("imageInternedStrings")) {
                imageInternedStringsFieldIndex = i;
                break;
            }
        }
        assert imageInternedStringsFieldIndex >= 0 : "The ImageInternedStrings singleton must have the 'imageInternedStrings' field";
        assert ((ObjectType) imageInternedStringsObject.members.get(
                        imageInternedStringsFieldIndex)).isNull() : "The ImageInternedStrings singleton must have the 'imageInternedStrings' field set to null";

        /* Manually snapshot the interned strings array. */
        ((AnalysisMetaAccess) metaAccess.getWrapped()).getUniverse().getHeapScanner().rescanObject(internedStrings, ObjectScanner.OtherReason.LATE_SCAN);

        JavaConstant internedStringsConstant = providers.getSnippetReflection().forObject(internedStrings);
        ObjectInspector.ArrayType<?> internedStringsNew = (ObjectInspector.ArrayType<?>) saveConstantObject(internedStringsConstant);
        imageInternedStringsObject.members.set(imageInternedStringsFieldIndex, internedStringsNew);
    }

    /**
     * Function for generating the code that emits the properties on each object, each object was
     * identified using {@link IdentityHashCodeProvider}.
     */
    public static final JSGenericFunctionDefinition WEB_IMAGE_CONST_PROPERTY_INIT_F_NAME = new JSGenericFunctionDefinition("generateConstantProperties", 0, false, null, false);

    /**
     * Generate code for constants: in general there are many sophisticated algorithms present which
     * deal with the resolving of cycles in object initialization, to keep it simple we allocate
     * each instance [temporary and constants] and then initialize them sequentially so all cycles
     * are broken as each object is allocated before property assignment.
     */
    @SuppressWarnings("try")
    public void lower(JSCodeGenTool jsLTools) {
        processInternedStrings();
        providers.objectInspector().freeze();

        CodeBuffer masm = jsLTools.getCodeBuffer();
        Labeler labeler = providers.labeler();
        masm.emitNewLine();

        JSBootImageHeapLowerer heapLowerer = WebImageHostedConfiguration.get().createBootImageHeapLowerer(providers, jsLTools, identityMapping);

        try (CodeSizeCollector collector = new CodeSizeCollector(ImageBreakdownMetricKeys.CONSTANT_DEFS_SIZE, jsLTools::getCodeSize);
                        Labeler.Injection injection = labeler.injectMetricLabel(jsLTools.getCodeBuffer(), ImageBreakdownMetricKeys.CONSTANT_DEFS_SIZE)) {
            heapLowerer.emitSetup();
        }

        masm.emitNewLine();
        masm.emitNewLine();
        masm.emitKeyword(JSKeyword.FUNCTION);
        masm.emitWhiteSpace();
        WEB_IMAGE_CONST_PROPERTY_INIT_F_NAME.emitReference(jsLTools);
        masm.emitText("() ");
        masm.emitScopeBegin();

        try (CodeSizeCollector collector = new CodeSizeCollector(ImageBreakdownMetricKeys.CONSTANT_INITS_SIZE, jsLTools::getCodeSize);
                        Labeler.Injection injection = labeler.injectMetricLabel(jsLTools.getCodeBuffer(), ImageBreakdownMetricKeys.CONSTANT_INITS_SIZE)) {
            // lower initialization
            heapLowerer.lowerInitialization();
        }

        jsLTools.genScopeEnd();

        // lower declaration
        try (CodeSizeCollector collector = new CodeSizeCollector(ImageBreakdownMetricKeys.CONSTANT_DEFS_SIZE, jsLTools::getCodeSize);
                        Labeler.Injection injection = labeler.injectMetricLabel(jsLTools.getCodeBuffer(), ImageBreakdownMetricKeys.CONSTANT_DEFS_SIZE)) {
            heapLowerer.lowerDeclarations();
        }
    }

    public void setDefaultReason(Object defaultReason) {
        this.defaultReason = defaultReason;
    }
}
