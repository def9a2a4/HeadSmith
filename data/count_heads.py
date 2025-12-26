#!/usr/bin/env python3
# /// script
# dependencies = [
#   "pyyaml",
# ]
# ///
"""Count heads per YAML file and save to docs."""

import json
import yaml
from pathlib import Path


def count_heads(repo_root: Path) -> dict:
    """Count heads from all YAML files listed in config.

    Args:
        repo_root: Path to the repository root

    Returns:
        Dict with counts per file and total
    """
    resources = repo_root / "headsmith/src/main/resources"
    config_path = resources / "config.yml"

    with open(config_path) as f:
        config = yaml.safe_load(f)

    counts = {}
    total = 0
    for head_file in config.get("head-files", []):
        path = resources / head_file
        if path.exists():
            with open(path) as f:
                data = yaml.safe_load(f)
                heads = data.get("heads", {})
                count = len(heads)
                counts[head_file] = count
                total += count

    counts["total"] = total
    return counts


def main():
    """CLI entry point."""
    repo_root = Path.cwd()
    counts = count_heads(repo_root)

    for file, count in counts.items():
        if file != "total":
            print(f"{file}: {count} heads")
    print(f"\nTotal: {counts['total']} heads")

    output_path = repo_root / "docs/util/head-count.json"
    output_path.write_text(json.dumps(counts, indent=2))
    print(f"Written to {output_path}")


if __name__ == "__main__":
    main()
