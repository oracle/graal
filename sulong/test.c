#include <stdio.h>

int main(int argc, char* argv[]) {
	if(argc<=1) {
		printf("Wieder keine Argumente...");
		return 0;
	}
	int a=65;
	int idx=0;
	while(a>0) {
		printf("%c - ",a);
		a=*(((char*)argv[1])+idx);
	idx++;
	}
	return 0;
}
