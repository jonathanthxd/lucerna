#!/usr/bin/env python3
"""Static, dependency and JAR smoke checks for the TFL Ornithe integration branch.
This deliberately does not certify actual GPU/shaderpack compatibility.
"""
import argparse
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
def prop(name: str) -> str:
    properties = (ROOT / "gradle.properties").read_text()
    match = re.search(r"^" + re.escape(name) + r"=(.+)$", properties, re.M)
    assert match, f"Missing gradle.properties value: {name}"
    return match.group(1).strip()

def check_source() -> None:
    meta = json.loads((ROOT / "src/main/resources/fabric.mod.json").read_text())
    mixins = json.loads((ROOT / "src/main/resources/lucerna.mixins.json").read_text())
    assert meta["id"] == "lucerna"
    assert meta["environment"] == "client"
    assert meta["depends"]["minecraft"] == "1.8.9"
    assert meta["depends"]["argentum"].startswith(">=")
    assert prop("minecraft_version") == "1.8.9"
    assert prop("fabric_version") == "0.19.5"
    assert prop("feather_build") == "2"
    assert prop("argentum_version").startswith("1.0.2")
    assert "JavaLanguageVersion.of(25)" in (ROOT / "build.gradle.kts").read_text()
    assert meta["mixins"] == ["lucerna.mixins.json"]
    assert mixins["required"] is True
    assert "argentum.MixinArgentumWorldRenderer" in mixins["client"]
    assert "celeritas.MixinShaderChunkRenderer" in mixins["client"]
    assert (ROOT / "src/main/java/us/z1x/lucerna/compat/argentum/ArgentumCompat.java").exists()
    print("PASS: Lucerna/Ornithe Gen2 metadata, toolchain and Argentum hooks")
    print("RUNTIME NOT CERTIFIED: Legacy LWJGL3, Argentum, shaders, TFL Core mixins")

def check_jar() -> None:
    libs = ROOT / "build/libs"
    jars = sorted(jar for jar in libs.glob("*.jar") if not any(s in jar.stem for s in ("-sources", "-dev", "-javadoc")))
    assert jars, "No installable candidate JAR produced by Gradle"
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            entries = set(archive.namelist())
            assert "fabric.mod.json" in entries, f"No Fabric metadata in {jar}"
            assert "lucerna.mixins.json" in entries, f"No mixin configuration in {jar}"
            assert "us/z1x/lucerna/Iris.class" in entries, f"Lucerna classes missing in {jar}"
            data = json.loads(archive.read("fabric.mod.json"))
            assert data["id"] == "lucerna"
            assert data["version"] != "${version}", "Mod version was not expanded"
        print(f"PASS: JAR structure {jar.name} ({jar.stat().st_size:,} bytes)")
    print("JAR structure PASS does not demonstrate successful shader rendering")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", action="store_true")
    args = parser.parse_args()
    check_source()
    if args.jar:
        check_jar()
