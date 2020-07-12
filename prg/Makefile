JAVA            = java
XSLTPROC	= xsltproc
RM              = 'rm' -fr

PHASE		= none
NUMBER_OF_REGISTERS = 64

.PHONY	: %
%	: %.p20
	$(RM) -fr $@.*.xml $@.*.html
	if $(JAVA) -cp ../bin:../src:../lib/antlr-4.8-complete.jar prev.Compiler --xsl=../lib/xsl/ --logged-phase=all --target-phase=$(PHASE) --num-regs=$(NUMBER_OF_REGISTERS) $< ; then for XML in `'ls' -1 $@.*.xml` ; do $(XSLTPROC) $$XML -o `basename $$XML xml`html; done ; fi

.PHONY  : clean
clean   :
	$(RM) *~
	$(RM) *xml *html *mmix *mmix.mmo