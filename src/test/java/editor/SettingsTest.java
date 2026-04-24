package editor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class SettingsTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void reset() {
        // Each test gets a fresh load from an empty directory
        Settings.load(tempDir);
    }

    // --- Defaults ---

    @Test
    void defaultsWhenNoFileExists() {
        assertThat(Settings.get(Settings.SettingKey.PYTHON_INTERPRETER)).isEqualTo("python3");
        assertThat(Settings.get(Settings.SettingKey.PYLSP_PATH)).isEqualTo("pylsp");
        assertThat(Settings.get(Settings.SettingKey.TS_SERVER_PATH)).isEqualTo("typescript-language-server --stdio");
    }

    // --- Load ---

    @Test
    void loadsAllValuesFromFile() throws IOException {
        writeSettings(tempDir,
            "{schema_version:'1.0.0',project:{sdk:{" +
            "python:{interpreter:'/usr/bin/python3',pylspPath:'/usr/bin/pylsp'}," +
            "typescript:{serverPath:'npx --yes typescript-language-server --stdio'}" +
            "}}}");
        Settings.load(tempDir);

        assertThat(Settings.get(Settings.SettingKey.PYTHON_INTERPRETER)).isEqualTo("/usr/bin/python3");
        assertThat(Settings.get(Settings.SettingKey.PYLSP_PATH)).isEqualTo("/usr/bin/pylsp");
        assertThat(Settings.get(Settings.SettingKey.TS_SERVER_PATH)).isEqualTo("npx --yes typescript-language-server --stdio");
    }

    @Test
    void loadsPythonOnlyLeavesTypeScriptDefault() throws IOException {
        writeSettings(tempDir,
            "{project:{sdk:{python:{interpreter:'/usr/bin/python3',pylspPath:'/usr/bin/pylsp'}}}}");
        Settings.load(tempDir);

        assertThat(Settings.get(Settings.SettingKey.PYTHON_INTERPRETER)).isEqualTo("/usr/bin/python3");
        assertThat(Settings.get(Settings.SettingKey.TS_SERVER_PATH)).isEqualTo("typescript-language-server --stdio");
    }

    @Test
    void loadsTypeScriptOnlyLeavesPythonDefaults() throws IOException {
        writeSettings(tempDir,
            "{project:{sdk:{typescript:{serverPath:'npx --yes typescript-language-server --stdio'}}}}");
        Settings.load(tempDir);

        assertThat(Settings.get(Settings.SettingKey.PYTHON_INTERPRETER)).isEqualTo("python3");
        assertThat(Settings.get(Settings.SettingKey.PYLSP_PATH)).isEqualTo("pylsp");
        assertThat(Settings.get(Settings.SettingKey.TS_SERVER_PATH)).isEqualTo("npx --yes typescript-language-server --stdio");
    }

    @Test
    void missingProjectKeyUsesDefaults() throws IOException {
        writeSettings(tempDir, "{schema_version:'1.0.0'}");
        Settings.load(tempDir);

        assertThat(Settings.get(Settings.SettingKey.PYTHON_INTERPRETER)).isEqualTo("python3");
        assertThat(Settings.get(Settings.SettingKey.PYLSP_PATH)).isEqualTo("pylsp");
        assertThat(Settings.get(Settings.SettingKey.TS_SERVER_PATH)).isEqualTo("typescript-language-server --stdio");
    }

    @Test
    void corruptFileUsesDefaults() throws IOException {
        writeSettings(tempDir, "not valid json5 }{");
        Settings.load(tempDir);

        assertThat(Settings.get(Settings.SettingKey.PYTHON_INTERPRETER)).isEqualTo("python3");
    }

    // --- Save round-trip ---

    @Test
    void savedValuesReloadCorrectly() {
        Settings.set(Settings.SettingKey.PYTHON_INTERPRETER, "/custom/python");
        Settings.set(Settings.SettingKey.PYLSP_PATH, "/custom/pylsp");
        Settings.set(Settings.SettingKey.TS_SERVER_PATH, "npx --yes typescript-language-server --stdio");

        Settings.load(tempDir); // reload from the file that was just saved

        assertThat(Settings.get(Settings.SettingKey.PYTHON_INTERPRETER)).isEqualTo("/custom/python");
        assertThat(Settings.get(Settings.SettingKey.PYLSP_PATH)).isEqualTo("/custom/pylsp");
        assertThat(Settings.get(Settings.SettingKey.TS_SERVER_PATH)).isEqualTo("npx --yes typescript-language-server --stdio");
    }

    @Test
    void savedFileExistsAfterSet() {
        Settings.set(Settings.SettingKey.PYTHON_INTERPRETER, "/some/python");
        assertThat(Files.exists(tempDir.resolve(".ide/settings.json5"))).isTrue();
    }

    // --- Blank / null fallback to defaults ---

    @Test
    void blankInterpreterFallsBackToDefault() {
        Settings.set(Settings.SettingKey.PYTHON_INTERPRETER, "   ");
        assertThat(Settings.get(Settings.SettingKey.PYTHON_INTERPRETER)).isEqualTo("python3");
    }

    @Test
    void nullInterpreterFallsBackToDefault() {
        Settings.set(Settings.SettingKey.PYTHON_INTERPRETER, null);
        assertThat(Settings.get(Settings.SettingKey.PYTHON_INTERPRETER)).isEqualTo("python3");
    }

    @Test
    void blankPylspPathFallsBackToDefault() {
        Settings.set(Settings.SettingKey.PYLSP_PATH, "");
        assertThat(Settings.get(Settings.SettingKey.PYLSP_PATH)).isEqualTo("pylsp");
    }

    @Test
    void blankTsServerPathFallsBackToDefault() {
        Settings.set(Settings.SettingKey.TS_SERVER_PATH, "  ");
        assertThat(Settings.get(Settings.SettingKey.TS_SERVER_PATH)).isEqualTo("typescript-language-server --stdio");
    }

    // --- Helpers ---

    private static void writeSettings(Path root, String json5) throws IOException {
        Path dir = root.resolve(".ide");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("settings.json5"), json5);
    }
}
