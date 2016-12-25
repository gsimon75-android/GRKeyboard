PROJECT=grkey
FLAVOUR=debug
PACKAGE=org.dyndns.fules.grkey

ANDROID_BUILDTOOLS=$(ANDROID_SDK)/build-tools/22.0.1
PACKAGE_PATH=$(subst .,/,$(PACKAGE))

.PHONY:    clean build

clean:
	rm -rf bin gen


EXTRA_JARS=

ANDROID_JAR=$(ANDROID_SDK)/platforms/android-$(ANDROID_SDK_PLATFORM)/android.jar
ANNOTATIONS_JAR=$(ANDROID_SDK)/tools/support/annotations.jar

JAVA_EXT_JARS = zipfs sunpkcs11 cldrdata nashorn dnsns sunjce_provider sunec jfxrt
JAVA_CLASSPATH = $(ANDROID_JAR)
JAVA_CLASSPATH += $(foreach jarname,$(JAVA_EXT_JARS),:$(JAVA_HOME)/jre/lib/ext/$(jarname).jar)
JAVA_CLASSPATH += :bin/classes
JAVA_CLASSPATH += :$(ANNOTATIONS_JAR)

JAVA_SOURCES=$(basename $(shell find src -type f -name '*.java'))
PNG_FILES=$(shell find res -type f -name '*.png')
NONPNG_RESOURCES=$(shell find res -type f -not -name '*.png')

bin,bin/res,gen,bin/classes,bin/dexedLibs:
	mkdir -p $@

gen/$(PACKAGE_PATH)/BuildConfig.java:	Makefile
	mkdir -p gen/$(PACKAGE_PATH)
	cat >gen/org/dyndns/fules/grkey/BuildConfig.java <<-"END-OF-BUILDCONFIG-JAVA"
	/** Automatically generated file. DO NOT MODIFY */
	package $(PACKAGE);
	public final class BuildConfig {
	    public final static boolean DEBUG = $(if $(subst release,,$(FLAVOUR)),true,false);
	}
	END-OF-BUILDCONFIG-JAVA

gen/$(PACKAGE_PATH)/R.java,gen/$(PACKAGE_PATH)/R.java.d:	AndroidManifest.xml
	$(ANDROID_BUILDTOOLS)/aapt package -m -J gen -M AndroidManifest.xml -I $(ANDROID_JAR) -S bin/res -S res --generate-dependencies

bin/classes/%.class:	src/%.java
	javac -source 1.5 -target 1.5 -cp "$(JAVA_CLASSPATH)" -d bin/classes 

bin/classes/%.class:	gen/%.java
	javac -source 1.5 -target 1.5 -cp "$(JAVA_CLASSPATH)" -d bin/classes 

bin/dexedLibs/%.jar.dex:	%.jar
	$(ANDROID_BUILDTOOLS)/dx --dex --output $@ $^

bin/classes.dex:		$(foreach src,$(JAVA_SOURCES),bin/classes/$(src).class) $(foreach lib,$(ANNOTATIONS_JAR) $(EXTRA_JARS),bin/dexedLibs/$(lib).dex)
	$(ANDROID_BUILDTOOLS)/dx --dex --output $@ $^

bin/%.png:	%.png
	$(ANDROID_BUILDTOOLS)/aapt singlecrunch -v -i $^ -o $@

bin/$(PROJECT).unsigned.apk:	bin/classes.dex $(foreach png,$(PNG_FILES),bin/$(png)) AndroidManifest.xml $(NONPNG_RESOURCES)
	$(ANDROID_BUILDTOOLS)/aapt package --no-crunch -f --debug-mode -M AndroidManifest.xml \
	-S bin/res -S res -I $(ANDROID_JAR) -F $@ --generate-dependencies
	( cd bin; $(ANDROID_BUILDTOOLS)/aapt add -f $@ classes.dex )

bin/$(PROJECT).unaligned.apk:	bin/$(PROJECT).unsigned.apk
	jarsigner \
	-keystore ~/.android/debug.keystore -storepass android \
	-digestalg SHA1 -sigalg MD5withRSA -sigfile CERT \
	-signedjar $@ $^ androiddebugkey

bin/$(PROJECT).apk:	bin/$(PROJECT).unaligned.apk
	$(ANDROID_BUILDTOOLS)/zipalign -f 4 $^ $@
