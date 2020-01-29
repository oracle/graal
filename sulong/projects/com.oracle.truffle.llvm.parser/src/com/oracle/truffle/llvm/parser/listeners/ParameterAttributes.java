/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.listeners;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.llvm.parser.model.attributes.Attribute;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesCodeEntry;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesGroup;
import com.oracle.truffle.llvm.parser.scanner.RecordBuffer;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.types.Type;

public class ParameterAttributes implements ParserListener {

    // https://github.com/llvm-mirror/llvm/blob/release_38/include/llvm/Bitcode/LLVMBitCodes.h#L110
    private static final int PARAMATTR_CODE_ENTRY_OLD = 1;
    private static final int PARAMATTR_CODE_ENTRY = 2;
    private static final int PARAMATTR_GRP_CODE_ENTRY = 3;

    // http://llvm.org/docs/BitCodeFormat.html#paramattr-grp-code-entry-record
    private static final int WELL_KNOWN_ATTRIBUTE_KIND = 0;
    private static final int WELL_KNOWN_INTEGER_ATTRIBUTE_KIND = 1;
    private static final int STRING_ATTRIBUTE_KIND = 3;
    private static final int STRING_VALUE_ATTRIBUTE_KIND = 4;
    private static final int BYVAL_ATTRIBUTE_KIND = 5;
    private static final int TYPED_BYVAL_ATTRIBUTE_KIND = 6;

    // stores attributes defined in PARAMATTR_GRP_CODE_ENTRY
    private final List<AttributesGroup> attributes = new ArrayList<>();

    // store code entries defined in PARAMATTR_CODE_ENTRY
    private final List<AttributesCodeEntry> parameterCodeEntry = new ArrayList<>();

    private final Types types;

    public ParameterAttributes(Types types) {
        this.types = types;
    }

    /**
     * Get ParsedAttributeGroup by Bitcode index.
     *
     * @param idx index as it was defined in the LLVM-Bitcode, means starting with 1
     * @return found attributeGroup, or otherwise an empty List
     */
    public AttributesCodeEntry getCodeEntry(long idx) {
        if (idx <= 0 || parameterCodeEntry.size() < idx) {
            return AttributesCodeEntry.EMPTY;
        }

        return parameterCodeEntry.get((int) (idx - 1));
    }

    @Override
    public void record(RecordBuffer buffer) {
        int id = buffer.getId();
        switch (id) {
            case PARAMATTR_CODE_ENTRY_OLD:
                decodeOldCodeEntry(buffer);
                break;

            case PARAMATTR_CODE_ENTRY:
                decodeCodeEntry(buffer);
                break;

            case PARAMATTR_GRP_CODE_ENTRY:
                decodeGroupCodeEntry(buffer);
                break;

            default:
                break;
        }
    }

    private void decodeOldCodeEntry(RecordBuffer buffer) {
        final List<AttributesGroup> attrGroup = new ArrayList<>();

        for (int i = 0; i < buffer.size(); i += 2) {
            attrGroup.add(decodeOldGroupCodeEntry(buffer.read(), buffer.read()));
        }

        parameterCodeEntry.add(new AttributesCodeEntry(attrGroup));
    }

