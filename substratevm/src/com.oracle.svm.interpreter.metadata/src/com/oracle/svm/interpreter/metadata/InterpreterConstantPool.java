/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKEDYNAMIC;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.shared.BuildPhaseProvider.AfterAnalysis;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.crema.CremaSupport;
import com.oracle.svm.core.hub.registry.SymbolsSupport;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.reflect.target.Target_java_lang_reflect_AccessibleObject;
import com.oracle.svm.espresso.classfile.ConstantPool;
import com.oracle.svm.espresso.classfile.ParserConstantPool;
import com.oracle.svm.espresso.classfile.descriptors.ByteSequence;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.svm.interpreter.metadata.serialization.VisibleForSerialization;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaMethod;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * JVMCI's {@link jdk.vm.ci.meta.ConstantPool} is not designed to be used in a performance-sensitive
 * bytecode interpreter, so a Espresso-like CP implementation is used instead for performance.
 * <p>
 * This class doesn't support runtime resolution on purpose, but supports pre-resolved entries
 * instead for AOT types.
 */
public class InterpreterConstantPool extends ConstantPool implements jdk.vm.ci.meta.ConstantPool {
    protected static final Object NULL_DYNAMIC_CONSTANT_SENTINEL = new Object();

    final InterpreterResolvedObjectType holder;
    final ParserConstantPool parserConstantPool;

    // Assigned after analysis.
    @UnknownObjectField(availability = AfterAnalysis.class, types = Object[].class) protected Object[] cachedEntries;

    // TODO move to crema once GR-71517 is resolved
    private volatile jdk.vm.ci.meta.ConstantPool ristrettoConstantPool;
    private static final AtomicReferenceFieldUpdater<InterpreterConstantPool, jdk.vm.ci.meta.ConstantPool> RISTRETTO_CONSTANT_POOL_UPDATER = AtomicReferenceFieldUpdater
                    .newUpdater(InterpreterConstantPool.class, jdk.vm.ci.meta.ConstantPool.class, "ristrettoConstantPool");

    Object objAt(int cpi) {
        if (cpi == 0) {
            // 0 implies unknown (!= unresolved) e.g. unknown class, field, method ...
            // In this case it's not possible to even provide a name or symbolic representation for
            // what's missing.
            // Index 0 must be handled by the resolution methods e.g. resolveType, resolveMethod ...
            // where an appropriate error should be thrown.
            throw VMError.shouldNotReachHere("Cannot resolve CP entry 0");
        }
        return cachedEntries[cpi];
    }

    protected InterpreterConstantPool(InterpreterResolvedObjectType holder, ParserConstantPool parserConstantPool, Object[] cachedEntries) {
        super(parserConstantPool);
        this.holder = MetadataUtil.requireNonNull(holder);
        this.parserConstantPool = parserConstantPool;
        this.cachedEntries = MetadataUtil.requireNonNull(cachedEntries);
    }

    protected InterpreterConstantPool(InterpreterResolvedObjectType holder, ParserConstantPool parserConstantPool) {
        this(holder, parserConstantPool, new Object[parserConstantPool.length()]);
    }

    @VisibleForSerialization
    public static InterpreterConstantPool create(InterpreterResolvedObjectType holder, ParserConstantPool parserConstantPool, Object[] cachedEntries) {
        return new InterpreterConstantPool(holder, parserConstantPool, cachedEntries);
    }

    public jdk.vm.ci.meta.ConstantPool getRistrettoConstantPool(Function<InterpreterConstantPool, jdk.vm.ci.meta.ConstantPool> ristrettoConstantPoolSupplier) {
        if (this.ristrettoConstantPool != null) {
            return this.ristrettoConstantPool;
        }
        /*
         * We allow concurrent allocation of a ristretto constant pool per interpreter constant
         * pool. Eventually however we CAS on the pointer in the interpreter representation, if
         * another thread was faster return its constant pool.
         */
        return getOrSetRistrettoConstantPool(ristrettoConstantPoolSupplier.apply(this));
    }

    private jdk.vm.ci.meta.ConstantPool getOrSetRistrettoConstantPool(jdk.vm.ci.meta.ConstantPool newRistrettoConstantPool) {
        if (RISTRETTO_CONSTANT_POOL_UPDATER.compareAndSet(this, null, newRistrettoConstantPool)) {
            return newRistrettoConstantPool;
        }
        var cp = this.ristrettoConstantPool;
        assert cp != null : "If CAS for null fails must have written a constant pool already";
        return cp;
    }

