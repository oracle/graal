package com.oracle.truffle.espresso.types;

import java.util.EnumMap;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.MetaUtil;

/**
 * Manages creation and parsing of type descriptors ("field descriptors" in the JVMS).
 *
 * @see "https://docs.oracle.com/javase/specs/jvms/se10/html/jvms-4.html#jvms-4.3.2"
 */
public final class TypeDescriptors extends DescriptorCache<TypeDescriptor> {
    @Override
    protected TypeDescriptor create(String key) {
        return new TypeDescriptor(key);
    }

    private static final EnumMap<JavaKind, TypeDescriptor> primitives = new EnumMap<>(JavaKind.class);

    static {
        for (JavaKind kind : JavaKind.values()) {
            if (kind.isPrimitive()) {
                String key = String.valueOf(kind.getTypeChar());
                TypeDescriptor descriptor = new TypeDescriptor(key);
                primitives.put(kind, descriptor);
            }
        }
    }

    private TypeDescriptor builtin(Class<?> c) {
        String name = MetaUtil.toInternalName(c.getName());
        TypeDescriptor type = new TypeDescriptor(name);
        cache.put(name, type);
        return type;
    }

    public final TypeDescriptor OBJECT = builtin(Object.class);
    public final TypeDescriptor CLASS = builtin(Class.class);
    public final TypeDescriptor THROWABLE = builtin(Throwable.class);
    public final TypeDescriptor STRING = builtin(String.class);

    // Exceptions
    public final TypeDescriptor OUT_OF_MEMORY_ERROR = builtin(java.lang.OutOfMemoryError.class);
    public final TypeDescriptor NULL_POINTER_EXCEPTION = builtin(java.lang.NullPointerException.class);
    public final TypeDescriptor CLASS_CAST_EXCEPTION = builtin(java.lang.ClassCastException.class);
    public final TypeDescriptor ARRAY_STORE_EXCEPTION = builtin(java.lang.ArrayStoreException.class);
    public final TypeDescriptor ARITHMETIC_EXCEPTION = builtin(java.lang.ArithmeticException.class);
    public final TypeDescriptor STACK_OVERFLOW_ERROR = builtin(java.lang.StackOverflowError.class);
    public final TypeDescriptor ILLEGAL_MONITOR_STATE_EXCEPTION = builtin(java.lang.IllegalMonitorStateException.class);
    public final TypeDescriptor ILLEGAL_ARGUMENT_EXCEPTION = builtin(java.lang.IllegalArgumentException.class);

    @Override
    public synchronized TypeDescriptor lookup(String key) {
        if (key.length() == 1) {
            JavaKind kind = JavaKind.fromPrimitiveOrVoidTypeChar(key.charAt(0));
            TypeDescriptor value = primitives.get(kind);
            if (value != null) {
                return value;
            }
        }
        return super.lookup(key);
    }

    public static TypeDescriptor forPrimitive(JavaKind kind) {
        assert kind.isPrimitive();
        return primitives.get(kind);
    }

    /**
     * Parses a valid Java type descriptor.
     *
     * @param string the string from which to create a Java type descriptor
     * @param beginIndex the index within the string from which to start parsing
     * @param slashes specifies if package components in {@code string} are separated by {@code '/'}
     *            or {@code '.'}
     * @throws ClassFormatError if the type descriptor is not valid
     */
    public TypeDescriptor parse(String string, int beginIndex, boolean slashes) throws ClassFormatError {
        int endIndex = skipValidTypeDescriptor(string, beginIndex, slashes);
        char ch = string.charAt(beginIndex);
        if (endIndex == beginIndex + 1) {
            return forPrimitive(JavaKind.fromPrimitiveOrVoidTypeChar(ch));
        }
        return make(string.substring(beginIndex, endIndex));
    }

    /**
     * Verifies that a valid type descriptor is at {@code beginIndex} in {@code string}.
     *
     * @param slashes specifies if package components are separated by {@code '/'} or {@code '.'}
     * @return the index one past the valid type descriptor starting at {@code beginIndex}
     * @throws ClassFormatError if there is no valid type descriptor
     */
    @CompilerDirectives.TruffleBoundary
    public static int skipValidTypeDescriptor(String string, int beginIndex, boolean slashes) throws ClassFormatError {
        if (beginIndex >= string.length()) {
            throw new ClassFormatError("invalid type descriptor: " + string);
        }
        char ch = string.charAt(beginIndex);
        if (ch != '[' && ch != 'L') {
            return beginIndex + 1;
        }
        switch (ch) {
            case 'L': {
                if (slashes) {
                    // parse a slashified Java class name
                    final int endIndex = skipClassName(string, beginIndex + 1, '/');
                    if (endIndex > beginIndex + 1 && endIndex < string.length() && string.charAt(endIndex) == ';') {
                        return endIndex + 1;
                    }
                } else {
                    // parse a dottified Java class name and convert to slashes
                    final int endIndex = skipClassName(string, beginIndex + 1, '.');
                    if (endIndex > beginIndex + 1 && endIndex < string.length() && string.charAt(endIndex) == ';') {
                        return endIndex + 1;
                    }
                }
                throw new ClassFormatError("Invalid Java name " + string.substring(beginIndex));
            }
            case '[': {
                // compute the number of dimensions
                int index = beginIndex;
                while (index < string.length() && string.charAt(index) == '[') {
                    index++;
                }
                final int dimensions = index - beginIndex;
                if (dimensions > 255) {
                    throw new ClassFormatError("Array with more than 255 dimensions " + string.substring(beginIndex));
                }
                return skipValidTypeDescriptor(string, index, slashes);
            }
        }
        throw new ClassFormatError("Invalid type descriptor " + string.substring(beginIndex));
    }

    /**
     * Gets the type descriptor for the specified component type descriptor with the specified
     * number of dimensions. For example if the number of dimensions is 1, then this method will
     * return a descriptor for an array of the component type; if the number of dimensions is 2, it
     * will return a descriptor for an array of an array of the component type, etc.
     *
     * @param descriptor the type descriptor for the component type of the array
     * @param dimensions the number of array dimensions
     * @return the canonical type descriptor for the specified component type and dimensions
     */
    public TypeDescriptor getArrayDescriptorForDescriptor(TypeDescriptor descriptor, int dimensions) {
        assert dimensions > 0;
        String value = descriptor.toString();
        if (descriptor.getArrayDimensions() + dimensions > 255) {
            throw new ClassFormatError("Array type with more than 255 dimensions");
        }
        for (int i = 0; i < dimensions; ++i) {
            value = "[" + value;
        }
        return make(value);
    }

    private static int skipClassName(String string, int from, final char separator) throws ClassFormatError {
        int index = from;
        final int length = string.length();
        while (index < length) {
            char ch = string.charAt(index);
            if (ch == '.' || ch == '/') {
                if (separator != ch) {
                    return index;
                }
            } else if (ch == ';' || ch == '[') {
                return index;
            }
            index++;
        }
        return index;
    }

}