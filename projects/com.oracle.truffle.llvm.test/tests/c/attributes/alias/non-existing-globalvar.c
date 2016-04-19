#include<stdlib.h>

int alias __attribute__((alias("non_existing")));
int alias2 __attribute__((alias("alias")));

int func() {
	return alias2 + alias;
}

int main() {
	return 0;
}
