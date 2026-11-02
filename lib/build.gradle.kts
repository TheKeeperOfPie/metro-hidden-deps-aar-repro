plugins { kotlin("jvm") }

dependencies {
  // Any published AAR whose zip local headers carry padding in the extra field. This one's
  // `classes.jar` entry has a 2-byte all-zero extra field. `@aar` selects the AAR artifact.
  implementation("com.unity3d.ads-mediation:adquality-sdk:9.2.1@aar")
}
