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

def run_command(command, working_dir):
    """执行 shell 命令并返回成功状态"""
    print(f"\n> Running: {' '.join(command)}")
    try:
        # 使用 shell=True 在 Windows 上更方便执行 git 命令，但在复杂或包含用户输入的场景下需谨慎
        # 对于固定的 git 命令，这里是相对安全的
        result = subprocess.run(command, cwd=working_dir, check=False, shell=True, capture_output=True, text=True, encoding='utf-8')
        if result.stdout:
            print(result.stdout.strip())
        if result.stderr:
            # Git 经常在 stderr 输出信息性内容，即使操作成功
            print(result.stderr.strip(), file=sys.stderr)

        if result.returncode != 0:
            # 特殊处理 'git commit' 返回 1 的情况 (通常表示没有东西可提交)
            if "commit" in command and "nothing to commit" in result.stdout:
                 print("   -> Nothing to commit, working tree clean.")
                 return True # 认为这种情况是“成功”的，可以继续 push
            else:
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
         sys.exit(1) # 如果 git --version 失败，则退出

    # 检查本地路径是否存在
    if not os.path.isdir(repo_path):
        print(f"\nERROR: Local path does not exist or is not a directory: {repo_path}", file=sys.stderr)
        sys.exit(1)

    # 切换到仓库目录 (虽然 run_command 使用 cwd, 这里显式切换可以确保 .git 检查在正确位置)
    try:
        os.chdir(repo_path)
        print(f"\nChanged directory to: {os.getcwd()}")
    except Exception as e:
        print(f"\nERROR: Cannot change directory to {repo_path}: {e}", file=sys.stderr)
        sys.exit(1)

    # 检查是否是 Git 仓库，如果不是则初始化
    if not os.path.isdir(".git"):
        print("\nInitializing new Git repository...")
        if not run_command(["git", "init"], repo_path):
            sys.exit(1)
        # 尝试设置默认分支名
        # 使用 2>nul 或类似重定向在 subprocess 中较复杂，这里忽略可能的错误
        run_command(["git", "branch", "-m", branch_name], repo_path)
        print(f"Default branch set/checked as {branch_name}.")


    # 检查远程仓库 'origin' 是否已添加
    # 使用 subprocess 获取 'git remote -v' 的输出并检查
    needs_remote_add = True
    try:
        result = subprocess.run(["git", "remote", "-v"], cwd=repo_path, check=True, capture_output=True, text=True, encoding='utf-8')
        if remote_url in result.stdout:
            print("\nRemote repository 'origin' already exists.")
            needs_remote_add = False
    except subprocess.CalledProcessError:
        # 如果 'git remote -v' 失败 (例如还没有 remote)，则需要添加
        pass
    except FileNotFoundError:
         print("\nERROR: Git command not found during remote check.", file=sys.stderr)
         sys.exit(1)

    if needs_remote_add:
        print(f"\nAdding remote repository 'origin': {remote_url} ...")
        # 尝试移除可能存在的旧 'origin'
        run_command(["git", "remote", "remove", "origin"], repo_path) # 忽略错误
        if not run_command(["git", "remote", "add", "origin", remote_url], repo_path):
            sys.exit(1)

    # 添加所有文件到暂存区
    print("\nStaging all files...")
    if not run_command(["git", "add", "."], repo_path):
        sys.exit(1)

    # 提交更改
    print("\nCommitting changes...")
    # 注意：将包含空格的提交信息作为一个单独的参数传递
    if not run_command(["git", "commit", "-m", commit_message], repo_path):
        # run_command 内部已经处理了 "nothing to commit" 的情况
        # 如果这里返回 False，说明是真正的 commit 错误
        print("Exiting due to commit error.", file=sys.stderr)
        sys.exit(1)


    # 推送到 GitHub
    print(f"\nPushing to GitHub (branch: {branch_name})...")
    print("You might be prompted for your GitHub username and password/token here...")
    if not run_command(["git", "push", "-u", "origin", branch_name], repo_path):
        print("\nERROR: Push failed. Please check the output above for details.", file=sys.stderr)
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
