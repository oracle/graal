package com.oracle.graal.truffle;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;

public final class DefaultTruffleStamp {

    private static final Object NO_TYPE = new Object();
    private static final Class<?> NO_CLASS = new Object[]{}.getClass();
    private static final Object NO_INSTANCE = new Object();

    private DefaultTruffleStamp() {
        // do not instantiate
    }

    public static TruffleStamp getInstance() {
        return UninitializedStamp.INSTANCE;
    }

    private static TruffleStamp createStamp(Object value) {
        if (value instanceof Object[]) {
            return ArrayStamp.INSTANCE.joinValue(value);
        } else if (!useInstanceStamps(value)) {
            Object type = getTypeIdentifier(value);
            if (type != NO_TYPE) {
                return new TypeStamp(value.getClass(), type);
            } else {
                return new ClassStamp(value.getClass());
            }
        } else {
            return new InstanceStamp(value);
        }
    }

    private static boolean useInstanceStamps(Object value) {
        if (TruffleCompilerOptions.TruffleSplittingTypeInstanceStamps.getValue()) {
            if (value instanceof TypedObject) {
                return true;
            }
        }
        if (TruffleCompilerOptions.TruffleSplittingClassInstanceStamps.getValue()) {
            return true;
        }
        return false;
    }

    private static Object getTypeIdentifier(Object value) {
        if (value instanceof TypedObject) {
            return ((TypedObject) value).getTypeIdentifier();
        }
        return NO_TYPE;
    }

    private static abstract class ValueStamp implements TruffleStamp {

        Class<?> getClazz() {
            return NO_CLASS;
        }

        Object getType() {
            return NO_TYPE;
        }

        Object getInstance() {
            return NO_INSTANCE;
        }

        @Override
        public final TruffleStamp joinValue(Object value) {
            return join(createStamp(value));
        }

        public final String toStringShort() {
            return getClass().getAnnotation(NodeInfo.class).shortName();
        }

        @Override
        public String toString() {
            return toStringShort();
        }

    }

    @NodeInfo(shortName = "U")
    private static final class UninitializedStamp extends ValueStamp {
        private static final UninitializedStamp INSTANCE = new UninitializedStamp();

        @Override
        public TruffleStamp join(TruffleStamp p) {
            return p;
        }

        @Override
        public boolean isCompatible(Object value) {
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == INSTANCE;
        }

        @Override
        public int hashCode() {
            return 1;
        }

    }

    @NodeInfo(shortName = "I")
    private static final class InstanceStamp extends ValueStamp {

        private final Object instance;
        private final Class<?> clazz;
        private final Object type;

        public InstanceStamp(Object instance) {
            this.instance = instance;
            this.type = instance != null ? getTypeIdentifier(instance) : NO_TYPE;
            this.clazz = instance != null ? instance.getClass() : NO_CLASS;
        }

        @Override
        public TruffleStamp join(TruffleStamp p) {
            if (p instanceof ValueStamp) {
                ValueStamp ap = ((ValueStamp) p);
                if (ap.getInstance() != NO_INSTANCE) {
                    if (isCompatible(ap.getInstance())) {
                        return this;
                    }
                }
                if (ap.getType() != NO_TYPE) {
                    if (type == ap.getType()) {
                        return new TypeStamp(clazz, type);
                    }
                }
                if (ap.getClazz() != NO_CLASS) {
                    if (clazz == ap.getClazz()) {
                        return new ClassStamp(clazz);
                    }
                }
            }
            return GenericStamp.INSTANCE;
        }

        @Override
        public boolean isCompatible(Object value) {
            return instance == value;
        }

        @Override
        Object getInstance() {
            return instance;
        }

        @Override
        Object getType() {
            return type;
        }

        @Override
        Class<?> getClazz() {
            return clazz;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof InstanceStamp && ((InstanceStamp) obj).instance == instance;
        }

        @Override
        public int hashCode() {
            if (instance != null) {
                return instance.hashCode();
            } else {
                return 31;
            }
        }

