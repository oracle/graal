/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.classfile.ParserException;
import com.oracle.truffle.espresso.classfile.ParserField;
import com.oracle.truffle.espresso.classfile.ParserKlass;
import com.oracle.truffle.espresso.classfile.ParserMethod;
import com.oracle.truffle.espresso.classfile.attributes.Attribute;
import com.oracle.truffle.espresso.classfile.attributes.CodeAttribute;
import com.oracle.truffle.espresso.classfile.attributes.ConstantValueAttribute;
import com.oracle.truffle.espresso.classfile.attributes.LineNumberTableAttribute;
import com.oracle.truffle.espresso.classfile.attributes.Local;
import com.oracle.truffle.espresso.classfile.attributes.LocalVariableTable;
import com.oracle.truffle.espresso.classfile.attributes.NestHostAttribute;
import com.oracle.truffle.espresso.classfile.attributes.NestMembersAttribute;
import com.oracle.truffle.espresso.classfile.attributes.PermittedSubclassesAttribute;
import com.oracle.truffle.espresso.classfile.attributes.RecordAttribute;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.classfile.bytecode.Bytecodes;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.ValidationException;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Signatures;
import com.oracle.truffle.espresso.impl.ClassRegistry;
import com.oracle.truffle.espresso.impl.EspressoClassLoadingException;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.RedefineAddedField;
import com.oracle.truffle.espresso.jdwp.api.RedefineInfo;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.preinit.ParserKlassProvider;
import com.oracle.truffle.espresso.redefinition.RedefinitionException.RedefinitionError;
import com.oracle.truffle.espresso.redefinition.plugins.impl.RedefinitionPluginHandler;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoThreadLocalState;
import com.oracle.truffle.espresso.runtime.JDWPContextImpl;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

public final class ClassRedefinition {
    public static final TruffleLogger LOGGER = TruffleLogger.getLogger(EspressoLanguage.ID, ClassRedefinition.class);
    private final Object redefineLock = new Object();
    private volatile boolean locked = false;
    private Thread redefineThread = null;
    private final EspressoContext context;
    private RedefinitionPluginHandler redefinitionPluginHandler;
    private final InnerClassRedefiner innerClassRedefiner;
    private final ArrayList<ReloadingAction> classInitializerActions = new ArrayList<>(1);
    private volatile Assumption missingFieldAssumption = Truffle.getRuntime().createAssumption();
    private ArrayList<Field> currentDelegationFields;

    public Assumption getMissingFieldAssumption() {
        return missingFieldAssumption;
    }

    public void invalidateMissingFields() {
        missingFieldAssumption.invalidate();
        missingFieldAssumption = Truffle.getRuntime().createAssumption();
    }

    public enum ClassChange {
        // currently supported
        NO_CHANGE,
        CONSTANT_POOL_CHANGE,
        METHOD_BODY_CHANGE,
        CLASS_NAME_CHANGED,
        ADD_METHOD,
        REMOVE_METHOD,
        NEW_CLASS,
        SCHEMA_CHANGE,
        CLASS_HIERARCHY_CHANGED
    }

    @TruffleBoundary
    public ClassRedefinition(EspressoContext context) {
        this.context = context;
        this.redefinitionPluginHandler = RedefinitionPluginHandler.create(context);
        this.innerClassRedefiner = new InnerClassRedefiner(context);
    }

    public void begin() {
        synchronized (redefineLock) {
            while (locked) {
                try {
                    redefineLock.wait();
                } catch (InterruptedException e) {
                    Thread.interrupted();
                }
            }
            // the redefine thread is privileged
            redefineThread = Thread.currentThread();
            locked = true;
        }
    }

    public void end() {
        synchronized (redefineLock) {
            locked = false;
            redefineThread = null;
            redefineLock.notifyAll();
        }
    }

    public boolean isRedefineThread() {
        return redefineThread == Thread.currentThread();
    }

    public void addExtraReloadClasses(List<RedefineInfo> redefineInfos, List<RedefineInfo> additional) {
        redefinitionPluginHandler.collectExtraClassesToReload(redefineInfos, additional);
    }

    public void runPostRedefinitionListeners(ObjectKlass[] changedKlasses) {
        redefinitionPluginHandler.postRedefinition(changedKlasses);
    }

    public void check() {
        CompilerAsserts.neverPartOfCompilation();
        // block until redefinition is done
        if (locked) {
            if (redefineThread == Thread.currentThread()) {
                // let the redefine thread pass
                return;
            }
            synchronized (redefineLock) {
                while (locked) {
                    try {
                        redefineLock.wait();
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                    }
                }
            }
        }
    }

    public synchronized Field createDelegationFrom(Field field) {
        Field delegationField = RedefineAddedField.createDelegationField(field);
        if (currentDelegationFields == null) {
            currentDelegationFields = new ArrayList<>(1);
        }
        currentDelegationFields.add(delegationField);
        return delegationField;
    }

    public synchronized void clearDelegationFields() {
        if (currentDelegationFields != null) {
            for (Field field : currentDelegationFields) {
                field.removeByRedefinition();
            }
            currentDelegationFields.clear();
        }
    }

