package uk.ac.man.cs.llvm.ir.module.records;

public enum DwTagRecord {

    DW_TAG_ARRAY_TYPE(786433),
    DW_TAG_ENUMERATION_TYPE(786436),
    DW_TAG_LEXICAL_BLOCK(786443),
    DW_TAG_MEMBER(786445),
    DW_TAG_COMPILE_UNIT(786449),
    DW_TAG_SUBROUTINE_TYPE(786453),
    DW_TAG_SUBRANGE_TYPE(786465),
    DW_TAG_BASE_TYPE(786468),
    DW_TAG_ENUMERATOR(786472),
    DW_TAG_SUBPROGRAM(786478),
    DW_TAG_FILE_TYPE(786473),
    DW_TAG_VARIABLE(786484),
    DW_TAG_AUTO_VARIABLE(786688),
    DW_TAG_STRUCTURE_TYPE(8786451);

    public static DwTagRecord decode(long code) {
        for (DwTagRecord cc : values()) {
            if (cc.code() == code) {
                return cc;
            }
        }
        return null;
    }

    private final int code;

    DwTagRecord(int code) {
        this.code = code;
    }

    public long code() {
        return code;
    }
}
