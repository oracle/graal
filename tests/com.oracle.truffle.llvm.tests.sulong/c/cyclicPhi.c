#include <stdio.h>

volatile int l = 29;

int main() {

	int j;
	for(j=l+1;j<33;j++) {
        printf("j = %d\n",j);
        printf("j - 1 = %d\n",j - 1);
    }
	
}