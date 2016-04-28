#include <stdlib.h>

#if __GNUC__
int non_existing() { return 0; }
#endif

int func() __attribute__((alias("non_existing")));
int func2() __attribute__((alias("func")));

int main() { return 0; }
