REF_TARGETS:=$(TESTS:%=%/ref.out)
BC_TARGETS:=$(foreach var,$(VARIANTS),$(TESTS:%=%/$(var).bc))

.PHONY: default
default: $(REF_TARGETS) $(BC_TARGETS)

%/ref.out: %.c
	@mkdir -p $(shell dirname $@)
	clang $(LDFLAGS) -o $@ $<

%/ref.out: %.cpp
	@mkdir -p $(shell dirname $@)
	clang++ $(LDFLAGS) -o $@ $<


define OPT_RULES
%/$(1).bc: %.c
	@mkdir -p $$(shell dirname $$@)
	clang -c -emit-llvm -$(1) -o $$@ $$<

%/$(1).bc: %.cpp
	@mkdir -p $$(shell dirname $$@)
	clang++ -c -emit-llvm -$(1) -o $$@ $$<
endef

$(eval $(call OPT_RULES,O0))
$(eval $(call OPT_RULES,O1))
$(eval $(call OPT_RULES,O2))
$(eval $(call OPT_RULES,O3))

%_BB_VECTORIZE.bc: %.bc
	opt -o $@ -functionattrs -instcombine -always-inline -jump-threading -simplifycfg -mem2reg -bb-vectorize $<
