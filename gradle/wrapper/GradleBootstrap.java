import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class GradleBootstrap {
    private static final String PROJECT_ENV = "CC_AEROWORKS_PROJECT_DIR";

    public static void main(String[] args) throws Exception {
        Path projectDir = requiredProjectDir();
        Path propertiesPath = projectDir.resolve("gradle/wrapper/gradle-wrapper.properties");
        Properties properties = loadProperties(propertiesPath);

        URI distributionUri = URI.create(required(properties, "distributionUrl"));
        String expectedSha256 = required(properties, "distributionSha256Sum").toLowerCase();
        int timeoutMillis = Integer.parseInt(properties.getProperty("networkTimeout", "10000"));

        String archiveName = Path.of(distributionUri.getPath()).getFileName().toString();
        String distributionName = archiveName.replaceFirst("\\.zip$", "");
        Path gradleUserHome = resolveGradleUserHome();
        Path installRoot = gradleUserHome.resolve("wrapper/dists").resolve(distributionName)
            .resolve(expectedSha256.substring(0, 16));
        Path executable = findExecutable(installRoot);

        Files.createDirectories(installRoot);
        Path lockPath = installRoot.resolve(".bootstrap.lock");
        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            executable = findExecutable(installRoot);
            if (executable == null) {
                installDistribution(distributionUri, expectedSha256, timeoutMillis, installRoot);
                executable = findExecutable(installRoot);
            }
        }

        if (executable == null) {
            throw new IllegalStateException("Gradle distribution was installed but no launcher was found in " + installRoot);
        }

        if (!isWindows()) {
            executable.toFile().setExecutable(true, true);
        }

        ProcessBuilder process = new ProcessBuilder();
        process.command().add(executable.toString());
        process.command().addAll(List.of(args));
        process.directory(Path.of(System.getProperty("user.dir")).toFile());
        process.inheritIO();
        int exitCode = process.start().waitFor();
        System.exit(exitCode);
    }

    private static void installDistribution(
        URI distributionUri,
        String expectedSha256,
        int timeoutMillis,
        Path installRoot
    ) throws Exception {
        Path archive = Files.createTempFile(installRoot, "gradle-", ".zip.part");
        Path unpacked = Files.createTempDirectory(installRoot, ".unpack-");
        try {
            System.err.println("Downloading " + distributionUri);
            HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofMillis(timeoutMillis))
                .build();
            HttpRequest request = HttpRequest.newBuilder(distributionUri)
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "CC-Aeroworks-Gradle-Bootstrap")
                .build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(archive));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Gradle download failed with HTTP " + response.statusCode());
            }

            String actualSha256 = sha256(archive);
            if (!actualSha256.equals(expectedSha256)) {
                throw new SecurityException(
                    "Gradle distribution checksum mismatch. Expected " + expectedSha256 +
                    " but received " + actualSha256
                );
            }

            unzip(archive, unpacked);
            try (var children = Files.list(unpacked)) {
                List<Path> roots = children.filter(Files::isDirectory).toList();
                if (roots.size() != 1) {
                    throw new IOException("Expected exactly one directory in Gradle distribution, found " + roots.size());
                }
                Path target = installRoot.resolve(roots.get(0).getFileName());
                if (Files.exists(target)) {
                    deleteRecursively(target);
                }
                Files.move(roots.get(0), target, StandardCopyOption.ATOMIC_MOVE);
            }
        } finally {
            Files.deleteIfExists(archive);
            deleteRecursively(unpacked);
        }
    }

    private static void unzip(Path archive, Path destination) throws IOException {
        try (InputStream input = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path output = destination.resolve(entry.getName()).normalize();
                if (!output.startsWith(destination)) {
                    throw new SecurityException("Blocked zip-slip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    private static Path findExecutable(Path installRoot) throws IOException {
        if (!Files.isDirectory(installRoot)) {
            return null;
        }
        String launcher = isWindows() ? "gradle.bat" : "gradle";
        try (var paths = Files.walk(installRoot, 3)) {
            return paths
                .filter(path -> path.getFileName().toString().equals(launcher))
                .filter(path -> path.getParent() != null && path.getParent().getFileName().toString().equals("bin"))
                .findFirst()
                .orElse(null);
        }
    }

    private static Properties loadProperties(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Missing wrapper configuration: " + path);
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required wrapper property: " + key);
        }
        return value.trim();
    }

    private static Path requiredProjectDir() {
        String value = System.getenv(PROJECT_ENV);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable " + PROJECT_ENV);
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    private static Path resolveGradleUserHome() {
        String configured = System.getenv("GRADLE_USER_HOME");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".gradle");
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            paths.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                .forEach(current -> {
                    try {
                        Files.deleteIfExists(current);
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException io) {
                throw io;
            }
            throw exception;
        }
    }
}
