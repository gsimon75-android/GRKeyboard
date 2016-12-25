#!/bin/bash

set -x

#set -e
export ANDROID_BUILDTOOLS="$ANDROID_SDK/build-tools/22.0.1"

#$JAVA_HOME/jre/bin/java \
#	-classpath $ANT_HOME/lib/ant-launcher.jar \
#	-Dant.home=$ANT_HOME \
#	-Dant.library.dir=$ANT_HOME/lib org.apache.tools.ant.launch.Launcher \
#	-cp "" \
#	debug

rm -rf bin gen

mkdir -p bin
mkdir -p bin/res
mkdir -p bin/rsObj
mkdir -p bin/rsLibs
mkdir -p gen
mkdir -p bin/classes
mkdir -p bin/dexedLibs

# FIXME: merge with others if needed
cp AndroidManifest.xml bin/AndroidManifest.xml
# TODO: process aidl if any

$ANDROID_BUILDTOOLS/aapt package \
	-f \
	-m \
	-0 apk \
	-M bin/AndroidManifest.xml \
	-S bin/res \
	-S res \
	-I $ANDROID_SDK/platforms/android-$ANDROID_SDK_PLATFORM/android.jar \
	-J gen \
	--generate-dependencies \
	-G bin/proguard.txt

mkdir -p gen/org/dyndns/fules/grkey
cat >gen/org/dyndns/fules/grkey/BuildConfig.java <<END-OF-BUILDCONFIG-JAVA
/** Automatically generated file. DO NOT MODIFY */
package org.dyndns.fules.grkey;

public final class BuildConfig {
    public final static boolean DEBUG = true;
}
END-OF-BUILDCONFIG-JAVA

JAVA_CLASSPATH="$ANDROID_SDK/platforms/android-$ANDROID_SDK_PLATFORM/android.jar:$JAVA_HOME/jre/lib/ext/localedata.jar:$JAVA_HOME/jre/lib/ext/zipfs.jar:$JAVA_HOME/jre/lib/ext/sunpkcs11.jar:$JAVA_HOME/jre/lib/ext/cldrdata.jar:$JAVA_HOME/jre/lib/ext/nashorn.jar:$JAVA_HOME/jre/lib/ext/dnsns.jar:$JAVA_HOME/jre/lib/ext/sunjce_provider.jar:$JAVA_HOME/jre/lib/ext/sunec.jar:$JAVA_HOME/jre/lib/ext/jfxrt.jar:bin/classes:$ANDROID_SDK/tools/support/annotations.jar"

find src gen -type f -name '*.java' -print0 | xargs -0r javac -source 1.5 -target 1.5 -cp "$JAVA_CLASSPATH" -d bin/classes

$ANDROID_BUILDTOOLS/dx \
	--dex \
	--output bin/dexedLibs/annotations-a764dfa84f45e186cc6d0f4a25a67274.jar \
	$ANDROID_SDK/tools/support/annotations.jar

$ANDROID_BUILDTOOLS/dx \
	--dex \
	--output bin/classes.dex \
	bin/classes \
	bin/dexedLibs/annotations-a764dfa84f45e186cc6d0f4a25a67274.jar

$ANDROID_BUILDTOOLS/aapt crunch \
	-v \
	-S res \
	-C bin/res

$ANDROID_BUILDTOOLS/aapt package \
	--no-crunch \
	-f \
	--debug-mode \
	-0 apk \
	-M bin/AndroidManifest.xml \
	-S bin/res \
	-S res \
	-I $ANDROID_SDK/platforms/android-$ANDROID_SDK_PLATFORM/android.jar \
	-F bin/grkey.ap_ \
	--generate-dependencies

(
cd bin
$ANDROID_BUILDTOOLS/aapt add \
	-f grkey.ap_ \
	classes.dex
)

jarsigner \
	-keystore ~/.android/debug.keystore \
	-storepass android \
	-digestalg SHA1 \
	-sigalg MD5withRSA \
	-sigfile CERT \
	-signedjar bin/grkey-debug-unaligned.apk \
	bin/grkey.ap_ \
	androiddebugkey

$ANDROID_BUILDTOOLS/zipalign \
	-f 4 \
	bin/grkey-debug-unaligned.apk \
	bin/grkey-debug.apk
