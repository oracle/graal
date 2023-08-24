package com.oracle.truffle.regex.charset;

// Sets for Unicode general categories
class JavaGc {
    static final CodePointSet UNASSIGNED = category("Cn");
    static final CodePointSet UPPERCASE_LETTER = category("Lu");
    static final CodePointSet LOWERCASE_LETTER = category("Ll");
    static final CodePointSet TITLECASE_LETTER = category("Lt");
    static final CodePointSet MODIFIER_LETTER = category("Lm");
    static final CodePointSet OTHER_LETTER = category("Lo");
    static final CodePointSet NON_SPACING_MARK = category("Mn");
    static final CodePointSet ENCLOSING_MARK = category("Me");
    static final CodePointSet COMBINING_SPACING_MARK = category("Mc");
    static final CodePointSet DECIMAL_DIGIT_NUMBER = category("Nd");
    static final CodePointSet LETTER_NUMBER = category("Nl");
    static final CodePointSet OTHER_NUMBER = category("No");
    static final CodePointSet SPACE_SEPARATOR = category("Zs");
    static final CodePointSet LINE_SEPARATOR = category("Zl");
    static final CodePointSet PARAGRAPH_SEPARATOR = category("Zp");
    static final CodePointSet CONTROL = category("Cc");
    static final CodePointSet FORMAT = category("Cf");
    static final CodePointSet PRIVATE_USE = category("Co");
    static final CodePointSet SURROGATE = category("Cs");
    static final CodePointSet DASH_PUNCTUATION = category("Pd");
    static final CodePointSet START_PUNCTUATION = category("Ps");
    static final CodePointSet END_PUNCTUATION = category("Pe");
    static final CodePointSet CONNECTOR_PUNCTUATION = category("Pc");
    static final CodePointSet OTHER_PUNCTUATION = category("Po");
    static final CodePointSet MATH_SYMBOL = category("Sm");
    static final CodePointSet CURRENCY_SYMBOL = category("Sc");
    static final CodePointSet MODIFIER_SYMBOL = category("Sk");
    static final CodePointSet OTHER_SYMBOL = category("So");
    static final CodePointSet INITIAL_QUOTE_PUNCTUATION = category("Pi");
    static final CodePointSet FINAL_QUOTE_PUNCTUATION = category("Pf");

    public static CodePointSet category(String name) {
        return UnicodePropertyData.retrieveProperty("gc=" + name);
    }
}
