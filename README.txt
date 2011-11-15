This project requires the following libraries to be present in the same
directory as the main project:

* .... Add shared libraries here

Make sure the projects are checked out in the same workspace in eclipse and
have the right name.

You might want to copy configuration files from appropriate release subdirectory
to resources/raw in order to get specific configuration.

----------------------------------------------------------------------------
Requirements :
	- Android SDK - https://dl-ssl.google.com/android/eclipse/
	- Ant ( only if not using IDE).

Additonal requirements for IDE :

- Eclipse Helios 3.6 IDE - http://www.eclipse.org/downloads/
- Android Eclipse ADT Plugin - http://developer.android.com/sdk/index.html

Optional requirements ( for analysis ) :
- PMD - http://pmd.sourceforge.net/
- Checkstyle - http://checkstyle.sourceforge.net/
- Findbugs - http://findbugs.sourceforge.net/
----------------------------------------------------------------------------

Building :

I. Building from ANT

       android update project -p . 
       ant

II. Building from Eclipse

	3. Install Eclipse IDE, and ADT Android plugin (http://developer.android.com/sdk/eclipse-adt.html#installing)
	4. Configure Android SDK in Eclipse IDE
	5. Download SDK Platform 8 in Android SDK Manager (Eclipse->Window->Android SDK and AVD manager)
	6. Import dependent libraries into eclipse workspace
	7. Run application.


----------------------------------------------------------------------------
Publishing :
----------------------------------------------------------------------------

To publish application on Android Market use instructions described here :

1. Sign application
http://developer.android.com/guide/publishing/app-signing.html

2. Publish
http://developer.android.com/guide/publishing/publishing.html