    public List<ChangePacket> detectClassChanges(HotSwapClassInfo[] classInfos, boolean jvmtiRestrictions) throws RedefinitionException {
        List<ChangePacket> result = new ArrayList<>(classInfos.length);
        EconomicMap<ObjectKlass, ChangePacket> temp = EconomicMap.create(1);
        EconomicSet<ObjectKlass> superClassChanges = EconomicSet.create(1);
        for (HotSwapClassInfo hotSwapInfo : classInfos) {
            ObjectKlass klass = hotSwapInfo.getKlass();
            if (klass == null) {
                // New anonymous inner class
                result.add(new ChangePacket(hotSwapInfo, ClassChange.NEW_CLASS));
                continue;
            }
            byte[] bytes = hotSwapInfo.getBytes();
            ParserKlass parserKlass;
            ParserKlass newParserKlass = null;
            ClassChange classChange;
            DetectedChange detectedChange = new DetectedChange();
            StaticObject loader = klass.getDefiningClassLoader();
            TypeSymbols typeSymbols = klass.getContext().getTypes();
            try {
                parserKlass = ParserKlassProvider.parseKlassWithHostErrors(ClassRegistry.ClassDefinitionInfo.EMPTY, context.getClassLoadingEnv(), loader,
                                typeSymbols.fromClassNameEntry(hotSwapInfo.getName()), bytes);
                if (hotSwapInfo.isPatched()) {
                    byte[] patched = hotSwapInfo.getPatchedBytes();
                    newParserKlass = parserKlass;
                    // we detect changes against the patched bytecode
                    parserKlass = ParserKlassProvider.parseKlassWithHostErrors(ClassRegistry.ClassDefinitionInfo.EMPTY, context.getClassLoadingEnv(), loader,
                                    typeSymbols.fromClassNameEntry(hotSwapInfo.getNewName()),
                                    patched);
                }
            } catch (ValidationException | ParserException.ClassFormatError validationOrBadFormat) {
                throw new RedefinitionException(RedefinitionError.InvalidClassFormat, validationOrBadFormat.getMessage());
            } catch (ParserException.UnsupportedClassVersionError unsupportedClassVersionError) {
                throw new RedefinitionException(RedefinitionError.UnsupportedVersion, unsupportedClassVersionError.getMessage());
            } catch (ParserException.NoClassDefFoundError noClassDefFoundError) {
                // see HotSpot VM_RedefineClasses::load_new_class_versions
                throw new RedefinitionException(RedefinitionError.NamesDontMatch, noClassDefFoundError.getMessage());
            } catch (ParserException parserException) {
                throw EspressoError.shouldNotReachHere("Not a validation nor parser exception", parserException);
            }
            classChange = detectClassChanges(parserKlass, klass, detectedChange, newParserKlass, jvmtiRestrictions);
            if (classChange == ClassChange.CLASS_HIERARCHY_CHANGED && detectedChange.getSuperKlass() != null) {
                // keep track of unhandled changed super classes
                ObjectKlass superKlass = detectedChange.getSuperKlass();
                ObjectKlass oldSuperKlass = klass.getSuperKlass();
                ObjectKlass commonSuperKlass = (ObjectKlass) oldSuperKlass.findLeastCommonAncestor(superKlass);
                while (superKlass != commonSuperKlass) {
                    superClassChanges.add(superKlass);
                    superKlass = superKlass.getSuperKlass();
                }
            }
            ChangePacket packet = new ChangePacket(hotSwapInfo, newParserKlass != null ? newParserKlass : parserKlass, classChange, detectedChange);
            result.add(packet);
            temp.put(klass, packet);
        }
        // add superclass change information to result
        for (ObjectKlass superKlass : superClassChanges) {
            ChangePacket packet = temp.get(superKlass);
            if (packet != null) {
                // update changed super klass
                packet.detectedChange.markChangedSuperClass();
            } else {
                // create new packet to signal a subclass was changed but the superclass didn't
                DetectedChange change = new DetectedChange();
                change.markChangedSuperClass();
                packet = new ChangePacket(HotSwapClassInfo.createForSuperClassChanged(superKlass), null, ClassChange.CLASS_HIERARCHY_CHANGED, change);
                result.add(packet);
            }
        }
        return result;
    }

    @TruffleBoundary
    public void redefineClasses(RedefineInfo[] redefineInfos, boolean applyTransformers) throws RedefinitionException {
        redefineClasses(Arrays.asList(redefineInfos), applyTransformers);
    }

    public void redefineClasses(List<RedefineInfo> redefineInfos, boolean applyTransformers) throws RedefinitionException {
        redefineClasses(redefineInfos, !context.advancedRedefinitionEnabled(), applyTransformers);
    }

    private synchronized void redefineClasses(List<RedefineInfo> redefineInfos, boolean jvmtiRestrictions, boolean applyTransformers) throws RedefinitionException {
        List<RedefineInfo> resultingInfos = applyTransformers ? getTransformedInfos(redefineInfos) : redefineInfos;

        // make sure the modules of redefined classes can read injected agent classes
        if (context.getJavaAgents() != null) {
            resultingInfos.forEach((redefineInfo) -> context.getJavaAgents().grantReadAccessToUnnamedModules(((Klass) redefineInfo.getKlass()).module()));
        }

        // list to collect all changed classes
        List<ObjectKlass> changedKlasses = new ArrayList<>(resultingInfos.size());
        try {
            context.getLogger().fine(() -> "Redefining " + redefineInfos.size() + " classes");

            // begin redefine transaction
            begin();

            // clear synthetic fields, which forces re-resolution
            clearDelegationFields();

            // invalidate missing fields assumption, which forces re-resolution
            invalidateMissingFields();

            // redefine classes based on direct code changes first
            doRedefine(resultingInfos, changedKlasses, jvmtiRestrictions);

            // Now, collect additional classes to redefine in response
            // to the redefined classes above
            List<RedefineInfo> additional = Collections.synchronizedList(new ArrayList<>());
            addExtraReloadClasses(resultingInfos, additional);
            // redefine additional classes now
            doRedefine(additional, changedKlasses, jvmtiRestrictions);

            // re-run all registered class initializers before ending transaction
            classInitializerActions.forEach((reloadingAction) -> {
                try {
                    reloadingAction.fire();
                } catch (Throwable t) {
                    // Some anomalies when rerunning class initializers
                    // to be expected. Treat them as non-fatal.
                    context.getLogger().warning(() -> "exception while re-running a class initializer!");
                }
            });
            assert !changedKlasses.contains(null);
            // run post redefinition plugins before ending the redefinition transaction
            try {
                runPostRedefinitionListeners(changedKlasses.toArray(new ObjectKlass[changedKlasses.size()]));
            } catch (Throwable t) {
                context.getLogger().severe(() -> JDWPContextImpl.class.getName() + ": redefineClasses: " + t.getMessage());
            }
        } finally {
            end();
        }
    }

