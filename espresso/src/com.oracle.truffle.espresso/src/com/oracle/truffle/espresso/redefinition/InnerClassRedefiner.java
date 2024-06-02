/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.redefinition;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.impl.ClassRegistry;
import com.oracle.truffle.espresso.impl.ConstantPoolPatcher;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.jdwp.api.ErrorCodes;
import com.oracle.truffle.espresso.jdwp.api.RedefineInfo;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public final class InnerClassRedefiner {
    private static final TruffleLogger LOGGER = TruffleLogger.getLogger(EspressoLanguage.ID, InnerClassRedefiner.class);

    public static final Pattern ANON_INNER_CLASS_PATTERN = Pattern.compile(".*\\$\\d+.*");
    public static final int METHOD_FINGERPRINT_EQUALS = 8;
    public static final int ENCLOSING_METHOD_FINGERPRINT_EQUALS = 4;
    public static final int FIELD_FINGERPRINT_EQUALS = 2;
    public static final int NUMBER_INNER_CLASSES = 1;
    public static final int MAX_SCORE = METHOD_FINGERPRINT_EQUALS + ENCLOSING_METHOD_FINGERPRINT_EQUALS + FIELD_FINGERPRINT_EQUALS + NUMBER_INNER_CLASSES;

    public static final String HOT_CLASS_MARKER = "$hot";

    private final EspressoContext context;

    // map from classloader to a map of class names to inner class infos
    private final Map<StaticObject, Map<Symbol<Name>, ImmutableClassInfo>> innerClassInfoMap = new WeakHashMap<>();

    // map from classloader to a map of Type to
    private final Map<StaticObject, Map<Symbol<Symbol.Type>, Set<ObjectKlass>>> innerKlassCache = new WeakHashMap<>();

    // list of class info for all top-level classed about to be redefined
    private final Map<Symbol<Name>, HotSwapClassInfo> hotswapState = new HashMap<>();

    public InnerClassRedefiner(EspressoContext context) {
        this.context = context;
    }

    public HotSwapClassInfo[] matchAnonymousInnerClasses(List<RedefineInfo> redefineInfos, List<ObjectKlass> removedInnerClasses) throws RedefinitionNotSupportedException {
        hotswapState.clear();
        ArrayList<RedefineInfo> unhandled = new ArrayList<>(redefineInfos);

        Map<Symbol<Name>, HotSwapClassInfo> handled = new HashMap<>(redefineInfos.size());
        // build inner/outer relationship from top-level to leaf class in order
        // each round below handles classes where the outer class was previously
        // handled
        int handledSize = 0;
        int previousHandledSize = -1;
        while (!unhandled.isEmpty() && handledSize > previousHandledSize) {
            Iterator<RedefineInfo> it = unhandled.iterator();
            while (it.hasNext()) {
                RedefineInfo redefineInfo = it.next();
                Symbol<Name> klassName = ClassfileParser.getClassName(context.getClassLoadingEnv(), redefineInfo.getClassBytes());
                if (handled.containsKey(klassName)) {
                    LOGGER.warning(() -> "Ignoring duplicate redefinition requests for name " + klassName);
                    it.remove();
                    continue;
                }
                Matcher matcher = ANON_INNER_CLASS_PATTERN.matcher(klassName.toString());
                if (matcher.matches()) {
                    // don't assume that associated old klass instance represents this redefineInfo
                    redefineInfo.clearKlass();
                    // anonymous inner class or nested named
                    // inner class of an anonymous inner class
                    // get the outer classinfo if present
                    HotSwapClassInfo info = handled.get(getOuterClassName(klassName));
                    if (info != null) {
                        HotSwapClassInfo classInfo = ClassInfo.create(klassName, redefineInfo.getClassBytes(), info.getClassLoader(), context, redefineInfo.isInnerTestKlass());
                        info.addInnerClass(classInfo);
                        handled.put(klassName, classInfo);
                        it.remove();
                    }
                } else {
                    // pure named class
                    it.remove();
                    if (redefineInfo.getKlass() != null) {
                        HotSwapClassInfo classInfo = ClassInfo.create(redefineInfo, context, redefineInfo.isInnerTestKlass());
                        handled.put(klassName, classInfo);
                        hotswapState.put(klassName, classInfo);
                    }
                }
            }
            previousHandledSize = handledSize;
            handledSize = handled.size();
        }

        // store renaming rules to be used for constant pool patching when class renaming happens
        Map<StaticObject, Map<Symbol<Name>, Symbol<Name>>> renamingRules = new HashMap<>(0);
        // begin matching from collected top-level classes
        for (HotSwapClassInfo info : hotswapState.values()) {
            matchClassInfo(info, removedInnerClasses, renamingRules);
        }

        // get the full list of changed classes
        ArrayList<HotSwapClassInfo> result = new ArrayList<>();
        collectAllHotswapClasses(hotswapState.values(), result);

        // now, do the constant pool patching
        for (HotSwapClassInfo classInfo : result) {
            if (classInfo.getBytes() != null) {
                Map<Symbol<Name>, Symbol<Name>> rules = renamingRules.get(classInfo.getClassLoader());
                if (rules != null && !rules.isEmpty()) {
                    try {
                        classInfo.patchBytes(ConstantPoolPatcher.patchConstantPool(classInfo.getBytes(), rules, context));
                    } catch (ClassFormatError ex) {
                        throw new RedefinitionNotSupportedException(ErrorCodes.INVALID_CLASS_FORMAT);
                    }
                }
            }
        }
        hotswapState.clear();
        return result.toArray(new HotSwapClassInfo[0]);
    }

    @SuppressWarnings("unchecked")
    private static void collectAllHotswapClasses(Collection<HotSwapClassInfo> infos, ArrayList<HotSwapClassInfo> result) {
        for (HotSwapClassInfo info : infos) {
            result.add(info);
            collectAllHotswapClasses((Collection<HotSwapClassInfo>) info.getInnerClasses(), result);
        }
    }

    private void fetchMissingInnerClasses(HotSwapClassInfo hotswapInfo) throws RedefinitionNotSupportedException {
        StaticObject definingLoader = hotswapInfo.getClassLoader();

        Set<Symbol<Name>> innerNames = new HashSet<>(1);
        try {
            searchConstantPoolForDirectInnerAnonymousClassNames(hotswapInfo, innerNames);
        } catch (ClassFormatError ex) {
            throw new RedefinitionNotSupportedException(ErrorCodes.INVALID_CLASS_FORMAT);
        }

        // poke the defining guest classloader for the resources
        for (Symbol<Name> innerName : innerNames) {
            if (!hotswapInfo.knowsInnerClass(innerName)) {
                byte[] classBytes = null;
                StaticObject resourceGuestString = context.getMeta().toGuestString(innerName + ".class");
                assert context.getCurrentPlatformThread() != null;
                StaticObject inputStream = (StaticObject) context.getMeta().java_lang_ClassLoader_getResourceAsStream.invokeDirect(definingLoader, resourceGuestString);
                if (StaticObject.notNull(inputStream)) {
                    classBytes = readAllBytes(inputStream);
                } else {
                    // There is no safe way to retrieve the class bytes using e.g. a scheme using
                    // j.l.ClassLoader#loadClass and special marker for the type to have the call
                    // end up in defineClass where we could grab the bytes. Guest language
                    // classloaders in many cases have caches for the class name that prevents
                    // forcefully attempting to load previously not loadable classes.
                    // without the class bytes, the matching is less precise and cached un-matched
                    // inner class instances that are executed after redefinition will lead to
                    // NoSuchMethod errors because they're marked as removed
                }
                if (classBytes != null) {
                    hotswapInfo.addInnerClass(ClassInfo.create(innerName, classBytes, definingLoader, context, false));
                }
            }
        }
    }

    private byte[] readAllBytes(StaticObject inputStream) {
        byte[] buf = new byte[4 * 0x400];
        StaticObject guestBuf = StaticObject.wrap(buf, context.getMeta());
        int readLen;

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            while ((readLen = (int) context.getMeta().java_io_InputStream_read.invokeDirect(inputStream, guestBuf, 0, buf.length)) != -1) {
                byte[] bytes = guestBuf.unwrap(context.getLanguage());
                outputStream.write(bytes, 0, readLen);
            }
            return outputStream.toByteArray();
        } catch (IOException ex) {
            return new byte[0];
        } finally {
            context.getMeta().java_io_InputStream_close.invokeDirect(inputStream);
        }
    }

    private void searchConstantPoolForDirectInnerAnonymousClassNames(ClassInfo classInfo, Set<Symbol<Name>> innerNames) throws ClassFormatError {
        byte[] bytes = classInfo.getBytes();
        assert bytes != null;

        ConstantPoolPatcher.getDirectInnerAnonymousClassNames(classInfo.getName(), bytes, innerNames, context);
    }

    private Symbol<Name> getOuterClassName(Symbol<Name> innerName) {
        String strName = innerName.toString();
        assert strName.contains("$");
        return context.getNames().getOrCreate(strName.substring(0, strName.lastIndexOf('$')));
    }

    private void matchClassInfo(HotSwapClassInfo hotSwapInfo, List<ObjectKlass> removedInnerClasses, Map<StaticObject, Map<Symbol<Name>, Symbol<Name>>> renamingRules)
                    throws RedefinitionNotSupportedException {
        Klass klass = hotSwapInfo.getKlass();
        // try to fetch all direct inner classes
        // based on the constant pool in the class bytes
        // by means of the defining class loader
        fetchMissingInnerClasses(hotSwapInfo);
        if (klass == null) {
            // non-mapped hotSwapInfo means it's a new inner class that didn't map
            // to any previous inner class
            // we need to generate a new unique name and apply to nested inner classes
            HotSwapClassInfo outerInfo = hotSwapInfo.getOuterClassInfo();
            String name = outerInfo.addHotClassMarker();
            hotSwapInfo.rename(context.getNames().getOrCreate(name));
            addRenamingRule(renamingRules, hotSwapInfo.getClassLoader(), hotSwapInfo.getName(), hotSwapInfo.getNewName(), context);
        } else {
            ImmutableClassInfo previousInfo = getGlobalClassInfo(klass);
            ArrayList<ImmutableClassInfo> previousInnerClasses = previousInfo.getImmutableInnerClasses();
            ArrayList<HotSwapClassInfo> newInnerClasses = hotSwapInfo.getHotSwapInnerClasses();

            // apply potential outer rename to inner class fingerprints
            // before matching
            if (hotSwapInfo.isRenamed()) {
                for (HotSwapClassInfo newInnerClass : newInnerClasses) {
                    newInnerClass.outerRenamed(hotSwapInfo.getName().toString(), hotSwapInfo.getNewName().toString());
                }
            }
            if (previousInnerClasses.size() > 0 || newInnerClasses.size() > 0) {
                ArrayList<ImmutableClassInfo> removedClasses = new ArrayList<>(previousInnerClasses);
                for (HotSwapClassInfo info : newInnerClasses) {
                    ImmutableClassInfo bestMatch = null;
                    int maxScore = 0;
                    for (ImmutableClassInfo removedClass : removedClasses) {
                        int score = info.match(removedClass);
                        if (score > 0 && score > maxScore) {
                            maxScore = score;
                            bestMatch = removedClass;
                            if (maxScore == MAX_SCORE) {
                                // found a perfect match, so stop iterating
                                break;
                            }
                        }
                    }
                    if (bestMatch != null) {
                        removedClasses.remove(bestMatch);
                        // rename class and associate with previous klass object
                        if (!info.getName().equals(bestMatch.getName())) {
                            info.rename(bestMatch.getName());
                            addRenamingRule(renamingRules, info.getClassLoader(), info.getName(), info.getNewName(), context);
                        }
                        info.setKlass(bestMatch.getKlass());
                    }
                }
                for (ImmutableClassInfo removedClass : removedClasses) {
                    if (removedClass.getKlass() != null) {
                        removedInnerClasses.add(removedClass.getKlass());
                    }
                }
            }
        }

        for (HotSwapClassInfo innerClass : hotSwapInfo.getHotSwapInnerClasses()) {
            matchClassInfo(innerClass, removedInnerClasses, renamingRules);
        }
    }

    private static void addRenamingRule(Map<StaticObject, Map<Symbol<Name>, Symbol<Name>>> renamingRules, StaticObject classLoader, Symbol<Name> originalName,
                    Symbol<Name> newName, EspressoContext context) {
        Map<Symbol<Name>, Symbol<Name>> classLoaderRules = renamingRules.computeIfAbsent(classLoader, k -> new HashMap<>(4));
        context.getClassRedefinition().getController().fine(() -> "Renaming inner class: " + originalName + " to: " + newName);
        assert classLoaderRules.getOrDefault(originalName, newName).equals(newName) : "rules already contain " + originalName + " -> " + classLoaderRules.get(originalName) + ", cannot map to " +
                        newName;
        // add simple class names
        classLoaderRules.put(originalName, newName);
        // add type names
        Symbol<Name> origTypeName = context.getNames().getOrCreate("L" + originalName + ";");
        Symbol<Name> newTypeName = context.getNames().getOrCreate("L" + newName + ";");
        assert classLoaderRules.getOrDefault(origTypeName, newTypeName).equals(newTypeName) : "rules already contain " + origTypeName + " -> " + classLoaderRules.get(origTypeName) +
                        ", cannot map to " + newTypeName;
        classLoaderRules.put(origTypeName, newTypeName);
        // add <init> signature names
        Symbol<Name> origSigName = context.getNames().getOrCreate("(L" + originalName + ";)V");
        Symbol<Name> newSigName = context.getNames().getOrCreate("(L" + newName + ";)V");
        assert classLoaderRules.getOrDefault(origSigName, newSigName).equals(newSigName) : "rules already contain " + origSigName + " -> " + classLoaderRules.get(origSigName) + ", cannot map to " +
                        newSigName;
        classLoaderRules.put(origSigName, newSigName);
    }

    public ImmutableClassInfo getGlobalClassInfo(Klass klass) {
        StaticObject classLoader = klass.getDefiningClassLoader();
        Map<Symbol<Name>, ImmutableClassInfo> infos = innerClassInfoMap.get(classLoader);

        if (infos == null) {
            infos = new HashMap<>(1);
            innerClassInfoMap.put(classLoader, infos);
        }

        ImmutableClassInfo result = infos.get(klass.getName());

        if (result == null) {
            result = ClassInfo.create(klass, this);
            infos.put(klass.getName(), result);
        }
        return result;
    }

    Set<ObjectKlass> findLoadedInnerClasses(Klass klass) {
        // we use a cache to store inner/outer class mappings
        Map<Symbol<Symbol.Type>, Set<ObjectKlass>> classLoaderMap = innerKlassCache.get(klass.getDefiningClassLoader());
        if (classLoaderMap == null) {
            classLoaderMap = new HashMap<>();
            // register a listener on the registry to fill in
            // future loaded anonymous inner classes
            ClassRegistry classRegistry = context.getRegistries().getClassRegistry(klass.getDefiningClassLoader());
            classRegistry.registerOnLoadListener(new DefineKlassListener() {
                @Override
                public void onKlassDefined(ObjectKlass objectKlass) {
                    InnerClassRedefiner.this.onKlassDefined(objectKlass);
                }
            });

            // do a one-time look up of all currently loaded
            // classes for this loader and fill in the map
            List<Klass> loadedKlasses = classRegistry.getLoadedKlasses();
            for (Klass loadedKlass : loadedKlasses) {
                if (loadedKlass instanceof ObjectKlass) {
                    ObjectKlass objectKlass = (ObjectKlass) loadedKlass;
                    Matcher matcher = ANON_INNER_CLASS_PATTERN.matcher(loadedKlass.getNameAsString());
                    if (matcher.matches()) {
                        Symbol<Name> outerClassName = getOuterClassName(loadedKlass.getName());
                        if (outerClassName != null && outerClassName.length() > 0) {
                            Symbol<Symbol.Type> outerType = context.getTypes().fromName(outerClassName);
                            Set<ObjectKlass> innerKlasses = classLoaderMap.get(outerType);
                            if (innerKlasses == null) {
                                innerKlasses = new HashSet<>(1);
                                classLoaderMap.put(outerType, innerKlasses);
                            }
                            innerKlasses.add(objectKlass);
                        }
                    }
                }
            }
            // add to cache
            innerKlassCache.put(klass.getDefiningClassLoader(), classLoaderMap);
        }
        Set<ObjectKlass> innerClasses = classLoaderMap.get(klass.getType());
        return innerClasses != null ? innerClasses : new HashSet<>(0);
    }

    private void onKlassDefined(ObjectKlass klass) {
        Matcher matcher = ANON_INNER_CLASS_PATTERN.matcher(klass.getNameAsString());

        if (matcher.matches()) {
            Map<Symbol<Symbol.Type>, Set<ObjectKlass>> classLoaderMap = innerKlassCache.get(klass.getDefiningClassLoader());
            // found inner class, now hunt down the outer
            Symbol<Name> outerName = getOuterClassName(klass.getName());
            Symbol<Symbol.Type> outerType = context.getTypes().fromName(outerName);

            Set<ObjectKlass> innerKlasses = classLoaderMap.get(outerType);
            if (innerKlasses == null) {
                innerKlasses = new HashSet<>(1);
                classLoaderMap.put(outerType, innerKlasses);
            }
            innerKlasses.add(klass);
        }
    }

    public void commit(HotSwapClassInfo[] infos) {
        // first remove the previous info
        for (HotSwapClassInfo info : infos) {
            StaticObject classLoader = info.getClassLoader();
            Map<Symbol<Name>, ImmutableClassInfo> classLoaderMap = innerClassInfoMap.get(classLoader);
            if (classLoaderMap != null) {
                classLoaderMap.remove(info.getNewName());
            }
        }
        for (HotSwapClassInfo hotSwapInfo : infos) {
            StaticObject classLoader = hotSwapInfo.getClassLoader();
            Map<Symbol<Name>, ImmutableClassInfo> classLoaderMap = innerClassInfoMap.get(classLoader);
            if (classLoaderMap == null) {
                classLoaderMap = new HashMap<>(1);
                innerClassInfoMap.put(classLoader, classLoaderMap);
            }
            // update the cache with the new class info
            classLoaderMap.put(hotSwapInfo.getName(), ClassInfo.copyFrom(hotSwapInfo));
        }
    }
}
