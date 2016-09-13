package uk.ac.man.cs.llvm.ir.module.records;

public enum DwTagRecord {

    DW_TAG_UNKNOW(-1),

    DW_TAG_ARRAY_TYPE(1),
    DW_TAG_ENUMERATION_TYPE(4),
    DW_TAG_FORMAL_PARAMTER(5),
    DW_TAG_LEXICAL_BLOCK(11),
    DW_TAG_MEMBER(13),
    DW_TAG_POINTER_TYPE(15),
    DW_TAG_REFERENCE_TYPE(16),
    DW_TAG_COMPILE_UNIT(17),
    DW_TAG_STRUCTURE_TYPE(19),
    DW_TAG_SUBROUTINE_TYPE(21),
    DW_TAG_TYPEDEF(22),
    DW_TAG_UNION_TYPE(23),
    DW_TAG_INHERITANCE(28),
    DW_TAG_PTR_TO_MEMBER_TYPE(31),
    DW_TAG_SUBRANGE_TYPE(33),
    DW_TAG_BASE_TYPE(36),
    DW_TAG_CONST_TYPE(38),
    DW_TAG_ENUMERATOR(40),
    DW_TAG_FILE_TYPE(41),
    DW_TAG_SUBPROGRAM(46),
    DW_TAG_VARIABLE(52),
    DW_TAG_VOLATILE_TYPE(53),
    DW_TAG_RESTRICTED_TYPE(55),
    DW_TAG_AUTO_VARIABLE(256),
    DW_TAG_ARG_VARIABLE(257),
    DW_TAG_VECTOR_TYPE(259);

    public static DwTagRecord decode(long code) {
        code &= 0x0000FFFF; // only the lower bytes are interesting for us
        for (DwTagRecord cc : values()) {
            if (cc.code() == code) {
                return cc;
            }
        }
        return DW_TAG_UNKNOW;
    }

    private final int code;

    DwTagRecord(int code) {
        this.code = code;
    }

    public long code() {
        return code;
    }
}
