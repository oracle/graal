#include <stdio.h>
#include <string.h>
#include <stdint.h>

int main(void)
{
	long double x, y;
	uint8_t* p = (uint8_t*) &x;
	uint8_t* q = (uint8_t*) &y;
	int i;
	memset(p, 0, sizeof(x));
	printf("raw bytes:");
	for(i = 0; i < sizeof(x); i++) {
		printf(" %02x", *p);
		p++;
	}
	printf("\n");
	p = (uint8_t*) &x;
	x = 15.31;
	printf("sizeof(long double) = %lu\n", sizeof(x));
	printf("raw bytes:");
	for(i = 0; i < sizeof(x); i++) {
		printf(" %02x", *p);
		*(q++) = *(p++);
	}
	printf("\n");
	printf("(x == y) = %d\n", x == y ? 1 : 0);
	return 0;
}
