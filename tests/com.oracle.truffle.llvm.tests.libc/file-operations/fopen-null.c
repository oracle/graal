#include <stdio.h>
#include <assert.h>

int main() { assert(fopen(NULL, "rb") == NULL); }
