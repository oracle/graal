package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.runtime.Attribute;

/**
 * The ConstantValue attribute is a fixed-length attribute in the attributes table of a field_info
 * structure (§4.5). A ConstantValue attribute represents the value of a constant expression (JLS
 * §15.28), and is used as follows:
 * <ul>
 * <li>If the ACC_STATIC flag in the access_flags item of the field_info structure is set, then the
 * field represented by the field_info structure is assigned the value represented by its
 * ConstantValue attribute as part of the initialization of the class or interface declaring the
 * field (§5.5). This occurs prior to the invocation of the class or interface initialization method
 * of that class or interface (§2.9).
 * <li>Otherwise, the Java Virtual Machine must silently ignore the attribute.
 * </ul>
 * There may be at most one ConstantValue attribute in the attributes table of a field_info
 * structure.
 */
public final class ConstantValueAttribute extends Attribute {
    public static final Symbol<Name> NAME = Name.ConstantValue;
    private final int constantvalueIndex;

    public ConstantValueAttribute(int constantvalueIndex) {
        super(NAME, null);
        this.constantvalueIndex = constantvalueIndex;
    }

    public int getConstantvalueIndex() {
        return constantvalueIndex;
    }
}
