#!/usr/bin/env sh
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAM_HOME="${JAM_HOME:-$HOME/.jam4j}"
BIN_DIR="$JAM_HOME/bin"

echo "==> Building jam4j fat JAR..."
mvn -f "$SCRIPT_DIR/pom.xml" clean package -q

# Locate the fat JAR without hardcoding the version
JAR_FILES=$(ls "$SCRIPT_DIR/target/jam4j-"*.jar 2>/dev/null | grep -v '\-sources\.jar' | grep -v '\-javadoc\.jar' || true)
JAR_COUNT=$(echo "$JAR_FILES" | grep -c '.jar' || true)

if [ "$JAR_COUNT" -eq 0 ]; then
  echo "ERROR: No jam4j JAR found in $SCRIPT_DIR/target/" >&2; exit 1
fi
if [ "$JAR_COUNT" -gt 1 ]; then
  echo "ERROR: Multiple JARs found — clean the target/ dir and retry:" >&2
  echo "$JAR_FILES" >&2; exit 1
fi

JAR_FILE="$JAR_FILES"
echo "==> Installing $(basename "$JAR_FILE") to $BIN_DIR ..."
mkdir -p "$BIN_DIR"
cp "$JAR_FILE" "$BIN_DIR/jam4j.jar"

cat > "$BIN_DIR/jam" << 'WRAPPER'
#!/usr/bin/env sh
JAM_HOME="${JAM_HOME:-$HOME/.jam4j}"
JAM_JAR="$JAM_HOME/bin/jam4j.jar"
if [ ! -f "$JAM_JAR" ]; then
  echo "jam: jam4j.jar not found at $JAM_JAR" >&2; exit 1
fi
exec java -jar "$JAM_JAR" "$@"
WRAPPER
chmod +x "$BIN_DIR/jam"

echo ""
echo "==> jam installed. Add to your shell profile:"
echo "      export PATH=\"\$HOME/.jam4j/bin:\$PATH\""
echo ""
echo "    Then run: jam --help"
