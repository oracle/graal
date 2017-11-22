int returnInt() {
	return 42;
}

int add(int a, int b) {
	return a + b;
}

int functionPointer(int (*func)(int, int)) {
	return func(40, 2);
}