    @Override
    public int length() {
        return cachedEntries.length;
    }

    @Override
    public JavaField lookupField(int cpi, ResolvedJavaMethod method, int opcode) {
        return (JavaField) objAt(cpi);
    }

    @Override
    public JavaMethod lookupMethod(int cpi, int opcode) {
        return (JavaMethod) objAt(cpi);
    }

    @Override
    public JavaMethod lookupMethod(int cpi, int opcode, ResolvedJavaMethod caller) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public List<BootstrapMethodInvocation> lookupBootstrapMethodInvocations(boolean invokeDynamic) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public JavaType lookupType(int cpi, int opcode) {
        return (JavaType) objAt(cpi);
    }

    @Override
    public Object lookupConstant(int cpi) {
        return lookupConstant(cpi, true);
    }

    @Override
    public Object lookupConstant(int cpi, boolean resolve) {
        final Tag tag = tagAt(cpi);
        return switch (tag) {
            case INTEGER -> JavaConstant.forInt(this.intAt(cpi));
            case FLOAT -> JavaConstant.forFloat(this.floatAt(cpi));
            case LONG -> JavaConstant.forLong(this.longAt(cpi));
            case DOUBLE -> JavaConstant.forDouble(this.doubleAt(cpi));
            case STRING -> SubstrateObjectConstant.forObject(resolvedAt(cpi, holder));
            case CLASS -> objAt(cpi);
            case METHODHANDLE, METHODTYPE -> SubstrateObjectConstant.forObject(queryConstantPool(cpi, resolve));
            case DYNAMIC -> {
                if (!resolve && objAt(cpi) == null) {
                    yield null;
                }
                Object ret = resolvedDynamicConstantAt(cpi, getHolder());
                yield switch (CremaTypeAccess.symbolToJvmciKind(dynamicType(cpi))) {
                    case Boolean -> JavaConstant.forBoolean((Boolean) ret);
                    case Byte -> JavaConstant.forByte((Byte) ret);
                    case Short -> JavaConstant.forShort((Short) ret);
                    case Char -> JavaConstant.forChar((Character) ret);
                    case Int -> JavaConstant.forInt((Integer) ret);
                    case Float -> JavaConstant.forFloat((Float) ret);
                    case Long -> JavaConstant.forLong((Long) ret);
                    case Double -> JavaConstant.forDouble((Double) ret);
                    case Object -> SubstrateObjectConstant.forObject(ret);
                    default -> throw VMError.shouldNotReachHere("Unexpected dynamic constant type " + dynamicType(cpi));
                };
            }
            default -> throw VMError.shouldNotReachHere("Unknown tag " + tag);
        };
    }

    private Object queryConstantPool(int cpi, boolean resolve) {
        if (resolve) {
            return resolvedAt(cpi, holder);
        } else {
            return objAt(cpi);
        }
    }

    @Override
    public JavaConstant lookupAppendix(int cpi, int opcode) {
        assert opcode == INVOKEDYNAMIC;
        return (JavaConstant) objAt(cpi);
    }

    @VisibleForSerialization
    @Platforms(Platform.HOSTED_ONLY.class)
    public Object[] getCachedEntries() {
        return cachedEntries;
    }

    public Object peekCachedEntry(int cpi) {
        return cachedEntries[cpi];
    }

    public InterpreterResolvedObjectType getHolder() {
        return holder;
    }

    @Override
    public final RuntimeException classFormatError(String message) {
        throw new ClassFormatError(message);
    }

    // region Unimplemented methods

    @Override
    public void loadReferencedType(int cpi, int opcode) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public JavaType lookupReferencedType(int cpi, int opcode) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public String lookupUtf8(int cpi) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public Signature lookupSignature(int cpi) {
        throw VMError.intentionallyUnimplemented();
    }

    // endregion Unimplemented methods

    @Override
    public ParserConstantPool getParserConstantPool() {
        return parserConstantPool;
    }

