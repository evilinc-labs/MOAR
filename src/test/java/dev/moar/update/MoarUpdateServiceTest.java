package dev.moar.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoarUpdateServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void selectsOnlyNewerReleaseForTheRunningMinecraftVersion() {
        String release = releaseJson("3.3.8", "1.21.11");
        var offer = MoarUpdateService.parseRelease(release, "3.3.7+1.21.11", "1.21.11");
        assertTrue(offer.isPresent());
        assertEquals("moar-3.3.8+1.21.11.jar", offer.get().jarName());
        assertFalse(MoarUpdateService.parseRelease(release, "3.3.8+1.21.11", "1.21.11").isPresent());
        assertFalse(MoarUpdateService.parseRelease(release, "3.3.7+1.21.4", "1.21.4").isPresent());
        assertTrue(MoarUpdateService.compareVersions("v3.4.0", "3.3.9+1.21.11") > 0);

        var compatible = MoarUpdateService.parseRelease(releaseJson("3.3.8", "1.21.10"),
                "3.3.7", "1.21.9");
        assertTrue(compatible.isPresent());
        assertEquals("moar-3.3.8+1.21.10.jar", compatible.get().jarName());
        var modern = MoarUpdateService.parseRelease(releaseJson("3.3.8", "26.1.1"),
                "3.3.7", "26.1");
        assertTrue(modern.isPresent());
        assertEquals("moar-3.3.8+26.1.1.jar", modern.get().jarName());
    }

    @Test
    void rejectsAnAssetOutsideTheMoarReleasePath() {
        String release = releaseJson("3.3.8", "1.21.11")
                .replace("github.com/evilinc-labs/MOAR", "github.com/other/MOAR");
        assertFalse(MoarUpdateService.parseRelease(release, "3.3.7+1.21.11", "1.21.11").isPresent());
    }

    @Test
    void verifiesTheJarChecksumBeforeInstallation() throws Exception {
        var offer = new MoarUpdateService.ReleaseOffer("3.3.8", "1.21.11", "1.21.11",
                URI.create("https://github.com/evilinc-labs/MOAR/releases/download/v3.3.8/moar-3.3.8+1.21.11.zip"), null);
        byte[] jar = "test jar content".getBytes(StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jar));
        Path archive = tempDir.resolve("release.zip");
        writeArchive(archive, offer.jarName(), jar, hash);
        Path extracted = tempDir.resolve(offer.jarName());
        MoarUpdateService.extractAndVerify(archive, extracted, offer);
        assertArrayEquals(jar, Files.readAllBytes(extracted));

        writeArchive(archive, offer.jarName(), jar, "0".repeat(64));
        assertThrows(IOException.class, () -> MoarUpdateService.extractAndVerify(archive, extracted, offer));
    }

    @Test
    void validatesFabricMetadataVersionAndMinecraftRange() throws IOException {
        var offer = new MoarUpdateService.ReleaseOffer("3.3.8", "1.21.11", "1.21.11",
                URI.create("https://github.com/evilinc-labs/MOAR/releases/download/v3.3.8/moar-3.3.8+1.21.11.zip"), null);
        Path jar = tempDir.resolve("moar.jar");
        writeJar(jar, "3.3.8", "1.21.11");
        MoarUpdateService.validateJar(jar, offer);

        writeJar(jar, "3.3.8", "1.21.10");
        assertThrows(IOException.class, () -> MoarUpdateService.validateJar(jar, offer));

        var rangedOffer = new MoarUpdateService.ReleaseOffer("3.3.8", "26.1", "26.1.1",
                URI.create("https://github.com/evilinc-labs/MOAR/releases/download/v3.3.8/moar-3.3.8+26.1.1.zip"), null);
        writeJarWithRange(jar, "3.3.8", ">=26.1 <=26.1.1");
        MoarUpdateService.validateJar(jar, rangedOffer);
    }

    private static void writeJar(Path target, String version, String minecraft) throws IOException {
        writeJarWithRange(target, version, ">=" + minecraft + " <=" + minecraft);
    }

    private static void writeJarWithRange(Path target, String version, String range) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(target))) {
            jar.putNextEntry(new JarEntry("fabric.mod.json"));
            jar.write(("{\"id\":\"moar\",\"version\":\"" + version
                    + "\",\"depends\":{\"minecraft\":\"" + range + "\"}}")
                    .getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }

    private static void writeArchive(Path target, String jarName, byte[] jar, String hash) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            zip.putNextEntry(new ZipEntry(jarName));
            zip.write(jar);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("SHA256SUMS.txt"));
            zip.write((hash + "  " + jarName + "\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private static String releaseJson(String version, String minecraft) {
        String zip = "moar-" + version + "+" + minecraft + ".zip";
        return """
                {"tag_name":"v%s","draft":false,"prerelease":false,"assets":[
                  {"name":"%s","browser_download_url":"https://github.com/evilinc-labs/MOAR/releases/download/v%s/%s"}
                ]}
                """.formatted(version, zip, version, zip);
    }
}
