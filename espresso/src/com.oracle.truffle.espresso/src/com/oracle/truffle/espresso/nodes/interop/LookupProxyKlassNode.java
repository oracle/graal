/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.nodes.interop;

import java.util.HashSet;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ClassRegistry;
import com.oracle.truffle.espresso.impl.EspressoClassLoadingException;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;

@GenerateUncached
public abstract class LookupProxyKlassNode extends EspressoNode {
    static final int LIMIT = 3;

    LookupProxyKlassNode() {
    }

    public abstract WrappedProxyKlass execute(Object metaObject, String metaName, Klass targetType) throws ClassCastException;

    @SuppressWarnings("unused")
    @Specialization(guards = {"targetType == cachedTargetType", "cachedMetaName.equals(metaName)"}, limit = "LIMIT")
    WrappedProxyKlass doCached(Object metaObject, String metaName, Klass targetType,
                    @Cached("metaObject") Object cachedMetaObject,
                    @Cached("targetType") Klass cachedTargetType,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Cached("metaName") String cachedMetaName,
                    @Cached("doUncached(metaObject, metaName, targetType, interop)") WrappedProxyKlass cachedProxyKlass) throws ClassCastException {
        return cachedProxyKlass;
    }

    @TruffleBoundary
    @Specialization(replaces = "doCached")
    WrappedProxyKlass doUncached(Object metaObject, String metaName, Klass targetType,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws ClassCastException {
        if (!getContext().interfaceMappingsEnabled()) {
            return null;
        }
        assert interop.isMetaObject(metaObject);
        EspressoForeignProxyGenerator.GeneratedProxyBytes proxyBytes = getContext().getProxyBytesOrNull(metaName);
        if (proxyBytes == null) {
            // cache miss
            Set<ObjectKlass> parentInterfaces = new HashSet<>();
            ObjectKlass superKlass = fillParents(metaObject, interop, getContext().getPolyglotTypeMappings(), parentInterfaces, getContext());
            if (parentInterfaces.isEmpty()) {
                if (superKlass != getMeta().java_lang_Object) {
                    if (!targetType.isAssignableFrom(superKlass)) {
                        throw new ClassCastException("super klass is not instance of expected type: " + targetType.getName());
                    }
                    return new WrappedProxyKlass(superKlass);
                }
                return null;
            }
            proxyBytes = EspressoForeignProxyGenerator.getProxyKlassBytes(metaName, parentInterfaces.toArray(new ObjectKlass[parentInterfaces.size()]), superKlass, getContext());
        }
        Klass proxyKlass = lookupOrDefineInBindingsLoader(proxyBytes, getContext());

        if (!targetType.isAssignableFrom(proxyKlass)) {
            throw new ClassCastException("proxy object is not instance of expected type: " + targetType.getName());
        }
        return proxyBytes.getProxyKlass((ObjectKlass) proxyKlass);
    }

    private static Klass lookupOrDefineInBindingsLoader(EspressoForeignProxyGenerator.GeneratedProxyBytes proxyBytes, EspressoContext context) {
        ClassRegistry registry = context.getRegistries().getClassRegistry(context.getBindingsLoader());

        Symbol<Symbol.Type> proxyName = context.getTypes().fromClassGetName(proxyBytes.name);
        Klass proxyKlass = registry.findLoadedKlass(context.getClassLoadingEnv(), proxyName);
        if (proxyKlass == null) {
            try {
                proxyKlass = registry.defineKlass(context, proxyName, proxyBytes.bytes);
            } catch (EspressoClassLoadingException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }
        return proxyKlass;
    }

    private static ObjectKlass fillParents(Object metaObject, InteropLibrary interop, PolyglotTypeMappings mappings, Set<ObjectKlass> parents, EspressoContext context) throws ClassCastException {
        Meta meta = context.getMeta();
        ObjectKlass superKlass = meta.java_lang_Object;
        try {
            if (interop.hasMetaParents(metaObject)) {
                Object metaParents = interop.getMetaParents(metaObject);

                long arraySize = interop.getArraySize(metaParents);
                for (long i = 0; i < arraySize; i++) {
                    Object parent = interop.readArrayElement(metaParents, i);
                    String metaName = interop.asString(interop.getMetaQualifiedName(parent));
                    ObjectKlass mappedKlass = mappings.mapInterfaceName(metaName);
                    if (mappedKlass != null) {
                        parents.add(mappedKlass);
                    } else if (context.getEspressoEnv().BuiltInPolyglotCollections) {
                        ObjectKlass mappedSuperKlass = mappings.mapEspressoForeignCollection(metaName);
                        if (mappedSuperKlass != null) {
                            superKlass = mappedSuperKlass;
                            continue;
                        }
                    }
                    ObjectKlass result = fillParents(parent, interop, mappings, parents, context);
                    if (superKlass == meta.java_lang_Object && result != meta.java_lang_Object) {
                        superKlass = result;
                    }
                }
            }
        } catch (InvalidArrayIndexException | UnsupportedMessageException e) {
            throw new ClassCastException();
        }
        return superKlass;
    }
}
