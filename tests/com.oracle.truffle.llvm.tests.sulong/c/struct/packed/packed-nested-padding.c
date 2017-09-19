#include <stdlib.h>
#include <stdio.h>

/* 
 * Tests that the padding of the middle structure does not depend on the largest alignment
 * of the packed inner structure
*/

struct innerStruct {
	double d;
	int i1;
	double d1;
	int i2;
}__attribute__((packed));

struct middleStruct {
	struct innerStruct st;
	int i;
};

struct outerStruct {
	struct middleStruct st;
	int i;
	char c;
};

int main() {
	struct outerStruct st = { {{0.1, 2, 0.3, 4}, 5}, 6, '7'};

	int* addr = (int*)&(st.st);
	printf("%d", *(addr + 7));
}