    @SuppressWarnings("try")
    private List<RedefineInfo> getTransformedInfos(List<RedefineInfo> redefineInfos) {
        List<RedefineInfo> transformedInfos = redefineInfos;
        if (context.getJavaAgents() != null && context.getJavaAgents().hasTransformers()) {
            // make sure bytes are transformed before performing the redefinition
            EspressoThreadLocalState tls = context.getLanguage().getThreadLocalState();
            if (!tls.isInTransformer()) {
                transformedInfos = new ArrayList<>(redefineInfos.size());
                try (EspressoThreadLocalState.TransformerScope transformerScope = tls.transformerScope()) {
                    for (RedefineInfo redefineInfo : redefineInfos) {
                        Klass klass = (Klass) redefineInfo.getKlass();
                        byte[] transformed = context.getJavaAgents().transformClass(klass, redefineInfo.getClassBytes());
                        transformedInfos.add(new RedefineInfo(klass, transformed));
                    }
                }
            }
        }
        return transformedInfos;
    }

    private void doRedefine(List<RedefineInfo> redefineInfos, List<ObjectKlass> changedKlasses, boolean jvmtiRestrictions) throws RedefinitionException {
        // list to hold removed inner classes that must be marked removed
        List<ObjectKlass> removedInnerClasses = new ArrayList<>(0);
        // list of classes that need to refresh due to
        // changes in other classes for things like vtable
        List<ObjectKlass> invalidatedClasses = new ArrayList<>();
        // list of all classes that have been redefined within this transaction
        List<ObjectKlass> redefinedClasses = new ArrayList<>();

        // match anon inner classes with previous state
        HotSwapClassInfo[] matchedInfos = innerClassRedefiner.matchAnonymousInnerClasses(redefineInfos, removedInnerClasses);

        // detect all changes to all classes, throws if redefinition cannot be completed
        // due to the nature of the changes
        List<ChangePacket> changePackets = detectClassChanges(matchedInfos, jvmtiRestrictions);

        // We have to redefine super classes prior to subclasses
        Collections.sort(changePackets, new HierarchyComparator());

        for (ChangePacket packet : changePackets) {
            context.getLogger().fine(() -> "Redefining class " + packet.info.getNewName());
            redefineClass(packet, invalidatedClasses, redefinedClasses);
        }

        // refresh invalidated classes if not already redefined
        Collections.sort(invalidatedClasses, new SubClassHierarchyComparator());
        for (ObjectKlass invalidatedClass : invalidatedClasses) {
            if (!redefinedClasses.contains(invalidatedClass)) {
                context.getLogger().fine(() -> "Refreshing invalidated class " + invalidatedClass.getName());
                invalidatedClass.swapKlassVersion();
            }
        }

        // include invalidated classes in all changed classes list
        changedKlasses.addAll(invalidatedClasses);

        for (ChangePacket changePacket : changePackets) {
            ObjectKlass klass = changePacket.info.getKlass();
            if (klass != null) {
                changedKlasses.add(klass);
            }
        }

        // tell the InnerClassRedefiner to commit the changes to cache
        innerClassRedefiner.commit(matchedInfos);

        for (ObjectKlass removed : removedInnerClasses) {
            removed.removeByRedefinition();
        }
    }

    public void redefineClass(ChangePacket packet, List<ObjectKlass> invalidatedClasses, List<ObjectKlass> redefinedClasses) throws RedefinitionException {
        try {
            switch (packet.classChange) {
                case METHOD_BODY_CHANGE:
                case CONSTANT_POOL_CHANGE:
                case CLASS_NAME_CHANGED:
                case ADD_METHOD:
                case REMOVE_METHOD:
                case SCHEMA_CHANGE:
                    doRedefineClass(packet, invalidatedClasses, redefinedClasses);
                    return;
                case CLASS_HIERARCHY_CHANGED:
                    context.markChangedHierarchy();
                    doRedefineClass(packet, invalidatedClasses, redefinedClasses);
                    return;
                case NEW_CLASS:
                    ClassInfo classInfo = packet.info;

                    // if there is a currently loaded class under that name
                    // we have to replace that in the class loader registry etc.
                    // otherwise, don't eagerly define the new class
                    Symbol<Type> type = context.getTypes().fromClassNameEntry(classInfo.getName());
                    ClassRegistry classRegistry = context.getRegistries().getClassRegistry(classInfo.getClassLoader());
                    Klass loadedKlass = classRegistry.findLoadedKlass(context.getClassLoadingEnv(), type);
                    if (loadedKlass != null) {
                        // OK, we have to define the new klass instance and
                        // inject it under the existing JDWP ID
                        classRegistry.onInnerClassRemoved(type);
                        ObjectKlass newKlass = classRegistry.defineKlass(context, type, classInfo.getBytes());
                        assert newKlass != loadedKlass && newKlass == classRegistry.findLoadedKlass(context.getClassLoadingEnv(), type);

                        packet.info.setKlass(newKlass);
                    } else if (classInfo.isNewInnerTestKlass()) {
                        // New inner test classes cannot be loaded because they'll
                        // have a versioned name on disk, so let's define them directly
                        classRegistry.defineKlass(context, type, classInfo.getBytes());
                    }
                    return;
            }
        } catch (EspressoException ex) {
            // TODO(Gregersen) - return appropriate error code based on the exception type
            // we get from parsing the class file
            throw new RedefinitionException(RedefinitionError.InvalidClassFormat);
        } catch (EspressoClassLoadingException.ClassCircularityError e) {
            throw new RedefinitionException(RedefinitionError.CircularClassDefinition);
        } catch (EspressoClassLoadingException e) {
            throw new RedefinitionException(RedefinitionError.FailsVerification, e.getMessage());
        }
    }

