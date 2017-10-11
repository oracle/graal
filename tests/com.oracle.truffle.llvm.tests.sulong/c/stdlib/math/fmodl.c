#include <math.h>
#include <stdio.h>

int main(void)
{
	long double x = 15.31;
	long double y = 3.14;
	long double result = fmodl(x, y);
	printf("%g %% %g = %g\n", (double) x, (double) y, (double) result);
	return 0;
}
