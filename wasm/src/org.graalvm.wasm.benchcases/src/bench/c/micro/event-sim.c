/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
#include <math.h>
#include <stdarg.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include "harness.h"

#define EVENT_QUEUE_CAPACITY (15000)

#define SEED (47561094)

#define STAT_EVENT_ID (0)
#define CREATE_SIM_EVENT_ID (1)
#define MOVE_SIM_EVENT_ID (2)

#define SIM_DELAY_MAX (100)
#define SIM_DELAY_LAMBDA (0.1)

#define WIDTH (100)
#define HEIGHT (100)
#define SIM_COUNT (2000)
//#define WIDTH (500)
//#define HEIGHT (500)
//#define SIM_COUNT (10000)
#define SIM_LIFE_COUNT (10)
//#define STAT_COUNT (10000)
#define STAT_COUNT (100)
#define STAT_DELAY (1000)

#define LOG_ENABLED (0)
//#define LOG_ENABLED (1)

/* math */

int32_t min32(int32_t x, int32_t y) {
  return x < y ? x : y;
}

int16_t clamp16(int16_t x, int16_t lo, int16_t hi) {
  if (x < lo) return lo;
  else if (x >= hi) return hi - 1;
  else return x;
}

int16_t abs16(int16_t x) {
  return x < 0 ? -x : x;
}

/* priority queue */

typedef struct {
  int32_t size;
  int32_t capacity;
  void** data;
  int (*compare)(void*, void*);
} pqueue;

void pq_init(pqueue* pq, int capacity, int (*compare)(void*, void*)) {
  pq->size = 0;
  pq->capacity = capacity;
  pq->data = (void**) malloc(capacity * sizeof(void*));
  memset((char*) pq->data, 0, capacity * sizeof(void*));
  pq->compare = compare;
}

void pq_destroy(pqueue* pq) {
  free(pq->data);
}

int pq_enqueue(pqueue* pq, void* item) {
  int (*compare)(void*, void*) = pq->compare;
  void** data = pq->data;
  int32_t idx = pq->size;

  if (idx == pq->capacity) {
    return 0;
  }

  while (idx != 0) {
    int parent_idx = (idx - 1) / 2;
    if (compare(data[parent_idx], item) < 0) break;
    data[idx] = data[parent_idx];
    idx = parent_idx;
  }
  data[idx] = item;
  pq->size++;

  return 1;
}

int pq_dequeue(pqueue* pq, void** item) {
  int (*compare)(void*, void*) = pq->compare;
  void** data = pq->data;

  if (pq->size == 0) {
    return 0;
  }

  *item = data[0];
  pq->size--;
  void* last = data[pq->size];
  int32_t idx = 0;
  while (1) {
    int32_t left_idx = idx * 2 + 1;
    if (left_idx >= pq->size) break;
    int32_t right_idx = left_idx + 1;
    int32_t next_idx = left_idx;
    if (right_idx < pq->size && compare(data[left_idx], data[right_idx]) > 0) {
      next_idx = right_idx;
    }
    if (compare(last, data[next_idx]) <= 0) break;
    data[idx] = data[next_idx];
    idx = next_idx;
  }
  data[idx] = last;

  return 1;
}

int32_t pq_size(pqueue* pq) {
  return pq->size;
}

void pq_print(pqueue* pq) {
  fprintf(stderr, "pqueue(%d): |", pq->size);
  for (int32_t i = 0; i < pq->size; i++) {
    int64_t* ptr = (int64_t*) pq->data[i];
    fprintf(stderr, " %jd |", *ptr);
  }
  fprintf(stderr, "\n");
}

/* random number generator */

typedef struct {
  int32_t seed;
} rng;

rng rng_create(int seed0) {
  rng r;
  r.seed = seed0 % 2147483647;
  if (r.seed <= 0) {
    r.seed += 2147483646;
  }
  return r;
}

int32_t rng_next_int(rng* r) {
  r->seed = (r->seed * 16807) % 2147483647;
  int32_t result = r->seed;
  if (result < 0) result = -result;
  return result;
}

