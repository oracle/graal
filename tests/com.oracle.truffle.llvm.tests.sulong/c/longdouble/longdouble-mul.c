#define _GNU_SOURCE
#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <math.h>
#include "longdouble.h"

int main(void)
{
	long double x = M_PIl;
	long double y = M_El;
	long double z;

	memset(&z, 0, sizeof(z));

	z = x * y;

	printfp("result", &z);

	return 0;
}
