package dev.moar.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.jar.JarFile;

/** Checks GitHub Releases and installs a verified JAR for the current Minecraft version. */
public final class MoarUpdateService {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Updates");
    private static final URI LATEST_RELEASE = URI.create("https://api.github.com/repos/evilinc-labs/MOAR/releases/latest");
    private static final Pattern VERSION = Pattern.compile("(?:v)?(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final long MAX_ARCHIVE_BYTES = 100L * 1024 * 1024;
    private static final long MAX_JAR_BYTES = 60L * 1024 * 1024;
    private static final String SKIPPED_VERSION = "skippedVersion";

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MOAR update check");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private volatile ReleaseOffer pending;
    private boolean checkStarted;
    private boolean delivered;

    public synchronized void checkAfterStartup() {
        if (checkStarted) return;
        checkStarted = true;
        worker.execute(() -> {
            try {
                ModContainer mod = FabricLoader.getInstance().getModContainer("moar").orElseThrow();
                String currentVersion = mod.getMetadata().getVersion().getFriendlyString();
                String minecraftVersion = FabricLoader.getInstance().getModContainer("minecraft")
                        .orElseThrow().getMetadata().getVersion().getFriendlyString();
                HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE)
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "MOAR-Updater")
                        .timeout(Duration.ofSeconds(20)).GET().build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    LOGGER.debug("Update check returned HTTP {}", response.statusCode());
                    return;
                }
                Optional<ReleaseOffer> offer = parseRelease(response.body(), currentVersion, minecraftVersion);
                if (offer.isPresent() && !offer.get().version().equals(readSkippedVersion())) {
                    pending = offer.get();
                }
            } catch (Exception ex) {
                // Offline clients should still start normally.
                LOGGER.debug("Could not check for a MOAR update", ex);
            }
        });
    }

    public synchronized ReleaseOffer takeOffer() {
        if (delivered || pending == null) return null;
        delivered = true;
        return pending;
    }

    public void skipVersion(ReleaseOffer offer) {
        try {
            Path path = settingsPath();
            Files.createDirectories(path.getParent());
            Properties properties = new Properties();
            properties.setProperty(SKIPPED_VERSION, offer.version());
            Path temp = Files.createTempFile(path.getParent(), "update-", ".tmp");
            try {
                try (var out = Files.newOutputStream(temp)) {
                    properties.store(out, "MOAR update preferences");
                }
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException ex) {
            LOGGER.warn("Could not save the dismissed MOAR version", ex);
        }
    }

    public void installAsync(ReleaseOffer offer, Consumer<InstallResult> callback) {
        worker.execute(() -> callback.accept(install(offer)));
    }

    private InstallResult install(ReleaseOffer offer) {
        Path archive = null;
        Path stagedJar = null;
        Path current = null;
        Path backup = null;
        boolean verifiedJar = false;
        try {
            Path updates = settingsPath().getParent().resolve("updates");
            Files.createDirectories(updates);
            archive = Files.createTempFile(updates, "moar-", ".zip.part");
            String archiveHash = download(offer.downloadUri(), archive, MAX_ARCHIVE_BYTES);
            if (offer.archiveSha256() != null && !offer.archiveSha256().equalsIgnoreCase(archiveHash)) {
                throw new IOException("Release archive checksum did not match");
            }
            stagedJar = updates.resolve(offer.jarName());
            if (Files.exists(stagedJar)) Files.delete(stagedJar);
            extractAndVerify(archive, stagedJar, offer);
            validateJar(stagedJar, offer);
            verifiedJar = true;

            ModContainer mod = FabricLoader.getInstance().getModContainer("moar").orElseThrow();
            if (mod.getOrigin().getKind() != ModOrigin.Kind.PATH || mod.getOrigin().getPaths().size() != 1) {
                return new InstallResult(false, "This launcher manages MOAR outside a normal mods folder.", stagedJar);
            }
            current = mod.getOrigin().getPaths().get(0).toAbsolutePath().normalize();
            if (!Files.isRegularFile(current) || !current.getFileName().toString().endsWith(".jar")) {
                return new InstallResult(false, "The current MOAR JAR could not be located.", stagedJar);
            }
            Path modsDirectory = FabricLoader.getInstance().getGameDir().resolve("mods").toRealPath();
            if (!current.toRealPath().startsWith(modsDirectory)) {
                return new InstallResult(false, "This launcher manages MOAR outside the mods folder.", stagedJar);
            }
            Path target = current.resolveSibling(offer.jarName());
            backup = current.resolveSibling(current.getFileName() + ".disabled");
            if (Files.exists(target) || Files.exists(backup)) {
                return new InstallResult(false, "Another MOAR update or backup is already in the mods folder.", stagedJar);
            }
            Path incoming = Files.createTempFile(current.getParent(), ".moar-update-", ".part");
            try {
                Files.copy(stagedJar, incoming, StandardCopyOption.REPLACE_EXISTING);
                Files.move(current, backup);
                try {
                    Files.move(incoming, target);
                } catch (IOException ex) {
                    try {
                        Files.move(backup, current);
                    } catch (IOException rollback) {
                        ex.addSuppressed(rollback);
                        throw new IOException("Install failed and the old JAR could not be restored from " + backup,
                                ex);
                    }
                    throw ex;
                }
            } finally {
                Files.deleteIfExists(incoming);
            }
            try { Files.deleteIfExists(stagedJar); } catch (IOException ex) {
                LOGGER.debug("Could not remove cached update JAR", ex);
            }
            return new InstallResult(true, "Installed. Restart Minecraft to load the update.", null);
        } catch (Exception ex) {
            LOGGER.warn("Could not install MOAR {}", offer.version(), ex);
            boolean restoreNeeded = current != null && backup != null
                    && !Files.exists(current) && Files.exists(backup);
            String message = restoreNeeded
                    ? "Restore the old .jar.disabled file before restarting."
                    : "Automatic install failed; the current build remains installed.";
            if (!verifiedJar && stagedJar != null) {
                try { Files.deleteIfExists(stagedJar); } catch (IOException ignored) { }
            }
            return new InstallResult(false, message,
                    verifiedJar && stagedJar != null && Files.isRegularFile(stagedJar) ? stagedJar : null);
        } finally {
            if (archive != null) {
                try { Files.deleteIfExists(archive); } catch (IOException ignored) { }
            }
        }
    }

    private String download(URI uri, Path target, long maximum) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", "MOAR-Updater")
                .timeout(Duration.ofMinutes(2)).GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) throw new IOException("Download returned HTTP " + response.statusCode());
        MessageDigest digest = sha256();
        try (InputStream in = response.body(); var out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maximum) throw new IOException("Release archive exceeds size limit");
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    static void extractAndVerify(Path archive, Path target, ReleaseOffer offer) throws IOException {
        String jarHash = null;
        String manifest = null;
        boolean foundJar = false;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (entry.getName().equals(offer.jarName())) {
                    if (foundJar) throw new IOException("Duplicate JAR in release archive");
                    foundJar = true;
                    MessageDigest digest = sha256();
                    try (var out = Files.newOutputStream(target)) {
                        byte[] buffer = new byte[8192];
                        long total = 0;
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            total += read;
                            if (total > MAX_JAR_BYTES) throw new IOException("MOAR JAR exceeds size limit");
                            digest.update(buffer, 0, read);
                            out.write(buffer, 0, read);
                        }
                    }
                    jarHash = hex(digest.digest());
                } else if (entry.getName().equals("SHA256SUMS.txt")) {
                    if (manifest != null) throw new IOException("Duplicate checksum file in release archive");
                    manifest = new String(zip.readNBytes(512), StandardCharsets.UTF_8);
                    if (zip.read() != -1) throw new IOException("Checksum file exceeds size limit");
                } else {
                    throw new IOException("Unexpected file in release archive");
                }
                zip.closeEntry();
            }
        }
        if (!foundJar || manifest == null || jarHash == null) throw new IOException("Incomplete release archive");
        Matcher match = Pattern.compile("(?i)^([0-9a-f]{64})\\s+" + Pattern.quote(offer.jarName()) + "\\s*$")
                .matcher(manifest);
        if (!match.matches() || !match.group(1).equalsIgnoreCase(jarHash)) {
            throw new IOException("MOAR JAR checksum did not match");
        }
    }

    static void validateJar(Path jar, ReleaseOffer offer) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            var entry = file.getJarEntry("fabric.mod.json");
            if (entry == null) throw new IOException("Release JAR has no Fabric metadata");
            try (InputStream in = file.getInputStream(entry)) {
                JsonObject metadata = JsonParser.parseString(new String(in.readNBytes(65536), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                String minecraftDependency = metadata.getAsJsonObject("depends")
                        .get("minecraft").getAsString();
                if (!"moar".equals(metadata.get("id").getAsString())
                        || !offer.version().equals(metadata.get("version").getAsString())
                        || !VersionPredicate.parse(minecraftDependency)
                                .test(Version.parse(offer.minecraftVersion()))) {
                    throw new IOException("Release JAR does not match the selected MOAR build");
                }
            }
        } catch (VersionParsingException ex) {
            throw new IOException("Release JAR has an invalid Minecraft version range", ex);
        } catch (RuntimeException ex) {
            throw new IOException("Release JAR has invalid Fabric metadata", ex);
        }
    }

    static Optional<ReleaseOffer> parseRelease(String json, String currentVersion, String minecraftVersion) {
        try {
            if (!minecraftVersion.matches("[0-9]+(?:\\.[0-9]+){1,2}")) return Optional.empty();
            JsonObject release = JsonParser.parseString(json).getAsJsonObject();
            if (release.get("draft").getAsBoolean() || release.get("prerelease").getAsBoolean()) {
                return Optional.empty();
            }
            String tag = release.get("tag_name").getAsString();
            if (!tag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+")
                    || compareVersions(tag, currentVersion) <= 0) return Optional.empty();
            String version = tag.substring(1);
            String artifactVersion = artifactVersionFor(minecraftVersion);
            String jarName = "moar-" + version + "+" + artifactVersion + ".jar";
            String zipName = jarName.substring(0, jarName.length() - 4) + ".zip";
            JsonArray assets = release.getAsJsonArray("assets");
            for (var assetElement : assets) {
                JsonObject asset = assetElement.getAsJsonObject();
                if (!zipName.equals(asset.get("name").getAsString())) continue;
                URI uri = URI.create(asset.get("browser_download_url").getAsString());
                if (!"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost())
                        || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                        || !uri.getPath().equals("/evilinc-labs/MOAR/releases/download/" + tag + "/" + zipName)) {
                    return Optional.empty();
                }
                String digest = asset.has("digest") && !asset.get("digest").isJsonNull()
                        ? asset.get("digest").getAsString() : null;
                if (digest != null && !digest.matches("(?i)sha256:[0-9a-f]{64}")) return Optional.empty();
                return Optional.of(new ReleaseOffer(version, minecraftVersion, artifactVersion, uri,
                        digest == null ? null : digest.substring(7).toLowerCase(Locale.ROOT)));
            }
        } catch (RuntimeException ignored) {
            // A malformed or partially published release is not an update offer.
        }
        return Optional.empty();
    }

    static int compareVersions(String left, String right) {
        int[] a = versionParts(left);
        int[] b = versionParts(right);
        if (a == null || b == null) return -1;
        for (int i = 0; i < 3; i++) {
            int compared = Integer.compare(a[i], b[i]);
            if (compared != 0) return compared;
        }
        return 0;
    }

    private static String artifactVersionFor(String minecraftVersion) {
        return switch (minecraftVersion) {
            case "1.21.9" -> "1.21.10";
            case "26.1" -> "26.1.1";
            default -> minecraftVersion;
        };
    }

    private static int[] versionParts(String version) {
        Matcher match = VERSION.matcher(version.split("\\+", 2)[0]);
        if (!match.matches()) return null;
        try {
            return new int[] { Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)),
                    Integer.parseInt(match.group(3)) };
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String readSkippedVersion() {
        try (InputStream in = Files.newInputStream(settingsPath())) {
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty(SKIPPED_VERSION, "");
        } catch (IOException ex) {
            return "";
        }
    }

    private static Path settingsPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("moar/update.properties");
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    public record ReleaseOffer(String version, String minecraftVersion, String artifactVersion, URI downloadUri,
                               String archiveSha256) {
        public String jarName() {
            return "moar-" + version + "+" + artifactVersion + ".jar";
        }
    }

    public record InstallResult(boolean installed, String message, Path stagedJar) {
    }
}
