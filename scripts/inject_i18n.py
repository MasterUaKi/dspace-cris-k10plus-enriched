#!/usr/bin/env python3
# Version: 1.1.0 | Release Date: 2026-11-17
# [DE] Skript zum Einfügen der Lokalisierungsschlüssel für K10plusEnriched in das Angular-Frontend (de.json5, en.json5)
# [EN] Safe i18n key injector for K10plusEnriched in the DSpace-CRIS 9 Angular frontend (de.json5, en.json5)

import os
import shutil
import sys

I18N_DIR = sys.argv[1] if len(sys.argv) > 1 else "/opt/dspace-cris-9-angular/src/assets/i18n"

TRANSLATIONS = {
    "de.json5": {
        "source": '  "submission.import-external.source.K10plusEnriched": "K10plus (angereichert mit OpenAlex)",',
        "search_tab": '  "submission.sections.describe.relationship-lookup.search-tab.tab-title.K10plusEnriched": "K10plus + OpenAlex ({{ count }})",',
        "selection_tab": '  "submission.sections.describe.relationship-lookup.selection-tab.title.K10plusEnriched": "Suchergebnisse",',
    },
    "en.json5": {
        "source": '  "submission.import-external.source.K10plusEnriched": "K10plus (Enriched with OpenAlex)",',
        "search_tab": '  "submission.sections.describe.relationship-lookup.search-tab.tab-title.K10plusEnriched": "K10plus + OpenAlex ({{ count }})",',
        "selection_tab": '  "submission.sections.describe.relationship-lookup.selection-tab.title.K10plusEnriched": "Search Results",',
    },
}

for filename, entries in TRANSLATIONS.items():
    path = os.path.join(I18N_DIR, filename)
    if not os.path.isfile(path):
        print(f"SKIP: {path} not found")
        continue

    shutil.copy2(path, path + ".bak")
    with open(path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    has_source = any("submission.import-external.source.K10plusEnriched" in l for l in lines)
    has_search = any("search-tab.tab-title.K10plusEnriched" in l for l in lines)
    has_selection = any("selection-tab.title.K10plusEnriched" in l for l in lines)

    new_lines = []
    for line in lines:
        new_lines.append(line)
        stripped = line.strip()
        if not has_source and stripped.startswith('"submission.import-external.source.'):
            new_lines.append(entries["source"] + "\n")
            has_source = True
        elif not has_search and stripped.startswith('"submission.sections.describe.relationship-lookup.search-tab.tab-title.'):
            new_lines.append(entries["search_tab"] + "\n")
            has_search = True
        elif not has_selection and stripped.startswith('"submission.sections.describe.relationship-lookup.selection-tab.title.'):
            new_lines.append(entries["selection_tab"] + "\n")
            has_selection = True

    with open(path, "w", encoding="utf-8") as f:
        f.writelines(new_lines)

    print(f"OK: {filename} | source={has_source}, search_tab={has_search}, selection_tab={has_selection}")