double rng_next_double(rng* r) {
  int n = rng_next_int(r);
  return (n - 1) / 2147483646.0;
}

double rng_next_exp(rng* r, double lambda) {
  double u = rng_next_double(r);
  return -1 * log(1 - u) / lambda;
}

/* statistics */

typedef struct {
  int64_t sample_count;
  int64_t sim_count_cumulative;
  int64_t sim_count_last;
  int64_t sim_count_max;
  int64_t win_count_max;
} statistics;

void statistics_init(statistics* stats) {
  stats->sample_count = 0;
  stats->sim_count_cumulative = 0;
  stats->sim_count_last = 0;
  stats->sim_count_max = 0;
  stats->win_count_max = 0;
}

/* state */

typedef struct {
  int32_t id;
  int16_t x;
  int16_t y;
  int8_t life_count;
  int64_t win_count;
} sim;

void sim_init(sim* s, int32_t id, int x, int y) {
  s->id = id;
  s->x = x;
  s->y = y;
  s->life_count = (int8_t) SIM_LIFE_COUNT;
  s->win_count = 0;
}

typedef struct {
  int64_t time;
  int32_t first_free_id;
  sim** sims;
  sim** map;
  statistics stats;
  rng rand;
  int8_t terminated;
} state;

void state_init(state* s, int32_t max_sim_count, int16_t width, int16_t height, int32_t seed) {
  s->time = 0;
  s->first_free_id = 0;
  s->sims = (sim**) malloc(max_sim_count * sizeof(sim*));
  memset(s->sims, 0, max_sim_count * sizeof(sim*));
  s->map = (sim**) malloc(width * height * sizeof(sim*));
  memset(s->map, 0, width * height * sizeof(sim*));
  statistics_init(&(s->stats));
  s->rand = rng_create(seed);
  s->terminated = 0;
}

void state_destroy(state* s) {
  free(s->sims);
  free(s->map);
}

sim* state_sim_by_id(state* s, int32_t id) {
  return s->sims[id];
}

sim* state_sim_at(state* s, int16_t x, int16_t y) {
  if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) {
    return NULL;
  }
  return s->map[y * WIDTH + x];
}

sim* state_sim_put(state* s, int16_t x, int16_t y, sim* sm) {
  if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) {
    return NULL;
  }
  if (s->map[y * WIDTH + x] != NULL) {
    return NULL;
  }
  s->map[y * WIDTH + x] = sm;
  s->sims[sm->id] = sm;
  return sm;
}

int state_sim_move(state* s, int32_t id, int16_t x, int16_t y) {
  sim* sm = s->sims[id];
  if (sm == NULL) {
    return 0;
  }
  if (s->map[y * WIDTH + x] != NULL) {
    return 0;
  }
  if (s->map[sm->y * WIDTH + sm->x] != sm) {
    return 0;
  }
  s->map[sm->y * WIDTH + sm->x] = NULL;
  s->map[y * WIDTH + x] = sm;
  sm->x = x;
  sm->y = y;
  return 1;
}

sim* state_sim_remove(state* s, int32_t id) {
  sim* sm = s->sims[id];
  if (sm == NULL) {
    return NULL;
  }
  int16_t x = sm->x;
  int16_t y = sm->y;
  if (s->map[y * WIDTH + x] != sm) {
    return NULL;
  }
  s->sims[id] = NULL;
  s->map[y * WIDTH + x] = NULL;
  return sm;
}

int32_t state_create_sim_id(state* s) {
  return s->first_free_id++;
}

/* logging */

void logs(state* s, const char* str, int x) {
  if (LOG_ENABLED) {
    fprintf(stderr, "%jd: %s (%d)\n", s->time, str, x);
  }
}

/* events */

typedef struct {
  int32_t samples_left;
} stat_event_data;

typedef struct {
  int32_t count;
} create_sim_event_data;

typedef struct {
  int32_t id;
} move_sim_event_data;

typedef union {
  stat_event_data stat;
  create_sim_event_data create_sim;
  move_sim_event_data move_sim;
} event_data;