        @Override
        public String toString() {
            return String.format("%s=0x%8h", toStringShort(), System.identityHashCode(instance));
        }

    }

    @NodeInfo(shortName = "T")
    private static final class TypeStamp extends ValueStamp {

        private final Class<?> clazz;
        private final Object type;

        public TypeStamp(Class<?> clazz, Object type) {
            this.clazz = clazz;
            this.type = type;
            assert type != NO_TYPE;
        }

        @Override
        public TruffleStamp join(TruffleStamp p) {
            if (p instanceof ValueStamp) {
                ValueStamp ap = ((ValueStamp) p);

                if (ap.getType() != NO_TYPE) {
                    if (type == ap.getType()) {
                        return this;
                    }
                }
                if (ap.getClazz() != NO_CLASS) {
                    if (clazz == ap.getClazz()) {
                        return new ClassStamp(clazz);
                    }
                }

            }
            return GenericStamp.INSTANCE;
        }

        @Override
        public boolean isCompatible(Object value) {
            return getTypeIdentifier(value) == type;
        }

        @Override
        Class<?> getClazz() {
            return clazz;
        }

        @Override
        Object getType() {
            return type;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof TypeStamp && ((TypeStamp) obj).type == type;
        }

        @Override
        public int hashCode() {
            return type.hashCode();
        }

        @Override
        public String toString() {
            return String.format("%s=0x%8h", toStringShort(), System.identityHashCode(type));
        }

    }

    @NodeInfo(shortName = "C")
    private static final class ClassStamp extends ValueStamp {

        private final Class<?> clazz;

        public ClassStamp(Class<?> clazz) {
            this.clazz = clazz;
        }

        @Override
        public boolean isCompatible(Object value) {
            return value.getClass() == clazz;
        }

        @Override
        public TruffleStamp join(TruffleStamp p) {
            if (p instanceof ValueStamp) {
                ValueStamp ap = ((ValueStamp) p);
                if (ap.getClazz() != NO_CLASS) {
                    if (clazz == ap.getClazz()) {
                        return this;
                    }
                }
            }
            return GenericStamp.INSTANCE;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ClassStamp && ((ClassStamp) obj).clazz == clazz;
        }

        @Override
        public int hashCode() {
            return clazz.hashCode();
        }

        @Override
        Class<?> getClazz() {
            return clazz;
        }

        @Override
        public String toString() {
            return String.format("%s=%-10s", toStringShort(), clazz.getSimpleName());
        }

    }

    private final static class ArrayStamp implements TruffleStamp {

        private static final ArrayStamp INSTANCE = new ArrayStamp(getInstance());

        private static final int MAX_STAMPED_ARGUMENTS = 10;
        private static final int GENERIC_LENGTH = -1;
        private static final int UNINITIALIZED_LENGTH = -2;

        private final TruffleStamp[] stampArray;
        private final int length;

        public ArrayStamp(TruffleStamp stamp) {
            this.stampArray = new TruffleStamp[MAX_STAMPED_ARGUMENTS];
            Arrays.fill(this.stampArray, stamp);
            this.length = UNINITIALIZED_LENGTH;
        }

        public ArrayStamp(TruffleStamp[] profiledTypes, int length) {
            this.stampArray = profiledTypes;
            this.length = length;
        }

        public boolean isCompatible(Object value) {
            if (!(value instanceof Object[])) {
                return false;
            }
            Object[] array = (Object[]) value;
            if ((length != array.length && length != GENERIC_LENGTH) || length == UNINITIALIZED_LENGTH) {
                return false;
            }
            TruffleStamp[] currentArray = this.stampArray;
            for (int i = 0; i < Math.min(array.length, currentArray.length); i++) {
                if (!currentArray[i].isCompatible(array[i])) {
                    return false;
                }
            }
            return true;
        }

