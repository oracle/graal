/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
import java.util.Optional;

import com.oracle.truffle.llvm.parser.model.attributes.AttributesCodeEntry;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesGroup;
import com.oracle.truffle.llvm.parser.model.attributes.KnownAttribute;
import com.oracle.truffle.llvm.parser.model.attributes.StringAttribute;
import com.oracle.truffle.llvm.parser.records.ParameterAttributeGroupRecord;
import com.oracle.truffle.llvm.runtime.LLVMLogger;

public class ParameterAttributes implements ParserListener {

    // https://github.com/llvm-mirror/llvm/blob/release_38/include/llvm/Bitcode/LLVMBitCodes.h#L110
    private static final int PARAMATTR_CODE_ENTRY_OLD = 1;
    private static final int PARAMATTR_CODE_ENTRY = 2;
    private static final int PARAMATTR_GRP_CODE_ENTRY = 3;

    // http://llvm.org/docs/BitCodeFormat.html#paramattr-grp-code-entry-record
    private static final int WELL_KNOWN_ATTRIBUTE_KIND = 0;
    private static final int WELL_KNOWN_INTEGER_ATTRIBUTE_KIND = 1;
    private static final int STRING_ATTRIBUTE_KIND = 3;
    private static final int STRING_VAULE_ATTRIBUTE_KIND = 4;

    // stores attributes defined in PARAMATTR_GRP_CODE_ENTRY
    private final List<AttributesGroup> attributes = new ArrayList<>();

    // store code entries defined in PARAMATTR_CODE_ENTRY
    private final List<AttributesCodeEntry> parameterCodeEntry = new ArrayList<>();

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
    public void record(long id, long[] args) {
        switch ((int) id) {
            case PARAMATTR_CODE_ENTRY_OLD:
                // Not required at the moment
                break;

            case PARAMATTR_CODE_ENTRY:
                decodeCodeEntry(args);
                break;

            case PARAMATTR_GRP_CODE_ENTRY:
                decodeGroupCodeEntry(args);
                break;

            default:
                LLVMLogger.info("unknow Parameter Attribute Id: " + id);
                break;
        }
    }

    private void decodeCodeEntry(long[] args) {
        final List<AttributesGroup> attrGroup = new ArrayList<>();

        for (long groupId : args) {
            // @formatter:off
            Optional<AttributesGroup> attr = attributes.stream().
                            filter(a -> a.getGroupId() == groupId).
                            findAny();
            // @formatter:on

            // attr should be present, otherwise the bitcode file is malformed
            attrGroup.add(attr.get());
        }

        parameterCodeEntry.add(new AttributesCodeEntry(attrGroup));
    }

    private void decodeGroupCodeEntry(long[] args) {
        int i = 0;

        final long groupId = args[i++];
        final long paramIdx = args[i++];

        AttributesGroup group = new AttributesGroup(groupId, paramIdx);
        attributes.add(group);

        while (i < args.length) {
            long type = args[i++];
            switch ((int) type) {
                case WELL_KNOWN_ATTRIBUTE_KIND: {
                    ParameterAttributeGroupRecord attr = ParameterAttributeGroupRecord.decode(args[i++]);
                    group.addAttibute(new KnownAttribute(attr));
                    break;
                }

                case WELL_KNOWN_INTEGER_ATTRIBUTE_KIND: {
                    ParameterAttributeGroupRecord attr = ParameterAttributeGroupRecord.decode(args[i++]);
                    group.addAttibute(new KnownAttribute(attr, args[i++]));
                    break;
                }

                case STRING_ATTRIBUTE_KIND: {
                    StringBuilder strAttr = new StringBuilder();
                    for (; args[i] != 0; i++) {
                        strAttr.append((char) args[i]);
                    }
                    i++;
                    group.addAttibute(new StringAttribute(strAttr.toString()));
                    break;
                }

                case STRING_VAULE_ATTRIBUTE_KIND: {
                    StringBuilder strAttr = new StringBuilder();
                    StringBuilder strVal = new StringBuilder();
                    for (; args[i] != 0; i++) {
                        strAttr.append((char) args[i]);
                    }
                    i++;
                    for (; args[i] != 0; i++) {
                        strVal.append((char) args[i]);
                    }
                    i++;
                    group.addAttibute(new StringAttribute(strAttr.toString(), strVal.toString()));
                    break;
                }

                default:
                    throw new RuntimeException("unexpected type: " + type);
            }
        }
    }

}