typedef struct {
  int64_t time;
  int8_t type;
  event_data* data;
} event;

event* event_alloc(int8_t type, int64_t time, event_data* data) {
  event* e = (event*) malloc(sizeof(event));
  e->type = type;
  e->time = time;
  e->data = data;
  return e;
}

int compare_events(void* a, void* b) {
  event* ea = (event*) a;
  event* eb = (event*) b;
  return ea->time - eb->time;
}

event_data* stat_alloc(int32_t samples_left) {
  event_data* ed = (event_data*) malloc(sizeof(event_data));
  ed->stat.samples_left = samples_left;
  return ed;
}

event_data* create_sim_alloc(int32_t count) {
  event_data* ed = (event_data*) malloc(sizeof(event_data));
  ed->create_sim.count = count;
  return ed;
}

event_data* move_sim_alloc(int32_t id) {
  event_data* ed = (event_data*) malloc(sizeof(event_data));
  ed->move_sim.id = id;
  return ed;
}

void stat_action(state* s, pqueue* events, stat_event_data* data) {
  logs(s, "--- taking stats, count", s->stats.sample_count + 1);

  // Collect stats.
  int32_t total_live_sims = 0;
  s->stats.sample_count++;
  for (int16_t y = 0; y < HEIGHT; y++) {
    for (int16_t x = 0; x < HEIGHT; x++) {
      sim* sm = state_sim_at(s, x, y);
      if (sm != NULL) {
        total_live_sims++;
        if (sm->win_count > s->stats.win_count_max) {
          s->stats.win_count_max = sm->win_count;
        }
      }
    }
  }
  s->stats.sim_count_last = total_live_sims;
  if (total_live_sims > s->stats.sim_count_max) s->stats.sim_count_max = total_live_sims;
  s->stats.sim_count_cumulative += total_live_sims;
  logs(s, "current sim count", total_live_sims);
  logs(s, "average sim count", s->stats.sim_count_cumulative / s->stats.sample_count);
  logs(s, "max win count", s->stats.win_count_max);

  // Maybe enqueue next stat.
  if (data->samples_left > 1) {
    logs(s, "setting next stat sampling, left", data->samples_left - 1);
    event* e = event_alloc(STAT_EVENT_ID, s->time + STAT_DELAY,
      stat_alloc(data->samples_left - 1));
    if (!pq_enqueue(events, e)) abort();
  } else {
    // Or otherwise, terminate the simulation.
    logs(s, "terminating the simulation, stats left", data->samples_left - 1);
    s->terminated = 1;
  }
}

void create_sim_action(state* s, pqueue* events, create_sim_event_data* data) {
  logs(s, "--- creating a sim", s->first_free_id);

  // Find a location for the sim, and add it to the map.
  sim* sm;
  while (1) {
    int16_t x = abs16((int16_t) rng_next_int(&s->rand)) % WIDTH;
    int16_t y = abs16((int16_t) rng_next_int(&s->rand)) % HEIGHT;
    if (state_sim_at(s, x, y) == NULL) {
      int32_t id = state_create_sim_id(s);
      sm = (sim*) malloc(sizeof(sim));
      sim_init(sm, id, x, y);
      state_sim_put(s, x, y, sm);
      break;
    }
  }

  // Schedule the move-sim event.
  logs(s, "setting up the move event, id", sm->id);
  event* e = event_alloc(MOVE_SIM_EVENT_ID, s->time + 1, move_sim_alloc(sm->id));
  if (!pq_enqueue(events, e)) abort();

  // Schedule the next create-sim event if necessary.
  if (data->count > 1) {
    logs(s, "setting next sim creation, left", data->count - 1);
    int delay = min32((int32_t) rng_next_exp(&s->rand, SIM_DELAY_LAMBDA), SIM_DELAY_MAX);
    event* e = event_alloc(CREATE_SIM_EVENT_ID, s->time + delay,
      create_sim_alloc(data->count - 1));
    if (!pq_enqueue(events, e)) abort();
  }
}

