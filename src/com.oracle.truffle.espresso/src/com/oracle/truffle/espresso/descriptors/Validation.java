package com.oracle.truffle.espresso.descriptors;

import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;

public final class Validation {
    private Validation() {
        /* no instances */
    }

    /**
     * Names of methods, fields, local variables, and formal parameters are stored as unqualified
     * names. An unqualified name must contain at least one Unicode code point and must not contain
     * any of the ASCII characters . ; [ / (that is, period or semicolon or left square bracket or
     * forward slash).
     *
     * Does not check that the byte sequence is well-formed modified-UTF8 string.
     */
    public static boolean validUnqualifiedName(ByteSequence bytes) {
        if (bytes.length() == 0) {
            return false;
        }
        for (int i = 0; i < bytes.length(); ++i) {
            char ch = (char) bytes.byteAt(i);
            if (ch == '.' || ch == ';' || ch == '[' || ch == '/') {
                return false;
            }
        }
        return true;
    }

    /**
     * Method names are further constrained so that, with the exception of the special method names
     * <init> and <clinit> (§2.9), they must not contain the ASCII characters < or > (that is, left
     * angle bracket or right angle bracket).
     *
     * Does not check that the byte sequence is well-formed modified-UTF8 string.
     */
    public static boolean validMethodName(ByteSequence bytes) {
        if (bytes.length() == 0) {
            return false;
        }
        char first = (char) bytes.byteAt(0);
        if (first == '<') {
            return bytes.contentEquals(Name.INIT) || bytes.contentEquals(Name.CLINIT);
        }
        for (int i = 0; i < bytes.length(); ++i) {
            char ch = (char) bytes.byteAt(i);
            if (ch == '.' || ch == ';' || ch == '[' || ch == '/' || ch == '<' || ch == '>') {
                return false;
            }
        }
        return true;
    }

    /**
     * For historical reasons, the syntax of binary names that appear in class file structures
     * differs from the syntax of binary names documented in JLS §13.1. In this internal form, the
     * ASCII periods (.) that normally separate the identifiers which make up the binary name are
     * replaced by ASCII forward slashes (/). The identifiers themselves must be unqualified names
     * (§4.2.2).
     */
    public static boolean validBinaryName(ByteSequence bytes) {
        if (bytes.length() == 0) {
            return false;
        }
        if (bytes.byteAt(0) == '/') {
            return false;
        }
        int prev = 0;
        for (int i = 0; i < bytes.length(); ++i) {
            while (i < bytes.length() && bytes.byteAt(i) != '/') {
                ++i;
            }
            if (!validUnqualifiedName(bytes.subSequence(prev, i - prev))) {
                return false;
            }
            prev = i + 1;
        }
        return true;
    }

    /**
     * Because arrays are objects, the opcodes anewarray and multianewarray can reference array
     * "classes" via CONSTANT_Class_info structures in the constant_pool table. For such array
     * classes, the name of the class is the descriptor of the array type.
     */
    public static boolean validClassNameEntry(ByteSequence bytes) {
        if (bytes.length() == 0) {
            return false;
        }
        if (bytes.byteAt(0) == '[') {
            return validTypeDescriptor(bytes, false);
        }
        return validBinaryName(bytes);
    }

    public static boolean validFieldDescriptor(ByteSequence bytes) {
        return validTypeDescriptor(bytes, false);
    }

    /**
     * Validates a type descriptor in internal form.
     *
     * <pre>
     * TypeDescriptor: BaseType | ObjectType | ArrayType
     * BaseType: B | C | D | F | I | J | S | Z | V (if allowVoid is true)
     * ObjectType: L ClassName ;
     * ArrayType: [ ComponentType
     * ComponentType: FieldDescriptor
     * </pre>
     * 
     * @param bytes Sequence to validate
     * @param allowVoid true, if the void (V) type is allowed. Fields and method parameters do not
     *            allow void.
     * @return true, if the descriptor represents a valid type descriptor in internal form.
     */
    public static boolean validTypeDescriptor(ByteSequence bytes, boolean allowVoid) {
        if (bytes.length() == 0) {
            return false;
        }
        char first = (char) bytes.byteAt(0);
        if (bytes.length() == 1) {
            return (first == 'V' && allowVoid) ||
                            first == 'B' ||
                            first == 'C' ||
                            first == 'D' ||
                            first == 'F' ||
                            first == 'I' ||
                            first == 'J' ||
                            first == 'S' ||
                            first == 'Z';
        }
        if (first == '[') {
            int dimensions = 0;
            while (dimensions < bytes.length() && bytes.byteAt(dimensions) == '[') {
                ++dimensions;
            }
            if (dimensions > Constants.MAX_ARRAY_DIMENSIONS) {
                return false;
            }
            // Arrays of void (V) are never allowed.
            return validFieldDescriptor(bytes.subSequence(dimensions, bytes.length() - dimensions));
        }
        if (first == 'L') {
            char last = (char) bytes.byteAt(bytes.length() - 1);
            if (last != ';') {
                return false;
            }
            return validBinaryName(bytes.subSequence(1, bytes.length() - 2));
        }
        return false;
    }

    /**
     * A method descriptor contains zero or more parameter descriptors, representing the types of
     * parameters that the method takes, and a return descriptor, representing the type of the value
     * (if any) that the method returns.
     * 
     * <pre>
     *     SignatureDescriptor: ( {FieldDescriptor} ) TypeDescriptor
     * </pre>
     */
    public static boolean validSignatureDescriptor(ByteSequence bytes) {
        if (bytes.length() < 3) { // shortest descriptor e.g. ()V
            return false;
        }
        if (bytes.byteAt(0) != '(') {
            return false;
        }
        int index = 1;
        while (index < bytes.length() && bytes.byteAt(index) != ')') {
            int prev = index;
            // skip array
            while (index < bytes.length() && bytes.byteAt(index) == '[') {
                ++index;
            }
            int dimensions = index - prev;
            if (dimensions > Constants.MAX_ARRAY_DIMENSIONS) {
                return false;
            }
            if (index >= bytes.length()) {
                return false;
            }
            if (bytes.byteAt(index) == 'L') {
                ++index;
                while (index < bytes.length() && bytes.byteAt(index) != ';') {
                    ++index;
                }
                if (index >= bytes.length()) {
                    return false;
                }
                assert bytes.byteAt(index) == ';';
                if (!validFieldDescriptor(bytes.subSequence(prev, index - prev + 1))) {
                    return false;
                }
                ++index; // skip ;
            } else {
                // Must be a non-void primitive.
                char ch = (char) bytes.byteAt(index);
                if (!(ch == 'B' ||
                                ch == 'C' ||
                                ch == 'D' ||
                                ch == 'F' ||
                                ch == 'I' ||
                                ch == 'J' ||
                                ch == 'S' ||
                                ch == 'Z')) {
                    return false;
                }
                ++index; // skip
            }
        }
        if (index >= bytes.length()) {
            return false;
        }
        assert bytes.byteAt(index) == ')';
        // Validate return type.
        return validTypeDescriptor(bytes.subSequence(index + 1, bytes.length() - index - 1), true);
    }

}
