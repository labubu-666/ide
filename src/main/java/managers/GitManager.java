package managers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Manages git repository detection, information retrieval, and operations.
 * Uses JGit to detect git repositories, extract branch information, and perform git operations.
 * Provides graceful fallback when git is not available or not a git repo.
 */
public class GitManager {
    private final Path rootPath;
    private Repository repository;
    private boolean isGitRepo;
    private Git git;
    private GitStatusWatcher watcher;

    public GitManager(Path rootPath) {
        this.rootPath = rootPath;
        this.isGitRepo = false;
        try {
            initializeRepository();
            initializeWatcher();
        } catch (IOException e) {
            // Not a git repository or error accessing it
            this.repository = null;
        }
    }

    /**
     * Initializes the git repository by finding the .git directory
     * starting from the root path and searching upward.
     */
    private void initializeRepository() throws IOException {
        RepositoryBuilder builder = new RepositoryBuilder();
        builder.findGitDir(rootPath.toFile());
        File gitDir = builder.getGitDir();
        
        if (gitDir != null && gitDir.exists()) {
            repository = builder.build();
            isGitRepo = true;
            this.git = new Git(repository);
        }
    }

    /**
     * Initializes the file system watcher for git changes.
     */
    private void initializeWatcher() throws IOException {
        if (!isGitRepo || repository == null) {
            return;
        }
        
        File gitDir = repository.getDirectory();
        if (gitDir != null && gitDir.exists()) {
            watcher = new GitStatusWatcher(gitDir.toPath(), rootPath);
            watcher.start();
            System.out.println("[Git] File system watcher started");
        }
    }

    /**
     * Gets the git status watcher if available.
     * 
     * @return the GitStatusWatcher, or null if not in a git repository
     */
    public GitStatusWatcher getWatcher() {
        return watcher;
    }

    /**
     * Gets the underlying JGit Repository, or null if not in a git repo.
     */
    public org.eclipse.jgit.lib.Repository getRepository() {
        return repository;
    }

    /**
     * Gets the root path of the project being managed.
     */
    public Path getRootPath() {
        return rootPath;
    }

    /**
     * Checks if the current path is inside a git repository.
     *
     * @return true if a git repository was detected, false otherwise
     */
    public boolean isGitRepository() {
        return isGitRepo;
    }

    /**
     * Gets the current branch name.
     *
     * @return Optional containing the branch name if in a git repo and not detached,
     *         or "HEAD" for detached state, or empty if not a git repo
     */
    public Optional<String> getCurrentBranch() {
        if (!isGitRepo || repository == null) {
            return Optional.empty();
        }

        try {
            String branch = repository.getBranch();
            if (branch != null) {
                return Optional.of(branch);
            }
        } catch (IOException e) {
            // Error reading branch info
        }

        return Optional.empty();
    }

    /**
     * Gets a display string for git info.
     * Returns the branch name if in a git repo, otherwise returns empty string.
     *
     * @return Display string for UI, or empty string if not in a git repo
     */
    public String getDisplayString() {
        if (!isGitRepo) {
            return "";
        }
        return getCurrentBranch().orElse("");
    }

