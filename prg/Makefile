JAVA            = java
XSLTPROC	= xsltproc
RM              = 'rm' -fr

PHASE		= none
NUMBER_OF_REGISTERS = 64
LOGGING_LEVEL = INFO

PEEPHOLE_OPTIMISATION = none
CONSTANT_FOLDING = none
SYMBOLIC_CONSTANT_FOLDING = none
CONSTANT_PROPAGATION = none
COPY_PROPAGATION = none
DEAD_CODE_ELIMINATION = none
COMMON_SUBEXPRESSION_ELIMINATION = none
LOOP_HOISTING = none
INDUCTION_VARIABLE_ELIMINATION = none

.PHONY	: %
%	: %.p20
	$(RM) -fr $@.*.xml $@.*.html
	if $(JAVA) -cp ../bin:../src:../lib/antlr-4.8-complete.jar prev.Compiler --xsl=../lib/xsl/ --logged-phase=all --logging-level=$(LOGGING_LEVEL) --target-phase=$(PHASE) --num-regs=$(NUMBER_OF_REGISTERS) --peephole-optimisation=$(PEEPHOLE_OPTIMISATION) --constant-folding=$(CONSTANT_FOLDING) --symbolic-constant-folding=$(SYMBOLIC_CONSTANT_FOLDING) --constant-propagation=$(CONSTANT_PROPAGATION) --copy-propagation=$(COPY_PROPAGATION) --dead-code-elimination=$(DEAD_CODE_ELIMINATION) --common-subexpression-elimination=$(COMMON_SUBEXPRESSION_ELIMINATION) --loop-hoisting=$(LOOP_HOISTING) --induction-variable-elimination=$(INDUCTION_VARIABLE_ELIMINATION) $< ; then for XML in `'ls' -1 $@.*.xml` ; do $(XSLTPROC) $$XML -o `basename $$XML xml`html; done ; fi

.PHONY  : clean
clean   :
	$(RM) *~
	$(RM) *xml *html *mmix *mmix.mmo