void move_sim_action(state* s, pqueue* events, move_sim_event_data* data) {
  logs(s, "--- moving a sim, id", data->id);

  // Check if alive.
  sim* sm = state_sim_by_id(s, data->id);
  if (sm->life_count == 0) {
    logs(s, "removing the sim, id", data->id);
    if (state_sim_remove(s, sm->id) != sm) abort();
    free(sm);
    return;
  }

  // Pick target location.
  int16_t xd = -1 + abs16((int16_t) rng_next_int(&s->rand)) % 3;
  int16_t yd = -1 + abs16((int16_t) rng_next_int(&s->rand)) % 3;
  int16_t xt = clamp16(sm->x + xd, 0, WIDTH);
  int16_t yt = clamp16(sm->y + yd, 0, HEIGHT);

  // Act if not staying at the same location.
  if (xt != sm->x || yt != sm->y) {
    logs(s, "target x", xt);
    logs(s, "target y", yt);
    sim* tsm = state_sim_at(s, xt, yt);
    if (tsm == NULL) {
      // Target location is empty.
      if (!state_sim_move(s, sm->id, xt, yt)) abort();
    } else {
      // Target location is occupied -- fight!
      logs(s, "fighting against sim with id", tsm->id);
      if (tsm->life_count > 0) {
        if (rng_next_double(&s->rand) > 0.5) {
          logs(s, "decreasing life count of sim with id", tsm->id);
          tsm->life_count--;
          logs(s, "win count is now", sm->win_count);
          sm->win_count++;
        }
      }
    }
  }

  // Schedule the next move.
  logs(s, "setting up the move event, id", sm->id);
  int delay = min32((int32_t) rng_next_exp(&s->rand, SIM_DELAY_LAMBDA), SIM_DELAY_MAX);
  event* e = event_alloc(MOVE_SIM_EVENT_ID, s->time + delay, move_sim_alloc(sm->id));
  if (!pq_enqueue(events, e)) abort();
}

/* simulation */

void action(state* s, pqueue* events, int8_t type, event_data* data) {
  switch (type) {
    case STAT_EVENT_ID:
      stat_action(s, events, (stat_event_data*) data);
      break;
    case CREATE_SIM_EVENT_ID:
      create_sim_action(s, events, (create_sim_event_data*) data);
      break;
    case MOVE_SIM_EVENT_ID:
      move_sim_action(s, events, (move_sim_event_data*) data);
      break;
  }
}

int simulate() {
  // Setup.
  state s;
  state_init(&s, SIM_COUNT, WIDTH, HEIGHT, SEED);
  pqueue events;
  pq_init(&events, EVENT_QUEUE_CAPACITY, compare_events);

  // Initial conditions.
  event* stat = event_alloc(STAT_EVENT_ID, STAT_DELAY, stat_alloc(STAT_COUNT));
  if (!pq_enqueue(&events, stat)) {
    return -1;
  }
  event* create_sim = event_alloc(CREATE_SIM_EVENT_ID, 0, create_sim_alloc(SIM_COUNT));
  if (!pq_enqueue(&events, create_sim)) {
    return -1;
  }

  // Simulate until termination.
  event* cur;
  while (pq_dequeue(&events, (void**) &cur) && !s.terminated) {
    s.time = cur->time;
    action(&s, &events, cur->type, cur->data);
    free(cur->data);
    free(cur);
  }

  int64_t checksum =
    s.stats.win_count_max +
    s.stats.sim_count_max +
    s.stats.sim_count_cumulative +
    s.stats.sample_count;

  // Teardown.
  while (pq_dequeue(&events, (void**) &cur)) {
    free(cur->data);
    free(cur);
  }
  for (int32_t id = 0; id < SIM_COUNT; id++) {
    if (s.sims[id] != NULL) free(s.sims[id]);
  }

  pq_destroy(&events);
  state_destroy(&s);

  return (int) checksum;
}

int benchmarkIterationsCount() {
  return 20;
}

void benchmarkSetupOnce() {
}

void benchmarkSetupEach() {
}

void benchmarkTeardownEach(char* outputFile) {
}

int benchmarkRun() {
  return simulate();
}
