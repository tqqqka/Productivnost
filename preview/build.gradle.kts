plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
}

// Инструмент разработки: рисует те же воксельные модели, что и приложение,
// но средствами JVM — чтобы смотреть на растения, не собирая APK.
kotlin {
    sourceSets["main"].kotlin.srcDir("../app/src/main/java")
    sourceSets["main"].kotlin.include(
        "com/tihon/productivnost/voxel/VoxelModel.kt",
        "com/tihon/productivnost/voxel/PlantGenerator.kt",
        "com/tihon/productivnost/core/Models.kt",
        "**/preview/**",
    )
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}

application {
    mainClass.set("preview.PreviewKt")
}
