#include <truffle.h>

int *needsStack() {
	int a = 5;
	return &a;
}

int noStack() {
	int a = 3 + 4;
	return a;
}

int main() {
	return 0;
}
