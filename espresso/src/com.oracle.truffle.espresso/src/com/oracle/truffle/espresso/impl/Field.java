/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl;

import java.util.function.Function;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.attributes.Attribute;
import com.oracle.truffle.espresso.classfile.attributes.ConstantValueAttribute;
import com.oracle.truffle.espresso.classfile.attributes.SignatureAttribute;
import com.oracle.truffle.espresso.classfile.descriptors.ModifiedUTF8;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.constantpool.RuntimeConstantPool;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.jdwp.api.FieldBreakpoint;
import com.oracle.truffle.espresso.jdwp.api.FieldRef;
import com.oracle.truffle.espresso.jdwp.api.TagConstants;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.FieldStorageObject;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.shared.meta.FieldAccess;

/**
 * Represents a resolved Espresso field.
 *
 * <h3>Class Redefinition</h3>
 *
 * In the presence of Class Redefinition a field can have three different behaviors:
 *
 * 1. An Original Field is a field that was declared in the first linked
 * {@link ObjectKlass.KlassVersion}. Accessing Original Fields is done directly through the
 * underlying {@link LinkedField}.
 *
 * 2. A {@link RedefineAddedField} which represents a field that was added by a
 * {@link com.oracle.truffle.espresso.redefinition.ClassRedefinition}. Management of Redefine Added
 * Fields is done through {@link ExtensionFieldsMetadata}. Accessing a Redefined Added Field
 * normally happens through the associated {@link FieldStorageObject instance} unless the Redefine
 * Added Field has a Compatible Field {@link #hasCompatibleField()}. A Compatible field is always an
 * Original Field that has the same name and type as the associated Redefine Added Field. A Redefine
 * Added field can be assigned a Compatible Field if e.g. the field access modifiers changed. The
 * state of the field is thus maintained by the Compatible (Original) Field. In this case the
 * Redefine Added Field serves only as an up-to-date representative of the field in the runtime.
 *
 * 3. A Delegation Field is a special field that is created whenever a certain field requires to be
 * re-resolved due to class redefinition. It allows obsolete code that uses a field to continue
 * accessing that field even though the field could no longer be resolved by the caller. Delegation
 * fields are always constructed as if they're Redefine Added Fields to trigger the alternative
 * access path as described above. Moreover, to delegate accesses to the field that maintains the
 * value (this could be either an Original Field or a Redefine Added Field) a Delegation field is
 * assigned the underlying field as a Compatible Field.
 */
public class Field extends Member<Type> implements FieldRef, FieldAccess<Klass, Method, Field> {

    public static final Field[] EMPTY_ARRAY = new Field[0];

    final LinkedField linkedField;
    protected final ObjectKlass.KlassVersion holder;

    protected final RuntimeConstantPool pool;
    @CompilationFinal private volatile Klass typeKlassCache;
    @CompilationFinal private Symbol<ModifiedUTF8> genericSignature;

    private boolean removedByRedefinition;

    public Field(ObjectKlass.KlassVersion holder, LinkedField linkedField, RuntimeConstantPool pool) {
        this.linkedField = linkedField;
        this.holder = holder;
        this.pool = pool;
    }

    @Override
    public final Symbol<Name> getName() {
        return linkedField.getName();
    }

    public final Symbol<Type> getType() {
        return linkedField.getType();
    }

    public void removeByRedefinition() {
        removedByRedefinition = true;
    }

    public final boolean isRemoved() {
        return removedByRedefinition;
    }

    public final boolean needsReResolution() {
        return !holder.getAssumption().isValid();
    }

    public final Attribute[] getAttributes() {
        return linkedField.getParserField().getAttributes();
    }

    @SuppressWarnings("unchecked")
    public final Symbol<ModifiedUTF8> getGenericSignature() {
        if (genericSignature == null) {
            SignatureAttribute attr = (SignatureAttribute) linkedField.getAttribute(SignatureAttribute.NAME);
            if (attr == null) {
                genericSignature = ModifiedUTF8.fromSymbol(getType());
            } else {
                genericSignature = (Symbol<ModifiedUTF8>) pool.utf8At(attr.getSignatureIndex(), "generic signature");
            }
        }
        return genericSignature;
    }

