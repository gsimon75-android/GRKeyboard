# Required environment variables:
# ANDROID_SDK		the root of the Android SDK
# JAVA_HOME		the root of the Java JDK
JAVA_HOME=/usr/lib/jvm/java-1.8.0/

# Project settings
PROJECT=grkey
FLAVOUR=debug
PACKAGE=org.dyndns.fules.grkey
EXTRA_JARS=
LOGCAT_TAGS=GRKeyboard

########################################################################################################################

# Needed additional extra .jars
ANDROID_JAR=$(ANDROID_SDK)/platforms/android-$(ANDROID_SDK_PLATFORM)/android.jar
ANNOTATIONS_JAR=$(ANDROID_SDK)/tools/support/annotations.jar
JAVA_EXT_JARS = zipfs sunpkcs11 cldrdata nashorn dnsns sunjce_provider sunec jfxrt

# Tools
ANDROID_BUILDTOOLS=$(ANDROID_SDK)/build-tools/$(shell ls $(ANDROID_SDK)/build-tools)
JAVAC=$(JAVA_HOME)/bin/javac
JARSIGNER=$(JAVA_HOME)/bin/jarsigner
AAPT=$(ANDROID_BUILDTOOLS)/aapt
DX=$(ANDROID_BUILDTOOLS)/dx
ZIPALIGN=$(ANDROID_BUILDTOOLS)/zipalign

# Tool options
JAVA_FLAGS=-Xlint:-options -source 1.5 -target 1.5 -cp "$(JAVA_CLASSPATH)" -d bin/classes -sourcepath src
AAPT_PACKAGE_FLAGS=-M AndroidManifest.xml -I $(ANDROID_JAR) -S bin/res -S res
EMPTY =
JAVA_CLASSPATH = $(ANDROID_JAR):$(ANNOTATIONS_JAR):bin/classes
JAVA_CLASSPATH += $(foreach jarname,$(JAVA_EXT_JARS),:$(JAVA_HOME)/jre/lib/ext/$(jarname).jar)
JAVA_CLASSPATH := $(subst $(EMPTY) :,:,$(JAVA_CLASSPATH))

# Project-specific resources
PACKAGE_PATH=$(subst .,/,$(PACKAGE))
JAVA_SOURCES=$(patsubst src/%.java,%,$(shell find src -type f -name '*.java'))
PNG_FILES=$(shell find res -type f -name '*.png')
NONPNG_RESOURCES=$(shell find res -type f -not -name '*.png')

# Default target
all:	bin/$(PROJECT).apk

.PHONY:    clean install test

# Clean target
clean:
	rm -rf bin gen

# Install target
install:	bin/$(PROJECT).apk
		adb install -r $<

# Test target
test:
	adb logcat -c
	adb logcat -v time -s 'AndroidRuntime:*' $(patsubst %,'%:*',$(LOGCAT_TAGS)) | tee run.log

# Create intermediate dirs if they don't exist yet
bin bin/res gen bin/classes bin/dexedLibs:
	mkdir -p $@

# Special autogenerated source gen/<package>/BuildConfig.java
define DEFAULT_BUILDCONFIG_JAVA=
	/** Automatically generated file. DO NOT MODIFY */
	package $(PACKAGE);
	public final class BuildConfig {
	    public final static boolean DEBUG = $(if $(subst release,,$(FLAVOUR)),true,false);
	}
endef
export DEFAULT_BUILDCONFIG_JAVA
gen/$(PACKAGE_PATH)/BuildConfig.java:	Makefile
	mkdir -p $(dir $@)
	echo "$${DEFAULT_BUILDCONFIG_JAVA}" > $@

# Special resource-id container gen/<package>/R.java
gen/$(PACKAGE_PATH)/R.java:	AndroidManifest.xml bin/res gen $(PNG_FILES) $(NONPNG_RESOURCES)
	$(AAPT) package -m -J gen $(AAPT_PACKAGE_FLAGS)

# Compile rules of the two special gen/.../*.java
bin/classes/$(PACKAGE_PATH)/R.class:	gen/$(PACKAGE_PATH)/R.java bin/classes
	$(JAVAC) $(JAVA_FLAGS) $<


# Barrier copy to prevent recompiling all java if only a resource detail (but not the id) has changed
bin/R.class.last:	bin/classes/$(PACKAGE_PATH)/R.class
	cmp -s $< $@ || cp $< $@

.SECONDARY:  bin/classes/$(PACKAGE_PATH)/BuildConfig.class
bin/classes/$(PACKAGE_PATH)/BuildConfig.class:	gen/$(PACKAGE_PATH)/BuildConfig.java bin/classes
	$(JAVAC) $(JAVA_FLAGS) $<

# Compile rule for the generic .java sources under src/
bin/classes/%.class:	src/%.java bin/classes bin/classes/$(PACKAGE_PATH)/BuildConfig.class bin/R.class.last
	$(JAVAC) $(JAVA_FLAGS) $<

# Pre-optimising rule for .jars
bin/dexedLibs/%.jar.dex:	%.jar
	mkdir -p $(dir $@) 
	$(DX) --dex --output $@ $^

# Special final-result archive bin/classes.dex
bin/classes.dex:		$(foreach src,$(JAVA_SOURCES),bin/classes/$(src).class) $(foreach lib,$(ANNOTATIONS_JAR) $(EXTRA_JARS),bin/dexedLibs/$(lib).dex)
	$(DX) --dex --output $@ bin/classes bin/dexedLibs

# PNG image crunching
bin/%.png:	%.png
	mkdir -p $(dir $@)
	$(AAPT) singlecrunch -v -i $^ -o $@

# Stage #1: Assembling the base .apk from the raw contents
bin/$(PROJECT).unsigned.apk:	bin/classes.dex $(foreach png,$(PNG_FILES),bin/$(png)) AndroidManifest.xml $(NONPNG_RESOURCES)
	$(AAPT) package --no-crunch -f $(AAPT_PACKAGE_FLAGS) -F $@
	( cd bin; $(AAPT) add -f $(subst bin/,,$@) classes.dex )

# Stage #2: Signing the .apk
bin/$(PROJECT).unaligned.apk:	bin/$(PROJECT).unsigned.apk
	$(JARSIGNER) \
	-keystore debug.keystore -storepass android \
	-digestalg SHA1 -sigalg MD5withRSA -sigfile CERT \
	-signedjar $@ $^ androiddebugkey

# Stage #3: Aligning the .apk
bin/$(PROJECT).apk:	bin/$(PROJECT).unaligned.apk
	$(ZIPALIGN) -f 4 $^ $@

