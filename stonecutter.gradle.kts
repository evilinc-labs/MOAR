plugins {
    id("dev.kikugie.stonecutter")
    id("fabric-loom") version "1.14-SNAPSHOT" apply false
}

stonecutter active "1.21.1" /* [SC] DO NOT EDIT */

stonecutter parameters {
    // Swap tokens — available in source as string constants
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
}
