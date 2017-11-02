#include<stdio.h>
#include<stdlib.h>

struct var_bit_width_t {
  unsigned x:2;
  unsigned y:1;
  unsigned z:1;
};


void switch_inside(struct var_bit_width_t *var) {
  switch (var->x) {
  case 0:
    printf("Zero\n");
    break;
  case 1:
    printf("One\n");
    break;
  case 2:
    printf("Two\n");
    break;
  case 3:
    printf("Three\n");
    break;
  default:
    printf("Error\n");
  }
}

int main() {
  struct var_bit_width_t *arg = malloc(sizeof(struct var_bit_width_t));
  arg->x = 1;
  switch_inside(arg);
}
