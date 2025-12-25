#!/usr/bin/env python3
# /// script
# dependencies = [
#   "pyyaml",
# ]
# ///
"""Count heads per YAML file and save"""

import json
import yaml
from pathlib import Path

def main():
    resources = Path(__file__).parent.parent / "src/main/resources"
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
                print(f"{head_file}: {count} heads")

    counts["total"] = total
    output_path = Path(__file__).parent.parent.parent / "docs/util/head-count.json"
    output_path.write_text(json.dumps(counts, indent=2))
    print(f"\nTotal: {total} heads -> {output_path}")

if __name__ == "__main__":
    main()
