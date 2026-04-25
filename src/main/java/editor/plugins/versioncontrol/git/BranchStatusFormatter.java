package editor.plugins.versioncontrol.git;

/**
 * Formats git branch status information into a display string.
 * This class is stateless and unit-testable.
 */
public class BranchStatusFormatter {
    
    /**
     * Formats branch status information into a display string.
     * 
     * Format examples:
     * - "main" - clean, in sync
     * - "main+" - has staged changes
     * - "main*" - has unstaged changes
     * - "main+*" - has both staged and unstaged
     * - "main ↑2" - 2 commits ahead (ready to push)
     * - "main ↓3" - 3 commits behind (ready to pull)
     * - "main ↑2↓3" - 2 ahead, 3 behind
     * - "main+* ↑1↓2" - everything combined
     * 
     * @param branchName the name of the current branch
     * @param hasStagedChanges true if there are staged changes
     * @param hasUnstagedChanges true if there are unstaged changes
     * @param hasRemote true if the branch has a remote tracking branch
     * @param ahead number of commits ahead of remote
     * @param behind number of commits behind remote
     * @return formatted display string
     */
    public String format(String branchName, boolean hasStagedChanges, boolean hasUnstagedChanges,
                        boolean hasRemote, int ahead, int behind) {
        if (branchName == null || branchName.isEmpty()) {
            return "";
        }
        
        StringBuilder result = new StringBuilder(branchName);
        
        // Add local change indicators
        if (hasStagedChanges) {
            result.append("+");
        }
        if (hasUnstagedChanges) {
            result.append("*");
        }
        
        // Add remote tracking indicators if there's a remote
        if (hasRemote && (ahead > 0 || behind > 0)) {
            result.append(" ");
            if (ahead > 0) {
                result.append("↑").append(ahead);
            }
            if (behind > 0) {
                result.append("↓").append(behind);
            }
        }
        
        return result.toString();
    }
    
    /**
     * Formats a detailed tooltip text for the branch status.
     * 
     * @param branchName the name of the current branch
     * @param hasStagedChanges true if there are staged changes
     * @param hasUnstagedChanges true if there are unstaged changes
     * @param hasRemote true if the branch has a remote tracking branch
     * @param ahead number of commits ahead of remote
     * @param behind number of commits behind remote
     * @return formatted tooltip text
     */
    public String formatTooltip(String branchName, boolean hasStagedChanges, boolean hasUnstagedChanges,
                                boolean hasRemote, int ahead, int behind) {
        if (branchName == null || branchName.isEmpty()) {
            return "No branch";
        }
        
        StringBuilder tooltip = new StringBuilder();
        tooltip.append("Branch: ").append(branchName);
        
        // Add local change information
        if (hasStagedChanges || hasUnstagedChanges) {
            tooltip.append("\n");
            if (hasStagedChanges) {
                tooltip.append("Staged changes (+)\n");
            }
            if (hasUnstagedChanges) {
                tooltip.append("Unstaged changes (*)\n");
            }
        }
        
        // Add remote tracking information
        if (hasRemote) {
            if (ahead > 0 || behind > 0) {
                tooltip.append("\n");
                if (ahead > 0) {
                    tooltip.append("↑ ").append(ahead).append(" commit(s) to push\n");
                }
                if (behind > 0) {
                    tooltip.append("↓ ").append(behind).append(" commit(s) to pull\n");
                }
            } else {
                tooltip.append("\nUp to date with remote");
            }
        } else {
            tooltip.append("\nNo remote tracking branch");
        }
        
        return tooltip.toString();
    }
}
