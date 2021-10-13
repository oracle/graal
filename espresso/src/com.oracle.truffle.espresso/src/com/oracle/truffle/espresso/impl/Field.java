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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.staticobject.StaticShape;
import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.SignatureAttribute;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.ModifiedUTF8;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.jdwp.api.FieldBreakpoint;
import com.oracle.truffle.espresso.jdwp.api.FieldRef;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * Represents a resolved Espresso field.
 */
public final class Field extends Member<Type> implements FieldRef {

    public static final Field[] EMPTY_ARRAY = new Field[0];

    final LinkedField linkedField;
    private final ObjectKlass.KlassVersion holder;

    private final RuntimeConstantPool pool;
    @CompilationFinal private volatile Klass typeKlassCache;
    @CompilationFinal private Symbol<ModifiedUTF8> genericSignature;

    private boolean removedByRedefinition;
    private Field compatibleField;
    private StaticShape<ExtensionFieldObject.ExtensionFieldObjectFactory> extensionShape;

    public Field(ObjectKlass.KlassVersion holder, LinkedField linkedField, RuntimeConstantPool pool) {
        this(holder, linkedField, pool, false);
    }

    private Field(ObjectKlass.KlassVersion holder, LinkedField linkedField, RuntimeConstantPool pool, boolean isSynthetic) {
        this.linkedField = linkedField;
        this.holder = holder;
        this.pool = pool;
        if (linkedField.isRedefineAdded() && !isSynthetic) {
            StaticShape.Builder shapeBuilder = StaticShape.newBuilder(getDeclaringKlass().getEspressoLanguage());
            shapeBuilder.property(linkedField, linkedField.getParserField().getPropertyType(), isFinalFlagSet());
            this.extensionShape = shapeBuilder.build(ExtensionFieldObject.FieldStorageObject.class, ExtensionFieldObject.ExtensionFieldObjectFactory.class);
        }
    }

    public static Field synthetic(Field field) {
        // update holder to latest klass version to ensure we
        // only re-resolve again when the class is redefined
        return new Field(field.holder.getKlass().getKlassVersion(), field.linkedField, field.pool, true);
    }

    @Override
    public Symbol<Name> getName() {
        return linkedField.getName();
    }

    public Symbol<Type> getType() {
        return linkedField.getType();
    }

    public void removeByRedefintion() {
        removedByRedefinition = true;
    }

    public boolean isRemoved() {
        return removedByRedefinition;
    }

    public boolean needsReResolution() {
        return !holder.getAssumption().isValid();
    }

    public Attribute[] getAttributes() {
        return linkedField.getParserField().getAttributes();
    }

    public Symbol<ModifiedUTF8> getGenericSignature() {
        if (genericSignature == null) {
            SignatureAttribute attr = (SignatureAttribute) linkedField.getAttribute(SignatureAttribute.NAME);
            if (attr == null) {
                genericSignature = ModifiedUTF8.fromSymbol(getType());
            } else {
                genericSignature = pool.symbolAt(attr.getSignatureIndex());
            }
        }
        return genericSignature;
    }

    public boolean isHidden() {
        return linkedField.isHidden();
    }

    public boolean isTrustedFinal() {
        ObjectKlass k = getDeclaringKlass();
        return isFinalFlagSet() && (isStatic() || k.isHidden() || k.isRecord());
    }

    public JavaKind getKind() {
        return linkedField.getKind();
    }

    @Override
    public int getModifiers() {
        return linkedField.getFlags() & Constants.JVM_RECOGNIZED_FIELD_MODIFIERS;
    }

    @Override
    public ObjectKlass getDeclaringKlass() {
        return holder.getKlass();
    }

    /**
     * The slot serves as the position in the `field table` of the ObjectKlass.
     */
    public int getSlot() {
        return linkedField.getSlot();
    }

    @Override
    public String toString() {
        return getDeclaringKlass().getNameAsString() + "." + getName() + ": " + getType();
    }