        public TruffleStamp join(TruffleStamp p) {
            if (!(p instanceof ArrayStamp)) {
                return GenericStamp.INSTANCE;
            }
            ArrayStamp other = (ArrayStamp) p;
            int newLength = profileLength(other.length);

            TruffleStamp[] newArray = this.stampArray;
            TruffleStamp[] otherArray = other.stampArray;
            assert newArray.length == otherArray.length;

            for (int i = 0; i < newArray.length; i++) {
                TruffleStamp thisStamp = newArray[i];
                TruffleStamp newStamp = thisStamp.join(otherArray[i]);

                if (thisStamp != newStamp) {
                    if (newArray == this.stampArray) {
                        newArray = Arrays.copyOf(newArray, newArray.length);
                    }
                    newArray[i] = newStamp;
                }
            }
            return create(newArray, newLength);
        }

        public TruffleStamp joinValue(Object value) {
            if (!(value instanceof Object[])) {
                return GenericStamp.INSTANCE;
            }
            Object[] array = (Object[]) value;
            int newLength = profileLength(array.length);
            TruffleStamp[] newArray = this.stampArray;
            for (int i = 0; i < Math.min(array.length, newArray.length); i++) {
                TruffleStamp oldStamp = newArray[i];
                Object newValue = array[i];
                if (!oldStamp.isCompatible(newValue)) {
                    if (newArray == this.stampArray) {
                        newArray = Arrays.copyOf(newArray, newArray.length);
                    }
                    newArray[i] = oldStamp.joinValue(newValue);
                }
            }
            return create(newArray, newLength);
        }

        private TruffleStamp create(TruffleStamp[] newArray, int newLength) {
            if (newLength != this.length || newArray != this.stampArray) {
                return new ArrayStamp(newArray != null ? newArray : stampArray, newLength);
            } else {
                return this;
            }
        }

        private int profileLength(int arrayLength) {
            int nextLength = this.length;
            switch (nextLength) {
                case UNINITIALIZED_LENGTH:
                    return arrayLength;
                case GENERIC_LENGTH:
                    return nextLength;
                default:
                    if (nextLength != arrayLength) {
                        if (arrayLength == UNINITIALIZED_LENGTH) {
                            return nextLength;
                        } else {
                            return GENERIC_LENGTH;
                        }
                    } else {
                        return nextLength;
                    }
            }

        }

        @Override
        public int hashCode() {
            return 31 * (31 + length) + Arrays.hashCode(stampArray);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ArrayStamp)) {
                return false;
            }
            ArrayStamp a = (ArrayStamp) obj;
            if (a.length != length) {
                return false;
            }
            return Arrays.equals(a.stampArray, stampArray);
        }

        public String toStringShort() {
            if (length == 0) {
                return "[]";
            }
            StringBuilder b = new StringBuilder("[");
            for (TruffleStamp stamp : stampArray) {
                if (stamp instanceof UninitializedStamp) {
                    continue;
                }
                if (stamp instanceof ValueStamp) {
                    b.append(((ValueStamp) stamp).toStringShort());
                } else if (stamp instanceof ArrayStamp) {
                    b.append(((ArrayStamp) stamp).toStringShort());
                } else {
                    b.append("?");
                }
            }
            b.append("]");

            b.append(".").append(formatLength());
            return b.toString();
        }

        @Override
        public String toString() {
            return "Array(length=" + formatLength() + ", " + Arrays.toString(stampArray) + ")";
        }

        private String formatLength() {
            String lengthString;
            if (length == GENERIC_LENGTH) {
                lengthString = "G";
            } else if (length == UNINITIALIZED_LENGTH) {
                lengthString = "U";
            } else {
                lengthString = String.valueOf(this.length);
            }
            return lengthString;
        }

    }

    @NodeInfo(shortName = "G")
    private static final class GenericStamp extends ValueStamp {

        private static final GenericStamp INSTANCE = new GenericStamp();

        @Override
        public boolean isCompatible(Object value) {
            return true;
        }

        @Override
        public TruffleStamp join(TruffleStamp p) {
            return this;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == INSTANCE;
        }

        @Override
        public int hashCode() {
            return 31;
        }

    }

}