    protected Object resolve(int cpi, @SuppressWarnings("unused") InterpreterResolvedObjectType accessingClass) {
        assert Thread.holdsLock(this);
        assert cpi != 0; // guaranteed by the caller

        @SuppressWarnings("unused")
        Tag tag = tagAt(cpi); // CPI bounds check

        Object entry = cachedEntries[cpi];
        if (isUnresolved(entry)) {
            /*
             * Runtime resolution is deliberately unsupported for AOT types (using base
             * InterpreterConstantPool). This can be relaxed in the future e.g. by attaching a
             * RuntimeInterpreterConstantPool instead.
             */
            throw new UnsupportedResolutionException();
        }

        return entry;
    }

    public Object resolvedAt(int cpi, InterpreterResolvedObjectType accessingClass) {
        Object entry = cachedEntries[cpi];
        if (isUnresolved(entry)) {
            // TODO(peterssen): GR-68611 Avoid deadlocks when hitting breakpoints (JDWP debugger)
            // during class resolution.
            /*
             * Class resolution can run arbitrary code (not in the to-be resolved class <clinit>
             * but) in the user class loaders where it can hit a breakpoint (JDWP debugger), causing
             * a deadlock.
             */
            synchronized (this) {
                entry = cachedEntries[cpi];
                if (isUnresolved(entry)) {
                    cachedEntries[cpi] = entry = resolve(cpi, accessingClass);
                }
            }
        }

        return entry;
    }

    private static boolean isUnresolved(Object entry) {
        return entry == null || entry instanceof UnresolvedJavaType || entry instanceof UnresolvedJavaMethod || entry instanceof UnresolvedJavaField;
    }

    @SuppressWarnings("unchecked")
    protected static <T extends Throwable> RuntimeException uncheckedThrow(Throwable t) throws T {
        throw (T) t;
    }

    public InterpreterResolvedJavaField resolvedFieldAt(InterpreterResolvedObjectType accessingKlass, int cpi) {
        Object resolvedEntry = resolvedAt(cpi, accessingKlass);
        assert resolvedEntry != null;
        return (InterpreterResolvedJavaField) resolvedEntry;
    }

    public InterpreterResolvedJavaMethod resolvedMethodAt(InterpreterResolvedObjectType accessingKlass, int cpi) {
        Object resolvedEntry = resolvedAt(cpi, accessingKlass);
        assert resolvedEntry != null;
        return (InterpreterResolvedJavaMethod) resolvedEntry;
    }

    public InterpreterResolvedObjectType resolvedTypeAt(InterpreterResolvedObjectType accessingKlass, int cpi) {
        Object resolvedEntry = resolvedAt(cpi, accessingKlass);
        assert resolvedEntry != null;
        return (InterpreterResolvedObjectType) resolvedEntry;
    }

    public String resolveStringAt(int cpi) {
        Object resolvedEntry = resolvedAt(cpi, null);
        if (resolvedEntry instanceof ReferenceConstant<?> referenceConstant) {
            resolvedEntry = referenceConstant.getReferent();
        }
        assert resolvedEntry != null;
        return (String) resolvedEntry;
    }

    public MethodHandle resolvedMethodHandleAt(int cpi, InterpreterResolvedObjectType accessingClass) {
        Object resolvedEntry = resolvedAt(cpi, accessingClass);
        assert resolvedEntry != null;
        return (MethodHandle) resolvedEntry;
    }

    public MethodType resolvedMethodTypeAt(char cpi, InterpreterResolvedObjectType accessingClass) {
        Object resolvedEntry = resolvedAt(cpi, accessingClass);
        assert resolvedEntry != null;
        return (MethodType) resolvedEntry;
    }

    /**
     * This is stored in the constant pool when a "sticky" failure happens while resolving a DYNAMIC
     * entry. It is used to throw the correct exception on subsequent accesses to that entry.
     */
    public static final class DynamicConstantError {
        private final LinkageError originalException;
        private Constructor<? extends LinkageError> cachedConstructor;

        public DynamicConstantError(LinkageError originalException) {
            this.originalException = originalException;
        }