    public Klass resolveTypeKlass() {
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

    public Attribute getAttribute(Symbol<Name> attrName) {
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

    @TruffleBoundary
    public void checkLoadingConstraints(StaticObject loader1, StaticObject loader2) {
        getDeclaringKlass().getContext().getRegistries().checkLoadingConstraint(getType(), loader1, loader2);
    }

    // region Field accesses

    // region Generic
    public Object get(StaticObject obj) {
        return get(obj, false);
    }

    public Object get(StaticObject obj, boolean forceVolatile) {
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
            default: throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
    }

    public void set(StaticObject obj, Object value) {
        set(obj, value, false);
    }

    public void set(StaticObject obj, Object value, boolean forceVolatile) {
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
            default: throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
    }

    public boolean getAsBoolean(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsBoolean(meta, obj, defaultIfNull, false);
    }

    public boolean getAsBoolean(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asBoolean(val, defaultIfNull);
    }

    public byte getAsByte(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsByte(meta, obj, defaultIfNull, false);
    }

    public byte getAsByte(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asByte(val, defaultIfNull);
    }

    public short getAsShort(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsShort(meta, obj, defaultIfNull, false);
    }

    public short getAsShort(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asShort(val, defaultIfNull);
    }

    public char getAsChar(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsChar(meta, obj, defaultIfNull, false);
    }

    public char getAsChar(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asChar(val, defaultIfNull);
    }

    public int getAsInt(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsInt(meta, obj, defaultIfNull, false);
    }

    public int getAsInt(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asInt(val, defaultIfNull);
    }

    public float getAsFloat(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsFloat(meta, obj, defaultIfNull, false);
    }

    public float getAsFloat(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asFloat(val, defaultIfNull);
    }

    public long getAsLong(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsLong(meta, obj, defaultIfNull, false);
    }

    public long getAsLong(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asLong(val, defaultIfNull);
    }

    public double getAsDouble(Meta meta, StaticObject obj, boolean defaultIfNull) {
        return getAsDouble(meta, obj, defaultIfNull, false);
    }

    public double getAsDouble(Meta meta, StaticObject obj, boolean defaultIfNull, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asDouble(val, defaultIfNull);
    }

    public StaticObject getAsObject(Meta meta, StaticObject obj) {
        return getAsObject(meta, obj, false);
    }

    public StaticObject getAsObject(Meta meta, StaticObject obj, boolean forceVolatile) {
        Object val = get(obj, forceVolatile);
        return meta.asObject(val);
    }
    // endregion Generic

    // region Object

    // region helper methods
    private Object getObjectHelper(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            return linkedField.getObjectVolatile(obj);
        } else {
            return linkedField.getObject(obj);
        }
    }

    private void setObjectHelper(StaticObject obj, Object value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (isVolatile() || forceVolatile) {
            linkedField.setObjectVolatile(obj, value);
        } else {
            linkedField.setObject(obj, value);
        }
    }
    // endregion helper methods

    // To access hidden fields, use the dedicated `(g|s)etHiddenObjectField` methods
    public StaticObject getObject(StaticObject obj) {
        return getObject(obj, false);
    }

    private StaticObject getObject(StaticObject obj, boolean forceVolatile) {
        assert !isHidden() : this + " is hidden, use getHiddenObject";
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return (StaticObject) compatibleField.getObjectHelper(obj, forceVolatile);
            } else {
                return getExtensionObject(obj).getObject(this, forceVolatile);
            }
        } else {
            return (StaticObject) getObjectHelper(obj, forceVolatile);
        }
    }

    public void setObject(StaticObject obj, Object value) {
        setObject(obj, value, false);
    }

    public void setObject(StaticObject obj, Object value, boolean forceVolatile) {
        assert !isHidden() : this + " is hidden, use setHiddenObject";
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                compatibleField.setObjectHelper(obj, value, forceVolatile);
            } else {
                getExtensionObject(obj).setObject(this, value, forceVolatile);
            }
        } else {
            setObjectHelper(obj, value, forceVolatile);
        }
    }

    public StaticObject getAndSetObject(StaticObject obj, StaticObject value) {
        obj.checkNotForeign();
        assert !isHidden() : this + " is hidden";
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.getAndSetObject(obj, value);
            } else {
                return getExtensionObject(obj).getAndSetObject(this, value);
            }
        } else {
            return (StaticObject) linkedField.getAndSetObject(obj, value);
        }
    }

    public boolean compareAndSwapObject(StaticObject obj, Object before, Object after) {
        obj.checkNotForeign();
        assert !isHidden() : this + " is hidden";
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.compareAndSwapObject(obj, before, after);
            } else {
                return getExtensionObject(obj).compareAndSwapObject(this, before, after);
            }
        } else {
            return linkedField.compareAndSwapObject(obj, before, after);
        }
    }

    public StaticObject compareAndExchangeObject(StaticObject obj, Object before, Object after) {
        obj.checkNotForeign();
        assert !isHidden() : this + " is hidden";
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.compareAndExchangeObject(obj, before, after);
            } else {
                return getExtensionObject(obj).compareAndExchangeObject(this, before, after);
            }
        } else {
            return (StaticObject) linkedField.compareAndExchangeObject(obj, before, after);
        }
    }

    // region hidden Object
    public Object getHiddenObject(StaticObject obj) {
        return getHiddenObject(obj, false);
    }

    public Object getHiddenObject(StaticObject obj, boolean forceVolatile) {
        assert isHidden() : this + " is not hidden, use getObject";
        return getObjectHelper(obj, forceVolatile);
    }

    public void setHiddenObject(StaticObject obj, Object value) {
        setHiddenObject(obj, value, false);
    }

    public void setHiddenObject(StaticObject obj, Object value, boolean forceVolatile) {
        assert isHidden() : this + " is not hidden, use setObject";
        setObjectHelper(obj, value, forceVolatile);
    }
    // endregion Hidden Object
    // endregion Object

    // region boolean
    public boolean getBoolean(StaticObject obj) {
        return getBoolean(obj, false);
    }

    public boolean getBoolean(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.getBoolean(obj, forceVolatile);
            } else {
                return getExtensionObject(obj).getBoolean(this, forceVolatile);
            }
        } else {
            if (isVolatile() || forceVolatile) {
                return linkedField.getBooleanVolatile(obj);
            } else {
                return linkedField.getBoolean(obj);
            }
        }
    }

    public void setBoolean(StaticObject obj, boolean value) {
        setBoolean(obj, value, false);
    }

    public void setBoolean(StaticObject obj, boolean value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                compatibleField.setBoolean(obj, value, forceVolatile);
            } else {
                getExtensionObject(obj).setBoolean(this, value, forceVolatile);
            }
        } else if (isVolatile() || forceVolatile) {
            linkedField.setBooleanVolatile(obj, value);
        } else {
            linkedField.setBoolean(obj, value);
        }
    }

    public boolean compareAndSwapBoolean(StaticObject obj, boolean before, boolean after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.compareAndSwapBoolean(obj, before, after);
            } else {
                return getExtensionObject(obj).compareAndSwapBoolean(this, before, after);
            }
        } else {
            return linkedField.compareAndSwapBoolean(obj, before, after);
        }
    }

    public boolean compareAndExchangeBoolean(StaticObject obj, boolean before, boolean after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.compareAndExchangeBoolean(obj, before, after);
            } else {
                return getExtensionObject(obj).compareAndExchangeBoolean(this, before, after);
            }
        } else {
            return linkedField.compareAndExchangeBoolean(obj, before, after);
        }
    }
    // endregion boolean

    // region byte
    public byte getByte(StaticObject obj) {
        return getByte(obj, false);
    }

    public byte getByte(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.getByte(obj, forceVolatile);
            } else {
                return getExtensionObject(obj).getByte(this, forceVolatile);
            }
        } else {
            if (isVolatile() || forceVolatile) {
                return linkedField.getByteVolatile(obj);
            } else {
                return linkedField.getByte(obj);
            }
        }
    }

    public void setByte(StaticObject obj, byte value) {
        setByte(obj, value, false);
    }

    public void setByte(StaticObject obj, byte value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                compatibleField.setByte(obj, value, forceVolatile);
            } else {
                getExtensionObject(obj).setByte(this, value, forceVolatile);
            }
        } else if (isVolatile() || forceVolatile) {
            linkedField.setByteVolatile(obj, value);
        } else {
            linkedField.setByte(obj, value);
        }
    }

    public boolean compareAndSwapByte(StaticObject obj, byte before, byte after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.compareAndSwapByte(obj, before, after);
            } else {
                return getExtensionObject(obj).compareAndSwapByte(this, before, after);
            }
        } else {
            return linkedField.compareAndSwapByte(obj, before, after);
        }
    }

    public byte compareAndExchangeByte(StaticObject obj, byte before, byte after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.compareAndExchangeByte(obj, before, after);
            } else {
                return getExtensionObject(obj).compareAndExchangeByte(this, before, after);
            }
        } else {
            return linkedField.compareAndExchangeByte(obj, before, after);
        }
    }
    // endregion byte

    // region char
    public char getChar(StaticObject obj) {
        return getChar(obj, false);
    }

    public char getChar(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.getChar(obj, forceVolatile);
            } else {
                return getExtensionObject(obj).getChar(this, forceVolatile);
            }
        } else {
            if (isVolatile() || forceVolatile) {
                return linkedField.getCharVolatile(obj);
            } else {
                return linkedField.getChar(obj);
            }
        }
    }

    public void setChar(StaticObject obj, char value) {
        setChar(obj, value, false);
    }

    public void setChar(StaticObject obj, char value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                compatibleField.setChar(obj, value, forceVolatile);
            } else {
                getExtensionObject(obj).setChar(this, value, forceVolatile);
            }
        } else if (isVolatile() || forceVolatile) {
            linkedField.setCharVolatile(obj, value);
        } else {
            linkedField.setChar(obj, value);
        }
    }

    public boolean compareAndSwapChar(StaticObject obj, char before, char after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.compareAndSwapChar(obj, before, after);
            } else {
                return getExtensionObject(obj).compareAndSwapChar(this, before, after);
            }
        } else {
            return linkedField.compareAndSwapChar(obj, before, after);
        }
    }

    public char compareAndExchangeChar(StaticObject obj, char before, char after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.compareAndExchangeChar(obj, before, after);
            } else {
                return getExtensionObject(obj).compareAndExchangeChar(this, before, after);
            }
        } else {
            return linkedField.compareAndExchangeChar(obj, before, after);
        }
    }
    // endregion char

    // region double
    public double getDouble(StaticObject obj) {
        return getDouble(obj, false);
    }

    public double getDouble(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.getDouble(obj, forceVolatile);
            } else {
                return getExtensionObject(obj).getDouble(this, forceVolatile);
            }
        } else {
            if (isVolatile() || forceVolatile) {
                return linkedField.getDoubleVolatile(obj);
            } else {
                return linkedField.getDouble(obj);
            }
        }
    }

    public void setDouble(StaticObject obj, double value) {
        setDouble(obj, value, false);
    }

    public void setDouble(StaticObject obj, double value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                compatibleField.setDouble(obj, value, forceVolatile);
            } else {
                getExtensionObject(obj).setDouble(this, value, forceVolatile);
            }
        } else if (isVolatile() || forceVolatile) {
            linkedField.setDoubleVolatile(obj, value);
        } else {
            linkedField.setDouble(obj, value);
        }
    }

    public boolean compareAndSwapDouble(StaticObject obj, double before, double after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.compareAndSwapDouble(obj, before, after);
            } else {
                return getExtensionObject(obj).compareAndSwapDouble(this, before, after);
            }
        } else {
            return linkedField.compareAndSwapDouble(obj, before, after);
        }
    }

    public double compareAndExchangeDouble(StaticObject obj, double before, double after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.compareAndExchangeDouble(obj, before, after);
            } else {
                return getExtensionObject(obj).compareAndExchangeDouble(this, before, after);
            }
        } else {
            return linkedField.compareAndExchangeDouble(obj, before, after);
        }
    }
    // endregion double

    // region float
    public float getFloat(StaticObject obj) {
        return getFloat(obj, false);
    }

    public float getFloat(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.getFloat(obj, forceVolatile);
            } else {
                return getExtensionObject(obj).getFloat(this, forceVolatile);
            }
        } else {
            if (isVolatile() || forceVolatile) {
                return linkedField.getFloatVolatile(obj);
            } else {
                return linkedField.getFloat(obj);
            }
        }
    }

    public void setFloat(StaticObject obj, float value) {
        setFloat(obj, value, false);
    }

    public void setFloat(StaticObject obj, float value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                compatibleField.setFloat(obj, value, forceVolatile);
            } else {
                getExtensionObject(obj).setFloat(this, value, forceVolatile);
            }
        } else if (isVolatile() || forceVolatile) {
            linkedField.setFloatVolatile(obj, value);
        } else {
            linkedField.setFloat(obj, value);
        }
    }

    public boolean compareAndSwapFloat(StaticObject obj, float before, float after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.compareAndSwapFloat(obj, before, after);
            } else {
                return getExtensionObject(obj).compareAndSwapFloat(this, before, after);
            }
        } else {
            return linkedField.compareAndSwapFloat(obj, before, after);
        }
    }

    public float compareAndExchangeFloat(StaticObject obj, float before, float after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.compareAndExchangeFloat(obj, before, after);
            } else {
                return getExtensionObject(obj).compareAndExchangeFloat(this, before, after);
            }
        } else {
            return linkedField.compareAndExchangeFloat(obj, before, after);
        }
    }
    // endregion float

    // region int
    public int getInt(StaticObject obj) {
        return getInt(obj, false);
    }

    public int getInt(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.getInt(obj, forceVolatile);
            } else {
                return getExtensionObject(obj).getInt(this, forceVolatile);
            }
        } else {
            if (isVolatile() || forceVolatile) {
                return linkedField.getIntVolatile(obj);
            } else {
                return linkedField.getInt(obj);
            }
        }
    }

    public void setInt(StaticObject obj, int value) {
        setInt(obj, value, false);
    }

    public void setInt(StaticObject obj, int value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                compatibleField.setInt(obj, value, forceVolatile);
            } else {
                getExtensionObject(obj).setInt(this, value, forceVolatile);
            }
        } else if (isVolatile() || forceVolatile) {
            linkedField.setIntVolatile(obj, value);
        } else {
            linkedField.setInt(obj, value);
        }
    }

    public boolean compareAndSwapInt(StaticObject obj, int before, int after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.compareAndSwapInt(obj, before, after);
            } else {
                return getExtensionObject(obj).compareAndSwapInt(this, before, after);
            }
        } else {
            return linkedField.compareAndSwapInt(obj, before, after);
        }
    }

    public int compareAndExchangeInt(StaticObject obj, int before, int after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.compareAndExchangeInt(obj, before, after);
            } else {
                return getExtensionObject(obj).compareAndExchangeInt(this, before, after);
            }
        } else {
            return linkedField.compareAndExchangeInt(obj, before, after);
        }
    }
    // endregion int

    // region long
    public long getLong(StaticObject obj) {
        return getLong(obj, false);
    }

    public long getLong(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        assert getKind().needsTwoSlots();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.getLong(obj, forceVolatile);
            } else {
                return getExtensionObject(obj).getLong(this, forceVolatile);
            }
        } else {
            if (isVolatile() || forceVolatile) {
                return linkedField.getLongVolatile(obj);
            } else {
                return linkedField.getLong(obj);
            }
        }
    }

    public void setLong(StaticObject obj, long value) {
        setLong(obj, value, false);
    }

    public void setLong(StaticObject obj, long value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        assert getKind().needsTwoSlots();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                compatibleField.setLong(obj, value, forceVolatile);
            } else {
                getExtensionObject(obj).setLong(this, value, forceVolatile);
            }
        } else if (isVolatile() || forceVolatile) {
            linkedField.setLongVolatile(obj, value);
        } else {
            linkedField.setLong(obj, value);
        }
    }

    public boolean compareAndSwapLong(StaticObject obj, long before, long after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        assert getKind().needsTwoSlots();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.compareAndSwapLong(obj, before, after);
            } else {
                return getExtensionObject(obj).compareAndSwapLong(this, before, after);
            }
        } else {
            return linkedField.compareAndSwapLong(obj, before, after);
        }
    }

    public long compareAndExchangeLong(StaticObject obj, long before, long after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.compareAndExchangeLong(obj, before, after);
            } else {
                return getExtensionObject(obj).compareAndExchangeLong(this, before, after);
            }
        } else {
            return linkedField.compareAndExchangeLong(obj, before, after);
        }
    }
    // endregion long

    // region short
    public short getShort(StaticObject obj) {
        return getShort(obj, false);
    }

    public short getShort(StaticObject obj, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.getShort(obj, forceVolatile);
            } else {
                return getExtensionObject(obj).getShort(this, forceVolatile);
            }
        } else {
            if (isVolatile() || forceVolatile) {
                return linkedField.getShortVolatile(obj);
            } else {
                return linkedField.getShort(obj);
            }
        }
    }

    public void setShort(StaticObject obj, short value) {
        setShort(obj, value, false);
    }

    public void setShort(StaticObject obj, short value, boolean forceVolatile) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                compatibleField.setShort(obj, value, forceVolatile);
            } else {
                getExtensionObject(obj).setShort(this, value, forceVolatile);
            }
        } else if (isVolatile() || forceVolatile) {
            linkedField.setShortVolatile(obj, value);
        } else {
            linkedField.setShort(obj, value);
        }
    }

    public boolean compareAndSwapShort(StaticObject obj, short before, short after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.compareAndSwapShort(obj, before, after);
            } else {
                return getExtensionObject(obj).compareAndSwapShort(this, before, after);
            }
        } else {
            return linkedField.compareAndSwapShort(obj, before, after);
        }
    }

    public short compareAndExchangeShort(StaticObject obj, short before, short after) {
        obj.checkNotForeign();
        assert getDeclaringKlass().isAssignableFrom(obj.getKlass()) : this + " does not exist in " + obj.getKlass();
        if (linkedField.isRedefineAdded()) {
            if (hasCompatibleField()) {
                return compatibleField.compareAndExchangeShort(obj, before, after);
            } else {
                return getExtensionObject(obj).compareAndExchangeShort(this, before, after);
            }
        } else {
            return linkedField.compareAndExchangeShort(obj, before, after);
        }
    }
    // endregion short

    private ExtensionFieldObject getExtensionObject(StaticObject instance) {
        ExtensionFieldObject extensionFieldObject;
        if (isStatic()) {
            extensionFieldObject = getDeclaringKlass().getStaticExtensionFieldObject();
        } else {
            Field extensionField = holder.getKlass().getMeta().HIDDEN_OBJECT_EXTENSION_FIELD;
            Object object = extensionField.getHiddenObject(instance);
            if (object == null) {
                // create new instance Extension field object
                synchronized (instance) {
                    object = extensionField.getHiddenObject(instance);
                    if (object == null) {
                        CompilerDirectives.transferToInterpreter();
                        extensionFieldObject = new ExtensionFieldObject();
                        extensionField.setHiddenObject(instance, extensionFieldObject);
                    } else {
                        extensionFieldObject = (ExtensionFieldObject) object;
                    }
                }
            } else {
                extensionFieldObject = (ExtensionFieldObject) object;
            }
        }
        return extensionFieldObject;
    }

    // endregion Field accesses

    // region jdwp-specific
    @Override
    public byte getTagConstant() {
        return getKind().toTagConstant();
    }

    @Override
    public String getNameAsString() {
        return getName().toString();
    }

    @Override
    public String getTypeAsString() {
        return getType().toString();
    }

    @Override
    public String getGenericSignatureAsString() {
        Symbol<ModifiedUTF8> signature = getGenericSignature();
        return signature.toString();
    }

    @Override
    public Object getValue(Object self) {
        return get((StaticObject) self);
    }

    @Override
    public void setValue(Object self, Object value) {
        set((StaticObject) self, value);
    }

    private final StableBoolean hasActiveBreakpoints = new StableBoolean(false);

    // array with maximum size 2, one access info and/or one modification info.
    private FieldBreakpoint[] infos = null;

    @Override
    public boolean hasActiveBreakpoint() {
        return hasActiveBreakpoints.get();
    }

    @Override
    public FieldBreakpoint[] getFieldBreakpointInfos() {
        return infos;
    }

    @Override
    public void addFieldBreakpointInfo(FieldBreakpoint info) {
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
    public void removeFieldBreakpointInfo(int requestId) {
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
                    return;
                }
        }
    }

    public void setCompatibleField(Field field) {
        compatibleField = field;
    }

    public boolean hasCompatibleField() {
        return compatibleField != null;
    }

    public Field getCompatibleField() {
        return compatibleField;
    }

    public StaticShape<ExtensionFieldObject.ExtensionFieldObjectFactory> getExtensionShape() {
        return extensionShape;
    }

    public ObjectKlass.KlassVersion getKlassVersion() {
        return holder;
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
