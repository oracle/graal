/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.compiler.TruffleCompilerRuntime;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Base class for references to well known types and fields.
 * <p>
 * The reason this class exists is to make sure fields are initialized, such that type, method and
 * field lookup can be expressed directly in the field initializer.
 */
public abstract class AbstractKnownTruffleTypes {

    private final TruffleCompilerRuntime runtime;
    protected final MetaAccessProvider metaAccess;
    private TypeCache typeCache;

    protected AbstractKnownTruffleTypes(TruffleCompilerRuntime runtime, MetaAccessProvider metaAccess) {
        this.runtime = runtime;
        this.metaAccess = metaAccess;
    }

    public final ResolvedJavaType lookupType(String className) {
        ResolvedJavaType type = runtime.resolveType(metaAccess, className);
        onTypeLookup(type);
        return type;
    }

    public final ResolvedJavaType lookupTypeOptional(String className) {
        ResolvedJavaType type = runtime.resolveType(metaAccess, className, false);
        onTypeLookup(type);
        return type;
    }

    protected final ResolvedJavaType lookupType(Class<?> c) {
        ResolvedJavaType type = metaAccess.lookupJavaType(c);
        onTypeLookup(type);
        return type;
    }

    protected final ResolvedJavaType lookupTypeCached(String className) {
        ResolvedJavaType type = runtime.resolveType(metaAccess, className);
        onTypeLookup(type);
        this.typeCache = createTypeCache(type);
        return typeCache.declaringClass;
    }

    protected final ResolvedJavaType lookupTypeCached(Class<?> c) {
        ResolvedJavaType type = metaAccess.lookupJavaType(c);
        onTypeLookup(type);
        this.typeCache = createTypeCache(type);
        return typeCache.declaringClass;
    }

    protected void onTypeLookup(@SuppressWarnings("unused") ResolvedJavaType type) {
        /*
         * Overridden by SVM to make type reachable.
         */
    }

    protected final ResolvedJavaMethod findMethod(ResolvedJavaType declaringClass, String name, ResolvedJavaType... types) {
        Collection<ResolvedJavaMethod> methods = getTypeCache(declaringClass).methods.get(name);
        return findMethod(declaringClass, name, methods, types);
    }

    private static ResolvedJavaMethod findMethod(ResolvedJavaType declaringClass, String name, Collection<ResolvedJavaMethod> methods, ResolvedJavaType... types) throws NoSuchMethodError {
        for (ResolvedJavaMethod method : methods) {
            assert method.getName().equals(name);
            Signature signature = method.getSignature();
            int parameterCount = signature.getParameterCount(false);
            if (parameterCount == types.length) {
                boolean allValid = true;
                for (int i = 0; i < parameterCount; i++) {
                    JavaType searchType = signature.getParameterType(i, declaringClass);
                    if (!searchType.equals(types[i])) {
                        allValid = false;
                        break;
                    }
                }
                if (allValid) {
                    return method;
                }
            }
        }
        throw new NoSuchMethodError(declaringClass.toJavaName() + "." + name + Arrays.toString(types));
    }

    ResolvedJavaMethod findMethod(ResolvedJavaType declaringClass, String name, String descriptor) {
        for (ResolvedJavaMethod method : getTypeCache(declaringClass).methods.get(name)) {
            if (method.getSignature().toMethodDescriptor().equals(descriptor)) {
                return method;
            }
        }
        throw new NoSuchMethodError(declaringClass.toJavaName() + "." + name + descriptor);
    }

    protected ResolvedJavaField[] findInstanceFields(ResolvedJavaType declaringClass) {
        return getTypeCache(declaringClass).instanceFields;
    }

    protected final ResolvedJavaField findField(ResolvedJavaType declaringClass, String name, boolean required) {
        TypeCache fc = getTypeCache(declaringClass);
        ResolvedJavaField field = fc.fields.get(name);
        if (field == null && required) {
            throw new GraalError("Could not find required field %s.%s", declaringClass.getName(), name);
        }
        return field;
    }

    protected final ResolvedJavaField findField(ResolvedJavaType declaringClass, String name) {
        return findField(declaringClass, name, true);
    }

    private TypeCache getTypeCache(ResolvedJavaType declaringClass) {
        if (typeCache == null || !typeCache.declaringClass.equals(declaringClass)) {
            GraalError.shouldNotReachHere("Use lookupTypeCached instead to lookup methods or fields.");
        }
        return typeCache;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private TypeCache createTypeCache(ResolvedJavaType declaringClass) {
        GraalError.guarantee(this.typeCache == null || !declaringClass.equals(this.typeCache.declaringClass), "duplicate consecutive cached type lookup");
        ResolvedJavaField[] instanceFields = declaringClass.getInstanceFields(false);
        ResolvedJavaField[] staticFields = declaringClass.getStaticFields();

        Map.Entry<String, ResolvedJavaField>[] entries = new Map.Entry[instanceFields.length + staticFields.length];
        for (int i = 0; i < instanceFields.length; i++) {
            ResolvedJavaField field = instanceFields[i];
            entries[i] = Map.entry(field.getName(), field);
        }
        for (int i = 0; i < staticFields.length; i++) {
            ResolvedJavaField field = staticFields[i];
            entries[instanceFields.length + i] = Map.entry(field.getName(), field);
        }
        Map<String, ResolvedJavaField> fields = CollectionsUtil.mapOfEntries(entries);
        GraalError.guarantee(fields.size() == entries.length, "duplicate field name");

        Map<String, List<ResolvedJavaMethod>> methods = new EconomicHashMap<>();
        for (ResolvedJavaMethod method : declaringClass.getDeclaredMethods(false)) {
            methods.computeIfAbsent(method.getName(), (v) -> new ArrayList<>(2)).add(method);
        }
        return new TypeCache(declaringClass, instanceFields, fields, methods);
    }

    private record TypeCache(ResolvedJavaType declaringClass,
                    ResolvedJavaField[] instanceFields,
                    Map<String, ResolvedJavaField> fields,
                    Map<String, List<ResolvedJavaMethod>> methods) {
    }

}
