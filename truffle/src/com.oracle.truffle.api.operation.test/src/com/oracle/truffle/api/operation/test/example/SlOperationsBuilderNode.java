package com.oracle.truffle.api.operation.test.example;

import java.util.ArrayList;

import com.oracle.truffle.api.operation.test.example.SlOperationsBuilderImpl.SlOperationsLabel;

class SlOperationsBuilderNode {

    private enum ValueResult {
        NEVER,
        ALWAYS,
        DIVERGES
    }

    public enum Type {
        // primitive
        BLOCK(0, -1),
        IF_THEN(0, 2),
        IF_THEN_ELSE(0, 3),
        WHILE(0, 2),
        CONST_OBJECT(1, 0),
        CONST_LONG(1, 0),
        LOAD_LOCAL(1, 0),
        STORE_LOCAL(1, 1),
        LOAD_ARGUMENT(1, 0),
        RETURN(0, 1),
        LABEL(1, 0),
        BRANCH(1, 0),
        // custom
        OP_ADD_OPERATION(0, 2),
        OP_LESS_THAN_OPERATION(0, 2);

        public final int argCount;
        public final int childCount;

        private Type(int argCount, int childCount) {
            this.argCount = argCount;
            this.childCount = childCount;
        }
    }

    public final SlOperationsBuilderNode.Type type;
    public final Object[] arguments;
    public final SlOperationsBuilderNode[] children;
    private final SlOperationsBuilderNode.ValueResult producesValue;

