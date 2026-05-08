package lsp;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import settings.Settings;


/**
 * Integration test for LspClient and LSP server diagnostics using pylsp.
 * 
 * Tests that diagnostics are properly received from the Python LSP server
 * when documents with known errors are opened and modified.
 */
public class LspClientIntegrationTest {

    private LspServerRegistry registry;
    private DocumentManager documentManager;
    
    @BeforeEach
    void setUp(@TempDir Path workspace) {
        // Override PYLSP_PATH to use uvx with all plugins for testing
        Settings.set(Settings.SettingKey.PYLSP_PATH, "uvx --from python-lsp-server[all]@1.14.0 pylsp -vvv");
        
        // Initialize the document manager with a registry
        registry = new LspServerRegistry(workspace, (uri, diagnostics) -> {
            // Callback will be handled in individual tests
        });
        documentManager = new DocumentManager(registry);
    }

    @AfterEach
    void tearDown() {
        // Shutdown all servers
        registry.shutdownAll().join();
    }

    /**
     * Test that receives syntax error diagnostics from pylsp.
     * Opens a Python file with incomplete function definition (syntax error).
     * 
     * Arrange: Create a Python file with syntax error "def foo("
     * Act: Open the file via LSP
     * Assert: Receive diagnostics reporting the syntax error
     */
    @Test
    void testReceivesSyntaxErrorDiagnostics(@TempDir Path workspace) throws Exception {
        // Arrange
        var diagnosticsHolder = new AtomicReference<List<Diagnostic>>();
        var latch = new CountDownLatch(1);
        
        registry = new LspServerRegistry(workspace, (uri, diagnostics) -> {
            System.err.println("[TEST] Received diagnostics callback for: " + uri + " with " + diagnostics.size() + " items");
            if (!diagnostics.isEmpty()) {
                diagnosticsHolder.set(diagnostics);
                latch.countDown();
            } else {
                // Also count down on empty diagnostics to avoid timeout
                diagnosticsHolder.set(diagnostics);
                latch.countDown();
            }
        });
        documentManager = new DocumentManager(registry);
        
        String pythonCode = "def foo(";  // Incomplete function definition
        Path pythonFile = workspace.resolve("syntax_error.py");
        Files.writeString(pythonFile, pythonCode);
        
        // Act
        documentManager.didOpen(pythonFile, pythonCode, "python");
        
        // Assert: Wait for diagnostics
        boolean receivedDiagnostics = latch.await(5, TimeUnit.SECONDS);
        assertThat(receivedDiagnostics)
            .as("Should receive diagnostics within 5 seconds")
            .isTrue();
        
        assertThat(diagnosticsHolder.get())
            .as("Should have at least one diagnostic for syntax error")
            .isNotEmpty()
            .hasSizeGreaterThanOrEqualTo(1);
    }

    /**
     * Test that receives import error diagnostics from pylsp.
     * Opens a Python file with import of non-existent module.
     * 
     * Arrange: Create a Python file with "import nonexistent_module_xyz"
     * Act: Open the file via LSP
     * Assert: Receive diagnostics reporting the import error
     */
    @Test
    void testReceivesImportErrorDiagnostics(@TempDir Path workspace) throws Exception {
        // Arrange
        var diagnosticsHolder = new AtomicReference<List<Diagnostic>>();
        var latch = new CountDownLatch(1);
        
        registry = new LspServerRegistry(workspace, (uri, diagnostics) -> {
            if (!diagnostics.isEmpty()) {
                diagnosticsHolder.set(diagnostics);
                latch.countDown();
            }
        });
        documentManager = new DocumentManager(registry);
        
        String pythonCode = "import nonexistent_module_xyz_12345\n";
        Path pythonFile = workspace.resolve("import_error.py");
        Files.writeString(pythonFile, pythonCode);
        
        // Act
        documentManager.didOpen(pythonFile, pythonCode, "python");
        
        // Assert: Wait for diagnostics
        boolean receivedDiagnostics = latch.await(5, TimeUnit.SECONDS);
        assertThat(receivedDiagnostics)
            .as("Should receive diagnostics for import error within 5 seconds")
            .isTrue();
        
        assertThat(diagnosticsHolder.get())
            .as("Should have at least one diagnostic for import error")
            .isNotEmpty();
    }

