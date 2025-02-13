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
package com.oracle.truffle.espresso.substitutions;

import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.ClasspathEntry;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Signatures;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.Classpath;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

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
        context.getJavaAgents().setHasTransformer((int) agentId, has);
    }

    @Substitution(hasReceiver = true)
    public static void setHasRetransformableTransformers(
                    @SuppressWarnings("unused") StaticObject self,
                    @SuppressWarnings("unused") long agentId,
                    @SuppressWarnings("unused") boolean has,
                    @Inject EspressoContext context) {
        throw context.getMeta().throwExceptionWithMessage(context.getMeta().java_lang_UnsupportedOperationException,
                        "Espresso VM doesn't support retransformable agents. This restriction will be lifted in later versions.");
    }

    @Substitution(hasReceiver = true)
    public static boolean isRetransformClassesSupported0(
                    @SuppressWarnings("unused") StaticObject self,
                    @SuppressWarnings("unused") long agentId) {
        return false;
    }

    @Substitution(hasReceiver = true)
    public static void redefineClasses0(
                    @SuppressWarnings("unused") StaticObject self,
                    @SuppressWarnings("unused") long agentId,
                    @JavaType(internalName = "[Ljava/lang/instrument/ClassDefinition;") StaticObject mirrorClasses,
                    @Inject EspressoContext context) {
        assert StaticObject.notNull(mirrorClasses);
        throw context.getMeta().throwExceptionWithMessage(context.getMeta().java_lang_UnsupportedOperationException,
                        "Espresso VM doesn't support redefining classes from java agents. This restriction will be lifted in later versions.");
    }

    @Substitution(hasReceiver = true)
    public static void retransformClasses0(
                    @SuppressWarnings("unused") StaticObject self,
                    @SuppressWarnings("unused") long agentId,
                    @JavaType(Class[].class) StaticObject mirrorClasses,
                    @Inject EspressoContext context) {
        assert StaticObject.notNull(mirrorClasses);
        throw context.getMeta().throwExceptionWithMessage(context.getMeta().java_lang_UnsupportedOperationException,
                        "Espresso VM doesn't support retransforming classes from java agents. This restriction will be lifted in later versions.");
    }

    @Substitution(hasReceiver = true)
    public static boolean isModifiableClass0(
                    @SuppressWarnings("unused") StaticObject self,
                    @SuppressWarnings("unused") long agentId,
                    @JavaType(Class.class) StaticObject mirrorClass) {
        assert StaticObject.notNull(mirrorClass);
        return false;
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
        List<Klass> initiatedKlasses = context.getRegistries().getClassRegistry(loader).getLoadedKlasses();
        return toGuestClassArray(context, initiatedKlasses);
    }

    @TruffleBoundary
    private static StaticObject toGuestClassArray(EspressoContext context, List<Klass> initiatedKlasses) {
        StaticObject[] guestKlasses = new StaticObject[initiatedKlasses.size()];
        for (int i = 0; i < initiatedKlasses.size(); i++) {
            guestKlasses[i] = initiatedKlasses.get(i).mirror();
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

    @Substitution(hasReceiver = true)
    public static void setNativeMethodPrefixes(
                    @SuppressWarnings("unused") StaticObject self,
                    @SuppressWarnings("unused") long agentId,
                    @SuppressWarnings("unused") @JavaType(String[].class) StaticObject prefixes,
                    @SuppressWarnings("unused") boolean isRetransformable,
                    @Inject EspressoContext context) {
        throw context.getMeta().throwExceptionWithMessage(context.getMeta().java_lang_UnsupportedOperationException,
                        "Espresso VM doesn't support setting native method prefix from java agents. This restriction will be lifted in later versions.");
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
                throw context.getMeta().throwExceptionWithMessage(context.getMeta().java_lang_IllegalArgumentException, notValidJarMessage(jarFile, context));
            }
            context.appendBootClasspath(entry);
        }
    }

    @TruffleBoundary
    private static String notValidJarMessage(StaticObject jarFile, EspressoContext context) {
        return context.getMeta().toHostString(jarFile) + " is not a valid jar!";
    }

    @Substitution()
    public static void loadAgent0(
                    @JavaType(String.class) StaticObject jarPath,
                    @Inject EspressoContext context) {

        throw context.getMeta().throwExceptionWithMessage(context.getMeta().java_lang_UnsupportedOperationException, noLauncherAgentClassSupport(jarPath, context));
    }

    @TruffleBoundary
    private static String noLauncherAgentClassSupport(StaticObject jarFile, EspressoContext context) {
        return context.getMeta().toHostString(jarFile) + " will not be launched!. " +
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
