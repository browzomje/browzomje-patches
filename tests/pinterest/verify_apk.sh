#!/usr/bin/env bash
set -euo pipefail

firebase=()
microg=()
while [[ "${1:-}" == --firebase-registration || "${1:-}" == --microg ]]; do
    case "$1" in
        --firebase-registration) firebase=(--firebase-registration) ;;
        --microg) microg=(--microg) ;;
    esac
    shift
done
if [[ $# != 6 ]]; then
    echo 'Usage: bash tests/pinterest/verify_apk.sh [--firebase-registration] [--microg] VERSION ORIGINAL.apk PATCHED.apk PATCH.log MORPHE_CLI.jar APKSIGNER' >&2
    exit 2
fi

version=$1
original=$2
patched=$3
log=$4
cli=$5
apksigner=$6
checks=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)

case "$version" in
    14.39.0|14.43.0|14.44.0) ;;
    *) printf 'No complete DEX check inventory for Pinterest %s\n' "$version" >&2; exit 2 ;;
esac

for file in "$original" "$patched" "$log" "$cli"; do
    [[ -f "$file" ]] || { printf 'Required input missing: %s\n' "$file" >&2; exit 2; }
done

python3 "$checks/check_patch_log.py" --version "$version" "${firebase[@]}" "${microg[@]}" "$log"
"$apksigner" verify "$patched"
printf 'PASS APK signature\n'

for check in CheckHomeRefreshDex CheckSaveTooltipDex CheckStartupTabDex CheckVideoFeedDex; do
    java -cp "$cli" "$checks/$check.java" "$original" "$patched" "$version"
done
if [[ ${#firebase[@]} != 0 ]]; then
    java -cp "$cli" "$checks/CheckFirebaseRegistrationDex.java" "$original" "$patched" "$version"
fi

if [[ ${#microg[@]} != 0 ]]; then
    java -cp "$cli" "$checks/CheckMicroGRouteDex.java" "$original" "$patched"
fi

printf 'PASS Pinterest %s static checks. Android behavior requires a separate device run.\n' "$version"
printf 'Original, patched APK, and patch log SHA-256 in that order\n'
for file in "$original" "$patched" "$log"; do
    sha256sum -- "$file" | cut -d ' ' -f 1
done
