/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.meta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.OriginalFieldProvider;
import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.infrastructure.Universe;
import com.oracle.graal.pointsto.infrastructure.WrappedConstantPool;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.graal.pointsto.infrastructure.WrappedSignature;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.code.CompilationInfo;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.code.FactoryMethod;
import com.oracle.svm.hosted.lambda.LambdaSubstitutionType;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import com.oracle.svm.hosted.substitute.SubstitutionType;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * The native image generator uses multiple layers of implementations of JVMCI interfaces for
 * {@link ResolvedJavaType types}, {@link ResolvedJavaMethod methods} and {@link ResolvedJavaField
 * fields}. In this documentation, we use the term "element" to refer to a type, method, or field.
 * All elements of one particular layer are called a "universe".
 * 
 * There are 4 layers in use:
 * <ol>
 * <li>The "HotSpot universe": the original source of elements, as parsed from class files.</li>
 * <li>The "substitution layer" to modify some of the elements coming from class files, without
 * modifying class files.</li>
 * <li>The "analysis universe": elements that the static analysis operates on.</li>
 * <li>The "hosted universe": elements that the AOT compilation operates on.</li>
 * </ol>
 * 
 * Not covered in this documentation is the "substrate universe", i.e., elements that are used for
 * JIT compilation at image run time when a native image contains the GraalVM compiler itself. JIT
 * compilation is only used when a native image contains a Truffle language, e.g., the JavaScript
 * implementation that is part of GraalVM. For "normal" applications, all code is AOT compiled and
 * no JIT compilation is necessary.
 * 
 * <h1>Navigating the Layers</h1>
 * 
 * Elements of higher layers wrap elements of lower layers. There is generally a method available
 * that returns the wrapped element, e.g., {@link HostedMethod#getWrapped()} and
 * {@link AnalysisMethod#getWrapped()}. The conversion from a lower layer to a higher layer is done
 * via the universe classes, e.g., {@link HostedUniverse#lookup(JavaMethod)} and
 * {@link AnalysisUniverse#lookup(JavaMethod)}.
 * 
 * There is no standard way to navigate the substitution layer, because each element there has a
 * different behaviour. In general it should be avoided as much as possible to reach directly into
 * the substitution layer. But sometimes it is unavoidable, and then code needs to be written for a
 * specific substitution element. For example, when it is necessary to introspect a method
 * substitution, a direct cast to {@link SubstitutionMethod} is necessary.
 * 
 * <h1>JVMCI vs. Reflection</h1>
 * 
 * The JVMCI interfaces are similar to the reflection objects: {@link ResolvedJavaType} for
 * {@link Class}, {@link ResolvedJavaMethod} for {@link Method}, {@link ResolvedJavaField} for
 * {@link Field}. But using the JVCMI interfaces has many advantages over reflection. It provides
 * access to VM-level information such as the bytecode array of a method; the constant pool of a
 * class; the offset of a field. But more importantly, it is not necessary that there is an actual
 * bytecode representation (and therefore a reflection object) of a JVMCI element. We make use of
 * that in the substitution layer.
 * 
 * In general, it is always easy and possible to convert a reflection object to a JVMCI object.
 * {@link MetaAccessProvider} has all the appropriate lookup methods for it. In JVMCI itself, there
 * is no link back from a JVMCI object to a reflection object. But in the native image generator, it
 * turned out to be necessary to sometimes convert back to a reflection object because not all
 * necessary information is available via JVMCI. This can be done via the interfaces
 * {@link OriginalClassProvider}, {@link OriginalMethodProvider}, and {@link OriginalFieldProvider}.
 * The elements from the analysis universe and hosted universe implement these interfaces. But it is
 * important to state again: it is not necessary that there is an actual bytecode representation for
 * JVCMI elements. This means there are JVMCI objects that do not have a corresponding reflection
 * object. <b>All code that uses reflection objects must therefore be prepared that the returned
 * reflection object is null</b>. And due to the substitution layer, any information returned by the
 * reflection object can be different compared to the JVMCI object. Even the most trivial things,
 * like the name of an element. For example, the reflection class for {@link DynamicHub} is
 * {@link java.lang.Class}, due to the {@link Substitute} and {@link TargetClass} annotations on
 * {@link DynamicHub}.
 * 
 * <h1>The HotSpot Universe</h1>
 * 
 * Most elements in a native image originate from .class files. The native image generator does not
 * contain a class file parser, so the only way information from class files flows in is via JVMCI
 * from the Java HotSpot VM. Since JVMCI is VM independent, in theory any other Java VM that
 * implements JVMCI could be the source of information. In practice, the Java HotSpot VM is the only
 * known and supported VM for now. Still, it is frowned upon to reach into any JVMCI object of the
 * HotSpot universe. Many of the HotSpot implementation classes are not public anyway, but even the
 * public ones must not be used directly.
 * 
 * Using the HotSpot universe keeps a lot of complexity out of the native image generator. Here are
 * some examples of code that does not exist in the native image generator:
 * <ul>
 * <li>A parser for the binary format of .class files.</li>
 * <li>Code to resolve and interpret constant pool entries.</li>
 * <li>Code to resolve virtual method calls, i.e., to compute the actual method invoked given a base
 * class method and a concrete implementation type.</li>
 * <li>Code for subtype check, i.e., is type A assignable from type B.</li>
 * </ul>
 * 
 * <h1>The Analysis Universe</h1>
 * 
 * The {@link AnalysisUniverse} manages the {@link AnalysisType types}, {@link AnalysisMethod
 * methods}, and {@link AnalysisField fields} that the static analysis operates on. These elements
 * store information used during static analysis as well as the static analysis results, for example
 * {@link AnalysisType#isReachable()} returns if that type was seen as reachable by the static
 * analysis.
 * 
 * A static analysis implements {@link BigBang}. Currently, the only analysis in the project is
 * {@link PointsToAnalysis}, but ongoing research projects investigate different kinds of static
 * analysis. Therefore, the element types are extensible, for example there is
 * {@link PointsToAnalysisMethod} as the implementation class used by {@link PointsToAnalysis}.
 * Using these implementation classes should be avoided as much as possible, to keep the static
 * analysis implementation exchangeable.
 * 
 * The elements in the analysis universe generally do not change the behavior of the elements they
 * wrap. One could therefore argue that there should not be an analysis universe at all, and
 * information used and computed by the static analysis should be stored in classes that do not
 * extend the JVMCI interfaces. It is however quite convenient to have parsed {@link StructuredGraph
 * Graal IR graphs} that reference JVMCI objects from a consistent universe. The analysis universe
 * therefore acts as a unifying layer above the quite unstructured substitution layer. And there are
 * a few places where analysis elements do not delegate to the wrapped layer, for example to query
 * if a {@link AnalysisType#isInitialized() type is initialized}. The analysis layer also implements
 * caches for a few operations that are expensive in the HotSpot layer, to reduce the time spent in
 * the static analysis.
 * 
 * <h1>The Hosted Universe</h1>
 * 
 * The {@link HostedUniverse} manages the {@link HostedType types}, {@link HostedMethod methods},
 * and {@link HostedField fields} that the ahead-of-time (AOT) compilation operates on. These
 * elements are created by the {@link UniverseBuilder} after the static analysis has finished. They
 * store information such as the layout of objects (offsets of fields), the vtable used for virtual
 * method calls, or information for is-assignable type checks in AOT compiled code.
 * 
 * For historic reasons, {@link HostedType} has subclasses for different kinds of types:
 * {@link HostedInstanceClass}, {@link HostedArrayClass}, {@link HostedInterface}, and
 * {@link HostedPrimitiveType}. There is not necessity to keep this class hierarchy, but also no
 * need to remove it.
 * 
 * Having a separate analysis universe and hosted universe complicates some things. For example,
 * {@link StructuredGraph graphs} parsed for static analysis need to be "transplanted" from the
 * analysis universe to the hosted universe (see code around
 * {@link CompileQueue#replaceAnalysisObjects}). But the separate universes make AOT compilation
 * more flexible because elements can be duplicated as necessary. For example, a method can be
 * compiled with different optimization levels or for different optimization contexts. One concrete
 * example are methods compiled as {@link HostedMethod#isDeoptTarget() deoptimization entry points}.
 * Therefore, no code must assume a 1:1 relationship between analysis and hosted elements, but a 1:n
 * relationship where there are multiple hosted elements for a single analysis element.
 * 
 * In theory, only analysis elements that are found reachable by the static analysis would need a
 * corresponding hosted element. But in practice, this optimization did not work: for example,
 * snippets and graph builder plugins reference elements that are not reachable themselves, because
 * they are used only during lowering or only during bytecode parsing. Therefore,
 * {@link UniverseBuilder} creates hosted elements also for unreachable analysis elements. It is
 * therefore safe to assume that {@link HostedUniverse} returns a hosted element for every analysis
 * element that is passed as an argument.
 * 
 * {@link HostedUniverse} returns the hosted element that was created by {@link UniverseBuilder} for
 * the corresponding analysis element. If multiple hosted elements exist for an analysis element,
 * the additional elements must be maintained in a secondary storage used by the AOT compilation
 * phases that need them. For example, the mapping between a regular method and a deoptimization
 * entry point method is maintained in {@link CompilationInfo}.
 * 
 * <h1>The Substitution Layer</h1>
 *
 * The substitution layer is a not-so-well-defined set of elements that sit between the HotSpot
 * universe and the analysis universe. These elements do not form a complete universe. This means
 * that for the majority of elements that are not affected by any substitution, the analysis element
 * directly wraps the HotSpot element. For example, for most types
 * {@link AnalysisType#getWrapped()}} returns a {@link HotSpotResolvedJavaType}.
 * 
 * Substitutions are processed by a chain of {@link SubstitutionProcessor} that are typically
 * registered by a {@link Feature} via {@link DuringSetupAccessImpl#registerSubstitutionProcessor}
 * (note that this is not an API exposed to application developers). Pairs of lookup/resolve methods
 * perform the substitution.
 *
 * The annotations like {@link Substitute}, {@link Alias}, {@link TargetClass} (and several more)
 * are processed by one particular implementation of {@link SubstitutionProcessor}:
 * {@link AnnotationSubstitutionProcessor}. This a prominent and flexible substitution processor,
 * but by far not the only one. Since many substitution processors are chained, there can also be
 * chains of elements between a HotSpot element and an analysis element.
 *
 * Elements produced by a substitution processor usually do one of the following things:
 * <ul>
 * <li>A substitution element wraps one other element and changes some aspects. For example,
 * {@link LambdaSubstitutionType} does not do much more than changing the name of its wrapped type,
 * and injecting an annotation (so that the type appears as it implements an annotation that is
 * actually not present in the .class file)</li>
 *
 * <li>A substitution element wraps two other elements and combines aspects. For example,
 * {@link SubstitutionMethod} wraps a {@link SubstitutionMethod#getOriginal()} method and a
 * {@link SubstitutionMethod#getAnnotated()} method and then forwards calls to either of these
 * depending on the operation (name and signature come from the original method, the bytecode from
 * the annotated method).</li>
 *
 * <li>A substitution element produces a new element that has no bytecode representation. For
 * example, {@link FactoryMethod} is a synthetic method that combines the allocation and the
 * constructor invocation of a type.</li>
 * </ul>
 *
 * Substitution processors can modify many aspects of elements, but there are also hard limitations:
 * they cannot modify aspects that are not implemented by the native image generator itself, but
 * accessed via the HotSpot universe. For example, they cannot modify virtual method resolution and
 * subtype checks (see the list in the section about the HotSpot universe). In general it is safe to
 * say that substituted elements can change any behavior of one particular element, but not how
 * multiple elements interact with each other (because substitutions are not a complete universe).
 *
 * For example, {@link SubstitutionType} changes a lot of aspects that are local to an existing type
 * (name, instance fields, ...). But it would be quite impossible to inject a new synthetic type
 * into a class hierarchy because that type would not participate properly in virtual method
 * resolution or subtype checks.
 */
public class HostedUniverse implements Universe {
    protected final Inflation bb;

    protected final Map<AnalysisType, HostedType> types = new HashMap<>();
    protected final Map<AnalysisField, HostedField> fields = new HashMap<>();
    protected final Map<AnalysisMethod, HostedMethod> methods = new HashMap<>();
    protected final Map<Signature, WrappedSignature> signatures = new HashMap<>();
    protected final Map<ConstantPool, WrappedConstantPool> constantPools = new HashMap<>();

    protected EnumMap<JavaKind, HostedType> kindToType = new EnumMap<>(JavaKind.class);

    protected List<HostedType> orderedTypes;
    protected List<HostedMethod> orderedMethods;
    protected List<HostedField> orderedFields;

    public HostedUniverse(Inflation bb) {
        this.bb = bb;
    }

    public HostedType getType(JavaKind kind) {
        assert kindToType.containsKey(kind);
        return kindToType.get(kind);
    }

    public HostedInstanceClass getObjectClass() {
        HostedInstanceClass result = (HostedInstanceClass) kindToType.get(JavaKind.Object);
        assert result != null;
        return result;
    }

    public synchronized HostedMethod createDeoptTarget(HostedMethod method) {
        if (method.compilationInfo.getDeoptTargetMethod() == null) {
            HostedMethod deoptTarget = new HostedMethod(this, method.getWrapped(), method.getDeclaringClass(), method.getSignature(), method.getConstantPool(), method.getExceptionHandlers(), method);
            assert method.staticAnalysisResults != null;
            deoptTarget.staticAnalysisResults = method.staticAnalysisResults;
        }
        return method.compilationInfo.getDeoptTargetMethod();
    }

    public boolean contains(JavaType type) {
        return types.containsKey(type);
    }

    @Override
    public SVMHost hostVM() {
        return bb.getHostVM();
    }

    @Override
    public SnippetReflectionProvider getSnippetReflection() {
        return bb.getProviders().getSnippetReflection();
    }

    @Override
    public HostedType lookup(JavaType type) {
        JavaType result = lookupAllowUnresolved(type);
        if (result instanceof ResolvedJavaType) {
            return (HostedType) result;
        }
        throw new UnsupportedFeatureException("Unresolved type found. Probably there are some compilation or classpath problems. " + type.toJavaName(true));
    }

    @Override
    public JavaType lookupAllowUnresolved(JavaType type) {
        if (!(type instanceof ResolvedJavaType)) {
            return type;
        }
        assert types.containsKey(type) : type;
        return optionalLookup(type);
    }

    public HostedType optionalLookup(JavaType type) {
        return types.get(type);
    }

    @Override
    public HostedField lookup(JavaField field) {
        JavaField result = lookupAllowUnresolved(field);
        if (result instanceof ResolvedJavaField) {
            return (HostedField) result;
        }
        throw new UnsupportedFeatureException("Unresolved field found. Probably there are some compilation or classpath problems. " + field.format("%H.%n"));
    }

    @Override
    public JavaField lookupAllowUnresolved(JavaField field) {
        if (!(field instanceof ResolvedJavaField)) {
            return field;
        }
        assert fields.containsKey(field) : field;
        return optionalLookup(field);
    }

    public HostedField optionalLookup(JavaField field) {
        return fields.get(field);
    }

    @Override
    public HostedMethod lookup(JavaMethod method) {
        JavaMethod result = lookupAllowUnresolved(method);
        if (result instanceof ResolvedJavaMethod) {
            return (HostedMethod) result;
        }
        throw new UnsupportedFeatureException("Unresolved method found: " + (method != null ? method.format("%H.%n(%p)") : "null") +
                        ". Probably there are some compilation or classpath problems. ");
    }

    @Override
    public JavaMethod lookupAllowUnresolved(JavaMethod method) {
        if (!(method instanceof ResolvedJavaMethod)) {
            return method;
        }
        assert methods.containsKey(method) : method;
        return optionalLookup(method);
    }

    public HostedMethod optionalLookup(JavaMethod method) {
        return methods.get(method);
    }

    public HostedMethod[] lookup(JavaMethod[] inputs) {
        HostedMethod[] result = new HostedMethod[inputs.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = lookup(inputs[i]);
        }
        return result;
    }

    @Override
    public WrappedSignature lookup(Signature signature, WrappedJavaType defaultAccessingClass) {
        assert signatures.containsKey(signature) : signature;
        return signatures.get(signature);
    }

    @Override
    public WrappedConstantPool lookup(ConstantPool constantPool, WrappedJavaType defaultAccessingClass) {
        assert constantPools.containsKey(constantPool) : constantPool;
        return constantPools.get(constantPool);
    }

    @Override
    public JavaConstant lookup(JavaConstant constant) {
        // There should not be any conversion necessary for constants.
        return constant;
    }

    public Collection<HostedType> getTypes() {
        return orderedTypes;
    }

    public Collection<HostedField> getFields() {
        return orderedFields;
    }

    public Collection<HostedMethod> getMethods() {
        return orderedMethods;
    }

    public Inflation getBigBang() {
        return bb;
    }

    public AnalysisConstantReflectionProvider getConstantReflectionProvider() {
        return (AnalysisConstantReflectionProvider) bb.getConstantReflectionProvider();
    }

    @Override
    public ResolvedJavaMethod resolveSubstitution(ResolvedJavaMethod method) {
        return method;
    }

    @Override
    public HostedType objectType() {
        return types.get(bb.getUniverse().objectType());
    }
}
