// code from https://web.mst.edu/~ercal/284/ThreadExamples/Thread-phil.c

/*******************************************************************/ 
/*   Program Description:                                          */ 
/*   The main() uses pthread_create() to create 5 threads which    */ 
/*   represent 5 dining philosophers. A dinning philosopher follows*/ 
/*   the following rule to eat a dinner. First she picks up a      */ 
/*   chopstick from the left if available, otherwise she waits and */ 
/*   tries again. After grabbing the left chopstick, she will try  */ 
/*   to pick up the right one. If the right chopstick is available */ 
/*   she picks it up, otherwise she puts down the left chopstick   */ 
/*   and tries again from the beginning. When she gets two         */ 
/*   chopsticks, she will eat for a while.                         */ 
/*                                                                 */ 
/*         O                                                       */ 
/*       |   |                                                     */ 
/*     O       O                                                   */ 
/*      |     |                                                    */ 
/*       O | O                                                     */ 
/*                                                                 */ 
/*   "O" - dinning philosopher, "|" - chopstick                    */ 
/*                                                                 */ 
/*   This program tries to simulate one meal for 5 philosophers.   */ 
/*   Five chopsticks are global resources. Whenever a philosopher  */ 
/*   is going to use a chopstick, she needs to use.                */
/*                                                                 */
/* COMPILATION: gcc -o program program.c -lpthread      (*LINUX*)  */
/*                                                                 */
/*******************************************************************/ 
 
/*********************************
 * Modified by: Brian Sea
 * Date: July 10, 2003
 *
 * Changes list:
 * - Ported to POSIX threads, mutexes and conds
 * - Changed *rand48 functions to equivalent *rand() functions.  
 *   *rand48 functions are obselete.
 * - srand should only be done in main(), not in each philosopher.
 *********************************/

/******************************** 
Modified by: Joe Henson  &   Philip Alt 
Date:        Feb 25 
 
Changes list: 
 - Added variables to count the number of meals eaten by each philosopher 
 - Added termination conditions to the threads (counter for the number of meals eaten) 
 - Added the "food settling" sleep statement to "balance" the number of meals eaten 
 - Added errorchecking at the thread join 
*********************************/ 
 
 
 
#include<stdio.h> 
#include<pthread.h>


#define thread_num 5 
#define max_meals 20 
 
/* initial status of chopstick */ 
/* 1 - the chopstick is available */ 
/* 0 - the chopstick is not available */ 
int chopstick[thread_num]={1,1,1,1,1}; 
 
/* mutex locks for each chopstick */ 
pthread_mutex_t m[thread_num];  
 
/* philosopher ID */ 
int p[thread_num]={0,1,2,3,4}; 
 
/* number of meals consumed by each philosopher */ 
int numMeals[thread_num]={0,0,0,0,0}; 
 
/* counter for the number of meals eaten */ 
int mealCount = 0; 
 
 
void *philosopher(void *); /* prototype of philosopher routine */ 
 
 
int main() 
{ 
    pthread_t tid[thread_num];
    void *status;
    int i,j; 

    srand( (long)time(NULL) );
 
    /* create 5 threads representing 5 dinning philosopher */ 
    for (i = 0; i < thread_num; i++) 
    { 
        if(pthread_create( tid + i, 0, philosopher, p + i ) != 0) 
	    { 
	        perror("pthread_create() failure."); 
	        exit(1); 
	    } 
    } 
 
    /* wait for the join of 5 threads */ 
    for (i = 0; i < thread_num; i++) 
    { 
        if(!pthread_join(tid[i], &status )==0) 
        { 
            perror("thr_join() failure."); 
            exit(1); 
        } 
    }  
     
    printf("\n"); 
    for(i=0; i<thread_num; i++) 
        printf("Philosopher %d ate %d meals.\n", i, numMeals[i]); 
    
    printf("\nmain(): The philosophers have left. I am going to exit!\n\n"); 
     
    return (0); 
} 
 
 
void *philosopher(void  *arg) 
{  
    int  sub = *(int*)arg; 
 
    while( mealCount < max_meals ) 
    { 
        printf("philosopher %d: I am going to eat!\n", sub); 
 
        /* lock the left chopstick */ 
        pthread_mutex_lock( m + sub ); 
         
        if( chopstick[sub] == 1 ) 
        { /* the left is available */ 
            printf("philosopher %d: left=%d\n",sub,chopstick[sub]);  
            printf("Philosopher %d: I got the left one!\n", sub); 
 
            chopstick[sub]=0; /* set the left chopstick unavailable */ 
             
            pthread_mutex_unlock( m + sub ); /* unlock the left chopstick */ 
 
            /* lock the right chopstick */ 
            pthread_mutex_lock( m + ((sub+1)%thread_num) ); 
             
            if( chopstick[(sub+1)%thread_num]==1 ) 
            { /* the right is available */ 
                printf("philosopher %d: right=%d\n", sub,
                                    chopstick[(sub+1)%thread_num]);
                
                /* set the right chopstick unavailable */ 
                chopstick[(sub+1)%thread_num]=0; 
                 
                /* unlock the right chopstick */ 
                pthread_mutex_unlock( m + ( (sub+1) % thread_num) ); 
		 
		        printf("Philosopher %d: I got two chopsticks!\n", sub); 
                printf("philosopher %d: I am eating!\n\n", sub); 
		
                numMeals[sub]++; 
		        mealCount++; 
                usleep(rand() % 3000000); /* eating time */ 
 
                /* lock the left & right chopsticks */ 
                pthread_mutex_lock( m + sub );  
                pthread_mutex_lock( m + ( (sub+1) % thread_num) ); 
 
                chopstick[sub]=1;   /*set the left chopstick available */ 
                
                /* set the right chopstick available */
                chopstick[(sub+1)%thread_num]=1;  
 
                /* unlock the left & right chopsticks */ 
                pthread_mutex_unlock ( &m[sub]); 
                pthread_mutex_unlock (&m[(sub+1)%thread_num]); 
		
                usleep(rand() % 3000000); /* food settling time */ 
            } 
            else 
            { /* the right is unavailable */ 
                printf("philosopher %d: right=%d\n", 
                       sub, chopstick[(sub+1)%thread_num]);	 
                printf("Philosopher %d: I cannot get the right one!\n\n", sub); 
 
                /* unlock the right chopstick */ 
                pthread_mutex_unlock( &m[(sub+1)%thread_num] ); 
 
                /* lock the left chopstick */ 
                pthread_mutex_lock( &m[sub] ); 
                 
                /* set the left chopstick available (put down the left one) */ 
                chopstick[sub]=1; 
 
                /* unlock the left chopstick */ 
                pthread_mutex_unlock( &m[sub] ); 

                /* wait for a while and try again later */ 
                usleep(rand() % 3000000); 
            } 
        }     
        else 
        { /* the left chopstick is unavailable */ 
            printf("philosopher %d: left=%d\n",sub,chopstick[sub]);  
            printf("Philosopher %d: I cannot even get the left chopstick!\n\n", 
                   sub); 
 
            /* unlock the left chopstick */ 
            pthread_mutex_unlock( &m[sub] ); 
             
           /* wait for a while and try again later */ 

            usleep(rand() % 3000000);
        } 
	
       sched_yield();  /* for LINUX you may also use pthread_yield(); */
    } 
    printf("Philosopher %d has finished the dinner and is leaving!\n", sub); 
    pthread_exit(0); 
} 
