#!/usr/bin/env python3
# Verifies gradle/libs.versions.toml is valid TOML and defines every
# libs.* / libs.plugins.* / libs.bundles.* accessor used in the build files.
import re, sys, tomllib, pathlib
root = pathlib.Path(__file__).resolve().parents[1]
cat = root / "gradle" / "libs.versions.toml"
if not cat.exists():
    print("FAIL: gradle/libs.versions.toml missing"); sys.exit(1)
try:
    data = tomllib.loads(cat.read_text())
except Exception as e:
    print(f"FAIL: TOML parse error: {e}"); sys.exit(1)
libs = set(data.get("libraries", {}).keys())
plugins = set(data.get("plugins", {}).keys())
bundles = set(data.get("bundles", {}).keys())
def norm(a):  # accessor segment -> catalog-key form
    return a.replace(".", "-").replace("_", "-")
build_files = [root/"app"/"build.gradle.kts", root/"build.gradle.kts", root/"settings.gradle.kts"]
missing = []
acc = re.compile(r"\blibs\.((?:plugins|bundles)\.)?([A-Za-z0-9_.]+)")
for bf in build_files:
    if not bf.exists():
        continue
    for ln in bf.read_text().splitlines():
        code = ln.split("//", 1)[0]          # ignore line comments
        for kind, name in acc.findall(code):
            key = norm(name)
            table = plugins if kind=="plugins." else bundles if kind=="bundles." else libs
            label = "plugin" if kind=="plugins." else "bundle" if kind=="bundles." else "library"
            if key not in table:
                missing.append(f"{bf.relative_to(root)}: libs.{kind or ''}{name} -> {label} key '{key}' NOT in catalog")
if missing:
    print("FAIL: unresolved catalog accessors:")
    print("\n".join(sorted(set(missing)))); sys.exit(1)
print(f"PASS: catalog valid; {len(libs)} libraries, {len(plugins)} plugins, {len(bundles)} bundles; all accessors resolve.")
sys.exit(0)
