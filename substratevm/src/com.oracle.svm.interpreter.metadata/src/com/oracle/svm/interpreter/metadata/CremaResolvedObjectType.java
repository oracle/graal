/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.metadata;

import static com.oracle.svm.espresso.classfile.Constants.ACC_IS_HIDDEN_CLASS;

import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.hub.crema.CremaResolvedJavaMethod;
import com.oracle.svm.core.hub.crema.CremaResolvedJavaRecordComponent;
import com.oracle.svm.core.hub.crema.CremaResolvedJavaType;
import com.oracle.svm.espresso.classfile.Constants;
import com.oracle.svm.espresso.classfile.ParserKlass;
import com.oracle.svm.espresso.classfile.attributes.Attribute;
import com.oracle.svm.espresso.classfile.attributes.AttributedElement;
import com.oracle.svm.espresso.classfile.attributes.BootstrapMethodsAttribute;
import com.oracle.svm.espresso.classfile.attributes.EnclosingMethodAttribute;
import com.oracle.svm.espresso.classfile.attributes.InnerClassesAttribute;
import com.oracle.svm.espresso.classfile.attributes.NestHostAttribute;
import com.oracle.svm.espresso.classfile.attributes.NestMembersAttribute;
import com.oracle.svm.espresso.classfile.attributes.PermittedSubclassesAttribute;
import com.oracle.svm.espresso.classfile.attributes.RecordAttribute;
import com.oracle.svm.espresso.classfile.attributes.RecordAttribute.RecordComponentInfo;
import com.oracle.svm.espresso.classfile.attributes.SignatureAttribute;
import com.oracle.svm.espresso.classfile.descriptors.Descriptor;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.ParserSymbols;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.shared.singletons.MultiLayeredImageSingleton;
import com.oracle.svm.shared.util.VMError;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.annotation.AbstractAnnotated;
import jdk.vm.ci.meta.annotation.AnnotationsInfo;

/**
 * A runtime-loaded, classfile-backed specialization of {@link InterpreterResolvedObjectType}.
 */
public final class CremaResolvedObjectType extends InterpreterResolvedObjectType implements CremaResolvedJavaType, AttributedElement {
    // GR-70288: Only keep a subset of the parsed attributes.
    private final Attribute[] attributes;

    private final byte[] primitiveStatics;
    private final Object[] referenceStatics;

    // GR-70720: Allow AOT types as nest host.
    private CremaResolvedObjectType host;

    public CremaResolvedObjectType(ParserKlass parserKlass, InterpreterResolvedJavaType componentType, InterpreterResolvedObjectType superclass,
                    InterpreterResolvedObjectType[] interfaces,
                    InterpreterConstantPool constantPool, Class<?> javaClass, boolean isWordType,
                    int staticReferenceFields, int staticPrimitiveFieldsSize) {
        super(parserKlass.getType(), parserKlass.getFlags() & Constants.JVM_RECOGNIZED_CLASS_MODIFIERS, componentType, superclass, interfaces, constantPool, javaClass, isWordType);
        this.primitiveStatics = new byte[staticPrimitiveFieldsSize];
        this.referenceStatics = new Object[staticReferenceFields];
        this.attributes = parserKlass.getAttributes();
    }

    @Override
    public Object getStaticStorage(boolean primitives, int layerNum) {
        assert layerNum != MultiLayeredImageSingleton.NONSTATIC_FIELD_LAYER_NUMBER;
        return primitives ? primitiveStatics : referenceStatics;
    }

    public BootstrapMethodsAttribute getBootstrapMethodsAttribute() {
        return getAttribute(BootstrapMethodsAttribute.NAME, BootstrapMethodsAttribute.class);
    }

    @Override
    public CremaResolvedJavaFieldImpl[] getDeclaredFields() {
        return (CremaResolvedJavaFieldImpl[]) declaredFields;
    }

    @Override
    public CremaResolvedJavaMethod[] getDeclaredCremaMethods() {
        // filter out constructors
        ArrayList<CremaResolvedJavaMethod> result = new ArrayList<>();
        for (InterpreterResolvedJavaMethod declaredMethod : getDeclaredMethods()) {
            if (!declaredMethod.isConstructor()) {
                result.add((CremaResolvedJavaMethod) declaredMethod);
            }
        }
        return result.toArray(new CremaResolvedJavaMethod[0]);
    }

    @Override
    public CremaResolvedJavaMethod[] getDeclaredCremaConstructors() {
        ArrayList<CremaResolvedJavaMethod> result = new ArrayList<>();
        for (InterpreterResolvedJavaMethod declaredMethod : getDeclaredMethods()) {
            if (declaredMethod.isConstructor()) {
                result.add((CremaResolvedJavaMethod) declaredMethod);
            }
        }
        return result.toArray(new CremaResolvedJavaMethod[0]);
    }

