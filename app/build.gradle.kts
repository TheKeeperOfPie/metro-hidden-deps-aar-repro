plugins {
  kotlin("jvm")
  id("dev.zacsweers.metro")
}

// `:lib`'s `implementation` dependency is on this project's runtime classpath but not its compile
// classpath, so the hidden-dependency check scans it.
dependencies { implementation(project(":lib")) }
