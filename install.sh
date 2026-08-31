#!/usr/bin/env sh
# Install the latest stable jam4j release for the current user.
#
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/tfredrich/jam4j/main/install.sh | sh

set -eu

REPOSITORY="tfredrich/jam4j"
RELEASES_API="https://api.github.com/repos/$REPOSITORY/releases/latest"
JAM_HOME="${JAM_HOME:-$HOME/.jam4j}"
BIN_DIR="$JAM_HOME/bin"

error() {
    echo "install.sh: $*" >&2
    exit 1
}

command -v curl >/dev/null 2>&1 || error "curl is required"
command -v java >/dev/null 2>&1 || error "Java 21 or newer is required, but java was not found"

JAVA_VERSION=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')
case "$JAVA_VERSION" in
    ''|*[!0-9]*) error "could not determine the installed Java version" ;;
esac
[ "$JAVA_VERSION" -ge 21 ] || error "Java 21 or newer is required (found Java $JAVA_VERSION)"

echo "==> Looking up the latest stable jam4j release..."
RELEASE_JSON=$(curl -fsSL \
    -H 'Accept: application/vnd.github+json' \
    -H 'User-Agent: jam4j-installer' \
    "$RELEASES_API") || error "could not query GitHub for the latest release"

TAG=$(printf '%s\n' "$RELEASE_JSON" \
    | sed -n 's/^[[:space:]]*"tag_name":[[:space:]]*"\([^"]*\)".*/\1/p' \
    | sed -n '1p')
printf '%s\n' "$TAG" | grep -Eq '^v[0-9]+\.[0-9]+\.[0-9]+$' \
    || error "GitHub returned an invalid release tag"

VERSION=${TAG#v}
JAR_NAME="jam4j-$VERSION.jar"
DOWNLOAD_BASE="https://github.com/$REPOSITORY/releases/download/$TAG"

if command -v sha256sum >/dev/null 2>&1; then
    sha256() { sha256sum "$1" | sed -n 's/^[[:space:]]*\([[:xdigit:]]*\)[[:space:]].*/\1/p'; }
elif command -v shasum >/dev/null 2>&1; then
    sha256() { shasum -a 256 "$1" | sed -n 's/^[[:space:]]*\([[:xdigit:]]*\)[[:space:]].*/\1/p'; }
else
    error "sha256sum or shasum is required"
fi

mkdir -p "$BIN_DIR"
umask 077
JAR_TEMP=$(mktemp "$BIN_DIR/.jam4j-download.XXXXXX") || error "could not create a temporary download"
WRAPPER_TEMP=$(mktemp "$BIN_DIR/.jam4j-wrapper.XXXXXX") || {
    rm -f "$JAR_TEMP"
    error "could not create a temporary launcher"
}
cleanup() {
    rm -f "$JAR_TEMP" "$WRAPPER_TEMP"
}
trap cleanup EXIT HUP INT TERM

echo "==> Downloading jam4j $VERSION..."
curl -fsSL -o "$JAR_TEMP" "$DOWNLOAD_BASE/$JAR_NAME" \
    || error "could not download $JAR_NAME"
CHECKSUMS=$(curl -fsSL "$DOWNLOAD_BASE/$JAR_NAME.sha256") \
    || error "could not download $JAR_NAME.sha256"
EXPECTED=$(printf '%s\n' "$CHECKSUMS" \
    | sed -n '1s/^[[:space:]]*\([[:xdigit:]]\{64\}\)[[:space:]].*/\1/p')
[ -n "$EXPECTED" ] || error "the release contains an invalid SHA-256 checksum"
ACTUAL=$(sha256 "$JAR_TEMP")
[ "$ACTUAL" = "$EXPECTED" ] || error "SHA-256 checksum verification failed"

mv "$JAR_TEMP" "$BIN_DIR/jam4j.jar"
cat > "$WRAPPER_TEMP" <<'WRAPPER'
#!/usr/bin/env sh
JAM_HOME="${JAM_HOME:-$HOME/.jam4j}"
JAM_JAR="$JAM_HOME/bin/jam4j.jar"
if [ ! -f "$JAM_JAR" ]; then
    echo "jam: jam4j.jar not found at $JAM_JAR" >&2
    exit 1
fi
exec java -jar "$JAM_JAR" "$@"
WRAPPER
chmod 755 "$WRAPPER_TEMP"
mv "$WRAPPER_TEMP" "$BIN_DIR/jam"

echo ""
echo "==> jam4j $VERSION installed in $JAM_HOME"
echo "    Add it to your PATH with:"
echo "      export PATH=\"$BIN_DIR:\$PATH\""
echo "    Then run: jam --help"
