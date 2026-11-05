#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
test_output=$(mktemp -d)
trap 'rm -rf "$test_output"' EXIT
java com.sun.tools.javac.Main -d "$test_output" src/main/java/us/z1x/lucerna/gui/element/{ListInteraction,MenuLayout}.java src/regression/java/us/z1x/lucerna/gui/element/ListInteractionRegression.java src/main/java/us/z1x/lucerna/api/farterrain/{FarTerrainFrame,FarTerrainMesh}.java src/regression/java/us/z1x/lucerna/api/farterrain/FarTerrainContractRegression.java
java -cp "$test_output" us.z1x.lucerna.gui.element.ListInteractionRegression
java -cp "$test_output" us.z1x.lucerna.api.farterrain.FarTerrainContractRegression
