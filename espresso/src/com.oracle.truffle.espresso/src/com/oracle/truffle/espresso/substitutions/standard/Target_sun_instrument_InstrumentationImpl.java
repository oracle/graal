/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions.standard;

import java.util.ArrayList;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.ClasspathEntry;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Signatures;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.jdwp.api.RedefineInfo;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.redefinition.RedefinitionException;
import com.oracle.truffle.espresso.runtime.Classpath;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;

@EspressoSubstitutions
public final class Target_sun_instrument_InstrumentationImpl {

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject jarFile(
                    @SuppressWarnings("unused") StaticObject self,
                    long agentId, @Inject EspressoContext context) {
        return context.getMeta().toGuestString(context.getJavaAgents().jarFile((int) agentId));
    }

    @Substitution(hasReceiver = true)
    public static void setHasTransformers(
                    @SuppressWarnings("unused") StaticObject self,
                    long agentId,
                    boolean has,
                    @Inject EspressoContext context) {
        context.getJavaAgents().setHasTransformers((int) agentId, has);
    }

    @Substitution(hasReceiver = true)
    public static void setHasRetransformableTransformers(
                    @SuppressWarnings("unused") StaticObject self,
                    long agentId,
                    boolean has,
                    @Inject EspressoContext context) {
        context.getJavaAgents().setHasRetransformers((int) agentId, has);
    }

    @Substitution(hasReceiver = true)
    public static boolean isRetransformClassesSupported0(
                    @SuppressWarnings("unused") StaticObject self,
                    long agentId,
                    @Inject EspressoContext context) {
        return context.getJavaAgents().isRetransformSupported((int) agentId);
    }

    @Substitution(hasReceiver = true)
    public static void redefineClasses0(
                    @SuppressWarnings("unused") StaticObject self,
                    @SuppressWarnings("unused") long agentId,
                    @JavaType(internalName = "[Ljava/lang/instrument/ClassDefinition;") StaticObject classDefinitions,
                    @Inject EspressoContext context) {
        assert StaticObject.notNull(classDefinitions);
        // translate the passed class definitions to our internal RedefineInfo objects
        RedefineInfo[] redefineInfos = new RedefineInfo[classDefinitions.length(context.getLanguage())];
        for (int i = 0; i < redefineInfos.length; i++) {
            redefineInfos[i] = translateClassDefinition(context.getMeta(), classDefinitions.get(context.getLanguage(), i));
        }
        try {
            context.getClassRedefinition().redefineClasses(redefineInfos, true);
        } catch (RedefinitionException e) {
            throw e.throwInstrumentationGuestException(context.getMeta());
        }
    }

    private static RedefineInfo translateClassDefinition(Meta meta, @JavaType(internalName = "Ljava/lang/instrument/ClassDefinition;") StaticObject classDefinition) {
        StaticObject guestClass = (StaticObject) meta.java_lang_instrument_ClassDefinition_getDefinitionClass.invokeDirectVirtual(classDefinition);
        if (StaticObject.isNull(guestClass)) {
            throw meta.throwException(meta.java_lang_NullPointerException);
        }
        Klass klass = guestClass.getMirrorKlass(meta);
        if (klass.isArray() || klass.isPrimitive()) {
            throw meta.throwExceptionWithMessage(meta.java_lang_instrument_UnmodifiableClassException, getUnmodifiableMessage(klass));
        }
        StaticObject guestBytes = (StaticObject) meta.java_lang_instrument_ClassDefinition_getDefinitionClassFile.invokeDirectVirtual(classDefinition);
        if (StaticObject.isNull(guestBytes)) {
            throw meta.throwExceptionWithMessage(meta.java_lang_NullPointerException, "byte array for class being redefined is null");
        }
        byte[] hostBytes = guestBytes.unwrap(meta.getLanguage());
        return new RedefineInfo(klass, hostBytes);
    }

    @TruffleBoundary
    private static String getUnmodifiableMessage(Klass klass) {
        return "class " + klass.getName() + " is not modifiable";
    }

    @Substitution(hasReceiver = true)
    public static void retransformClasses0(
                    @SuppressWarnings("unused") StaticObject self,
                    @SuppressWarnings("unused") long agentId,
                    @JavaType(Class[].class) StaticObject mirrorClasses,
                    @Inject EspressoContext context,
                    @Inject EspressoLanguage language) {
        Meta meta = context.getMeta();
        if (StaticObject.isNull(mirrorClasses)) {
            throw meta.throwExceptionWithMessage(meta.java_lang_NullPointerException, "classes array is null");
        }
        Klass[] klasses = new Klass[mirrorClasses.length(language)];
        for (int i = 0; i < klasses.length; i++) {
            StaticObject guestClass = mirrorClasses.get(language, i);
            if (StaticObject.isNull(guestClass)) {
                throw meta.throwExceptionWithMessage(meta.java_lang_NullPointerException, "class in array is null");
            }
            klasses[i] = guestClass.getMirrorKlass(meta);
            if (klasses[i].isArray() || klasses[i].isPrimitive()) {
                throw meta.throwExceptionWithMessage(meta.java_lang_instrument_UnmodifiableClassException, getUnmodifiableMessage(klasses[i]));
            }
        }
        try {
            context.getJavaAgents().retransformClasses(klasses);
        } catch (RedefinitionException e) {
            throw e.throwInstrumentationGuestException(context.getMeta());
        }
    }