        /**
         * Throws an exception when a failed DYNAMIC entry is accessed again. It tries to create a
         * fresh exception to give an accurate stack trace.
         */
        LinkageError throwOnAccess() {
            if (originalException.getClass().getClassLoader() == null) {
                /*
                 * Create a fresh exception for boot class loader exceptions. We avoid doing this
                 * for other exception types because we don't control their constructor, getMessage,
                 * and getCause implementations. See JDK-8349141.
                 */
                String message = originalException.getMessage();
                var constructor = getConstructor();
                if (constructor != null) {
                    try {
                        LinkageError error = constructor.newInstance(message);
                        Throwable cause = originalException.getCause();
                        if (cause != null) {
                            error.initCause(cause);
                        }
                        throw error;
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                        VMError.shouldNotReachHere(e);
                    }
                }
            }
            throw originalException;
        }

        private Constructor<? extends LinkageError> getConstructor() {
            if (cachedConstructor == null) {
                Class<? extends LinkageError> exceptionType = originalException.getClass();
                try {
                    cachedConstructor = exceptionType.getConstructor(String.class);
                } catch (NoSuchMethodException e) {
                    /*
                     * The only boot classes known to miss the String constructor are native image's
                     * Missing*RegistrationError.
                     */
                    assert exceptionType.getName().startsWith("Missing") && exceptionType.getName().endsWith("RegistrationError");
                    return null;
                }
                /*
                 * We don't need any access checks for this constructor. It's safe to mutate this
                 * instance since reflection returned a fresh copy that we own.
                 */
                SubstrateUtil.cast(cachedConstructor, Target_java_lang_reflect_AccessibleObject.class).override = true;
            }
            return cachedConstructor;
        }
    }

    public Object resolvedDynamicConstantAt(int cpi, InterpreterResolvedObjectType accessingClass) {
        Object resolvedEntry = resolvedAt(cpi, accessingClass);
        if (resolvedEntry instanceof DynamicConstantError savedError) {
            throw savedError.throwOnAccess();
        }
        if (resolvedEntry == NULL_DYNAMIC_CONSTANT_SENTINEL) {
            return null;
        }
        return resolvedEntry;
    }

    @Override
    public int intAt(int index) {
        checkTag(index, CONSTANT_Integer);
        Object entry = cachedEntries[index];
        assert entry == null || entry instanceof PrimitiveConstant;
        if (entry instanceof PrimitiveConstant primitiveConstant) {
            assert primitiveConstant.getJavaKind() == JavaKind.Int;
            return primitiveConstant.asInt();
        }
        return super.intAt(index);
    }

    @Override
    public float floatAt(int index) {
        checkTag(index, CONSTANT_Float);
        Object entry = cachedEntries[index];
        assert entry == null || entry instanceof PrimitiveConstant;
        if (entry instanceof PrimitiveConstant primitiveConstant) {
            assert primitiveConstant.getJavaKind() == JavaKind.Float;
            return primitiveConstant.asFloat();
        }
        return super.floatAt(index);
    }

    @Override
    public double doubleAt(int index) {
        checkTag(index, CONSTANT_Double);
        Object entry = cachedEntries[index];
        assert entry == null || entry instanceof PrimitiveConstant;
        if (entry instanceof PrimitiveConstant primitiveConstant) {
            assert primitiveConstant.getJavaKind() == JavaKind.Double;
            return primitiveConstant.asDouble();
        }
        return super.doubleAt(index);
    }

    @Override
    public long longAt(int index) {
        checkTag(index, CONSTANT_Long);
        Object entry = cachedEntries[index];
        assert entry == null || entry instanceof PrimitiveConstant;
        if (entry instanceof PrimitiveConstant primitiveConstant) {
            assert primitiveConstant.getJavaKind() == JavaKind.Long;
            return primitiveConstant.asLong();
        }
        return super.longAt(index);
    }

    public JavaType findClassAt(int cpi) {
        if (peekCachedEntry(cpi) instanceof InterpreterResolvedObjectType type) {
            return type;
        }
        Symbol<Name> nameSymbol = className(cpi);
        ByteSequence typeBytes = TypeSymbols.nameToType(nameSymbol);
        Symbol<Type> typeSymbol = SymbolsSupport.getTypes().lookupValidType(typeBytes);
        if (typeSymbol == null) {
            return null;
        }
        Class<?> cls = CremaSupport.singleton().findLoadedClass(typeSymbol, getHolder());
        if (cls == null) {
            return UnresolvedJavaType.create(typeBytes.toString());
        } else {
            return DynamicHub.fromClass(cls).getInterpreterType();
        }
    }
}