    // detect all types of class changes, but return early when a change that require arbitrary
    // changes
    private static ClassChange detectClassChanges(ParserKlass newParserKlass, ObjectKlass oldKlass, DetectedChange collectedChanges, ParserKlass finalParserKlass, boolean jvmtiRestrictions)
                    throws RedefinitionException {
        if (oldKlass.getSuperKlass() == oldKlass.getMeta().java_lang_Enum) {
            detectInvalidEnumConstantChanges(newParserKlass, oldKlass);
        }
        ConstantPool oldConstantPool = oldKlass.getConstantPool();
        ConstantPool newConstantPool = newParserKlass.getConstantPool();
        // detect invalid attribute changes for jvmti restrictions
        if (jvmtiRestrictions) {
            if (attrChanged(oldKlass.getAttribute(NestHostAttribute.NAME), newParserKlass.getAttribute(NestHostAttribute.NAME), oldConstantPool, newConstantPool)) {
                throw new RedefinitionException(RedefinitionError.ClassAttributeChanged);
            }
            if (attrChanged(oldKlass.getAttribute(NestMembersAttribute.NAME), newParserKlass.getAttribute(NestMembersAttribute.NAME), oldConstantPool, newConstantPool)) {
                throw new RedefinitionException(RedefinitionError.ClassAttributeChanged);
            }
            if (attrChanged(oldKlass.getAttribute(RecordAttribute.NAME), newParserKlass.getAttribute(RecordAttribute.NAME), oldConstantPool, newConstantPool)) {
                throw new RedefinitionException(RedefinitionError.ClassAttributeChanged);
            }
            if (attrChanged(oldKlass.getAttribute(PermittedSubclassesAttribute.NAME), newParserKlass.getAttribute(PermittedSubclassesAttribute.NAME), oldConstantPool, newConstantPool)) {
                throw new RedefinitionException(RedefinitionError.ClassAttributeChanged);
            }
        }

        ClassChange result = ClassChange.NO_CHANGE;
        ParserKlass oldParserKlass = oldKlass.getLinkedKlass().getParserKlass();
        boolean isPatched = finalParserKlass != null;

        // detect method changes (including constructors)
        ParserMethod[] newParserMethods = newParserKlass.getMethods();
        List<Method> oldMethods = new ArrayList<>(Arrays.asList(oldKlass.getDeclaredMethods()));
        List<ParserMethod> newMethods = new ArrayList<>(Arrays.asList(newParserMethods));
        Map<Method, ParserMethod> bodyChanges = new HashMap<>();
        List<ParserMethod> newSpecialMethods = new ArrayList<>(1);

        boolean constantPoolChanged = !oldConstantPool.immutableContentEquals(newConstantPool);
        Iterator<Method> oldIt = oldMethods.iterator();
        Iterator<ParserMethod> newIt;
        while (oldIt.hasNext()) {
            Method oldMethod = oldIt.next();
            ParserMethod oldParserMethod = oldMethod.getParserMethod();
            // verify that there is a new corresponding method
            newIt = newMethods.iterator();
            while (newIt.hasNext()) {
                ParserMethod newMethod = newIt.next();
                if (isSameMethod(oldParserMethod, newMethod, oldConstantPool, newConstantPool)) {
                    // detect method changes
                    ClassChange change = detectMethodChanges(oldParserMethod, newMethod);
                    switch (change) {
                        case NO_CHANGE:
                            if (isPatched) {
                                checkForSpecialConstructor(collectedChanges, bodyChanges, newSpecialMethods, oldMethod, oldParserMethod, newMethod);
                            } else if (constantPoolChanged) {
                                if (isObsolete(oldParserMethod, newMethod, oldParserKlass.getConstantPool(), newParserKlass.getConstantPool())) {
                                    result = ClassChange.CONSTANT_POOL_CHANGE;
                                    collectedChanges.addMethodBodyChange(oldMethod, newMethod);
                                } else {
                                    collectedChanges.addUnchangedMethod(oldMethod);
                                }
                            } else {
                                collectedChanges.addUnchangedMethod(oldMethod);
                            }
                            break;
                        case METHOD_BODY_CHANGE:
                            result = change;
                            if (isPatched) {
                                checkForSpecialConstructor(collectedChanges, bodyChanges, newSpecialMethods, oldMethod, oldParserMethod, newMethod);
                            } else {
                                collectedChanges.addMethodBodyChange(oldMethod, newMethod);
                            }
                            break;
                        default:
                            return change;
                    }
                    newIt.remove();
                    oldIt.remove();
                    break;
                }
            }
        }
        if (isPatched) {
            ParserMethod[] finalMethods = finalParserKlass.getMethods();
            // lookup the final new method based on the index in the parser method array

            // map found changed methods
            for (Map.Entry<Method, ParserMethod> entry : bodyChanges.entrySet()) {
                Method oldMethod = entry.getKey();
                ParserMethod changed = entry.getValue();
                for (int i = 0; i < newParserMethods.length; i++) {
                    if (newParserMethods[i] == changed) {
                        collectedChanges.addMethodBodyChange(oldMethod, finalMethods[i]);
                        break;
                    }
                }
            }
            // map found new methods
            newMethods.addAll(newSpecialMethods);
            for (ParserMethod changed : newMethods) {
                for (int i = 0; i < newParserMethods.length; i++) {
                    if (newParserMethods[i] == changed) {
                        collectedChanges.addNewMethod(finalMethods[i]);
                        break;
                    }
                }
            }
        } else {
            collectedChanges.addNewMethods(newMethods);
        }

        for (Method oldMethod : oldMethods) {
            collectedChanges.addRemovedMethod(oldMethod.getMethodVersion());
        }

        if (!oldMethods.isEmpty()) {
            if (jvmtiRestrictions) {
                throw new RedefinitionException(RedefinitionError.MethodDeleted);
            }
            result = ClassChange.REMOVE_METHOD;
        } else if (!newMethods.isEmpty()) {
            if (jvmtiRestrictions) {
                throw new RedefinitionException(RedefinitionError.MethodAdded);
            }
            result = ClassChange.ADD_METHOD;
        }

        if (isPatched) {
            result = ClassChange.CLASS_NAME_CHANGED;
        }

        // detect field changes
        Field[] oldFields = oldKlass.getDeclaredFields();
        ParserField[] newFields = newParserKlass.getFields();

        ArrayList<Field> oldFieldsList = new ArrayList<>(Arrays.asList(oldFields));
        ArrayList<ParserField> newFieldsList = new ArrayList<>(Arrays.asList(newFields));
        Map<ParserField, Field> compatibleFields = new HashMap<>();

        Iterator<Field> oldFieldsIt = oldFieldsList.iterator();
        Iterator<ParserField> newFieldsIt;

        AcceptedJVMTIChangedFields acceptedChanges = new AcceptedJVMTIChangedFields();

        while (oldFieldsIt.hasNext()) {
            Field oldField = oldFieldsIt.next();
            newFieldsIt = newFieldsList.iterator();
            // search for a new corresponding field
            while (newFieldsIt.hasNext()) {
                ParserField newField = newFieldsIt.next();
                // first look for a perfect match
                if (isUnchangedField(oldField, newField, compatibleFields, oldConstantPool, newConstantPool, acceptedChanges)) {
                    // A nested anonymous inner class may contain a field reference to the outer
                    // class instance. Since we match against the patched (inner class rename rules
                    // applied) if the current class was patched (renamed) the resulting outer
                    // field pointer will have a changed type. Hence we should mark it as a new
                    // field.
                    Matcher matcher = InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(oldField.getType().toString());
                    if (isPatched && matcher.matches()) {
                        break;
                    }
                    oldFieldsIt.remove();
                    newFieldsIt.remove();
                    break;
                }
            }
        }

        if (!newFieldsList.isEmpty()) {
            if (jvmtiRestrictions) {
                // only restrict is there's actual new fields, not only fields with constant value
                // attribute changes
                if (newFieldsList.size() != acceptedChanges.numAcceptedFields) {
                    throw new RedefinitionException(RedefinitionError.SchemaChanged);
                }
            }
            if (isPatched) {
                ParserField[] finalFields = finalParserKlass.getFields();
                // lookup the final new field based on the index in the parser field array
                for (ParserField parserField : newFieldsList) {
                    for (int i = 0; i < newFields.length; i++) {
                        if (parserField == newFields[i]) {
                            collectedChanges.addNewField(finalFields[i]);
                            break;
                        }
                    }
                }
            } else {
                collectedChanges.addNewFields(newFieldsList);
            }
            result = ClassChange.SCHEMA_CHANGE;
        }

        if (!oldFieldsList.isEmpty()) {
            if (jvmtiRestrictions) {
                // only restrict is there's actual removed fields, not only fields with constant
                // value attribute changes
                if (oldFieldsList.size() != acceptedChanges.numAcceptedFields) {
                    throw new RedefinitionException(RedefinitionError.SchemaChanged);
                }
            }
            collectedChanges.addRemovedFields(oldFieldsList);
            result = ClassChange.SCHEMA_CHANGE;
        }

        // detect class-level changes
        if (newParserKlass.getFlags() != oldParserKlass.getFlags()) {
            if (jvmtiRestrictions) {
                throw new RedefinitionException(RedefinitionError.ClassModifiersChanged);
            }
            result = ClassChange.SCHEMA_CHANGE;
        }

        collectedChanges.addCompatibleFields(compatibleFields);

        // detect changes to superclass and implemented interfaces
        Klass superKlass = oldKlass.getSuperKlass();
        if (!newParserKlass.getSuperKlass().equals(oldParserKlass.getSuperKlass())) {
            if (jvmtiRestrictions) {
                throw new RedefinitionException(RedefinitionError.HierarchyChanged);
            }
            result = ClassChange.CLASS_HIERARCHY_CHANGED;
            superKlass = getLoadedKlass(newParserKlass.getSuperKlass(), oldKlass);
        }
        collectedChanges.addSuperKlass((ObjectKlass) superKlass);

        ObjectKlass[] newSuperInterfaces = oldKlass.getSuperInterfaces();
        if (!Arrays.equals(newParserKlass.getSuperInterfaces(), oldParserKlass.getSuperInterfaces())) {
            if (jvmtiRestrictions) {
                throw new RedefinitionException(RedefinitionError.HierarchyChanged);
            }
            result = ClassChange.CLASS_HIERARCHY_CHANGED;
            newSuperInterfaces = new ObjectKlass[newParserKlass.getSuperInterfaces().length];
            for (int i = 0; i < newParserKlass.getSuperInterfaces().length; i++) {
                newSuperInterfaces[i] = (ObjectKlass) getLoadedKlass(newParserKlass.getSuperInterfaces()[i], oldKlass);
            }
        }
        collectedChanges.addSuperInterfaces(newSuperInterfaces);

        return result;
    }

