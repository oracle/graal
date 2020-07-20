/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.regex.charset;

import java.util.Arrays;

public class UnicodeGeneralCategoriesGenerator {

    public static void main(String[] args) {
        // The following aggregate general categories are defined in Unicode Standard Annex 44,
        // Section 5.7.1. (http://www.unicode.org/reports/tr44/#GC_Values_Table).
        String[][] generalCategories = new String[][]{
                        {"gc=LC", "Lu", "Ll", "Lt"},
                        {"gc=L", "Lu", "Ll", "Lt", "Lm", "Lo"},
                        {"gc=M", "Mn", "Mc", "Me"},
                        {"gc=N", "Nd", "Nl", "No"},
                        {"gc=P", "Pc", "Pd", "Ps", "Pe", "Pi", "Pf", "Po"},
                        {"gc=S", "Sm", "Sc", "Sk", "So"},
                        {"gc=Z", "Zs", "Zl", "Zp"},
                        {"gc=C", "Cc", "Cf", "Cs", "Co", "Cn"}
        };
        System.out.println(String.format("/*\n" +
                        " * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.\n" +
                        " * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.\n" +
                        " *\n" +
                        " * The Universal Permissive License (UPL), Version 1.0\n" +
                        " *\n" +
                        " * Subject to the condition set forth below, permission is hereby granted to any\n" +
                        " * person obtaining a copy of this software, associated documentation and/or\n" +
                        " * data (collectively the \"Software\"), free of charge and under any and all\n" +
                        " * copyright rights in the Software, and any and all patent rights owned or\n" +
                        " * freely licensable by each licensor hereunder covering either (i) the\n" +
                        " * unmodified Software as contributed to or provided by such licensor, or (ii)\n" +
                        " * the Larger Works (as defined below), to deal in both\n" +
                        " *\n" +
                        " * (a) the Software, and\n" +
                        " *\n" +
                        " * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if\n" +
                        " * one is included with the Software each a \"Larger Work\" to which the Software\n" +
                        " * is contributed by such licensors),\n" +
                        " *\n" +
                        " * without restriction, including without limitation the rights to copy, create\n" +
                        " * derivative works of, display, perform, and distribute the Software and make,\n" +
                        " * use, sell, offer for sale, import, export, have made, and have sold the\n" +
                        " * Software and the Larger Work(s), and to sublicense the foregoing rights on\n" +
                        " * either these or other terms.\n" +
                        " *\n" +
                        " * This license is subject to the following condition:\n" +
                        " *\n" +
                        " * The above copyright notice and either this complete permission notice or at a\n" +
                        " * minimum a reference to the UPL must be included in all copies or substantial\n" +
                        " * portions of the Software.\n" +
                        " *\n" +
                        " * THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\n" +
                        " * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\n" +
                        " * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\n" +
                        " * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\n" +
                        " * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\n" +
                        " * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE\n" +
                        " * SOFTWARE.\n" +
                        " */\n" +
                        "package com.oracle.truffle.regex.charset;\n" +
                        "\n" +
                        "import org.graalvm.collections.EconomicMap;\n" +
                        "\n" +
                        "/**\n" +
                        " * Generated by {@link UnicodeGeneralCategoriesGenerator}.\n" +
                        " */\n" +
                        "class UnicodeGeneralCategories {\n" +
                        "\n" +
                        "    private static final EconomicMap<String, CodePointSet> GENERAL_CATEGORIES = EconomicMap.create(%d);\n" +
                        "\n" +
                        "    static CodePointSet getGeneralCategory(String name) {\n" +
                        "        return GENERAL_CATEGORIES.get(name);\n" +
                        "    }\n" +
                        "\n" +
                        "    static {", generalCategories.length));
        for (String[] category : generalCategories) {
            String name = category[0];
            String[] props = Arrays.copyOfRange(category, 1, category.length);
            System.out.println(String.format("        GENERAL_CATEGORIES.put(\"%s\", CodePointSet.createNoDedup(%s));", name, unionOfGeneralCategories(props).dumpRaw()));
        }
        System.out.println("    }");
        System.out.println("}");
    }

    /**
     * @param generalCategoryNames *Abbreviated* names of general categories
     */
    private static CodePointSet unionOfGeneralCategories(String... generalCategoryNames) {
        CodePointSet set = CodePointSet.getEmpty();
        for (String generalCategoryName : generalCategoryNames) {
            set = set.union(UnicodePropertyData.retrieveProperty("gc=" + generalCategoryName));
        }
        return set;
    }
}