    public SlOperationsBuilderNode(SlOperationsBuilderNode.Type type, Object[] arguments, SlOperationsBuilderNode[] children) {
        assert arguments.length == type.argCount;
        assert type.childCount == -1 || children.length == type.childCount;
        this.type = type;
        this.arguments = arguments;
        this.children = children;

        switch (type) {
            case BLOCK:
                if (children.length == 0)
                    producesValue = ValueResult.NEVER;
                else
                    producesValue = children[children.length - 1].producesValue;
                break;
            case IF_THEN_ELSE:
                assert children[0].producesValue != ValueResult.NEVER;
                if (children[1].producesValue == ValueResult.NEVER || children[2].producesValue == ValueResult.NEVER) {
                    producesValue = ValueResult.NEVER;
                } else {
                    producesValue = ValueResult.ALWAYS;
                }
                break;
            case IF_THEN:
            case WHILE:
            case STORE_LOCAL:
                assert children[0].producesValue != ValueResult.NEVER;
                producesValue = ValueResult.NEVER;
                break;
            case OP_ADD_OPERATION:
            case OP_LESS_THAN_OPERATION:
            case CONST_OBJECT:
            case CONST_LONG:
            case LOAD_LOCAL:
            case LOAD_ARGUMENT:
                for (SlOperationsBuilderNode child : children) {
                    assert child.producesValue != ValueResult.NEVER : "" + this;
                }
                producesValue = ValueResult.ALWAYS;
                break;
            case RETURN:
            case BRANCH:
                producesValue = ValueResult.DIVERGES;
                break;
            case LABEL:
                producesValue = ValueResult.NEVER;
                break;
            default:
                throw new RuntimeException();

        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        sb.append(type);
        for (SlOperationsBuilderNode child : children) {
            sb.append(" ");
            sb.append(child);
        }
        sb.append(")");
        return sb.toString();
    }

    final int build(byte[] bc, int inBci, ArrayList<Object> consts) {
        int bci = inBci;
        // System.out.println("building " + this);

        switch (type) {
            case BLOCK: {
                for (int i = 0; i < children.length; i++) {
                    bci = children[i].build(bc, bci, consts);
                    if (i != children.length - 1 && children[i].producesValue == ValueResult.ALWAYS) {
                        bc[bci++] = SlOperationsBuilderImpl.PRIM_OP_POP;
                    }
                }
                break;
            }
            case IF_THEN: {
                bci = children[0].build(bc, bci, consts);

                bc[bci++] = SlOperationsBuilderImpl.PRIM_OP_JUMP_FALSE;
                int targetDest = bci;
                bci += SlOperationsBuilderImpl.IMM_DEST_LENGTH;

                bci = children[1].build(bc, bci, consts);
                if (children[1].producesValue == ValueResult.ALWAYS)
                    bc[bci++] = SlOperationsBuilderImpl.PRIM_OP_POP;

                SlOperationsBuilderImpl.putDestination(bc, targetDest, bci);
                break;
            }
            case IF_THEN_ELSE: {
                bci = children[0].build(bc, bci, consts);

                bc[bci++] = SlOperationsBuilderImpl.PRIM_OP_JUMP_FALSE;
                int elseDest = bci;
                bci += SlOperationsBuilderImpl.IMM_DEST_LENGTH;

                bci = children[1].build(bc, bci, consts);
                if (children[1].producesValue == ValueResult.ALWAYS && producesValue == ValueResult.NEVER)
                    bc[bci++] = SlOperationsBuilderImpl.PRIM_OP_POP;

                bc[bci++] = SlOperationsBuilderImpl.PRIM_OP_UNCOND_JUMP;
                int endDest = bci;
                bci += SlOperationsBuilderImpl.IMM_DEST_LENGTH;

                SlOperationsBuilderImpl.putDestination(bc, elseDest, bci);

                bci = children[2].build(bc, bci, consts);
                if (children[2].producesValue == ValueResult.ALWAYS && producesValue == ValueResult.NEVER)
                    bc[bci++] = SlOperationsBuilderImpl.PRIM_OP_POP;

                SlOperationsBuilderImpl.putDestination(bc, endDest, bci);
                break;
            }
            case WHILE: {
                int startBci = bci;

                bci = children[0].build(bc, bci, consts);

                bc[bci++] = SlOperationsBuilderImpl.PRIM_OP_JUMP_FALSE;
                int endDest = bci;
                bci += SlOperationsBuilderImpl.IMM_DEST_LENGTH;

                bci = children[1].build(bc, bci, consts);
                if (children[1].producesValue == ValueResult.ALWAYS)
                    bc[bci++] = SlOperationsBuilderImpl.PRIM_OP_POP;

                bc[bci++] = SlOperationsBuilderImpl.PRIM_OP_UNCOND_JUMP;
                SlOperationsBuilderImpl.putDestination(bc, bci, startBci);
                bci += SlOperationsBuilderImpl.IMM_DEST_LENGTH;

                SlOperationsBuilderImpl.putDestination(bc, endDest, bci);
                break;
            }
            case LABEL: {
                SlOperationsLabel label = (SlOperationsLabel) arguments[0];
                label.resolve(bc, bci);
                break;
            }
            case BRANCH: {
                SlOperationsLabel label = (SlOperationsLabel) arguments[0];
                bc[bci++] = SlOperationsBuilderImpl.PRIM_OP_UNCOND_JUMP;
                label.putValue(bc, bci);
                bci += SlOperationsBuilderImpl.IMM_DEST_LENGTH;
                break;
            }
            case CONST_OBJECT:
            case CONST_LONG:
            case LOAD_LOCAL:
            case STORE_LOCAL:
            case LOAD_ARGUMENT:
            case RETURN:
            case OP_ADD_OPERATION:
            case OP_LESS_THAN_OPERATION: {
                for (SlOperationsBuilderNode child : children) {
                    bci = child.build(bc, bci, consts);
                }

                switch (type) {
                    case CONST_LONG:
                    case CONST_OBJECT: {
                        bc[bci++] = SlOperationsBuilderImpl.PRIM_OP_CONST_OBJECT;
                        SlOperationsBuilderImpl.putConst(bc, bci, arguments[0], consts);
                        bci += SlOperationsBuilderImpl.IMM_CONST_LENGTH;
                        break;
                    }
                    case LOAD_LOCAL: {
                        bc[bci++] = SlOperationsBuilderImpl.PRIM_OP_LOAD_LOCAL;
                        SlOperationsBuilderImpl.BYTES.putShort(bc, bci, (short) (int) arguments[0]);
                        bci += 2;
                        break;
                    }
                    case STORE_LOCAL: {
                        bc[bci++] = SlOperationsBuilderImpl.PRIM_OP_STORE_LOCAL;
                        SlOperationsBuilderImpl.BYTES.putShort(bc, bci, (short) (int) arguments[0]);
                        bci += 2;
                        break;
                    }
                    case LOAD_ARGUMENT: {
                        bc[bci++] = SlOperationsBuilderImpl.PRIM_OP_LOAD_ARGUMENT;
                        SlOperationsBuilderImpl.BYTES.putShort(bc, bci, (short) (int) arguments[0]);
                        bci += 2;
                        break;
                    }
                    case RETURN: {
                        bc[bci++] = SlOperationsBuilderImpl.PRIM_OP_RETURN;
                        break;
                    }
                    case OP_ADD_OPERATION: {
                        bc[bci++] = SlOperationsBuilderImpl.OP_ADD_OPERATION;
                        break;
                    }
                    case OP_LESS_THAN_OPERATION: {
                        bc[bci++] = SlOperationsBuilderImpl.OP_LESS_THAN_OPERATION;
                        break;
                    }
                }
                break;
            }
            default: {
                throw new RuntimeException("" + type);
            }
        }

        return bci;
    }

    public static String dump(byte[] bc, Object[] consts) {
        StringBuilder sb = new StringBuilder();

        for (int bci = 0; bci < bc.length;) {
            sb.append(String.format(" %04x ", bci));
            switch (bc[bci]) {
                case SlOperationsBuilderImpl.PRIM_OP_NOP:
                    sb.append("nop");
                    bci += 1;
                    break;
                case SlOperationsBuilderImpl.PRIM_OP_JUMP_FALSE:
                    sb.append(String.format("jp_f  %04x", SlOperationsBuilderImpl.BYTES.getShort(bc, bci + 1)));
                    bci += 3;
                    break;

                case SlOperationsBuilderImpl.PRIM_OP_UNCOND_JUMP:
                    sb.append(String.format("jp    %04x", SlOperationsBuilderImpl.BYTES.getShort(bc, bci + 1)));
                    bci += 3;
                    break;
                case SlOperationsBuilderImpl.PRIM_OP_CONST_OBJECT: {
                    int index = SlOperationsBuilderImpl.BYTES.getShort(bc, bci + 1);
                    sb.append(String.format("const #%d  // (%s) %s", index, consts[index].getClass().getName(), consts[index]));
                    bci += 3;
                    break;
                }
                case SlOperationsBuilderImpl.PRIM_OP_POP:
                    sb.append("pop");
                    bci += 1;
                    break;
                case SlOperationsBuilderImpl.PRIM_OP_RETURN:
                    sb.append("ret");
                    bci += 1;
                    break;
                case SlOperationsBuilderImpl.PRIM_OP_LOAD_ARGUMENT:
                    sb.append(String.format("ldarg %04x", SlOperationsBuilderImpl.BYTES.getShort(bc, bci + 1)));
                    bci += 3;
                    break;
                case SlOperationsBuilderImpl.PRIM_OP_LOAD_LOCAL:
                    sb.append(String.format("ldloc %04x", SlOperationsBuilderImpl.BYTES.getShort(bc, bci + 1)));
                    bci += 3;
                    break;
                case SlOperationsBuilderImpl.PRIM_OP_STORE_LOCAL:
                    sb.append(String.format("stloc %04x", SlOperationsBuilderImpl.BYTES.getShort(bc, bci + 1)));
                    bci += 3;
                    break;
                case SlOperationsBuilderImpl.OP_ADD_OPERATION:
                    sb.append("op    AddOperation");
                    bci += 1;
                    break;
                case SlOperationsBuilderImpl.OP_LESS_THAN_OPERATION:
                    sb.append("op    LessThanOperation");
                    bci += 1;
                    break;
                default:
                    sb.append(String.format("unknown %02x", bc[bci]));
                    bci += 1;
                    break;
            }
            sb.append("\n");
        }

        return sb.toString();
    }

}