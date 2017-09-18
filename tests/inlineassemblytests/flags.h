#ifndef __FLAGS_H__
#define __FLAGS_H__

#define CC_C 0x0001
#define CC_P 0x0004
#define CC_A 0x0010
#define CC_Z 0x0040
#define CC_S 0x0080
#define CC_O 0x0800

#define CC_MASK (CC_C | CC_P | CC_Z | CC_S | CC_O | CC_A)
#define CC_MASK8 (CC_C | CC_P | CC_Z | CC_S | CC_A)

#endif