    @Override
    public ResolvedJavaMethod getClassInitializer() {
        for (InterpreterResolvedJavaMethod method : getDeclaredMethods(false)) {
            if (method.isClassInitializer()) {
                return method;
            }
        }
        return null;
    }

    @Override
    public List<? extends CremaResolvedJavaRecordComponent> getRecordComponents() {
        RecordAttribute recordAttribute = getAttribute(RecordAttribute.NAME, RecordAttribute.class);
        if (recordAttribute == null) {
            return null;
        }
        RecordComponentInfo[] components = recordAttribute.getComponents();
        if (components.length == 0) {
            return List.of();
        }
        CremaResolvedJavaRecordComponent[] result = new CremaResolvedJavaRecordComponent[components.length];
        for (int i = 0; i < components.length; i++) {
            result[i] = new CremaRecordComponent(this, components[i], i);
        }
        return List.of(result);
    }

    @Override
    public byte[] getRawAnnotations() {
        Attribute attribute = getAttribute(ParserSymbols.ParserNames.RuntimeVisibleAnnotations);
        if (attribute == null) {
            return null;
        }
        return attribute.getData();
    }

    @Override
    public byte[] getRawTypeAnnotations() {
        Attribute attribute = getAttribute(ParserSymbols.ParserNames.RuntimeVisibleTypeAnnotations);
        if (attribute == null) {
            return null;
        }
        return attribute.getData();
    }

    @Override
    public ResolvedJavaMethod getEnclosingMethod() {
        EnclosingMethodInfo info = getEnclosingMethodInfo();
        if (info == null || info.isPartial()) {
            return null;
        }

        InterpreterResolvedJavaMethod[] methods = info.isMethod() ? info.enclosingClass.getDeclaredMethods() : info.isConstructor() ? info.enclosingClass.getDeclaredConstructors() : null;
        if (methods != null) {
            for (InterpreterResolvedJavaMethod m : methods) {
                if (info.name.equals(m.getSymbolicName()) && info.descriptor.equals(m.getSymbolicSignature())) {
                    return m;
                }
            }
        }
        return null;
    }

    @Override
    public JavaType[] getDeclaredClasses() {
        InnerClassesAttribute innerClasses = getAttribute(InnerClassesAttribute.NAME, InnerClassesAttribute.class);
        if (innerClasses == null || innerClasses.entryCount() == 0) {
            return new JavaType[0];
        }

        InterpreterConstantPool pool = getConstantPool();
        ArrayList<JavaType> innerKlasses = new ArrayList<>();
        for (int i = 0; i < innerClasses.entryCount(); i++) {
            InnerClassesAttribute.Entry entry = innerClasses.entryAt(i);
            if (entry.innerClassIndex == 0 || entry.outerClassIndex == 0) {
                continue;
            }

            Symbol<Name> outerDescriptor = pool.className(entry.outerClassIndex);
            if (!outerDescriptor.equals(getSymbolicName())) {
                continue;
            }

            InterpreterResolvedObjectType outerKlass = pool.resolvedTypeAt(this, entry.outerClassIndex);
            if (outerKlass != this) {
                continue;
            }

            InterpreterResolvedObjectType innerKlass = pool.resolvedTypeAt(this, entry.innerClassIndex);
            if (innerKlass.isArray()) {
                throw new IncompatibleClassChangeError(toClassName() + " and " + innerKlass.toClassName() + " disagree on InnerClasses attribute");
            }
            checkOuterAndInnerClassAgree(this, innerKlass);
            innerKlasses.add(innerKlass);
        }
        return innerKlasses.toArray(new JavaType[0]);
    }

    @Override
    public boolean isHidden() {
        return (getModifiers() & ACC_IS_HIDDEN_CLASS) != 0;
    }

    @Override
    public JavaType[] getPermittedSubClasses() {
        PermittedSubclassesAttribute permittedSubclasses = getAttribute(PermittedSubclassesAttribute.NAME, PermittedSubclassesAttribute.class);
        if (permittedSubclasses == null || permittedSubclasses.getClasses().length == 0) {
            return EMPTY_ARRAY;
        }

        InterpreterConstantPool pool = getConstantPool();
        ArrayList<JavaType> result = new ArrayList<>(permittedSubclasses.getClasses().length);
        for (int classIndex : permittedSubclasses.getClasses()) {
            try {
                JavaType permittedSubclass = pool.resolvedTypeAt(this, classIndex);
                if (permittedSubclass != null && !permittedSubclass.isArray()) {
                    result.add(permittedSubclass);
                }
            } catch (VirtualMachineError e) {
                // Propagate this error like HotSpot does
                throw e;
            } catch (Throwable t) {
                // Javadoc for Class.getPermittedSubclasses:
                // If a Class object cannot be obtained, it is silently ignored, and not included in
                // the result array.
            }
        }
        return result.isEmpty() ? EMPTY_ARRAY : result.toArray(EMPTY_ARRAY);
    }

