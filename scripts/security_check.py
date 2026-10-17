#!/usr/bin/env python3
"""Offline publication guard. Reports locations/rules only, never matched secrets.

This is a targeted guard, not a substitute for GitHub secret scanning or review.
Workspace mode checks tracked + untracked, non-ignored files; staged reads the index.
History scans all reachable blobs, including deleted files and binary credentials.
"""
import argparse
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
RULES = {
    "private-key": rb"-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----",
    "github-token": rb"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})\b",
    "aws-access-key": rb"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b",
    "credential-literal": rb'''(?i)(?:["']?(?:usos[._])?(?:consumer[._])?(?:secret|password|api[._]?key|access[._]?token|token[._]?secret)["']?\s*[:=]\s*["'])([A-Za-z0-9_+/=.!@#$%&*-]{16,})["']''',
    "usos-property": rb"(?im)^[ \t]*(?:usos\.(?:consumer\.)?(?:key|secret)|CONSUMER_(?:KEY|SECRET))[ \t]*[:=][ \t]*[^\s#][^\r\n]{7,}$",
}
PATTERNS = {name: re.compile(pattern) for name, pattern in RULES.items()}


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, stderr=subprocess.PIPE)


def forbidden(path):
    p = PurePosixPath(path.lower())
    name = p.name
    if name == "local.properties.example" or name == ".env.example":
        return False
    return (name == "local.properties" or name == ".env" or name.startswith(".env.")
            or name.endswith(("secrets.properties", "signing.properties", "key.properties"))
            or p.suffix in {".jks", ".keystore", ".pem", ".key", ".p12", ".pfx", ".apk", ".aab", ".apks", ".class", ".zip", ".hprof"}
            or any(part in {".idea", ".gradle", "build", "bin", "private", "captures", "security-reports"} for part in p.parts))


def local_secrets():
    # Compare local credentials without printing or persisting them anywhere.
    path = ROOT / "local.properties"
    if not path.exists():
        return []
    values = []
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.match(r"\s*([^#!:=\s]+)\s*[:=]\s*(.*?)\s*$", line)
        if match and re.search(r"(?i)secret|key|token|password", match[1]) and len(match[2]) >= 8:
            values.append(match[2].encode())
    return values


def inspect(data, secrets):
    found = [name for name, pattern in PATTERNS.items() if pattern.search(data)]
    if any(value in data for value in secrets):
        found.append("local-credential-match")
    return found


def check(staged=False, history=False):
    problems = set()
    secrets = local_secrets()
    if history:
        if git("rev-parse", "--is-shallow-repository").strip() == b"true":
            raise ValueError("History scan requires a full clone (git fetch --unshallow).")
        # Object IDs only: paths and commit messages can themselves contain secrets.
        objects = git("rev-list", "--objects", "--all", "--no-object-names").splitlines()
        batch = subprocess.Popen(["git", "cat-file", "--batch"], cwd=ROOT,
                                 stdin=subprocess.PIPE, stdout=subprocess.PIPE)
        try:
            for oid in objects:
                batch.stdin.write(oid + b"\n")
                batch.stdin.flush()
                header = batch.stdout.readline().split()
                if len(header) != 3:
                    raise ValueError("Cannot read history object.")
                data = batch.stdout.read(int(header[2]))
                batch.stdout.read(1)
                # Scan metadata too, but ignore historical artifact names: old binaries
                # must still be checked for embedded secrets, not silently allowlisted.
                for rule in inspect(data, secrets):
                    problems.add(("object " + oid.decode()[:12], rule))
        finally:
            batch.stdin.close()
            batch.stdout.close()
            batch.wait()
    else:
        args = ("diff", "--cached", "--name-only", "--diff-filter=ACMR", "-z") if staged else (
            "ls-files", "--cached", "--others", "--exclude-standard", "-z")
        for raw in set(git(*args).split(b"\0")) - {b""}:
            path = raw.decode("utf-8")
            file = ROOT / path
            if not staged and not file.exists():
                continue
            if forbidden(path):
                problems.add((path, "private-or-generated-file"))
            # Reject symlinks so the workspace scan cannot follow files outside the repo.
            if file.is_symlink():
                problems.add((path, "symlink"))
                continue
            data = git("show", ":" + path) if staged else file.read_bytes()
            for rule in inspect(data, secrets):
                problems.add((path, rule))
    for location, rule in sorted(problems):
        # Escape control characters to prevent terminal/CI annotation injection.
        print(f"BLOCKED {ascii(location)}: {rule}")
    print(f"Security check: {'FAILED' if problems else 'OK'} ({len(problems)} findings).")
    return not problems


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--staged", action="store_true")
    mode.add_argument("--history", action="store_true")
    args = parser.parse_args()
    try:
        return 0 if check(args.staged, args.history) else 1
    except (OSError, ValueError, subprocess.SubprocessError):
        print("Security check could not finish; publication is blocked. Check Git/Python and clone depth.", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