    @Substitution(hasReceiver = true)
    public static boolean isModifiableClass0(
                    @SuppressWarnings("unused") StaticObject self,
                    @SuppressWarnings("unused") long agentId,
                    @JavaType(Class.class) StaticObject guestClass) {
        assert StaticObject.notNull(guestClass);
        Klass klass = guestClass.getMirrorKlass();
        return !klass.isArray() && !klass.isPrimitive();
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class[].class) StaticObject getAllLoadedClasses0(
                    @SuppressWarnings("unused") StaticObject self,
                    @SuppressWarnings("unused") long agentId,
                    @Inject EspressoContext context) {
        return toGuestClassArray(context, context.getRegistries().getAllLoadedClasses());
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class[].class) StaticObject getInitiatedClasses0(
                    @SuppressWarnings("unused") StaticObject self,
                    @SuppressWarnings("unused") long agentId,
                    @JavaType(ClassLoader.class) StaticObject loader,
                    @Inject EspressoContext context) {
        Set<Klass> initiatedKlasses = context.getRegistries().getLoadedClassesByLoader(loader, false);
        return toGuestClassArray(context, initiatedKlasses);
    }

    @TruffleBoundary
    private static StaticObject toGuestClassArray(EspressoContext context, Set<Klass> initiatedKlasses) {
        StaticObject[] guestKlasses = new StaticObject[initiatedKlasses.size()];
        int i = 0;
        for (Klass initiatedKlass : initiatedKlasses) {
            guestKlasses[i++] = initiatedKlass.mirror();
        }
        return StaticObject.wrap(guestKlasses, context.getMeta());
    }

    @Substitution(hasReceiver = true)
    public static long getObjectSize0(
                    @SuppressWarnings("unused") StaticObject self,
                    @SuppressWarnings("unused") long agentId,
                    @JavaType(Object.class) StaticObject object,
                    @Inject EspressoLanguage language) {
        return object.getObjectSize(language);
    }

    @TruffleBoundary
    @Substitution(hasReceiver = true)
    @SuppressWarnings("unchecked")
    public static void setNativeMethodPrefixes(
                    @SuppressWarnings("unused") StaticObject self,
                    long agentId,
                    @JavaType(String[].class) StaticObject guestPrefixes,
                    @SuppressWarnings("unused") boolean isRetransformable,
                    @Inject EspressoContext context) {
        // when this method is called, we know that the agent in question can set native prefixes
        ArrayList<Symbol<Name>> prefixes = new ArrayList<>();
        for (int i = 0; i < guestPrefixes.length(context.getLanguage()); i++) {
            StaticObject guestString = guestPrefixes.get(context.getLanguage(), i);
            if (StaticObject.notNull(guestString)) {
                String hostString = context.getMeta().toHostString(guestString);
                prefixes.add(context.getNames().getOrCreate(hostString));
            }
        }
        context.getJavaAgents().setNativePrefixes((int) agentId, prefixes.toArray(Symbol.EMPTY_ARRAY));
    }

    @Substitution(hasReceiver = true)
    public static void appendToClassLoaderSearch0(
                    @SuppressWarnings("unused") StaticObject self,
                    @SuppressWarnings("unused") long agentId,
                    @JavaType(String.class) StaticObject jarFile,
                    boolean isBootLoader,
                    @Inject EspressoContext context) {
        assert StaticObject.notNull(jarFile);
        Meta meta = context.getMeta();
        if (!isBootLoader) {
            appendPathToSystemClassLoader(jarFile, meta);
        } else {
            ClasspathEntry entry = Classpath.createEntry(meta.toHostString(jarFile));
            if (!entry.isArchive()) {
                // we only accept valid archives here so throw IllegalArgumentException
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, notValidJarMessage(jarFile, meta));
            }
            context.appendBootClasspath(entry);
        }
    }

    @TruffleBoundary
    private static String notValidJarMessage(StaticObject jarFile, Meta meta) {
        return meta.toHostString(jarFile) + " is not a valid jar!";
    }

    @Substitution()
    public static void loadAgent0(
                    @JavaType(String.class) StaticObject jarPath,
                    @Inject EspressoContext context) {
        Meta meta = context.getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_UnsupportedOperationException, noLauncherAgentClassSupport(jarPath, meta));
    }

    @TruffleBoundary
    private static String noLauncherAgentClassSupport(StaticObject jarFile, Meta meta) {
        return meta.toHostString(jarFile) + " will not be launched!. " +
                        "Espresso doesn't support launching Java agents after the VM has started. " +
                        "Launcher-Agent-Class attribute in the agent manifest is ignored. " +
                        "This restriction will be lifted in later versions.";
    }

    public static void appendPathToSystemClassLoader(@JavaType(String.class) StaticObject jarFile, Meta meta) {
        // get the system class loader instance, lookup the special append method and invoke it if
        // declared
        StaticObject systemClassLoader = (StaticObject) meta.java_lang_ClassLoader_getSystemClassLoader.invokeDirectStatic();
        Method appendMethod = systemClassLoader.getKlass().lookupDeclaredMethod(Names.appendToClassPathForInstrumentation, Signatures._void_String);
        if (appendMethod != null) {
            appendMethod.invokeDirectVirtual(systemClassLoader, jarFile);
        } else {
            throw meta.throwExceptionWithMessage(meta.java_lang_UnsupportedOperationException, "system class loader does not support a jar file to be searched");
        }
    }
}
