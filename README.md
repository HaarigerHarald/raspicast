Raspicast
====================================================================

These are the sources of the android app Raspicast:
https://play.google.com/store/apps/details?id=at.huber.raspicast


This was written during 2013 - 2015, due to the upcoming deprecation of the OMX API on the Raspberry Pi it will require updates.

Building with Gradle
====================================================================

If you have the android sdk and the java jdk installed you 
can build from the commandline with:

    gradlew build

Or to build just the release:

    gradlew aR

It can also be built with android studio after importing.

License
====================================================================

The sources are licensed under the GPLv2 see:
https://www.gnu.org/licenses/old-licenses/gpl-2.0.html or
the supplied LICENSE.
