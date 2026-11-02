# Hidden-dependency check crashes on an AAR with padded zip extra fields

`checkMainMetroHiddenDependencies` fails with `bad zip: truncated header in extra field` when a
hidden runtime dependency is an AAR whose zip entries carry padding in the local-header extra
field. The check crashes before it writes a report, so it can't run in any project that has such
an AAR on a graph's runtime classpath.

Reproduces with Metro 1.5.0 and 1.5.1, Kotlin 2.4.20, Gradle 9.7.1 and JDK 21.

## Reproduce

```shell
./gradlew :app:checkMainMetroHiddenDependencies
```

`:lib` has an `implementation` dependency on `com.unity3d.ads-mediation:adquality-sdk:9.2.1@aar`
from Maven Central, and `:app` depends on `:lib`. The AAR is on `:app`'s runtime classpath but not
its compile classpath, so the check scans it.

**Expected:** the check passes, because the AAR contains no Metro hints or Hilt aggregation
metadata.

**Actual:**

```
Execution failed for task ':app:checkMainMetroHiddenDependencies'.
> java.io.IOException: bad zip: truncated header in extra field

Caused by: java.io.IOException: bad zip: truncated header in extra field
	at okio.internal.ZipFilesKt.readExtra(ZipFiles.kt:359)
	at okio.internal.ZipFilesKt.readOrSkipLocalHeader(ZipFiles.kt:423)
	at okio.internal.ZipFilesKt.readLocalHeader(ZipFiles.kt:388)
	at okio.ZipFileSystem.metadataOrNull(ZipFileSystem.kt:56)
	at dev.zacsweers.metro.gradle.validation.MetroHintScanner.scanAar(MetroHintScanner.kt:87)
	at dev.zacsweers.metro.gradle.validation.MetroHintScanner.findHints(MetroHintScanner.kt:45)
	at dev.zacsweers.metro.gradle.validation.MetroHintScanner.findHints$default(MetroHintScanner.kt:27)
	at dev.zacsweers.metro.gradle.validation.CheckHiddenMetroDependenciesTask.check(CheckHiddenMetroDependenciesTask.kt:83)
```

## Cause

The AAR's `classes.jar` entry is stored uncompressed, and its local file header has a 2-byte
extra field containing only `00 00`. That padding aligns the entry's data to 4 bytes; the
central-directory copy of the header has no extra field:

```
$ xxd -s 50013 -l 43 adquality-sdk-9.2.1.aar
0000c35d: 504b 0304 0a00 0000 0000 3664 275c 8edb  PK........6d'\..
0000c36d: 43eb d746 1b00 d746 1b00 0b00 0200 636c  C..F...F......cl
0000c37d: 6173 7365 732e 6a61 7200 00              asses.jar..
                                     ^^^^^ extra field (length 0x0002)
```

`MetroHintScanner.scanAar` opens the AAR with Okio's `ZipFileSystem` and calls
`metadataOrNull("/classes.jar")`, which reads that local header. Okio's `readExtra` expects the
extra field to be a sequence of `(id, size, data)` records and throws when 1–3 bytes remain. Other
zip readers ignore the trailing padding: `java.util.zip.ZipFile`, `unzip -t`, Gradle and AGP all
read this file without errors. The artifact matches Maven Central's published SHA-1, so it isn't
a corrupted download.

## Possible fixes

- Read archives with `java.util.zip.ZipFile`/`ZipInputStream`, as `scanAarJar` already does for
  nested jars.
- Or catch the `IOException` per artifact and report the artifact as unreadable instead of
  failing the whole check.

## Workaround

Putting the AAR on the graph module's compile classpath, for example with
`compileOnly("com.unity3d.ads-mediation:adquality-sdk:9.2.1")`, makes the check treat it as
visible and skip it.
