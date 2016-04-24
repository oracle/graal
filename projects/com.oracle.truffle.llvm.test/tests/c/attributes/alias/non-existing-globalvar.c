#include <stdlib.h>

#if __GNUC__
int non_existing = 3;
#endif

extern int asdf __attribute__((alias("non_existing")));
extern int asdf2 __attribute__((alias("asdf")));

int func() { return asdf2 + asdf; }

int main() { return 0; }
