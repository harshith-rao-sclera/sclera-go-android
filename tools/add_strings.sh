#!/bin/bash
# tools/add_strings.sh TABLE.tsv  (run from app/src/main/res; use bash)
# TABLE lines: key<TAB>locale<TAB>text   (locale: en, ar, de, ..., pt-rBR, zh-rCN)
# Appends <string name="key">text</string> to values[-locale]/strings.xml unless the key is already there.
# Text must already be valid Android XML text: escape ' as \' , & as &amp; , < as &lt;
set -euo pipefail
TABLE="$1"
for loc in $(cut -f2 "$TABLE" | sort -u); do
  dir=values; [ "$loc" != "en" ] && dir="values-$loc"
  file="$dir/strings.xml"
  [ -f "$file" ] || { echo "missing $file" >&2; exit 1; }
  block=$(mktemp)
  while IFS=$'\t' read -r key l text; do
    text=${text%$''}
    [ "$l" = "$loc" ] || continue
    grep -q "name=\"$key\"" "$file" && continue
    printf '    <string name="%s">%s</string>\n' "$key" "$text" >> "$block"
  done < "$TABLE"
  if [ -s "$block" ]; then
    BLOCK="$block" perl -0pi -e 'BEGIN{ local $/; open F, "<", $ENV{BLOCK}; $b = <F>; close F } s{</resources>}{$b</resources>}' "$file"
  fi
  rm -f "$block"
done
echo done
