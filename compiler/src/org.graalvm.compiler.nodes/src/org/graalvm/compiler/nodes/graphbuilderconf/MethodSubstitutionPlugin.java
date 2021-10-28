/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.graphbuilderconf;

import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;
import static org.graalvm.compiler.core.common.GraalOptions.UseEncodedGraphs;
import static org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.INLINE_AFTER_PARSING;
import static org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.resolveType;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * An {@link InvocationPlugin} for a method where the implementation of the method is provided by a
 * {@linkplain #getSubstitute(MetaAccessProvider) substitute} method. A substitute method must be
 * static even if the substituted method is not.
 *
 * While performing intrinsification with method substitutions is simpler than writing an
 * {@link InvocationPlugin} that does manual graph weaving, it has a higher compile time cost than
 * the latter; parsing bytecodes to create nodes is slower than simply creating nodes. As such, the
 * recommended practice is to use {@link MethodSubstitutionPlugin} only for complex
 * intrinsifications which is typically those using non-straight-line control flow.
 */
public final class MethodSubstitutionPlugin implements InvocationPlugin {

    private InvocationPlugins.Registration registration;

    private ResolvedJavaMethod cachedSubstitute;

    /**
     * The class in which the substitute method is declared.
     */
    private final Class<?> declaringClass;

    /**
     * The name of the substitute method.
     */
    private final String substituteName;

    /**
     * The name of the original method.
     */
    private final String originalName;

    /**
     * The parameter types of the substitute method.
     */
    private final Type[] parameters;

    private final boolean originalIsStatic;

    private final BytecodeProvider bytecodeProvider;

    /**
     * Creates a method substitution plugin.
     *
     * @param bytecodeProvider used to get the bytecodes to parse for the substitute method
     * @param originalName the name of the original method
     * @param declaringClass the class in which the substitute method is declared
     * @param substituteName the name of the substitute method
     * @param parameters the parameter types of the substitute method. If the original method is not
     *            static, then {@code parameters[0]} must be the {@link Class} value denoting
     *            {@link InvocationPlugin.Receiver}
     */
    public MethodSubstitutionPlugin(InvocationPlugins.Registration registration, BytecodeProvider bytecodeProvider, String originalName, Class<?> declaringClass, String substituteName,
                    Type... parameters) {
        assert bytecodeProvider != null : "Requires a non-null methodSubstitutionBytecodeProvider";
        this.registration = registration;
        this.bytecodeProvider = bytecodeProvider;
        this.originalName = originalName;
        this.declaringClass = declaringClass;
        this.substituteName = substituteName;
        this.parameters = parameters;
        this.originalIsStatic = parameters.length == 0 || parameters[0] != InvocationPlugin.Receiver.class;
    }

    /**
     * Gets the substitute method, resolving it first if necessary.
     */
    public ResolvedJavaMethod getSubstitute(MetaAccessProvider metaAccess) {
        if (cachedSubstitute == null) {
            cachedSubstitute = metaAccess.lookupJavaMethod(getJavaSubstitute());
        }
        return cachedSubstitute;
    }

    /**
     * Gets the object used to access the bytecodes of the substitute method.
     */
    public BytecodeProvider getBytecodeProvider() {
        return bytecodeProvider;
    }

    /**
     * Gets the class in which the substitute method is declared.
     */
    public Class<?> getDeclaringClass() {
        return declaringClass;
    }

    /**
     * Gets the reflection API version of the substitution method.
     */
    public Method getJavaSubstitute() throws GraalError {
        Method substituteMethod = lookupSubstitute();
        int modifiers = substituteMethod.getModifiers();
        if (Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers)) {
            throw new GraalError("Substitution method must not be abstract or native: " + substituteMethod);
        }
        if (!Modifier.isStatic(modifiers)) {
            throw new GraalError("Substitution method must be static: " + substituteMethod);
        }
        return substituteMethod;
    }

    /**
     * Determines if a given method is the substitute method of this plugin.
     */
    private boolean isSubstitute(Method m) {
        if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(substituteName)) {
            if (parameters.length == m.getParameterCount()) {
                Class<?>[] mparams = m.getParameterTypes();
                int start = 0;
                if (!originalIsStatic) {
                    start = 1;
                    if (!mparams[0].isAssignableFrom(resolveType(parameters[0], false))) {
                        return false;
                    }
                }
                for (int i = start; i < mparams.length; i++) {
                    if (mparams[i] != resolveType(parameters[i], false)) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private Method lookupSubstitute(Method excluding) {
        for (Method m : declaringClass.getDeclaredMethods()) {
            if (!m.equals(excluding) && isSubstitute(m)) {
                return m;
            }
        }
        return null;
    }

    /**
     * Gets the substitute method of this plugin.
     */
    private Method lookupSubstitute() {
        Method m = lookupSubstitute(null);
        if (m != null) {
            assert lookupSubstitute(m) == null : String.format("multiple matches found for %s:%n%s%n%s", this, m, lookupSubstitute(m));
            return m;
        }
        throw new GraalError("No method found specified by %s", this);
    }

    @Override
    public boolean execute(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode[] argsIncludingReceiver) {
        if (IS_IN_NATIVE_IMAGE || (UseEncodedGraphs.getValue(b.getOptions()) && !b.parsingIntrinsic())) {
            StructuredGraph subst = b.getReplacements().getMethodSubstitution(this,
                            targetMethod,
                            INLINE_AFTER_PARSING,
                            StructuredGraph.AllowAssumptions.ifNonNull(b.getAssumptions()),
                            null /* cancellable */,
                            b.getOptions());
            if (subst == null) {
                throw new GraalError("No graphs found for substitution %s", this);
            }
            return b.intrinsify(targetMethod, subst, receiver, argsIncludingReceiver);
        }
        ResolvedJavaMethod substitute = getSubstitute(b.getMetaAccess());
        return b.intrinsify(bytecodeProvider, targetMethod, substitute, receiver, argsIncludingReceiver);
    }

    @Override
    public String getSourceLocation() {
        Class<?> c = getClass();
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals("execute")) {
                return String.format("%s.%s()", m.getClass().getName(), m.getName());
            }
        }
        throw new GraalError("could not find method named \"execute\" in " + c.getName());
    }

    @Override
    public String toString() {
        return String.format("%s[%s.%s(%s)]", getClass().getSimpleName(), declaringClass.getName(), substituteName,
                        Arrays.asList(parameters).stream().map(c -> c.getTypeName()).collect(Collectors.joining(", ")));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MethodSubstitutionPlugin that = (MethodSubstitutionPlugin) o;
        return originalIsStatic == that.originalIsStatic &&
                        Objects.equals(declaringClass, that.declaringClass) &&
                        Objects.equals(substituteName, that.substituteName) &&
                        Objects.equals(originalName, that.originalName) &&
                        Arrays.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(declaringClass, substituteName, originalName, originalIsStatic);
        return result;
    }

    public String originalMethodAsString() {
        return String.format("%s.%s(%s)", declaringClass.getName(), substituteName, Arrays.asList(parameters).stream().map(c -> c.getTypeName()).collect(Collectors.joining(", ")));
    }

    public ResolvedJavaMethod getOriginalMethod(MetaAccessProvider metaAccess) {
        Class<?> clazz = resolveType(registration.getDeclaringType(), false);
        if (clazz == null) {
            throw new GraalError("Can't find original class for " + this + " with class " + registration.getDeclaringType());
        }
        ResolvedJavaType type = metaAccess.lookupJavaType(clazz);
        String argumentsDescriptor = InvocationPlugins.toArgumentDescriptor(originalIsStatic, this.parameters);
        for (ResolvedJavaMethod declared : type.getDeclaredMethods()) {
            if (declared.getName().equals(originalName) && declared.isStatic() == originalIsStatic &&
                            declared.getSignature().toMethodDescriptor().startsWith(argumentsDescriptor)) {
                return declared;
            }
        }
        throw new GraalError("Can't find original method for " + this + " with class " + registration.getDeclaringType());
    }
}