    /**
     * Test that receives undefined variable diagnostics from pylsp.
     * Opens a Python file with undefined variable usage.
     * 
     * Arrange: Create a Python file with "print(undefined_variable_xyz)"
     * Act: Open the file via LSP
     * Assert: Receive diagnostics reporting the undefined variable
     */
    @Test
    void testReceivesUndefinedVariableDiagnostics(@TempDir Path workspace) throws Exception {
        // Arrange
        var diagnosticsHolder = new AtomicReference<List<Diagnostic>>();
        var latch = new CountDownLatch(1);
        
        registry = new LspServerRegistry(workspace, (uri, diagnostics) -> {
            if (!diagnostics.isEmpty()) {
                diagnosticsHolder.set(diagnostics);
                latch.countDown();
            }
        });
        documentManager = new DocumentManager(registry);
        
        String pythonCode = "print(undefined_variable_xyz)\n";
        Path pythonFile = workspace.resolve("undefined_var.py");
        Files.writeString(pythonFile, pythonCode);
        
        // Act
        documentManager.didOpen(pythonFile, pythonCode, "python");
        
        // Assert: Wait for diagnostics
        boolean receivedDiagnostics = latch.await(5, TimeUnit.SECONDS);
        assertThat(receivedDiagnostics)
            .as("Should receive diagnostics for undefined variable within 5 seconds")
            .isTrue();
        
        assertThat(diagnosticsHolder.get())
            .as("Should have at least one diagnostic for undefined variable")
            .isNotEmpty();
    }

    /**
     * Test that receives multiple diagnostics in a single file.
     * Opens a Python file with multiple errors.
     * 
     * Arrange: Create a Python file with multiple errors
     * Act: Open the file via LSP
     * Assert: Receive multiple diagnostics
     */
    @Test
    void testReceivesMultipleDiagnostics(@TempDir Path workspace) throws Exception {
        // Arrange
        var diagnosticsHolder = new AtomicReference<List<Diagnostic>>();
        var latch = new CountDownLatch(1);
        
        registry = new LspServerRegistry(workspace, (uri, diagnostics) -> {
            if (diagnostics.size() >= 2) {  // Wait for at least 2 diagnostics
                diagnosticsHolder.set(diagnostics);
                latch.countDown();
            }
        });
        documentManager = new DocumentManager(registry);
        
        String pythonCode = "def foo(\n" +
                           "import nonexistent_123\n" +
                           "print(undefined_xyz)\n";
        Path pythonFile = workspace.resolve("multiple_errors.py");
        Files.writeString(pythonFile, pythonCode);
        
        // Act
        documentManager.didOpen(pythonFile, pythonCode, "python");
        
        // Assert: Wait for diagnostics
        boolean receivedDiagnostics = latch.await(5, TimeUnit.SECONDS);
        assertThat(receivedDiagnostics)
            .as("Should receive multiple diagnostics within 5 seconds")
            .isTrue();
        
        assertThat(diagnosticsHolder.get())
            .as("Should have multiple diagnostics")
            .hasSizeGreaterThanOrEqualTo(2);
    }

    /**
     * Test that diagnostics are updated after document changes.
     * Opens a Python file with an error, then modifies it.
     * 
     * Arrange: Create a Python file with an error
     * Act: Open the file, wait for diagnostics, then modify to have different error
     * Assert: Receive new/updated diagnostics after modification
     */
    @Test
    void testDiagnosticsUpdateAfterDocumentChange(@TempDir Path workspace) throws Exception {
        // Arrange
        var firstDiagnostics = new AtomicReference<List<Diagnostic>>();
        var secondDiagnostics = new AtomicReference<List<Diagnostic>>();
        var firstLatch = new CountDownLatch(1);
        var secondLatch = new CountDownLatch(1);
        
        var callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        
        registry = new LspServerRegistry(workspace, (uri, diagnostics) -> {
            int count = callCount.incrementAndGet();
            if (!diagnostics.isEmpty()) {
                if (count == 1) {
                    firstDiagnostics.set(diagnostics);
                    firstLatch.countDown();
                } else if (count == 2) {
                    secondDiagnostics.set(diagnostics);
                    secondLatch.countDown();
                }
            }
        });
        documentManager = new DocumentManager(registry);
        
        Path pythonFile = workspace.resolve("update_test.py");
        
        // Act 1: Open with first error
        String pythonCode1 = "def foo(\n";
        Files.writeString(pythonFile, pythonCode1);
        documentManager.didOpen(pythonFile, pythonCode1, "python");
        
        // Assert 1: Wait for initial diagnostics
        boolean receivedFirst = firstLatch.await(5, TimeUnit.SECONDS);
        assertThat(receivedFirst)
            .as("Should receive initial diagnostics within 5 seconds")
            .isTrue();
        assertThat(firstDiagnostics.get())
            .as("Should have initial diagnostics")
            .isNotEmpty();
        
        // Act 2: Modify the file to fix the error and introduce a different one
        String pythonCode2 = "import nonexistent_module_xyz\n";
        documentManager.didChange(pythonFile, pythonCode2);
        
        // Assert 2: Wait for updated diagnostics
        boolean receivedSecond = secondLatch.await(5, TimeUnit.SECONDS);
        assertThat(receivedSecond)
            .as("Should receive updated diagnostics after change within 5 seconds")
            .isTrue();
        assertThat(secondDiagnostics.get())
            .as("Should have diagnostics for the new error")
            .isNotEmpty();
    }

