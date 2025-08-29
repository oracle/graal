/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.jni.Mangle;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.MethodKey;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.substitutions.Substitution;

/**
 * Espresso implementation of a standard native library.
 * <p>
 * It is simply a mapping from {@link Mangle jni symbols} to a {@link JavaSubstitution}.
 *
 * @implNote {@link com.oracle.truffle.espresso.libs.libjava.LibJava} is loaded very early, even
 *           before the guest java version of the guest is known. As such,
 *           {@link Substitution#languageFilter()} should not be checking java versions when used
 *           for libjava methods. Instead, use method overloading, and specify the signature mangle
 *           flag, using {@link Substitution#flags()} and
 *           {@link com.oracle.truffle.espresso.substitutions.SubstitutionFlag#needsSignatureMangle},
 *           like {@code @Substitution(flags = NeedsSignatureMangle.class)}
 */
public final class Lib implements TruffleObject {

    public interface Factory {
        String name();

        Lib create(EspressoContext ctx);
    }

    private final String name;

    public Lib(EspressoContext context, List<JavaSubstitution.Factory> collector, String name) {
        this.name = name;
        for (JavaSubstitution.Factory factory : collector) {
            if (factory.isValidFor(context.getLanguage())) {
                List<MethodKey> refs = getRefs(context, factory);
                for (MethodKey ref : refs) {
                    String key = Mangle.mangleMethod(
                                    ref.getHolderType(),
                                    ref.getName().toString(),
                                    factory.needsSignatureMangle()
                                                    ? ref.getSignature()
                                                    : null,
                                    false);
                    assert !bindings.containsKey(key);
                    context.getLogger().finer(() -> "Registering " + name() + " library entry: " + key);
                    bindings.put(key, factory);
                }
            }
        }
    }

    public String name() {
        return name;
    }

    /**
     * Lookup the implementation associated with the given {@link Mangle jni symbol}.
     */
    public SubstitutionFactoryWrapper find(String symbol) {
        JavaSubstitution.Factory factory = bindings.get(symbol);
        if (factory == null) {
            return null;
        }
        return new SubstitutionFactoryWrapper(factory);
    }

    private final Map<String, JavaSubstitution.Factory> bindings = new TreeMap<>() /*- Sorted map for debug purposes */;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<MethodKey> getRefs(EspressoContext ctx, JavaSubstitution.Factory factory) {
        List<Symbol<Type>> params = new ArrayList<>(factory.parameterTypes().length);
        for (String param : factory.parameterTypes()) {
            Symbol<Type> e = ctx.getTypes().fromDescriptorString(param);
            params.add(e);
        }
        Symbol<Type> rType = ctx.getTypes().fromDescriptorString(factory.returnType());
        Symbol<Signature> signature = ctx.getSignatures().makeRaw(rType, params.toArray(Symbol.EMPTY_ARRAY));

        int nRefs = factory.getMethodNames().length;
        List<MethodKey> refs = new ArrayList<>(nRefs);

        for (int i = 0; i < nRefs; i++) {
            String mName = factory.getMethodNames()[i];
            String holderName = factory.substitutionClassNames()[i];
            Symbol<Name> methodName = ctx.getNames().getOrCreate(mName);
            Symbol<Type> holderType = ctx.getTypes().fromClassGetName(holderName);

            refs.add(new MethodKey(holderType, methodName, signature, !factory.hasReceiver()));
        }

        return refs;
    }
}