    public final boolean isHidden() {
        return linkedField.isHidden();
    }

    public final boolean isTrustedFinal() {
        ObjectKlass k = getDeclaringKlass();
        return isFinalFlagSet() && (isStatic() || k.isHidden() || k.isRecord());
    }

    public final JavaKind getKind() {
        return linkedField.getKind();
    }

    @Override
    public final int getModifiers() {
        return getFlags() & Constants.JVM_RECOGNIZED_FIELD_MODIFIERS;
    }

    public final int getFlags() {
        return linkedField.getFlags();
    }

    @Override
    public final ObjectKlass getDeclaringKlass() {
        return holder.getKlass();
    }

    /**
     * The slot serves as the position in the `field table` of the ObjectKlass.
     */
    public final int getSlot() {
        return linkedField.getSlot();
    }

    @Override
    public final String toString() {
        return getDeclaringKlass().getNameAsString() + "." + getName() + ": " + getType();
    }

    public final Klass resolveTypeKlass() {
        Klass tk = typeKlassCache;
        if (tk == null) {
            if (CompilerDirectives.isPartialEvaluationConstant(this)) {
                // This can be used from contexts where this is not a constant (e.g., Unsafe)
                // as well as context where this is constant (e.g., field access)
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            doResolveType();
        }
        return typeKlassCache;
    }

    @TruffleBoundary
    private void doResolveType() {
        synchronized (this) {
            Klass tk = typeKlassCache;
            if (tk == null) {
                tk = holder.getKlass().getMeta().resolveSymbolOrFail(getType(),
                                holder.getKlass().getDefiningClassLoader(),
                                holder.getKlass().protectionDomain());
                typeKlassCache = tk;
            }
        }
    }

    public final Attribute getAttribute(Symbol<Name> attrName) {
        return linkedField.getAttribute(attrName);
    }

    public static Field getReflectiveFieldRoot(StaticObject seed, Meta meta) {
        StaticObject curField = seed;
        Field target = null;
        while (target == null) {
            target = (Field) meta.HIDDEN_FIELD_KEY.getHiddenObject(curField);
            if (target == null) {
                curField = meta.java_lang_reflect_Field_root.getObject(curField);
            }
        }
        return target;
    }

    @Override
    public final void checkLoadingConstraints(StaticObject loader1, StaticObject loader2, Function<String, RuntimeException> errorHandler) {
        getDeclaringKlass().getContext().getRegistries().checkLoadingConstraint(getType(), loader1, loader2, errorHandler);
    }

    // region FieldAccess impl

    @Override
    public final boolean shouldEnforceInitializerCheck() {
        return (getDeclaringKlass().getMeta().getLanguage().getSpecComplianceMode() == EspressoOptions.SpecComplianceMode.STRICT) ||
                        // HotSpot enforces this only for >= Java 9 (v53) .class files.
                        getDeclaringClass().getMajorVersion() >= ClassfileParser.JAVA_9_VERSION;
    }

    // endregion FieldAccess impl

    // region Field accesses

    // region Generic
    public final Object get(StaticObject obj) {
        return get(obj, false);
    }

    public final Object get(StaticObject obj, boolean forceVolatile) {
        // @formatter:off
        switch (getKind()) {
            case Boolean: return getBoolean(obj, forceVolatile);
            case Byte: return getByte(obj, forceVolatile);
            case Short: return getShort(obj, forceVolatile);
            case Char: return getChar(obj, forceVolatile);
            case Int: return getInt(obj, forceVolatile);
            case Float: return getFloat(obj, forceVolatile);
            case Long: return getLong(obj, forceVolatile);
            case Double: return getDouble(obj, forceVolatile);
            case Object: return getObject(obj, forceVolatile);
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
    }

    public final void set(StaticObject obj, Object value) {
        set(obj, value, false);
    }

    public final void set(StaticObject obj, Object value, boolean forceVolatile) {
        // @formatter:off
        switch (getKind()) {
            case Boolean: setBoolean(obj, (boolean) value, forceVolatile); break;
            case Byte: setByte(obj, (byte) value, forceVolatile); break;
            case Short: setShort(obj, (short) value, forceVolatile); break;
            case Char: setChar(obj, (char) value, forceVolatile); break;
            case Int: setInt(obj, (int) value, forceVolatile); break;
            case Float: setFloat(obj, (float) value, forceVolatile); break;
            case Long: setLong(obj, (long) value, forceVolatile); break;
            case Double: setDouble(obj, (double) value, forceVolatile); break;
            case Object: setObject(obj, value, forceVolatile); break;
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
    }

    public final boolean getAsBoolean(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsBoolean(meta, obj, defaultIfNull, false);
    }

    public final boolean getAsBoolean(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asBoolean(val, defaultIfNull);
    }

    public final byte getAsByte(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsByte(meta, obj, defaultIfNull, false);
    }

    public final byte getAsByte(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asByte(val, defaultIfNull);
    }

    public final short getAsShort(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsShort(meta, obj, defaultIfNull, false);
    }

    public final short getAsShort(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asShort(val, defaultIfNull);
    }

    public final char getAsChar(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsChar(meta, obj, defaultIfNull, false);
    }

    public final char getAsChar(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asChar(val, defaultIfNull);
    }

    public final int getAsInt(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsInt(meta, obj, defaultIfNull, false);
    }

    public final int getAsInt(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asInt(val, defaultIfNull);
    }

    public final float getAsFloat(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsFloat(meta, obj, defaultIfNull, false);
    }

    public final float getAsFloat(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asFloat(val, defaultIfNull);
    }

    public final long getAsLong(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsLong(meta, obj, defaultIfNull, false);
    }

    public final long getAsLong(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asLong(val, defaultIfNull);
    }

    public final double getAsDouble(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsDouble(meta, obj, defaultIfNull, false);
    }

    public final double getAsDouble(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asDouble(val, defaultIfNull);
    }

    public final StaticObject getAsObject(Meta meta, StaticObject obj) {
        return getAsObject(meta, obj, false);
    }

    public final StaticObject getAsObject(Meta meta, StaticObject obj, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asObject(val);
    }
    // endregion Generic

    // region Object

    // region helper methods
    private Object getHiddenObjectHelper(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            return linkedField.getObjectVolatile(obj);
        } else {
            return linkedField.getObject(obj);
        }
    }

    private Object getObjectHelper(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();

        if (!getDeclaringKlass().getContext().anyHierarchyChanged()) {
            if (isVolatile() || forceVolatile) {
                return linkedField.getObjectVolatile(obj);
            } else {
                return linkedField.getObject(obj);
            }
        } else {
            // class hierarchy changes have been made, so enable
            // additional type checks to guard against reading
            // a now invalid value
            StaticObject result;
            if (isVolatile() || forceVolatile) {
                result = (StaticObject) linkedField.getObjectVolatile(obj);
            } else {
                result = (StaticObject) linkedField.getObject(obj);
            }
            if (result == StaticObject.NULL) {
                return result;
            }
            return checkGetValueValidity(result);
        }
    }

    protected StaticObject checkGetValueValidity(StaticObject object) {
        StaticObject result = object;
        try {
            Klass klass = resolveTypeKlass();
            if (klass != null && !klass.isAssignableFrom((result).getKlass())) {
                result = StaticObject.NULL;
            }
        } catch (EspressoException e) {
            // ignore if type klass cannot be resolved
        }
        return result;
    }

    private void setObjectHelper(StaticObject obj, Object value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();

        if (getDeclaringKlass().getContext().anyHierarchyChanged()) {
            checkSetValueValidity(value);
        }
        if (isVolatile() || forceVolatile) {
            linkedField.setObjectVolatile(obj, value);
        } else {
            linkedField.setObject(obj, value);
        }
    }

    protected void checkSetValueValidity(Object value) {
        if (value != StaticObject.NULL && value instanceof StaticObject) {
            Klass klass = null;
            try {
                klass = resolveTypeKlass();
            } catch (EspressoException e) {
                // ignore if type klass cannot be resolved
            }
            if (klass != null && !klass.isAssignableFrom(((StaticObject) value).getKlass())) {
                throw getDeclaringKlass().getMeta().throwException(getDeclaringKlass().getMeta().java_lang_IncompatibleClassChangeError);
            }
        }
    }
    // endregion helper methods

    // To access hidden fields, use the dedicated `(g|s)etHiddenObjectField` methods
    public final StaticObject getObject(StaticObject obj) {
        return getObject(obj, false);
    }

    public StaticObject getObject(StaticObject obj, boolean forceVolatile) {
        assert !isHidden() : this + " is hidden, use getHiddenObject";
        return (StaticObject) getObjectHelper(obj, forceVolatile);
    }

    public final void setObject(StaticObject obj, Object value) {
        setObject(obj, value, false);
    }

    public void setObject(StaticObject obj, Object value, boolean forceVolatile) {
        assert !isHidden() : this + " is hidden, use setHiddenObject";
        setObjectHelper(obj, value, forceVolatile);
    }

    public StaticObject getAndSetObject(StaticObject obj, StaticObject value) {
        obj.checkNotForeign();
        assert !isHidden() : this + " is hidden";
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return (StaticObject) linkedField.getAndSetObject(obj, value);
    }

    public boolean compareAndSwapObject(StaticObject obj, Object before, Object after) {
        obj.checkNotForeign();
        assert !isHidden() : this + " is hidden";
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return linkedField.compareAndSwapObject(obj, before, after);
    }

    public StaticObject compareAndExchangeObject(StaticObject obj, Object before, Object after) {
        obj.checkNotForeign();
        assert !isHidden() : this + " is hidden";
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return (StaticObject) linkedField.compareAndExchangeObject(obj, before, after);
    }

    // region hidden Object
    public final Object getHiddenObject(StaticObject obj) {
        return getHiddenObject(obj, false);
    }

    public final Object getHiddenObject(StaticObject obj, boolean forceVolatile) {
        assert isHidden() : this + " is not hidden, use getObject";
        return getHiddenObjectHelper(obj, forceVolatile);
    }

    public final void setHiddenObject(StaticObject obj, Object value) {
        setHiddenObject(obj, value, false);
    }

    public final void setHiddenObject(StaticObject obj, Object value, boolean forceVolatile) {
        assert isHidden() : this + " is not hidden, use setObject";
        setObjectHelper(obj, value, forceVolatile);
    }

    public final void setMaybeHiddenObject(StaticObject obj, StaticObject value) {
        setMaybeHiddenObject(obj, value, false);
    }

    public final void setMaybeHiddenObject(StaticObject obj, StaticObject value, boolean forceVolatile) {
        setObjectHelper(obj, value, forceVolatile);
    }

    public final StaticObject getMaybeHiddenObject(StaticObject obj) {
        return getMaybeHiddenObject(obj, false);
    }

    public final StaticObject getMaybeHiddenObject(StaticObject obj, boolean forceVolatile) {
        return (StaticObject) getObjectHelper(obj, forceVolatile);
    }

    public Object compareAndExchangeHiddenObject(StaticObject obj, Object before, Object after) {
        obj.checkNotForeign();
        assert isHidden() : this + " is not hidden";
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return linkedField.compareAndExchangeObject(obj, before, after);
    }
    // endregion Hidden Object
    // endregion Object

    // region boolean
    public final boolean getBoolean(StaticObject obj) {
        return getBoolean(obj, false);
    }

    public boolean getBoolean(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            return linkedField.getBooleanVolatile(obj);
        } else {
            return linkedField.getBoolean(obj);
        }
    }

    public final void setBoolean(StaticObject obj, boolean value) {
        setBoolean(obj, value, false);
    }

    public void setBoolean(StaticObject obj, boolean value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            linkedField.setBooleanVolatile(obj, value);
        } else {
            linkedField.setBoolean(obj, value);
        }
    }

