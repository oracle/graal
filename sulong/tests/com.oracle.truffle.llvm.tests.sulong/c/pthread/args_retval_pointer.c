// code from https://w3.cs.jmu.edu/kirkpams/OpenCSF/Books/cs361/html/POSIXArgs.html

#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>



/* struct for passing arguments to the child thread */
struct args {
  int a;
  int b;
};

/* struct for returning results from the child thread */
struct results {
  int sum;
  int difference;
  int product;
  int quotient;
  int modulus;
};

void *calculator(void *_args)
{
  /* Cast the args to the usable struct type */
  struct args *args = (struct args *) _args;

  /* Allocate heap space for this thread's results */
  struct results *results = calloc(sizeof(struct results), 1);
  results->sum        = args->a + args->b;
  results->difference = args->a - args->b;
  results->product    = args->a * args->b;
  results->quotient   = args->a / args->b;
  results->modulus    = args->a % args->b;

  /* De-allocate input data and return the pointer to results on heap */
  free(args);
  pthread_exit(results);
}

int main()
{
  /* Create 5 threads, each calling calculator() */
  pthread_t child[5];
  int i;

  /* Allocate arguments and create the threads */
  struct args *args[5] = { NULL, NULL, NULL, NULL, NULL };
  for (i = 0; i < 5; i++)
  {
    /* args[i] is a pointer to the arguments for thread i */
    args[i] = calloc(sizeof(struct args), 1);

    /* thread 0 calls calculator(1,1)
       thread 1 calls calculator(2,4)
       thread 2 calls calculator(3,9)
       and so on... */
    args[i]->a = i + 1;
    args[i]->b = (i + 1) * (i + 1);
    pthread_create(&child[i], NULL, calculator, args[i]);
  }

  /* Allocate an array of pointers to result structs */
  struct results *results[5];
  for (i = 0; i < 5; i++)
  {
    /* Passing results[i] by reference creates (void**) */
    pthread_join(child[i], (void **) &results[i]);

    /* Print each of the results and free the struct */
    printf("Calculator (%d, %2d) ==> ", i+1, (i+1)*(i+1));
    printf("+:%3d;   ", results[i]->sum);
    printf("-:%3d;   ", results[i]->difference);
    printf("*:%3d;   ", results[i]->product);
    printf("/:%3d;   ", results[i]->quotient);
    printf("%%:%3d\n", results[i]->modulus);
    free(results[i]);
  }
}

