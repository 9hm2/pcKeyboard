// Note on the extra repository below: it's Google's GCS mirror of Maven
// Central, listed BEFORE mavenCentral(). Same artifacts, but
// repo.maven.apache.org rate-limits CI runner IPs (HTTP 429) hard
// enough to kill whole builds — the mirror doesn't. Anything genuinely
// missing from the mirror falls through to the canonical repositories.
pluginManagement {
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/") {
            name = "MavenCentralGcsMirror"
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/") {
            name = "MavenCentralGcsMirror"
        }
        mavenCentral()
    }
}

rootProject.name = "pcKeyboard"
include(":app")