    private static void detectInvalidEnumConstantChanges(ParserKlass newParserKlass, ObjectKlass oldKlass) throws RedefinitionException {
        // detect invalid enum constant changes
        // currently, we only allow appending new enum constants
        Field[] oldEnumFields = oldKlass.getDeclaredFields();
        LinkedList<Symbol<Name>> oldEnumConstants = new LinkedList<>();
        for (Field oldEnumField : oldEnumFields) {
            if (oldEnumField.getType() == oldKlass.getType()) {
                oldEnumConstants.addLast(oldEnumField.getName());
            }
        }
        LinkedList<Symbol<Name>> newEnumConstants = new LinkedList<>();
        ParserField[] newEnumFields = newParserKlass.getFields();
        for (ParserField newEnumField : newEnumFields) {
            if (newEnumField.getType() == oldKlass.getType()) {
                newEnumConstants.addLast(newEnumField.getName());
            }
        }
        // we don't currently allow removing enum constants
        if (oldEnumConstants.size() > newEnumConstants.size()) {
            throw new RedefinitionException(RedefinitionError.SchemaChanged);
        }

        // compare ordered lists, we don't allow reordering enum constants
        for (int i = 0; i < oldEnumConstants.size(); i++) {
            if (oldEnumConstants.get(i) != newEnumConstants.get(i)) {
                throw new RedefinitionException(RedefinitionError.SchemaChanged);
            }
        }
    }