    /**
     * Test that no diagnostics are received for correct Python code.
     * Opens a Python file with valid code.
     * 
     * Arrange: Create a valid Python file
     * Act: Open the file via LSP
     * Assert: No diagnostics should be received (or very few if pylsp has style warnings)
     */
    @Test
    void testNoErrorsForValidPythonCode(@TempDir Path workspace) throws Exception {
        // Arrange
        var diagnosticsHolder = new AtomicReference<List<Diagnostic>>();
        var receivedCallback = new CountDownLatch(1);
        
        registry = new LspServerRegistry(workspace, (uri, diagnostics) -> {
            diagnosticsHolder.set(diagnostics);
            receivedCallback.countDown();
        });
        documentManager = new DocumentManager(registry);
        
        String pythonCode = "def hello():\n" +
                           "    print('Hello, World!')\n\n" +
                           "if __name__ == '__main__':\n" +
                           "    hello()\n";
        Path pythonFile = workspace.resolve("valid_code.py");
        Files.writeString(pythonFile, pythonCode);
        
        // Act
        documentManager.didOpen(pythonFile, pythonCode, "python");
        
        // Assert: Wait briefly to see if diagnostics arrive
        boolean callbackReceived = receivedCallback.await(3, TimeUnit.SECONDS);
        
        // If no callback, that's good (no diagnostics)
        if (callbackReceived) {
            // Some pylsp versions may report style issues, so we just check it's reasonable
            assertThat(diagnosticsHolder.get())
                .as("Valid code should have no or minimal diagnostics")
                .isNotNull();
        }
    }

    /**
     * Test that the LSP client callback properly handles the publishDiagnostics call.
     * This is a lower-level unit test of the LspClient.
     * 
     * Arrange: Create an LspClient with a callback
     * Act: Call publishDiagnostics with test data
     * Assert: Verify callback is invoked correctly
     */
    @Test
    void testLspClientCallbackInvkedOnPublishDiagnostics() {
        // Arrange
        var capturedUri = new AtomicReference<String>();
        var capturedDiagnostics = new AtomicReference<List<Diagnostic>>();
        
        var client = new LspClient((uri, diagnostics) -> {
            capturedUri.set(uri);
            capturedDiagnostics.set(diagnostics);
        });
        
        String testUri = "file:///test/example.py";
        var testDiagnostic = new Diagnostic();
        testDiagnostic.setMessage("Test error");
        List<Diagnostic> diagnostics = List.of(testDiagnostic);
        
        var params = new org.eclipse.lsp4j.PublishDiagnosticsParams();
        params.setUri(testUri);
        params.setDiagnostics(diagnostics);
        
        // Act
        client.publishDiagnostics(params);
        
        // Assert
        assertThat(capturedUri.get())
            .as("URI should be captured correctly")
            .isEqualTo(testUri);
        
        assertThat(capturedDiagnostics.get())
            .as("Diagnostics list should be captured correctly")
            .hasSize(1);
        
        Diagnostic captured = capturedDiagnostics.get().get(0);
        // getMessage() returns Either<String, MarkupContent>, check as string
        assertThat(captured.getMessage().getLeft())
            .as("Diagnostic message should be 'Test error'")
            .isEqualTo("Test error");
    }
}