    public boolean compareAndSwapBoolean(StaticObject obj, boolean before, boolean after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return linkedField.compareAndSwapBoolean(obj, before, after);
    }

    public boolean compareAndExchangeBoolean(StaticObject obj, boolean before, boolean after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return linkedField.compareAndExchangeBoolean(obj, before, after);
    }
    // endregion boolean

    // region byte
    public final byte getByte(StaticObject obj) {
        return getByte(obj, false);
    }

    public byte getByte(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            return linkedField.getByteVolatile(obj);
        } else {
            return linkedField.getByte(obj);
        }
    }

    public final void setByte(StaticObject obj, byte value) {
        setByte(obj, value, false);
    }

    public void setByte(StaticObject obj, byte value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            linkedField.setByteVolatile(obj, value);
        } else {
            linkedField.setByte(obj, value);
        }
    }

    public boolean compareAndSwapByte(StaticObject obj, byte before, byte after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return linkedField.compareAndSwapByte(obj, before, after);
    }

    public byte compareAndExchangeByte(StaticObject obj, byte before, byte after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return linkedField.compareAndExchangeByte(obj, before, after);
    }
    // endregion byte

    // region char
    public final char getChar(StaticObject obj) {
        return getChar(obj, false);
    }

    public char getChar(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            return linkedField.getCharVolatile(obj);
        } else {
            return linkedField.getChar(obj);
        }
    }

    public final void setChar(StaticObject obj, char value) {
        setChar(obj, value, false);
    }

    public void setChar(StaticObject obj, char value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            linkedField.setCharVolatile(obj, value);
        } else {
            linkedField.setChar(obj, value);
        }
    }

    public boolean compareAndSwapChar(StaticObject obj, char before, char after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return linkedField.compareAndSwapChar(obj, before, after);
    }

    public char compareAndExchangeChar(StaticObject obj, char before, char after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return linkedField.compareAndExchangeChar(obj, before, after);
    }
    // endregion char

    // region double
    public final double getDouble(StaticObject obj) {
        return getDouble(obj, false);
    }

    public double getDouble(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            return linkedField.getDoubleVolatile(obj);
        } else {
            return linkedField.getDouble(obj);
        }
    }

    public final void setDouble(StaticObject obj, double value) {
        setDouble(obj, value, false);
    }

    public void setDouble(StaticObject obj, double value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            linkedField.setDoubleVolatile(obj, value);
        } else {
            linkedField.setDouble(obj, value);
        }
    }

    public boolean compareAndSwapDouble(StaticObject obj, double before, double after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return linkedField.compareAndSwapDouble(obj, before, after);
    }

    public double compareAndExchangeDouble(StaticObject obj, double before, double after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return linkedField.compareAndExchangeDouble(obj, before, after);
    }
    // endregion double

    // region float
    public final float getFloat(StaticObject obj) {
        return getFloat(obj, false);
    }

    public float getFloat(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            return linkedField.getFloatVolatile(obj);
        } else {
            return linkedField.getFloat(obj);
        }
    }

    public final void setFloat(StaticObject obj, float value) {
        setFloat(obj, value, false);
    }

    public void setFloat(StaticObject obj, float value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            linkedField.setFloatVolatile(obj, value);
        } else {
            linkedField.setFloat(obj, value);
        }
    }

    public boolean compareAndSwapFloat(StaticObject obj, float before, float after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return linkedField.compareAndSwapFloat(obj, before, after);
    }

    public float compareAndExchangeFloat(StaticObject obj, float before, float after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return linkedField.compareAndExchangeFloat(obj, before, after);
    }
    // endregion float

    // region int
    public final int getInt(StaticObject obj) {
        return getInt(obj, false);
    }

    public int getInt(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            return linkedField.getIntVolatile(obj);
        } else {
            return linkedField.getInt(obj);
        }
    }

    public final void setInt(StaticObject obj, int value) {
        setInt(obj, value, false);
    }

    public void setInt(StaticObject obj, int value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            linkedField.setIntVolatile(obj, value);
        } else {
            linkedField.setInt(obj, value);
        }
    }

    public int getAndSetInt(StaticObject obj, int value) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return linkedField.getAndSetInt(obj, value);
    }

    public boolean compareAndSwapInt(StaticObject obj, int before, int after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return linkedField.compareAndSwapInt(obj, before, after);
    }

    public int compareAndExchangeInt(StaticObject obj, int before, int after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return linkedField.compareAndExchangeInt(obj, before, after);
    }
    // endregion int

    // region long
    public final long getLong(StaticObject obj) {
        return getLong(obj, false);
    }

    public long getLong(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        assert getKind().needsTwoSlots();
        if (isVolatile() || forceVolatile) {
            return linkedField.getLongVolatile(obj);
        } else {
            return linkedField.getLong(obj);
        }
    }

    public final void setLong(StaticObject obj, long value) {
        setLong(obj, value, false);
    }

    public void setLong(StaticObject obj, long value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        assert getKind().needsTwoSlots();
        if (isVolatile() || forceVolatile) {
            linkedField.setLongVolatile(obj, value);
        } else {
            linkedField.setLong(obj, value);
        }
    }

    public long getAndSetLong(StaticObject obj, long value) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        assert getKind().needsTwoSlots();
        return linkedField.getAndSetLong(obj, value);
    }

    public boolean compareAndSwapLong(StaticObject obj, long before, long after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        assert getKind().needsTwoSlots();
        return linkedField.compareAndSwapLong(obj, before, after);
    }

    public long compareAndExchangeLong(StaticObject obj, long before, long after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return linkedField.compareAndExchangeLong(obj, before, after);
    }
    // endregion long

    // region short
    public final short getShort(StaticObject obj) {
        return getShort(obj, false);
    }

    public short getShort(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            return linkedField.getShortVolatile(obj);
        } else {
            return linkedField.getShort(obj);
        }
    }

    public final void setShort(StaticObject obj, short value) {
        setShort(obj, value, false);
    }

    public void setShort(StaticObject obj, short value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            linkedField.setShortVolatile(obj, value);
        } else {
            linkedField.setShort(obj, value);
        }
    }

    public boolean compareAndSwapShort(StaticObject obj, short before, short after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return linkedField.compareAndSwapShort(obj, before, after);
    }

    public short compareAndExchangeShort(StaticObject obj, short before, short after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        return linkedField.compareAndExchangeShort(obj, before, after);
    }
    // endregion short

    // endregion Field accesses

    // region jdwp-specific
    @Override
    public final byte getTagConstant() {
        return TagConstants.toTagConstant(getKind());
    }

    @Override
    public final String getNameAsString() {
        return getName().toString();
    }

    @Override
    public final String getTypeAsString() {
        return getType().toString();
    }

    @Override
    public final String getGenericSignatureAsString() {
        Symbol<ModifiedUTF8> signature = getGenericSignature();
        return signature.toString();
    }

    @Override
    public final Object getValue(Object self) {
        return get((StaticObject) self);
    }

    @Override
    public final void setValue(Object self, Object value) {
        set((StaticObject) self, value);
    }

    private final StableBoolean hasActiveBreakpoints = new StableBoolean(false);

    // array with maximum size 2, one access info and/or one modification info.
    private FieldBreakpoint[] infos = null;

    @Override
    public final boolean hasActiveBreakpoint() {
        return hasActiveBreakpoints.get();
    }

    @Override
    public final FieldBreakpoint[] getFieldBreakpointInfos() {
        return infos;
    }

    @Override
    public final void addFieldBreakpointInfo(FieldBreakpoint info) {
        if (infos == null) {
            infos = new FieldBreakpoint[]{info};
            hasActiveBreakpoints.set(true);
            return;
        }

        int length = infos.length;
        FieldBreakpoint[] temp = new FieldBreakpoint[length + 1];
        System.arraycopy(infos, 0, temp, 0, length);
        temp[length] = info;
        infos = temp;
        hasActiveBreakpoints.set(true);
    }

    @Override
    public final void removeFieldBreakpointInfo(int requestId) {
        // shrink the array to avoid null values
        switch (infos.length) {
            case 0:
                throw new RuntimeException("Field: " + getNameAsString() + " should contain field breakpoint info");
            case 1:
                infos = null;
                hasActiveBreakpoints.set(false);
                return;
            case 2:
                FieldBreakpoint[] temp = new FieldBreakpoint[1];
                FieldBreakpoint info = infos[0];
                if (info.getRequestId() == requestId) {
                    // remove index 0, but keep info at index 1
                    temp[0] = infos[1];
                    infos = temp;
                    return;
                }
                info = infos[1];
                if (info.getRequestId() == requestId) {
                    // remove index 1, but keep info at index 0
                    temp[0] = infos[0];
                    infos = temp;
                }
        }
    }

    @Override
    public void disposeFieldBreakpoint() {
        hasActiveBreakpoints.set(false);
        infos = null;
    }

    public void setCompatibleField(@SuppressWarnings("unused") Field field) {
        // only applicable to RedefineAddedFields
    }

    public boolean hasCompatibleField() {
        return false;
    }

    public Field getCompatibleField() {
        return null;
    }

    @TruffleBoundary
    public StaticObject makeMirror(Meta meta) {
        StaticObject instance = meta.java_lang_reflect_Field.allocateInstance(meta.getContext());

        Attribute rawRuntimeVisibleAnnotations = getAttribute(Names.RuntimeVisibleAnnotations);
        StaticObject runtimeVisibleAnnotations = rawRuntimeVisibleAnnotations != null
                        ? StaticObject.wrap(rawRuntimeVisibleAnnotations.getData(), meta)
                        : StaticObject.NULL;

        Attribute rawRuntimeVisibleTypeAnnotations = getAttribute(Names.RuntimeVisibleTypeAnnotations);
        StaticObject runtimeVisibleTypeAnnotations = rawRuntimeVisibleTypeAnnotations != null
                        ? StaticObject.wrap(rawRuntimeVisibleTypeAnnotations.getData(), meta)
                        : StaticObject.NULL;
        if (meta.getJavaVersion().java15OrLater()) {
            meta.java_lang_reflect_Field_init.invokeDirectSpecial(
                            /* this */ instance,
                            /* declaringKlass */ getDeclaringKlass().mirror(),
                            /* name */ meta.getStrings().intern(getName()),
                            /* type */ resolveTypeKlass().mirror(),
                            /* modifiers */ getModifiers(),
                            /* trustedFinal */ isTrustedFinal(),
                            /* slot */ getSlot(),
                            /* signature */ meta.toGuestString(getGenericSignature()),
                            /* annotations */ runtimeVisibleAnnotations);
        } else {
            meta.java_lang_reflect_Field_init.invokeDirectSpecial(
                            /* this */ instance,
                            /* declaringKlass */ getDeclaringKlass().mirror(),
                            /* name */ meta.getStrings().intern(getName()),
                            /* type */ resolveTypeKlass().mirror(),
                            /* modifiers */ getModifiers(),
                            /* slot */ getSlot(),
                            /* signature */ meta.toGuestString(getGenericSignature()),
                            /* annotations */ runtimeVisibleAnnotations);
        }
        meta.HIDDEN_FIELD_KEY.setHiddenObject(instance, this);
        meta.HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS.setHiddenObject(instance, runtimeVisibleTypeAnnotations);
        return instance;
    }

    public int getConstantValueIndex() {
        ConstantValueAttribute a = (ConstantValueAttribute) getAttribute(Names.ConstantValue);
        if (a == null) {
            return 0;
        }
        int constantValueIndex = a.getConstantValueIndex();
        assert constantValueIndex != 0;
        return constantValueIndex;
    }

    /**
     * Helper class that uses an assumption to switch between two "stable" states efficiently.
     * Copied from DebuggerSession with modifications to the set method to make it thread safe (but
     * slower on the slow path).
     */
    static final class StableBoolean {

        @CompilationFinal private volatile Assumption unchanged;
        @CompilationFinal private volatile boolean value;

        StableBoolean(boolean initialValue) {
            this.value = initialValue;
            this.unchanged = Truffle.getRuntime().createAssumption("Unchanged boolean");
        }

        @SuppressFBWarnings(value = "UG_SYNC_SET_UNSYNC_GET", justification = "The get method returns a volatile field.")
        public boolean get() {
            if (!unchanged.isValid()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            return value;
        }

        /**
         * This method needs to be behind a boundary due to the fact that compiled code will
         * constant fold the value, hence the first check might yield a wrong result.
         */
        @CompilerDirectives.TruffleBoundary
        public synchronized void set(boolean value) {
            if (this.value != value) {
                this.value = value;
                Assumption old = this.unchanged;
                unchanged = Truffle.getRuntime().createAssumption("Unchanged boolean");
                old.invalidate();
            }
        }
    }
    // endregion jdwp-specific
}