    private static Klass getLoadedKlass(Symbol<Type> klassType, ObjectKlass oldKlass) throws RedefinitionException {
        Klass klass;
        klass = oldKlass.getContext().getRegistries().findLoadedClass(klassType, oldKlass.getDefiningClassLoader());
        if (klass == null) {
            // new super interface must be loaded eagerly then
            StaticObject resourceGuestString = oldKlass.getMeta().toGuestString(TypeSymbols.binaryName(klassType));
            try {
                StaticObject loadedClass = (StaticObject) oldKlass.getMeta().java_lang_ClassLoader_loadClass.invokeDirectVirtual(oldKlass.getDefiningClassLoader(), resourceGuestString);
                klass = loadedClass.getMirrorKlass();
            } catch (Throwable t) {
                throw new RedefinitionException(RedefinitionError.NoSuperDefFound);
            }
        }
        return klass;
    }

    private static void checkForSpecialConstructor(DetectedChange collectedChanges, Map<Method, ParserMethod> bodyChanges, List<ParserMethod> newSpecialMethods,
                    Method oldMethod, ParserMethod oldParserMethod, ParserMethod newMethod) {
        // mark constructors of nested anonymous inner classes
        // if they include an anonymous inner class type parameter
        if (Names._init_.equals(oldParserMethod.getName()) && Signatures._void != oldParserMethod.getSignature()) {
            // only mark constructors that contain the outer anonymous inner class
            Matcher matcher = InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(oldParserMethod.getSignature().toString());
            if (matcher.matches()) {
                newSpecialMethods.add(newMethod);
                collectedChanges.addRemovedMethod(oldMethod.getMethodVersion());
            } else {
                bodyChanges.put(oldMethod, newMethod);
            }
        } else {
            // for class-name patched classes we have to redefine all methods
            bodyChanges.put(oldMethod, newMethod);
        }
    }

    private static boolean isObsolete(ParserMethod oldMethod, ParserMethod newMethod, ConstantPool oldPool, ConstantPool newPool) {
        CodeAttribute oldCodeAttribute = (CodeAttribute) oldMethod.getAttribute(Names.Code);
        CodeAttribute newCodeAttribute = (CodeAttribute) newMethod.getAttribute(Names.Code);
        if (oldCodeAttribute == null) {
            return newCodeAttribute != null;
        } else if (newCodeAttribute == null) {
            return oldCodeAttribute != null;
        }
        BytecodeStream oldCode = new BytecodeStream(oldCodeAttribute.getOriginalCode());
        BytecodeStream newCode = new BytecodeStream(newCodeAttribute.getOriginalCode());

        return !isSame(oldCode, oldPool, newCode, newPool);
    }

