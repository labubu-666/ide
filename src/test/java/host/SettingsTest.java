package host;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import settings.Settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class SettingsTest {

    @TempDir
    Path tempDir;

    private Path userHome;
    private Path projectRoot;
    private String originalUserHome;

    @BeforeEach
    void reset() throws IOException {
        originalUserHome = System.getProperty("user.home");
        userHome = tempDir.resolve("home");
        projectRoot = tempDir.resolve("project");
        Files.createDirectories(userHome);
        Files.createDirectories(projectRoot);
        System.setProperty("user.home", userHome.toString());
        Settings.load(projectRoot);
    }

    @AfterEach
    void restoreUserHome() {
        if (originalUserHome == null) {
            System.clearProperty("user.home");
            return;
        }
        System.setProperty("user.home", originalUserHome);
    }

    // --- Defaults ---

    @Test
    void defaultsWhenNoFileExists() {
        assertThat(Settings.get(Settings.SettingKey.PYTHON_INTERPRETER)).isEqualTo("python3");
        assertThat(Settings.get(Settings.SettingKey.PYLSP_PATH)).isEqualTo("pylsp");
        assertThat(Settings.get(Settings.SettingKey.TS_SERVER_PATH)).isEqualTo("typescript-language-server --stdio");
        assertThat(Settings.getRunConfigurations()).isEmpty();
    }

    @Test
    void projectOverridesGlobalSdkSettings() throws IOException {
        writeGlobalSettings("{project:{sdk:{python:{interpreter:'/global/python',pylspPath:'/global/pylsp'}}}}");
        writeProjectSettings("{project:{sdk:{python:{interpreter:'/project/python'}}}}");

        Settings.load(projectRoot);

        assertThat(Settings.get(Settings.SettingKey.PYTHON_INTERPRETER)).isEqualTo("/project/python");
        assertThat(Settings.get(Settings.SettingKey.PYLSP_PATH)).isEqualTo("/global/pylsp");
    }

    @Test
    void runConfigurationsAreMergedAndProjectOverridesGlobalOnCollision() throws IOException {
        writeGlobalSettings("{project:{runConfigurations:{" +
                "ruff_format:{preCommands:[],commands:['global ruff'],postCommands:[]}," +
                "global_only:{preCommands:[],commands:['global only'],postCommands:[]}" +
                "}}}");
        writeProjectSettings("{project:{runConfigurations:{" +
                "ruff_format:{preCommands:[],commands:['project ruff'],postCommands:[]}," +
                "project_only:{preCommands:[],commands:['project only'],postCommands:[]}" +
                "}}}");

        Settings.load(projectRoot);

        Map<String, Settings.RunConfiguration> runConfigurations = Settings.getRunConfigurations();
        assertThat(runConfigurations).containsOnlyKeys("ruff_format", "global_only", "project_only");
        assertThat(runConfigurations.get("ruff_format").commands()).containsExactly("project ruff");
        assertThat(runConfigurations.get("global_only").commands()).containsExactly("global only");
        assertThat(runConfigurations.get("project_only").commands()).containsExactly("project only");
        assertThat(Settings.getRunConfigurationCollisionKeys()).containsExactly("ruff_format");
    }

    @Test
    void malformedRunConfigurationEntriesAreIgnored() throws IOException {
        writeProjectSettings(
                "{project:{runConfigurations:{" +
                        "good:{preCommands:[],commands:['uv run pytest'],postCommands:[]}," +
                        "missingPost:{preCommands:[],commands:['uv run ruff format .']}," +
                        "wrongType:{preCommands:'bad',commands:['x'],postCommands:[]}," +
                        "wrongShape:['bad']" +
                        "}}}");

        Settings.load(projectRoot);

        Map<String, Settings.RunConfiguration> runConfigurations = Settings.getRunConfigurations();
        assertThat(runConfigurations).containsOnlyKeys("good");
        assertThat(runConfigurations.get("good").commands()).containsExactly("uv run pytest");
    }

    @Test
    void globalScopePersistsUnderUserHomeIdeFolder() {
        Settings.set(Settings.Scope.GLOBAL, Settings.SettingKey.PYTHON_INTERPRETER, "/global/python");

        assertThat(Files.exists(userHome.resolve(".ide/settings.json5"))).isTrue();
        Settings.load(projectRoot);
        assertThat(Settings.get(Settings.SettingKey.PYTHON_INTERPRETER)).isEqualTo("/global/python");
    }

    @Test
    void projectScopePersistsUnderProjectIdeFolder() {
        Settings.set(Settings.SettingKey.PYTHON_INTERPRETER, "/some/python");

        assertThat(Files.exists(projectRoot.resolve(".ide/settings.json5"))).isTrue();
        Settings.load(projectRoot);
        assertThat(Settings.get(Settings.SettingKey.PYTHON_INTERPRETER)).isEqualTo("/some/python");
    }

    @Test
    void savedRunConfigurationsReloadCorrectly() {
        Map<String, Settings.RunConfiguration> globalInput = new LinkedHashMap<>();
        globalInput.put("ruff_format", new Settings.RunConfiguration(
                List.of(),
                List.of("global ruff"),
                List.of()));
        Map<String, Settings.RunConfiguration> projectInput = new LinkedHashMap<>();
        projectInput.put("pytest", new Settings.RunConfiguration(
                List.of("echo preparing"),
                List.of("uv run pytest"),
                List.of("echo done")));

        Settings.setRunConfigurations(Settings.Scope.GLOBAL, globalInput);
        Settings.setRunConfigurations(Settings.Scope.PROJECT, projectInput);

        Settings.load(projectRoot);

        Map<String, Settings.RunConfiguration> runConfigurations = Settings.getRunConfigurations();
        assertThat(runConfigurations).containsOnlyKeys("ruff_format", "pytest");
        assertThat(runConfigurations.get("ruff_format").commands()).containsExactly("global ruff");
        assertThat(runConfigurations.get("pytest").preCommands()).containsExactly("echo preparing");
        assertThat(runConfigurations.get("pytest").commands()).containsExactly("uv run pytest");
        assertThat(runConfigurations.get("pytest").postCommands()).containsExactly("echo done");
    }

    @Test
    void blankProjectInterpreterFallsBackToGlobalValue() {
        Settings.set(Settings.Scope.GLOBAL, Settings.SettingKey.PYTHON_INTERPRETER, "/global/python");
        Settings.set(Settings.SettingKey.PYTHON_INTERPRETER, "   ");

        assertThat(Settings.get(Settings.SettingKey.PYTHON_INTERPRETER)).isEqualTo("/global/python");
    }

    @Test
    void blankGlobalInterpreterFallsBackToDefault() {
        Settings.set(Settings.Scope.GLOBAL, Settings.SettingKey.PYTHON_INTERPRETER, "  ");

        assertThat(Settings.get(Settings.SettingKey.PYTHON_INTERPRETER)).isEqualTo("python3");
    }

    // --- Helpers ---

    private static void writeSettings(Path root, String json5) throws IOException {
        Path dir = root.resolve(".ide");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("settings.json5"), json5);
    }

    private void writeGlobalSettings(String json5) throws IOException {
        writeSettings(userHome, json5);
    }

    private void writeProjectSettings(String json5) throws IOException {
        writeSettings(projectRoot, json5);
    }
}
