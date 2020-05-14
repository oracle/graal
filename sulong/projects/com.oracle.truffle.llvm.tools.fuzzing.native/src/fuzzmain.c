#include <stdio.h>

void autogen_SD0(char* a, int* b, long* c, int d, long e, char f);

int main(){
	char a = 'a';
	int b = 42;
	long c = 74;
	autogen_SD0(&a, &b, &c, 1, 1, 1);
	printf("%d %d %ld \n", a, b, c);
	return 0;
}