    private static boolean isSame(BytecodeStream oldCode, ConstantPool oldPool, BytecodeStream newCode, ConstantPool newPool) {
        int bci;
        int nextBCI = 0;
        while (nextBCI < oldCode.endBCI()) {
            bci = nextBCI;
            int opcode = oldCode.currentBC(bci);
            nextBCI = oldCode.nextBCI(bci);
            if (opcode == Bytecodes.LDC ||
                            opcode == Bytecodes.LDC2_W ||
                            opcode == Bytecodes.LDC_W ||
                            opcode == Bytecodes.NEW ||
                            opcode == Bytecodes.INVOKEDYNAMIC ||
                            opcode == Bytecodes.GETFIELD ||
                            opcode == Bytecodes.GETSTATIC ||
                            opcode == Bytecodes.PUTFIELD ||
                            opcode == Bytecodes.PUTSTATIC ||
                            Bytecodes.isInvoke(opcode)) {
                int oldCPI = oldCode.readCPI(bci);
                int newCPI = newCode.readCPI(bci);
                if (!newPool.isSame(newCPI, oldCPI, oldPool)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static ClassChange detectMethodChanges(ParserMethod oldMethod, ParserMethod newMethod) {
        // check code attribute
        CodeAttribute oldCodeAttribute = (CodeAttribute) oldMethod.getAttribute(Names.Code);
        CodeAttribute newCodeAttribute = (CodeAttribute) newMethod.getAttribute(Names.Code);

        if (oldCodeAttribute == null) {
            return newCodeAttribute != null ? ClassChange.METHOD_BODY_CHANGE : ClassChange.NO_CHANGE;
        } else if (newCodeAttribute == null) {
            return oldCodeAttribute != null ? ClassChange.METHOD_BODY_CHANGE : ClassChange.NO_CHANGE;
        }

        if (!Arrays.equals(oldCodeAttribute.getOriginalCode(), newCodeAttribute.getOriginalCode())) {
            return ClassChange.METHOD_BODY_CHANGE;
        }
        // check line number table
        if (checkLineNumberTable(oldCodeAttribute.getLineNumberTableAttribute(), newCodeAttribute.getLineNumberTableAttribute())) {
            return ClassChange.METHOD_BODY_CHANGE;
        }

        // check local variable table
        if (checkLocalVariableTable(oldCodeAttribute.getLocalvariableTable(), newCodeAttribute.getLocalvariableTable())) {
            return ClassChange.METHOD_BODY_CHANGE;
        }

        // check local variable type table
        if (checkLocalVariableTable(oldCodeAttribute.getLocalvariableTypeTable(), newCodeAttribute.getLocalvariableTypeTable())) {
            return ClassChange.METHOD_BODY_CHANGE;
        }

        return ClassChange.NO_CHANGE;
    }

    private static boolean checkLineNumberTable(LineNumberTableAttribute table1, LineNumberTableAttribute table2) {
        List<LineNumberTableAttribute.Entry> oldEntries = table1.getEntries();
        List<LineNumberTableAttribute.Entry> newEntries = table2.getEntries();

        if (oldEntries.size() != newEntries.size()) {
            return true;
        }

        for (int i = 0; i < oldEntries.size(); i++) {
            LineNumberTableAttribute.Entry oldEntry = oldEntries.get(i);
            LineNumberTableAttribute.Entry newEntry = newEntries.get(i);
            if (oldEntry.getLineNumber() != newEntry.getLineNumber() || oldEntry.getBCI() != newEntry.getBCI()) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkLocalVariableTable(LocalVariableTable table1, LocalVariableTable table2) {
        Local[] oldLocals = table1.getLocals();
        Local[] newLocals = table2.getLocals();

        if (oldLocals.length != newLocals.length) {
            return true;
        }

        for (int i = 0; i < oldLocals.length; i++) {
            Local oldLocal = oldLocals[i];
            Local newLocal = newLocals[i];
            if (!oldLocal.getNameAsString().equals(newLocal.getNameAsString()) || oldLocal.getSlot() != newLocal.getSlot() || oldLocal.getStartBCI() != newLocal.getStartBCI() ||
                            oldLocal.getEndBCI() != newLocal.getEndBCI()) {
                return true;
            }
        }
        return false;
    }

    private static boolean attrChanged(Attribute oldAttribute, Attribute newAttribute, ConstantPool oldConstantPool, ConstantPool newConstantPool) {
        if ((oldAttribute == null || newAttribute == null)) {
            // both have to be null then
            return oldAttribute != null || newAttribute != null;
        } else {
            return !oldAttribute.isSame(newAttribute, oldConstantPool, newConstantPool);
        }
    }

    private static boolean isSameMethod(ParserMethod oldMethod, ParserMethod newMethod, ConstantPool oldConstantPool, ConstantPool newConstantPool) {
        boolean same = oldMethod.getName().equals(newMethod.getName()) &&
                        oldMethod.getSignature().equals(newMethod.getSignature()) &&
                        oldMethod.getFlags() == newMethod.getFlags();
        if (same) {
            // check method attributes that would constitute a higher-level
            // class redefinition than a method body change
            if (attrChanged(oldMethod.getAttribute(Names.RuntimeVisibleTypeAnnotations), newMethod.getAttribute(Names.RuntimeVisibleTypeAnnotations), oldConstantPool, newConstantPool)) {
                return false;
            }

            if (attrChanged(oldMethod.getAttribute(Names.RuntimeInvisibleTypeAnnotations), newMethod.getAttribute(Names.RuntimeInvisibleTypeAnnotations), oldConstantPool, newConstantPool)) {
                return false;
            }

            if (attrChanged(oldMethod.getAttribute(Names.RuntimeVisibleAnnotations), newMethod.getAttribute(Names.RuntimeVisibleAnnotations), oldConstantPool, newConstantPool)) {
                return false;
            }

            if (attrChanged(oldMethod.getAttribute(Names.RuntimeInvisibleAnnotations), newMethod.getAttribute(Names.RuntimeInvisibleAnnotations), oldConstantPool, newConstantPool)) {
                return false;
            }

            if (attrChanged(oldMethod.getAttribute(Names.RuntimeInvisibleParameterAnnotations), newMethod.getAttribute(Names.RuntimeInvisibleParameterAnnotations), oldConstantPool, newConstantPool)) {
                return false;
            }

            if (attrChanged(oldMethod.getAttribute(Names.RuntimeVisibleParameterAnnotations), newMethod.getAttribute(Names.RuntimeVisibleParameterAnnotations), oldConstantPool, newConstantPool)) {
                return false;
            }

            if (attrChanged(oldMethod.getAttribute(Names.Exceptions), newMethod.getAttribute(Names.Exceptions), oldConstantPool, newConstantPool)) {
                return false;
            }

            if (attrChanged(oldMethod.getAttribute(Names.Signature), newMethod.getAttribute(Names.Signature), oldConstantPool, newConstantPool)) {
                return false;
            }

            if (attrChanged(oldMethod.getAttribute(Names.Exceptions), newMethod.getAttribute(Names.Exceptions), oldConstantPool, newConstantPool)) {
                return false;
            }
        }
        return same;
    }

    private static boolean isUnchangedField(Field oldField, ParserField newField, Map<ParserField, Field> compatibleFields, ConstantPool oldConstantPool, ConstantPool newConstantPool,
                    AcceptedJVMTIChangedFields acceptedChanges) {
        boolean sameName = oldField.getName() == newField.getName();
        boolean sameType = oldField.getType() == newField.getType();
        boolean sameFlags = oldField.getModifiers() == (newField.getFlags() & Constants.JVM_RECOGNIZED_FIELD_MODIFIERS);

        if (sameName && sameType) {
            if (sameFlags) {
                // same name + type + flags

                // check field attributes
                Attribute[] oldAttributes = oldField.getAttributes();
                Attribute[] newAttributes = newField.getAttributes();

                if (oldAttributes.length != newAttributes.length) {
                    return false;
                }

                for (Attribute oldAttribute : oldAttributes) {
                    boolean found = false;
                    for (Attribute newAttribute : newAttributes) {
                        if (oldAttribute.getName() == newAttribute.getName() && oldAttribute.isSame(newAttribute, oldConstantPool, newConstantPool)) {
                            /*
                             * Due to us not replacing field attributes in the existing parser
                             * field, we have to make sure a constant value attribute doesn't change
                             * the constant value index as well. If so, we treat this as a combo of
                             * a removed and added field using the field extension mechanism
                             */
                            if (oldAttribute instanceof ConstantValueAttribute oldConstantValueAttr) {
                                ConstantValueAttribute newConstantValueAttr = (ConstantValueAttribute) newAttribute;
                                if (oldConstantValueAttr.getConstantValueIndex() != newConstantValueAttr.getConstantValueIndex()) {
                                    // don't restrict JVMTI when the constant value didn't change
                                    // but we have to do a combo of removed and added field
                                    acceptedChanges.numAcceptedFields++;
                                    return false;
                                }
                            }
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        return false;
                    }
                }
                // identical field found
                return true;
            } else {
                // same name + type
                if (Modifier.isStatic(oldField.getModifiers()) != Modifier.isStatic(newField.getFlags())) {
                    // a change from static -> non-static or vice versa is not compatible
                } else {
                    compatibleFields.put(newField, oldField);
                }
                return false;
            }
        }
        return false;
    }

    private void doRedefineClass(ChangePacket packet, List<ObjectKlass> invalidatedClasses, List<ObjectKlass> redefinedClasses) {
        ObjectKlass oldKlass = packet.info.getKlass();
        if (packet.info.isRenamed()) {
            // renaming a class is done by
            // 1. Rename the 'name' and 'type' Symbols in the Klass
            // 2. Update the loaded class cache in the associated ClassRegistry
            // 3. Set the guest language java.lang.Class#name field to null
            // 4. update the JDWP refType ID for the klass instance
            // 5. replace/record a classloader constraint for the new type and klass combination

            Symbol<Name> newName = packet.info.getName();
            Symbol<Type> newType = context.getTypes().fromClassNameEntry(newName);

            oldKlass.patchClassName(newName, newType);
            ClassRegistry classRegistry = context.getRegistries().getClassRegistry(packet.info.getClassLoader());
            classRegistry.onClassRenamed(oldKlass);

            InterpreterToVM.setFieldObject(StaticObject.NULL, oldKlass.mirror(), context.getMeta().java_lang_Class_name);
        }
        if (packet.classChange == ClassChange.CLASS_HIERARCHY_CHANGED) {
            oldKlass.removeAsSubType();
        }
        oldKlass.redefineClass(packet, invalidatedClasses);
        redefinedClasses.add(oldKlass);
        if (redefinitionPluginHandler.shouldRerunClassInitializer(oldKlass, packet.detectedChange.clinitChanged())) {
            classInitializerActions.add(new ReloadingAction(oldKlass));
        }
    }

    /**
     * @param receiverKlass the receiver's klass when the method is not static, resolutionSeed's
     *            declaring klass otherwise
     */
    @TruffleBoundary
    public Method handleRemovedMethod(Method resolutionSeed, Klass receiverKlass) {
        // wait for potential ongoing redefinition to complete
        check();
        Method replacementMethod = receiverKlass.lookupMethod(resolutionSeed.getName(), resolutionSeed.getRawSignature());
        Meta meta = resolutionSeed.getMeta();
        if (replacementMethod == null) {
            throw meta.throwExceptionWithMessage(meta.java_lang_NoSuchMethodError,
                            meta.toGuestString(resolutionSeed.getDeclaringKlass().getNameAsString() + "." + resolutionSeed.getName() + resolutionSeed.getRawSignature()) +
                                            " was removed by class redefinition");
        } else if (resolutionSeed.isStatic() != replacementMethod.isStatic()) {
            String message = resolutionSeed.isStatic() ? "expected static method: " : "expected non-static method:" + replacementMethod.getName();
            throw meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError, message);
        } else {
            // Update to the latest version of the replacement method
            return replacementMethod;
        }
    }

    public void registerExternalHotSwapHandler(StaticObject handler) {
        redefinitionPluginHandler.registerExternalHotSwapHandler(handler);
    }

    private static final class HierarchyComparator implements Comparator<ChangePacket> {
        public int compare(ChangePacket packet1, ChangePacket packet2) {
            Klass k1 = packet1.info.getKlass();
            Klass k2 = packet2.info.getKlass();
            // we need to do this check because isAssignableFrom is true in this case
            // and we would get an order that doesn't exist
            if (k1 == null || k2 == null || k1.equals(k2)) {
                return 0;
            }
            if (k1.isAssignableFrom(k2)) {
                return -1;
            } else if (k2.isAssignableFrom(k1)) {
                return 1;
            }
            // no hierarchy, check anon inner classes
            Matcher m1 = InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(k1.getNameAsString());
            Matcher m2 = InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(k2.getNameAsString());
            if (!m1.matches()) {
                return -1;
            } else {
                if (m2.matches()) {
                    return 0;
                } else {
                    return 1;
                }
            }
        }
    }

    private static final class SubClassHierarchyComparator implements Comparator<ObjectKlass> {
        public int compare(ObjectKlass k1, ObjectKlass k2) {
            // we need to do this check because isAssignableFrom is true in this case
            // and we would get an order that doesn't exist
            if (k1.equals(k2)) {
                return 0;
            }
            if (k1.isAssignableFrom(k2)) {
                return -1;
            } else if (k2.isAssignableFrom(k1)) {
                return 1;
            }
            // no hierarchy
            return 0;
        }
    }

    private static final class ReloadingAction {
        private ObjectKlass klass;

        private ReloadingAction(ObjectKlass klass) {
            this.klass = klass;
        }

        private void fire() {
            klass.reRunClinit();
        }
    }

    private static final class AcceptedJVMTIChangedFields {
        private int numAcceptedFields;
    }
}