    @Override
    public CremaResolvedObjectType getNestHost() {
        if (host == null) {
            host = resolveHost();
        }
        return host;
    }

    @Override
    public InterpreterResolvedObjectType[] getNestMembers() {
        /*
         * This method is not called for VM operations, only for reflection. No need to cache the
         * result as this is a rare operation.
         */
        CremaResolvedObjectType nestHost = getNestHost();
        if (this != nestHost) {
            return resolveNestMembers(nestHost);
        }
        return resolveNestMembers(this);
    }

    private CremaResolvedObjectType resolveHost() {
        NestHostAttribute nestHostAttribute = getAttribute(NestHostAttribute.NAME, NestHostAttribute.class);
        if (nestHostAttribute == null) {
            return this;
        }
        try {
            InterpreterResolvedObjectType declaredHost = getConstantPool().resolvedTypeAt(this, nestHostAttribute.hostClassIndex);
            if (!(declaredHost instanceof CremaResolvedObjectType cremaHost)) {
                throw VMError.unimplemented("Specifying an AOT type as nest host is currently unsupported in runtime-loaded classes.");
            }
            if (cremaHost == this || !sameRuntimePackage(cremaHost) || !nestMemberCheck(cremaHost, this)) {
                /*
                 * Let H be the class named in the NestHostAttribute of the current class M. If any
                 * of the following is true, then M is its own nest host:
                 *
                 * - H is not in the same run-time package as M.
                 *
                 * - H lacks a NestMembers attribute. (checked above)
                 *
                 * - H has a NestMembers attribute, but there is no entry in its classes array that
                 * refers to a class or interface with the name N, where N is the name of M.
                 */
                return this;
            }
            return cremaHost;
        } catch (Throwable e) {
            /*
             * JVMS sect. 5.4.4: Any exception thrown as a result of failure of class or interface
             * resolution is not rethrown.
             */
            return this;
        }
    }

    @Override
    public void link() {
        RuntimeClassLoading.ensureLinked(DynamicHub.fromClass(clazz));
    }

    @Override
    public InterpreterResolvedJavaType resolveClassConstantInPool(int cpi) {
        return getConstantPool().resolvedTypeAt(this, cpi);
    }

    private boolean sameRuntimePackage(InterpreterResolvedJavaType other) {
        return this.getJavaClass().getClassLoader() == other.getJavaClass().getClassLoader() && this.getSymbolicRuntimePackage() == other.getSymbolicRuntimePackage();
    }

    /**
     * Returns whether the given nest {@code host} class declares {@code member} as one of its nest
     * members.
     */
    private static boolean nestMemberCheck(CremaResolvedObjectType host, InterpreterResolvedJavaType member) {
        NestMembersAttribute members = host.getAttribute(NestMembersAttribute.NAME, NestMembersAttribute.class);
        if (members == null) {
            return false;
        }
        for (int clsIndex : members.getClasses()) {
            if (host.getConstantPool().className(clsIndex) == member.getSymbolicName()) {
                return true;
            }
        }
        return false;
    }

    private static InterpreterResolvedObjectType[] resolveNestMembers(CremaResolvedObjectType host) {
        NestMembersAttribute nestMembersAttribute = host.getAttribute(NestMembersAttribute.NAME, NestMembersAttribute.class);
        if (nestMembersAttribute == null || nestMembersAttribute.getClasses().length == 0) {
            return new InterpreterResolvedObjectType[]{host};
        }
        ArrayList<InterpreterResolvedObjectType> members = new ArrayList<>(nestMembersAttribute.getClasses().length + 1);
        members.add(host);
        InterpreterConstantPool pool = host.getConstantPool();
        for (int memberIndex : nestMembersAttribute.getClasses()) {
            DynamicHub memberHub;
            InterpreterResolvedObjectType member;
            try {
                member = pool.resolvedTypeAt(host, memberIndex);
                memberHub = DynamicHub.fromClass(member.getJavaClass());
            } catch (Throwable e) {
                /*
                 * Don't allow badly constructed nest members to break execution here, only report
                 * well-constructed entries.
                 */
                continue;
            }
            ResolvedJavaType memberActualNestHost = DynamicHub.fromClass(memberHub.getNestHost()).getInterpreterType();
            if (host != memberActualNestHost) {
                // Skip nest members that do not declare 'this' as their host.
                continue;
            }
            members.add(member);
        }
        return members.toArray(new InterpreterResolvedObjectType[0]);
    }