    private static AttributesGroup decodeOldGroupCodeEntry(long paramIdx, long attr) {
        AttributesGroup group = new AttributesGroup(-1, paramIdx);

        if ((attr & (1L << 0)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.ZEROEXT));
        }
        if ((attr & (1L << 1)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.SIGNEXT));
        }
        if ((attr & (1L << 2)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.NORETURN));
        }
        if ((attr & (1L << 3)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.INREG));
        }
        if ((attr & (1L << 4)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.SRET));
        }
        if ((attr & (1L << 5)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.NOUNWIND));
        }
        if ((attr & (1L << 6)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.NOALIAS));
        }
        if ((attr & (1L << 7)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.BYVAL));
        }
        if ((attr & (1L << 8)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.NEST));
        }
        if ((attr & (1L << 9)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.READNONE));
        }
        if ((attr & (1L << 10)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.READONLY));
        }
        if ((attr & (1L << 11)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.NOINLINE));
        }
        if ((attr & (1L << 12)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.ALWAYSINLINE));
        }
        if ((attr & (1L << 13)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.OPTSIZE));
        }
        if ((attr & (1L << 14)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.SSP));
        }
        if ((attr & (1L << 15)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.SSPREQ));
        }
        if ((attr & (0xFFL << 16)) != 0) {
            final int align = (int) ((attr >> 16) & 0xFFL);
            group.addAttribute(new Attribute.KnownIntegerValueAttribute(Attribute.Kind.ALIGN, align));
        }
        if ((attr & (1L << 32)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.NOCAPTURE));
        }
        if ((attr & (1L << 33)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.NOREDZONE));
        }
        if ((attr & (1L << 34)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.NOIMPLICITFLOAT));
        }
        if ((attr & (1L << 35)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.NAKED));
        }
        if ((attr & (1L << 36)) != 0) {
            group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.INLINEHINT));
        }
        if ((attr & (0x07L << 37)) != 0) {
            final int alignstack = 1 << ((int) ((attr >> 37) & 0x07L) - 1);
            group.addAttribute(new Attribute.KnownIntegerValueAttribute(Attribute.Kind.ALIGNSTACK, alignstack));
        }

        return group;
    }

    private void decodeCodeEntry(RecordBuffer buffer) {
        final List<AttributesGroup> attrGroup = new ArrayList<>();

        while (buffer.remaining() > 0) {
            long groupId = buffer.read();
            for (AttributesGroup attr : attributes) {
                if (attr.getGroupId() == groupId) {
                    attrGroup.add(attr);
                    break;
                }
            }
        }

        if (attrGroup.size() != buffer.size()) {
            throw new LLVMParserException("Mismatching number of defined and found attributes in AttributesGroup");
        }

        parameterCodeEntry.add(new AttributesCodeEntry(attrGroup));
    }

    private void decodeGroupCodeEntry(RecordBuffer buffer) {
        final long groupId = buffer.read();
        final long paramIdx = buffer.read();

        AttributesGroup group = new AttributesGroup(groupId, paramIdx);
        attributes.add(group);

        while (buffer.remaining() > 0) {
            int type = buffer.readInt();
            switch (type) {
                case WELL_KNOWN_ATTRIBUTE_KIND: {
                    Attribute.Kind attr = Attribute.Kind.decode(buffer.read());
                    group.addAttribute(new Attribute.KnownAttribute(attr));
                    break;
                }

                case WELL_KNOWN_INTEGER_ATTRIBUTE_KIND: {
                    Attribute.Kind attr = Attribute.Kind.decode(buffer.read());
                    group.addAttribute(new Attribute.KnownIntegerValueAttribute(attr, buffer.read()));
                    break;
                }

                case STRING_ATTRIBUTE_KIND: {
                    String strAttr = readString(buffer);
                    group.addAttribute(new Attribute.StringAttribute(strAttr));
                    break;
                }

                case STRING_VALUE_ATTRIBUTE_KIND: {
                    String strAttr = readString(buffer);
                    String strVal = readString(buffer);
                    group.addAttribute(new Attribute.StringValueAttribute(strAttr, strVal));
                    break;
                }

                case BYVAL_ATTRIBUTE_KIND: {
                    Attribute.Kind attr = Attribute.Kind.decode(buffer.read());
                    if (attr == Attribute.Kind.BYVAL) {
                        group.addAttribute(new Attribute.KnownAttribute(Attribute.Kind.BYVAL));
                    }
                    break;
                }

                case TYPED_BYVAL_ATTRIBUTE_KIND: {
                    Attribute.Kind attr = Attribute.Kind.decode(buffer.read());
                    if (attr == Attribute.Kind.BYVAL) {
                        final Type valueType = types.get(buffer.read());
                        group.addAttribute(new Attribute.KnownTypedAttribute(Attribute.Kind.BYVAL, valueType));
                    }
                    break;
                }

                default:
                    throw new LLVMParserException("Unexpected code of attribute group: " + type);
            }
        }
    }

    private static String readString(RecordBuffer buffer) {
        StringBuilder sb = new StringBuilder();
        while (true) {
            long value = buffer.read();
            if (value == 0) {
                break;
            }
            sb.append((char) value);
        }
        return sb.toString();
    }
}
