import os
import subprocess
import sys

# --- 配置 ---
# 本地仓库路径 (请确保路径正确)
repo_path = r"D:\linqianxia\ChatApp"
# GitHub 仓库 URL (请替换成你的仓库地址)
remote_url = "https://github.com/Arain119/ChatApp.git"
# 提交信息
commit_message = "Initial upload via Python script"
# 默认分支名 (GitHub 现在通常是 main)
branch_name = "main"
# --- 配置结束 ---

def run_command(command, working_dir, ignore_errors=False):
    """执行 shell 命令并返回成功状态"""
    print(f"\n> Running: {' '.join(command)}")
    try:
        # 使用 shell=True 在 Windows 上更方便执行 git 命令
        result = subprocess.run(command, cwd=working_dir, check=False, shell=True, capture_output=True, text=True, encoding='utf-8')
        if result.stdout:
            print(result.stdout.strip())
        if result.stderr:
            # Git 经常在 stderr 输出信息性内容，即使操作成功
            # 但也可能包含真正的错误，比如 pull/rebase 冲突
            print(result.stderr.strip(), file=sys.stderr)

        if result.returncode != 0 and not ignore_errors:
            # 特殊处理 'git commit' 返回 1 的情况 (通常表示没有东西可提交)
            if "commit" in command and "nothing to commit" in result.stdout:
                 print("   -> Nothing to commit, working tree clean.")
                 return True # 认为这种情况是“成功”的，可以继续

            # 特殊处理 rebase/pull 冲突
            if ("pull" in command or "rebase" in command) and "conflict" in (result.stdout + result.stderr).lower():
                 print("\nERROR: Merge conflict detected during 'git pull --rebase'.", file=sys.stderr)
                 print("   Please resolve the conflicts manually in the repository folder:", file=sys.stderr)
                 print(f"   '{repo_path}'", file=sys.stderr)
                 print("   Then run 'git rebase --continue' after resolving.", file=sys.stderr)
                 print("   Or run 'git rebase --abort' to cancel.", file=sys.stderr)
                 return False

            print(f"ERROR: Command failed with exit code {result.returncode}", file=sys.stderr)
            return False
        return True
    except FileNotFoundError:
        print(f"ERROR: Command not found (is Git installed and in PATH?): {command[0]}", file=sys.stderr)
        return False
    except Exception as e:
        print(f"ERROR: An unexpected error occurred: {e}", file=sys.stderr)
        return False

def main():
    print("==================================================")
    print(" GitHub Folder Upload Script (Python Version)")
    print("==================================================")
    print(f"Local Path: {repo_path}")
    print(f"Remote URL: {remote_url}")

    # 检查 Git 是否安装
    if not run_command(["git", "--version"], "."):
         sys.exit(1)

    # 检查本地路径是否存在
    if not os.path.isdir(repo_path):
        print(f"\nERROR: Local path does not exist or is not a directory: {repo_path}", file=sys.stderr)
        sys.exit(1)

    # 切换到仓库目录
    try:
        os.chdir(repo_path)
        print(f"\nChanged directory to: {os.getcwd()}")
    except Exception as e:
        print(f"\nERROR: Cannot change directory to {repo_path}: {e}", file=sys.stderr)
        sys.exit(1)

    # 检查是否是 Git 仓库，如果不是则初始化
    is_new_repo = not os.path.isdir(".git")
    if is_new_repo:
        print("\nInitializing new Git repository...")
        if not run_command(["git", "init"], repo_path):
            sys.exit(1)
        run_command(["git", "branch", "-m", branch_name], repo_path, ignore_errors=True) # 忽略可能的错误
        print(f"Default branch set/checked as {branch_name}.")

    # 检查远程仓库 'origin' 是否已添加
    needs_remote_add = True
    try:
        # 检查 remote url 是否匹配，即使 origin 存在
        result = subprocess.run(["git", "remote", "get-url", "origin"], cwd=repo_path, check=False, capture_output=True, text=True, encoding='utf-8')
        if result.returncode == 0 and result.stdout.strip() == remote_url:
            print("\nRemote repository 'origin' already exists and matches URL.")
            needs_remote_add = False
        elif result.returncode == 0:
             print(f"\nWarning: Remote 'origin' exists but points to '{result.stdout.strip()}'. Will update it.", file=sys.stderr)
             run_command(["git", "remote", "remove", "origin"], repo_path, ignore_errors=True)
        # else: origin 不存在或 get-url 失败，需要添加
    except FileNotFoundError:
         print("\nERROR: Git command not found during remote check.", file=sys.stderr)
         sys.exit(1)

    if needs_remote_add:
        print(f"\nAdding/Updating remote repository 'origin': {remote_url} ...")
        if not run_command(["git", "remote", "add", "origin", remote_url], repo_path):
            # 如果添加失败，可能是因为它以不同的 URL 存在，尝试 set-url
            if not run_command(["git", "remote", "set-url", "origin", remote_url], repo_path):
                 print("ERROR: Failed to add or update remote repository.", file=sys.stderr)
                 sys.exit(1)

    # 如果不是新仓库，先尝试拉取更新
    if not is_new_repo:
        print(f"\nAttempting to pull updates from remote '{branch_name}' branch before pushing...")
        # 使用 --rebase 合并远程更改，--autostash 自动处理本地未提交更改
        if not run_command(["git", "pull", "origin", branch_name, "--rebase", "--autostash"], repo_path):
             print("\nERROR: Failed to pull and rebase changes from remote.", file=sys.stderr)
             print("   This might be due to merge conflicts that need manual resolution.", file=sys.stderr)
             print("   Alternatively, if you are CERTAIN you want to overwrite the remote history", file=sys.stderr)
             print(f"   with your local changes, you can try running 'git push --force origin {branch_name}' manually.", file=sys.stderr)
             sys.exit(1)

    # 添加所有文件到暂存区
    print("\nStaging all files...")
    if not run_command(["git", "add", "."], repo_path):
        sys.exit(1)

    # 提交更改
    print("\nCommitting changes...")
    if not run_command(["git", "commit", "-m", commit_message], repo_path):
        # run_command 内部已经处理了 "nothing to commit" 的情况
        # 如果这里返回 False，说明是真正的 commit 错误或之前的 pull 失败了
        print("Exiting due to commit error or previous pull failure.", file=sys.stderr)
        sys.exit(1)

    # 推送到 GitHub
    print(f"\nPushing to GitHub (branch: {branch_name})...")
    print("You might be prompted for your GitHub username and password/token here...")
    if not run_command(["git", "push", "-u", "origin", branch_name], repo_path):
        print("\nERROR: Push failed. Please check the output above for details.", file=sys.stderr)
        # 之前的 pull 应该已经解决了 non-fast-forward 问题，如果还失败，可能是其他原因
        print("   Common checks:")
        print(f"   1. Does the remote repository {remote_url} exist?")
        print("   2. Do you have permission to push?")
        print("   3. Is your network connection stable?")
        print("   4. Was your authentication successful?")
        sys.exit(1)

    print("\n==================================================")
    print("  Success! Folder contents pushed to GitHub.")
    print("==================================================")

if __name__ == "__main__":
    main()