    @Override
    public Attribute[] getAttributes() {
        return attributes;
    }

    static final class CremaRecordComponent extends AbstractAnnotated implements CremaResolvedJavaRecordComponent, AttributedElement {
        private final CremaResolvedObjectType declaringRecord;
        private final String name;
        private final JavaType type;
        private final String signature;
        private final Attribute[] attributes;
        private final int index;

        @SuppressWarnings("unchecked")
        CremaRecordComponent(CremaResolvedObjectType declaringRecord, RecordComponentInfo component, int index) {
            this.declaringRecord = declaringRecord;
            this.attributes = component.getAttributes();
            this.index = index;

            InterpreterConstantPool constantPool = declaringRecord.getConstantPool();
            this.name = constantPool.utf8At(component.getNameIndex(), "record component name").toString();
            this.type = CremaMethodAccess.toJavaType((Symbol<Type>) constantPool.utf8At(component.getDescriptorIndex(), "record component descriptor"));
            this.signature = extractSignature(constantPool, component);
        }

        private static String extractSignature(InterpreterConstantPool constantPool, RecordComponentInfo component) {
            SignatureAttribute signatureAttribute = component.getAttribute(SignatureAttribute.NAME, SignatureAttribute.class);
            if (signatureAttribute == null) {
                return null;
            }
            return constantPool.utf8At(signatureAttribute.getSignatureIndex(), "signature").toString();
        }

        @Override
        public CremaResolvedObjectType getDeclaringRecord() {
            return declaringRecord;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public JavaType getType() {
            return type;
        }

        @Override
        public String getSignature() {
            return signature;
        }

        @Override
        public byte[] getRawAnnotations() {
            Attribute attribute = getAttribute(ParserSymbols.ParserNames.RuntimeVisibleAnnotations);
            return attribute != null ? attribute.getData() : null;
        }

        @Override
        public byte[] getRawTypeAnnotations() {
            Attribute attribute = getAttribute(ParserSymbols.ParserNames.RuntimeVisibleTypeAnnotations);
            return attribute != null ? attribute.getData() : null;
        }

        @Override
        public Attribute[] getAttributes() {
            return attributes;
        }

        @Override
        public AnnotationsInfo getRawDeclaredAnnotationInfo() {
            return AnnotationsInfo.make(getRawAnnotations(), declaringRecord.getConstantPool(), declaringRecord);
        }

        @Override
        public AnnotationsInfo getTypeAnnotationInfo() {
            return AnnotationsInfo.make(getRawTypeAnnotations(), declaringRecord.getConstantPool(), declaringRecord);
        }

        @Override
        public int hashCode() {
            return declaringRecord.hashCode() + 31 * index;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CremaRecordComponent other)) {
                return false;
            }
            return declaringRecord.equals(other.declaringRecord) && index == other.index;
        }