    /**
     * Gets the status of files in the repository.
     * Returns a map where keys are file paths and values are status strings (M, A, D, R, etc.)
     *
     * @return Map of file paths to status strings, or empty map if not a git repo
     */
    public Map<Path, String> getStatus() {
        if (!isGitRepo || git == null) {
            return Collections.emptyMap();
        }

        try {
            var status = git.status().call();
            Map<Path, String> result = new HashMap<>();

            // Added files
            for (String path : status.getAdded()) {
                result.put(Paths.get(path), "A");
            }
            // Removed files
            for (String path : status.getRemoved()) {
                result.put(Paths.get(path), "D");
            }
            // Modified files
            for (String path : status.getModified()) {
                result.put(Paths.get(path), "M");
            }
            // Untracked files
            for (String path : status.getUntracked()) {
                result.put(Paths.get(path), "U");
            }
            // Staged changes
            for (String path : status.getChanged()) {
                result.put(Paths.get(path), "S");
            }

            return result;
        } catch (GitAPIException e) {
            System.err.println("[Git] Error getting status: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Gets staged files that are ready to commit.
     *
     * @return Map of staged file paths to status (A, M, D, etc.)
     */
    public Map<Path, String> getStagedChanges() {
        if (!isGitRepo || git == null) {
            return Collections.emptyMap();
        }

        try {
            var status = git.status().call();
            Map<Path, String> result = new HashMap<>();

            // Added (staged)
            for (String path : status.getAdded()) {
                result.put(Paths.get(path), "A");
            }
            // Removed (staged)
            for (String path : status.getRemoved()) {
                result.put(Paths.get(path), "D");
            }
            // Modified (staged)
            for (String path : status.getChanged()) {
                result.put(Paths.get(path), "M");
            }

            return result;
        } catch (GitAPIException e) {
            System.err.println("[Git] Error getting staged changes: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Gets unstaged changes.
     *
     * @return Map of unstaged file paths to status
     */
    public Map<Path, String> getUnstagedChanges() {
        if (!isGitRepo || git == null) {
            return Collections.emptyMap();
        }

        try {
            var status = git.status().call();
            Map<Path, String> result = new HashMap<>();

            // Modified (not staged) - files changed in working tree but not added to index
            for (String path : status.getModified()) {
                result.put(Paths.get(path), "M");
            }
            // Missing (deleted) - files deleted in working tree but not staged
            for (String path : status.getMissing()) {
                result.put(Paths.get(path), "D");
            }
            // Untracked - new files not yet added
            for (String path : status.getUntracked()) {
                result.put(Paths.get(path), "U");
            }
            // Conflicting - files with merge conflicts
            for (String path : status.getConflicting()) {
                result.put(Paths.get(path), "C");
            }

            return result;
        } catch (GitAPIException e) {
            System.err.println("[Git] Error getting unstaged changes: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Stages a file for commit.
     *
     * @param filePath relative path of the file to stage
     * @return true if successful, false otherwise
     */
    public boolean stageFile(Path filePath) {
        if (!isGitRepo || git == null) {
            return false;
        }

        try {
            String relativePath = filePath.isAbsolute() 
                ? rootPath.relativize(filePath).toString() 
                : filePath.toString();
            git.add().addFilepattern(relativePath).call();
            return true;
        } catch (GitAPIException e) {
            System.err.println("[Git] Error staging file: " + e.getMessage());
            return false;
        }
    }

    /**
     * Stages all changed files.
     *
     * @return true if successful, false otherwise
     */
    public boolean stageAll() {
        if (!isGitRepo || git == null) {
            return false;
        }

        try {
            git.add().addFilepattern(".").call();
            return true;
        } catch (GitAPIException e) {
            System.err.println("[Git] Error staging all: " + e.getMessage());
            return false;
        }
    }

    /**
     * Unstages a file.
     *
     * @param filePath relative path of the file to unstage
     * @return true if successful, false otherwise
     */
    public boolean unstageFile(Path filePath) {
        if (!isGitRepo || git == null) {
            return false;
        }

        try {
            String relativePath = filePath.isAbsolute() 
                ? rootPath.relativize(filePath).toString() 
                : filePath.toString();
            git.reset().addPath(relativePath).call();
            return true;
        } catch (GitAPIException e) {
            System.err.println("[Git] Error unstaging file: " + e.getMessage());
            return false;
        }
    }

    /**
     * Unstages all staged files.
     *
     * @return true if successful, false otherwise
     */
    public boolean unstageAll() {
        if (!isGitRepo || git == null) {
            return false;
        }

        try {
            git.reset().call();
            return true;
        } catch (GitAPIException e) {
            System.err.println("[Git] Error unstaging all: " + e.getMessage());
            return false;
        }
    }

    /**
     * Commits staged changes.
     *
     * @param message commit message
     * @return true if successful, false otherwise
     */
    public boolean commit(String message) {
        if (!isGitRepo || git == null || message == null || message.trim().isEmpty()) {
            return false;
        }

        try {
            PersonIdent author = new PersonIdent(repository);
            git.commit().setAuthor(author).setCommitter(author).setMessage(message).call();
            return true;
        } catch (GitAPIException e) {
            System.err.println("[Git] Error committing: " + e.getMessage());
            return false;
        }
    }

    /**
     * Amends the last commit with new message and/or changes.
     *
     * @param message new commit message
     * @return true if successful, false otherwise
     */
    public boolean amendCommit(String message) {
        if (!isGitRepo || git == null || message == null || message.trim().isEmpty()) {
            return false;
        }

        try {
            PersonIdent author = new PersonIdent(repository);
            git.commit().setAmend(true).setAuthor(author).setCommitter(author).setMessage(message).call();
            return true;
        } catch (GitAPIException e) {
            System.err.println("[Git] Error amending commit: " + e.getMessage());
            return false;
        }
    }


    /**
     * Checks if there are any staged changes ready to commit.
     *
     * @return true if there are staged changes, false otherwise
     */
    public boolean hasStagedChanges() {
        return !getStagedChanges().isEmpty();
    }

    /**
     * Checks if there are any unstaged changes in the working directory.
     *
     * @return true if there are unstaged changes, false otherwise
     */
    public boolean hasUnstagedChanges() {
        return !getUnstagedChanges().isEmpty();
    }

    /**
     * Gets a list of all local branch names.
     *
     * @return List of branch names, or empty list if not a git repo
     */
    public List<String> getBranches() {
        if (!isGitRepo || git == null) {
            return Collections.emptyList();
        }

        try {
            List<Ref> branches = git.branchList().call();
            List<String> branchNames = new ArrayList<>();
            for (Ref branch : branches) {
                String name = branch.getName();
                // Strip refs/heads/ prefix
                if (name.startsWith("refs/heads/")) {
                    name = name.substring("refs/heads/".length());
                }
                branchNames.add(name);
            }
            return branchNames;
        } catch (GitAPIException e) {
            System.err.println("[Git] Error getting branches: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Creates a new branch.
     *
     * @param branchName name of the branch to create
     * @return true if successful, false otherwise
     */
    public boolean createBranch(String branchName) {
        if (!isGitRepo || git == null || branchName == null || branchName.trim().isEmpty()) {
            return false;
        }

        try {
            git.branchCreate().setName(branchName).call();
            return true;
        } catch (GitAPIException e) {
            System.err.println("[Git] Error creating branch: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks out an existing branch.
     *
     * @param branchName name of the branch to checkout
     * @return true if successful, false otherwise
     */
    public boolean checkoutBranch(String branchName) {
        if (!isGitRepo || git == null || branchName == null || branchName.trim().isEmpty()) {
            return false;
        }

        try {
            git.checkout().setName(branchName).call();
            return true;
        } catch (GitAPIException e) {
            System.err.println("[Git] Error checking out branch: " + e.getMessage());
            return false;
        }
    }

    /**
     * Creates a new branch and checks it out.
     *
     * @param branchName name of the branch to create and checkout
     * @return true if successful, false otherwise
     */
    public boolean createAndCheckoutBranch(String branchName) {
        if (!isGitRepo || git == null || branchName == null || branchName.trim().isEmpty()) {
            return false;
        }

        try {
            git.checkout().setCreateBranch(true).setName(branchName).call();
            return true;
        } catch (GitAPIException e) {
            System.err.println("[Git] Error creating and checking out branch: " + e.getMessage());
            return false;
        }
    }

    /**
     * Creates a new branch from a specified base branch and checks it out.
     *
     * @param branchName name of the branch to create
     * @param baseBranch name of the branch to create from
     * @return true if successful, false otherwise
     */
    public boolean createBranchFrom(String branchName, String baseBranch) {
        if (!isGitRepo || git == null || branchName == null || branchName.trim().isEmpty() 
                || baseBranch == null || baseBranch.trim().isEmpty()) {
            return false;
        }

        try {
            git.checkout().setCreateBranch(true).setName(branchName).setStartPoint(baseBranch).call();
            return true;
        } catch (GitAPIException e) {
            System.err.println("[Git] Error creating branch from " + baseBranch + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Deletes a branch.
     *
     * @param branchName name of the branch to delete
     * @return true if successful, false otherwise
     */
    public boolean deleteBranch(String branchName) {
        if (!isGitRepo || git == null || branchName == null || branchName.trim().isEmpty()) {
            return false;
        }

        // Prevent deleting the current branch
        Optional<String> currentBranch = getCurrentBranch();
        if (currentBranch.isPresent() && currentBranch.get().equals(branchName)) {
            System.err.println("[Git] Cannot delete the current branch: " + branchName);
            return false;
        }

        try {
            git.branchDelete().setBranchNames(branchName).setForce(false).call();
            return true;
        } catch (GitAPIException e) {
            System.err.println("[Git] Error deleting branch: " + e.getMessage());
            return false;
        }
    }

    /**
     * Closes the repository and releases resources.
     */
    public void close() {
        if (watcher != null) {
            watcher.stop();
        }
        if (git != null) {
            git.close();
        }
        if (repository != null) {
            repository.close();
        }
    }
}