        @Override
        public String toString() {
            return "CremaResolvedJavaRecordComponent<" + declaringRecord.toClassName() + "." + name + " " + type.toClassName() + ">";
        }
    }

    /**
     * Resolves the declaring class of this type from the {@code InnerClasses} attribute.
     *
     * @return the declaring class, or {@code null} if this type is not declared as an inner class
     */
    public Class<?> getDeclaringClass() {
        InnerClassesAttribute innerClassesAttribute = getAttribute(InnerClassesAttribute.NAME, InnerClassesAttribute.class);
        if (innerClassesAttribute == null) {
            return null;
        }
        InterpreterConstantPool pool = getConstantPool();

        for (int i = 0; i < innerClassesAttribute.entryCount(); i++) {
            InnerClassesAttribute.Entry entry = innerClassesAttribute.entryAt(i);
            if (entry.innerClassIndex == 0) {
                continue;
            }
            Symbol<Name> innerDescriptor = pool.className(entry.innerClassIndex);
            // Check descriptors/names before resolving.
            if (!innerDescriptor.equals(getSymbolicName())) {
                continue;
            }
            InterpreterResolvedObjectType innerKlass = pool.resolvedTypeAt(this, entry.innerClassIndex);
            if (innerKlass != this) {
                continue;
            }
            if (entry.outerClassIndex == 0) {
                return null;
            }
            InterpreterResolvedObjectType outerKlass = pool.resolvedTypeAt(this, entry.outerClassIndex);
            if (outerKlass.isArray()) {
                // An array class cannot declare inner classes.
                throw new IncompatibleClassChangeError(toClassName() + " and " + outerKlass.toClassName() + " disagree on InnerClasses attribute");
            }
            if (outerKlass instanceof CremaResolvedObjectType cremaOuterKlass) {
                // Only if the outer class is also runtime-loaded can we check consistency
                // of the inner-outer class relationship between the two.
                checkOuterAndInnerClassAgree(cremaOuterKlass, this);
            } else {
                // InnerClassesAttribute is unavailable for outer class so the
                // consistency check cannot be performed.
            }
            return outerKlass.getJavaClass();
        }
        return null;
    }

    /**
     * Checks that {@code outerKlass} has declared {@code innerKlass} as an inner klass.
     *
     * @throws IncompatibleClassChangeError if the check fails
     */
    private static void checkOuterAndInnerClassAgree(InterpreterResolvedObjectType outerKlass, InterpreterResolvedObjectType innerKlass) {
        if (!(outerKlass instanceof CremaResolvedObjectType cremaOuterKlass)) {
            return;
        }
        InnerClassesAttribute outerInnerClasses = cremaOuterKlass.getAttribute(InnerClassesAttribute.NAME, InnerClassesAttribute.class);
        if (outerInnerClasses != null) {
            InterpreterConstantPool pool = cremaOuterKlass.getConstantPool();
            for (int i = 0; i < outerInnerClasses.entryCount(); i++) {
                InnerClassesAttribute.Entry entry = outerInnerClasses.entryAt(i);
                if (entry.innerClassIndex == 0 || entry.outerClassIndex == 0) {
                    continue;
                }
                if (!pool.className(entry.outerClassIndex).equals(outerKlass.getSymbolicName()) || !pool.className(entry.innerClassIndex).equals(innerKlass.getSymbolicName())) {
                    continue;
                }
                InterpreterResolvedObjectType resolvedOuterKlass = pool.resolvedTypeAt(cremaOuterKlass, entry.outerClassIndex);
                if (resolvedOuterKlass != outerKlass) {
                    continue;
                }
                InterpreterResolvedObjectType resolvedInnerKlass = pool.resolvedTypeAt(cremaOuterKlass, entry.innerClassIndex);
                if (resolvedInnerKlass == innerKlass) {
                    return;
                }
            }
        }
        throw new IncompatibleClassChangeError(outerKlass.toClassName() + " and " + innerKlass.toClassName() + " disagree on InnerClasses attribute");
    }

    public record EnclosingMethodInfo(
                    InterpreterResolvedObjectType enclosingClass,
                    Symbol<Name> name,
                    Symbol<? extends Descriptor> descriptor) {
        public boolean isPartial() {
            return name == null || descriptor == null;
        }

        boolean isConstructor() {
            return !isPartial() && name == ParserSymbols.ParserNames._init_;
        }

        boolean isMethod() {
            return !isPartial() && !isConstructor() && name != ParserSymbols.ParserNames._clinit_;
        }

        private static String asStringOrNull(Symbol<?> sym) {
            if (sym == null) {
                return null;
            }
            return sym.toString();
        }

        public Object[] toJDKInfo() {
            return new Object[]{enclosingClass.getJavaClass(), asStringOrNull(name), asStringOrNull(descriptor)};
        }
    }

    public EnclosingMethodInfo getEnclosingMethodInfo() {
        EnclosingMethodAttribute enclosingMethodAttr = getAttribute(EnclosingMethodAttribute.NAME, EnclosingMethodAttribute.class);
        if (enclosingMethodAttr == null) {
            return null;
        }
        int classIndex = enclosingMethodAttr.getClassIndex();
        if (classIndex == 0) {
            return null;
        }
        InterpreterConstantPool pool = getConstantPool();
        InterpreterResolvedObjectType enclosingKlass = pool.resolvedTypeAt(this, classIndex);

        // Not a method, but a NameAndType entry.
        int nameAndTypeIndex = enclosingMethodAttr.getNameAndTypeIndex();
        Symbol<Name> methodName = null;
        Symbol<? extends Descriptor> methodDesc = null;
        if (nameAndTypeIndex != 0) {
            methodName = pool.nameAndTypeName(nameAndTypeIndex);
            methodDesc = pool.nameAndTypeDescriptor(nameAndTypeIndex);
        }
        return new EnclosingMethodInfo(enclosingKlass, methodName, methodDesc);
    }
}
